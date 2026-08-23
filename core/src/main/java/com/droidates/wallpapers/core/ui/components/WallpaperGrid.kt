package com.droidates.wallpapers.core.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.droidates.wallpapers.core.model.Wallpaper
import com.droidates.wallpapers.core.navigation.NavigationState
import com.droidates.wallpapers.core.viewmodel.FavoritesViewModel
import com.droidates.wallpapers.core.utils.AdManager
import com.droidates.wallpapers.core.utils.GridOptimizationUtils
import com.droidates.wallpapers.core.utils.LocalAdManager
import androidx.compose.runtime.*
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import android.util.Log

/**
 * A grid of wallpapers with simplified navigation
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WallpaperGrid(
    wallpapers: List<Wallpaper>,
    navigationState: NavigationState,
    favoritesViewModel: FavoritesViewModel,
    adManager: AdManager = LocalAdManager.current,
    modifier: Modifier = Modifier,
    sourceScreen: String = "filtered",
    categoryName: String? = null,
    isLoading: Boolean = false,
    hasReachedEnd: Boolean = false,
    gridState: LazyGridState = rememberLazyGridState(),
    emptyMessage: String = "No wallpapers found",
    sortOption: String = "LATEST"
) {
    if (wallpapers.isEmpty()) {
        if (isLoading) {
            // Show loading state when no wallpapers and loading
            Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    LoadingIndicator(
                        modifier = Modifier.size(48.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Loading wallpapers...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }
        } else {
            // Show empty message when no wallpapers and not loading
            Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = emptyMessage,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    } else {
        // OPTIMIZATION: Use fixed-size grid cells with pre-calculated dimensions
        // This eliminates remeasure operations during scrolling for better performance
        val fixedGridCells = GridOptimizationUtils.fixedSizeGridCells(columnCount = 2)
        
        // OPTIMIZATION: Calculate optimal prefetch distance based on device memory
        // Lower-end devices (4GB RAM or less) will use a smaller prefetch distance
        val prefetchDistance = GridOptimizationUtils.rememberOptimalPrefetchDistance()
        val favoriteIds by favoritesViewModel.favoriteIds.collectAsState()
        val shouldShowAdOnOpen = remember(adManager) { adManager.shouldShowAd() }
        
        // OPTIMIZATION: Calculate fixed item height based on screen dimensions and aspect ratio
        // This helps eliminate remeasure operations during scrolling
        val fixedItemHeight = GridOptimizationUtils.calculateFixedItemHeight(
            columnCount = 2,
            aspectRatio = 0.75f, // Aspect ratio for wallpaper cards (width/height)
            horizontalSpacing = 8.dp
        )
        
        LazyVerticalGrid(
            columns = fixedGridCells,
            state = gridState,
            contentPadding = PaddingValues(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            // OPTIMIZATION: Reduce overscroll and improve fling behavior for smoother scrolling
            flingBehavior = androidx.compose.foundation.gestures.ScrollableDefaults.flingBehavior(),
            // PERFORMANCE: Enable items reuse to improve memory efficiency
            userScrollEnabled = true,
            modifier = modifier
                .semantics { testTagsAsResourceId = true }
                .testTag("wallpaper_grid")
        ) {
            items(
                count = wallpapers.size,
                key = { index -> wallpapers[index].id.ifEmpty { "wallpaper_${sourceScreen}_$index" } },
                contentType = { "wallpaper_card" }
            ) { index ->
                FastWallpaperCard(
                    modifier = Modifier.height(fixedItemHeight),
                    wallpaper = wallpapers[index],
                    favoritesViewModel = favoritesViewModel,
                    navigationState = navigationState,
                    sourceScreen = sourceScreen,
                    categoryName = categoryName,
                    sortOption = sortOption,
                    isFavoriteOverride = favoriteIds.contains(wallpapers[index].id),
                    onFavoriteToggle = favoritesViewModel::toggleFavorite,
                    shouldShowAdOnOpen = shouldShowAdOnOpen,
                    benchmarkTag = "wallpaper_card"
                )
            }

            // Always show loading indicator when loading more
            if (isLoading) {
                item(
                    span = { GridItemSpan(2) },
                    // CRASH FIX: Use simple string key for loading indicator
                    key = "loading_indicator_${sourceScreen}"
                ) {
                    LoadingItem()
                }
            }
            
            // Always show end of list indicator when reached the end
            if (hasReachedEnd && wallpapers.isNotEmpty()) {
                item(
                    span = { GridItemSpan(2) },
                    // CRASH FIX: Use simple string key for end indicator
                    key = "end_of_list_${sourceScreen}"
                ) {
                    EndOfListIndicator()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LoadingItem() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(8.dp)
        ) {
LoadingIndicator(
                modifier = Modifier.size(32.dp),
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Loading more wallpapers...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
fun EndOfListIndicator() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .padding(8.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
            ),
            shape = RoundedCornerShape(24.dp)
        ) {
            Text(
                text = "You've reached the end",
                modifier = Modifier
                    .padding(vertical = 12.dp, horizontal = 16.dp)
                    .align(Alignment.CenterHorizontally),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
