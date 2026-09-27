package com.droidates.oneplus7twallpapers

import com.droidates.wallpapers.core.config.AppSpec

/**
 * Everything unique to OnePlus 15T Wallpapers. All screens and logic come from `:core`.
 *
 * NOTE the package: this app reuses the Play listing of "Wallpapers for One Plus 7T",
 * one of the two flavours the legacy :app module used to build. Its applicationId stays
 * `com.droidates.oneplus7twallpapers` and can never change — only the store name is new.
 */
object OnePlus15TSpec : AppSpec {

    override val appName = "OnePlus 15T Wallpapers"
    override val toolbarTitle = "ONEPLUS 15T WALLPAPERS"
    override val homeSectionTitle = "Official OnePlus Drops"
    override val onboardingTagline = "Your home for official OnePlus wallpapers."
    override val deviceFamilyName = "OnePlus 15T"

    // TODO(release): set to match the launcher icon / onboarding artwork. Placeholder is
    // shifted off :oneplus7's #C62828 and :oneplus15's #6A4BA8 so no two apps collide.
    override val brandAccent = 0xFF8E1F3D
    override val brandOnAccent = 0xFFFFFFFF
    override val brandAccentContainer = 0xFFFFD9DF
    override val brandOnAccentContainer = 0xFF3B0012

    override val supportsSeriesFilter = true
    override val supportsLaunchYearSort = true

    // ── Firestore ───────────────────────────────────────────────────────────
    // Brand-level values are SHARED with :oneplus7 and :oneplus15 on purpose — all three
    // read the same OnePlus catalogue. Only the per-app sub-collections below differ.
    override val categoryBrandName = "OnePlus"
    override val collectionHome = "OnePlus"
    override val documentDevicesBrand = "OnePlus"
    override val documentDevicesSecondary = "Android"
    override val seriesPrefixRanges = listOf("OnePlus" to "OnePlut")

    override val documentBannersApp = "OnePlusWallpapers"
    // TODO(release): create these in Firestore, or confirm the names match what is there.
    // A missing sub-collection is not an error — it returns nothing, so banners fail
    // silently. That is exactly how the :redmi banner bug hid.
    override val collectionBannersSub = "OnePlus15TBanners"
    override val documentUsersApp = "OnePlusWallpapers"
    override val collectionUsersSub = "OnePlus15TAndroidUsers"
    override val documentAppUpdate = "OnePlus15TWallpapers"

    // ── Local storage ───────────────────────────────────────────────────────
    override val prefsName = "oneplus15t_wallpapers_prefs"
    override val downloadFolderName = "OnePlus15TWallpapers"
    override val favoritesPrefsKey = "oneplus15tfavorites"
    // The 2019 build predates :core's Room database entirely, so there is no pre-:core
    // favourites table for the migration to rename.
    override val legacyFavoritesTable = "favorites"

    // ── Monetisation ────────────────────────────────────────────────────────
    // TODO(release): create + ACTIVATE this product in Play Console at ₹199.
    override val billingLifetimeId = "oneplus15tlifetime"
    override val billingLifetimeFallbackPrice = "₹199"
    override val billingLifetimeComparePrice = "₹299"

    // TODO(release): replace with this app's own AdMob ids before publishing.
    // The legacy :app flavour used app id ca-app-pub-6427410984085546~7395873201 — reuse
    // it only if that AdMob app still exists and belongs to this package.
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
