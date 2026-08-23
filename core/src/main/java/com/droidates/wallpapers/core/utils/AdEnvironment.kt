package com.droidates.wallpapers.core.utils

import android.os.Build
import com.droidates.wallpapers.core.config.AppConfig

/**
 * Test ads are limited to debug builds on emulators only.
 * Real devices always use production ad unit IDs.
 */
object AdEnvironment {

    private const val TEST_BANNER_AD_UNIT_ID = "ca-app-pub-3940256099942544/9214589741"

    fun isEmulator(): Boolean {
        return Build.FINGERPRINT.contains("generic", ignoreCase = true) ||
            Build.FINGERPRINT.contains("unknown", ignoreCase = true) ||
            Build.MODEL.contains("google_sdk", ignoreCase = true) ||
            Build.MODEL.contains("Emulator", ignoreCase = true) ||
            Build.MODEL.contains("Android SDK built for x86", ignoreCase = true) ||
            Build.MANUFACTURER.contains("Genymotion", ignoreCase = true) ||
            (Build.BRAND.startsWith("generic", ignoreCase = true) &&
                Build.DEVICE.startsWith("generic", ignoreCase = true)) ||
            Build.PRODUCT == "google_sdk" ||
            Build.HARDWARE.contains("goldfish", ignoreCase = true) ||
            Build.HARDWARE.contains("ranchu", ignoreCase = true)
    }

    fun shouldUseTestAds(): Boolean = AppConfig.IS_DEBUG && isEmulator()

    fun bannerAdUnitId(): String {
        return if (shouldUseTestAds()) TEST_BANNER_AD_UNIT_ID else AppConfig.AD_BANNER_ID
    }
}
