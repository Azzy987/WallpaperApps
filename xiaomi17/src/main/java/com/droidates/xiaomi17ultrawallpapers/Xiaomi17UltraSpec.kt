package com.droidates.xiaomi17ultrawallpapers

import com.droidates.wallpapers.core.config.AppSpec

/** Everything unique to Xiaomi 17 Ultra Wallpapers. All screens and logic come from `:core`. */
object Xiaomi17UltraSpec : AppSpec {

    override val appName = "Xiaomi 18 Fold Wallpapers"
    override val toolbarTitle = "XIAOMI 18 FOLD WALLPAPERS"
    override val homeSectionTitle = "Official Xiaomi Drops"
    override val onboardingTagline = "Your home for official Xiaomi wallpapers."
    override val deviceFamilyName = "Xiaomi 18 Fold"

    override val brandAccent = 0xFFEF6C00
    override val brandOnAccent = 0xFFFFFFFF
    override val brandAccentContainer = 0xFFFFDCC2
    override val brandOnAccentContainer = 0xFF2E1500

    // ── Firestore ───────────────────────────────────────────────────────────
    override val supportsSeriesFilter = true
    override val supportsLaunchYearSort = true

    override val categoryBrandName = "Xiaomi"
    override val collectionHome = "Xiaomi"
    override val documentDevicesBrand = "Xiaomi"
    override val documentDevicesSecondary = "Android"
    override val seriesPrefixRanges = listOf("Xiaomi" to "Xiaomj")

    override val documentBannersApp = "XiaomiWallpapers"
    override val collectionBannersSub = "Xiaomi17UltraWallpapersBanners"
    override val documentUsersApp = "XiaomiWallpapers"
    override val collectionUsersSub = "Xiaomi17UltraAndroidUsers"
    override val documentAppUpdate = "xiaomi17ultrawallpapers"

    // ── Local storage ───────────────────────────────────────────────────────
    override val prefsName = "xiaomi17ultra_wallpapers_prefs"
    override val downloadFolderName = "Xiaomi17UltraWallpapers"
    override val favoritesPrefsKey = "xiaomi17ultrafavorites"
    // Pre-:core Room table. The v2->v3 migration renames it so existing users
    // keep their saved wallpapers on update.
    override val legacyFavoritesTable = "xiaomi17ultrafavorites"

    // ── Monetisation ────────────────────────────────────────────────────────
    override val billingLifetimeId = "xiaomi17ultralifetime"
    override val billingLifetimeFallbackPrice = "₹199"
    override val billingLifetimeComparePrice = "₹299"

    // Must match APPLICATION_ID in this module's AndroidManifest.xml.
    override val adMobAppId = "ca-app-pub-6427410984085546~2342842550"
    override val adBannerId = "ca-app-pub-6427410984085546/5873489769"
    override val adInterstitialId = "ca-app-pub-6427410984085546/5912052902"
    override val adRewardedId = "ca-app-pub-6427410984085546/8099933240"

    // ── Build info ──────────────────────────────────────────────────────────
    override val isDebug = BuildConfig.DEBUG
    override val versionName = BuildConfig.VERSION_NAME
    override val versionCode = BuildConfig.VERSION_CODE
    override val applicationId = BuildConfig.APPLICATION_ID
}
