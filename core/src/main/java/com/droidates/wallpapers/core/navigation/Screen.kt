package com.droidates.wallpapers.core.navigation

import android.net.Uri
import androidx.compose.ui.graphics.Color

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Category : Screen("category/{categoryName}") {
        fun createRoute(categoryName: String) = "category/$categoryName"
    }
    data object Detail : Screen("detail/{wallpaperId}/{source}/{showAd}/{sortOption}") {
        fun createRoute(wallpaperId: String, sourceScreen: String, showAd: Boolean = false, sortOption: String = "LATEST") = "detail/$wallpaperId/$sourceScreen/$showAd/$sortOption"
    }
    data object Trending : Screen("trending")
    data object Favorites : Screen("favorites")
    data object Search : Screen("search")
    data object Settings : Screen("settings")
    data object About : Screen("about")
    data object Help : Screen("help")
    
    data object Categories : Screen("categories")
    data object Premium : Screen("premium")
    
    data object FilteredWallpapers : Screen("filtered/{type}/{value}") {
        fun createRoute(type: String, value: String) = "filtered/$type/$value"
    }
    
    data object Edit : Screen("edit/{wallpaperId}") {
        fun createRoute(wallpaperId: String) = "edit/$wallpaperId"
    }
}