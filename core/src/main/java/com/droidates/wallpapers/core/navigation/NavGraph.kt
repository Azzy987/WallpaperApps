package com.droidates.wallpapers.core.navigation

import android.net.Uri
import android.util.Log
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.droidates.wallpapers.core.ui.theme.Material3Motion
import com.droidates.wallpapers.core.ui.components.ErrorBoundary
import com.droidates.wallpapers.core.ui.screens.CategoryScreen
import com.droidates.wallpapers.core.ui.screens.DetailScreen
import com.droidates.wallpapers.core.ui.screens.EditWallpaperScreen
import com.droidates.wallpapers.core.ui.screens.MainScreen
import com.droidates.wallpapers.core.ui.screens.PremiumScreen
import com.droidates.wallpapers.core.ui.screens.SettingsScreen

private const val TAG = "NavGraph"
// Control logging verbosity
private const val VERBOSE_LOGGING = false

@Composable
fun NavGraph(
    navController: NavHostController,
    navigationState: NavigationState,
    startDestination: String = Screen.Home.route
) {
    ErrorBoundary {
        NavHost(
            navController = navController,
            startDestination = startDestination
        ) {
            // Main screens with bottom navigation
            composable(Screen.Home.route) {
                MainScreen(
                    navigationState = navigationState
                )
            }

            // Category screen
            composable(
                route = Screen.Category.route,
                arguments = listOf(
                    navArgument("categoryName") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val categoryName = backStackEntry.arguments?.getString("categoryName") ?: ""
                if (VERBOSE_LOGGING) {
                    Log.d(TAG, "Navigating to Category: $categoryName")
                }
                CategoryScreen(
                    navigationState = navigationState,
                    categoryName = categoryName
                )
            }

            // Detail screen with optimized hero animation
            composable(
                route = Screen.Detail.route,
                arguments = listOf(
                    navArgument("wallpaperId") { type = NavType.StringType },
                    navArgument("source") { type = NavType.StringType },
                    navArgument("showAd") { type = NavType.BoolType },
                    navArgument("sortOption") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val wallpaperId = backStackEntry.arguments?.getString("wallpaperId") ?: ""
                val source = backStackEntry.arguments?.getString("source") ?: "home"
                val sortOption = backStackEntry.arguments?.getString("sortOption") ?: "LATEST"
                
                if (VERBOSE_LOGGING) {
                    Log.d(TAG, "Navigating to Detail with ID: $wallpaperId from source: $source, sortOption: $sortOption")
                }
                
                DetailScreen(
                    wallpaperId = wallpaperId,
                    source = source,
                    sortOption = sortOption,
                    navigationState = navigationState
                )
            }
            
            // Edit Wallpaper screen
            composable(
                route = Screen.Edit.route,
                arguments = listOf(
                    navArgument("wallpaperId") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val wallpaperId = backStackEntry.arguments?.getString("wallpaperId") ?: ""
                
                if (VERBOSE_LOGGING) {
                    Log.d(TAG, "Navigating to Edit with ID: $wallpaperId")
                }
                
                EditWallpaperScreen(
                    wallpaperId = wallpaperId,
                    navigationState = navigationState
                )
            }

            // Settings and Help screens
            composable(Screen.Settings.route) {
                SettingsScreen(navigationState = navigationState)
            }


            // Premium screen
            composable(Screen.Premium.route) {
                PremiumScreen(navigationState = navigationState)
            }
        }
    }
}