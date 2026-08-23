package com.droidates.iphone18wallpapers

import com.droidates.wallpapers.core.config.AppSpec

/**
 * Everything unique to iPhone 18 Wallpapers.
 *
 * Compare against `Pixel11Spec` — the two apps differ ONLY in this file, their
 * `build.gradle.kts`, icons/onboarding art and Play/AdMob identifiers. All screens
 * and logic are shared from `:core`.
 */
object IPhone18Spec : AppSpec {

    override val appName = "iPhone 18 Wallpapers"
    override val toolbarTitle = "IPHONE 18 WALLPAPERS"
    override val homeSectionTitle = "Official iPhone Drops"
    override val onboardingTagline = "Your home for official Apple & iPhone wallpapers."
    override val deviceFamilyName = "iPhone 18"

    // Original Droidates orange accent.
    override val brandAccent = 0xFFBF5000
    override val brandOnAccent = 0xFFFFFFFF
    override val brandAccentContainer = 0xFFFFDBC9
    override val brandOnAccentContainer = 0xFF3A1400

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

    // TODO(iphone18): replace with the real unit IDs from a NEW AdMob app, and
    //  update the App ID in this module's AndroidManifest.xml. These are Google's
    //  official TEST units — they earn nothing and must not ship.
    // Must match APPLICATION_ID in this module's AndroidManifest.xml.
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
