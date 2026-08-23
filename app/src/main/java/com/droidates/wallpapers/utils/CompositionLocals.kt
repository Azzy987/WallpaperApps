package com.droidates.wallpapers.utils

import androidx.compose.runtime.compositionLocalOf
import com.droidates.wallpapers.data.preferences.UserPreferences
import com.droidates.wallpapers.ui.components.ToastManager
// FirebaseRemoteConfig import removed

// Composition local for AdManager
val LocalAdManager = compositionLocalOf<AdManager> {
    error("No AdManager provided")
}

// RemoteConfig composition local removed as part of remote config cleanup

// Composition local for UserPreferences
val LocalUserPreferences = compositionLocalOf<UserPreferences> {
    error("No UserPreferences provided")
}

// Composition local for ToastManager
val LocalToastManager = compositionLocalOf<ToastManager> {
    error("No ToastManager provided")
}

// Composition local for NetworkUtils
val LocalNetworkUtils = compositionLocalOf<NetworkUtils> {
    error("No NetworkUtils provided")
} 