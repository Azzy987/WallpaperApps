package com.droidates.wallpapers.ui.screens

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalView
import android.view.HapticFeedbackConstants
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
// PERF FIX: Removed onGloballyPositioned import — replaced with LocalConfiguration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import androidx.compose.animation.core.*
import com.droidates.wallpapers.data.preferences.LocalUserPreferences
import com.droidates.wallpapers.navigation.NavigationState
import com.droidates.wallpapers.ui.components.*
import com.droidates.wallpapers.ui.components.detail.WallpaperPreview
import com.droidates.wallpapers.model.Wallpaper
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material.icons.outlined.ReportProblem
import androidx.core.view.WindowCompat
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.RadioButton
import androidx.compose.ui.semantics.Role
import android.app.Application
import android.os.Bundle
import androidx.compose.material.icons.outlined.Report
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Info
import com.droidates.wallpapers.config.AppConfig
import com.droidates.wallpapers.utils.PreferencesManager
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.material3.SnackbarHostState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import android.Manifest.permission.POST_NOTIFICATIONS
import kotlinx.coroutines.delay
import com.droidates.wallpapers.ui.components.GlassmorphicBox
import com.droidates.wallpapers.ui.components.NoInternetConnectionScreen
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.material.icons.outlined.GridView
import com.droidates.wallpapers.utils.LocalAdManager
import com.droidates.wallpapers.utils.LocalNetworkUtils
import com.droidates.wallpapers.utils.toSafeScale
import com.droidates.wallpapers.utils.toSafeAlpha
import com.droidates.wallpapers.utils.toSafeTranslation
import com.droidates.wallpapers.viewmodel.DetailViewModel
import com.droidates.wallpapers.WallpaperApplication
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.ktx.logEvent
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.compose.material.icons.rounded.Star
import kotlin.math.abs
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.net.toUri

private const val TAG = "DetailScreen"

/**
 * IMPORTANT: Pinch-to-zoom and double-tap functionality have been removed as requested.
 * Changes made:
 * 1. Removed transform gesture detection
 * 2. Changed dynamic scale to constant 1f 
 * 3. Removed zoom-related back handling
 * 4. Replaced tap gesture with simple clickable
 */

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DetailScreen(
    wallpaperId: String,
    source: String,
    sortOption: String = "LATEST",
    navigationState: NavigationState,
) {
    // PERF FIX: Removed top-level Log.d that ran on every recomposition
    // Create ViewModels and managers inside the function to avoid verification issues
    val viewModel: DetailViewModel = hiltViewModel()
    val adManager = LocalAdManager.current
    
    // State to force reload images when wallpaper ID changes - moved to top of function
    val refreshTrigger = remember { mutableStateOf(0) }
    
    val context = LocalContext.current
    val activity = context.findActivity()
    val view = LocalView.current
    // RemoteConfig removed as part of remote config cleanup
    
    // Animation states for favorite button
    var favoriteScale by remember { mutableStateOf(1f) }
    var triggerFavoriteAnimation by remember { mutableStateOf(false) }
    
    // Reset scale after animation
    LaunchedEffect(triggerFavoriteAnimation) {
        if (triggerFavoriteAnimation) {
            favoriteScale = 1.2f
            kotlinx.coroutines.delay(150)
            favoriteScale = 1f
            kotlinx.coroutines.delay(150)
            triggerFavoriteAnimation = false
        }
    }

    // Network utilities for checking connection status
    val networkUtils = LocalNetworkUtils.current
    val isConnected by networkUtils.isConnected.collectAsState()

    // State for tracking detail screen visits - initialize asynchronously to avoid StrictMode violation
    var preferencesManager by remember { mutableStateOf<PreferencesManager?>(null) }
    var detailScreenVisits by remember { mutableStateOf(0) }
    
    // Initialize PreferencesManager asynchronously to avoid StrictMode violation
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val pm = PreferencesManager(context)
            val visits = pm.getDetailScreenVisits()
            withContext(Dispatchers.Main) {
                preferencesManager = pm
                detailScreenVisits = visits
            }
        }
    }

    // State for bottom info container expansion
    var isInfoContainerExpanded by remember { mutableStateOf(false) }

    // Removed zoom functionality - constant values instead of state
    val scale = 1f // Fixed scale - no zooming
    val offset = Offset.Zero // Fixed offset - no panning
    val isZoomed = false // Always false - zoom disabled

    // BOUNCY FIX: No longer need fixed heights - animateContentSize handles everything
    // All container sizing is now dynamic and animated smoothly

    // Track if we're returning from premium screen to avoid showing the unlock dialog again
    var isReturningFromPremiumScreen by remember { mutableStateOf(false) }

    // Store source screen value as an immutable variable to prevent it from being modified
    // Use the same name as the parameter to ensure we're using the correct value
    val sourceScreen = source // This ensures we use the source parameter from navigation
    
    // Debug log to verify source parameter is being received correctly
    LaunchedEffect(Unit) {
        Log.d("DetailScreen", "DetailScreen created with source: $source, wallpaperId: $wallpaperId")
    }

    // Make sure the ViewModel knows which source screen we're coming from
    LaunchedEffect(sourceScreen) {
        Log.d("DetailScreen", "Setting source screen: $sourceScreen")
        viewModel.setSourceScreen(sourceScreen)
    }
    
    // FIXED: Pass sort option to ViewModel for consistent adjacent wallpaper loading
    LaunchedEffect(sortOption) {
        Log.d("DetailScreen", "Setting sort option: $sortOption")
        viewModel.setSortOption(sortOption)
    }

    // Create a scope for async operations
    val coroutineScope = rememberCoroutineScope()
    
    // PERF FIX: Wrap in remember keyed on wallpaperId to avoid calling every recomposition
    val nextWallpaperId: String? = remember(wallpaperId) { viewModel.getNextWallpaperId() }
    val prevWallpaperId: String? = remember(wallpaperId) { viewModel.getPreviousWallpaperId() }
    
    // IMPROVED: Safer handling of cached wallpapers with proper coroutine management
    // We need this rememberCoroutineScope to safely launch coroutines within the composable
    val wallpaperSetupScope = rememberCoroutineScope()
    
    // State for notification permission dialog
    var showNotificationPermissionDialog by remember { mutableStateOf(false) }

    // Notification permission launcher
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        // Just mark as requested regardless of outcome
        preferencesManager?.setHasRequestedNotificationPermission(true)
        Log.d("DetailScreen", "Notification permission granted: $isGranted")
    }

    // Show ad on detail screen open if conditions are met
    LaunchedEffect(Unit) {
        // Using logged count for debugging purposes only
        Log.d("DetailScreen", "Current visit count in AdManager: ${adManager.getDetailScreenVisitCount()}")

        // Also increment persisted counter for analytics
        detailScreenVisits++
        preferencesManager?.saveDetailScreenVisits(detailScreenVisits)

        // Check if we should show notification permission dialog
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPermission = ContextCompat.checkSelfPermission(
                context,
                POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            if (!hasPermission && preferencesManager?.getHasRequestedNotificationPermission() == false &&
                detailScreenVisits % 3 == 0 && detailScreenVisits > 1
            ) {
                showNotificationPermissionDialog = true
            }
        }
    }

    // Add effect to track navigation to premium screen
    LaunchedEffect(navigationState.currentRoute) {
        // If we're returning from premium screen to detail screen, mark the flag
        if (navigationState.previousRoute?.startsWith("premium") == true &&
            navigationState.currentRoute?.startsWith("detail") == true) {
            Log.d("DetailScreen", "Returning from premium screen")
            isReturningFromPremiumScreen = true
        }
    }

    // Handle no internet connection
    if (!isConnected) {
        Log.d("DetailScreen", "No internet connection")
        NoInternetConnectionScreen(
            onRetryClick = {
                Log.d("DetailScreen", "Retry clicked - checking connection and refreshing")
                if (networkUtils.isNetworkAvailable()) {
                    viewModel.loadWallpaper(sourceScreen, wallpaperId)
                }
            }
        )
        return
    }

    // Image loading state - explicitly set to true initially to show loading when coming from notification


    // Track metadata loading separately
    var isMetadataLoading by remember { mutableStateOf(true) }

    // Track if we're coming from a notification
    val isFromNotification = remember { source == "notification" }

    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Get system services and managers
    val userPreferences = LocalUserPreferences.current

    // Get premium status from AdManager
    val isPremiumUser by adManager.isPremiumUser.collectAsState()

    // Track user feedback state
    val hasGivenFeedback by userPreferences.hasGivenFeedback.collectAsState(initial = false)
    val appOpenCount by userPreferences.appOpenCount.collectAsState(initial = 0)
    val lastFeedbackPrompt by userPreferences.lastFeedbackPrompt.collectAsState(initial = 0L)
    val wallpaper by viewModel.wallpaper.collectAsState()
    // PERF FIX: Removed duplicate `currentWallpaper` collector — use `wallpaper` everywhere
    
    // Animated favorite button properties
    val animatedFavoriteScale by animateFloatAsState(
        targetValue = favoriteScale,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "favoriteScale"
    )
    
    val animatedFavoriteColor by animateColorAsState(
        targetValue = if (wallpaper?.isFavorite == true) Color.Red else Color.White,
        animationSpec = tween(durationMillis = 300, easing = EaseOutCubic),
        label = "favoriteColor"
    )
    val isLoading by viewModel.isLoading.collectAsState()
    
    // TASK 14 FIX: Add swipe loading states for progress indicators during wallpaper transitions
    val isLoadingNextWallpaper by viewModel.isLoadingNextWallpaper.collectAsState()
    val isLoadingPrevWallpaper by viewModel.isLoadingPrevWallpaper.collectAsState()
    val isSwipeLoading = isLoadingNextWallpaper || isLoadingPrevWallpaper
    
    // REDUCED LOGGING: Only log when loading states actually change
    LaunchedEffect(isLoadingNextWallpaper, isLoadingPrevWallpaper) {
        if (isLoadingNextWallpaper || isLoadingPrevWallpaper) {
        }
    }
    var isPreviewMode by remember { mutableStateOf(false) }
    var isLockScreenPreview by remember { mutableStateOf(false) }
    var showSetWallpaperSheet by remember { mutableStateOf(false) }
    var showUnlockDialog by remember { mutableStateOf(false) }
    var showFeedbackDialog by remember { mutableStateOf(false) }
    var showReportDialog by remember { mutableStateOf(false) }
    var isLoadingRewardAd by remember { mutableStateOf(true) }
    var hasWatchedAd by remember { mutableStateOf(false) }
    var isEditing by remember { mutableStateOf(false) }
    var isDownloaded by remember { mutableStateOf(false) }

    // TASK 5: Simplified loading states for blur thumbnail approach
    var thumbnailLoaded by remember { mutableStateOf(false) }
    var highResLoaded by remember { mutableStateOf(false) }
    
    var isImageLoading by remember { mutableStateOf(false) } // Start with loading = false to show thumbnail immediately
    var highResLoadingStarted by remember { mutableStateOf(false) }
    var showHighRes by remember { mutableStateOf(false) }
    
    // HERO ANIMATION FIX: Use global state manager to persist state across screen recreations
    // Simple state tracking
    var isInitialized by remember(wallpaperId) { mutableStateOf(false) }
    
    var isCacheDetected by remember { mutableStateOf(false) }
    var loadingStartTime by remember { mutableLongStateOf(0L) }
    var imageLoadingCompleted by remember { mutableStateOf(false) }
    
    // UI VISIBILITY STATE: Add state for tap-to-hide functionality
    var isUiVisible by remember { mutableStateOf(true) }
    
    // Initialize loading state when screen first loads - CACHE-AWARE VERSION
    LaunchedEffect(wallpaperId) {
        Log.d("DetailScreen", "Loading wallpaper: $wallpaperId from source: $source")

        // PERFORMANCE FIX: Removed entry ad (incrementDetailScreenVisitCount + shouldShowAdForDetailScreen)
        // Ads now only show after download/set wallpaper completion — not on screen entry

        // Pause native ad videos to free video decoder resources during detail view
        adManager.pauseNativeAdVideos()

        // Reset all states - but don't start with loading=true to avoid hiding thumbnail
        // isImageLoading will be set by high-res image loading callbacks
        thumbnailLoaded = false
        highResLoaded = false
        showHighRes = false
        imageLoadingCompleted = false
        isUiVisible = true

        // PERF FIX: Removed duplicate loadWallpaper() call — the second LaunchedEffect(wallpaperId)
        // block below calls loadWallpaper(sourceScreen, wallpaperId, updateFlow = true) which is more complete.

        // ANALYTICS: Increment views count in Firebase when wallpaper is loaded
        viewModel.incrementViews(source, wallpaperId)

        // Firebase Analytics: Track wallpaper view
        try {
            val app = context.applicationContext as? WallpaperApplication
            app?.analytics?.logEvent("wallpaper_viewed") {
                param("wallpaper_id", wallpaperId)
                param("source", source)
            }
        } catch (e: Exception) {
            Log.e("DetailScreen", "Error logging analytics: ${e.message}")
        }
    }
    
    // The wallpaper loading will be handled by the ViewModel

    // Use fixed colors instead of animated colors
    val statIconColor = MaterialTheme.colorScheme.primary

    // Track UI visibility state based on whether full image is loaded
    var isUiReady by remember { mutableStateOf(false) }
    
    // Fix for EditWallpaperScreen return: Reset ALL states when returning from edit screen
    LaunchedEffect(key1 = wallpaper?.id) {
        // When wallpaper is loaded, and we have valid data, ensure ALL states are proper
        wallpaper?.let { wp ->
            // If we have wallpaper data, reset everything to show UI properly
            if (wp.id == wallpaperId) {
                // Reset loading completion state
                imageLoadingCompleted = true
                isImageLoading = false
                isUiVisible = true
                Log.d("DetailScreen", "Reset ALL states on return from edit screen for wallpaper: ${wp.id}")
            }
        }
    }

    // Check and update hasWatchedAd state based on premium status and unlock status
    LaunchedEffect(wallpaper, isPremiumUser) {
        // For premium users, always consider content as unlocked
        if (isPremiumUser) {
            hasWatchedAd = true
            Log.d("DetailScreen", "Premium user - content is unlocked")
        } else if (wallpaper?.exclusive == true) {
            // For regular users, check if this specific wallpaper is unlocked in this session only
            hasWatchedAd = viewModel.isWallpaperUnlocked(wallpaperId)
            Log.d("DetailScreen", "Regular user with exclusive content - unlocked in session: $hasWatchedAd")
        } else {
            // Non-exclusive content is always available
            hasWatchedAd = true
            Log.d("DetailScreen", "Non-exclusive content - always unlocked")
        }
    }

    // Separate LaunchedEffect for feedback dialog check
    LaunchedEffect(wallpaperId) {
        // Check if we should show the feedback dialog
        if (!hasGivenFeedback) {
            val currentTime = System.currentTimeMillis()
            val dayInMillis = 24 * 60 * 60 * 1000L

            // Show feedback dialog if:
            // 1. User has opened the app at least 3 times
            // 2. It's been at least 1 day since the last prompt
            // 3. User hasn't given feedback yet
            if (appOpenCount >= 3 && (currentTime - lastFeedbackPrompt > dayInMillis)) {
                showFeedbackDialog = true
                // Update the last feedback prompt time
                userPreferences.setLastFeedbackPrompt(currentTime)
            }
        }
    }

    // Permission handling for both download and edit
    val permissionToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_IMAGES
    } else {
        Manifest.permission.WRITE_EXTERNAL_STORAGE
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            when (viewModel.permissionRequestType) {
                PermissionRequestType.DOWNLOAD -> {
                    wallpaper?.let { wall ->
                        viewModel.downloadWallpaper(
                            context = context,
                            wallpaper = wall,
                            showToast = false,
                            onComplete = {
                                // Show interstitial ad after download completes if user is not premium
                                activity?.let { act ->
                                    // We should show ads for non-premium users, regardless of sign-in state
                                    if (!isPremiumUser) {
                                        Log.d("DetailScreen", "Showing ad after download for non-premium user")
                                        adManager.showInterstitialAd(act)
                                    } else {
                                        Log.d("DetailScreen", "User is premium, skipping ad after download")
                                    }
                                }

                                // Show snackbar message
                                snackbarHostState.currentSnackbarData?.dismiss()
                                scope.launch {
                                    snackbarHostState.showSnackbar("Download complete!")
                                }
                            }
                        )
                    }
                }

                PermissionRequestType.SHARE -> {
                    wallpaper?.let { wall ->
                        viewModel.shareWallpaper(
                            context = context,
                            wallpaper = wall
                        )
                    }
                }

                PermissionRequestType.EDIT -> {
                    wallpaper?.let { wall ->
                        navigationState.navigateToEdit(wall.id)
                    }
                }
            }
        } else {
            // Only show settings dialog if permission is permanently denied
            if (!shouldShowRequestPermissionRationale(context as Activity, permissionToRequest)) {
                showPermissionSettingsDialog(context)
            } else {
                val message = when (viewModel.permissionRequestType) {
                    PermissionRequestType.DOWNLOAD -> "Permission denied. Cannot download wallpaper."
                    PermissionRequestType.SHARE -> "Permission denied. Cannot share wallpaper."
                    PermissionRequestType.EDIT -> "Permission denied. Cannot edit wallpaper."
                }
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun checkAndRequestPermission(type: PermissionRequestType, onGranted: () -> Unit = {}) {
        Log.d("DetailScreen", "Checking permission for: $type")
        viewModel.permissionRequestType = type
        when {
            ContextCompat.checkSelfPermission(
                context,
                permissionToRequest
            ) == PackageManager.PERMISSION_GRANTED -> {
                Log.d("DetailScreen", "Permission already granted for: $type")
                when (type) {
                    PermissionRequestType.DOWNLOAD -> {
                        onGranted()
                    }

                    PermissionRequestType.SHARE -> {
                        onGranted()
                    }

                    PermissionRequestType.EDIT -> {
                        wallpaper?.let { wall ->
                            navigationState.navigateToEdit(wall.id)
                        }
                    }
                }
            }

            shouldShowRequestPermissionRationale(context as Activity, permissionToRequest) -> {
                Log.d("DetailScreen", "Showing permission rationale for: $type")
                showPermissionRationaleDialog(context) {
                    permissionLauncher.launch(permissionToRequest)
                }
            }

            else -> {
                // First time asking for permission
                Log.d("DetailScreen", "Requesting permission for first time: $type")
                permissionLauncher.launch(permissionToRequest)
            }
        }
    }

    // Set FLAG_SECURE to prevent screenshots
    LaunchedEffect(Unit) {
        activity?.window?.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        // Enable edge-to-edge display
        activity?.window?.let { window ->
            WindowCompat.setDecorFitsSystemWindows(window, false)
        }

        // Preload reward ad on launch
        adManager.loadRewardAd {
            isLoadingRewardAd = false
        }

        // Also preload interstitial ad
        adManager.loadInterstitialAd()
    }

    // Clean up FLAG_SECURE when leaving screen
    DisposableEffect(Unit) {
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
    



    // Update the handleDownloadClick function with permission check restored
    fun handleDownloadClick() {
        if (wallpaper?.exclusive == true && !hasWatchedAd && !isPremiumUser) {
            showUnlockDialog = true
        } else {
            // Show toast before starting the download
            Toast.makeText(context, "Starting download...", Toast.LENGTH_SHORT).show()

            // Preload an ad for non-premium users before starting download
            if (!isPremiumUser) {
                Log.d("DetailScreen", "Pre-loading ad for download completion")
                adManager.loadInterstitialAd()
            }

            // Use checkAndRequestPermission with a proper download action
            checkAndRequestPermission(PermissionRequestType.DOWNLOAD) {
                wallpaper?.let { wall ->
                    viewModel.downloadWallpaper(
                        context = context,
                        wallpaper = wall,
                        showToast = false,
                        onComplete = {
                            // Show interstitial ad after download completes if user is not premium
                            activity?.let { act ->
                                // We should show ads for non-premium users, regardless of sign-in state
                                if (!isPremiumUser) {
                                    Log.d("DetailScreen", "Showing ad after download for non-premium user")
                                    adManager.showInterstitialAd(act)
                                } else {
                                    Log.d("DetailScreen", "User is premium, skipping ad after download")
                                }
                            }

                            // Show snackbar message
                            snackbarHostState.currentSnackbarData?.dismiss()
                            scope.launch {
                                snackbarHostState.showSnackbar("Download complete!")
                            }
                        }
                    )
                }
            }
        }
    }

    // Handle set wallpaper click - open full bottom sheet
    fun handleSetWallpaperClick() {
        if (wallpaper?.exclusive == true && !hasWatchedAd && !isPremiumUser) {
            showUnlockDialog = true
        } else {
            showSetWallpaperSheet = true
        }
    }

    // Handle wallpaper setting with ad
    fun handleWallpaperSet(option: WallpaperSetOption) {
        wallpaper?.let { wall ->
            // First check if this is exclusive content that requires watching an ad
            // Skip for premium users
            if (wall.exclusive && !hasWatchedAd && !isPremiumUser) {
                showUnlockDialog = true
                return
            }

            if (option == WallpaperSetOption.EXTERNAL) {
                // Show toast before starting the process
                Toast.makeText(context, "Preparing wallpaper...", Toast.LENGTH_SHORT).show()

                // Set isSettingWallpaper to true to show progress indicator
                viewModel.setIsSettingWallpaper(true)

                // Use specialized download function to avoid UI conflicts
                viewModel.downloadWallpaperForExternal(
                    context = context,
                    wallpaper = wall,
                    onComplete = { filePath ->
                        // Reset isSettingWallpaper when complete
                        viewModel.setIsSettingWallpaper(false)

                        if (filePath.isNotEmpty()) {
                            val file = java.io.File(filePath)
                            if (file.exists()) {
                                // Show toast after preparation is complete
                                Toast.makeText(context, "Opening system wallpaper picker...", Toast.LENGTH_SHORT).show()

                                // Create a flag to track if user has returned to app
                                var hasReturned = false

                                // Register an activity lifecycle callback to show ad when user returns
                                activity?.let { act ->
                                    // Only show ad for non-premium users
                                    if (!isPremiumUser) {
                                        val lifecycleCallbacks =
                                            object : Application.ActivityLifecycleCallbacks {
                                                override fun onActivityResumed(activity: Activity) {
                                                    // Only show ad if this is our activity and user is returning from external app
                                                    if (activity == act && hasReturned) {
                                                        adManager.showInterstitialAd(act)
                                                        // Remove callback after showing ad
                                                        activity.application.unregisterActivityLifecycleCallbacks(
                                                            this
                                                        )
                                                    }
                                                    // Set flag to true after first pause-resume cycle
                                                    hasReturned = true
                                                }

                                                // Empty implementations for other callbacks
                                                override fun onActivityCreated(
                                                    activity: Activity,
                                                    savedInstanceState: Bundle?
                                                ) {}

                                                override fun onActivityStarted(activity: Activity) {}
                                                override fun onActivityPaused(activity: Activity) {}
                                                override fun onActivityStopped(activity: Activity) {}
                                                override fun onActivitySaveInstanceState(
                                                    activity: Activity,
                                                    outState: Bundle
                                                ) {}

                                                override fun onActivityDestroyed(activity: Activity) {
                                                    // Clean up in case activity is destroyed
                                                    activity.application.unregisterActivityLifecycleCallbacks(
                                                        this
                                                    )
                                                }
                                            }

                                        // Register the callback before opening external app
                                        act.application.registerActivityLifecycleCallbacks(
                                            lifecycleCallbacks
                                        )
                                    }
                                }

                                // Open system wallpaper picker with file
                                openDirectWallpaperPicker(file, context, { message ->
                                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                })
                            } else {
                                Toast.makeText(
                                    context,
                                    "Could not find downloaded wallpaper",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        } else {
                            Toast.makeText(
                                context,
                                "Failed to download wallpaper",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                )
            } else {
                // Show toast before starting the process
                Toast.makeText(context, "Setting as wallpaper...", Toast.LENGTH_SHORT).show()

                viewModel.setWallpaper(
                    context = context,
                    wallpaper = wall,
                    option = option,
                    onComplete = {
                        activity?.let { act ->
                            if (!isPremiumUser) {
                                // WALLPAPER AD CRASH FIX: Add delay and lifecycle checks before showing ad
                                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                                    // Wait for wallpaper setting process to complete fully
                                    kotlinx.coroutines.delay(800)
                                    
                                    // Double-check activity state before showing ad
                                    if (!act.isDestroyed && !act.isFinishing && !act.isChangingConfigurations) {
                                        try {
                                            adManager.showInterstitialAd(act)
                                        } catch (e: Exception) {
                                            Log.e("DetailScreen", "Error showing ad after wallpaper set: ${e.message}")
                                        }
                                    } else {
                                        Log.w("DetailScreen", "Activity not ready for ad display after wallpaper set")
                                    }
                                }
                            }
                        }

                        // Show toast after completion
                        val message = when (option) {
                            WallpaperSetOption.HOME_SCREEN -> "Set as home screen wallpaper"
                            WallpaperSetOption.LOCK_SCREEN -> "Set as lock screen wallpaper"
                            WallpaperSetOption.BOTH_SCREENS -> "Set as home and lock screen wallpaper"
                            else -> "Wallpaper set successfully"
                        }
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }

    // ULTRA-OPTIMIZED: Immediate display with parallel loading to eliminate black screen entirely
    // TASK 5: Simplified wallpaper loading with immediate thumbnail display
    LaunchedEffect(wallpaperId) {
        
        // ISSUE 3 FIX: Properly reset all loading states for new wallpaper ID
        thumbnailLoaded = false
        highResLoaded = false
        // Don't reset isImageLoading to true - let high-res loading control it
        
        // Check if wallpaper is cached to potentially skip loading indicator
        val isCached = viewModel.isWallpaperCached(wallpaperId)
        
        // Load wallpaper data in parallel without blocking UI
        scope.launch(Dispatchers.IO) {
            try {
                // Start main data loading
                    viewModel.loadWallpaper(sourceScreen, wallpaperId, updateFlow = true)
                
                // Load adjacent wallpapers in background
                delay(200) // Small delay to prioritize current wallpaper
                    viewModel.forceUpdateAdjacentWallpapers(true)
                
            } catch (e: Exception) {
                Log.e("DetailScreen", "TASK 5: Error during loading: ${e.message}")
            }
        }
    }

    // Properly collect the currentWallpaperIdFlow outside of the LaunchedEffect parameter
    val currentWallpaperIdFromFlow by viewModel.currentWallpaperIdFlow.collectAsState(null)
        
    // Use the updated wallpaper ID from the flow for all operations (but don't navigate)
    val effectiveWallpaperId = currentWallpaperIdFromFlow ?: wallpaperId
    
    // Use DisposableEffect instead of LaunchedEffect for better lifecycle management
    DisposableEffect(effectiveWallpaperId) {
        // Launch in a controlled scope to prevent NullPointerException
        val job = wallpaperSetupScope.launch {
            if (effectiveWallpaperId == null || effectiveWallpaperId.isEmpty()) {
                Log.d("DetailScreen", "Skipping wallpaper setup - invalid ID")
                return@launch
            }
            
            Log.d("DetailScreen", "Setting up wallpaper context for: $effectiveWallpaperId")
            
            try {
                // Check cache state but don't log it as error
                val isCached = viewModel.isWallpaperCached(effectiveWallpaperId)
                
                viewModel.completelyRebuildWallpaperContext(effectiveWallpaperId, sourceScreen)
                
                // Short delay to ensure context is established
                delay(100)
                
                // Update adjacent wallpapers with a safety check
                withContext(Dispatchers.Default) {
                    try {
                        viewModel.forceUpdateAdjacentWallpapers()
                    } catch (e: Exception) {
                        Log.d("DetailScreen", "Non-critical error updating adjacent wallpapers: ${e.message}")
                    }
                }
                
                // Get adjacent IDs with null safety
                val nextId = viewModel.getNextWallpaperId()
                val prevId = viewModel.getPreviousWallpaperId()
                Log.d("DetailScreen", "Setup complete - Next: $nextId, Prev: $prevId")
                
                // Preload adjacent wallpapers in parallel to improve performance
                nextId?.let { nId ->
                    launch {
                        try {
                            viewModel.preloadWallpaper(nId, highPriority = true)
                        } catch (e: Exception) {
                            Log.d("DetailScreen", "Non-critical preload error: ${e.message}")
                        }
                    }
                }
                
                prevId?.let { pId ->
                    launch {
                        try {
                            viewModel.preloadWallpaper(pId, highPriority = true)
            } catch (e: Exception) {
                            Log.d("DetailScreen", "Non-critical preload error: ${e.message}")
            }
        }
    }
            } catch (e: Exception) {
                Log.d("DetailScreen", "Error in wallpaper setup: ${e.message}")
            }
        }
        
        // Make sure to cancel the job when the effect leaves composition
        onDispose {
            job.cancel()
        }
    }
    
    // Log when wallpaper ID changes due to swipe
    LaunchedEffect(currentWallpaperIdFromFlow) {
        val newWallpaperId = currentWallpaperIdFromFlow
        if (newWallpaperId != null && newWallpaperId != wallpaperId) {
            Log.d("DetailScreen", "Wallpaper ID updated from swipe: $newWallpaperId (original: $wallpaperId)")
            // Don't navigate - just use the new ID for current screen operations
        }
    }
    
    // We'll use the existing wallpaper state without modifications
    
    // Track initial entry to the screen for logging
    val initialEntry = remember { mutableStateOf(true) }
    if (initialEntry.value) {
        initialEntry.value = false
    }
    
    // Trigger navigation when the wallpaper ID changes from swipe navigation - REMOVED as swipe now loads directly
    /* REMOVED
    LaunchedEffect(currentWallpaperIdFromFlow) {
        currentWallpaperIdFromFlow?.let { newWallpaperId ->
            if (newWallpaperId != wallpaperId) {
                Log.d("DetailScreen", "Wallpaper ID changed from flow: $newWallpaperId (current: $wallpaperId)")
                // Navigate to the new wallpaper detail screen with the same source
                Log.d("DetailScreen", "Navigating to new wallpaper with source: $sourceScreen")
                navigationState.navigateToDetail(newWallpaperId, sourceScreen)
            }
        }
    }
    */
    val backgroundColor by viewModel.backgroundColor.collectAsState()
    val downloadProgress by viewModel.downloadProgress.collectAsState()
    val isDownloading by viewModel.isDownloading.collectAsState()
    val isSettingWallpaper by viewModel.isSettingWallpaper.collectAsState()
    val isSharing by viewModel.isSharing.collectAsState()

    // Track metadata loading state in a way that prevents UI flickering
    val imageDimensions by viewModel.imageDimensions.collectAsState()
    val imageSize by viewModel.imageSize.collectAsState()
    val localDownloads by viewModel.localDownloads.collectAsState()
    val localViews by viewModel.localViews.collectAsState()

    // Update metadata loading state only once when metadata is available
    LaunchedEffect(imageDimensions, imageSize) {
        if (imageDimensions.isNotEmpty() && imageSize.isNotEmpty() && isMetadataLoading) {
            isMetadataLoading = false
            Log.d("DetailScreen", "Metadata loaded: dimensions=$imageDimensions, size=$imageSize")
        }
    }

    // REMOVED: This was causing progress indicator to disappear before image loads
    // The image loading state should only be controlled by AsyncImage callbacks
    // LaunchedEffect(wallpaper) { ... } - REMOVED

    // Reset loading states when current wallpaper changes
    LaunchedEffect(wallpaper?.id) {
        if (wallpaper?.id != null) {
            Log.d("DetailScreen", "New wallpaper loaded: ${wallpaper?.id}, resetting image loading states")
            // Reset all image loading states for new wallpaper - but don't hide thumbnail
            // isImageLoading will be controlled by high-res loading
            highResLoaded = false
            thumbnailLoaded = false
            imageLoadingCompleted = false
            isCacheDetected = false
            loadingStartTime = 0L
            
            // LOADING INDICATOR FIX: Add safety timeout to prevent stuck loading indicator
            scope.launch {
                delay(3000) // 3 second safety timeout
                if (isImageLoading) {
                    Log.d("DetailScreen", "Safety timeout triggered: Force resetting loading state for ${wallpaper?.id}")
                    isImageLoading = false
                    highResLoaded = true
                    imageLoadingCompleted = true
                }
            }
            
            Log.d("DetailScreen", "Reset loading states for new wallpaper: ${wallpaper?.id}")
        }
    }

    // refreshTrigger is now declared at the top of the function
    LaunchedEffect(effectiveWallpaperId) {
        // Force new load when wallpaper ID changes
        refreshTrigger.value = refreshTrigger.value + 1
        Log.d("DetailScreen", "Refresh trigger updated: ${refreshTrigger.value} for ID: $effectiveWallpaperId")
    }

    // New state for managing transitions
    var isTransitioningWallpaper by remember { mutableStateOf(false) }

    // PERF FIX: Calculate screen dimensions from configuration (no recomposition from onGloballyPositioned)
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenHeight = remember(configuration.screenHeightDp) {
        with(density) { configuration.screenHeightDp.dp.toPx() }
    }
    val screenWidth = remember { context.resources.displayMetrics.widthPixels }

    // Handle back press
    BackHandler {
        // If in preview mode, exit preview mode first
        if (isPreviewMode) {
            isPreviewMode = false
        }
        // Check if we're showing any dialogs that should be closed first
        else if (showSetWallpaperSheet) {
            showSetWallpaperSheet = false
        } else if (showUnlockDialog) {
            showUnlockDialog = false
        } else if (showFeedbackDialog) {
            showFeedbackDialog = false
        } else if (showReportDialog) {
            showReportDialog = false
        }
        // Otherwise navigate back based on source screen
        else {
            Log.d("DetailScreen", "Back button pressed, navigating back from source: $sourceScreen")
            navigationState.navigateBack()
        }
    }

    // Add showInfoDialog state
    var showInfoDialog by remember { mutableStateOf(false) }

    // Track swipe navigation state with simplified animation support
    var swipeOffset by remember { mutableStateOf(0f) }
    var swipeDirection by remember { mutableIntStateOf(0) } // -1 for up, 1 for down, 0 for none
    
    // Simplified animation state variables for smooth swipe effect (no bounce)
    var targetSwipeOffset by remember { mutableFloatStateOf(0f) }
    val animatedSwipeOffset = remember { Animatable(0f) }
    var isSwipeInProgress by remember { mutableStateOf(false) }
    var isAnimatingSwipe by remember { mutableStateOf(false) }
    
    // Enhanced swipe states for YouTube-like behavior
    var dragStartY by remember { mutableStateOf(0f) }
    var dragCurrentY by remember { mutableStateOf(0f) }
    var previewProgress by remember { mutableStateOf(0f) } // 0 to 1 for preview animation
    var lastDragTime by remember { mutableLongStateOf(0L) } // For velocity calculation
    var dragVelocity by remember { mutableStateOf(0f) } // Pixels per ms
    
    // YouTube Shorts-like gesture sensitivity and thresholds
    val swipeThreshold = screenHeight * 0.15f // Lower threshold for more responsive swipes (like YouTube)
    val previewThreshold = screenHeight * 0.05f // Very low threshold to start showing preview immediately
    
    // State for tracking pending wallpaper loading
    var pendingWallpaperLoad by remember { mutableStateOf<(() -> Unit)?>(null) }
    
    // Execute pending wallpaper load if animation is completed
    LaunchedEffect(isAnimatingSwipe) {
        if (!isAnimatingSwipe && pendingWallpaperLoad != null) {
            pendingWallpaperLoad?.invoke()
            pendingWallpaperLoad = null
        }
    }

    // Function to handle navigation with the correct source screen
    fun navigateWithSource(targetId: String) {
        Log.d("DetailScreen", "Navigating to wallpaper: $targetId with source: $sourceScreen")
        navigationState.navigateToDetail(targetId, sourceScreen)
        viewModel.incrementSwipeCount()
    }

    // Revert back to using wallpaper directly
    // We'll fix the image loading issues in a different way
    // Remove the special state holder that's causing continuous recomposition
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {
        // Main content with simple Box layout
            Box(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                wallpaper?.let { currentWallpaper ->
                    // Log image loading state
                    LaunchedEffect(isImageLoading) {
                        Log.d("DetailScreen", "Image loading state changed: $isImageLoading")
                    }

                    // GRADIENT BLUE FIX: Create gradient background to prevent black flash
                    val gradientBrush = remember {
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF1A1A2E), // Deep blue
                                Color(0xFF16213E), // Navy blue
                                Color(0xFF0F3460)  // Darker blue
                            )
                        )
                    }
                    
                    // Prepare image loading - ensure gradient background is shown immediately
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(gradientBrush) // Use gradient instead of black to prevent flash
                            .clickable {
                                // Simple click - collapse info container if expanded
                                if (isInfoContainerExpanded) {
                                    isInfoContainerExpanded = false
                                }
                            }
                            // Add vertical swipe detection when not zoomed
                            .pointerInput("dragVertical") {
                                detectVerticalDragGestures(
                                    onDragStart = { offset ->
                                        isSwipeInProgress = true
                                        swipeDirection = 0
                                        dragStartY = offset.y
                                        dragCurrentY = offset.y
                                        previewProgress = 0f
                                        lastDragTime = System.currentTimeMillis() // Initialize drag time
                                        dragVelocity = 0f // Reset velocity
                                        
                                        // Ensure wallpaper context is available immediately
                                        scope.launch(Dispatchers.IO) {
                                            try {
                                                viewModel.ensureCollectionLoaded(effectiveWallpaperId, sourceScreen)
                                            } catch (e: Exception) {
                                            }
                                        }
                                    },
                                    onDragEnd = {
                                        val nextId = viewModel.getNextWallpaperId()
                                        val prevId = viewModel.getPreviousWallpaperId()
                                        
                                        // Enhanced completion logic - check distance threshold OR velocity
                                        val dragDistance = abs(swipeOffset)
                                        val absVelocity = abs(dragVelocity)
                                        
                                        // Complete if either:
                                        // 1. Distance exceeds threshold (normal swipe)
                                        // 2. Velocity is high enough (quick flick, like YouTube)
                                        val velocityThreshold = 0.8f // pixels per ms (adjust as needed)
                                        val shouldComplete = dragDistance > swipeThreshold || (dragDistance > previewThreshold && absVelocity > velocityThreshold)
                                        
                                        val hasAdjacentWallpaper = (swipeDirection == -1 && nextId != null) || 
                                                                      (swipeDirection == 1 && prevId != null)
                                        
                                        
                                        if (shouldComplete && hasAdjacentWallpaper) {
                                            // Complete the swipe with simple, smooth animation (no bounce)
                                            isTransitioningWallpaper = true
                                            isAnimatingSwipe = true
                                            
                                            scope.launch {
                                                try {
                                                    // Simple slide-out animation
                                                    targetSwipeOffset = if (swipeDirection == -1) {
                                                        -screenHeight // Simple full-screen slide
                                                    } else {
                                                        screenHeight
                                                    }
                                                    
                                                    // Smooth, linear animation without bounce
                                                    animatedSwipeOffset.animateTo(
                                                        targetValue = targetSwipeOffset,
                                                        animationSpec = tween(
                                                            durationMillis = 150, // Faster animation like YouTube
                                                            easing = FastOutSlowInEasing // More responsive easing
                                                        )
                                                    )
                                                    
                                                    // Load new wallpaper
                                                    if (swipeDirection == -1 && nextId != null) {
                                                        viewModel.loadNextWallpaper()
                                                    } else if (swipeDirection == 1 && prevId != null) {
                                                        viewModel.loadPreviousWallpaper()
                                                    }
                                                    
                                                    // Increment swipe count and check for ad
                                                    viewModel.incrementSwipeCount()
                                                    
                                                    // ENHANCED: Reset animation states immediately before showing ad
                                                    delay(100) // Allow wallpaper loading to start
                                                    animatedSwipeOffset.snapTo(0f)
                                                    swipeOffset = 0f
                                                    previewProgress = 0f
                                                    isTransitioningWallpaper = false
                                                    
                                                    // Show ad if needed for non-premium users
                                                    if (adManager.shouldShowAdForWallpaperSwipe() && !isPremiumUser) {
                                                        activity?.let { act ->
                                                            adManager.showInterstitialAd(act)
                                                        }
                                                    }
                                                    
                                                } catch (e: Exception) {
                                                    isTransitioningWallpaper = false
                                                    // Reset on error
                                                    animatedSwipeOffset.snapTo(0f)
                                                    swipeOffset = 0f
                                                } finally {
                                                    isAnimatingSwipe = false
                                                    isSwipeInProgress = false
                                                }
                                            }
                                        } else {
                                            // Cancel swipe with simple spring back (no bounce)
                                            isAnimatingSwipe = true
                                            scope.launch {
                                                try {
                                                    animatedSwipeOffset.animateTo(
                                                        targetValue = 0f,
                                                        animationSpec = tween(
                                                            durationMillis = 120, // Even faster for cancel animation
                                                            easing = FastOutSlowInEasing // More responsive easing
                                                        )
                                                    )
                                                    swipeOffset = 0f
                                                    previewProgress = 0f
                                                } catch (e: Exception) {
                                                    animatedSwipeOffset.snapTo(0f)
                                                    swipeOffset = 0f
                                                } finally {
                                                    isAnimatingSwipe = false
                                                    isSwipeInProgress = false
                                                }
                                            }
                                        }
                                    },
                                    onDragCancel = {
                                        // Reset all values on cancel
                                        scope.launch {
                                            animatedSwipeOffset.snapTo(0f)
                                        }
                                        swipeOffset = 0f
                                        targetSwipeOffset = 0f
                                        previewProgress = 0f
                                        isSwipeInProgress = false
                                    },
                                    onVerticalDrag = { change, dragAmount ->
                                        if (isAnimatingSwipe) return@detectVerticalDragGestures
                                        
                                        // Update drag position
                                        dragCurrentY += dragAmount
                                        swipeOffset += dragAmount
                                        
                                        // Calculate velocity (pixels per ms) for YouTube-like flick detection
                                        val currentTime = System.currentTimeMillis()
                                        val timeDelta = (currentTime - lastDragTime).coerceAtLeast(1L) // Avoid division by zero
                                        dragVelocity = dragAmount / timeDelta
                                        lastDragTime = currentTime
                                        
                                        // Determine swipe direction
                                        swipeDirection = if (swipeOffset < 0) -1 else 1
                                        
                                        // Check if we can swipe in this direction
                                        val canSwipe = (swipeDirection == -1 && viewModel.getNextWallpaperId() != null) ||
                                                      (swipeDirection == 1 && viewModel.getPreviousWallpaperId() != null)
                                        
                                        if (!canSwipe) {
                                            // Simple resistance - limit movement to 10% of screen
                                            val maxResistance = screenHeight * 0.1f
                                            swipeOffset = swipeOffset.coerceIn(-maxResistance, maxResistance)
                                        } else {
                                            // Enhanced preview progress calculation for YouTube-like feel
                                            // Accelerate the preview progress to make it feel more responsive
                                            // This makes small swipes show more preview content faster
                                            val rawProgress = abs(swipeOffset) / screenHeight
                                            // Apply a power curve to make initial movement more responsive
                                            previewProgress = (rawProgress * 1.5f).coerceIn(0f, 1f)
                                        }
                                        
                                        // Update animated offset immediately for responsive feel
                                        scope.launch {
                                            animatedSwipeOffset.snapTo(swipeOffset)
                                        }
                                        
                                        // Consume the event
                                        change.consume()
                                    }
                                )
                            }
                        ) {
                            // Simple thumbnail loaded tracking
                            var thumbnailLoaded by remember(wallpaperId) { mutableStateOf(false) }
                            
                            var highResLoaded by remember { mutableStateOf(false) }
                            
                            // TASK 5: Handle wallpaper changes with simplified state management
                            LaunchedEffect(currentWallpaper?.id) {
                                if (currentWallpaper != null) {
                                    
                                    // Reset local states for new wallpaper
                                highResLoaded = false
                                        highResLoadingStarted = false
                                        showHighRes = false
                                    Log.d("DetailScreen", "Reset local loading states for new wallpaper: ${currentWallpaper.id}")
                                    
                                    // REMOVED: Manual metadata calculation - ViewModel handles this automatically in loadMetadataInBackground()
                                    // The ViewModel automatically calculates and caches metadata when wallpaper loads
                                }
                            }

                            // Container with normal background
                                        Box(modifier = Modifier
                                            .fillMaxSize()
                                            .background(MaterialTheme.colorScheme.background)
                                // SWIPE ANIMATION FIX: Add graphicsLayer transformation for smooth YouTube-like swipe animation
                                .graphicsLayer {
                                    // Apply smooth vertical translation during swipe
                                    translationY = animatedSwipeOffset.value.toSafeTranslation()

                                    // Add subtle scale effect during swipe for better visual feedback
                                    // CRASH FIX: Protect against NaN from division by zero or invalid values
                                    val scaleProgress = if (screenHeight > 0f) {
                                        (abs(animatedSwipeOffset.value) / screenHeight).coerceIn(0f, 1f)
                                    } else 0f
                                    val scale = (1f - (scaleProgress * 0.05f)).toSafeScale(min = 0.8f, max = 1.0f)
                                    scaleX = scale
                                    scaleY = scale

                                    // Add subtle alpha fade during transition for smoother effect
                                    alpha = if (isSwipeInProgress) {
                                        val fadeProgress = if (screenHeight > 0f) {
                                            (abs(animatedSwipeOffset.value) / screenHeight).coerceIn(0f, 1f)
                                        } else 0f
                                        (1f - fadeProgress * 0.2f).toSafeAlpha().coerceIn(0.8f, 1f) // Subtle fade, never below 80%
                                    } else 1f
                                }
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null // No ripple effect
                                ) {
                                    if (!isSwipeInProgress && !isAnimatingSwipe) {
                                        // ENHANCED TAP BEHAVIOR: Handle bottom info container state
                                        if (isInfoContainerExpanded) {
                                            // If bottom info is expanded, collapse it first
                                            isInfoContainerExpanded = false
                                        } else {
                                            // If bottom info is collapsed, toggle UI visibility
                                            isUiVisible = !isUiVisible
                                        }
                                    } else {
                                    }
                                }
                            ) {
                                // HERO ANIMATION FIX: NO GRADIENT BACKGROUND 
                                // Only the shared element thumbnail should be visible to allow proper hero animation
                                // PERF FIX: Removed composable-body Log.d that ran every recomposition

                                // 2. IMMEDIATE THUMBNAIL LOADING: Start loading immediately with enhanced fallback
                                val thumbnailUrl = remember(currentWallpaper) {
                                    wallpaper?.thumbnail ?: currentWallpaper?.thumbnail
                                }
                                
                                // ULTRA-FAST THUMBNAIL: Show thumbnail immediately without complex animations
                                thumbnailUrl?.let { url ->
                                    val thumbnailRequest = remember(url) {
                                        ImageRequest.Builder(context)
                                            .data(url)
                                            .memoryCacheKey("thumb_${currentWallpaper?.id}")
                                            .diskCacheKey("thumb_${currentWallpaper?.id}")
                                            .crossfade(false)
                                            .allowHardware(true)
                                            .allowRgb565(true)
                                            .build()
                                    }
                                    
                                        AsyncImage(
                                        model = thumbnailRequest,
                                        contentDescription = "Wallpaper thumbnail",
                                            contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxSize(),
                                        onLoading = {
                                            Log.d("DetailScreen-HeroFix", "THUMBNAIL LOADING: Starting for ${currentWallpaper?.id}")
                                        },
                                        onSuccess = { 
                                            // Thumbnail loaded successfully
                                            isInitialized = true
                                            thumbnailLoaded = true
                                            Log.d("DetailScreen-HeroFix", "THUMBNAIL SUCCESS: Loaded for ${currentWallpaper?.id}, hero transition complete")
                                        },
                                        onError = { 
                                            Log.e("DetailScreen-HeroFix", "THUMBNAIL ERROR: Failed for ${currentWallpaper?.id}")
                                        }
                                    )
                                }
                                        
                                // 3. HIGH-RES IMAGE: Load when wallpaper data is available with enhanced progress
                                val displayWallpaper = remember(currentWallpaper) {
                                    wallpaper ?: currentWallpaper
                                }
                                
                                displayWallpaper?.let { wp ->
                                    // PERFORMANCE FIX: Use optimized ImageRequest with proper caching
                                    val imageRequest = remember(wp.imageUrl) {
                                        ImageRequest.Builder(context)
                                            .data(wp.imageUrl)
                                            .memoryCacheKey(wp.id) // Use wallpaper ID as cache key
                                            .diskCacheKey(wp.id)
                                            .crossfade(false) // Disable crossfade for faster loading
                                            .allowHardware(true) // Enable hardware acceleration
                                                .build()
                                    }
                                                
                                            AsyncImage(
                                                model = imageRequest,
                                        contentDescription = "High resolution wallpaper",
                                                contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize(),
                                        onLoading = {
                                            Log.d("DetailScreen-HeroFix", "HIGH-RES LOADING: Starting for ${wp.id}")
                                            // Only start loading state if not already completed for this wallpaper
                                            if (!imageLoadingCompleted) {
                                                isImageLoading = true
                                                highResLoadingStarted = true
                                                loadingStartTime = System.currentTimeMillis()
                                                Log.d("DetailScreen-HeroFix", "HIGH-RES LOADING: Set loading=true for ${wp.id}")
                                } else {
                                                Log.d("DetailScreen-HeroFix", "HIGH-RES LOADING: Skipping - already completed for ${wp.id}")
                                            }
                                        },
                                        onSuccess = {
                                            val loadTime = if (loadingStartTime != 0L) System.currentTimeMillis() - loadingStartTime else -1L
                                            val isCached = loadTime in 0L..99L

                                            Log.d("DetailScreen-HeroFix", "HIGH-RES SUCCESS: ${if (isCached) "CACHE HIT" else "NETWORK"} - ${if (loadTime >= 0) "${loadTime}ms" else "N/A"} for ${wp.id}")
                                            
                                            // Update all success states
                                            highResLoaded = true
                                            // FIXED: Don't set thumbnailLoaded - it's managed by global state
                                            isCacheDetected = isCached
                                            showHighRes = true
                                            imageLoadingCompleted = true
                                            
                                            // LOADING INDICATOR FIX: Ensure loading indicator is always hidden properly
                                            // For cached images, use a shorter delay to ensure UI responsiveness
                                            if (isCached) {
                                                // Launch coroutine to delay hiding loading for cached images
                                                scope.launch {
                                                    delay(150) // Shorter delay for cached images (150ms is enough for visual feedback)
                                                    isImageLoading = false
                                                    Log.d("DetailScreen", "Cache hit: Reset loading state after short delay")
                                                }
                                            } else {
                                                // For non-cached images, hide loading immediately
                                                isImageLoading = false
                                                Log.d("DetailScreen", "Network load: Reset loading state immediately")
                                            }
                                            
                                            // RELIABILITY FIX: Add a safety timeout to ensure indicator never gets stuck
                                            scope.launch {
                                                delay(1000) // Safety timeout of 1 second
                                                if (isImageLoading) {
                                                    Log.d("DetailScreen", "Safety timeout: Force reset loading state")
                                                    isImageLoading = false
                                                }
                                            }
                                            
                                            Log.d("DetailScreen", "High-res loaded successfully for ${wp.id} (cached: $isCached, time: ${loadTime}ms)")
                                        },
                                        onError = {
                                            // Handle error state
                                            highResLoaded = false
                                            showHighRes = false
                                            isImageLoading = false
                                            imageLoadingCompleted = true // Mark as completed even on error
                                            Log.e("DetailScreen", "High-res load error for ${wp.id}")
                                }
                                    )
                            }
                            
                                val shouldShowProgress = remember(isImageLoading, highResLoaded, thumbnailLoaded) {
                                    // Show progress if image is loading and high-res is not loaded yet
                                    val show = isImageLoading && !highResLoaded
                                    Log.d("DetailScreen", "LOADING INDICATOR: shouldShow=$show (isLoading=$isImageLoading, highResLoaded=$highResLoaded, thumbnailLoaded=$thumbnailLoaded)")
                                    show
                                }
                                
                                if (shouldShowProgress) {
                                    Log.d("DetailScreen-HeroFix", "LOADING INDICATOR: Showing for ${currentWallpaper?.id}")
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                        // Material 3 Expressive Contained Loading Indicator
                                    ContainedLoadingIndicator(
                                        modifier = Modifier.size(64.dp),
                                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
                                        indicatorColor = MaterialTheme.colorScheme.primary,
                                        containerShape = CircleShape
                                    )
                                        
                                }
                                        }
                                
                            }
                        }
                        
                        // YOUTUBE SHORTS-LIKE SWIPE PREVIEW: Show next/previous wallpaper during swipe
                        if (isSwipeInProgress) {
                            val nextWallpaperId = viewModel.getNextWallpaperId()
                            val prevWallpaperId = viewModel.getPreviousWallpaperId()
                            
                            // Show next wallpaper preview when swiping up (swipeDirection = -1)
                            if (swipeDirection == -1 && nextWallpaperId != null && swipeOffset < 0) {
                                val nextWallpaper = viewModel.getWallpaperById(nextWallpaperId)
                                nextWallpaper?.let { nextWp ->
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .graphicsLayer {
                                                // Position next wallpaper below current one, sliding up
                                                translationY = screenHeight + animatedSwipeOffset.value
                                                // Fade in as it approaches
                                                alpha = (abs(animatedSwipeOffset.value) / (screenHeight * 0.5f)).coerceIn(0f, 1f)
                                            }
                                    ) {
                                        AsyncImage(
                                            model = ImageRequest.Builder(context)
                                                .data(nextWp.imageUrl)
                                                .memoryCacheKey("preview_${nextWp.id}")
                                                .diskCacheKey("preview_${nextWp.id}")
                                                .crossfade(false)
                                                .allowHardware(true)
                                                .allowRgb565(true)
                                                .build(),
                                            contentDescription = "Next wallpaper preview",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                    }
                                }
                                
                            // Show previous wallpaper preview when swiping down (swipeDirection = 1)
                            if (swipeDirection == 1 && prevWallpaperId != null && swipeOffset > 0) {
                                val prevWallpaper = viewModel.getWallpaperById(prevWallpaperId)
                                prevWallpaper?.let { prevWp ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                            .graphicsLayer {
                                                // Position previous wallpaper above current one, sliding down
                                                translationY = -screenHeight + animatedSwipeOffset.value
                                                // Fade in as it approaches
                                                alpha = (abs(animatedSwipeOffset.value) / (screenHeight * 0.5f)).coerceIn(0f, 1f)
                                            }
                                    ) {
                                    AsyncImage(
                                            model = ImageRequest.Builder(context)
                                                .data(prevWp.imageUrl)
                                                .memoryCacheKey("preview_${prevWp.id}")
                                                .diskCacheKey("preview_${prevWp.id}")
                                                .crossfade(false)
                                                .allowHardware(true)
                                                .allowRgb565(true)
                                            .build(),
                                            contentDescription = "Previous wallpaper preview",
                                        contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }
                            }
                        }

                        // Show UI elements when UI is visible (don't wait for image loading)
                        AnimatedVisibility(
            visible = isUiVisible, // Show UI immediately, don't wait for image loading
            enter = fadeIn() + slideInVertically { -it / 2 },
            exit = fadeOut() + slideOutVertically { -it / 2 }
                        ) {
                            // Top bar with back button and preview toggle
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                                    // Add top padding for status bar
                                    .statusBarsPadding()
                                    .padding(top = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                            // Back button with chevron icon
                                IconButton(
                                    onClick = {
                                        Log.d("DetailScreen", "Back button clicked, source: $sourceScreen")
                                        navigationState.navigateBack()
                                    },
                                    modifier = Modifier
                                    .size(40.dp)
                                        .background(
                                            color = Color.Black.copy(alpha = 0.4f),
                                            shape = CircleShape
                                        )
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back",
                                        tint = Color.White
                                    )
                                }

                                // Preview mode toggle - matched to back button styling
                                IconButton(
                                    onClick = { isPreviewMode = !isPreviewMode },
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(
                                            color = Color.Black.copy(alpha = 0.4f),
                                            shape = CircleShape
                                        )
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.RemoveRedEye,
                                        contentDescription = "Preview",
                                        tint = Color.White
                                    )
                        }
                    }
                }
            }
        }

        // Bottom Info Container with glassmorphic effect
        // Always show bottom info container - no more hiding behind loading states
            AnimatedVisibility(
                visible = !isPreviewMode && isUiVisible, // Show UI immediately, don't wait for image loading
                enter = fadeIn(animationSpec = tween(500, easing = EaseOutCubic)) +
                       slideInVertically(animationSpec = tween(700, easing = EaseOutCubic)) { it },
                exit = fadeOut(animationSpec = tween(300)) +
                       slideOutVertically(animationSpec = tween(500)) { it },
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Column {
                    // SWIPE HINT: Add hint above bottom info container
                    AnimatedVisibility(
                        visible = !isInfoContainerExpanded,
                        enter = fadeIn() + slideInVertically { it / 2 },
                        exit = fadeOut() + slideOutVertically { it / 2 }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 32.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Left side: Swipe text in 2 lines with arrows
                            Column(
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.Start
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.KeyboardArrowUp,
                                        contentDescription = "Swipe up",
                                        tint = Color.White.copy(alpha = 0.7f),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Swipe up or down",
                                        color = Color.White.copy(alpha = 0.7f),
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.KeyboardArrowDown,
                                        contentDescription = "Swipe down", 
                                        tint = Color.White.copy(alpha = 0.7f),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "to change wallpapers",
                                        color = Color.White.copy(alpha = 0.7f),
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                            
                            // Right side: Category chip
                            wallpaper?.let { currentWallpaper ->
                                if (currentWallpaper.category.isNotEmpty() || currentWallpaper.series.isNotEmpty()) {
                                    Card(
                                        shape = RoundedCornerShape(50.dp),
                                        colors = CardDefaults.cardColors(containerColor = Color.Black),
                                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                                        modifier = Modifier
                                            .border(
                                                width = 1.5.dp,
                                                brush = Brush.verticalGradient(
                                                    colors = listOf(
                                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                                        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.3f)
                                                    )
                                                ),
                                                shape = RoundedCornerShape(50.dp)
                                            )
                                            .clickable {
                                                val categoryToNavigate = if (currentWallpaper.category.isNotEmpty())
                                                    currentWallpaper.category
                                                else
                                                    AppConfig.COLLECTION_HOME
                                                navigationState.navigateToCategory(categoryToNavigate)
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.GridView,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = if (currentWallpaper.category.isNotEmpty())
                                                    currentWallpaper.category.uppercase()
                                                else
                                                    AppConfig.COLLECTION_HOME.uppercase(),
                                                color = Color.White,
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp) // Adjusted padding
                        .navigationBarsPadding()
                        .padding(bottom = 8.dp)
                ) {
                    wallpaper?.let { currentWallpaper ->
                        // Apply Material Design 3 glassmorphic effect with Surface and gradient border
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                // PROPER BOUNCY FIX: Use wrapContentHeight always for smooth animation
                                .wrapContentHeight()
                                // Apply bouncy animation for both expand and collapse transitions
                                .animateContentSize(
                                            animationSpec = spring(
                                                dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                                                stiffness = androidx.compose.animation.core.Spring.StiffnessLow
                                            )
                                )
                                .clickable { isInfoContainerExpanded = !isInfoContainerExpanded },
                            contentAlignment = Alignment.Center
                        ) {
                            GlassmorphicBox(
                                modifier = Modifier.fillMaxWidth(),
                                cornerRadius = 24.dp,
                                backgroundAlpha = if (isInfoContainerExpanded) 0.65f else 1.0f
                            ) {
                                // Gradient border using Box with border
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(
                                            width = 1.5.dp,
                                            brush = Brush.verticalGradient(
                                                colors = listOf(
                                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                                    MaterialTheme.colorScheme.tertiary.copy(alpha = 0.3f)
                                                )
                                            ),
                                            shape = RoundedCornerShape(24.dp)
                                        )
                                ) {
                                    // Content of the bottom info container
                                    if (isInfoContainerExpanded) {
                                        // Expanded state: Use Column with normal layout
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                                .padding(start = 0.dp, end = 16.dp, top = 28.dp, bottom = 28.dp)
                                    ) {
                                        // Title and category row with arrow indicator
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(start = 24.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = currentWallpaper.wallpaperName,
                                                    color = Color.White,
                                                    style = MaterialTheme.typography.titleLarge,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )

                                                Spacer(modifier = Modifier.height(4.dp))

                                                // Only show source for non-Apple wallpapers and when source is not empty
                                                val shouldShowSource = currentWallpaper.source.isNotEmpty() && 
                                                    !currentWallpaper.series.isNotEmpty() // Apple wallpapers have series field
                                                
                                                if (shouldShowSource) {
                                                    Text(
                                                        text = currentWallpaper.source,
                                                        color = Color.White,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }

                                            // Arrow indicator that rotates based on expansion state
                                            Icon(
                                                    imageVector = Icons.Rounded.KeyboardArrowDown,
                                                    contentDescription = "Collapse",
                                                tint = Color.White,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }

                                                Spacer(modifier = Modifier.height(16.dp))

                                                // Stats row with updated order
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(start = 24.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    // Left column: Downloads, Dimensions, Info
                                                    Column(
                                                        modifier = Modifier.weight(1f),
                                                        verticalArrangement = Arrangement.spacedBy(16.dp)
                                                    ) {
                                                        AnimatedCounterStatItem(
                                                            icon = Icons.Rounded.ArrowCircleDown,
                                                            count = localDownloads ?: currentWallpaper.downloads,
                                                            label = "downloads",
                                                            iconColor = Color.White
                                                        )
                                                        StatItem(
                                                            icon = Icons.Rounded.AspectRatio,
                                                            value = currentWallpaper.dimensions.ifEmpty { 
                                                                imageDimensions.ifEmpty { "Calculating..." }
                                                            },
                                                            iconColor = Color.White
                                                        )
                                                        StatItem(
                                                            icon = Icons.Outlined.Info,
                                                            value = "INFO",
                                                            iconColor = Color.White,
                                                            modifier = Modifier.clickable { showInfoDialog = true }
                                                        )
                                                    }

                                                    // Right column: Views, Size, Report
                                                    Column(
                                                        modifier = Modifier.weight(1f),
                                                        verticalArrangement = Arrangement.spacedBy(16.dp)
                                                    ) {
                                                        AnimatedCounterStatItem(
                                                            icon = Icons.Rounded.RemoveRedEye,
                                                            count = localViews ?: currentWallpaper.views,
                                                            label = "views",
                                                            iconColor = Color.White
                                                        )
                                                        StatItem(
                                                            icon = Icons.Outlined.Storage,
                                                            value = currentWallpaper.size.ifEmpty { 
                                                                imageSize.ifEmpty { "Calculating..." }
                                                            },
                                                            iconColor = Color.White
                                                        )
                                                        StatItem(
                                                            icon = Icons.Outlined.Report,
                                                            value = "REPORT",
                                                            iconColor = Color.White,
                                                            modifier = Modifier.clickable { showReportDialog = true }
                                                        )
                                                    }
                                                }

                                                Spacer(modifier = Modifier.height(16.dp))

                                            // Action buttons aligned with wallpaper name start position
                                                Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(start = 24.dp, end = 16.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    ActionButton(
                                                        icon = Icons.Rounded.ArrowDownward,
                                                        onClick = { handleDownloadClick() },
                                                        isLoading = isDownloading && !isSettingWallpaper && !isSharing
                                                    )
                                                    ActionButton(
                                                        icon = if (currentWallpaper.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                                        onClick = { 
                                                            // Haptic feedback
                                                            view.performHapticFeedback(
                                                                if (currentWallpaper.isFavorite) HapticFeedbackConstants.CONTEXT_CLICK 
                                                                else HapticFeedbackConstants.KEYBOARD_TAP
                                                            )
                                                            
                                                            // Trigger animation
                                                            triggerFavoriteAnimation = true
                                                            
                                                            // Toggle favorite
                                                            viewModel.toggleFavorite()
                                                        },
                                                        tint = animatedFavoriteColor,
                                                        scale = animatedFavoriteScale
                                                    )
                                                    ActionButton(
                                                        icon = Icons.Rounded.FormatPaint,
                                                        onClick = { handleSetWallpaperClick() },
                                                        isLoading = isSettingWallpaper && !isDownloading && !isSharing
                                                    )
                                                    ActionButton(
                                                        icon = Icons.Rounded.IosShare,
                                                        onClick = {
                                                            if (currentWallpaper.exclusive) {
                                                                if (!hasWatchedAd) {
                                                                    showUnlockDialog = true
                                                                } else {
                                                                    // Show toast before starting to share
                                                                    Toast.makeText(context, "Preparing to share...", Toast.LENGTH_SHORT).show()

                                                                    viewModel.shareWallpaper(
                                                                        context = context,
                                                                        wallpaper = currentWallpaper
                                                                    )
                                                                }
                                                            } else {
                                                                // Show toast before starting to share
                                                                Toast.makeText(context, "Preparing to share...", Toast.LENGTH_SHORT).show()

                                                                viewModel.shareWallpaper(
                                                                    context = context,
                                                                    wallpaper = currentWallpaper
                                                                )
                                                            }
                                                        },
                                                        isLoading = isSharing && !isDownloading && !isSettingWallpaper
                                                    )
                                                    ActionButton(
                                                        icon = Icons.Rounded.Edit,
                                                        onClick = {
                                                            if (currentWallpaper.exclusive && !hasWatchedAd) {
                                                                showUnlockDialog = true
                                                            } else {
                                                                // Show toast before starting edit process
                                                                Toast.makeText(context, "Opening editor...", Toast.LENGTH_SHORT).show()

                                                                checkAndRequestPermission(PermissionRequestType.EDIT)
                                                            }
                                                        }
                                                    )
                                                }
                                            }
                                    } else {
                                        // Collapsed state: Use Box for perfect centering
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(start = 0.dp, end = 16.dp, top = 16.dp, bottom = 16.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(start = 24.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = currentWallpaper.wallpaperName,
                                                        color = Color.White,
                                                        style = MaterialTheme.typography.titleLarge,
                                                        fontWeight = FontWeight.Bold,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    // Category removed from collapsed state
                                                }

                                                // Arrow indicator pointing up when collapsed
                                                Icon(
                                                    imageVector = Icons.Rounded.KeyboardArrowUp,
                                                    contentDescription = "Expand",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
                } // End Column for swipe hint

        // Preview mode content
        AnimatedVisibility(
            visible = isPreviewMode,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                // Show the wallpaper in preview mode using the WallpaperPreview component
                wallpaper?.let { wall ->
                    WallpaperPreview(
                        isLockScreenPreview = isLockScreenPreview,
                        imageUrl = wall.imageUrl,
                        wallpaperId = wall.id, // Pass wallpaper ID for cache consistency
                        onClosePreview = { isPreviewMode = false },
                        onToggleLockScreenPreview = { isLockScreenPreview = !isLockScreenPreview }
                    )
                }
            }
        }
    }

    if (showSetWallpaperSheet) {
        SetWallpaperBottomSheet(
            onDismiss = { showSetWallpaperSheet = false },
            onOptionSelected = { option ->
                                handleWallpaperSet(option)
                            },
            isSettingWallpaper = isSettingWallpaper,
            progress = downloadProgress,
            showExternalOption = true
        )
    }

    if (showUnlockDialog) {
        UnlockDialog(
            onDismiss = {
                // Just close the dialog — do NOT navigate back.
                // The user tapped "Not Now" on a specific action (download/share/set/edit),
                // they should stay on the wallpaper screen.
                showUnlockDialog = false
            },
            onWatchAd = {
                if (adManager != null) {
                    showUnlockDialog = false
                    // Attempt to load reward ad
                    isLoadingRewardAd = true

                    // Keep track if toast has been shown
                    var hasShownFailureToast = false

                    // Add timeout for ad loading
                    scope.launch {
                        try {
                            delay(15000) // 15 second timeout
                            if (isLoadingRewardAd) {
                                Log.d("DetailScreen", "Ad loading timed out after 15 seconds")
                                isLoadingRewardAd = false

                                if (!hasShownFailureToast) {
                                    hasShownFailureToast = true
                                    Toast.makeText(
                                        context,
                                        "Failed to load ad. Please try again later.",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }

                                if (wallpaper?.exclusive == true && !hasWatchedAd) {
                                    navigationState.navigateBack()
                                }
                            }
                        } catch (e: Exception) {
                            // Ignore cancellation exceptions
                            Log.e("DetailScreen", "Error in ad loading timeout: ${e.message}")
                        }
                    }

                    adManager.loadRewardAd(
                        onAdLoaded = {
                            isLoadingRewardAd = false
                            adManager.showRewardAd(
                                activity = context as Activity,
                                onRewarded = {
                                    Log.d("DetailScreen", "User rewarded")
                                    hasWatchedAd = true
                                    // Save to preferences that user has watched ad for this wallpaper
                                    scope.launch {
                                        wallpaper?.id?.let {
                                            viewModel.markWallpaperAsUnlocked(it)
                                            userPreferences.addUnlockedWallpaper(it)
                                        }
                                    }
                                },
                                onAdDismissed = {
                                    Log.d("DetailScreen", "Rewarded ad closed")
                                    // If ad fails to show, this callback will still run
                                    // Check if user got reward - if not and it's exclusive, navigate back
                                    if (wallpaper?.exclusive == true && !hasWatchedAd) {
                                        Toast.makeText(
                                            context,
                                            "Failed to display ad. Please try again later.",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        navigationState.navigateBack()
                                    }
                                }
                            )
                        }
                    )
                }
            },
            onUpgrade = {
                Log.d("DetailScreen", "Upgrade button clicked, navigating to premium")
                showUnlockDialog = false
                isReturningFromPremiumScreen = true  // Mark that we're going to premium screen
                navigationState.navigateToPremium()
            },
            isLoading = isLoadingRewardAd,
            loadingText = "Loading Ad..."
        )
    }

    if (showFeedbackDialog) {
        FeedbackDialog(
            onDismiss = { showFeedbackDialog = false },
            onSubmit = { rating, feedback ->
                scope.launch {
                    if (rating >= 4) {
                        // Open Play Store
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            data = Uri.parse("market://details?id=${context.packageName}")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        try {
                            context.startActivity(intent)
                        } catch (e: ActivityNotFoundException) {
                            // If Play Store app is not installed, open in browser
                            intent.data =
                                Uri.parse("https://play.google.com/store/apps/details?id=${context.packageName}")
                            context.startActivity(intent)
                        }
                    }
                    // Save feedback status
                    userPreferences.setHasGivenFeedback(true)
                    showFeedbackDialog = false
                }
            }
        )
    }

    if (showReportDialog) {
        ReportDialog(
            onDismiss = { showReportDialog = false },
            onSubmit = { report ->
                // Handle report submission
                scope.launch {
                    // Send report to server


                    // Send email directly to Gmail
                    val deviceName = Build.MANUFACTURER + " " + Build.MODEL
                    val androidVersion = "Android " + Build.VERSION.RELEASE

                    // Create email intent specifically for Gmail
                    try {
                        val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
                            data = "mailto:${AppConfig.SUPPORT_EMAIL}".toUri()
                            putExtra(
                                Intent.EXTRA_SUBJECT,
                                "OnePlus 7 Wallpapers - Report a Wallpaper"
                            )
                            putExtra(
                                Intent.EXTRA_TEXT, """
                                Reason: ${report.text}

                                ---------------------
                                Wallpaper: ${wallpaper?.wallpaperName ?: "Unknown"}
                                Wallpaper ID: ${wallpaper?.id ?: "Unknown"}
                                Device: $deviceName
                                Android: $androidVersion
                                """.trimIndent()
                            )

                            // Try to specifically target Gmail
                            val gmailPackage = "com.google.android.gm"
                            setPackage(gmailPackage)
                        }

                        // Check if Gmail is installed
                        val packageManager = context.packageManager
                        val activities = packageManager.queryIntentActivities(emailIntent, 0)

                        if (activities.isNotEmpty()) {
                            // Gmail is installed, open it directly
                            context.startActivity(emailIntent)
                            Log.d(TAG, "Opened email in Gmail")
                            return@launch
                        } else {
                            // Gmail not found, fall back to any email app
                            val fallbackIntent = Intent(Intent.ACTION_SENDTO).apply {
                                data = Uri.parse("mailto:${AppConfig.SUPPORT_EMAIL}")
                                putExtra(
                                    Intent.EXTRA_SUBJECT,
                                    "OnePlus 7 Wallpapers - Report a Wallpaper"
                                )
                                putExtra(
                                    Intent.EXTRA_TEXT, """
                                    Reason: ${report.text}

                                    ---------------------
                                    Wallpaper: ${wallpaper?.wallpaperName ?: "Unknown"}
                                    Wallpaper ID: ${wallpaper?.id ?: "Unknown"}
                                    Device: $deviceName
                                    Android: $androidVersion
                                    """.trimIndent()
                                )
                            }
                            context.startActivity(
                                Intent.createChooser(
                                    fallbackIntent,
                                    "Send email using..."
                                )
                            )
                        }
                    } catch (e: Exception) {
                        Toast.makeText(context, "No email app found", Toast.LENGTH_SHORT).show()
                    }

                    showReportDialog = false
                }
            }
        )
    }

    // Add info dialog
    if (showInfoDialog) {
        wallpaper?.let { wall ->
            InfoDialog(
                onDismiss = { showInfoDialog = false },
                wallpaper = wall
            )
        }
    }

    // Notification permission dialog
    if (showNotificationPermissionDialog && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        AlertDialog(
            onDismissRequest = {
                showNotificationPermissionDialog = false
                preferencesManager?.setHasRequestedNotificationPermission(true)
            },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 12.dp)
                    )
                    Text(
                        text = "Enable Notifications",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
    Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                Text(
                        text = "Don't miss our amazing new wallpapers! Stay updated with the latest exclusive content and be the first to know when we add depth effect wallpapers.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    Text(
                        text = "You can change this anytime in your device settings.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        notificationPermissionLauncher.launch(POST_NOTIFICATIONS)
                        showNotificationPermissionDialog = false
                    },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Allow")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        showNotificationPermissionDialog = false
                        preferencesManager?.setHasRequestedNotificationPermission(true)
                    },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Not Now")
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 8.dp
        )
    }

    // DEBUG: Track state changes
    LaunchedEffect(isImageLoading) {
    }
    
    LaunchedEffect(highResLoaded) {
    }
    
    LaunchedEffect(thumbnailLoaded) {
    }
    
    LaunchedEffect(imageLoadingCompleted) {
    }

    // SWIPE LOADING FIX: Register callback to reset loading states when ad is dismissed
    LaunchedEffect(adManager) {
        adManager.setSwipeAdDismissCallback {
            // Reset all swipe loading states when ad is dismissed
            viewModel.resetSwipeLoadingStates()
            
            // LOADING INDICATOR FIX: Also reset local loading state variables
            // This ensures the loading indicator disappears after ad dismissal
            isImageLoading = false
            highResLoaded = true
            imageLoadingCompleted = true
            
            Log.d("DetailScreen", "Reset all loading states after ad dismissal")
        }
    }
    
    // Clean up callback when screen is disposed
    DisposableEffect(adManager) {
        onDispose {
            adManager.setSwipeAdDismissCallback(null)
        }
    }
}

// Updated StatItem with custom icon color and adjusted colors
@Composable
private fun StatItem(
    icon: ImageVector,
    value: String,
    iconColor: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(22.dp)
        )

        Spacer(modifier = Modifier.width(12.dp))

        // Add underline for INFO and REPORT options
        if (value == "INFO" || value == "REPORT") {
            Text(
                text = value,
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium.copy(
                    textDecoration = TextDecoration.Underline
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        } else {
            Text(
                text = value,
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// Animated counter StatItem: new number enters from below, old exits upward (clock/odometer flip)
@Composable
private fun AnimatedCounterStatItem(
    icon: ImageVector,
    count: Int,
    label: String,
    iconColor: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        AnimatedContent(
            targetState = count,
            transitionSpec = {
                (slideInVertically { fullHeight -> fullHeight } + fadeIn())
                    .togetherWith(slideOutVertically { fullHeight -> -fullHeight } + fadeOut())
            },
            label = "counter_flip"
        ) { animatedCount ->
            Text(
                text = "$animatedCount $label",
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// Enhanced ActionButton implementation for bottom info container
@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun ActionButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
    isLoading: Boolean = false,
    scale: Float = 1f
) {
    Surface(
        modifier = modifier
            .size(48.dp) // COMPACT: Smaller size for better layout
            .scale(scale) // Apply scale animation
            .shadow(
                elevation = 8.dp, // COMPACT: Reduced elevation
                shape = CircleShape,
                spotColor = Color(0x80000000),
                ambientColor = Color(0x60000000)
            )
            .clickable(onClick = onClick),
        shape = CircleShape,
        color = Color(0xFF1A1A2E).copy(alpha = 0.9f)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (isLoading) {
LoadingIndicator(
                    modifier = Modifier.size(22.dp), // COMPACT: Smaller loading indicator
                    color = Color.White
                )
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(22.dp) // COMPACT: Smaller icon size
                )
            }
        }
    }
}

fun showPermissionSettingsDialog(context: Context) {
    AlertDialog.Builder(context)
        .setTitle("Permission Required")
        .setMessage("Storage permission is required to download wallpapers. Please enable it in app settings.")
        .setPositiveButton("Open Settings") { _, _ ->
            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
            })
        }
        .setNegativeButton("Cancel", null)
        .show()
}

fun showPermissionRationaleDialog(context: Context, onConfirm: () -> Unit) {
    AlertDialog.Builder(context)
        .setTitle("Permission Required")
        .setMessage("Storage permission is required to download and share wallpapers.")
        .setPositiveButton("Grant Permission") { _, _ -> onConfirm() }
        .setNegativeButton("Cancel", null)
        .show()
}

enum class PermissionRequestType {
    DOWNLOAD, SHARE, EDIT
}


// Helper function to directly open system wallpaper picker with specified file
private fun openDirectWallpaperPicker(
    file: java.io.File,
    context: Context,
    onError: (message: String) -> Unit
) {
    try {
        val fileUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            file
        )

        // Create intent with proper flags
        val intent = Intent(Intent.ACTION_ATTACH_DATA).apply {
            setDataAndType(fileUri, "image/*")
            putExtra(
                Intent.EXTRA_SUBJECT,
                "Set as wallpaper"
            )
            putExtra(
                Intent.EXTRA_TEXT, """
                Set this image as your wallpaper
                """.trimIndent()
            )
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        // Check if Google Photos is available
        val googlePhotosPackage = "com.google.android.apps.photos"
        intent.setPackage(googlePhotosPackage)

        try {
            context.startActivity(intent)
            Log.d(TAG, "Opened wallpaper picker in Google Photos")
            return
        } catch (e: Exception) {
            Log.d(TAG, "Google Photos not available, showing chooser: ${e.message}")
            intent.setPackage(null) // Reset package
        }

        // Create a chooser as last resort
        val chooserIntent = Intent.createChooser(intent, "Set as wallpaper using")
        chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        try {
            context.startActivity(chooserIntent)
            Log.d(TAG, "Opened wallpaper picker using chooser dialog")
            return
        } catch (e: Exception) {
            Log.e(TAG, "Error opening chooser: ${e.message}")
            onError("Unable to find an app to set wallpaper. Please try using the in-app options.")
        }
    } catch (e: Exception) {
        Log.e(TAG, "Error opening wallpaper picker: ${e.message}", e)
        onError("Error setting wallpaper: ${e.message ?: "Unknown error"}")
    }
}

// Add InfoDialog composable
@Composable
private fun InfoDialog(
    onDismiss: () -> Unit,
    wallpaper: Wallpaper
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 12.dp)
                )
                Text(
                    text = "Wallpaper Information",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Name: ${wallpaper.wallpaperName}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                )

                // Special section for exclusive wallpapers
                if (wallpaper.exclusive) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                            .padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Star,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Exclusive Wallpaper",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Text(
                            text = "This is an exclusive wallpaper designed by DroidAtes. Our exclusive wallpapers feature high-quality artwork with premium design elements specifically created for OnePlus 7 devices.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Depth effect section if applicable
                if (wallpaper.depthEffect == true) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.1f))
                            .padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Layers,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Depth Effect",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.tertiary,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Text(
                            text = "This wallpaper supports the Depth Effect feature on compatible devices, providing a dynamic 3D-like experience with subtle movement effects.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }

                Text(
                    text = "Source: We strive to use only properly licensed wallpapers. If you believe this wallpaper is being used without proper permission, please let us know at ${AppConfig.SUPPORT_EMAIL}.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                Text(
                    text = "Once you send us an email, we'll investigate and take necessary action promptly. Thank you for helping us maintain a high-quality collection.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("OK")
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(24.dp),
        tonalElevation = 8.dp
    )
}

enum class ReportReason(val text: String) {
    COPYRIGHT_VIOLATION("Copyright Violation"),
    INAPPROPRIATE_CONTENT("Inappropriate Content"),
    OFFENSIVE_MATERIAL("Offensive Material"),
    POOR_QUALITY("Poor Quality"),
    WRONG_CATEGORY("Wrong Category"),
    OTHER("Other")
}

/**
 * Helper function to find the activity
 */
private fun Context.findActivity(): Activity? {
    var currentContext = this
    while (currentContext is ContextWrapper) {
        if (currentContext is Activity) {
            return currentContext
        }
        currentContext = currentContext.baseContext
    }
    return null
}

@Composable
private fun ReportDialog(
    onDismiss: () -> Unit,
    onSubmit: (ReportReason) -> Unit
) {
    var selectedReason by remember { mutableStateOf<ReportReason?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.ReportProblem,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 12.dp)
                )
                Text(
                    text = "Report Wallpaper",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectableGroup()
            ) {
                Text(
                    text = "Please select a reason for reporting:",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // List of report reasons with radio buttons and less spacing
                ReportReason.entries.forEach { reason ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp) // Reduced height for less spacing
                            .selectable(
                                selected = (reason == selectedReason),
                                onClick = { selectedReason = reason },
                                role = Role.RadioButton
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (reason == selectedReason),
                            onClick = null // null because we're handling the click on the row
                        )
                        Text(
                            text = reason.text,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    selectedReason?.let { reason ->
                        onSubmit(reason)
                    }
                },
                enabled = selectedReason != null,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Submit")
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Cancel")
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(24.dp),
        tonalElevation = 8.dp
    )
}
