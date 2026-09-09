package com.droidates.samsungfold8wallpapers

import com.droidates.wallpapers.core.config.AppSpec

/** Everything unique to Samsung Fold 8 Wallpapers. All screens and logic come from `:core`. */
object SamsungFold8Spec : AppSpec {

    override val appName = "Samsung Fold 8 Wallpapers"
    override val toolbarTitle = "SAMSUNG FOLD 8 WALLPAPERS"
    override val homeSectionTitle = "Official Samsung Drops"
    override val onboardingTagline = "Your home for official Samsung wallpapers."
    override val deviceFamilyName = "Galaxy Z Fold 8"

    // Violet, sampled from the launcher icon. The four values are one palette: accent and container are
    // the light/dark pair, and the two "on" colours must stay legible on them.
    override val brandAccent = 0xFF5C298E
    override val brandOnAccent = 0xFFFFFFFF
    override val brandAccentContainer = 0xFFE6DEED
    override val brandOnAccentContainer = 0xFF1C0D2B

    // ── Firestore ───────────────────────────────────────────────────────────
    // Shares the Samsung brand collections with :s25ultra and :s26ultra — only the
    // per-app sub-collections and the update doc are distinct.
    override val supportsSeriesFilter = true
    override val supportsLaunchYearSort = true

    override val categoryBrandName = "Samsung"
    override val collectionHome = "Samsung"
    override val documentDevicesBrand = "Samsung"
    override val documentDevicesSecondary = "Android"
    override val seriesPrefixRanges = listOf("Galaxy" to "Galaxz")

    override val documentBannersApp = "SamsungWallpapers"
    override val collectionBannersSub = "SamsungFold8Banners"
    override val documentUsersApp = "SamsungWallpapers"
    override val collectionUsersSub = "SamsungFold8AndroidUsers"
    // Case-sensitive Firestore doc id — matches the standalone project's AppConfig
    // (DOCUMENT_APP_UPDATE), so existing force-update checks keep resolving.
    override val documentAppUpdate = "SamsungFold8Wallpapers"

    // ── Local storage ───────────────────────────────────────────────────────
    // Carried over verbatim from the standalone project's AppConfig so an update
    // keeps premium status, onboarding state and favourites for existing users.
    override val prefsName = "samsungfold8_wallpapers_prefs"
    override val downloadFolderName = "SamsungFold8Wallpapers"
    override val favoritesPrefsKey = "samsungfold8favorites"
    // Pre-:core Room table (AppConfig.TABLE_FAVORITES). The v2->v3 migration renames
    // it to the shared "favorites" table so saved wallpapers survive the update.
    override val legacyFavoritesTable = "samsungfold8favorites"

    // ── Monetisation ────────────────────────────────────────────────────────
    // Must match the product id already live in Play, or existing premium users
    // fail to restore.
    override val billingLifetimeId = "samsungfold8lifetime"
    override val billingLifetimeFallbackPrice = "₹199"
    override val billingLifetimeComparePrice = "₹299"

    // Live AdMob ids from the standalone project.
    override val adMobAppId = "ca-app-pub-6427410984085546~4654247213"
    override val adBannerId = "ca-app-pub-6427410984085546/3341165546"
    override val adInterstitialId = "ca-app-pub-6427410984085546/9188268453"
    override val adRewardedId = "ca-app-pub-6427410984085546/7875186783"

    // ── Build info ──────────────────────────────────────────────────────────
    override val isDebug = BuildConfig.DEBUG
    override val versionName = BuildConfig.VERSION_NAME
    override val versionCode = BuildConfig.VERSION_CODE
    override val applicationId = BuildConfig.APPLICATION_ID
}
