package com.droidates.wallpapers.core.ui.components

import android.os.Bundle
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
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAdRefreshCallback
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAdRequest
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError

/**
 * Anchored adaptive banner, hidden for premium users.
 *
 * Loads through [AdView.loadAd] rather than the standalone `BannerAd.load()` +
 * `registerBannerAd()` pair. That distinction is the whole reason auto-refresh works:
 * `loadAd` hands the request to the view that will host it, so the SDK owns the ad's
 * refresh cycle and honours the refresh rate configured on the ad unit in the AdMob UI.
 * Loading the ad detached and registering it afterwards leaves the SDK with no view to
 * drive, and the ad never refreshes no matter what the console says.
 *
 * Refresh costs no main-thread work of ours: the SDK swaps the creative inside the
 * existing WebView on its own schedule, the view's measured height never changes (the
 * size is fixed at request time), and no recomposition is triggered — the refresh
 * callback deliberately touches no Compose state.
 *
 * The request asks for a **collapsible** banner. The first fill may render expanded with
 * a close button that collapses it back to the anchored size, which lifts CPM because the
 * larger creative is worth more. Three properties make this safe to enable blindly:
 * collapsing is always the user's own tap, the collapsed state is the same height this
 * slot already reserves, and the SDK deliberately drops the collapsible flag on every
 * auto-refresh after the first — so a user browsing one screen is never re-expanded on.
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

        // Holds the container so the load effect below can drive it once it exists.
        val adViewRef = remember { mutableStateOf<AdView?>(null) }
        val adView = adViewRef.value

        // Load only AFTER the SDK is initialized. The Next-Gen SDK throws
        // "MobileAds.initialize must be called before using the Google Mobile Ads SDK"
        // instead of tolerating an early request like the old one did — and init is
        // deliberately deferred to a background worker, so the first composition of this
        // banner almost always beats it.
        LaunchedEffect(adView, adUnitId, adSize) {
            val view = adView ?: return@LaunchedEffect
            if (view.context.findActivity() == null) {
                adFailed = true
                return@LaunchedEffect
            }

            MobileAdsInitializer.ensureInitialized(view.context.applicationContext)

            // "bottom" anchors the expanded creative to the bottom edge, matching where
            // both callers pin this composable. Using "top" here would have the ad expand
            // upward away from its own slot and overlay the content the user is browsing.
            //
            // Google demand only: a mediated fill (Meta, Unity) ignores the flag and
            // renders a standard banner, which is the normal outcome and not an error.
            val collapsibleExtras = Bundle().apply { putString("collapsible", "bottom") }
            val request = BannerAdRequest.Builder(adUnitId, adSize)
                .setGoogleExtrasBundle(collapsibleExtras)
                .build()
            // loadAd() must run on the UI thread — it attaches the ad's WebView to this
            // view as soon as the load resolves.
            view.post {
                view.loadAd(
                    request,
                    object : AdLoadCallback<BannerAd> {
                        override fun onAdLoaded(ad: BannerAd) {
                            // Callbacks arrive on a background dispatcher, unlike the old
                            // main-thread AdListener, and this one flips Compose state.
                            view.post { isAdLoaded = true }

                            // Refresh happens inside the SDK; this callback exists only so
                            // a failed refresh doesn't silently leave a blank slot. It
                            // must stay free of Compose state writes — a refresh every 60s
                            // that recomposed the screen would be exactly the jank this
                            // implementation is meant to avoid.
                            ad.bannerAdRefreshCallback = object : BannerAdRefreshCallback {
                                override fun onAdFailedToRefresh(error: LoadAdError) {
                                    // Keep the currently displayed creative and the
                                    // reserved height. The SDK retries on its own cadence,
                                    // so collapsing here would make the layout jump for a
                                    // transient network blip.
                                }
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
        }

        // No lifecycle pause/resume hook is wired here on purpose. This SDK's AdView
        // exposes no pause/resume/visibility API (only load, resize, register, destroy) —
        // it tracks on-screen visibility itself and, per the ad unit refresh contract,
        // only refreshes while the banner is actually visible. A backgrounded screen
        // therefore stops refreshing without our help, and calling the inherited View
        // visibility methods by hand would do nothing but look like it worked.
        AndroidView(
            // The view must already have its full height when the ad attaches, or the SDK
            // logs "Not enough space to show the full ad … only have 426x0 dp" and renders
            // nothing. Reserve the height as soon as an ad is on its way, and only collapse
            // back to 0 if the load actually fails.
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
