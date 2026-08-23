package com.droidates.wallpapers.core.utils

import android.app.Activity
import android.app.Application
import android.os.Bundle

/**
 * Cleaned no-op RefreshRateLifecycleObserver to eliminate activity-transition overhead.
 */
class RefreshRateLifecycleObserver(private val refreshRateManager: RefreshRateManager) : 
    Application.ActivityLifecycleCallbacks {
    
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    
    override fun onActivityStarted(activity: Activity) {}
    
    override fun onActivityResumed(activity: Activity) {
        // High refresh rate is already applied natively by MainActivity at startup.
        // No need to repeatedly make expensive display IPC queries here.
    }
    
    override fun onActivityPaused(activity: Activity) {}
    
    override fun onActivityStopped(activity: Activity) {}
    
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    
    override fun onActivityDestroyed(activity: Activity) {}
}
