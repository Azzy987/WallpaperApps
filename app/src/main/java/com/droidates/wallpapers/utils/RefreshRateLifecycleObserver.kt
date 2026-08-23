package com.droidates.wallpapers.utils

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Log

/**
 * Lifecycle observer that applies the preferred refresh rate to each activity as it becomes visible.
 * This ensures that all activities in the app use the highest refresh rate available.
 */
class RefreshRateLifecycleObserver(private val refreshRateManager: RefreshRateManager) : 
    Application.ActivityLifecycleCallbacks {
    
    companion object {
        private const val TAG = "RefreshRateObserver"
    }
    
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        // No action needed
    }
    
    override fun onActivityStarted(activity: Activity) {
        // No action needed
    }
    
    override fun onActivityResumed(activity: Activity) {
        // Apply refresh rate settings when activity is resumed (becomes visible)
        Log.d(TAG, "Activity resumed: ${activity.javaClass.simpleName}")
        refreshRateManager.applyPreferredRefreshRate(activity.window)
    }
    
    override fun onActivityPaused(activity: Activity) {
        // No action needed
    }
    
    override fun onActivityStopped(activity: Activity) {
        // No action needed
    }
    
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
        // No action needed
    }
    
    override fun onActivityDestroyed(activity: Activity) {
        // No action needed
    }
}
