package com.droidates.wallpapers.core.utils

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Simplified CircuitBreakerManager stub to eliminate over-engineered ad component tracking overhead.
 * Always CLOSED state (always allows ad requests).
 */
@Singleton
class CircuitBreakerManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferencesManager: PreferencesManager
) {
    enum class State {
        CLOSED, OPEN, HALF_OPEN
    }
    
    enum class AdComponent {
        INTERSTITIAL, REWARDED
    }
    
    private val _circuitState = mapOf(
        AdComponent.INTERSTITIAL to MutableStateFlow(State.CLOSED),
        AdComponent.REWARDED to MutableStateFlow(State.CLOSED)
    )
    
    val circuitState: Map<AdComponent, StateFlow<State>> = _circuitState.mapValues { it.value.asStateFlow() }
    
    fun recordSuccess(component: AdComponent, loadTimeMs: Long) {
        // No-op
    }
    
    fun recordError(component: AdComponent, errorType: String = "general") {
        // No-op
    }
    
    fun isAllowed(component: AdComponent): Boolean {
        return true
    }
    
    fun resetAllCircuits() {
        // No-op
    }
    
    fun getAverageLoadTime(component: AdComponent): Long {
        return 0L
    }
}
