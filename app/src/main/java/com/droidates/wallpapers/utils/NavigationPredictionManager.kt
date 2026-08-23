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
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages prediction of user navigation patterns to intelligently preload ads.
 * This class tracks user navigation behavior and predicts likely next screens
 * to optimize ad preloading.
 */
@Singleton
class NavigationPredictionManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferencesManager: PreferencesManager
) {
    companion object {
        private const val TAG = "NavigationPrediction"
        private const val PREF_NAVIGATION_PATTERNS = "navigation_patterns"
        private const val PREF_SCREEN_DURATIONS = "screen_durations"
        private const val MAX_PATTERN_HISTORY = 50
        private const val MIN_CONFIDENCE_THRESHOLD = 0.6f // 60% confidence required for prediction
    }
    
    // Screen types for navigation tracking
    enum class ScreenType {
        HOME,
        DETAIL,
        CATEGORY,
        FAVORITES,
        SETTINGS,
        PREMIUM,
        SEARCH,
        EDIT_WALLPAPER,
        ABOUT,
        UNKNOWN
    }
    
    // Prediction result with confidence score
    data class NavigationPrediction(
        val predictedScreen: ScreenType,
        val confidence: Float,
        val estimatedDuration: Long // in milliseconds
    )
    
    // Current prediction state
    private val _currentPrediction = MutableStateFlow<NavigationPrediction?>(
        NavigationPrediction(ScreenType.DETAIL, 0.7f, 30000) // Default prediction
    )
    val currentPrediction: StateFlow<NavigationPrediction?> = _currentPrediction.asStateFlow()
    
    // Track current screen
    private val _currentScreen = MutableStateFlow(ScreenType.HOME)
    val currentScreen: StateFlow<ScreenType> = _currentScreen.asStateFlow()
    
    // Navigation history
    private val navigationHistory = mutableListOf<Pair<ScreenType, Long>>() // Screen and timestamp
    
    // Navigation patterns (from -> to) with counts
    private val navigationPatterns = ConcurrentHashMap<Pair<ScreenType, ScreenType>, Int>()
    
    // Screen durations (screen type -> average duration in ms)
    private val screenDurations = ConcurrentHashMap<ScreenType, Long>()
    
    // Coroutine scope for background operations
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // Track when user entered current screen
    private var currentScreenEnteredTime = System.currentTimeMillis()
    
    init {
        // Load saved patterns and durations
        loadNavigationData()
    }
    
    /**
     * Record a screen navigation event
     * @param screenType The screen being navigated to
     */
    fun recordNavigation(screenType: ScreenType) {
        val previousScreen = _currentScreen.value
        val currentTime = System.currentTimeMillis()
        
        // Calculate time spent on previous screen
        val timeSpent = currentTime - currentScreenEnteredTime
        
        // Only record if it's a valid duration (between 1 second and 10 minutes)
        if (timeSpent in 1000..600000) {
            // Update average duration for the previous screen
            updateScreenDuration(previousScreen, timeSpent)
            
            // Record the transition pattern
            val pattern = Pair(previousScreen, screenType)
            navigationPatterns[pattern] = (navigationPatterns[pattern] ?: 0) + 1
            
            // Add to history
            navigationHistory.add(Pair(previousScreen, currentTime))
            if (navigationHistory.size > MAX_PATTERN_HISTORY) {
                navigationHistory.removeAt(0)
            }
            
            // Save patterns periodically
            if (navigationHistory.size % 5 == 0) {
                saveNavigationData()
            }
            
            Log.d(TAG, "Recorded navigation: $previousScreen -> $screenType (${timeSpent}ms)")
        }
        
        // Update current screen
        _currentScreen.value = screenType
        currentScreenEnteredTime = currentTime
        
        // Generate new prediction based on this navigation
        predictNextNavigation()
    }
    
    /**
     * Update the average duration for a screen type
     */
    private fun updateScreenDuration(screenType: ScreenType, duration: Long) {
        val currentAverage = screenDurations[screenType] ?: duration
        val count = navigationPatterns.entries.count { it.key.first == screenType }
        
        // Weighted average (more weight to new data)
        val newAverage = if (count > 1) {
            (currentAverage * 0.7f + duration * 0.3f).toLong()
        } else {
            duration
        }
        
        screenDurations[screenType] = newAverage
    }
    
    /**
     * Predict the next likely navigation based on patterns
     */
    fun predictNextNavigation() {
        coroutineScope.launch {
            val currentScreen = _currentScreen.value
            
            // Get all transitions from current screen
            val possibleTransitions = navigationPatterns.entries
                .filter { it.key.first == currentScreen }
                
            if (possibleTransitions.isEmpty()) {
                // No data for prediction, use default
                if (currentScreen == ScreenType.HOME) {
                    _currentPrediction.value = NavigationPrediction(
                        ScreenType.DETAIL,
                        0.7f,
                        30000
                    )
                } else {
                    _currentPrediction.value = NavigationPrediction(
                        ScreenType.HOME,
                        0.6f,
                        15000
                    )
                }
                return@launch
            }
            
            // Calculate total transitions from current screen
            val totalTransitions = possibleTransitions.sumOf { it.value }
            
            // Find most likely next screen
            val mostLikelyTransition = possibleTransitions.maxByOrNull { it.value }
            
            if (mostLikelyTransition != null) {
                val nextScreen = mostLikelyTransition.key.second
                val confidence = mostLikelyTransition.value.toFloat() / totalTransitions
                
                // Only predict if confidence is above threshold
                if (confidence >= MIN_CONFIDENCE_THRESHOLD) {
                    val estimatedDuration = screenDurations[currentScreen] ?: 30000
                    
                    _currentPrediction.value = NavigationPrediction(
                        nextScreen,
                        confidence,
                        estimatedDuration
                    )
                    
                    Log.d(TAG, "Predicted next navigation: $currentScreen -> $nextScreen " +
                            "(confidence: ${String.format("%.2f", confidence)}, " +
                            "estimated duration: ${estimatedDuration}ms)")
                } else {
                    // Not confident enough
                    _currentPrediction.value = null
                }
            } else {
                // No prediction
                _currentPrediction.value = null
            }
        }
    }
    
    /**
     * Save navigation patterns and durations to preferences
     */
    private fun saveNavigationData() {
        coroutineScope.launch {
            try {
                // Convert patterns to serializable format
                val patternData = navigationPatterns.entries.map { 
                    "${it.key.first.name},${it.key.second.name}:${it.value}"
                }.joinToString("|")
                
                // Convert durations to serializable format
                val durationData = screenDurations.entries.map { 
                    "${it.key.name}:${it.value}"
                }.joinToString("|")
                
                // Save to preferences
                preferencesManager.saveNavigationPatterns(patternData)
                preferencesManager.saveScreenDurations(durationData)
                
                Log.d(TAG, "Saved navigation data: ${navigationPatterns.size} patterns, " +
                        "${screenDurations.size} durations")
            } catch (e: Exception) {
                Log.e(TAG, "Error saving navigation data: ${e.message}")
            }
        }
    }
    
    /**
     * Load navigation patterns and durations from preferences
     */
    private fun loadNavigationData() {
        coroutineScope.launch {
            try {
                // Load patterns
                val patternData = preferencesManager.getNavigationPatterns()
                if (!patternData.isNullOrEmpty()) {
                    patternData.split("|").forEach { entry ->
                        val parts = entry.split(":")
                        if (parts.size == 2) {
                            val pattern = parts[0].split(",")
                            if (pattern.size == 2) {
                                try {
                                    val fromScreen = ScreenType.valueOf(pattern[0])
                                    val toScreen = ScreenType.valueOf(pattern[1])
                                    val count = parts[1].toIntOrNull() ?: 0
                                    
                                    navigationPatterns[Pair(fromScreen, toScreen)] = count
                                } catch (e: IllegalArgumentException) {
                                    // Invalid enum value, ignore
                                }
                            }
                        }
                    }
                }
                
                // Load durations
                val durationData = preferencesManager.getScreenDurations()
                if (!durationData.isNullOrEmpty()) {
                    durationData.split("|").forEach { entry ->
                        val parts = entry.split(":")
                        if (parts.size == 2) {
                            try {
                                val screen = ScreenType.valueOf(parts[0])
                                val duration = parts[1].toLongOrNull() ?: 0L
                                
                                screenDurations[screen] = duration
                            } catch (e: IllegalArgumentException) {
                                // Invalid enum value, ignore
                            }
                        }
                    }
                }
                
                Log.d(TAG, "Loaded navigation data: ${navigationPatterns.size} patterns, " +
                        "${screenDurations.size} durations")
            } catch (e: Exception) {
                Log.e(TAG, "Error loading navigation data: ${e.message}")
            }
        }
    }
    
    /**
     * Clear all navigation data
     */
    fun clearNavigationData() {
        navigationPatterns.clear()
        screenDurations.clear()
        navigationHistory.clear()
        _currentPrediction.value = null
        
        // Clear from preferences
        coroutineScope.launch {
            preferencesManager.saveNavigationPatterns("")
            preferencesManager.saveScreenDurations("")
        }
        
        Log.d(TAG, "Cleared all navigation data")
    }
}
