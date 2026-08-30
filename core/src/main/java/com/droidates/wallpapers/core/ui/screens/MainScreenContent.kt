package com.droidates.wallpapers.core.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import com.droidates.wallpapers.core.navigation.NavigationState
import com.droidates.wallpapers.core.ui.components.BannerAd
import com.droidates.wallpapers.core.ui.components.CustomTabBar
import com.droidates.wallpapers.core.ui.screens.tabs.CategoriesTabScreen
import com.droidates.wallpapers.core.ui.screens.tabs.FavoritesTabScreen
import com.droidates.wallpapers.core.ui.screens.tabs.HomeTabScreen
import com.droidates.wallpapers.core.ui.screens.tabs.TrendingTabScreen
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.launch
import android.util.Log
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.abs


private const val TAG = "MainScreenContent"

private const val VERBOSE_LOGGING = false


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreenContent(
    padding: PaddingValues,
    navigationState: NavigationState,
    scrollBehavior: TopAppBarScrollBehavior,
    sortTrigger: Int = 0,
    currentSortOption: com.droidates.wallpapers.core.utils.SortOption =
        // Not LAUNCH_YEAR unconditionally: apps without that field would order by it
        // and Firestore would return nothing.
        if (com.droidates.wallpapers.core.config.AppConfig.SUPPORTS_LAUNCH_YEAR_SORT)
            com.droidates.wallpapers.core.utils.SortOption.LAUNCH_YEAR
        else com.droidates.wallpapers.core.utils.SortOption.LATEST,
) {
    val currentTab = navigationState.currentTabIndex

    val tabCount = 4
    val virtualPageCount = Int.MAX_VALUE
    val startIndex = virtualPageCount / 2 - (virtualPageCount / 2) % tabCount

    val pagerState = rememberPagerState(
        initialPage = startIndex + currentTab,
        pageCount = { virtualPageCount }
    )
    val scope = rememberCoroutineScope()
    var allowPagerToDriveTab by remember { mutableStateOf(false) }

    val indicatorTab by remember {
        derivedStateOf {
            if (pagerState.isScrollInProgress) {
                pagerState.targetPage % tabCount
            } else {
                pagerState.settledPage % tabCount
            }
        }
    }

    LaunchedEffect(currentTab) {
        val pagerTab = pagerState.settledPage % tabCount
        if (pagerTab != currentTab) {
            val targetPage = pagerState.currentPage + (currentTab - pagerTab + tabCount) % tabCount
            pagerState.scrollToPage(targetPage)
        }
        allowPagerToDriveTab = true
    }

    LaunchedEffect(pagerState, allowPagerToDriveTab) {
        if (!allowPagerToDriveTab) return@LaunchedEffect
        snapshotFlow { pagerState.settledPage % tabCount }
            .distinctUntilChanged()
            .collect { settledTab ->
                if (navigationState.currentTabIndex != settledTab) {
                    navigationState.setCurrentTab(settledTab)
                    if (VERBOSE_LOGGING) Log.d(TAG, "Pager settled on tab $settledTab")
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
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column {
                    CustomTabBar(
                        selectedTab = indicatorTab,
                        onTabSelected = { newTab ->
                            if (indicatorTab != newTab) {
                                navigationState.setCurrentTab(newTab)
                                scope.launch {
                                    val currentPage = pagerState.currentPage
                                    val currentTabFromPager = currentPage % tabCount
                                    val forward = (newTab - currentTabFromPager + tabCount) % tabCount
                                    val backward = forward - tabCount
                                    val shortestDelta = if (abs(forward) <= abs(backward)) {
                                        forward
                                    } else {
                                        backward
                                    }
                                    pagerState.animateScrollToPage(currentPage + shortestDelta)
                                }
                            }
                        }
                    )
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
                beyondViewportPageCount = tabCount - 1,
                userScrollEnabled = true
            ) { page ->
                val actualPage = page % tabCount
                Box(modifier = Modifier.fillMaxSize()) {
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
                }
            }

            BannerAd(modifier = Modifier.fillMaxWidth())
        }
    }
}
