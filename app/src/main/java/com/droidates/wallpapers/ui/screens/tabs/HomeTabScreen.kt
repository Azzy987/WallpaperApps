package com.droidates.wallpapers.ui.screens.tabs

import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.droidates.wallpapers.config.AppConfig
import com.droidates.wallpapers.navigation.NavigationState
import com.droidates.wallpapers.ui.components.EndOfListIndicator
import com.droidates.wallpapers.ui.components.LoadingItem
import com.droidates.wallpapers.ui.components.NetworkStatusBar
import com.droidates.wallpapers.ui.components.NoInternetConnectionScreen
import com.droidates.wallpapers.ui.components.PullToRefreshContent
import com.droidates.wallpapers.ui.components.ScrollToTopButton
import com.droidates.wallpapers.ui.components.FastWallpaperCard
import com.droidates.wallpapers.ui.components.WallpaperCarousel
import com.droidates.wallpapers.ui.components.NativeAdCard
import com.droidates.wallpapers.utils.LocalNetworkUtils
import com.droidates.wallpapers.utils.LocalAdManager
import com.droidates.wallpapers.utils.AdManager
import com.droidates.wallpapers.viewmodel.FavoritesViewModel
import com.droidates.wallpapers.viewmodel.HomeViewModel
import android.util.Log
import androidx.compose.runtime.remember
import android.widget.Toast
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import androidx.compose.ui.platform.LocalContext

// Object to store scroll positions across recompositions when tab state changes
private object HomeTabScrollStates {
    var scrollPosition = 0
    var itemIndex = 0
    var isInitialized = false
    // FIXED: Persistent sort state across tab switches and dialog interaction
    var currentAppliedSortOption: com.droidates.wallpapers.utils.SortOption? = null
}

private const val TAG = "HomeTabScreen"

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeTabScreen(
    navigationState: NavigationState,
    viewModel: HomeViewModel = hiltViewModel(),
    favoritesViewModel: FavoritesViewModel = hiltViewModel(),
    adManager: AdManager = LocalAdManager.current,
    sortTrigger: Int = 0,
    currentSortOption: com.droidates.wallpapers.utils.SortOption = com.droidates.wallpapers.utils.SortOption.LAUNCH_YEAR,
) {
    var isTabVisible by remember { mutableStateOf(true) } // Start as visible for Home tab

    // Check if this tab is currently visible
    LaunchedEffect(navigationState.getCurrentTab()) {
        val currentTab = navigationState.getCurrentTab()
        isTabVisible = (currentTab == 0) // Home tab is index 0
    }
    
    // PERFORMANCE: Load data after UI is fully rendered with optimized timing
    LaunchedEffect(isTabVisible) {
        if (isTabVisible) {
            // STARTUP OPTIMIZATION: Reduced delay for faster loading while still preventing frame skips
            kotlinx.coroutines.delay(200) // Reduced delay for faster loading
            viewModel.loadInitialDataIfNeeded()
        }
    }
    
    // PERFORMANCE: Delay sort application to prevent startup blocking
    LaunchedEffect(isTabVisible, currentSortOption, sortTrigger) {
        if (isTabVisible) {
            // Only apply sort if it has actually changed or if triggered by sort dialog
            val shouldApply = (sortTrigger > 0) || 
                             (HomeTabScrollStates.currentAppliedSortOption != currentSortOption)
            
            if (shouldApply) {
                // STARTUP OPTIMIZATION: Extended delay to allow ViewRootImpl compilation to complete
                kotlinx.coroutines.delay(300) // Reduced delay for faster sort application
                // PRODUCTION PERFORMANCE: Removed sort application logging to prevent I/O overhead
                viewModel.applySortOption(currentSortOption)
                HomeTabScrollStates.currentAppliedSortOption = currentSortOption
            }
        }
    }
    
    // Show loading indicator only if data hasn't loaded yet, not based on tab visibility
    val wallpapers by viewModel.wallpapers.collectAsState()
    val banners by viewModel.banners.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val hasReachedEnd by viewModel.hasReachedEnd.collectAsState()
    val error by viewModel.error.collectAsState()

    // Show error state if load failed and no data available
    if (error != null && wallpapers.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = error!!, style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = { viewModel.retryLoad() }) { Text("Try Again") }
            }
        }
        return
    }

    // PERFORMANCE: Show simplified loading state to prevent heavy UI compilation
    if (wallpapers.isEmpty() && isLoading && !HomeTabScrollStates.isInitialized) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(32.dp),
                color = MaterialTheme.colorScheme.primary
            )
        }
        return
    }

    // ULTIMATE FIX: Remove immediate ViewModel creation - use passed parameters
    // This eliminates the lag caused by Firebase queries during tab creation
    
    // TASK 15: Restore scroll position from persistent storage on first load
    LaunchedEffect(Unit) {
        try {
            if (!HomeTabScrollStates.isInitialized) {
                val savedPosition = navigationState.getSavedScrollPosition(0) // Home tab is index 0
                if (savedPosition > 0) {
                    // Decode the combined position back to index and offset
                    val savedIndex = savedPosition / 1000
                    val savedOffset = savedPosition % 1000
                    
                    HomeTabScrollStates.itemIndex = savedIndex
                    HomeTabScrollStates.scrollPosition = savedOffset
                    HomeTabScrollStates.isInitialized = true
                    
                    // Scroll position restored silently for performance
                } else {
                    HomeTabScrollStates.isInitialized = true
                    // Starting from top position
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "TASK 15: Error restoring scroll position: ${e.message}")
            HomeTabScrollStates.isInitialized = true
        }
    }

    // ULTIMATE FIX: Use standard rememberLazyGridState for better performance
    val scrollState = rememberLazyGridState(
        initialFirstVisibleItemIndex = HomeTabScrollStates.itemIndex,
        initialFirstVisibleItemScrollOffset = HomeTabScrollStates.scrollPosition
    )

    // SCROLL PERFORMANCE FIX: Monitor scroll state to pause ad preloading during scroll
    val isScrolling by remember {
        derivedStateOf {
            scrollState.isScrollInProgress
        }
    }

    // Update AdManager's global scroll state to pause preloading during scroll
    LaunchedEffect(isScrolling) {
        adManager.setGlobalScrollActive(isScrolling)
    }

    val scope = rememberCoroutineScope()
    val showScrollToTop = remember {
        derivedStateOf {
            scrollState.firstVisibleItemIndex >= 3
        }
    }

    // OPTIMIZED: Simplified scroll position tracking - reduce main thread work
    LaunchedEffect(scrollState) { // Only key on scrollState to reduce recompositions
        snapshotFlow { scrollState.firstVisibleItemIndex }
            .filter { currentIndex ->
                wallpapers.isNotEmpty() &&
                !isLoading &&
                HomeTabScrollStates.isInitialized &&
                currentIndex != HomeTabScrollStates.itemIndex && // Avoid redundant updates
                currentIndex % 10 == 0 // Update every 10 items for better performance
            }
            .distinctUntilChanged()
            .collect { currentIndex ->
                HomeTabScrollStates.itemIndex = currentIndex
                HomeTabScrollStates.scrollPosition = scrollState.firstVisibleItemScrollOffset
            }
    }

    // ULTIMATE FIX: Optimized shouldLoadMore calculation
    val shouldLoadMore = remember(wallpapers.size, isLoading, hasReachedEnd) {
        derivedStateOf {
            !isLoading && !hasReachedEnd && wallpapers.isNotEmpty() &&
                    scrollState.layoutInfo.visibleItemsInfo.lastOrNull()?.index?.let { lastIndex ->
                        lastIndex >= wallpapers.size - 3
                    } ?: false
        }
    }

    LaunchedEffect(shouldLoadMore.value) {
        if (shouldLoadMore.value) {
            viewModel.loadMoreWallpapers()
        }
    }

    // Network status
    val networkUtils = LocalNetworkUtils.current
    val isConnected by networkUtils.isConnected.collectAsState()
    val isSlowConnection by networkUtils.isSlowConnection.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    // SCROLL PERFORMANCE FIX: Aggressively preload ads in the background
    // This ensures ads are ready before user scrolls to them (no stuttering)
    LaunchedEffect(Unit) {
        // Trigger aggressive preloading once on screen load
        adManager.aggressivelyPreloadNativeAds()
    }

    // Handle no internet connection
    if (!isConnected) {
        // No internet connection
        NoInternetConnectionScreen(
            onRetryClick = {
                // Retry clicked - refreshing network and reloading
                networkUtils.refreshNetworkState()
                viewModel.refreshWallpapers(forceRefresh = true)
                viewModel.refreshBanners(forceRefresh = true)
                scope.launch {
                    Toast.makeText(context, "Checking connection and reloading...", Toast.LENGTH_SHORT).show()
                }
            }
        )
        return
    }

    // Main content with pull-to-refresh
    PullToRefreshContent(
        isRefreshing = false,
        onRefresh = {
            // Pull to refresh triggered
            viewModel.refreshWallpapers(forceRefresh = true)
            viewModel.refreshBanners(forceRefresh = true)
        }
    ) {
            Box(modifier = Modifier.fillMaxSize()) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        state = scrollState,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 4.dp)
                            ) {
                                WallpaperCarousel(
                                    banners = banners,
                                    navigationState = navigationState
                                )
                            }
                        }

                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Text(
                                text = "Official iPhone Drops",
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }

                        // SCROLL PERFORMANCE FIX: Native ads injected every 16 wallpapers
                        items(
                            count = wallpapers.size + (wallpapers.size / 16), // Add 1 ad slot for every 16 wallpapers
                            key = { index ->
                                val adsBefore = index / 17 // Number of ads before this index
                                val adjustedIndex = index - adsBefore // Wallpaper index after removing ads
                                if (index % 17 == 16) {
                                    "native_ad_${adsBefore}"
                                } else if (adjustedIndex < wallpapers.size) {
                                    wallpapers[adjustedIndex].id
                                } else {
                                    "home_wallpaper_$index"
                                }
                            },
                            contentType = { index ->
                                if (index % 17 == 16) "native_ad" else "wallpaper_card"
                            },
                            // Native ads span full width for better visibility (industry standard)
                            span = { index ->
                                if (index % 17 == 16) GridItemSpan(2) else GridItemSpan(1)
                            }
                        ) { index ->
                            if (index % 17 == 16) {
                                // Show native ad every 16 wallpapers (at positions 16, 33, 50, 67, etc.)
                                // CRITICAL PERFORMANCE FIX: Use only pre-cached ads during scroll
                                val adIndex = index / 17

                                // PERFORMANCE: Use pre-cached ads only
                                key(adIndex) {
                                    val nativeAd = remember(adIndex) {
                                        adManager.getCachedNativeAd(adIndex)
                                    }

                                    nativeAd?.let { ad ->
                                        // FIXED: Always show the ad like Instagram/TikTok
                                        NativeAdCard(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .heightIn(min = 200.dp)
                                                .padding(horizontal = 4.dp),
                                            nativeAd = ad
                                        )
                                    }
                                    // UX IMPROVEMENT: No fallback container when no ad is available
                                }
                            } else {
                                // Show wallpaper
                                val adjustedIndex = index - (index / 17) // Account for ads every 17 positions
                                if (adjustedIndex < wallpapers.size) {
                                    val wallpaper = wallpapers[adjustedIndex]
                                    FastWallpaperCard(
                                        wallpaper = wallpaper,
                                        favoritesViewModel = favoritesViewModel,
                                        navigationState = navigationState,
                                        sourceScreen = AppConfig.SOURCE_HOME,
                                        sortOption = currentSortOption.name,
                                    )
                                }
                            }
                        }

                        if (isLoading) {
                            item(span = { GridItemSpan(2) }) {
                                LoadingItem()
                            }
                        }

                        if (hasReachedEnd && wallpapers.isNotEmpty()) {
                            item(span = { GridItemSpan(2) }) {
                                EndOfListIndicator()
                        }
                    }
                }

                NetworkStatusBar(
                    isConnected = isConnected,
                    isSlowConnection = isSlowConnection,
                    modifier = Modifier.align(Alignment.TopCenter)
                )

                ScrollToTopButton(
                    visible = showScrollToTop.value,
                    onClick = {
                        scope.launch {
                            scrollState.animateScrollToItem(0)
                        }
                    },
                    modifier = Modifier.align(Alignment.BottomEnd)
                )
        }
    }
}
