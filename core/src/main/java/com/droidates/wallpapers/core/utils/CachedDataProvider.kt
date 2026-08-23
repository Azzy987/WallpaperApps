package com.droidates.wallpapers.core.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import com.droidates.wallpapers.core.model.Wallpaper

/**
 * Composition Local for providing cached wallpaper data throughout the app
 * This prevents recreation of data objects during recomposition
 */
val LocalCachedWallpapers = compositionLocalOf<Map<String, Wallpaper>> { emptyMap() }

/**
 * Provides a cached version of wallpapers to all child composables
 * This significantly reduces the cost of recompositions by ensuring
 * that wallpaper objects are stable and remembered across recompositions
 */
@Composable
fun CachedDataProvider(
    wallpapers: List<Wallpaper>,
    content: @Composable () -> Unit
) {
    // Convert list to map with ID as key for O(1) lookups
    val wallpaperMap = remember(wallpapers) {
        wallpapers.associateBy { it.id }
    }
    
    CompositionLocalProvider(LocalCachedWallpapers provides wallpaperMap) {
        content()
    }
}

/**
 * Extension function to get a cached wallpaper by ID
 * Will return the wallpaper if found, or null if not in cache
 */
fun Map<String, Wallpaper>.getWallpaperById(id: String): Wallpaper? {
    return this[id]
}
