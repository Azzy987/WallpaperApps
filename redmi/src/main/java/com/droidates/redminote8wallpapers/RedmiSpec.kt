package com.droidates.redminote8wallpapers

import com.droidates.wallpapers.core.config.AppSpec

/**
 * Everything unique to Redmi 17 Pro Max Wallpapers. All screens and logic come from `:core`.
 *
 * NOTE the package: this app reuses the Play listing of the original "Redmi Note 8 Pro
 * Wallpapers" (removed in the 2020 policy sweep), so its applicationId stays
 * `com.droidates.redminote8wallpapers` — that can never change once published. Only the
 * store name and the content are new; the listing keeps its rating and review history,
 * which is the reason for reviving it rather than creating a new entry.
 */
object RedmiSpec : AppSpec {

    override val appName = "Redmi 17 Pro Max Wallpapers"
    override val toolbarTitle = "REDMI 17 PRO MAX WALLPAPERS"
    override val homeSectionTitle = "Official Redmi Drops"
    override val onboardingTagline = "Your home for official Redmi wallpapers."
    override val deviceFamilyName = "Redmi 17 Pro Max"

    // Redmi orange.
    override val brandAccent = 0xFFFF6900
    override val brandOnAccent = 0xFFFFFFFF
    override val brandAccentContainer = 0xFFFFDBC7
    override val brandOnAccentContainer = 0xFF351000

    override val supportsSeriesFilter = true
    override val supportsLaunchYearSort = true

    // ── Firestore ───────────────────────────────────────────────────────────
    // Redmi is kept as its own brand rather than sharing :xiaomi17's collection, so
    // these documents must exist in Firestore or the tabs come back empty.
    override val categoryBrandName = "Redmi"
    override val collectionHome = "Redmi"
    override val documentDevicesBrand = "Redmi"
    override val documentDevicesSecondary = "Android"
    // Firestore range is [first, second) — bump the last character of the prefix.
    // Wallpapers must carry a `series` starting with "Redmi" or the chips come back empty.
    override val seriesPrefixRanges = listOf("Redmi" to "Redmj")

    override val documentBannersApp = "RedmiWallpapers"
    override val collectionBannersSub = "Redmi17ProMaxBanners"
    override val documentUsersApp = "RedmiWallpapers"
    override val collectionUsersSub = "Redmi17ProMaxAndroidUsers"
    override val documentAppUpdate = "Redmi17ProMaxWallpapers"

    // ── Local storage ───────────────────────────────────────────────────────
    override val prefsName = "redmi17promax_wallpapers_prefs"
    override val downloadFolderName = "Redmi17ProMaxWallpapers"
    override val favoritesPrefsKey = "redmi17promaxfavorites"
    // The 2019 app predates :core's Room database entirely — it never shipped the
    // pre-:core favorites table the migration looks for, and the handful of users still
    // on that build are being replaced by a fresh install anyway. "favorites" is the
    // shared table :core already uses.
    override val legacyFavoritesTable = "favorites"

    // ── Monetisation ────────────────────────────────────────────────────────
    override val billingLifetimeId = "redmi17promaxlifetime"
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
