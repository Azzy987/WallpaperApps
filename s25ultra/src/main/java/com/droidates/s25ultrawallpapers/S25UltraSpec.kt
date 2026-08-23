package com.droidates.s25ultrawallpapers

import com.droidates.wallpapers.core.config.AppSpec

/** Everything unique to S25 Ultra Wallpapers. All screens and logic come from `:core`. */
object S25UltraSpec : AppSpec {

    override val appName = "S25 Ultra Wallpapers"
    override val toolbarTitle = "S25 ULTRA WALLPAPERS"
    override val homeSectionTitle = "Official Samsung Drops"
    override val onboardingTagline = "Your home for official Samsung wallpapers."
    override val deviceFamilyName = "S25 Ultra"

    override val brandAccent = 0xFF6A1B9A
    override val brandOnAccent = 0xFFFFFFFF
    override val brandAccentContainer = 0xFFEFDBFF
    override val brandOnAccentContainer = 0xFF250040

    // ── Firestore ───────────────────────────────────────────────────────────
    override val categoryBrandName = "Samsung"
    override val collectionHome = "Samsung"
    override val documentDevicesBrand = "Samsung"
    override val documentDevicesSecondary = "Android"
    override val seriesPrefixRanges = listOf("Galaxy" to "Galaxz")

    override val documentBannersApp = "SamsungWallpapers"
    override val collectionBannersSub = "S25UltraWallpapersBanners"
    override val documentUsersApp = "SamsungWallpapers"
    override val collectionUsersSub = "S25UltraAndroidUsers"
    // Case-sensitive Firestore doc id — the live app uses "S25UltraWallpapers" (see the old
    // project's UpdateManager). Lowercasing it makes force-update checks silently find nothing.
    override val documentAppUpdate = "S25UltraWallpapers"

    // ── Local storage ───────────────────────────────────────────────────────
    // NOTE: the live app predates the shared AppConfig pattern and hardcoded these in
    // PreferencesManager/FavoritesManager. They do NOT follow the "s25ultra…" convention
    // the other apps use — changing them would silently reset premium status, onboarding
    // state and favourites for every existing user.
    override val prefsName = "s25_ultra_wallpapers_prefs"
    override val downloadFolderName = "S25UltraWallpapers"
    override val favoritesPrefsKey = "s25favorites"
    // Pre-:core Room table. The v2->v3 migration renames it so existing users
    // keep their saved wallpapers on update.
    // The live app's table is "s25favorites" — NOT "s25ultra…" like the other apps.
    // Verified against S25UltraWallpapers Android Latest/…/FavoriteEntity.kt.
    override val legacyFavoritesTable = "s25favorites"

    // ── Monetisation ────────────────────────────────────────────────────────
    // Must match the product id already live in Play, or existing premium users fail to
    // restore. The live app uses "s25ultrawallpapers_lifetime" — not the shorter form the
    // other apps use.
    override val billingLifetimeId = "s25ultrawallpapers_lifetime"
    override val billingLifetimeFallbackPrice = "₹299"
    override val billingLifetimeComparePrice = "₹399"

    // Must match APPLICATION_ID in this module's AndroidManifest.xml.
    override val adMobAppId = "ca-app-pub-6427410984085546~5645139571"
    override val adBannerId = "ca-app-pub-6427410984085546/4195973834"
    override val adInterstitialId = "ca-app-pub-6427410984085546/4347535628"
    override val adRewardedId = "ca-app-pub-6427410984085546/7442835772"

    // ── Build info ──────────────────────────────────────────────────────────
    override val isDebug = BuildConfig.DEBUG
    override val versionName = BuildConfig.VERSION_NAME
    override val versionCode = BuildConfig.VERSION_CODE
    override val applicationId = BuildConfig.APPLICATION_ID
}
