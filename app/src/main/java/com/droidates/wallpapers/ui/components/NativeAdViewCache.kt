package com.droidates.wallpapers.ui.components

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import java.util.concurrent.ConcurrentHashMap

/**
 * Singleton cache for AndroidView instances to prevent recreation during scroll
 * This eliminates the expensive WebView/MediaCodec recreation that causes lag
 */
object NativeAdViewCache {
    private val viewCache = ConcurrentHashMap<String, NativeAdView>()
    private val maxCacheSize = 10

    fun getOrCreateView(
        adKey: String,
        context: Context,
        nativeAd: NativeAd,
        factory: () -> NativeAdView
    ): NativeAdView {
        return viewCache[adKey] ?: run {
            // Clean cache if too large
            if (viewCache.size >= maxCacheSize) {
                val oldestKey = viewCache.keys.first()
                viewCache.remove(oldestKey)?.let { oldView ->
                    // Clean up the old view
                    oldView.removeAllViews()
                }
            }

            val newView = factory()
            viewCache[adKey] = newView
            newView
        }
    }

    fun removeView(adKey: String) {
        viewCache.remove(adKey)?.let { view ->
            view.removeAllViews()
        }
    }

    fun clearCache() {
        viewCache.values.forEach { view ->
            view.removeAllViews()
        }
        viewCache.clear()
    }
}

/**
 * Cached AndroidView composable that prevents recreation
 */
@Composable
fun CachedAndroidView(
    adKey: String,
    nativeAd: NativeAd,
    factory: (Context) -> NativeAdView,
    update: (NativeAdView) -> Unit = {}
) {
    val context = LocalContext.current

    // Remember the view instance to prevent recreation
    val view = remember(adKey) {
        NativeAdViewCache.getOrCreateView(adKey, context, nativeAd) {
            factory(context)
        }
    }

    // Apply any updates
    update(view)
}