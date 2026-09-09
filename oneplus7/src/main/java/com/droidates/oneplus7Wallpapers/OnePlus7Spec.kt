package com.droidates.oneplus7Wallpapers

import com.droidates.wallpapers.core.config.AppSpec

/** Everything unique to OnePlus 7 Wallpapers. All screens and logic come from `:core`. */
object OnePlus7Spec : AppSpec {

    // Live branding is "OnePlus Wallpapers" (no "7") — matches the Play listing.
    override val appName = "OnePlus Wallpapers"
    override val toolbarTitle = "ONEPLUS WALLPAPERS"
    override val homeSectionTitle = "Official OnePlus Drops"
    override val onboardingTagline = "Your home for official OnePlus wallpapers."
    override val deviceFamilyName = "OnePlus 7"

    // Rose red, sampled from the launcher icon. The four values are one palette: accent and container are
    // the light/dark pair, and the two "on" colours must stay legible on them.
    override val brandAccent = 0xFFA21639
    override val brandOnAccent = 0xFFFFFFFF
    override val brandAccentContainer = 0xFFF0DBE0
    override val brandOnAccentContainer = 0xFF310711

    // ── Firestore ───────────────────────────────────────────────────────────
    override val supportsSeriesFilter = true
    override val supportsLaunchYearSort = true

    override val categoryBrandName = "OnePlus"
    override val collectionHome = "OnePlus"
    override val documentDevicesBrand = "OnePlus"
    override val documentDevicesSecondary = "Android"
    override val seriesPrefixRanges = listOf("OnePlus" to "OnePlut")

    override val documentBannersApp = "OnePlusWallpapers"
    override val collectionBannersSub = "OnePlus7Banners"
    override val documentUsersApp = "OnePlusWallpapers"
    override val collectionUsersSub = "OnePlus7AndroidUsers"
    override val documentAppUpdate = "oneplus7wallpapers"

    // ── Local storage ───────────────────────────────────────────────────────
    override val prefsName = "oneplus7_wallpapers_prefs"
    override val downloadFolderName = "OnePlus7Wallpapers"
    override val favoritesPrefsKey = "oneplus7favorites"
    // Pre-:core Room table. The v2->v3 migration renames it so existing users
    // keep their saved wallpapers on update.
    override val legacyFavoritesTable = "oneplus7favorites"

    // ── Monetisation ────────────────────────────────────────────────────────
    override val billingLifetimeId = "oneplus7_lifetime"
    override val billingLifetimeFallbackPrice = "₹199"
    override val billingLifetimeComparePrice = "₹299"

    // Must match APPLICATION_ID in this module's AndroidManifest.xml.
    override val adMobAppId = "ca-app-pub-6427410984085546~7840971629"
    override val adBannerId = "ca-app-pub-6427410984085546/8679616502"
    override val adInterstitialId = "ca-app-pub-6427410984085546/4505443419"
    override val adRewardedId = "ca-app-pub-6427410984085546/3470776262"

    // ── Build info ──────────────────────────────────────────────────────────
    override val isDebug = BuildConfig.DEBUG
    override val versionName = BuildConfig.VERSION_NAME
    override val versionCode = BuildConfig.VERSION_CODE
    override val applicationId = BuildConfig.APPLICATION_ID
}
