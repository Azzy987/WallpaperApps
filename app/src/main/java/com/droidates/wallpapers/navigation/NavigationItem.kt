package com.droidates.wallpapers.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Help
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.ui.graphics.vector.ImageVector

sealed class NavigationItem(
    val route: String,
    val title: String,
    val icon: ImageVector
) {
    data object OnePlus7Wallpapers : NavigationItem("home", "OnePlus 7 Wallpapers", Icons.Rounded.Home)
    data object Settings : NavigationItem("settings", "Settings", Icons.Rounded.Settings)
    data object RateUs : NavigationItem("rate_us", "Rate Us", Icons.Rounded.Star)
    data object More : NavigationItem("more", "More Apps", Icons.Rounded.Apps)
    data object Help : NavigationItem("help", "Help", Icons.AutoMirrored.Rounded.Help)
}

val navigationItems = listOf(
    NavigationItem.OnePlus7Wallpapers,
    NavigationItem.Settings,
    NavigationItem.RateUs,
    NavigationItem.More,
    NavigationItem.Help
) 