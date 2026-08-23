package com.droidates.wallpapers.core.utils

import com.droidates.wallpapers.core.config.AppConfig
import android.content.Context
import android.util.Log
import com.google.android.libraries.ads.mobile.sdk.MobileAds
import com.google.android.libraries.ads.mobile.sdk.common.RequestConfiguration
import com.google.android.libraries.ads.mobile.sdk.initialization.InitializationConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * Single entry point for GMA Next-Gen SDK initialization on a background thread.
 *
 * Migrated from `com.google.android.gms:play-services-ads` (22.6.0). Two differences that
 * matter:
 *
 *  1. The AdMob **App ID** is passed in code via [InitializationConfig], not read from
 *     `com.google.android.gms.ads.APPLICATION_ID` manifest meta-data. It comes from the
 *     installed AppSpec so each app initializes with its own ID.
 *  2. [RequestConfiguration] is set through the init config rather than a separate
 *     `MobileAds.setRequestConfiguration` call before initialize().
 *
 * The old OPTIMIZE_INITIALIZATION / OPTIMIZE_AD_LOADING manifest flags no longer apply —
 * the Next-Gen SDK does its own off-main-thread init — but initializing from a worker
 * keeps startup off the critical path regardless.
 */
object MobileAdsInitializer {

    private const val TAG = "MobileAdsInitializer"

    private val initMutex = Mutex()

    @Volatile
    private var initialized = false

    /**
     * True only when the SDK itself reports being initialized.
     *
     * Ask [MobileAds] rather than trusting our own flag: the Next-Gen SDK *throws*
     * ("MobileAds.initialize must be called before using the Google Mobile Ads SDK")
     * if an ad is requested too early, where the old SDK quietly tolerated it. Callers
     * use this to decide whether loading an ad is safe at all.
     */
    fun isInitialized(): Boolean = initialized && MobileAds.isInitialized

    fun markInitialized() {
        initialized = true
    }

    suspend fun ensureInitialized(context: Context) {
        if (initialized) return
        initMutex.withLock {
            if (initialized) return
            withContext(Dispatchers.IO) {
                // The Next-Gen SDK recognises emulators as test devices on its own — there is
                // no DEVICE_ID_EMULATOR constant to pass any more, and setTestDeviceIds() with
                // an empty list would be a no-op. AdEnvironment still decides whether test ad
                // UNITS are used (debug + emulator only), which is the part that matters.
                //
                // Being a test device also turns on the SDK's Ad Inspector, which logs every ad
                // request/response under the GoogleMobileAdsNetwork tag. That is emulator-only
                // noise; release builds on real devices are silent.
                val requestConfiguration = RequestConfiguration.Builder().build()

                val config = InitializationConfig.Builder(AppConfig.ADMOB_APP_ID)
                    .setRequestConfiguration(requestConfiguration)
                    // No mediation adapters are bundled, so skip discovering them.
                    .disableMediationAdapterInitialization()
                    .build()

                suspendCancellableCoroutine { continuation ->
                    MobileAds.initialize(context.applicationContext, config) { initStatus ->
                        if (AppConfig.IS_DEBUG) {
                            Log.d(
                                TAG,
                                "MobileAds initialized in ${initStatus.totalLatency}ms " +
                                    "(${initStatus.adapterStatusMap.size} adapters)"
                            )
                        }
                        if (continuation.isActive) {
                            continuation.resume(Unit)
                        }
                    }
                }
                initialized = true
            }
        }
    }
}
