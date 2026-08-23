package com.droidates.wallpapers.core.ui.theme

import android.app.ActivityManager
import android.content.Context
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        val scale = runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        }.getOrDefault(1f)
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val isLowRamDevice = activityManager?.isLowRamDevice == true
        scale == 0f || isLowRamDevice
    }
}
