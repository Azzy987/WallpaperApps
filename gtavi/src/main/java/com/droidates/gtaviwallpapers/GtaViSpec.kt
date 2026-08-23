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
    override val categoryBrandName = "GTA"
    override val collectionHome = "GTA"
    override val documentDevicesBrand = "GTA"
    // The chips merge a second Devices doc; "Android" is the shared cross-app list.
    override val documentDevicesSecondary = "Android"
    // Firestore range is [first, second) — bump the last character of the prefix.
    // Wallpapers must carry a `series` starting with "GTA" (e.g. "GTA VI") or the
    // filter chips silently come back empty.
    override val seriesPrefixRanges = listOf("GTA" to "GTB")

    override val documentBannersApp = "GTAWallpapers"
    override val collectionBannersSub = "GtaViBanners"
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
    override val billingLifetimeFallbackPrice = "₹299"
    override val billingLifetimeComparePrice = "₹399"

    // TODO(release): replace with this app's own AdMob ids before publishing.
    // These are Google's public TEST ids — they serve test ads and earn nothing.
    // tools/check_release_ready.py fails the build-readiness check while they are
    // here, so this cannot ship by accident.
    override val adMobAppId = "ca-app-pub-3940256099942544~3347511713"
    override val adBannerId = "ca-app-pub-3940256099942544/6300978111"
    override val adInterstitialId = "ca-app-pub-3940256099942544/1033173712"
    override val adRewardedId = "ca-app-pub-3940256099942544/5224354917"

    // ── Build info ──────────────────────────────────────────────────────────
    override val isDebug = BuildConfig.DEBUG
    override val versionName = BuildConfig.VERSION_NAME
    override val versionCode = BuildConfig.VERSION_CODE
    override val applicationId = BuildConfig.APPLICATION_ID
}
