package com.droidates.wallpapers.core.navigation

import com.droidates.wallpapers.core.config.AppConfig
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Help
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.ui.graphics.vector.ImageVector

sealed class NavigationItem(
    val route: String,
    val icon: ImageVector
) {
    /** Resolved per app at call time — the Home entry shows the running app's name. */
    open val title: String get() = staticTitle
    protected open val staticTitle: String get() = ""

    data object Home : NavigationItem("home", Icons.Rounded.Home) {
        override val title: String get() = AppConfig.APP_NAME
    }
    data object Settings : NavigationItem("settings", Icons.Rounded.Settings) { override val staticTitle = "Settings" }
    data object RateUs : NavigationItem("rate_us", Icons.Rounded.Star) { override val staticTitle = "Rate Us" }
    data object More : NavigationItem("more", Icons.Rounded.Apps) { override val staticTitle = "More Apps" }
    data object Help : NavigationItem("help", Icons.AutoMirrored.Rounded.Help) { override val staticTitle = "Help" }
}

val navigationItems = listOf(
    NavigationItem.Home,
    NavigationItem.Settings,
    NavigationItem.RateUs,
    NavigationItem.More,
    NavigationItem.Help
) 