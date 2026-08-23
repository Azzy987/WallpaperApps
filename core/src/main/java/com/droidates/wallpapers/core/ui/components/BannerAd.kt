package com.droidates.wallpapers.core.ui.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.droidates.wallpapers.core.utils.AdEnvironment
import com.droidates.wallpapers.core.utils.LocalAdManager
import com.droidates.wallpapers.core.utils.MobileAdsInitializer
import com.droidates.wallpapers.core.utils.findActivity
import com.google.android.libraries.ads.mobile.sdk.banner.AdSize
import com.google.android.libraries.ads.mobile.sdk.banner.AdView
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAd
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAdRequest
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError

/**
 * Anchored adaptive banner, hidden for premium users.
 *
 * GMA Next-Gen shape: the ad unit id and size go into [BannerAdRequest], and the loaded
 * [BannerAd] is registered into an [AdView] container — rather than the old
 * `AdView.adUnitId = …` + `setAdSize()` + `loadAd()` sequence. Registering needs an
 * Activity, so the view collapses to zero height if none is available.
 */
@Composable
fun BannerAd(
    modifier: Modifier = Modifier
) {
    val adManager = LocalAdManager.current
    val isPremiumUser by adManager.isPremiumUser.collectAsState()

    if (isPremiumUser) return

    val context = LocalContext.current
    val density = LocalDensity.current
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val adUnitId = AdEnvironment.bannerAdUnitId()
    // Two separate flags: [isAdLoaded] drives the top padding once an ad is actually
    // showing, while [adFailed] collapses the slot when no ad will ever arrive. The view
    // keeps its reserved height in between, which the SDK requires to render.
    var isAdLoaded by remember { mutableStateOf(false) }
    var adFailed by remember { mutableStateOf(false) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = if (isAdLoaded) 8.dp else 0.dp)
    ) {
        val adWidthDp = maxWidth.value.toInt().takeIf { it > 0 } ?: screenWidthDp
        val adSize = remember(context, adWidthDp) {
            AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, adWidthDp)
        }
        val adHeight = remember(adSize) {
            with(density) { adSize.getHeightInPixels(context).toDp() }
        }

        // Holds the container so the load effect below can attach the ad once it arrives.
        val adViewRef = remember { mutableStateOf<AdView?>(null) }
        val adView = adViewRef.value

        // Load only AFTER the SDK is initialized. The Next-Gen SDK throws
        // "MobileAds.initialize must be called before using the Google Mobile Ads SDK"
        // instead of tolerating an early request like the old one did — and init is
        // deliberately deferred to a background worker, so the first composition of this
        // banner almost always beats it.
        LaunchedEffect(adView, adUnitId, adSize) {
            val view = adView ?: return@LaunchedEffect
            val activity = view.context.findActivity()
            if (activity == null) {
                adFailed = true
                return@LaunchedEffect
            }

            MobileAdsInitializer.ensureInitialized(view.context.applicationContext)

            val request = BannerAdRequest.Builder(adUnitId, adSize).build()
            BannerAd.load(
                request,
                object : AdLoadCallback<BannerAd> {
                    override fun onAdLoaded(ad: BannerAd) {
                        // Callbacks arrive on a background dispatcher — unlike the old
                        // main-thread AdListener. registerBannerAd() attaches a WebView to
                        // the hierarchy, so calling it here crashes with
                        // "Calling View methods on another thread than the UI thread".
                        view.post {
                            view.registerBannerAd(ad, activity)
                            isAdLoaded = true
                        }
                    }

                    override fun onAdFailedToLoad(adError: LoadAdError) {
                        view.post {
                            isAdLoaded = false
                            adFailed = true
                        }
                    }
                }
            )
        }

        AndroidView(
            // The view must already have its full height when registerBannerAd() runs, or
            // the SDK logs "Not enough space to show the full ad … only have 426x0 dp" and
            // renders nothing. Reserve the height as soon as an ad is on its way, and only
            // collapse back to 0 if the load actually fails.
            modifier = Modifier
                .fillMaxWidth()
                .height(if (adFailed) 0.dp else adHeight),
            factory = { viewContext ->
                AdView(viewContext).also { adViewRef.value = it }
            },
            onRelease = { view ->
                adViewRef.value = null
                view.destroy()
            }
        )
    }
}
