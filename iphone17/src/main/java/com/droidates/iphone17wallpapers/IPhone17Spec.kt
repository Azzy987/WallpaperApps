package com.droidates.iphone17wallpapers

import com.droidates.wallpapers.core.config.AppSpec

/** Everything unique to iPhone 17 Wallpapers. All screens and logic come from `:core`. */
object IPhone17Spec : AppSpec {

    override val appName = "iPhone 17 Wallpapers"
    override val toolbarTitle = "IPHONE 17 WALLPAPERS"
    override val homeSectionTitle = "Official iPhone Drops"
    override val onboardingTagline = "Your home for official Apple & iPhone wallpapers."
    override val deviceFamilyName = "iPhone 17"

    override val brandAccent = 0xFFBF5000
    override val brandOnAccent = 0xFFFFFFFF
    override val brandAccentContainer = 0xFFFFDBC9
    override val brandOnAccentContainer = 0xFF3A1400

    // ── Firestore ───────────────────────────────────────────────────────────
    override val supportsSeriesFilter = true
    override val supportsLaunchYearSort = true

    override val categoryBrandName = "Apple"
    override val collectionHome = "Apple"
    override val documentDevicesBrand = "Apple"
    override val documentDevicesSecondary = "iOS"
    override val seriesPrefixRanges = listOf("iPhone" to "iQ", "iOS" to "iT")

    override val documentBannersApp = "iPhoneWallpapers"
    override val collectionBannersSub = "iPhone17Banners"
    override val documentUsersApp = "iPhoneWallpapers"
    override val collectionUsersSub = "iPhone17AndroidUsers"
    override val documentAppUpdate = "iphone17wallpapers"

    // ── Local storage ───────────────────────────────────────────────────────
    override val prefsName = "iphone17_wallpapers_prefs"
    override val downloadFolderName = "IPhone17Wallpapers"
    override val favoritesPrefsKey = "iphone17favorites"
    // Pre-:core Room table. The v2->v3 migration renames it so existing users
    // keep their saved wallpapers on update.
    override val legacyFavoritesTable = "iphone17favorites"

    // ── Monetisation ────────────────────────────────────────────────────────
    override val billingLifetimeId = "iphone17lifetime"
    override val billingLifetimeFallbackPrice = "₹199"
    override val billingLifetimeComparePrice = "₹299"

    // Must match APPLICATION_ID in this module's AndroidManifest.xml.
    override val adMobAppId = "ca-app-pub-6427410984085546~1729487126"
    override val adBannerId = "ca-app-pub-6427410984085546/4595754115"
    override val adInterstitialId = "ca-app-pub-6427410984085546/3636916421"
    override val adRewardedId = "ca-app-pub-6427410984085546/3445344735"

    // ── Build info ──────────────────────────────────────────────────────────
    override val isDebug = BuildConfig.DEBUG
    override val versionName = BuildConfig.VERSION_NAME
    override val versionCode = BuildConfig.VERSION_CODE
    override val applicationId = BuildConfig.APPLICATION_ID
}
