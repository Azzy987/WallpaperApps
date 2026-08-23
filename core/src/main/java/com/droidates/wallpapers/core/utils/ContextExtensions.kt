package com.droidates.wallpapers.core.utils

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

/**
 * Walks the ContextWrapper chain to find the hosting Activity, or null if there isn't one.
 *
 * Needed because Compose hands out a context that is usually a wrapper rather than the
 * Activity itself, while the ads SDK requires an Activity to show full-screen ads and to
 * register a banner for display.
 *
 * (DetailScreen.kt and WallpaperCarousel.kt each keep a private copy predating this file.)
 */
fun Context.findActivity(): Activity? {
    var currentContext = this
    while (currentContext is ContextWrapper) {
        if (currentContext is Activity) {
            return currentContext
        }
        currentContext = currentContext.baseContext
    }
    return null
}
