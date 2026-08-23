package com.droidates.s26ultrawallpapers

import com.droidates.wallpapers.core.config.AppSpec

/** Everything unique to S26 Ultra Wallpapers. All screens and logic come from `:core`. */
object S26UltraSpec : AppSpec {

    override val appName = "S26 Ultra Wallpapers"
    override val toolbarTitle = "S26 ULTRA WALLPAPERS"
    override val homeSectionTitle = "Official Samsung Drops"
    override val onboardingTagline = "Your home for official Samsung wallpapers."
    override val deviceFamilyName = "S26 Ultra"

    override val brandAccent = 0xFF1565C0
    override val brandOnAccent = 0xFFFFFFFF
    override val brandAccentContainer = 0xFFD6E3FF
    override val brandOnAccentContainer = 0xFF001B3D

    // ── Firestore ───────────────────────────────────────────────────────────
    override val categoryBrandName = "Samsung"
    override val collectionHome = "Samsung"
    override val documentDevicesBrand = "Samsung"
    override val documentDevicesSecondary = "Android"
    override val seriesPrefixRanges = listOf("Galaxy" to "Galaxz")

    override val documentBannersApp = "SamsungWallpapers"
    override val collectionBannersSub = "S26UltraWallpapersBanners"
    override val documentUsersApp = "SamsungWallpapers"
    override val collectionUsersSub = "S26UltraAndroidUsers"
    override val documentAppUpdate = "S26UltraWallpapers"

    // ── Local storage ───────────────────────────────────────────────────────
    override val prefsName = "s26ultra_wallpapers_prefs"
    override val downloadFolderName = "S26UltraWallpapers"
    override val favoritesPrefsKey = "s26ultrafavorites"
    // Pre-:core Room table. The v2->v3 migration renames it so existing users
    // keep their saved wallpapers on update.
    override val legacyFavoritesTable = "s26ultrafavorites"

    // ── Monetisation ────────────────────────────────────────────────────────
    override val billingLifetimeId = "s26ultralifetime"
    override val billingLifetimeFallbackPrice = "₹299"
    override val billingLifetimeComparePrice = "₹399"

    // Must match APPLICATION_ID in this module's AndroidManifest.xml.
    override val adMobAppId = "ca-app-pub-6427410984085546~3321902833"
    override val adBannerId = "ca-app-pub-6427410984085546/7186571433"
    override val adInterstitialId = "ca-app-pub-6427410984085546/3948588790"
    override val adRewardedId = "ca-app-pub-6427410984085546/7907180954"

    // ── Build info ──────────────────────────────────────────────────────────
    override val isDebug = BuildConfig.DEBUG
    override val versionName = BuildConfig.VERSION_NAME
    override val versionCode = BuildConfig.VERSION_CODE
    override val applicationId = BuildConfig.APPLICATION_ID
}
