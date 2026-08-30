package com.droidates.wallpapers.core.ui.screens.tabs

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
import androidx.compose.ui.res.stringResource
import com.droidates.wallpapers.core.R
import com.droidates.wallpapers.core.config.AppConfig
import com.droidates.wallpapers.core.navigation.NavigationState
import com.droidates.wallpapers.core.ui.components.EndOfListIndicator
import com.droidates.wallpapers.core.ui.components.LoadingItem
import com.droidates.wallpapers.core.ui.components.NetworkStatusBar
import com.droidates.wallpapers.core.ui.components.NoInternetConnectionScreen
import com.droidates.wallpapers.core.ui.components.PullToRefreshContent
import com.droidates.wallpapers.core.ui.components.ScrollToTopButton
import com.droidates.wallpapers.core.ui.components.FastWallpaperCard
import com.droidates.wallpapers.core.ui.components.WallpaperCarousel
import com.droidates.wallpapers.core.utils.LocalNetworkUtils
import com.droidates.wallpapers.core.utils.LocalAdManager
import com.droidates.wallpapers.core.utils.AdManager
import com.droidates.wallpapers.core.viewmodel.FavoritesViewModel
import com.droidates.wallpapers.core.viewmodel.HomeViewModel
import android.util.Log
import androidx.compose.runtime.remember
import android.widget.Toast
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId

// Object to store scroll positions across recompositions when tab state changes
private object HomeTabScrollStates {
    var scrollPosition = 0
    var itemIndex = 0
    var isInitialized = false
    // FIXED: Persistent sort state across tab switches and dialog interaction
    var currentAppliedSortOption: com.droidates.wallpapers.core.utils.SortOption? = null
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
    currentSortOption: com.droidates.wallpapers.core.utils.SortOption =
        // Not LAUNCH_YEAR unconditionally: apps without that field would order by it
        // and Firestore would return nothing.
        if (com.droidates.wallpapers.core.config.AppConfig.SUPPORTS_LAUNCH_YEAR_SORT)
            com.droidates.wallpapers.core.utils.SortOption.LAUNCH_YEAR
        else com.droidates.wallpapers.core.utils.SortOption.LATEST,
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
    val favoriteIds by favoritesViewModel.favoriteIds.collectAsState()

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

    val scope = rememberCoroutineScope()
    val showScrollToTop = remember {
        derivedStateOf {
            scrollState.firstVisibleItemIndex >= 3
        }
    }

    LaunchedEffect(scrollState) {
        snapshotFlow {
            scrollState.firstVisibleItemIndex to scrollState.firstVisibleItemScrollOffset
        }
            .filter {
                wallpapers.isNotEmpty() &&
                    !isLoading &&
                    HomeTabScrollStates.isInitialized
            }
            .distinctUntilChanged()
            .collect { (currentIndex, currentOffset) ->
                val previousIndex = HomeTabScrollStates.itemIndex
                HomeTabScrollStates.itemIndex = currentIndex
                HomeTabScrollStates.scrollPosition = currentOffset
                if (currentIndex != previousIndex && currentIndex % 5 == 0) {
                    navigationState.saveScrollPosition(currentIndex * 1000 + currentOffset.coerceIn(0, 999))
                }
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
                        modifier = Modifier
                            .fillMaxSize()
                            .semantics { testTagsAsResourceId = true }
                            .testTag("home_wallpaper_grid")
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
                                text = stringResource(R.string.home_wallpapers_section_title, AppConfig.CATEGORY_BRAND_NAME),
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }

                        items(
                            count = wallpapers.size,
                            key = { index -> wallpapers[index].id.ifEmpty { "home_wallpaper_$index" } },
                            contentType = { "wallpaper_card" }
                        ) { index ->
                            val wallpaper = wallpapers[index]
                            FastWallpaperCard(
                                wallpaper = wallpaper,
                                favoritesViewModel = favoritesViewModel,
                                navigationState = navigationState,
                                sourceScreen = AppConfig.SOURCE_HOME,
                                sortOption = currentSortOption.name,
                                isFavoriteOverride = favoriteIds.contains(wallpaper.id),
                                onFavoriteToggle = favoritesViewModel::toggleFavorite,
                                benchmarkTag = "wallpaper_card",
                            )
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
