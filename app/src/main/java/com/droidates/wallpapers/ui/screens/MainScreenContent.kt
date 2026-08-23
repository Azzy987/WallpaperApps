package com.droidates.wallpapers.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import com.droidates.wallpapers.navigation.NavigationState
import com.droidates.wallpapers.ui.components.BottomNavigationBar
import com.droidates.wallpapers.ui.components.CustomTabBar
import com.droidates.wallpapers.ui.screens.tabs.CategoriesTabScreen
import com.droidates.wallpapers.ui.screens.tabs.FavoritesTabScreen
import com.droidates.wallpapers.ui.screens.tabs.HomeTabScreen
import com.droidates.wallpapers.ui.screens.tabs.TrendingTabScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import android.util.Log
import kotlin.math.abs


private const val TAG = "MainScreenContent"

// Control logging verbosity to reduce log spam
private const val VERBOSE_LOGGING = false



@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreenContent(
    padding: PaddingValues,
    navigationState: NavigationState,
    scrollBehavior: TopAppBarScrollBehavior,
    sortTrigger: Int = 0,
    currentSortOption: com.droidates.wallpapers.utils.SortOption = com.droidates.wallpapers.utils.SortOption.LAUNCH_YEAR,
) {
    // SIMPLIFIED TAB NAVIGATION: Add circular motion support
    var selectedTab by remember { mutableIntStateOf(0) }
    
    val tabCount = 4
    val virtualPageCount = Int.MAX_VALUE // Large number for infinite scrolling
    val startIndex = virtualPageCount / 2 - (virtualPageCount / 2) % tabCount // Start at a position divisible by tabCount
    
    val pagerState = rememberPagerState(
        initialPage = startIndex,
        pageCount = { virtualPageCount }
    )
    val scope = rememberCoroutineScope()

    // PERFORMANCE: Initialize tab immediately after first frame
    LaunchedEffect(Unit) {
        delay(16) // Single frame delay to ensure smooth startup
        selectedTab = 0
        navigationState.setCurrentTab(0)
        if (VERBOSE_LOGGING) Log.d(TAG, "Initialized app with Home tab (0)")
    }

    // FIXED: Prevent feedback loop to stop tab indicator flickering
    var isUpdatingFromPager by remember { mutableStateOf(false) }
    var isUpdatingFromTabBar by remember { mutableStateOf(false) }
    
    // Pager to tab synchronization - only when not updating from tab bar
    LaunchedEffect(pagerState.currentPage) {
        if (!isUpdatingFromTabBar) {
            val actualPage = pagerState.currentPage % tabCount
            if (selectedTab != actualPage) {
                isUpdatingFromPager = true
                selectedTab = actualPage
                navigationState.setCurrentTab(actualPage)
                if (VERBOSE_LOGGING) Log.d(TAG, "Pager changed tab to $actualPage")
                isUpdatingFromPager = false
            }
        }
    }
    
    // CRITICAL FIX: Sync pager with navigation state changes (e.g., from back navigation)
    LaunchedEffect(navigationState.getCurrentTab()) {
        val navTab = navigationState.getCurrentTab()
        if (selectedTab != navTab) {
            Log.d(TAG, "Navigation state changed from $selectedTab to $navTab, forcing sync")
            isUpdatingFromTabBar = true
            selectedTab = navTab
            scope.launch {
                // Calculate target page to maintain current position in circular pager
                val currentPage = pagerState.currentPage
                val currentTab = currentPage % tabCount
                val targetPage = currentPage + (navTab - currentTab + tabCount) % tabCount
                pagerState.animateScrollToPage(targetPage)
                // Add delay to ensure UI updates properly
                delay(100)
                isUpdatingFromTabBar = false
            }
        }
    }
    
    // Additional sync to force tab indicator to match content when returning from detail
    LaunchedEffect(pagerState.isScrollInProgress) {
        if (!pagerState.isScrollInProgress) {
            val actualTab = pagerState.currentPage % tabCount
            if (selectedTab != actualTab && !isUpdatingFromTabBar) {
                selectedTab = actualTab
                navigationState.setCurrentTab(actualTab)
                Log.d(TAG, "Post-scroll sync: forcing tab indicator to match content: $actualTab")
            }
        }
    }
    
    // ENHANCED FIX: Force tab sync whenever we detect a mismatch
    LaunchedEffect(pagerState.currentPage, selectedTab) {
        val actualTab = pagerState.currentPage % tabCount
        val navTab = navigationState.getCurrentTab()
        if (actualTab != selectedTab || actualTab != navTab) {
            Log.d(TAG, "Detected mismatch - Pager: $actualTab, Selected: $selectedTab, Nav: $navTab. Forcing sync.")
            if (!isUpdatingFromTabBar && !isUpdatingFromPager) {
                selectedTab = actualTab
                navigationState.setCurrentTab(actualTab)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .nestedScroll(scrollBehavior.nestedScrollConnection)
        ) {
            // Top section: CustomTabBar with divider
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column {
                    CustomTabBar(
                        selectedTab = selectedTab,
                        onTabSelected = { newTab ->
                            if (selectedTab != newTab && !isUpdatingFromPager) {
                                isUpdatingFromTabBar = true
                            selectedTab = newTab
                            navigationState.setCurrentTab(newTab)
                            scope.launch {
                                    // Calculate the target page to minimize scrolling distance
                                    val currentPage = pagerState.currentPage
                                    val currentTab = currentPage % tabCount
                                    val targetPage = currentPage + (newTab - currentTab + tabCount) % tabCount
                                    pagerState.animateScrollToPage(targetPage)
                                    isUpdatingFromTabBar = false
                                }
                            }
                        }
                    )
                    // Material 3 Compliant: Subtle surface elevation with accent tint for visual separation
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                            )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
    
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                pageSpacing = 0.dp,
                userScrollEnabled = true
            ) { page ->
                // Circular page mapping
                val actualPage = page % tabCount
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .animateContentSize()
                ) {
                    // PERSISTENCE: Load all tabs to maintain state when switching
                    val shouldLoadTab = true // Always load all tabs for persistence
                    
                    if (shouldLoadTab) {
                        when (actualPage) {
                            0 -> HomeTabScreen(
                                navigationState = navigationState,
                                sortTrigger = sortTrigger,
                                currentSortOption = currentSortOption
                            )
                            1 -> CategoriesTabScreen(navigationState = navigationState)
                            2 -> TrendingTabScreen(
                                navigationState = navigationState,
                                sortTrigger = sortTrigger,
                                currentSortOption = currentSortOption
                            )
                            3 -> FavoritesTabScreen(navigationState = navigationState)
                        }
                    } else {
                        // Show empty placeholder for unloaded tabs
                        Box(modifier = Modifier.fillMaxSize())
                    }
                }
            }
        }
    }
}
