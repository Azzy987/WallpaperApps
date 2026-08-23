package com.droidates.wallpapers.ui.components

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
import com.droidates.wallpapers.BuildConfig
import com.droidates.wallpapers.model.Wallpaper
import com.droidates.wallpapers.navigation.NavigationState
import com.droidates.wallpapers.viewmodel.FavoritesViewModel
import com.droidates.wallpapers.utils.AdManager
import com.droidates.wallpapers.utils.LocalAdManager
import com.droidates.wallpapers.utils.ImageUtils
import com.droidates.wallpapers.utils.GridOptimizationUtils
import androidx.compose.runtime.*
import com.google.android.gms.ads.nativead.NativeAd
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import android.util.Log

// Import the viewport-aware native ad card for performance
import com.droidates.wallpapers.ui.components.ViewportAwareNativeAdCard

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
        
        // OPTIMIZATION: Calculate fixed item height based on screen dimensions and aspect ratio
        // This helps eliminate remeasure operations during scrolling
        val fixedItemHeight = GridOptimizationUtils.calculateFixedItemHeight(
            columnCount = 2,
            aspectRatio = 0.75f, // Aspect ratio for wallpaper cards (width/height)
            horizontalSpacing = 8.dp
        )
        
        // SCROLL PERFORMANCE FIX: Monitor scroll state to pause ad preloading during scroll
        val isScrolling by remember {
            derivedStateOf {
                gridState.isScrollInProgress
            }
        }

        // Update AdManager's global scroll state to pause preloading during scroll
        LaunchedEffect(isScrolling) {
            adManager.setGlobalScrollActive(isScrolling)
        }

        // PERFORMANCE FIX: Stabilize combinedItems - only recompute when wallpapers change
        // Remove hasNativeAd() check to prevent excessive recompositions
        val combinedItems = remember(wallpapers.size) {
            buildCombinedItemsList(wallpapers, true) // Always build with ad slots
        }

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
        ) {
            items(
                count = combinedItems.size,
                // CRASH FIX: Use simple string key instead of complex lambda to prevent Bundle errors
                key = { index ->
                    when (val item = combinedItems[index]) {
                        is GridItem.WallpaperItem -> "wallpaper_${sourceScreen}_${item.wallpaperIndex}"
                        is GridItem.AdItem -> "native_ad_${item.adIndex}"
                    }
                },
                // Native ads span full width for better visibility (industry standard)
                span = { index ->
                    when (combinedItems[index]) {
                        is GridItem.WallpaperItem -> GridItemSpan(1)
                        is GridItem.AdItem -> GridItemSpan(2) // Full width for native ads
                    }
                }
            ) { index ->
                when (val item = combinedItems[index]) {
                    is GridItem.WallpaperItem -> {
                        // ISSUE 3b FIX: Use FastWallpaperCard for better performance and consistency
                        // This consolidates card components to use only the optimized version
                        FastWallpaperCard(
                            modifier = Modifier.height(fixedItemHeight),
                            wallpaper = wallpapers[item.wallpaperIndex],
                            favoritesViewModel = favoritesViewModel,
                            navigationState = navigationState,
                            sourceScreen = sourceScreen,
                            categoryName = categoryName,
                            sortOption = sortOption
                        )
                    }
                    is GridItem.AdItem -> {
                        // SCROLL PERFORMANCE FIX: Only use pre-cached ads, never load during scroll
                        // Aggressive preloading ensures ads are ready before user scrolls to them
                        val nativeAd = remember(item.adIndex) {
                            adManager.getCachedNativeAd(item.adIndex)
                        }

                        nativeAd?.let { ad ->
                            // Native ad spans full width for better visibility (industry standard)
                            ViewportAwareNativeAdCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 200.dp), // Better height for full-width native ads
                                nativeAd = ad,
                                itemIndex = index,
                                lazyGridState = gridState,
                                preloadOffset = 0 // No preload to prevent audio queries during scroll
                            )
                        }
                    }
                }
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

/**
 * Sealed class to represent different types of grid items
 */
sealed class GridItem {
    data class WallpaperItem(val wallpaperIndex: Int) : GridItem()
    data class AdItem(val adIndex: Int) : GridItem()
}

/**
 * Build a combined list of wallpapers and ad positions
 * Injects native ads every 16 wallpapers (consistent with HomeTab and TrendingTab)
 */
private fun buildCombinedItemsList(wallpapers: List<Wallpaper>, hasAds: Boolean): List<GridItem> {
    val combinedItems = mutableListOf<GridItem>()
    var adIndex = 0

    wallpapers.forEachIndexed { index, _ ->
        // Add wallpaper item
        combinedItems.add(GridItem.WallpaperItem(index))

        // Add native ad every 16 wallpapers (consistent with HomeTab and TrendingTab)
        // This ensures uniform ad placement across all screens
        if (hasAds && (index + 1) % 16 == 0) {
            combinedItems.add(GridItem.AdItem(adIndex))
            adIndex++
        }
    }

    return combinedItems
}