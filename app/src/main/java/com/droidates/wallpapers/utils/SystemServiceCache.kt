package com.droidates.wallpapers.utils

import android.app.Activity
import android.app.ActivityManager
import android.app.NotificationManager
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.location.LocationManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.os.PowerManager
import android.telephony.TelephonyManager
import android.util.Log
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import androidx.annotation.MainThread
import androidx.core.content.ContextCompat
import java.lang.ref.WeakReference

/**
 * Utility class to cache system service instances to avoid repeated getSystemService() calls
 * This improves performance by reducing IPC calls to the system server
 */
class SystemServiceCache(context: Context) {
    
    private val TAG = "SystemServiceCache"
    
    // Use WeakReference to avoid memory leaks
    private val contextRef = WeakReference(context.applicationContext)
    
    // Cached system services
    private var connectivityManagerInstance: ConnectivityManager? = null
    private var notificationManagerInstance: NotificationManager? = null
    private var activityManagerInstance: ActivityManager? = null
    private var packageManagerInstance: PackageManager? = null
    private var powerManagerInstance: PowerManager? = null
    private var displayManagerInstance: DisplayManager? = null
    private var windowManagerInstance: WindowManager? = null
    private var audioManagerInstance: AudioManager? = null
    private var locationManagerInstance: LocationManager? = null
    private var telephonyManagerInstance: TelephonyManager? = null
    private var wifiManagerInstance: WifiManager? = null
    private var clipboardManagerInstance: ClipboardManager? = null
    private var inputMethodManagerInstance: InputMethodManager? = null
    
    /**
     * Get the ConnectivityManager instance
     * @return Cached ConnectivityManager instance
     */
    fun getConnectivityManager(): ConnectivityManager? {
        if (connectivityManagerInstance == null) {
            val context = contextRef.get() ?: return null
            try {
                connectivityManagerInstance = ContextCompat.getSystemService(
                    context,
                    ConnectivityManager::class.java
                )
                Log.d(TAG, "ConnectivityManager instance cached")
            } catch (e: Exception) {
                Log.e(TAG, "Error getting ConnectivityManager", e)
            }
        }
        return connectivityManagerInstance
    }
    
    /**
     * Get the NotificationManager instance
     * @return Cached NotificationManager instance
     */
    fun getNotificationManager(): NotificationManager? {
        if (notificationManagerInstance == null) {
            val context = contextRef.get() ?: return null
            try {
                notificationManagerInstance = ContextCompat.getSystemService(
                    context,
                    NotificationManager::class.java
                )
                Log.d(TAG, "NotificationManager instance cached")
            } catch (e: Exception) {
                Log.e(TAG, "Error getting NotificationManager", e)
            }
        }
        return notificationManagerInstance
    }
    
    /**
     * Get the ActivityManager instance
     * @return Cached ActivityManager instance
     */
    fun getActivityManager(): ActivityManager? {
        if (activityManagerInstance == null) {
            val context = contextRef.get() ?: return null
            try {
                activityManagerInstance = ContextCompat.getSystemService(
                    context,
                    ActivityManager::class.java
                )
                Log.d(TAG, "ActivityManager instance cached")
            } catch (e: Exception) {
                Log.e(TAG, "Error getting ActivityManager", e)
            }
        }
        return activityManagerInstance
    }
    
    /**
     * Get the PackageManager instance
     * @return Cached PackageManager instance
     */
    fun getPackageManager(): PackageManager? {
        if (packageManagerInstance == null) {
            val context = contextRef.get() ?: return null
            try {
                packageManagerInstance = context.packageManager
                Log.d(TAG, "PackageManager instance cached")
            } catch (e: Exception) {
                Log.e(TAG, "Error getting PackageManager", e)
            }
        }
        return packageManagerInstance
    }
    
    /**
     * Get the PowerManager instance
     * @return Cached PowerManager instance
     */
    fun getPowerManager(): PowerManager? {
        if (powerManagerInstance == null) {
            val context = contextRef.get() ?: return null
            try {
                powerManagerInstance = ContextCompat.getSystemService(
                    context,
                    PowerManager::class.java
                )
                Log.d(TAG, "PowerManager instance cached")
            } catch (e: Exception) {
                Log.e(TAG, "Error getting PowerManager", e)
            }
        }
        return powerManagerInstance
    }
    
    /**
     * Get the DisplayManager instance
     * @return Cached DisplayManager instance
     */
    fun getDisplayManager(): DisplayManager? {
        if (displayManagerInstance == null) {
            val context = contextRef.get() ?: return null
            try {
                displayManagerInstance = ContextCompat.getSystemService(
                    context,
                    DisplayManager::class.java
                )
                Log.d(TAG, "DisplayManager instance cached")
            } catch (e: Exception) {
                Log.e(TAG, "Error getting DisplayManager", e)
            }
        }
        return displayManagerInstance
    }
    
    /**
     * Get the WindowManager instance
     * @return Cached WindowManager instance
     */
    fun getWindowManager(): WindowManager? {
        if (windowManagerInstance == null) {
            val context = contextRef.get() ?: return null
            try {
                windowManagerInstance = ContextCompat.getSystemService(
                    context,
                    WindowManager::class.java
                )
                Log.d(TAG, "WindowManager instance cached")
            } catch (e: Exception) {
                Log.e(TAG, "Error getting WindowManager", e)
            }
        }
        return windowManagerInstance
    }
    
    /**
     * Get the AudioManager instance
     * @return Cached AudioManager instance
     */
    fun getAudioManager(): AudioManager? {
        if (audioManagerInstance == null) {
            val context = contextRef.get() ?: return null
            try {
                audioManagerInstance = ContextCompat.getSystemService(
                    context,
                    AudioManager::class.java
                )
                Log.d(TAG, "AudioManager instance cached")
            } catch (e: Exception) {
                Log.e(TAG, "Error getting AudioManager", e)
            }
        }
        return audioManagerInstance
    }
    
    /**
     * Get the LocationManager instance
     * @return Cached LocationManager instance
     */
    fun getLocationManager(): LocationManager? {
        if (locationManagerInstance == null) {
            val context = contextRef.get() ?: return null
            try {
                locationManagerInstance = ContextCompat.getSystemService(
                    context,
                    LocationManager::class.java
                )
                Log.d(TAG, "LocationManager instance cached")
            } catch (e: Exception) {
                Log.e(TAG, "Error getting LocationManager", e)
            }
        }
        return locationManagerInstance
    }
    
    /**
     * Get the TelephonyManager instance
     * @return Cached TelephonyManager instance
     */
    fun getTelephonyManager(): TelephonyManager? {
        if (telephonyManagerInstance == null) {
            val context = contextRef.get() ?: return null
            try {
                telephonyManagerInstance = ContextCompat.getSystemService(
                    context,
                    TelephonyManager::class.java
                )
                Log.d(TAG, "TelephonyManager instance cached")
            } catch (e: Exception) {
                Log.e(TAG, "Error getting TelephonyManager", e)
            }
        }
        return telephonyManagerInstance
    }
    
    /**
     * Get the WifiManager instance
     * @return Cached WifiManager instance
     */
    fun getWifiManager(): WifiManager? {
        if (wifiManagerInstance == null) {
            val context = contextRef.get() ?: return null
            try {
                wifiManagerInstance = ContextCompat.getSystemService(
                    context,
                    WifiManager::class.java
                )
                Log.d(TAG, "WifiManager instance cached")
            } catch (e: Exception) {
                Log.e(TAG, "Error getting WifiManager", e)
            }
        }
        return wifiManagerInstance
    }
    
    /**
     * Get the ClipboardManager instance
     * @return Cached ClipboardManager instance
     */
    fun getClipboardManager(): ClipboardManager? {
        if (clipboardManagerInstance == null) {
            val context = contextRef.get() ?: return null
            try {
                clipboardManagerInstance = ContextCompat.getSystemService(
                    context,
                    ClipboardManager::class.java
                )
                Log.d(TAG, "ClipboardManager instance cached")
            } catch (e: Exception) {
                Log.e(TAG, "Error getting ClipboardManager", e)
            }
        }
        return clipboardManagerInstance
    }
    
    /**
     * Get the InputMethodManager instance
     * @return Cached InputMethodManager instance
     */
    fun getInputMethodManager(): InputMethodManager? {
        if (inputMethodManagerInstance == null) {
            val context = contextRef.get() ?: return null
            try {
                inputMethodManagerInstance = ContextCompat.getSystemService(
                    context,
                    InputMethodManager::class.java
                )
                Log.d(TAG, "InputMethodManager instance cached")
            } catch (e: Exception) {
                Log.e(TAG, "Error getting InputMethodManager", e)
            }
        }
        return inputMethodManagerInstance
    }
    
    /**
     * Warm up the cache by pre-initializing commonly used system services
     */
    fun warmUpCache() {
        Log.d(TAG, "Warming up system service cache")
        try {
            // Pre-initialize the most commonly used services
            getConnectivityManager()
            getNotificationManager()
            getActivityManager()
        } catch (e: Exception) {
            Log.e(TAG, "Error warming up cache", e)
        }
    }
    
    /**
     * Clear all cached system service instances
     * Call this method when the app is under memory pressure
     */
    @MainThread
    fun clearCache() {
        Log.d(TAG, "Clearing system service cache")
        connectivityManagerInstance = null
        notificationManagerInstance = null
        activityManagerInstance = null
        packageManagerInstance = null
        powerManagerInstance = null
        displayManagerInstance = null
        windowManagerInstance = null
        audioManagerInstance = null
        locationManagerInstance = null
        telephonyManagerInstance = null
        wifiManagerInstance = null
        clipboardManagerInstance = null
        inputMethodManagerInstance = null
    }
    
    companion object {
        // Singleton instance
        @Volatile
        private var INSTANCE: SystemServiceCache? = null
        
        /**
         * Get the singleton instance of SystemServiceCache
         * @param context Context to use for getting system services
         * @return SystemServiceCache singleton instance
         */
        fun getInstance(context: Context): SystemServiceCache {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SystemServiceCache(context).also { INSTANCE = it }
            }
        }
    }
}
