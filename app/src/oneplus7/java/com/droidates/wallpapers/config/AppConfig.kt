package com.droidates.wallpapers.config

object AppConfig {

    // ── App Identity ──────────────────────────────────────────────────────
    const val APP_NAME = "OnePlus 7 Wallpapers"
    const val TOOLBAR_TITLE = "ONEPLUS 7 WALLPAPERS"
    const val DEVELOPER_NAME = "Droidates"
    const val SUPPORT_EMAIL = "droidates@gmail.com"

    // ── Firestore — Wallpaper Collections ────────────────────────────────
    const val CATEGORY_BRAND_NAME = "OnePlus"
    const val COLLECTION_HOME = "OnePlus"
    const val COLLECTION_TRENDING = "TrendingWallpapers"
    const val COLLECTION_CATEGORIES = "Categories"
    const val COLLECTION_CATEGORY_WALLPAPERS = "wallpapers"
    const val COLLECTION_FEATURE_REQUESTS = "RequestedUpdates"
    const val COLLECTION_WALLPAPERS_FALLBACK = "Wallpapers"
    const val COLLECTION_DEPTH_EFFECT = "DepthEffectWallpapers"
    const val COLLECTION_PAYWALL_BANNERS = "PaywallWallpapers"
    val WALLPAPER_SEARCH_COLLECTIONS = listOf(COLLECTION_HOME, COLLECTION_TRENDING)

    // ── Navigation Source Identifiers ─────────────────────────────────────
    const val SOURCE_HOME = "home"
    const val SOURCE_TRENDING = "trending"
    const val SOURCE_CATEGORY = "category"
    const val SOURCE_FAVORITES = "favorites"
    const val SOURCE_DEPTH = "depth"

    // ── Firestore — Banners ───────────────────────────────────────────────
    const val COLLECTION_BANNERS = "Banners"
    const val DOCUMENT_BANNERS_APP = "OnePlusWallpapers"
    const val COLLECTION_BANNERS_SUB = "OnePlus7Banners"

    // ── Firestore — Users ─────────────────────────────────────────────────
    const val COLLECTION_USERS = "Users"
    const val DOCUMENT_USERS_APP = "OnePlusWallpapers"
    const val COLLECTION_USERS_SUB = "OnePlus7AndroidUsers"

    // ── Firestore — App Update ────────────────────────────────────────────
    const val COLLECTION_APP_UPDATE = "AppUpdate"
    const val DOCUMENT_APP_UPDATE = "oneplus7wallpapers"
    const val FIELD_UPDATE_VERSION = "version"
    const val FIELD_UPDATE_MANDATORY = "mandatoryUpdate"
    const val FIELD_UPDATE_MESSAGE = "message"
    const val FIELD_TIMESTAMP = "timestamp"

    // ── Room Database ─────────────────────────────────────────────────────
    const val DATABASE_NAME = "wallpaper_database"
    const val TABLE_FAVORITES = "oneplus7favorites"

    // ── SharedPreferences ─────────────────────────────────────────────────
    const val PREFS_NAME = "oneplus7_wallpapers_prefs"

    // ── File System ───────────────────────────────────────────────────────
    const val DOWNLOAD_FOLDER_NAME = "OnePlus7Wallpapers"

    // ── Google Sign-In ────────────────────────────────────────────────────
    const val GOOGLE_WEB_CLIENT_ID =
        "352408897001-k4f317q8jsrr1bm3rn9bah919ofg9eou.apps.googleusercontent.com"

    // ── AdMob ─────────────────────────────────────────────────────────────
    const val AD_INTERSTITIAL_ID = "ca-app-pub-6427410984085546/4505443419"
    const val AD_REWARDED_ID     = "ca-app-pub-6427410984085546/3470776262"
    const val AD_NATIVE_ID       = "ca-app-pub-6427410984085546/1425023858"

    // ── Google Play Billing ───────────────────────────────────────────────
    const val BILLING_LIFETIME_ID = "oneplus7_lifetime"
    const val BILLING_LIFETIME_FALLBACK_PRICE = "₹199"

    // ── Links ─────────────────────────────────────────────────────────────
    const val URL_PRIVACY_POLICY = "https://www.droidates.com/p/privacy-policy-droidates-wallpaper-apps.html"
    const val URL_TERMS_OF_USE   = "https://www.droidates.com/p/terms-of-use-droidates-wallpaper-apps.html"
    const val URL_LICENSES       = "https://www.droidates.com/p/licenses-credits-droidates-wallpaper.html"
    const val URL_DEVELOPER_PAGE = "https://play.google.com/store/apps/developer?id=$DEVELOPER_NAME"

    // ── Share ─────────────────────────────────────────────────────────────
    const val SHARE_APP_SUBJECT      = "Check out $APP_NAME!"
    const val SHARE_APP_TEXT_PREFIX  = "I've been using $APP_NAME and it's amazing! Download it here: "
    const val SHARE_WALLPAPER_TEXT_SUFFIX = " from $APP_NAME App!\n\nGet more wallpapers: "
}
