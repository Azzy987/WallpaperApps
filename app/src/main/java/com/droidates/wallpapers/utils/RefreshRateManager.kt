package com.droidates.wallpapers.utils

import android.app.Activity
import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.util.Log
import android.view.Display
import android.view.Window
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Manages the refresh rate settings for the app.
 * Provides functionality to get supported refresh rates and set the preferred refresh rate.
 * OPTIMIZATION: Added support for placeholder initialization to defer expensive operations
 */
class RefreshRateManager(private val context: Context, private val isPlaceholder: Boolean = false) {
    
    companion object {
        private const val TAG = "RefreshRateManager"
        
        // Refresh rate constants
        const val REFRESH_RATE_AUTO = 0f
        const val REFRESH_RATE_60HZ = 60f
        const val REFRESH_RATE_90HZ = 90f
        const val REFRESH_RATE_120HZ = 120f
        
        // Preference key for storing the selected refresh rate
        private const val PREF_REFRESH_RATE = "pref_refresh_rate"
        
        // Default to highest refresh rate for maximum smoothness
        private const val DEFAULT_TO_HIGHEST = true
    }
    
    // Shared preferences for storing the selected refresh rate - lazy initialized to avoid main thread I/O
    private val preferences by lazy { context.getSharedPreferences("refresh_rate_prefs", Context.MODE_PRIVATE) }
    
    // Current refresh rate state - initialized with default value to avoid main thread I/O
    private val _currentRefreshRate = MutableStateFlow(REFRESH_RATE_AUTO)
    val currentRefreshRate: StateFlow<Float> = _currentRefreshRate.asStateFlow()
    
    // Coroutine scope for background operations
    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    // Available refresh rates on the device
    private val _availableRefreshRates = MutableStateFlow<List<Float>>(emptyList())
    val availableRefreshRates: StateFlow<List<Float>> = _availableRefreshRates.asStateFlow()
    
    init {
        // OPTIMIZATION: Skip expensive initialization if this is just a placeholder
        // This prevents main thread blocking during app startup
        if (!isPlaceholder) {
            // Initialize available refresh rates
            updateAvailableRefreshRates()
            // Initialize current refresh rate value - will be done off main thread
            initializeCurrentRefreshRate()
        } else {
            // Set default values for placeholder mode
            _availableRefreshRates.value = listOf(REFRESH_RATE_AUTO, REFRESH_RATE_60HZ)
            Log.d(TAG, "Created placeholder RefreshRateManager, deferring full initialization")
        }
    }
    
    /**
     * Initializes the current refresh rate from SharedPreferences off the main thread
     * This prevents StrictMode violations during app startup
     */
    private fun initializeCurrentRefreshRate() {
        backgroundScope.launch {
            try {
                val storedRate = getStoredRefreshRate()
                _currentRefreshRate.value = storedRate
                Log.d(TAG, "Initialized current refresh rate off main thread: $storedRate")
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing refresh rate: ${e.message}")
            }
        }
    }
    
    /**
     * Updates the list of available refresh rates on the device
     */
    private fun updateAvailableRefreshRates() {
        try {
            val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
            val defaultDisplay = displayManager.getDisplay(Display.DEFAULT_DISPLAY)
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val supportedModes = defaultDisplay.supportedModes
                val refreshRates = supportedModes.map { it.refreshRate }.distinct().sorted()
                
                // Always add AUTO as an option
                val rates = mutableListOf(REFRESH_RATE_AUTO)
                rates.addAll(refreshRates)
                
                _availableRefreshRates.value = rates
                
                Log.d(TAG, "Available refresh rates: ${rates.joinToString()}")  
            } else {
                // For older devices, just use the default refresh rate
                val defaultRate = defaultDisplay.refreshRate
                _availableRefreshRates.value = listOf(REFRESH_RATE_AUTO, defaultRate)
                
                Log.d(TAG, "Default refresh rate: $defaultRate")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting refresh rates: ${e.message}")
            // Fallback to standard refresh rates if there's an error
            _availableRefreshRates.value = listOf(REFRESH_RATE_AUTO, REFRESH_RATE_60HZ)
        }
    }
    
    /**
     * Gets the stored refresh rate preference or the highest available if DEFAULT_TO_HIGHEST is true
     * IMPORTANT: This method accesses disk and should NOT be called on the main thread
     */
    private fun getStoredRefreshRate(): Float {
        try {
            val storedRate = preferences.getFloat(PREF_REFRESH_RATE, REFRESH_RATE_AUTO)
            
            // If stored rate is AUTO and we're configured to use highest by default, find the highest available
            if (storedRate == REFRESH_RATE_AUTO && DEFAULT_TO_HIGHEST) {
                val highestRate = findHighestRefreshRate()
                if (highestRate > 0f) {
                    Log.d(TAG, "Using highest available refresh rate: $highestRate")
                    return highestRate
                }
            }
            
            return storedRate
        } catch (e: Exception) {
            Log.e(TAG, "Error reading stored refresh rate: ${e.message}")
            return REFRESH_RATE_AUTO
        }
    }
    
    /**
     * Gets the stored refresh rate preference safely off the main thread
     * Returns via callback to avoid blocking
     */
    fun getStoredRefreshRateAsync(callback: (Float) -> Unit) {
        backgroundScope.launch {
            val rate = getStoredRefreshRate()
            callback(rate)
        }
    }
    
    /**
     * Finds the highest available refresh rate on the device
     */
    private fun findHighestRefreshRate(): Float {
        try {
            val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
            val defaultDisplay = displayManager.getDisplay(Display.DEFAULT_DISPLAY)
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val supportedModes = defaultDisplay.supportedModes
                val refreshRates = supportedModes.map { it.refreshRate }.distinct().sorted()
                
                // Return the highest refresh rate, or 0 if none found
                return refreshRates.maxOrNull() ?: 0f
            } else {
                // For older devices, just use the default refresh rate
                return defaultDisplay.refreshRate
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error finding highest refresh rate: ${e.message}")
            return 0f
        }
    }
    
    /**
     * OPTIMIZATION: Fully initializes the manager when called from a background thread
     * This allows the main thread to continue with UI rendering while refresh rate detection
     * happens in the background
     */
    fun fullyInitialize() {
        if (isPlaceholder) {
            backgroundScope.launch {
                try {
                    Log.d(TAG, "Fully initializing RefreshRateManager in background thread")
                    updateAvailableRefreshRates()
                    
                    // Update current refresh rate based on complete data
                    val updatedRate = getStoredRefreshRate()
                    if (updatedRate != _currentRefreshRate.value) {
                        _currentRefreshRate.value = updatedRate
                    }
                    
                    Log.d(TAG, "RefreshRateManager fully initialized with ${_availableRefreshRates.value.size} refresh rates")
                } catch (e: Exception) {
                    Log.e(TAG, "Error during full initialization: ${e.message}")
                }
            }
        }
    }
    
    /**
     * Sets the preferred refresh rate for the app
     * Handles saving to SharedPreferences off the main thread to avoid StrictMode violations
     */
    fun setPreferredRefreshRate(refreshRate: Float) {
        // Update the current value immediately for UI responsiveness
        _currentRefreshRate.value = refreshRate
        Log.d(TAG, "Set preferred refresh rate to: $refreshRate")
        
        // Save to SharedPreferences using coroutines
        backgroundScope.launch {
            try {
                preferences.edit().putFloat(PREF_REFRESH_RATE, refreshRate).commit()
                Log.d(TAG, "Saved refresh rate preference to disk: $refreshRate")
                
                // Notify any observers that the refresh rate has changed on main thread
                try {
                    val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
                    displayManager.getDisplay(Display.DEFAULT_DISPLAY)?.let { display ->
                        // Force a display change event to update any listeners
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            val displayListener = object : DisplayManager.DisplayListener {
                                override fun onDisplayAdded(displayId: Int) {}
                                override fun onDisplayRemoved(displayId: Int) {}
                                override fun onDisplayChanged(displayId: Int) {}
                            }
                            
                            // Register and immediately unregister to force a refresh
                            displayManager.registerDisplayListener(displayListener, null)
                            displayManager.unregisterDisplayListener(displayListener)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error notifying display change: ${e.message}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error saving refresh rate preference: ${e.message}")
            }
        }
    }
    
    /**
     * Applies the preferred refresh rate to the given window
     */
    @RequiresApi(Build.VERSION_CODES.R)
    fun applyPreferredRefreshRate(window: Window) {
        val refreshRate = _currentRefreshRate.value
        val mainHandler = Handler(Looper.getMainLooper())
        val applyOnMain = Runnable {
            try {
                if (refreshRate == REFRESH_RATE_AUTO) {
                    window.attributes = window.attributes.apply {
                        preferredDisplayModeId = 0
                    }
                    Log.d(TAG, "Applied AUTO refresh rate (system default)")
                    return@Runnable
                }
                val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
                val display = displayManager.getDisplay(Display.DEFAULT_DISPLAY)
                val modes = display.supportedModes
                val targetMode = modes.minByOrNull { Math.abs(it.refreshRate - refreshRate) }
                if (targetMode != null) {
                    window.attributes = window.attributes.apply {
                        preferredDisplayModeId = targetMode.modeId
                    }
                    Log.d(TAG, "Applied refresh rate: ${targetMode.refreshRate} using preferredDisplayModeId: ${targetMode.modeId}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error applying refresh rate: ${e.message}")
            }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            applyOnMain.run()
        } else {
            mainHandler.post(applyOnMain)
        }
    }

    /**
     * Applies the preferred refresh rate to the given window for older Android versions
     */
    @Suppress("DEPRECATION")
    fun applyPreferredRefreshRateForOlderVersions(window: Window) {
        val refreshRate = _currentRefreshRate.value
        if (refreshRate == REFRESH_RATE_AUTO) return
        val mainHandler = Handler(Looper.getMainLooper())
        val applyOnMain = Runnable {
            try {
                window.attributes = window.attributes.apply {
                    preferredRefreshRate = refreshRate
                }
                Log.d(TAG, "Applied refresh rate: $refreshRate using preferredRefreshRate")
            } catch (e: Exception) {
                Log.e(TAG, "Error applying refresh rate: ${e.message}")
            }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            applyOnMain.run()
        } else {
            mainHandler.post(applyOnMain)
        }
    }
}

/**
 * Composable function to get the current refresh rate of the device
 */
@Composable
fun rememberCurrentRefreshRate(): State<Float> {
    val context = LocalContext.current
    val refreshRateState = remember { mutableStateOf(0f) }
    
    DisposableEffect(context) {
        val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val displayListener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) {}
            
            override fun onDisplayRemoved(displayId: Int) {}
            
            override fun onDisplayChanged(displayId: Int) {
                if (displayId == Display.DEFAULT_DISPLAY) {
                    val display = displayManager.getDisplay(displayId)
                    refreshRateState.value = display.refreshRate
                }
            }
        }
        
        // Initialize with current refresh rate
        val display = displayManager.getDisplay(Display.DEFAULT_DISPLAY)
        refreshRateState.value = display.refreshRate
        
        // Register listener
        displayManager.registerDisplayListener(displayListener, null)
        
        onDispose {
            displayManager.unregisterDisplayListener(displayListener)
        }
    }
    
    return refreshRateState
}

/**
 * Composable function to apply the preferred refresh rate to the current activity
 */
@Composable
fun ApplyPreferredRefreshRate(refreshRateManager: RefreshRateManager) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentRefreshRate by refreshRateManager.currentRefreshRate.collectAsState(initial = RefreshRateManager.REFRESH_RATE_AUTO)
    
    DisposableEffect(lifecycleOwner, currentRefreshRate) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                (context as? Activity)?.window?.let { window ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        refreshRateManager.applyPreferredRefreshRate(window)
                    } else {
                        refreshRateManager.applyPreferredRefreshRateForOlderVersions(window)
                    }
                }
            }
        }
        
        lifecycleOwner.lifecycle.addObserver(observer)
        
        // Apply immediately if the activity is already resumed
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            (context as? Activity)?.window?.let { window ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    refreshRateManager.applyPreferredRefreshRate(window)
                } else {
                    refreshRateManager.applyPreferredRefreshRateForOlderVersions(window)
                }
            }
        }
        
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
}

/**
 * Extension function to collect a StateFlow as a Compose State
 */
@Composable
fun <T> StateFlow<T>.collectAsState(initial: T): State<T> {
    val state = remember { mutableStateOf(initial) }
    
    LaunchedEffect(this) {
        collect { value ->
            state.value = value
        }
    }
    
    return state
}
