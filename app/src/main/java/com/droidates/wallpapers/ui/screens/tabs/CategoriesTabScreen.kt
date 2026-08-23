package com.droidates.wallpapers.ui.screens.tabs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.droidates.wallpapers.navigation.NavigationState
import androidx.hilt.navigation.compose.hiltViewModel
import com.droidates.wallpapers.viewmodel.CategoriesViewModel
import com.droidates.wallpapers.ui.components.CategoryCard
import androidx.compose.runtime.remember
import android.util.Log
import com.droidates.wallpapers.ui.components.NoInternetConnectionScreen
import com.droidates.wallpapers.ui.components.PullToRefreshContent
import com.droidates.wallpapers.ui.components.ScrollToTopButton
import com.droidates.wallpapers.utils.LocalNetworkUtils
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow // Added for LaunchedEffect improvement
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import android.widget.Toast

// Object to store scroll positions across recompositions when tab state changes
private object CategoriesTabScrollStates {
    var scrollPosition = 0
    var isInitialized = false
    var hasInitiallyLoaded = false
    
    // Removed unused fun clearScrollStates()
}

private const val TAG = "CategoriesTabScreen"

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CategoriesTabScreen(
    navigationState: NavigationState,
    viewModel: CategoriesViewModel = hiltViewModel()
) {
    var isTabVisible by remember { mutableStateOf(false) } 
    
    // Check if this tab is currently visible
    LaunchedEffect(navigationState.getCurrentTab()) {
        val currentTab = navigationState.getCurrentTab()
        isTabVisible = (currentTab == 1) // Categories tab is index 1
        
        // Update ViewModel with tab visibility for persistence
        viewModel.setTabVisibility(isTabVisible)
        
        // Load data only when tab becomes visible
        if (isTabVisible) {
            viewModel.loadInitialDataIfNeeded()
        }
    }
    
    // Ensure data is loaded when tab becomes visible
    LaunchedEffect(isTabVisible) {
        if (isTabVisible && !CategoriesTabScrollStates.hasInitiallyLoaded) {
            viewModel.loadInitialDataIfNeeded()
            CategoriesTabScrollStates.hasInitiallyLoaded = true
        }
    }
    
    // Show loading indicator only if data hasn't loaded yet, not based on tab visibility
    val categories by viewModel.categories.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    
    // Only show loading if data hasn't loaded and is actually loading
    if (categories.isEmpty() && isLoading) {
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
                    text = "Loading categories...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        }
        return
    }
    
    val scrollState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    
    // TASK 15: Restore scroll position from persistent storage on first load
    LaunchedEffect(Unit) {
        try {
            if (!CategoriesTabScrollStates.isInitialized) {
                // FIXED: Don't restore scroll position on app restart - always start from top
                // This ensures the screen always starts from the top when app is restarted
                Log.d(TAG, "Starting categories tab from top (scroll position reset)")
                try {
                    scrollState.scrollToItem(0)
                    CategoriesTabScrollStates.scrollPosition = 0
                    CategoriesTabScrollStates.isInitialized = true
                } catch (scrollException: Exception) {
                    if (scrollException !is CancellationException) {
                        Log.e(TAG, "Error scrolling to top", scrollException)
                    }
                    // Still mark as initialized to prevent retry loops
                    CategoriesTabScrollStates.isInitialized = true
                }
            }
        } catch (e: Exception) {
            if (e !is CancellationException) {
                Log.e(TAG, "Error initializing scroll position", e)
            }
            // Mark as initialized to prevent retry loops
            CategoriesTabScrollStates.isInitialized = true
        }
    }
    
    // Save scroll position when it changes - Fixed coroutine cancellation
    LaunchedEffect(scrollState) {
        try {
            snapshotFlow { scrollState.firstVisibleItemIndex }
                .collect { currentPosition ->
                    try {
                        if (currentPosition > 0) {
                            CategoriesTabScrollStates.scrollPosition = currentPosition
                            navigationState.saveScrollPosition(currentPosition)
                        }
                    } catch (e: Exception) {
                        if (e !is CancellationException) {
                            Log.e(TAG, "Error saving scroll position", e)
                        }
                    }
                }
        } catch (e: Exception) {
            // Handle coroutine cancellation gracefully
            if (e !is CancellationException) {
                Log.e(TAG, "Error initializing scroll position monitoring", e)
            }
        }
    }
    
    // Show scroll to top button when user has scrolled down
    val showScrollToTop = remember {
        derivedStateOf {
            scrollState.firstVisibleItemIndex >= 2
        }
    }

    // Network utilities for checking connection status
    val networkUtils = LocalNetworkUtils.current
    val isConnected by networkUtils.isConnected.collectAsState()
    
    // Get context for Toast messages
    val context = androidx.compose.ui.platform.LocalContext.current
    
    // Refreshing state for pull-to-refresh
    var isRefreshing by remember { mutableStateOf(false) }
    
    // Function to refresh content
    val refreshContent = {
        Log.d(TAG, "Refreshing categories - pull to refresh triggered")
        isRefreshing = true
        viewModel.loadCategories()
    }
    
    // Update refreshing state when loading is done
    LaunchedEffect(isLoading) {
        if (!isLoading && isRefreshing) {
            isRefreshing = false
            Log.d(TAG, "Categories refresh completed")
        }
    }
    
    // Handle no internet connection
    if (!isConnected) {
        Log.d(TAG, "No internet connection")
        NoInternetConnectionScreen(
            onRetryClick = {
                Log.d(TAG, "Retry clicked - forcing network state refresh and reloading")
                // Force refresh network state
                networkUtils.refreshNetworkState()
                
                // Retry loading data regardless of current connection state
                viewModel.loadCategories()
                
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
    
    // Main content with pull-to-refresh
    PullToRefreshContent(
        isRefreshing = isRefreshing,
        onRefresh = refreshContent
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (categories.isEmpty()) {
                Text(
                    text = "No categories available",
                    modifier = Modifier.align(Alignment.Center),
                    style = MaterialTheme.typography.bodyLarge
                )
            } else {
                LazyColumn(
                    state = scrollState,
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(
                        items = categories,
                        key = { it.id }
                    ) { category ->
                        CategoryCard(
                            category = category,
                            navigationState = navigationState,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    
                    // Add end of list indicator for categories
                    if (categories.isNotEmpty() && !isLoading) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth(0.8f),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                                    ),
                                    shape = RoundedCornerShape(24.dp)
                                ) {
                                    Text(
                                        text = "All categories loaded",
                                        modifier = Modifier
                                            .padding(vertical = 12.dp, horizontal = 16.dp)
                                            .align(Alignment.CenterHorizontally),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
            
            // Add ScrollToTopButton
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