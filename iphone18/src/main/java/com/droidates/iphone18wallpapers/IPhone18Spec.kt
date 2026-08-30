package com.droidates.iphone18wallpapers

import com.droidates.wallpapers.core.config.AppSpec

/**
 * Everything unique to iPhone Fold Wallpapers.
 *
 * Compare against `Pixel11Spec` — the two apps differ ONLY in this file, their
 * `build.gradle.kts`, icons/onboarding art and Play/AdMob identifiers. All screens
 * and logic are shared from `:core`.
 */
object IPhone18Spec : AppSpec {

    override val appName = "iPhone Fold Wallpapers"
    override val toolbarTitle = "IPHONE FOLD WALLPAPERS"
    override val homeSectionTitle = "Official iPhone Drops"
    override val onboardingTagline = "Your home for official Apple & iPhone wallpapers."
    override val deviceFamilyName = "iPhone Fold"

    // Original Droidates orange accent.
    // Material Blue. The four values are one palette, not independent colours: accent
    // and container are the light/dark pair, and the two "on" colours must stay legible
    // on top of them — so changing the accent alone would leave dark-orange text sitting
    // on a blue container.
    override val brandAccent = 0xFF1976D2          // Blue 700
    override val brandOnAccent = 0xFFFFFFFF
    override val brandAccentContainer = 0xFFD1E4FF // light blue surface
    override val brandOnAccentContainer = 0xFF001C38

    // ── Firestore ───────────────────────────────────────────────────────────
    // Apple-brand wallpapers, shared with the other iPhone/iOS apps.
    override val categoryBrandName = "Apple"
    override val collectionHome = "Apple"
    override val documentDevicesBrand = "Apple"
    override val documentDevicesSecondary = "iOS"
    override val seriesPrefixRanges = listOf(
        "iPhone" to "iQ",
        "iOS" to "iT",
    )

    override val documentBannersApp = "iPhoneWallpapers"
    override val documentUsersApp = "iPhoneWallpapers"
    override val collectionBannersSub = "iPhone18Banners"
    override val collectionUsersSub = "iPhone18AndroidUsers"
    override val documentAppUpdate = "iphone18wallpapers"

    // ── Local storage ───────────────────────────────────────────────────────
    override val prefsName = "iphone18_wallpapers_prefs"
    override val downloadFolderName = "IPhone18Wallpapers"
    // Brand-new app: no pre-:core table to rename.
    override val legacyFavoritesTable = "favorites"
    override val favoritesPrefsKey = "iphone18favorites"

    // ── Monetisation ────────────────────────────────────────────────────────
    override val billingLifetimeId = "iphone18lifetime"
    override val billingLifetimeFallbackPrice = "₹199"
    override val billingLifetimeComparePrice = "₹299"

    // Real AdMob units for this app (publisher 6427410984085546). Supplied to the SDK
    // through AppSpec — the GMA Next-Gen migration removed the manifest meta-data.
    override val adMobAppId = "ca-app-pub-6427410984085546~9795223799"
    override val adBannerId = "ca-app-pub-6427410984085546/6370984106"
    override val adInterstitialId = "ca-app-pub-6427410984085546/5057902430"
    override val adRewardedId = "ca-app-pub-6427410984085546/3293344299"

    // ── Build info ──────────────────────────────────────────────────────────
    override val isDebug = BuildConfig.DEBUG
    override val versionName = BuildConfig.VERSION_NAME
    override val versionCode = BuildConfig.VERSION_CODE
    override val applicationId = BuildConfig.APPLICATION_ID
}
