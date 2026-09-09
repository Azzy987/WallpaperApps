package com.droidates.pixel11wallpapers

import com.droidates.wallpapers.core.config.AppSpec

/**
 * Everything unique to Pixel 11 Wallpapers.
 *
 * This file plus the module's `build.gradle.kts`, launcher icons, onboarding art
 * and `google-services.json` entry are the ONLY app-specific pieces — all screens
 * and logic come from `:core`.
 */
object Pixel11Spec : AppSpec {

    override val appName = "Pixel 11 Wallpapers"
    override val toolbarTitle = "PIXEL 11 WALLPAPERS"
    override val homeSectionTitle = "Official Pixel Drops"
    override val onboardingTagline = "Your home for official Google & Pixel wallpapers."
    override val deviceFamilyName = "Pixel 11"

    // Rose, sampled from the launcher icon. The four values are one palette: accent and container are
    // the light/dark pair, and the two "on" colours must stay legible on them.
    override val brandAccent = 0xFF8E294B
    override val brandOnAccent = 0xFFFFFFFF
    override val brandAccentContainer = 0xFFEDDEE3
    override val brandOnAccentContainer = 0xFF2B0D17

    // ── Firestore ───────────────────────────────────────────────────────────
    // Home wallpapers are SHARED by all Pixel apps.
    override val supportsSeriesFilter = true
    override val supportsLaunchYearSort = true

    override val categoryBrandName = "Google"
    override val collectionHome = "Google"
    override val documentDevicesBrand = "Google"
    override val documentDevicesSecondary = "Android"
    override val seriesPrefixRanges = listOf(
        "Pixel" to "Pixem",
        "Android" to "Androie",
    )

    // Brand-level namespaces shared with future Pixel apps. These two intentionally
    // differ: banners live under Banners/GoogleWallpapers/… in Firestore, while user
    // records stay under Users/PixelWallpapers/….
    override val documentBannersApp = "GoogleWallpapers"
    override val documentUsersApp = "PixelWallpapers"
    // …but these must stay unique per app.
    override val collectionBannersSub = "Pixel11Banners"
    override val collectionUsersSub = "Pixel11AndroidUsers"
    override val documentAppUpdate = "pixel11wallpapers"

    // ── Local storage ───────────────────────────────────────────────────────
    override val prefsName = "pixel11_wallpapers_prefs"
    override val downloadFolderName = "Pixel11Wallpapers"
    // Brand-new app: no pre-:core table to rename.
    override val legacyFavoritesTable = "favorites"
    override val favoritesPrefsKey = "pixel11favorites"

    // ── Monetisation ────────────────────────────────────────────────────────
    override val billingLifetimeId = "pixel11lifetime"
    override val billingLifetimeFallbackPrice = "₹199"
    override val billingLifetimeComparePrice = "₹299"

    // Must match APPLICATION_ID in this module's AndroidManifest.xml.
    override val adMobAppId = "ca-app-pub-6427410984085546~1782204114"
    override val adBannerId = "ca-app-pub-6427410984085546/2234344269"
    override val adInterstitialId = "ca-app-pub-6427410984085546/9469122446"
    override val adRewardedId = "ca-app-pub-6427410984085546/4668935912"

    // ── Build info ──────────────────────────────────────────────────────────
    override val isDebug = BuildConfig.DEBUG
    override val versionName = BuildConfig.VERSION_NAME
    override val versionCode = BuildConfig.VERSION_CODE
    override val applicationId = BuildConfig.APPLICATION_ID
}
