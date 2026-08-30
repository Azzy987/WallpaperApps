package com.droidates.wallpapers.core.config

/**
 * Central configuration for the shared wallpaper codebase.
 *
 * Values that are the same for every app are plain constants here. Values that
 * differ per app are delegated to the [AppSpec] the app installs at startup, so
 * shared code can keep referring to `AppConfig.X` exactly as before.
 *
 * Call [install] from the app's Application.onCreate() before anything touches
 * these properties.
 */
object AppConfig {

    @Volatile
    private var spec: AppSpec? = null

    fun install(spec: AppSpec) {
        this.spec = spec
    }

    private val s: AppSpec
        get() = spec ?: error(
            "AppConfig.install(AppSpec) was never called — do it in Application.onCreate()."
        )

    // ─────────────────────────────────────────────────────────────────────────
    // Per-app values (from AppSpec)
    // ─────────────────────────────────────────────────────────────────────────

    val APP_NAME: String get() = s.appName
    val TOOLBAR_TITLE: String get() = s.toolbarTitle
    val HOME_SECTION_TITLE: String get() = s.homeSectionTitle
    val ONBOARDING_TAGLINE: String get() = s.onboardingTagline
    val DEVICE_FAMILY_NAME: String get() = s.deviceFamilyName

    val BRAND_ACCENT: Long get() = s.brandAccent
    val BRAND_ON_ACCENT: Long get() = s.brandOnAccent
    val BRAND_ACCENT_CONTAINER: Long get() = s.brandAccentContainer
    val BRAND_ON_ACCENT_CONTAINER: Long get() = s.brandOnAccentContainer
    val SUPPORTS_LAUNCH_YEAR_SORT: Boolean get() = s.supportsLaunchYearSort
    val SUPPORTS_SERIES_FILTER: Boolean get() = s.supportsSeriesFilter
    val CATEGORY_BRAND_NAME: String get() = s.categoryBrandName

    val COLLECTION_HOME: String get() = s.collectionHome
    val DOCUMENT_DEVICES_BRAND: String get() = s.documentDevicesBrand
    val DOCUMENT_DEVICES_SECONDARY: String get() = s.documentDevicesSecondary
    val SERIES_PREFIX_RANGES: List<Pair<String, String>> get() = s.seriesPrefixRanges

    val DOCUMENT_BANNERS_APP: String get() = s.documentBannersApp
    val COLLECTION_BANNERS_SUB: String get() = s.collectionBannersSub
    val DOCUMENT_USERS_APP: String get() = s.documentUsersApp
    val COLLECTION_USERS_SUB: String get() = s.collectionUsersSub
    val DOCUMENT_APP_UPDATE: String get() = s.documentAppUpdate

    /**
     * FCM topic targeting only this app. All apps share one Firebase project, so
     * "all_users" reaches every app — use this to target a single one.
     */
    val FCM_TOPIC_APP: String get() = s.documentAppUpdate

    val PREFS_NAME: String get() = s.prefsName
    val DOWNLOAD_FOLDER_NAME: String get() = s.downloadFolderName
    val FAVORITES_PREFS_KEY: String get() = s.favoritesPrefsKey
    val LEGACY_FAVORITES_TABLE: String get() = s.legacyFavoritesTable

    val BILLING_LIFETIME_ID: String get() = s.billingLifetimeId
    val BILLING_LIFETIME_FALLBACK_PRICE: String get() = s.billingLifetimeFallbackPrice
    val BILLING_LIFETIME_COMPARE_PRICE: String get() = s.billingLifetimeComparePrice

    val ADMOB_APP_ID: String get() = s.adMobAppId
    val AD_BANNER_ID: String get() = s.adBannerId
    val AD_INTERSTITIAL_ID: String get() = s.adInterstitialId
    val AD_REWARDED_ID: String get() = s.adRewardedId

    val IS_DEBUG: Boolean get() = s.isDebug
    val VERSION_NAME: String get() = s.versionName
    val VERSION_CODE: Int get() = s.versionCode
    val APPLICATION_ID: String get() = s.applicationId

    /** All collections searched when looking up a wallpaper by ID. */
    val WALLPAPER_SEARCH_COLLECTIONS: List<String>
        get() = listOf(COLLECTION_HOME, COLLECTION_TRENDING)

    // ─────────────────────────────────────────────────────────────────────────
    // Shared across every app
    // ─────────────────────────────────────────────────────────────────────────

    const val DEVELOPER_NAME = "Droidates"
    const val SUPPORT_EMAIL = "droidates@gmail.com"

    /** Categories tab — depth-effect wallpapers (filtered by depthEffect == true). */
    const val CATEGORY_DEPTH_EFFECT = "Depth Effect"

    const val COLLECTION_TRENDING = "TrendingWallpapers"
    const val COLLECTION_CATEGORIES = "Categories"
    const val COLLECTION_DEVICES = "Devices"
    const val COLLECTION_CATEGORY_WALLPAPERS = "wallpapers"
    const val COLLECTION_FEATURE_REQUESTS = "RequestedUpdates"
    const val COLLECTION_WALLPAPERS_FALLBACK = "Wallpapers"
    const val COLLECTION_DEPTH_EFFECT = "DepthEffectWallpapers"
    const val COLLECTION_PAYWALL_BANNERS = "PaywallWallpapers"

    const val COLLECTION_BANNERS = "Banners"
    const val COLLECTION_USERS = "Users"
    const val COLLECTION_APP_UPDATE = "AppUpdate"

    // Navigation — source screen identifiers
    const val SOURCE_HOME = "home"
    const val SOURCE_TRENDING = "trending"
    const val SOURCE_CATEGORY = "category"
    const val SOURCE_FAVORITES = "favorites"
    const val SOURCE_DEPTH = "depth"

    // Firestore field names
    const val FIELD_UPDATE_VERSION = "version"
    const val FIELD_UPDATE_MANDATORY = "mandatoryUpdate"
    const val FIELD_UPDATE_MESSAGE = "message"
    const val FIELD_TIMESTAMP = "timestamp"

    const val DATABASE_NAME = "wallpaper_database"

    /** Same Firebase project for every app, so the web client ID is shared. */
    const val GOOGLE_WEB_CLIENT_ID =
        "352408897001-k4f317q8jsrr1bm3rn9bah919ofg9eou.apps.googleusercontent.com"

    const val URL_PRIVACY_POLICY =
        "https://www.droidates.com/p/privacy-policy-droidates-wallpaper-apps.html"
    const val URL_TERMS_OF_USE =
        "https://www.droidates.com/p/terms-of-use-droidates-wallpaper-apps.html"
    const val URL_LICENSES =
        "https://www.droidates.com/p/licenses-credits-droidates-wallpaper.html"
    const val URL_DEVELOPER_PAGE =
        "https://play.google.com/store/apps/developer?id=$DEVELOPER_NAME"

    // Share / social — built from the installed spec at call time
    val SHARE_APP_SUBJECT: String get() = "Check out $APP_NAME!"
    val SHARE_APP_TEXT_PREFIX: String
        get() = "I've been using $APP_NAME and it's amazing! Download it here: "
    val SHARE_WALLPAPER_TEXT_SUFFIX: String
        get() = " from $APP_NAME App!\n\nGet more wallpapers: "
}
