package com.droidates.wallpapers.core.data.preferences

import androidx.compose.runtime.staticCompositionLocalOf

val LocalUserPreferences = staticCompositionLocalOf<UserPreferences> {
    error("No UserPreferences provided")
} 