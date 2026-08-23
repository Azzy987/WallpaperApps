package com.droidates.wallpapers.core.ui.screens.tabs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.droidates.wallpapers.core.config.AppConfig
import com.droidates.wallpapers.core.navigation.NavigationState
import androidx.hilt.navigation.compose.hiltViewModel
import com.droidates.wallpapers.core.viewmodel.FavoritesViewModel
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Sync
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.EaseInOutCubic
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.RepeatMode
import androidx.compose.ui.graphics.graphicsLayer
import com.droidates.wallpapers.core.utils.toSafeScale
import androidx.compose.material.icons.filled.HeartBroken
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.background
import com.droidates.wallpapers.core.ui.components.ScrollToTopButton
import com.droidates.wallpapers.core.ui.components.WallpaperGrid
import com.droidates.wallpapers.core.utils.LocalAdManager
import com.droidates.wallpapers.core.utils.LocalNetworkUtils
import com.droidates.wallpapers.core.viewmodel.AuthViewModel
import android.util.Log
import com.droidates.wallpapers.core.ui.components.NoInternetConnectionScreen
import com.droidates.wallpapers.core.ui.components.PullToRefreshContent
import com.droidates.wallpapers.core.ui.components.PremiumRequiredDialog
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.distinctUntilChanged
import android.widget.Toast
import com.droidates.wallpapers.core.ui.theme.rememberReducedMotion

private const val TAG = "FavoritesTabScreen"
private const val VERBOSE_LOGGING = false

private object FavoritesTabScrollStates {
    var scrollPosition = 0
    var itemIndex = 0
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FavoritesTabScreen(
    navigationState: NavigationState,
    viewModel: FavoritesViewModel = hiltViewModel()
) {
    // ULTIMATE FIX: Remove immediate ViewModel creation - use passed parameters
    // This eliminates the lag caused by Firebase queries during tab creation
    
    // Get AuthViewModel only when needed for favorites functionality
    val authViewModel: AuthViewModel = hiltViewModel()
    
    val scrollState = rememberLazyGridState(
        initialFirstVisibleItemIndex = FavoritesTabScrollStates.itemIndex,
        initialFirstVisibleItemScrollOffset = FavoritesTabScrollStates.scrollPosition
    )
    val scope = rememberCoroutineScope()
    val showScrollToTop = remember {
        derivedStateOf {
            scrollState.firstVisibleItemIndex >= 2
        }
    }
    val favorites by viewModel.favoriteWallpapers.collectAsState()
    val isSignedIn by authViewModel.isSignedIn.collectAsState()
    val favoritesCount by authViewModel.favoritesAdded.collectAsState()
    
    // States for UI elements
    var showSignInPrompt by remember { mutableStateOf(false) }
    val context = LocalContext.current

    LaunchedEffect(scrollState) {
        snapshotFlow {
            scrollState.firstVisibleItemIndex to scrollState.firstVisibleItemScrollOffset
        }
            .distinctUntilChanged()
            .collect { (currentIndex, currentOffset) ->
                if (favorites.isNotEmpty()) {
                    FavoritesTabScrollStates.itemIndex = currentIndex
                    FavoritesTabScrollStates.scrollPosition = currentOffset
                }
            }
    }
    
    // Show sign in button only if:
    // 1. User is not signed in
    // 2. There are favorites to display
    // 3. User has scrolled down (making it non-intrusive)
    val showSignInFab = remember {
        derivedStateOf {
            !isSignedIn && 
            favorites.isNotEmpty() && 
            scrollState.firstVisibleItemIndex > 0
        }
    }

    // Check if we should show the sign-in prompt after adding X favorites
    LaunchedEffect(favoritesCount) {
        if (!isSignedIn && favoritesCount >= 5) {
            showSignInPrompt = true
            authViewModel.resetFavoritesCounter()
        }
    }
    
    // Network utilities for checking connection status
    val networkUtils = LocalNetworkUtils.current
    val isConnected by networkUtils.isConnected.collectAsState()
    val isSlowConnection by networkUtils.isSlowConnection.collectAsState()
    var isRefreshing by remember { mutableStateOf(false) }
    val isSyncing by viewModel.isSyncing.collectAsState()
    
    // Ensure the UI updates when sign-in state changes
    LaunchedEffect(key1 = Unit) {
        authViewModel.signInState.collect { state ->
            when (state) {
                is AuthViewModel.SignInState.Success -> {
                    if (VERBOSE_LOGGING) Log.d(TAG, "Sign-in state success in FavoritesTabScreen")
                    // No longer auto-syncing on sign-in
                }
                is AuthViewModel.SignInState.Idle -> {
                    if (VERBOSE_LOGGING) Log.d(TAG, "User signed out in FavoritesTabScreen")
                }
                else -> { /* Handle other states if needed */ }
            }
        }
    }
    
    // Function to refresh content - just refresh UI, no auto-sync
    val refreshContent = {
        if (VERBOSE_LOGGING) Log.d(TAG, "Refreshing favorites - pull to refresh triggered")
        isRefreshing = true
        // Just end refreshing without auto-syncing
        isRefreshing = false
    }
    
    // Add a sync button for explicit user-initiated syncing
    val showSyncButton = remember {
        derivedStateOf {
            isSignedIn && 
            favorites.isNotEmpty() &&
            !isSyncing
        }
    }
    
    // Update refreshing state when sync is done
    LaunchedEffect(isSyncing) {
        if (!isSyncing && isRefreshing) {
            isRefreshing = false
            if (VERBOSE_LOGGING) Log.d(TAG, "Sync completed")
        }
    }
    
    // Handle no internet connection when trying to sync
    var showNoConnectionScreen by remember { mutableStateOf(false) }
    LaunchedEffect(isConnected) {
        showNoConnectionScreen = !isConnected && isSignedIn && isRefreshing
        if (showNoConnectionScreen) {
            if (VERBOSE_LOGGING) Log.d(TAG, "No internet connection when sync is required")
        }
    }
    
    // Add this LaunchedEffect to load favorites from Firestore when screen is shown
    LaunchedEffect(key1 = isSignedIn) {
        if (isSignedIn) {
            if (VERBOSE_LOGGING) Log.d(TAG, "User is signed in, loading favorites from Firestore")
            viewModel.loadFavoritesFromFirestore()
        }
    }
    
    // Add a loading state from view model
    val isLoading by viewModel.isLoading.collectAsState()
    
    if (showNoConnectionScreen) {
        NoInternetConnectionScreen(
            onRetryClick = {
                if (VERBOSE_LOGGING) Log.d(TAG, "Retry clicked - forcing network state refresh and syncing")
                networkUtils.refreshNetworkState()
                viewModel.syncFavoritesToCloud()
                Toast.makeText(context, "Checking connection and syncing...", Toast.LENGTH_SHORT).show()
            }
        )
        return
    }

    // ULTIMATE FIX: Proper loading state for initial load
    if (favorites.isEmpty() && isLoading) {
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
                    text = "Loading Favorites...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        }
        return
    }
    
    // Main content with pull-to-refresh - only enable when signed in
    PullToRefreshContent(
        isRefreshing = isRefreshing,
        onRefresh = refreshContent,
        enabled = isSignedIn
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (favorites.isEmpty() && !isLoading) {
                // FIXED: Animated empty state with broken heart
                EmptyFavoritesState()
            } else {
            WallpaperGrid(
                wallpapers = favorites,
                navigationState = navigationState,
                favoritesViewModel = viewModel,
                adManager = LocalAdManager.current,
                sourceScreen = AppConfig.SOURCE_FAVORITES,
                gridState = scrollState,
                hasReachedEnd = favorites.isNotEmpty(), // Show end indicator when there are favorites
                emptyMessage = "No favorite wallpapers yet"
            )
            }

            // Add loading indicator
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
LoadingIndicator(
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Loading your favorites...",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
            
            // Use our ScrollToTopButton component
            ScrollToTopButton(
                visible = showScrollToTop.value,
                onClick = {
                    scope.launch {
                        scrollState.animateScrollToItem(0)
                    }
                },
                modifier = Modifier.align(Alignment.BottomEnd)
            )
            
            // Sync floating button (only show when signed in and has favorites)
            AnimatedVisibility(
                visible = showSyncButton.value,
                enter = fadeIn() + slideInVertically { it },
                exit = fadeOut() + slideOutVertically { it },
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp)
            ) {
                FloatingActionButton(
                    onClick = { 
                        viewModel.syncFavoritesToCloud() 
                    },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    if (isSyncing) {
LoadingIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = "Sync Favorites"
                        )
                    }
                }
            }
            
            // Sign in floating button (only show if not signed in and has favorites)
            AnimatedVisibility(
                visible = showSignInFab.value,
                enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 }),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp)
            ) {
                Box {
                    ExtendedFloatingActionButton(
                        icon = { Icon(Icons.Default.AccountCircle, contentDescription = null) },
                        text = { Text("Sign in with Google") },
                        onClick = {
                            authViewModel.signIn()
                        },
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            
            // Show premium required dialog
            val showPremiumPrompt by viewModel.showPremiumPrompt.collectAsState()
            if (showPremiumPrompt) {
                PremiumRequiredDialog(
                    onDismiss = { viewModel.resetPremiumPrompt() },
                    onUpgrade = {
                        viewModel.resetPremiumPrompt()
                        navigationState.navigateToPremium()
                    },
                    title = "Premium Required",
                    message = "Sync favorites across devices requires a premium subscription. Upgrade now to sync your favorite wallpapers to all your devices!"
                )
            }
        }
    }
    
    // Sign in prompt dialog
    if (showSignInPrompt) {
        AlertDialog(
            onDismissRequest = { showSignInPrompt = false },
            title = { Text("Sign In Required") },
            text = { Text("Sign in to save your favorite wallpapers to your account") },
            confirmButton = {
                Button(
                    onClick = {
                        showSignInPrompt = false
                        authViewModel.signIn()
                    }
                ) {
                    Text("Sign In")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignInPrompt = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

/**
 * Animated empty state with broken heart for when there are no favorites
 */
@Composable
private fun EmptyFavoritesState() {
    val reducedMotion = rememberReducedMotion()
    val transition = rememberInfiniteTransition(label = "favoritesHeartPulse")
    val heartScale by if (reducedMotion) {
        remember { mutableStateOf(1f) }
    } else {
        transition.animateFloat(
            initialValue = 0.8f,
            targetValue = 1.1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1600, easing = EaseInOutCubic),
                repeatMode = RepeatMode.Reverse
            ),
            label = "HeartScale"
        )
    }
    
    // Animated alpha for the overall content
    val contentAlpha by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 800),
        label = "ContentFade"
    )
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = contentAlpha },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp)
        ) {
            // Animated broken heart icon
            Icon(
                imageVector = Icons.Default.HeartBroken,
                contentDescription = "No favorites",
                modifier = Modifier
                    .size(120.dp)
                    .graphicsLayer {
                        // CRASH FIX: Ensure scale values are never NaN
                        val finalScale = (0.8f + (heartScale * 0.3f)).toSafeScale(min = 0.5f, max = 1.5f)
                        scaleX = finalScale
                        scaleY = finalScale
                    },
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Title text
            Text(
                text = "No Favorites Yet",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Description text
            Text(
                text = "Start exploring wallpapers and tap the heart icon to add them to your favorites!",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
    }
}
