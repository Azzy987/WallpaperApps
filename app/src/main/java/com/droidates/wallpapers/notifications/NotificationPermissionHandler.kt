package com.droidates.wallpapers.notifications

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.droidates.wallpapers.utils.SystemServiceCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Optimized utility class to handle notification permissions
 * with caching to avoid repeated permission checks
 */
object NotificationPermissionHandler {
    private const val TAG = "NotificationPermission"
    private const val PREF_NOTIFICATION_PERMISSION = "notification_permission_state"
    
    // OPTIMIZATION: Cache permission state to avoid repeated checks
    private var cachedPermissionState: Boolean? = null
    
    /**
     * Get the NotificationManager using SystemServiceCache for lazy initialization
     * This avoids repeated calls to getSystemService() and improves performance
     * @param context The context to get the NotificationManager from
     * @return The NotificationManager instance
     */
    fun getNotificationManager(context: Context): NotificationManager? {
        return SystemServiceCache.getInstance(context).getNotificationManager()
    }
    
    /**
     * Check if notification permission is granted
     * OPTIMIZATION: Uses cached permission state to avoid repeated system calls
     * @param context The context to check permissions with
     * @return true if notification permission is granted, false otherwise
     */
    fun hasNotificationPermission(context: Context): Boolean {
        // OPTIMIZATION: Return cached value if available to avoid system calls
        cachedPermissionState?.let { return it }
        
        // Check if we need to check for notification permission (Android 13+)
        val permissionRequired = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        
        // If permission is not required, return true
        if (!permissionRequired) {
            cachedPermissionState = true
            // Store in persistent storage
            saveCachedPermissionState(context, true)
            return true
        }
        
        // OPTIMIZATION: Try to get permission state from cached value only to avoid blocking
        // SharedPreferences access moved to async initialization to prevent main thread blocking
        // If no cached value, fall back to system permission check
        
        // If not in persistent storage, check system permission state
        val permissionState = context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        
        // Cache result to avoid repeated checks
        cachedPermissionState = permissionState
        // Store in persistent storage
        saveCachedPermissionState(context, permissionState)
        
        return permissionState
    }
    
    /**
     * Save the permission state to persistent storage
     * @param context The context to use for saving
     * @param state The permission state to save
     */
    private fun saveCachedPermissionState(context: Context, state: Boolean) {
        CoroutineScope(Dispatchers.IO).launch {
        try {
            val prefsManager = context.getSharedPreferences("notification_prefs", Context.MODE_PRIVATE)
            prefsManager.edit().putBoolean(PREF_NOTIFICATION_PERMISSION, state).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving permission state", e)
            }
        }
    }
    
    /**
     * Asynchronously check notification permission without blocking the main thread
     * This should be used during app startup instead of the synchronous version
     */
    fun checkNotificationPermissionAsync(activity: Activity) {
        // Skip for Android versions below 13 (TIRAMISU)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            // For older versions, just set the cached state to true and save it
            cachedPermissionState = true
            saveCachedPermissionState(activity, true)
            return
        }
        
        // OPTIMIZATION: Check if we already have cached permission state in memory
        if (cachedPermissionState != null) {
            return
        }
        
        // OPTIMIZATION: Check if we have the permission state in persistent storage (async)
        CoroutineScope(Dispatchers.IO).launch {
            try {
        val prefsManager = activity.getSharedPreferences("notification_prefs", Context.MODE_PRIVATE)
        if (prefsManager.contains(PREF_NOTIFICATION_PERMISSION)) {
            // Use stored permission state if available
                    val storedPermissionState = prefsManager.getBoolean(PREF_NOTIFICATION_PERMISSION, false)
                    cachedPermissionState = storedPermissionState
                    return@launch
                }
                
                // If not in storage, check system permission and cache it
                val systemPermissionState = ContextCompat.checkSelfPermission(
                    activity, 
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
                
                cachedPermissionState = systemPermissionState
                saveCachedPermissionState(activity, systemPermissionState)
            } catch (e: Exception) {
                Log.e(TAG, "Error during async permission check", e)
            }
        }
        
        // Use coroutine to check permission without blocking if not cached
        CoroutineScope(Dispatchers.IO).launch {
            val permissionState = activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            // Cache the result in memory
            cachedPermissionState = permissionState
            // Store in persistent storage
            saveCachedPermissionState(activity, permissionState)
            Log.d(TAG, "Async permission check completed: $permissionState")
        }
    }
    
    /**
     * Request notification permission if needed
     * OPTIMIZATION: Uses cached permission state and updates cache when permission changes
     */
    @Composable
    fun RequestNotificationPermissionIfNeeded(
        activity: Activity,
        onPermissionResult: (Boolean) -> Unit = {}
    ) {
        var permissionRequested by remember { mutableStateOf(false) }
        
        val permissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            // OPTIMIZATION: Update cached permission state when permission changes
            cachedPermissionState = isGranted
            Log.d(TAG, "Permission result received: $isGranted, updated cache")
            onPermissionResult(isGranted)
        }
        
        LaunchedEffect(Unit) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !permissionRequested) {
                // Use cached permission state if available
                val hasPermission = hasNotificationPermission(activity)
                
                if (!hasPermission) {
                    Log.d(TAG, "Requesting notification permission")
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    permissionRequested = true
                } else {
                    Log.d(TAG, "Notification permission already granted")
                    onPermissionResult(true)
                }
            } else {
                // Below Android 13, permission is automatically granted
                Log.d(TAG, "Notification permission not required for this Android version")
                onPermissionResult(true)
            }
        }
    }
    
    /**
     * Invalidate the cached permission state
     * Call this when you need to force a fresh permission check
     */
    fun invalidateCache() {
        Log.d(TAG, "Invalidating permission cache")
        cachedPermissionState = null
    }
} 