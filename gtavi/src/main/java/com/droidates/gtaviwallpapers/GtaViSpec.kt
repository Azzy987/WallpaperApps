package com.droidates.gtaviwallpapers

import com.droidates.wallpapers.core.config.AppSpec

/**
 * Everything unique to GTA VI Wallpapers. All screens and logic come from `:core`.
 *
 * Unlike the sibling apps this is a *game* rather than a device brand, so it owns
 * its Firestore namespace outright instead of sharing a manufacturer collection:
 * there is no second GTA app to share `documentBannersApp` / `documentUsersApp`
 * with, and no device family behind `Devices/GTA`.
 */
object GtaViSpec : AppSpec {

    override val appName = "GTA VI Wallpapers"
    override val toolbarTitle = "GTA VI WALLPAPERS"
    override val homeSectionTitle = "Vice City Drops"
    override val onboardingTagline = "Your home for GTA VI wallpapers."
    override val deviceFamilyName = "GTA VI"

    // Vice City neon pink/magenta.
    override val brandAccent = 0xFFD81B7A
    override val brandOnAccent = 0xFFFFFFFF
    override val brandAccentContainer = 0xFFFFD8E8
    override val brandOnAccentContainer = 0xFF3D0022

    // ── Firestore ───────────────────────────────────────────────────────────
    // GTA VI wallpapers carry no `launchYear` field, and Firestore's orderBy drops
    // documents that lack the ordered field — sorting by it returned an empty Home tab.
    // Also hides the Release Date option, which is meaningless for a game.
    override val supportsLaunchYearSort = false

    override val categoryBrandName = "GTAVI"
    override val collectionHome = "GTAVI"
    override val documentDevicesBrand = "GTAVI"
    // The chips merge a second Devices doc; "Android" is the shared cross-app list.
    override val documentDevicesSecondary = "Android"
    // Firestore range is [first, second) — bump the last character of the prefix.
    //
    // No wallpaper in the GTAVI collection carries a `series` field today, so this scan
    // returns nothing and CategoryViewModel falls back to a lone "All Series" chip —
    // which is the correct look for an app with no sub-series. The range is kept rather
    // than emptied so that adding `series: "GTAVI …"` later starts populating chips with
    // no code change; it costs one prefix query that matches zero documents.
    override val seriesPrefixRanges = listOf("GTA" to "GTB")

    // Live path is Banners/GTAVI/GTAVIBanners.
    override val documentBannersApp = "GTAVI"
    override val collectionBannersSub = "GTAVIBanners"
    override val documentUsersApp = "GTAWallpapers"
    override val collectionUsersSub = "GtaViAndroidUsers"
    override val documentAppUpdate = "GtaViWallpapers"

    // ── Local storage ───────────────────────────────────────────────────────
    override val prefsName = "gtavi_wallpapers_prefs"
    override val downloadFolderName = "GtaViWallpapers"
    override val favoritesPrefsKey = "gtavifavorites"
    // Brand-new app: no pre-:core install exists, so there is no legacy table to
    // rename. "favorites" is the shared table :core already uses.
    override val legacyFavoritesTable = "favorites"

    // ── Monetisation ────────────────────────────────────────────────────────
    override val billingLifetimeId = "gtavilifetime"
    override val billingLifetimeFallbackPrice = "₹199"
    override val billingLifetimeComparePrice = "₹299"

    // Must match APPLICATION_ID in this module's AndroidManifest.xml.
    override val adMobAppId = "ca-app-pub-6427410984085546~2184327085"
    override val adBannerId = "ca-app-pub-6427410984085546/1980262625"
    override val adInterstitialId = "ca-app-pub-6427410984085546/4909678292"
    override val adRewardedId = "ca-app-pub-6427410984085546/4618918738"

    // ── Build info ──────────────────────────────────────────────────────────
    override val isDebug = BuildConfig.DEBUG
    override val versionName = BuildConfig.VERSION_NAME
    override val versionCode = BuildConfig.VERSION_CODE
    override val applicationId = BuildConfig.APPLICATION_ID
}
