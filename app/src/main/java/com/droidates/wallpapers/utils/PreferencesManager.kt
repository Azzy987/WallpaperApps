package com.droidates.wallpapers.utils

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PreferencesManager @Inject constructor(context: Context) : AppInitializer.ClearableCache {
    
    private val TAG = "PreferencesManager"
    private val VERBOSE_LOGGING = false
    
    // Main SharedPreferences instance
    private val sharedPreferences: SharedPreferences = context.getSharedPreferences(
        PREFERENCES_NAME, Context.MODE_PRIVATE
    )
    
    // In-memory cache for frequently accessed preferences
    private val memoryCache = HashMap<String, Any>()
    
    // Mutex for thread-safe cache access
    private val cacheMutex = Mutex()
    
    // Coroutine scope for asynchronous writes
    private val ioScope = CoroutineScope(Dispatchers.IO)
    
    // Initialize cache with frequently accessed values
    init {
        if (VERBOSE_LOGGING) {
            Log.d(TAG, "Initializing PreferencesManager with memory cache")
        }
        
        // Preload frequently accessed preferences into memory cache
        memoryCache[KEY_PRIVACY_POLICY_ACCEPTED] = sharedPreferences.getBoolean(KEY_PRIVACY_POLICY_ACCEPTED, false)
        memoryCache[KEY_ONBOARDING_COMPLETED] = sharedPreferences.getBoolean(KEY_ONBOARDING_COMPLETED, false)
        memoryCache[KEY_HAS_REQUESTED_NOTIFICATION_PERMISSION] = sharedPreferences.getBoolean(KEY_HAS_REQUESTED_NOTIFICATION_PERMISSION, false)
        memoryCache[KEY_PAYWALL_SHOWN_AFTER_ONBOARDING] = sharedPreferences.getBoolean(KEY_PAYWALL_SHOWN_AFTER_ONBOARDING, false)
    }
    
    companion object {
        private val PREFERENCES_NAME = com.droidates.wallpapers.config.AppConfig.PREFS_NAME
        private const val KEY_PRIVACY_POLICY_ACCEPTED = "privacy_policy_accepted"
        private const val KEY_LAST_UPDATE_CHECK = "last_update_check"
        private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
        private const val KEY_DETAIL_SCREEN_VISITS = "detail_screen_visits"
        private const val KEY_HAS_REQUESTED_NOTIFICATION_PERMISSION = "has_requested_notification_permission"
        private const val KEY_NAVIGATION_PATTERNS = "navigation_patterns"
        private const val KEY_SCREEN_DURATIONS = "screen_durations"
        private const val KEY_CIRCUIT_BREAKER_STATE = "circuit_breaker_state"
        private const val KEY_CIRCUIT_BREAKER_TIMESTAMPS = "circuit_breaker_timestamps"
        private const val KEY_PAYWALL_SHOWN_AFTER_ONBOARDING = "paywall_shown_after_onboarding"
    }
    
    /**
     * Check if the user has accepted the privacy policy
     * OPTIMIZATION: Uses in-memory cache for faster access
     */
    fun isPrivacyPolicyAccepted(): Boolean {
        // Get from memory cache first for better performance
        return memoryCache[KEY_PRIVACY_POLICY_ACCEPTED] as? Boolean 
            ?: sharedPreferences.getBoolean(KEY_PRIVACY_POLICY_ACCEPTED, false).also { value ->
                // Update cache if we had to read from disk
                ioScope.launch {
                    cacheMutex.withLock {
                        memoryCache[KEY_PRIVACY_POLICY_ACCEPTED] = value
                    }
                }
            }
    }
    
    /**
     * Set the privacy policy as accepted
     * OPTIMIZATION: Updates in-memory cache immediately and writes to disk asynchronously
     */
    fun setPrivacyPolicyAccepted(accepted: Boolean) {
        // Update memory cache immediately for fast access
        ioScope.launch {
            cacheMutex.withLock {
                memoryCache[KEY_PRIVACY_POLICY_ACCEPTED] = accepted
            }
            
            // Write to disk asynchronously
            sharedPreferences.edit(commit = false) {
                putBoolean(KEY_PRIVACY_POLICY_ACCEPTED, accepted)
            }
        }
    }
    
    /**
     * Get the timestamp of the last update check
     */
    fun getLastUpdateCheck(): Long {
        return sharedPreferences.getLong(KEY_LAST_UPDATE_CHECK, 0)
    }
    
    /**
     * Set the timestamp of the last update check
     */
    fun setLastUpdateCheck(timestamp: Long) {
        sharedPreferences.edit {
            putLong(KEY_LAST_UPDATE_CHECK, timestamp)
        }
    }
    
    /**
     * Check if we should check for updates (once per day)
     */
    fun shouldCheckForUpdates(): Boolean {
        val lastCheck = getLastUpdateCheck()
        val currentTime = System.currentTimeMillis()
        val oneDayInMillis = 24 * 60 * 60 * 1000L
        
        return currentTime - lastCheck > oneDayInMillis
    }
    
    /**
     * Check if onboarding has been completed
     * OPTIMIZATION: Uses in-memory cache for faster access
     */
    fun isOnboardingCompleted(): Boolean {
        // Get from memory cache first for better performance
        return memoryCache[KEY_ONBOARDING_COMPLETED] as? Boolean 
            ?: sharedPreferences.getBoolean(KEY_ONBOARDING_COMPLETED, false).also { value ->
                // Update cache if we had to read from disk
                ioScope.launch {
                    cacheMutex.withLock {
                        memoryCache[KEY_ONBOARDING_COMPLETED] = value
                    }
                }
            }
    }
    
    /**
     * Set onboarding as completed
     * OPTIMIZATION: Updates in-memory cache immediately and writes to disk asynchronously
     */
    fun setOnboardingCompleted(completed: Boolean) {
        // Update memory cache immediately for fast access
        ioScope.launch {
            cacheMutex.withLock {
                memoryCache[KEY_ONBOARDING_COMPLETED] = completed
            }
            
            // Write to disk asynchronously
            sharedPreferences.edit(commit = false) {
                putBoolean(KEY_ONBOARDING_COMPLETED, completed)
            }
        }
    }
    
    /**
     * Get the number of times user has opened the detail screen
     */
    fun getDetailScreenVisits(): Int {
        return sharedPreferences.getInt(KEY_DETAIL_SCREEN_VISITS, 0)
    }
    
    /**
     * Save the number of times user has opened the detail screen
     */
    fun saveDetailScreenVisits(count: Int) {
        sharedPreferences.edit {
            putInt(KEY_DETAIL_SCREEN_VISITS, count)
        }
    }
    
    /**
     * Check if we've already requested notification permission
     * OPTIMIZATION: Uses in-memory cache for faster access
     */
    fun getHasRequestedNotificationPermission(): Boolean {
        // Get from memory cache first for better performance
        return memoryCache[KEY_HAS_REQUESTED_NOTIFICATION_PERMISSION] as? Boolean 
            ?: sharedPreferences.getBoolean(KEY_HAS_REQUESTED_NOTIFICATION_PERMISSION, false).also { value ->
                // Update cache if we had to read from disk
                ioScope.launch {
                    cacheMutex.withLock {
                        memoryCache[KEY_HAS_REQUESTED_NOTIFICATION_PERMISSION] = value
                    }
                }
            }
    }
    
    /**
     * Save whether we've requested notification permission
     * OPTIMIZATION: Updates in-memory cache immediately and writes to disk asynchronously
     */
    fun setHasRequestedNotificationPermission(hasRequested: Boolean) {
        // Update memory cache immediately for fast access
        ioScope.launch {
            cacheMutex.withLock {
                memoryCache[KEY_HAS_REQUESTED_NOTIFICATION_PERMISSION] = hasRequested
            }
            
            // Write to disk asynchronously
            sharedPreferences.edit(commit = false) {
                putBoolean(KEY_HAS_REQUESTED_NOTIFICATION_PERMISSION, hasRequested)
            }
        }
    }
    
    /**
     * Clean up resources when the application is terminated
     */
    fun cleanup() {
        ioScope.launch {
            // Flush any pending writes to disk
            try {
                // Force commit any pending changes to ensure they're saved
                cacheMutex.withLock {
                    for ((key, value) in memoryCache) {
                        when (value) {
                            is Boolean -> sharedPreferences.edit(commit = true) { putBoolean(key, value) }
                            is Int -> sharedPreferences.edit(commit = true) { putInt(key, value) }
                            is Long -> sharedPreferences.edit(commit = true) { putLong(key, value) }
                            is String -> sharedPreferences.edit(commit = true) { putString(key, value) }
                        }
                    }
                }
                if (VERBOSE_LOGGING) {
                    Log.d(TAG, "Successfully flushed preference cache to disk")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error flushing preferences cache", e)
            }
        }
    }
    
    /**
     * Implement ClearableCache interface
     * This clears the in-memory cache but preserves the disk preferences
     */
    override fun clearCache() {
        ioScope.launch {
            try {
                // First flush any pending changes to disk
                cleanup()
                
                // Then clear the in-memory cache to free up memory
                cacheMutex.withLock {
                    memoryCache.clear()
                }
                
                if (VERBOSE_LOGGING) {
                    Log.d(TAG, "Preferences memory cache cleared")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error clearing preferences memory cache", e)
            }
        }
    }
    
    /**
     * Save navigation patterns for intelligent ad preloading
     * @param patterns String representation of navigation patterns
     */
    fun saveNavigationPatterns(patterns: String) {
        sharedPreferences.edit {
            putString(KEY_NAVIGATION_PATTERNS, patterns)
        }
    }
    
    /**
     * Get saved navigation patterns
     * @return String representation of navigation patterns or null if not found
     */
    fun getNavigationPatterns(): String? {
        return sharedPreferences.getString(KEY_NAVIGATION_PATTERNS, null)
    }
    
    /**
     * Save screen durations for intelligent ad preloading
     * @param durations String representation of screen durations
     */
    fun saveScreenDurations(durations: String) {
        sharedPreferences.edit {
            putString(KEY_SCREEN_DURATIONS, durations)
        }
    }
    
    /**
     * Get saved screen durations
     * @return String representation of screen durations or null if not found
     */
    fun getScreenDurations(): String? {
        return sharedPreferences.getString(KEY_SCREEN_DURATIONS, null)
    }
    
    /**
     * Save a string value to preferences
     * @param key The preference key
     * @param value The string value to save
     */
    fun saveString(key: String, value: String) {
        sharedPreferences.edit {
            putString(key, value)
        }
    }
    
    /**
     * Get a string value from preferences
     * @param key The preference key
     * @param defaultValue The default value to return if the key is not found
     * @return The string value or defaultValue if not found
     */
    fun getString(key: String, defaultValue: String): String? {
        return sharedPreferences.getString(key, defaultValue)
    }
    
    /**
     * Save a long value to preferences
     * @param key The preference key
     * @param value The long value to save
     */
    fun saveLong(key: String, value: Long) {
        sharedPreferences.edit {
            putLong(key, value)
        }
    }
    
    /**
     * Get a long value from preferences
     * @param key The preference key
     * @param defaultValue The default value to return if the key is not found
     * @return The long value or defaultValue if not found
     */
    fun getLong(key: String, defaultValue: Long): Long {
        return sharedPreferences.getLong(key, defaultValue)
    }
    
    /**
     * Save circuit breaker state for ad components
     * @param stateData String representation of circuit breaker states
     */
    fun saveCircuitBreakerState(stateData: String) {
        sharedPreferences.edit {
            putString(KEY_CIRCUIT_BREAKER_STATE, stateData)
        }
    }
    
    /**
     * Get saved circuit breaker state
     * @return String representation of circuit breaker states or null if not found
     */
    fun getCircuitBreakerState(): String? {
        return sharedPreferences.getString(KEY_CIRCUIT_BREAKER_STATE, null)
    }
    
    /**
     * Save circuit breaker timestamps for ad components
     * @param timestampData String representation of circuit breaker timestamps
     */
    fun saveCircuitBreakerTimestamps(timestampData: String) {
        sharedPreferences.edit {
            putString(KEY_CIRCUIT_BREAKER_TIMESTAMPS, timestampData)
        }
    }
    
    /**
     * Get saved circuit breaker timestamps
     * @return String representation of circuit breaker timestamps or null if not found
     */
    fun getCircuitBreakerTimestamps(): String? {
        return sharedPreferences.getString(KEY_CIRCUIT_BREAKER_TIMESTAMPS, null)
    }

    fun isPaywallShownAfterOnboarding(): Boolean {
        return memoryCache[KEY_PAYWALL_SHOWN_AFTER_ONBOARDING] as? Boolean ?: false
    }

    fun setPaywallShownAfterOnboarding(shown: Boolean) {
        memoryCache[KEY_PAYWALL_SHOWN_AFTER_ONBOARDING] = shown
        ioScope.launch {
            sharedPreferences.edit(commit = false) {
                putBoolean(KEY_PAYWALL_SHOWN_AFTER_ONBOARDING, shown)
            }
        }
    }
} 