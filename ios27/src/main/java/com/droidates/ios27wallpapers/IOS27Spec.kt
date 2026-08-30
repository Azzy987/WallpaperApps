package com.droidates.ios27wallpapers

import com.droidates.wallpapers.core.config.AppSpec

/** Everything unique to iOS 27 Wallpapers. All screens and logic come from `:core`. */
object IOS27Spec : AppSpec {

    override val appName = "iOS 27 Wallpapers"
    override val toolbarTitle = "IOS 27 WALLPAPERS"
    override val homeSectionTitle = "Official Apple Drops"
    override val onboardingTagline = "Your home for official Apple & iOS wallpapers."
    override val deviceFamilyName = "iOS 27"

    override val brandAccent = 0xFF37474F
    override val brandOnAccent = 0xFFFFFFFF
    override val brandAccentContainer = 0xFFD7E3EA
    override val brandOnAccentContainer = 0xFF0B1B22

    // ── Firestore ───────────────────────────────────────────────────────────
    override val supportsSeriesFilter = true
    override val supportsLaunchYearSort = true

    override val categoryBrandName = "Apple"
    override val collectionHome = "Apple"
    override val documentDevicesBrand = "Apple"
    override val documentDevicesSecondary = "iOS"
    override val seriesPrefixRanges = listOf("iPhone" to "iQ", "iOS" to "iT")

    override val documentBannersApp = "iPhoneWallpapers"
    override val collectionBannersSub = "iOS27Banners"
    override val documentUsersApp = "iOS27Wallpapers"
    override val collectionUsersSub = "iOS27AndroidUsers"
    override val documentAppUpdate = "ios27wallpapers"

    // ── Local storage ───────────────────────────────────────────────────────
    override val prefsName = "ios27_wallpapers_prefs"
    override val downloadFolderName = "IOS27Wallpapers"
    override val favoritesPrefsKey = "ios27favorites"
    // Pre-:core Room table. The v2->v3 migration renames it so existing users
    // keep their saved wallpapers on update.
    override val legacyFavoritesTable = "ios27favorites"

    // ── Monetisation ────────────────────────────────────────────────────────
    override val billingLifetimeId = "ios27lifetime"
    override val billingLifetimeFallbackPrice = "₹199"
    override val billingLifetimeComparePrice = "₹299"

    // iOS 27's own live banner unit. (4595754115 is iPhone 17's — don't reuse it here.)
    // Must match APPLICATION_ID in this module's AndroidManifest.xml.
    override val adMobAppId = "ca-app-pub-6427410984085546~9564360369"
    override val adBannerId = "ca-app-pub-6427410984085546/5625115352"
    override val adInterstitialId = "ca-app-pub-6427410984085546/9200564133"
    override val adRewardedId = "ca-app-pub-6427410984085546/6514839431"

    // ── Build info ──────────────────────────────────────────────────────────
    override val isDebug = BuildConfig.DEBUG
    override val versionName = BuildConfig.VERSION_NAME
    override val versionCode = BuildConfig.VERSION_CODE
    override val applicationId = BuildConfig.APPLICATION_ID
}
