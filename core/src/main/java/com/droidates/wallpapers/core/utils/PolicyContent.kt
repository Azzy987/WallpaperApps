package com.droidates.wallpapers.core.utils

import com.droidates.wallpapers.core.config.AppConfig

/**
 * Utility class containing content for various legal and informational dialogs.
 */
object PolicyContent {
    
    /**
     * Privacy Policy content
     */
    val privacyPolicy: String get() = """
Privacy Policy for ${AppConfig.APP_NAME}

Effective Date: 09-09-2025

${AppConfig.APP_NAME} ("we", "our", or "us") respects your privacy. This Privacy Policy explains how your information is collected, used, and protected when you use our Android application ${AppConfig.APP_NAME}.

By using the app, you agree to the terms of this Privacy Policy.

1. Information We Collect

a. Information Collected Automatically

When you use our app, we automatically collect certain information, including:

Device Information (model, OS version, unique identifiers)

Usage Data (wallpapers viewed/downloaded, interactions, time spent)

Crash Reports and Diagnostics (to improve app stability)

Ad Identifiers (used for advertising and analytics)

b. Firebase Services

We use several Firebase tools:

Firebase Firestore: For fetching wallpapers and app content.

Firebase Remote Config: To update app settings and features dynamically.

Firebase Cloud Messaging (FCM): To send push notifications.

These services collect data in accordance with Firebase's Privacy and Security policies.

c. AdMob

We use Google AdMob to serve ads within the app. AdMob may collect information such as:

Device identifiers

Ad interactions

Approximate location (for contextual ad targeting)

Learn more about how Google uses information: Google’s Privacy & Terms.

2. Permissions We Use

The app requests the following permissions:

INTERNET & ACCESS_NETWORK_STATE: To fetch data and show ads.

VIBRATE: For notification vibrations.

READ/WRITE_EXTERNAL_STORAGE (Android ≤ 12): To save wallpapers.

On Android 10 and above the app saves wallpapers through the system media store and does not request access to your photo library. It can only see the wallpapers it saved itself, never the rest of your gallery.

SET_WALLPAPER: To allow setting wallpapers directly from the app.

POST_NOTIFICATIONS: To send you alerts and updates.

AD_ID: To serve personalized and relevant ads via AdMob.

We do not collect or access any personal files, contacts, or sensitive user data.

3. How We Use Your Information

We use the data collected to:

Provide and enhance the user experience.

Deliver push notifications about new features or wallpapers.

Improve app performance and fix bugs.

Show relevant, non-intrusive advertisements using AdMob.

4. Sharing of Information

We do not sell, rent, or trade your personal data. We may share anonymous usage data with:

Firebase (for analytics and backend services)

AdMob / Google (for displaying ads)

Analytics Tools (for improving user experience)

5. Children's Privacy

Our app is not intended for children under 13. We do not knowingly collect personal information from children. If we become aware of such data, we will delete it promptly.

6. Data Retention

We retain usage and analytics data only as long as necessary to fulfill the purposes outlined in this policy, or as required by law. Wallpapers downloaded from the app remain on your device until manually deleted.

7. Your Choices

You can disable notifications via your device settings.

You can revoke app permissions at any time through system settings.

You may opt out of personalized ads through your Google account ad settings.

Uninstalling the app will stop all data collection.

8. Security

We use industry-standard security practices to protect your data. However, no method of transmission over the Internet is 100% secure.

9. Changes to This Policy

We may update this Privacy Policy from time to time. Updates will be posted here, and major changes may be communicated through the app.

10. Contact Us

If you have any questions or concerns, feel free to contact us:

📧 Email: ${AppConfig.SUPPORT_EMAIL}
🌐 Website: ${AppConfig.URL_PRIVACY_POLICY}
    """.trimIndent()

    /**
     * Terms of Use content
     */
    val termsOfUse: String get() = """
        ##   

📑 Terms of Use – ${AppConfig.APP_NAME}

Last updated: September 09, 2025

Welcome to ${AppConfig.APP_NAME}, developed by Droidates. By downloading or using the app, you agree to the following Terms of Use. If you do not agree, please do not use the app.

🖼️ 1. Wallpaper Usage

Exclusive Wallpapers
Wallpapers labeled as "Exclusive" are original creations by Droidates. These are provided for personal use only. You may set them as your device wallpaper but may not copy, redistribute, modify, or use them for commercial purposes without explicit permission.

Sourced Wallpapers
Other wallpapers are collected from publicly available sources (such as HDQWalls, WallpaperAccess, Twitter, Unsplash, and official device manufacturer websites). These are intended solely for personal, non-commercial usewithin the app.

If you are the rightful owner of any content and wish it to be removed, please contact us at ${AppConfig.SUPPORT_EMAIL} with proof of ownership, and we will take prompt action.

⚠️ 2. Restrictions

By using the app, you agree not to:

Use wallpapers for resale, printing, or commercial distribution.

Re-host the images on other platforms or websites.

Attempt to reverse engineer, modify, or copy any part of the app.

Violate any local or international copyright laws.

🧩 3. Third-Party Libraries & Services

${AppConfig.APP_NAME} uses open-source libraries and services including but not limited to:

Firebase (Firestore, Storage, Analytics, Messaging, Remote Config, Auth)

Google Sign-In & Google Play Billing

Coil for image loading

Hilt for dependency injection

Jetpack Compose, AndroidX libraries, Material 3, and more

All third-party libraries are used under their respective open-source licenses such as Apache License 2.0.

For a full list of libraries used, please refer to our Licenses & Credits page.

🔐 4. Privacy & Data Usage

We value your privacy. Please review our Privacy Policy to understand what data we collect and how it's used.
In short:

We use Firebase Analytics to understand app usage.

Firebase Remote Config allows us to customize content dynamically.

Firebase Messaging is used to send notifications.

We display ads through Google AdMob.

We do not sell your personal data or access sensitive permissions without your consent.

📱 5. Permissions

To provide functionality, the app requests the following permissions:

INTERNET & Network Access (to fetch wallpapers)

Storage Access & Wallpaper Setting (to save and set wallpapers)

Notification Access (to send push notifications)

Ad Identifier (for personalized ads via AdMob)

All permissions are only used to support app features and enhance user experience.

💳 6. In-App Purchases

Some features, such as access to premium/exclusive wallpapers or ad removal, may be offered via in-app purchases. All purchases are handled through Google Play Billing and are subject to Google’s payment policies.

📬 7. Contact

If you have any questions, copyright concerns, or feature requests, feel free to contact us:

✉️ Email: ${AppConfig.SUPPORT_EMAIL}

🌐 Website: ${AppConfig.URL_TERMS_OF_USE}

📌 8. Changes to These Terms

We may update these Terms of Use from time to time. Changes will be posted on this page, and your continued use of the app implies agreement with the latest version.
    """.trimIndent()
    
    /**
     * Licenses & Credits content
     */
    val licensesAndCredits: String get() = """
        ## ${AppConfig.APP_NAME} – Licenses & Credits

        ### Wallpapers

        ${AppConfig.APP_NAME} provides a curated collection of high-quality wallpapers sourced from various platforms:

        #### Exclusive Wallpapers

        Some wallpapers in this app are **exclusively created by the developer** of ${AppConfig.APP_NAME}. These are original works and are **not available anywhere else**. Redistribution or commercial use of these exclusive wallpapers is **strictly prohibited** without prior permission.

        #### Sourced Wallpapers

        Other wallpapers are collected from publicly available sources for personal use:

        * HDQWalls
        * WallpaperAccess
        * Twitter (various creators)
        * Unsplash _(licensed under Unsplash License — free for commercial and personal use)_
        * Official manufacturer websites for brand-themed wallpapers

        **Disclaimer:** All wallpapers remain the property of their respective owners. If you own any content and wish it to be removed, please contact us at **${AppConfig.SUPPORT_EMAIL}** and we will take immediate action.

        ### Open Source Libraries

        Below is the list of open-source libraries used in this app, along with their respective licenses:

        #### Firebase SDKs (Firestore, Auth, Analytics, Messaging, Storage)

        * Firebase by Google
        * **License:** Apache License 2.0

        #### Google AdMob SDK

        * AdMob by Google
        * **License:** Google APIs Terms of Service

        #### Google Play Billing Library

        * **License:** Google Play Billing Terms

        #### Google Play Services Auth

        * For Google Sign-In
        * **License:** Google APIs Terms of Service

        #### Android Jetpack Libraries

        Includes:

        * androidx.core, lifecycle, room, navigation, datastore, palette, etc.
        * **License:** Apache License 2.0

        #### Jetpack Compose + Material 3 + Icons

        * For UI components and system integration
        * **License:** Apache License 2.0

        #### Coil (Image Loading)

        * Coil
        * **License:** Apache License 2.0

        #### Accompanist Libraries

        Used for pager, swipe refresh, and system UI controller

        * Accompanist by Google
        * **License:** Apache License 2.0

        #### Hilt (Dependency Injection)

        * Hilt by Google
        * **License:** Apache License 2.0

        #### Coroutines

        * Kotlin Coroutines
        * **License:** Apache License 2.0

        #### Material Dialogs (if used)

        * Material Dialogs
        * **License:** Apache License 2.0

        ### Contact

        If you have any questions, copyright concerns, or issues, please contact us:

        * **Email:** ${AppConfig.SUPPORT_EMAIL}
        * **Website:** ${AppConfig.URL_LICENSES}
    """.trimIndent()
    
    /**
     * Changelog content - can be updated as needed
     */
    val changelog: String get() = """
        # Changelog

        ## Version 1.5.0 (Latest)
        - Added new exclusive wallpapers for ${AppConfig.DEVICE_FAMILY_NAME}
        - Improved home screen and lock screen preview UI
        - Enhanced support for Android 14
        - Fixed notification handling
        - Improved app performance and stability

        ## Version 1.4.0
        - Added dark mode support
        - Enhanced UI with Material 3 design
        - Added wallpaper categories
        - Fixed bugs related to downloads

        ## Version 1.3.0
        - Added premium features
        - Improved wallpaper browsing experience
        - Added favorites sync across devices
        - Enhanced search functionality

        ## Version 1.2.0
        - Added more wallpaper collections
        - Improved image loading and caching
        - Added share functionality
        - Bug fixes and performance improvements

        ## Version 1.1.0
        - Initial release with basic functionality
        - Basic wallpaper browsing and setting
    """.trimIndent()
    
    /**
     * App information for bug reports
     */
    fun getDeviceInfo(): String {
        val manufacturer = android.os.Build.MANUFACTURER
        val model = android.os.Build.MODEL
        val version = android.os.Build.VERSION.SDK_INT
        val versionRelease = android.os.Build.VERSION.RELEASE
        
        return """
            Device Information:
            - Manufacturer: $manufacturer
            - Model: $model
            - Android Version: $versionRelease (API $version)
            - App Version: ${android.os.Build.VERSION.RELEASE}
        """.trimIndent()
    }
} 