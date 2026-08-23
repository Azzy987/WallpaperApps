package com.droidates.wallpapers.core.utils

import android.content.Context
import android.os.Build
import android.view.Window
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Cleaned and optimized RefreshRateManager stub.
 * Removes heavy background thread creation, display listener registrations,
 * and high overhead IPC queries while remaining fully compatible with SettingsScreen.
 */
class RefreshRateManager(private val context: Context, private val isPlaceholder: Boolean = false) {
    
    companion object {
        const val REFRESH_RATE_AUTO = 0f
        const val REFRESH_RATE_60HZ = 60f
        const val REFRESH_RATE_90HZ = 90f
        const val REFRESH_RATE_120HZ = 120f
        private const val PREF_REFRESH_RATE = "pref_refresh_rate"
    }
    
    private val preferences by lazy { context.getSharedPreferences("refresh_rate_prefs", Context.MODE_PRIVATE) }
    
    private val _currentRefreshRate = MutableStateFlow(REFRESH_RATE_AUTO)
    val currentRefreshRate: StateFlow<Float> = _currentRefreshRate.asStateFlow()
    
    private val _availableRefreshRates = MutableStateFlow(
        listOf(REFRESH_RATE_AUTO, REFRESH_RATE_60HZ, REFRESH_RATE_90HZ, REFRESH_RATE_120HZ)
    )
    val availableRefreshRates: StateFlow<List<Float>> = _availableRefreshRates.asStateFlow()
    
    init {
        val stored = preferences.getFloat(PREF_REFRESH_RATE, REFRESH_RATE_AUTO)
        _currentRefreshRate.value = stored
    }
    
    fun getStoredRefreshRateAsync(callback: (Float) -> Unit) {
        callback(_currentRefreshRate.value)
    }
    
    fun fullyInitialize() {
        // No-op
    }
    
    fun setPreferredRefreshRate(refreshRate: Float) {
        _currentRefreshRate.value = refreshRate
        preferences.edit().putFloat(PREF_REFRESH_RATE, refreshRate).apply()
    }
    
    fun applyPreferredRefreshRate(window: Window) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val refreshRate = _currentRefreshRate.value
            if (refreshRate == REFRESH_RATE_AUTO) {
                window.attributes = window.attributes.apply {
                    preferredDisplayModeId = 0
                }
            } else {
                val display = window.context.display
                val modes = display?.supportedModes ?: emptyArray()
                val targetMode = modes.minByOrNull { Math.abs(it.refreshRate - refreshRate) }
                if (targetMode != null) {
                    window.attributes = window.attributes.apply {
                        preferredDisplayModeId = targetMode.modeId
                    }
                }
            }
        }
    }

    fun applyPreferredRefreshRateForOlderVersions(window: Window) {
        val refreshRate = _currentRefreshRate.value
        if (refreshRate != REFRESH_RATE_AUTO) {
            @Suppress("DEPRECATION")
            window.attributes = window.attributes.apply {
                preferredRefreshRate = refreshRate
            }
        }
    }
}

@Composable
fun rememberCurrentRefreshRate(): State<Float> {
    return remember { mutableStateOf(60f) }
}

@Composable
fun ApplyPreferredRefreshRate(refreshRateManager: RefreshRateManager) {
    // No-op
}

@Composable
fun <T> StateFlow<T>.collectAsState(initial: T): State<T> {
    val state = remember { mutableStateOf(initial) }
    androidx.compose.runtime.LaunchedEffect(this) {
        collect { value ->
            state.value = value
        }
    }
    return state
}
