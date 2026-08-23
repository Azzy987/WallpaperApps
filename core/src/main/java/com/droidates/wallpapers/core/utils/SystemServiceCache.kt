package com.droidates.wallpapers.core.utils

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
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import androidx.core.content.ContextCompat

/**
 * Cleaned and optimized SystemServiceCache that safely delegates to standard Android
 * system service fetchers. Eliminates locking, weak reference polling, and diagnostic logging overhead.
 */
class SystemServiceCache(context: Context) {
    
    private val appContext = context.applicationContext
    
    fun getConnectivityManager(): ConnectivityManager? = 
        ContextCompat.getSystemService(appContext, ConnectivityManager::class.java)
        
    fun getNotificationManager(): NotificationManager? = 
        ContextCompat.getSystemService(appContext, NotificationManager::class.java)
        
    fun getActivityManager(): ActivityManager? = 
        ContextCompat.getSystemService(appContext, ActivityManager::class.java)
        
    fun getPackageManager(): PackageManager? = 
        appContext.packageManager
        
    fun getPowerManager(): PowerManager? = 
        ContextCompat.getSystemService(appContext, PowerManager::class.java)
        
    fun getDisplayManager(): DisplayManager? = 
        ContextCompat.getSystemService(appContext, DisplayManager::class.java)
        
    fun getWindowManager(): WindowManager? = 
        ContextCompat.getSystemService(appContext, WindowManager::class.java)
        
    fun getAudioManager(): AudioManager? = 
        ContextCompat.getSystemService(appContext, AudioManager::class.java)
        
    fun getLocationManager(): LocationManager? = 
        ContextCompat.getSystemService(appContext, LocationManager::class.java)
        
    fun getTelephonyManager(): TelephonyManager? = 
        ContextCompat.getSystemService(appContext, TelephonyManager::class.java)
        
    fun getWifiManager(): WifiManager? = 
        ContextCompat.getSystemService(appContext, WifiManager::class.java)
        
    fun getClipboardManager(): ClipboardManager? = 
        ContextCompat.getSystemService(appContext, ClipboardManager::class.java)
        
    fun getInputMethodManager(): InputMethodManager? = 
        ContextCompat.getSystemService(appContext, InputMethodManager::class.java)
        
    fun warmUpCache() {
        // No-op - caching is managed efficiently by the Android system framework
    }
    
    fun clearCache() {
        // No-op
    }
    
    companion object {
        @Volatile
        private var INSTANCE: SystemServiceCache? = null
        
        fun getInstance(context: Context): SystemServiceCache {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SystemServiceCache(context).also { INSTANCE = it }
            }
        }
    }
}
