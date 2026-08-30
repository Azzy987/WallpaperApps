package com.droidates.wallpapers.core.config

/**
 * Everything that differs between wallpaper apps.
 *
 * Each app module ships exactly one implementation of this (see the app's
 * `<App>Spec.kt`) and hands it to [AppConfig.install] from its Application class.
 * Shared code never hardcodes brand values — it reads them from [AppConfig].
 *
 * Adding a new app should mean writing one of these, not editing shared code.
 */
interface AppSpec {

    // ── Identity ────────────────────────────────────────────────────────────
    val appName: String
    val toolbarTitle: String

    /** Home tab section header, e.g. "Official Pixel Drops". */
    val homeSectionTitle: String

    /** Onboarding first-page subtitle, e.g. "Your home for official Google & Pixel wallpapers." */
    val onboardingTagline: String

    /** Device family named in marketing copy, e.g. "Pixel 11", "iPhone 18". */
    val deviceFamilyName: String

    // ── Brand accent (ARGB longs, e.g. 0xFFBF5000) ──────────────────────────
    /** Main accent colour used across both light and dark themes. */
    val brandAccent: Long
    /** Content colour drawn on top of [brandAccent] — keep the contrast readable. */
    val brandOnAccent: Long
    /** Lighter tonal container of the accent. */
    val brandAccentContainer: Long
    /** Content colour drawn on top of [brandAccentContainer]. */
    val brandOnAccentContainer: Long

    /**
     * Whether this app's wallpapers are divided into `series` at all.
     *
     * False hides both series surfaces in the brand category — the filter chip row and
     * the toolbar filter icon. CategoryViewModel always emits a lone "All Series" entry
     * as a fallback, so without this an app with no series data still renders a chip row
     * containing one chip that filters nothing.
     */
    val supportsSeriesFilter: Boolean

    /**
     * Whether wallpapers in this app carry a `launchYear` field.
     *
     * Firestore's orderBy silently DROPS documents that lack the ordered field, so an
     * app whose wallpapers have no `launchYear` returns an empty Home tab rather than an
     * error. Set false for catalogues that are not device-release based (a game, say);
     * the Release Date sort is then hidden and the default sort falls back to Latest.
     */
    val supportsLaunchYearSort: Boolean

    /** Brand name used to filter categories in Firestore (`categoryType == "brand"`). */
    val categoryBrandName: String

    // ── Firestore ───────────────────────────────────────────────────────────
    /** Brand wallpaper collection powering the Home tab, e.g. `Google`, `Apple`. */
    val collectionHome: String

    /** `Devices/<id>` doc holding the series filter chips for this brand. */
    val documentDevicesBrand: String

    /** Optional second `Devices/<id>` doc merged into the chips. */
    val documentDevicesSecondary: String

    /**
     * Prefix ranges used to discover `series` values in [collectionHome].
     * Firestore range is `[first, second)` — so `"Pixel" to "Pixem"` matches every
     * label starting with `Pixel`. Get this wrong and the chips silently go empty.
     */
    val seriesPrefixRanges: List<Pair<String, String>>

    /** Brand-level namespace shared by apps of the same brand, e.g. `PixelWallpapers`. */
    val documentBannersApp: String

    /** Per-app banner sub-collection, e.g. `Pixel11Banners`. */
    val collectionBannersSub: String

    /** Brand-level user namespace, e.g. `PixelWallpapers`. */
    val documentUsersApp: String

    /** Per-app user sub-collection, e.g. `Pixel11AndroidUsers`. */
    val collectionUsersSub: String

    /**
     * Force-update doc id under `AppUpdate`.
     * MUST be unique per app — sharing it force-updates another app's users.
     */
    val documentAppUpdate: String

    // ── Local storage ───────────────────────────────────────────────────────
    val prefsName: String
    val downloadFolderName: String

    /**
     * SharedPreferences key holding the favourites id set.
     * (The Room table itself is shared and named "favorites" — each app already has
     * its own private database file, so it needs no per-app table name.)
     */
    val favoritesPrefsKey: String

    /**
     * The Room table this app used *before* moving onto :core (e.g. "oneplus7favorites").
     * Used once, by the v2->v3 migration, to rename it to the shared "favorites" table so
     * existing users keep their saved wallpapers. Use "favorites" for brand-new apps.
     */
    val legacyFavoritesTable: String

    // ── Monetisation ────────────────────────────────────────────────────────
    val billingLifetimeId: String
    val billingLifetimeFallbackPrice: String
    val billingLifetimeComparePrice: String

    /**
     * AdMob App ID, e.g. "ca-app-pub-6427410984085546~1782204114" (note the `~`).
     *
     * The GMA Next-Gen SDK takes this in code via `InitializationConfig.Builder(appId)`
     * instead of reading `com.google.android.gms.ads.APPLICATION_ID` meta-data from the
     * manifest. Keep it identical to the value in the module's AndroidManifest.xml — a
     * mismatch stops ads serving for that app.
     */
    val adMobAppId: String

    val adBannerId: String
    val adInterstitialId: String
    val adRewardedId: String

    // ── Build info (supplied from each app's own BuildConfig) ───────────────
    val isDebug: Boolean
    val versionName: String
    val versionCode: Int
    val applicationId: String
}
