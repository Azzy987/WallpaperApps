package com.droidates.wallpapers.ui.screens.tabs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material3.*
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.*
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.ui.Alignment
import kotlinx.coroutines.launch
import com.droidates.wallpapers.config.AppConfig
import com.droidates.wallpapers.navigation.NavigationState
import com.droidates.wallpapers.utils.AdManager
import com.droidates.wallpapers.utils.LocalAdManager
import com.droidates.wallpapers.utils.PaginationUtils
import com.droidates.wallpapers.viewmodel.FavoritesViewModel
import com.droidates.wallpapers.viewmodel.TrendingViewModel
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import android.util.Log
import com.droidates.wallpapers.ui.components.NoInternetConnectionScreen
import com.droidates.wallpapers.ui.components.PullToRefreshContent
import com.droidates.wallpapers.utils.LocalNetworkUtils
import kotlinx.coroutines.delay
import com.droidates.wallpapers.ui.components.ScrollToTopButton
import android.widget.Toast
import com.droidates.wallpapers.ui.components.FastWallpaperCard
import com.droidates.wallpapers.ui.components.LoadingItem
import com.droidates.wallpapers.ui.components.EndOfListIndicator
import com.droidates.wallpapers.ui.components.NativeAdCard
import com.droidates.wallpapers.ui.components.ViewportAwareNativeAdCard

private const val TAG = "TrendingTabScreen"

// Object to store scroll positions across recompositions when tab state changes
private object TrendingTabScrollStates {
    var scrollPosition = 0
    var itemIndex = 0
    var isInitialized = false
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TrendingTabScreen(
    navigationState: NavigationState,
    viewModel: TrendingViewModel = hiltViewModel(),
    favoritesViewModel: FavoritesViewModel = hiltViewModel(),
    adManager: AdManager = LocalAdManager.current,
    sortTrigger: Int = 0,
    currentSortOption: com.droidates.wallpapers.utils.SortOption = com.droidates.wallpapers.utils.SortOption.LATEST,
) {
    var isTabVisible by remember { mutableStateOf(false) }

    // Check if this tab is currently visible
    LaunchedEffect(navigationState.getCurrentTab()) {
        val currentTab = navigationState.getCurrentTab()
        isTabVisible = (currentTab == 2) // Trending tab is index 2
        
        // Load data only when tab becomes visible
        if (isTabVisible) {
            viewModel.loadInitialDataIfNeeded()
        }
    }
    
    // Listen for sort trigger changes and apply sort immediately with loading indicator
    LaunchedEffect(sortTrigger) {
        if (sortTrigger > 0 && isTabVisible) {
            Log.d(TAG, "Sort trigger changed: $sortTrigger, applying sort option: $currentSortOption")
            viewModel.applySortOption(currentSortOption)
        }
    }
    
    // Show loading indicator if tab is not visible or data hasn't loaded yet
    val wallpapers by viewModel.trendingWallpapers.collectAsState()
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

    // Only show loading if data hasn't loaded and is actually loading
    if (wallpapers.isEmpty() && isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
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
                    text = "Loading trending wallpapers...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        }
        return
    }

    // ULTIMATE FIX: Use standard rememberLazyGridState for better performance
    val scrollState = rememberLazyGridState(
        initialFirstVisibleItemIndex = TrendingTabScrollStates.itemIndex,
        initialFirstVisibleItemScrollOffset = TrendingTabScrollStates.scrollPosition
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

    // Network utilities for checking connection status
    val networkUtils = LocalNetworkUtils.current
    val isConnected by networkUtils.isConnected.collectAsState()
    val isSlowConnection by networkUtils.isSlowConnection.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    var isRefreshing by remember { mutableStateOf(false) }

    // Ad Manager for native ads
    val adManager = LocalAdManager.current

    // OPTIMIZED: Simplified scroll position tracking - reduce main thread work
    LaunchedEffect(scrollState.firstVisibleItemIndex) {
        // Only save major scroll changes to reduce write frequency
        if (wallpapers.isNotEmpty() && !isLoading) {
            // Only update every few items to reduce overhead
            if (scrollState.firstVisibleItemIndex != TrendingTabScrollStates.itemIndex &&
                scrollState.firstVisibleItemIndex % 5 == 0) {
            TrendingTabScrollStates.itemIndex = scrollState.firstVisibleItemIndex
            TrendingTabScrollStates.scrollPosition = scrollState.firstVisibleItemScrollOffset
            TrendingTabScrollStates.isInitialized = true
            }
        }
    }

    // FIXED: Use PaginationUtils for consistent pagination behavior across the app
    val shouldLoadMore = PaginationUtils.shouldLoadMore(
        gridState = scrollState,
        itemCount = wallpapers.size,
        isLoading = isLoading,
        hasReachedEnd = hasReachedEnd
    )

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) {
            viewModel.loadMoreWallpapers()
        }
    }
    
    // Fix for pull-to-refresh: update isRefreshing state when loading is complete
    LaunchedEffect(isLoading) {
        if (!isLoading && isRefreshing) {
            delay(300)
            isRefreshing = false
            Log.d(TAG, "Refresh completed, hiding indicator")
        }
    }
    
    // Function to refresh content
    val refreshContent = {
        Log.d(TAG, "Refreshing trending wallpapers")
        isRefreshing = true
        viewModel.refreshWallpapers()
    }
    
    // Check for network connection issues
    if (!isConnected) {
        NoInternetConnectionScreen(
            onRetryClick = {
                Log.d(TAG, "Retry clicked - forcing network state refresh and reloading")
                networkUtils.refreshNetworkState()
                refreshContent()
                scope.launch {
                    Toast.makeText(context, "Checking connection and reloading...", Toast.LENGTH_SHORT).show()
                }
            }
        )
        return
    }

    PullToRefreshContent(
        isRefreshing = isRefreshing,
        onRefresh = refreshContent
    ) {
            Box(modifier = Modifier.fillMaxSize()) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                state = scrollState,
                contentPadding = PaddingValues(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                // Wallpaper items with native ads injected every 16 items (consistent with HomeTab)
                items(
                    count = wallpapers.size + (wallpapers.size / 16), // Extra space for native ads
                    key = { index ->
                        val adjustedIndex = index - (index / 17) // Account for ads in numbering
                        if (index % 17 == 16) {
                            "trending_native_ad_${index / 17}"
                        } else if (adjustedIndex < wallpapers.size) {
                            "trending_wallpaper_$adjustedIndex"
                        } else {
                            "trending_wallpaper_$index"
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
                        // PERFORMANCE FIX: Cache both native ad AND the composable per position
                        val adIndex = index / 17

                        // PERFORMANCE: Use pre-cached ads only
                        key(adIndex) {
                            val nativeAd = remember(adIndex) {
                                adManager.getCachedNativeAd(adIndex)
                            }

                            nativeAd?.let { ad ->
                                NativeAdCard(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 200.dp)
                                        .padding(horizontal = 4.dp),
                                    nativeAd = ad
                                )
                            }
                            // UX IMPROVEMENT: No fallback container when no ad is available
                            // This eliminates unprofessional blank containers and reduces scroll lag
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
                                sourceScreen = AppConfig.SOURCE_TRENDING,
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