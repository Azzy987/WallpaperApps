@file:OptIn(ExperimentalMaterial3Api::class)

package com.droidates.wallpapers.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.*
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.droidates.wallpapers.viewmodel.CategoryViewModel
import com.droidates.wallpapers.viewmodel.FavoritesViewModel
import com.droidates.wallpapers.navigation.NavigationState
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import com.droidates.wallpapers.ui.components.WallpaperGrid
import com.droidates.wallpapers.utils.ScrollUtils
import com.droidates.wallpapers.utils.PaginationUtils
import com.droidates.wallpapers.utils.AdManager
import com.droidates.wallpapers.utils.LocalAdManager
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import com.droidates.wallpapers.R
import android.util.Log
// Removed accompanist use swipe refresh imports - using built-in alternatives
import com.droidates.wallpapers.utils.LocalNetworkUtils
import com.droidates.wallpapers.ui.components.NoInternetConnectionScreen
import com.droidates.wallpapers.ui.components.SeriesFilterDialog
import android.widget.Toast
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import com.droidates.wallpapers.config.AppConfig

// Apple now uses HorizontalPager for smooth navigation

// Create a companion object to save scroll state for categories
object CategoryScreenScrollStates {
    // Map to store scroll positions by category name
    val scrollPositions = mutableMapOf<String, Pair<Int, Int>>()

    fun clearScrollPositions() {
        scrollPositions.clear()
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CategoryScreen(
    navigationState: NavigationState,
    categoryName: String,
    viewModel: CategoryViewModel = hiltViewModel(),
    favoritesViewModel: FavoritesViewModel = hiltViewModel(),
    adManager: AdManager = LocalAdManager.current
) {
    val categoryWallpapers by viewModel.categoryWallpapers.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val hasReachedEnd by viewModel.hasReachedEnd.collectAsState()
    val currentLoadedCategory by viewModel.currentCategory.collectAsState()

    // Persist grid-states by sub-category (null = "All")
    val gridStates =
        remember { mutableStateMapOf<String?, androidx.compose.foundation.lazy.grid.LazyGridState>() }
        
    // APPLE CATEGORY FIX: Individual grid states for each series to prevent freezing
    val seriesGridStates = remember { 
        mutableStateMapOf<String?, androidx.compose.foundation.lazy.grid.LazyGridState>() 
    }

    // Get saved scroll position for this category (for Apple category only)
    val savedScrollPosition = CategoryScreenScrollStates.scrollPositions[categoryName] ?: Pair(0, 0)

    // Use remembered scroll state with saved position (for Apple category only)
    val scrollState = rememberLazyGridState(
        initialFirstVisibleItemIndex = savedScrollPosition.first,
        initialFirstVisibleItemScrollOffset = savedScrollPosition.second
    )

    // Save scroll position whenever it changes (for Apple category only)
    LaunchedEffect(
        remember { derivedStateOf { scrollState.firstVisibleItemIndex } },
        remember { derivedStateOf { scrollState.firstVisibleItemScrollOffset } }) {
        if (categoryWallpapers.isNotEmpty()) {
            CategoryScreenScrollStates.scrollPositions[categoryName] = Pair(
                scrollState.firstVisibleItemIndex,
                scrollState.firstVisibleItemScrollOffset
            )
        }
    }

    val scope = rememberCoroutineScope()
    val showScrollToTop = ScrollUtils.rememberScrollToTopState(scrollState)

    // Removed swipe refresh dependency - using simple refresh mechanism

    // State for selected Apple series
    var selectedSeries by rememberSaveable { mutableStateOf<String?>(null) }

    // State for selected subcategory
    var selectedSubcategory by rememberSaveable { mutableStateOf<String?>(null) }

    // Get available series from the ViewModel
    val availableSeries by viewModel.availableSeries.collectAsState()

    // Get available subcategories for this category
    val subcategories by viewModel.subcategories.collectAsState()

    // State for depth effect info dialog
    var showDepthEffectDialog by rememberSaveable { mutableStateOf(false) }

    // State for Apple series filter dialog
    var showSeriesFilterDialog by rememberSaveable { mutableStateOf(false) }

    // Add pagination check (for Apple category only)
    val shouldLoadMore = PaginationUtils.shouldLoadMore(
        gridState = scrollState,
        itemCount = categoryWallpapers.size,
        isLoading = isLoading,
        hasReachedEnd = hasReachedEnd
    )

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) {
            viewModel.loadMoreWallpapers()
        }
    }

    // Add state for initial fetch complete
    val initialLoadComplete = remember { mutableStateOf(false) }

    LaunchedEffect(categoryName) {
        // STATE PERSISTENCE FIX: Only reset if actually changing categories
        if (currentLoadedCategory != categoryName) {
            Log.d(
                "CategoryScreen",
                "Category changed from $currentLoadedCategory to $categoryName, resetting state"
            )
            selectedSeries = null
            selectedSubcategory = null

            initialLoadComplete.value = false

            // Force immediate state clearing to prevent flash of wrong wallpapers
            viewModel.clearWallpapers()

            // This prevents showing wrong wallpapers from previous category navigation
            viewModel.fetchCategoryByName(categoryName)
        } else {
            Log.d(
                "CategoryScreen",
                "Same category ($categoryName), preserving state and scroll position"
            )
            // Same category - don't reload data, just ensure we show existing content
            if (categoryWallpapers.isNotEmpty()) {
                initialLoadComplete.value = true
            } else {
                // If no wallpapers but same category, we need to reload
                Log.d("CategoryScreen", "Same category but no wallpapers, reloading...")
                initialLoadComplete.value = false
                viewModel.fetchCategoryByName(categoryName)
            }
        }
    }

    // Removed swipe refresh state management

    // Set initial load complete after first successful load
    LaunchedEffect(isLoading, categoryWallpapers.isEmpty()) {
        if (!isLoading && !categoryWallpapers.isEmpty()) {
            initialLoadComplete.value = true
        }
    }

    // Update initial load complete when hasReachedEnd is true
    LaunchedEffect(hasReachedEnd) {
        if (hasReachedEnd) {
            initialLoadComplete.value = true
        }
    }

    // Show depth effect info dialog if needed
    if (showDepthEffectDialog) {
        DepthEffectInfoDialog(
            onDismiss = { showDepthEffectDialog = false }
        )
    }

    // Show Apple series filter dialog if needed
    if (showSeriesFilterDialog) {
        SeriesFilterDialog(
            availableSeries = availableSeries,
            currentSeries = selectedSeries,
            onDismiss = { showSeriesFilterDialog = false },
            onSeriesSelected = { series ->
                selectedSeries = series
                viewModel.filterBySeries(series)
                showSeriesFilterDialog = false
            }
        )
    }

    // Network utilities for checking connection status
    val networkUtils = LocalNetworkUtils.current
    val isConnected by networkUtils.isConnected.collectAsState()

    // Get context for Toast messages
    val context = androidx.compose.ui.platform.LocalContext.current

    // Handle no internet connection
    if (!isConnected) {
        NoInternetConnectionScreen(
            onRetryClick = {
                // Force refresh network state
                networkUtils.refreshNetworkState()

                // Retry loading data regardless of current connection state
                // This handles potential false negatives in connection detection
                viewModel.refresh()

                // Give user feedback that we're trying
                scope.launch {
                    Toast.makeText(
                        context,
                        "Checking connection and reloading...",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        )
        return
    }

    // Animation state for subcategory transitions
    var isAnimating by remember { mutableStateOf(false) }

    fun navigateToSubcategory(newSubcategory: String?) {
        if (isAnimating) return // Prevent rapid successive swipes
        isAnimating = true

        selectedSubcategory = newSubcategory
        initialLoadComplete.value = false

        // Only refresh if data is not already cached
        if (!viewModel.hasCacheFor(newSubcategory)) {
            if (newSubcategory == null) {
                viewModel.refresh()
            } else {
                viewModel.refreshWithSubcategory(newSubcategory)
            }
        }

        scope.launch {
            // Use spring animation for smoother transitions like other components
            scrollState.animateScrollToItem(
                index = 0,
                scrollOffset = 0
            )
            delay(100) // Minimal delay for spring animations
            isAnimating = false
        }
    }

    // Note: Apple series navigation now handled by HorizontalPager - no manual functions needed

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(categoryName) },
                navigationIcon = {
                    IconButton(onClick = { navigationState.navigateBack() }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back")
                    }
                },
                actions = {
                    // Show filter icon for Apple category
                    if (categoryName.equals(AppConfig.COLLECTION_HOME, ignoreCase = true)) {
                        IconButton(onClick = { showSeriesFilterDialog = true }) {
                            Icon(Icons.Default.FilterList, "Filter Series")
                        }
                    }
                    // Show info icon only for Depth Effect category
                    if (categoryName.equals("Depth Effect", ignoreCase = true)) {
                        IconButton(onClick = { showDepthEffectDialog = true }) {
                            Icon(Icons.Default.Info, "Depth Effect Information")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                modifier = Modifier.shadow(elevation = 4.dp)
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.padding(padding)) {
                // Show subcategories if available and not just "none"
                val validSubcategories = subcategories.filter { it != "none" }
                if (validSubcategories.isNotEmpty() && !categoryName.equals(
                        AppConfig.COLLECTION_HOME,
                        ignoreCase = true
                    )
                ) {
                    // Create a list with "All" + valid subcategories for index calculations
                    val allSubcategoryOptions = remember(validSubcategories) {
                        listOf("All") + validSubcategories
                    }

                    // Get the index of the currently selected subcategory
                    val selectedIndex = remember(selectedSubcategory, allSubcategoryOptions) {
                        if (selectedSubcategory == null) 0 // "All" is at index 0
                        else allSubcategoryOptions.indexOf(selectedSubcategory).takeIf { it != -1 }
                            ?: 0
                    }

                    // Display subcategory chips in a row for selection
                    val subcategoryRowState = rememberLazyListState()

                    // Auto-scroll to selected item when it changes
                    LaunchedEffect(selectedSubcategory) {
                        val targetIndex = if (selectedSubcategory == null) 0
                        else allSubcategoryOptions.indexOf(selectedSubcategory).takeIf { it != -1 }
                            ?: 0

                        delay(50)
                        subcategoryRowState.animateScrollToItem(
                            index = targetIndex,
                            scrollOffset = -50
                        )
                    }

                    // Setup for HorizontalPager with infinite scrolling
                    val pageCount = allSubcategoryOptions.size
                    val virtualPageCount = Int.MAX_VALUE // Large number for infinite scrolling
                    val startIndex =
                        virtualPageCount / 2 - (virtualPageCount / 2) % pageCount // Start at a position divisible by pageCount

                    // Calculate the initial page based on the selected subcategory
                    val initialPage = startIndex + selectedIndex

                    // Create pager state with the calculated initial page
                    val pagerState = rememberPagerState(
                        initialPage = initialPage,
                        pageCount = { virtualPageCount }
                    )

                    LazyRow(
                        state = subcategoryRowState,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(allSubcategoryOptions.size) { index ->
                            val subcategory = allSubcategoryOptions[index]
                            val isSelected =
                                (subcategory == "All" && selectedSubcategory == null) ||
                                        (subcategory != "All" && selectedSubcategory == subcategory)
                            SubcategoryChip(
                                subcategory = subcategory,
                                isSelected = isSelected,
                                onClick = {
                                    scope.launch {
                                        val targetPage = startIndex + index
                                        pagerState.animateScrollToPage(targetPage)
                                    }
                                }
                            )
                        }
                    }

                    // Sync pager state with selected subcategory (single source of truth)
                    LaunchedEffect(pagerState.currentPage) {
                        val actualPage = pagerState.currentPage % pageCount
                        val newSubcategory =
                            if (actualPage == 0) null else allSubcategoryOptions[actualPage]

                        // Only navigate if the subcategory is actually changing
                        if (newSubcategory != selectedSubcategory) {
                            selectedSubcategory = newSubcategory
                            viewModel.filterBySubcategory(newSubcategory)
                        }
                    }

                    // Content pager for subcategories
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        pageSpacing = 0.dp,
                        userScrollEnabled = true
                    ) { page ->
                        // Map virtual page to actual subcategory index
                        val actualPage = page % pageCount
                        val pageSubcategory =
                            if (actualPage == 0) null else allSubcategoryOptions[actualPage]

                        // Get (or create) the grid-state for this sub-category
                        val pageGridState = gridStates.getOrPut(pageSubcategory) {
                            rememberLazyGridState()
                        }

                        val pageWallpapers by viewModel.wallpapersForSubcategory(pageSubcategory)
                            .collectAsState()
                        val pageIsLoading by viewModel.isLoadingForSubcategory(pageSubcategory)
                            .collectAsState()
                        val pageHasReachedEnd by viewModel.hasReachedEndForSubcategory(
                            pageSubcategory
                        ).collectAsState()

                        val pageShouldLoadMore = PaginationUtils.shouldLoadMore(
                            gridState = pageGridState,
                            itemCount = pageWallpapers.size,
                            isLoading = pageIsLoading,
                            hasReachedEnd = pageHasReachedEnd
                        )

                        LaunchedEffect(pageShouldLoadMore) {
                            if (pageShouldLoadMore) {
                                viewModel.loadMoreWallpapersForSubcategory(pageSubcategory)
                            }
                        }

                        // Show the wallpaper grid for this subcategory
                        Box(modifier = Modifier.fillMaxSize()) {
                            // Fix: Show loading indicator when loading first page
                            if (pageWallpapers.isEmpty() && pageIsLoading) {
                                // Show centered loading indicator when loading first page or when category changes
                                LoadingIndicator(
                                    modifier = Modifier.align(Alignment.Center)
                                )
                            } else if (pageWallpapers.isEmpty()) { // Fix: Redundant condition removed
                                // Show empty state with message specific to subcategory if one is selected
                                val emptyMessage = if (pageSubcategory != null) {
                                    "No wallpapers found for $pageSubcategory subcategory"
                                } else {
                                    "No wallpapers found in this category"
                                }

                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = emptyMessage,
                                        style = MaterialTheme.typography.titleMedium,
                                        textAlign = TextAlign.Center
                                    )

                                    Spacer(modifier = Modifier.height(16.dp))

                                    Button(
                                        onClick = {
                                            if (pageSubcategory != null) {
                                                // Reset subcategory selection
                                                navigateToSubcategory(null)
                                            } else {
                                                // Refresh the category
                                                viewModel.refresh()
                                            }
                                        }
                                    ) {
                                        Text(
                                            text = if (pageSubcategory != null) "Show All Wallpapers" else "Refresh"
                                        )
                                    }
                                }
                            } else {
                                // Show wallpaper grid with loading indicator for pagination
                                WallpaperGrid(
                                    wallpapers = pageWallpapers,
                                    navigationState = navigationState,
                                    favoritesViewModel = favoritesViewModel,
                                    adManager = adManager,
                                    sourceScreen = AppConfig.SOURCE_CATEGORY,
                                    categoryName = categoryName,
                                    isLoading = pageIsLoading, // Show loading state during pagination
                                    hasReachedEnd = pageHasReachedEnd,
                                    gridState = pageGridState, // Use per-page grid state
                                    emptyMessage = if (pageSubcategory != null) {
                                        "No wallpapers found for $pageSubcategory subcategory"
                                    } else {
                                        "No wallpapers found in this category"
                                    },
                                    // Pass a modifier that detects swipe gestures for categories without the pager
                                    modifier = if (categoryName.equals(
                                            AppConfig.COLLECTION_HOME,
                                            ignoreCase = true
                                        ) || !subcategories.any { it != "none" } // Fix: Simplified collection call
                                    ) {
                                        // Use the old swipe detection for Apple or categories without subcategories
                                        Modifier
                                    } else {
                                        // For categories with subcategories, we don't need swipe detection as the pager handles it
                                        Modifier
                                    }
                                )
                            }

                            // Scroll to top button for this page
                            val pageShowScrollToTop =
                                ScrollUtils.rememberScrollToTopState(pageGridState)
                            if (pageShowScrollToTop) {
                                FloatingActionButton(
                                    onClick = {
                                        scope.launch {
                                            pageGridState.animateScrollToItem(0)
                                        }
                                    },
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(16.dp),
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ) {
                                    Icon(Icons.Default.KeyboardArrowUp, "Scroll to top")
                                }
                            }
                        }
                    }
                }

                // Show Apple series list only for Apple category
                if (categoryName.equals(
                        AppConfig.COLLECTION_HOME,
                        ignoreCase = true
                    ) && availableSeries.isNotEmpty()
                ) {
                    // Add state for Apple series LazyRow scroll position
                    val seriesRowState = rememberLazyListState()

                    // Get the index of the currently selected series
                    val selectedSeriesIndex = remember(selectedSeries, availableSeries) {
                        val effectiveSeries = selectedSeries ?: "All Series"
                        availableSeries.indexOf(effectiveSeries).takeIf { it != -1 } ?: 0
                    }

                    // Auto-scroll to selected series when it changes
                    LaunchedEffect(selectedSeries) {
                        // FIXED: Always scroll to selected index, even if it's at the beginning
                        // Animate scroll to make the selected item fully visible
                        delay(50)
                        seriesRowState.animateScrollToItem(
                            index = selectedSeriesIndex,
                            // Scroll with some offset to center the item better
                            scrollOffset = -50
                        )
                    }

                    LazyRow(
                        state = seriesRowState,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(availableSeries) { series ->
                            val isSelected =
                                selectedSeries == series || (selectedSeries == null && series == "All Series")
                            SeriesChip(
                                series = series,
                                isSelected = isSelected,
                                onClick = {
                                    if (selectedSeries == series) {
                                        // If clicking the already selected series, deselect it (show all)
                                        selectedSeries = null
                                        // Reset the loading state to show empty state until data is loaded
                                        initialLoadComplete.value = false
                                        viewModel.filterBySeries(null)
                                    } else {
                                        selectedSeries = series
                                        // Reset the loading state to show empty state until data is loaded
                                        initialLoadComplete.value = false
                                        viewModel.filterBySeries(series)
                                    }
                                }
                            )
                        }
                    }
                }

                // Show wallpapers for Apple category with HorizontalPager (like subcategories)
                if (categoryName.equals(
                        AppConfig.COLLECTION_HOME,
                        ignoreCase = true
                    ) && availableSeries.isNotEmpty()
                ) {
                    // Setup HorizontalPager for Apple series navigation
                    val allSeriesOptions = remember(availableSeries) {
                        listOf("All Series") + availableSeries.filter { it != "All Series" }
                    }

                    val pageCount = allSeriesOptions.size
                    val virtualPageCount = Int.MAX_VALUE
                    val startIndex = virtualPageCount / 2 - (virtualPageCount / 2) % pageCount

                    val selectedSeriesIndex = remember(selectedSeries, allSeriesOptions) {
                        val effectiveSeries = selectedSeries ?: "All Series"
                        allSeriesOptions.indexOf(effectiveSeries).takeIf { it != -1 } ?: 0
                    }

                    val initialPage = startIndex + selectedSeriesIndex

                    val seriesPagerState = rememberPagerState(
                        initialPage = initialPage,
                        pageCount = { virtualPageCount }
                    )

                    // Track if we're programmatically updating (to avoid conflicts)
                    var isProgrammaticUpdate by remember { mutableStateOf(false) }

                    // APPLE CATEGORY FIX: Debounced state synchronization (pager -> selectedSeries)
                    var debounceJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
                    
                    LaunchedEffect(seriesPagerState.currentPage) {
                        // Only sync if this is not a programmatic update
                        if (!isProgrammaticUpdate) {
                            debounceJob?.cancel()
                            debounceJob = launch {
                                delay(100) // Debounce rapid changes
                                val actualPage = seriesPagerState.currentPage % pageCount
                                val newSeries =
                                    if (actualPage == 0) "All Series" else allSeriesOptions[actualPage]

                                if ((newSeries == "All Series" && selectedSeries != null) ||
                                    (newSeries != "All Series" && selectedSeries != newSeries)
                                ) {
                                    selectedSeries = if (newSeries == "All Series") null else newSeries
                                    viewModel.filterBySeries(selectedSeries)
                                }
                            }
                        }
                    }

                    // Sync selected series with pager state (selectedSeries -> pager)
                    LaunchedEffect(selectedSeries, allSeriesOptions) {
                        val effectiveSeries = selectedSeries ?: "All Series"
                        val targetSeriesIndex =
                            allSeriesOptions.indexOf(effectiveSeries).takeIf { it != -1 } ?: 0
                        val targetPage = startIndex + targetSeriesIndex

                        if (seriesPagerState.currentPage != targetPage) {
                            isProgrammaticUpdate = true
                            // Use scrollToPage for instant, exact positioning (no animation)
                            seriesPagerState.scrollToPage(targetPage)
                            // Small delay to ensure state settles
                            delay(50)
                            isProgrammaticUpdate = false
                        }
                    }

                    // Content pager for Apple series
                    HorizontalPager(
                        state = seriesPagerState,
                        modifier = Modifier.fillMaxSize(),
                        pageSpacing = 0.dp,
                        userScrollEnabled = true
                    ) { page ->
                        // Map virtual page to actual series index
                        val actualPage = page % pageCount
                        val pageSeries = if (actualPage == 0) null else allSeriesOptions[actualPage]

                        // APPLE CATEGORY FIX: Use individual series data states
                        val pageWallpapers by viewModel.wallpapersForSeries(pageSeries).collectAsState()
                        val pageIsLoading by viewModel.isLoadingForSeries(pageSeries).collectAsState()
                        val pageHasReachedEnd by viewModel.hasReachedEndForSeries(pageSeries).collectAsState()
                        
                        // Get individual grid state for this series
                        val pageGridState = seriesGridStates.getOrPut(pageSeries) {
                            rememberLazyGridState()
                        }
                        
                        // APPLE CATEGORY FIX: Individual pagination handling for each series
                        val pageShouldLoadMore = PaginationUtils.shouldLoadMore(
                            gridState = pageGridState,
                            itemCount = pageWallpapers.size,
                            isLoading = pageIsLoading,
                            hasReachedEnd = pageHasReachedEnd
                        )

                        LaunchedEffect(pageShouldLoadMore) {
                            if (pageShouldLoadMore) {
                                viewModel.loadMoreWallpapersForSeries(pageSeries)
                            }
                        }

                        Box(modifier = Modifier.fillMaxSize()) {
                            if (pageWallpapers.isNotEmpty() || pageIsLoading) {
                                WallpaperGrid(
                                    wallpapers = pageWallpapers,
                                    navigationState = navigationState,
                                    favoritesViewModel = favoritesViewModel,
                                    adManager = adManager,
                                    sourceScreen = AppConfig.SOURCE_CATEGORY,
                                    categoryName = categoryName,
                                    isLoading = pageIsLoading,
                                    hasReachedEnd = pageHasReachedEnd,
                                    gridState = pageGridState, // APPLE CATEGORY FIX: Use individual grid state
                                    emptyMessage = if (pageSeries != null) "No wallpapers found for $pageSeries series" else "No wallpapers found"
                                )
                            } else {
                                // Empty state
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(32.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = if (pageSeries != null) "No wallpapers found for $pageSeries series" else "No wallpapers found",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center
                                    )

                                    Spacer(modifier = Modifier.height(16.dp))

                                    Button(
                                        onClick = {
                                            if (pageSeries != null) {
                                                selectedSeries = null
                                                viewModel.filterBySeries(null)
                                            } else {
                                                viewModel.refresh()
                                            }
                                        }
                                    ) {
                                        Text(if (pageSeries != null) "Show All Series" else "Refresh")
                                    }
                                }
                            }

                            // Scroll to top button for this page
                            val pageShowScrollToTop =
                                ScrollUtils.rememberScrollToTopState(pageGridState) // APPLE CATEGORY FIX: Use individual grid state
                            if (pageShowScrollToTop) {
                                FloatingActionButton(
                                    onClick = {
                                        scope.launch {
                                            pageGridState.animateScrollToItem(0) // APPLE CATEGORY FIX: Use individual grid state
                                        }
                                    },
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(16.dp),
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ) {
                                    Icon(Icons.Default.KeyboardArrowUp, "Scroll to top")
                                }
                            }
                        }
                    }
                }
                // Show wallpapers for categories without subcategories (excluding Apple)
                else if (validSubcategories.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        // Fix: Show loading indicator when loading first page or when category changes
                        if (categoryWallpapers.isEmpty() && (isLoading || !initialLoadComplete.value)) {
                            // Show centered loading indicator when loading first page or when category changes
                            LoadingIndicator(
                                modifier = Modifier.align(Alignment.Center)
                            )
                        } else if (categoryWallpapers.isEmpty()) { // Fix: Redundant condition removed
                            // Show empty state with message specific to series if one is selected
                            val emptyMessage = if (selectedSeries != null) {
                                "No wallpapers found for $selectedSeries series"
                            } else {
                                "No wallpapers found in this category"
                            }

                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = emptyMessage,
                                    style = MaterialTheme.typography.titleMedium,
                                    textAlign = TextAlign.Center
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                Button(
                                    onClick = {
                                        if (selectedSeries != null) {
                                            // Reset series selection
                                            selectedSeries = null
                                            viewModel.filterBySeries(null)
                                        } else {
                                            // Refresh the category
                                            viewModel.refresh()
                                        }
                                    }
                                ) {
                                    Text(
                                        text = if (selectedSeries != null) "Show All Series" else "Refresh"
                                    )
                                }
                            }
                        } else {
                            // Show wallpaper grid with loading indicator for pagination
                            WallpaperGrid(
                                wallpapers = categoryWallpapers,
                                navigationState = navigationState,
                                favoritesViewModel = favoritesViewModel,
                                adManager = adManager,
                                sourceScreen = AppConfig.SOURCE_CATEGORY,
                                categoryName = categoryName,
                                isLoading = isLoading, // Show loading state during pagination
                                hasReachedEnd = hasReachedEnd,
                                gridState = scrollState,
                                emptyMessage = if (selectedSeries != null) {
                                    "No wallpapers found for $selectedSeries series"
                                } else {
                                    "No wallpapers found in this category"
                                },
                                // Pass a modifier that detects swipe gestures for categories without the pager
                                modifier = if (categoryName.equals(
                                        AppConfig.COLLECTION_HOME,
                                        ignoreCase = true
                                    ) || !subcategories.any { it != "none" } // Fix: Simplified collection call
                                ) {
                                    // Use the old swipe detection for Apple or categories without subcategories
                                    Modifier
                                } else {
                                    // For categories with subcategories, we don't need swipe detection as the pager handles it
                                    Modifier
                                }
                            )
                        }

                        // Scroll to top button
                        if (showScrollToTop) {
                            FloatingActionButton(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(16.dp),
                                onClick = {
                                    scope.launch {
                                        ScrollUtils.smoothScrollToTop(scrollState)
                                    }
                                }
                            ) {
                                Icon(Icons.Default.KeyboardArrowUp, "Scroll to top")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SubcategoryChip(
    subcategory: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    SuggestionChip(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        colors = SuggestionChipDefaults.suggestionChipColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.surfaceVariant,
            labelColor = if (isSelected) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurfaceVariant
        ),
        label = {
            Text(
                text = subcategory,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
        }
    )
}

@Composable
fun SeriesChip(
    series: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    SuggestionChip(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        colors = SuggestionChipDefaults.suggestionChipColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.surfaceVariant,
            labelColor = if (isSelected) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurfaceVariant
        ),
        label = {
            Text(
                text = series,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                textAlign = TextAlign.Center
            )
        }
    )
}

@Composable
fun DepthEffectInfoDialog(
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.depth_effect_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(stringResource(R.string.depth_effect_description))

                Text(stringResource(R.string.depth_effect_step1))

                Text(stringResource(R.string.depth_effect_step2))

                Text(stringResource(R.string.depth_effect_step3))

                Text(stringResource(R.string.depth_effect_step4))

                Text(stringResource(R.string.depth_effect_note))

                Text(stringResource(R.string.depth_effect_tip))
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(stringResource(R.string.got_it))
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(28.dp)
    )
}
