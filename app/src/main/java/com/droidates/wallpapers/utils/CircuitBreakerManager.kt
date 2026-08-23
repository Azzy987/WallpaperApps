package com.droidates.wallpapers.utils

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Circuit breaker pattern implementation for ad components.
 * Monitors crashes and performance issues in ad components and
 * automatically disables problematic components to maintain app stability.
 */
@Singleton
class CircuitBreakerManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferencesManager: PreferencesManager
) {
    companion object {
        private const val TAG = "CircuitBreaker"
        private const val PREF_CIRCUIT_BREAKER_STATE = "circuit_breaker_state"
        private const val PREF_ERROR_COUNTS = "ad_error_counts"
        private const val PREF_LAST_RESET_TIME = "circuit_breaker_last_reset"
        
        // Thresholds for circuit breaker
        private const val ERROR_THRESHOLD = 5 // Number of errors before tripping
        private const val PERFORMANCE_THRESHOLD_MS = 5000 // 5 seconds load time is too slow
        private const val AUTO_RESET_DURATION = 24 * 60 * 60 * 1000L // 24 hours
        private const val SLOW_LOAD_WEIGHT = 0.5 // Slow loads count as partial errors
    }
    
    // Circuit breaker states
    enum class State {
        CLOSED, // Normal operation
        OPEN,   // Component disabled
        HALF_OPEN // Testing if component can be re-enabled
    }
    
    // Ad component types
    enum class AdComponent {
        INTERSTITIAL,
        REWARDED,
        NATIVE
    }
    
    // Current state of each circuit breaker
    private val _circuitState = ConcurrentHashMap<AdComponent, MutableStateFlow<State>>().apply {
        AdComponent.values().forEach { component ->
            put(component, MutableStateFlow(State.CLOSED))
        }
    }
    
    // Expose states as immutable StateFlows
    val circuitState: Map<AdComponent, StateFlow<State>> = _circuitState.mapValues { it.value.asStateFlow() }
    
    // Track error counts
    private val errorCounts = ConcurrentHashMap<AdComponent, AtomicInteger>().apply {
        AdComponent.values().forEach { component ->
            put(component, AtomicInteger(0))
        }
    }
    
    // Track performance metrics
    private val loadTimes = ConcurrentHashMap<AdComponent, MutableList<Long>>().apply {
        AdComponent.values().forEach { component ->
            put(component, mutableListOf())
        }
    }
    
    // Coroutine scope for background operations
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    init {
        // Load saved circuit breaker states
        loadCircuitBreakerState()
        
        // Schedule auto-reset check
        scheduleAutoReset()
    }
    
    /**
     * Record a successful ad operation with its load time
     * @param component The ad component
     * @param loadTimeMs The time it took to load the ad in milliseconds
     */
    fun recordSuccess(component: AdComponent, loadTimeMs: Long) {
        // Track load time for performance monitoring
        synchronized(loadTimes) {
            val times = loadTimes[component] ?: mutableListOf()
            times.add(loadTimeMs)
            
            // Keep only the last 10 load times
            if (times.size > 10) {
                times.removeAt(0)
            }
            
            // Check if load time is too slow
            if (loadTimeMs > PERFORMANCE_THRESHOLD_MS) {
                Log.w(TAG, "Slow ad load detected for $component: ${loadTimeMs}ms")
                // Count slow loads as partial errors
                incrementErrorCount(component, SLOW_LOAD_WEIGHT)
            }
        }
        
        // If circuit is half-open and operation succeeded, close the circuit
        if (_circuitState[component]?.value == State.HALF_OPEN) {
            Log.d(TAG, "Circuit for $component successfully tested, closing circuit")
            closeCircuit(component)
        }
    }
    
    /**
     * Record an error in an ad component
     * @param component The ad component
     * @param errorType The type of error (optional for categorization)
     */
    fun recordError(component: AdComponent, errorType: String = "general") {
        Log.w(TAG, "Ad error recorded for $component: $errorType")
        incrementErrorCount(component, 1.0)
    }
    
    /**
     * Increment the error count for a component and check if circuit should trip
     * @param component The ad component
     * @param weight The weight of the error (1.0 for full errors, less for warnings)
     */
    private fun incrementErrorCount(component: AdComponent, weight: Double) {
        // Only increment if circuit is not already open
        if (_circuitState[component]?.value != State.OPEN) {
            val currentCount = errorCounts[component]?.get() ?: 0
            val newCount = currentCount + (weight * 100).toInt()
            errorCounts[component]?.set(newCount)
            
            Log.d(TAG, "Error count for $component: ${newCount/100.0} (threshold: $ERROR_THRESHOLD)")
            
            // Check if threshold is exceeded
            if (newCount >= ERROR_THRESHOLD * 100) {
                Log.w(TAG, "Circuit breaker tripped for $component - too many errors")
                openCircuit(component)
            }
            
            // Save updated error counts
            saveErrorCounts()
        }
    }
    
    /**
     * Open the circuit for a component (disable it)
     * @param component The ad component to disable
     */
    private fun openCircuit(component: AdComponent) {
        _circuitState[component]?.value = State.OPEN
        saveCircuitBreakerState()
        Log.w(TAG, "$component ads disabled due to excessive errors or performance issues")
    }
    
    /**
     * Close the circuit for a component (enable it)
     * @param component The ad component to enable
     */
    private fun closeCircuit(component: AdComponent) {
        _circuitState[component]?.value = State.CLOSED
        errorCounts[component]?.set(0)
        saveCircuitBreakerState()
        saveErrorCounts()
        Log.d(TAG, "$component ads re-enabled")
    }
    
    /**
     * Set the circuit to half-open state to test if component can be re-enabled
     * @param component The ad component to test
     */
    private fun halfOpenCircuit(component: AdComponent) {
        if (_circuitState[component]?.value == State.OPEN) {
            _circuitState[component]?.value = State.HALF_OPEN
            saveCircuitBreakerState()
            Log.d(TAG, "Testing $component ads to see if they can be re-enabled")
        }
    }
    
    /**
     * Check if a component is allowed to operate
     * @param component The ad component to check
     * @return true if the component is allowed to operate, false if it should be disabled
     */
    fun isAllowed(component: AdComponent): Boolean {
        val state = _circuitState[component]?.value ?: State.CLOSED
        
        // If half-open, allow one test operation
        if (state == State.HALF_OPEN) {
            Log.d(TAG, "Allowing test operation for $component in half-open state")
        }
        
        // Component is allowed if circuit is CLOSED or HALF_OPEN
        return state != State.OPEN
    }
    
    /**
     * Force reset all circuit breakers
     */
    fun resetAllCircuits() {
        Log.d(TAG, "Manually resetting all circuit breakers")
        AdComponent.values().forEach { component ->
            closeCircuit(component)
        }
        preferencesManager.saveLong(PREF_LAST_RESET_TIME, System.currentTimeMillis())
    }
    
    /**
     * Schedule automatic reset check for circuit breakers
     */
    private fun scheduleAutoReset() {
        coroutineScope.launch {
            val lastResetTime = preferencesManager.getLong(PREF_LAST_RESET_TIME, 0L)
            val currentTime = System.currentTimeMillis()
            
            // If enough time has passed since last reset, try half-opening circuits
            if (currentTime - lastResetTime > AUTO_RESET_DURATION) {
                Log.d(TAG, "Auto-reset time reached, attempting to recover circuits")
                
                AdComponent.values().forEach { component ->
                    if (_circuitState[component]?.value == State.OPEN) {
                        halfOpenCircuit(component)
                    }
                }
                
                preferencesManager.saveLong(PREF_LAST_RESET_TIME, currentTime)
            }
        }
    }
    
    /**
     * Save circuit breaker states to preferences
     */
    private fun saveCircuitBreakerState() {
        val stateData = AdComponent.values().joinToString("|") { component ->
            "${component.name}:${_circuitState[component]?.value?.name ?: State.CLOSED.name}"
        }
        
        preferencesManager.saveString(PREF_CIRCUIT_BREAKER_STATE, stateData)
    }
    
    /**
     * Save error counts to preferences
     */
    private fun saveErrorCounts() {
        val countData = AdComponent.values().joinToString("|") { component ->
            "${component.name}:${errorCounts[component]?.get() ?: 0}"
        }
        
        preferencesManager.saveString(PREF_ERROR_COUNTS, countData)
    }
    
    /**
     * Load circuit breaker states from preferences
     */
    private fun loadCircuitBreakerState() {
        coroutineScope.launch {
            try {
                // Load circuit states
                val stateData = preferencesManager.getString(PREF_CIRCUIT_BREAKER_STATE, "")
                if (!stateData.isNullOrEmpty()) {
                    stateData.split("|").forEach { entry ->
                        val parts = entry.split(":")
                        if (parts.size == 2) {
                            try {
                                val component = AdComponent.valueOf(parts[0])
                                val state = State.valueOf(parts[1])
                                
                                _circuitState[component]?.value = state
                            } catch (e: IllegalArgumentException) {
                                // Invalid enum value, ignore
                            }
                        }
                    }
                }
                
                // Load error counts
                val countData = preferencesManager.getString(PREF_ERROR_COUNTS, "")
                if (!countData.isNullOrEmpty()) {
                    countData.split("|").forEach { entry ->
                        val parts = entry.split(":")
                        if (parts.size == 2) {
                            try {
                                val component = AdComponent.valueOf(parts[0])
                                val count = parts[1].toIntOrNull() ?: 0
                                
                                errorCounts[component]?.set(count)
                            } catch (e: IllegalArgumentException) {
                                // Invalid enum value, ignore
                            }
                        }
                    }
                }
                
                Log.d(TAG, "Loaded circuit breaker states: " + 
                      AdComponent.values().joinToString(", ") { "${it.name}=${_circuitState[it]?.value}" })
            } catch (e: Exception) {
                Log.e(TAG, "Error loading circuit breaker state: ${e.message}")
            }
        }
    }
    
    /**
     * Get average load time for a component
     * @param component The ad component
     * @return The average load time in milliseconds, or 0 if no data
     */
    fun getAverageLoadTime(component: AdComponent): Long {
        val times = loadTimes[component] ?: return 0
        if (times.isEmpty()) return 0
        return times.sum() / times.size
    }
}
