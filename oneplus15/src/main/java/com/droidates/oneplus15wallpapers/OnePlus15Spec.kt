package com.droidates.oneplus15wallpapers

import com.droidates.wallpapers.core.config.AppSpec

/** Everything unique to OnePlus 15 Wallpapers. All screens and logic come from `:core`. */
object OnePlus15Spec : AppSpec {

    override val appName = "OnePlus 15 Wallpapers"
    override val toolbarTitle = "ONEPLUS 15 WALLPAPERS"
    override val homeSectionTitle = "Official OnePlus Drops"
    override val onboardingTagline = "Your home for official OnePlus wallpapers."
    override val deviceFamilyName = "OnePlus 15"

    // TODO(release): set to match the launcher icon / onboarding artwork. Placeholder
    // is OnePlus red, shifted off :oneplus7's #C62828 so the two apps are not identical.
    override val brandAccent = 0xFFB71C1C
    override val brandOnAccent = 0xFFFFFFFF
    override val brandAccentContainer = 0xFFFFDAD5
    override val brandOnAccentContainer = 0xFF410001

    override val supportsSeriesFilter = true
    override val supportsLaunchYearSort = true

    // ── Firestore ───────────────────────────────────────────────────────────
    // Brand-level values are SHARED with :oneplus7 on purpose — both apps read the same
    // OnePlus wallpaper catalogue. Only the per-app sub-collections below differ.
    override val categoryBrandName = "OnePlus"
    override val collectionHome = "OnePlus"
    override val documentDevicesBrand = "OnePlus"
    override val documentDevicesSecondary = "Android"
    override val seriesPrefixRanges = listOf("OnePlus" to "OnePlut")

    override val documentBannersApp = "OnePlusWallpapers"
    // TODO(release): confirm these two sub-collections exist in Firestore under
    // Banners/OnePlusWallpapers/ and Users/OnePlusWallpapers/ — a missing sub-collection
    // is not an error, it just returns nothing, so banners fail silently.
    override val collectionBannersSub = "OnePlus15Banners"
    override val documentUsersApp = "OnePlusWallpapers"
    override val collectionUsersSub = "OnePlus15AndroidUsers"
    override val documentAppUpdate = "OnePlus15Wallpapers"

    // ── Local storage ───────────────────────────────────────────────────────
    override val prefsName = "oneplus15_wallpapers_prefs"
    override val downloadFolderName = "OnePlus15Wallpapers"
    override val favoritesPrefsKey = "oneplus15favorites"
    // The standalone OnePlus15Wallpapers project predates :core's Room database, so
    // there is no pre-:core favourites table for the migration to rename.
    override val legacyFavoritesTable = "favorites"

    // ── Monetisation ────────────────────────────────────────────────────────
    // Copied verbatim from the standalone project's AppConfig: this product id is
    // already live in Play, and changing it would break restore for existing buyers.
    override val billingLifetimeId = "oneplus15wallpapers_lifetime"
    override val billingLifetimeFallbackPrice = "₹199"
    override val billingLifetimeComparePrice = "₹299"

    // TODO(release): replace with this app's own AdMob ids before publishing.
    // These are Google's public TEST ids and earn nothing;
    // tools/check_release_ready.py fails while they are here, so they cannot ship.
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
