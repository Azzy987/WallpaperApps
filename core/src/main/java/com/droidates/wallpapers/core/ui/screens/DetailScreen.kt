package com.droidates.wallpapers.core.ui.screens

import com.droidates.wallpapers.core.utils.ImageUtils
import com.droidates.wallpapers.core.notifications.NotificationPermissionEffect
import com.droidates.wallpapers.core.notifications.NotificationPermissionPrompt
import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.provider.MediaStore
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
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.platform.LocalView
import kotlin.math.abs
import com.droidates.wallpapers.core.utils.toSafeScale
import com.droidates.wallpapers.core.utils.toSafeTranslation
import android.view.HapticFeedbackConstants
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
// PERF FIX: Removed onGloballyPositioned import — replaced with LocalConfiguration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Precision
import coil.size.Size
import kotlinx.coroutines.Dispatchers
import androidx.compose.animation.core.*
import com.droidates.wallpapers.core.data.preferences.LocalUserPreferences
import com.droidates.wallpapers.core.navigation.NavigationState
import com.droidates.wallpapers.core.ui.components.*
import com.droidates.wallpapers.core.ui.components.detail.WallpaperPreview
import com.droidates.wallpapers.core.ui.components.detail.ActionPhase
import com.droidates.wallpapers.core.ui.components.detail.ActionButtonContent
import com.droidates.wallpapers.core.ui.components.detail.rememberFavoriteBounce
import com.droidates.wallpapers.core.ui.components.AdFreeOfferDialog
import com.droidates.wallpapers.core.ui.components.WallpaperLimitGateDialog
import com.droidates.wallpapers.core.utils.StatFormatter
import com.droidates.wallpapers.core.model.Wallpaper
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.RadioButton
import androidx.compose.ui.semantics.Role
import android.app.Application
import android.os.Bundle
import androidx.compose.material.icons.outlined.Report
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Info
import com.droidates.wallpapers.core.config.AppConfig
import com.droidates.wallpapers.core.utils.PreferencesManager
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import android.Manifest.permission.POST_NOTIFICATIONS
import kotlinx.coroutines.delay
import com.droidates.wallpapers.core.ui.components.GlassmorphicBox
import com.droidates.wallpapers.core.ui.components.NoInternetConnectionScreen
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material.icons.outlined.GridView
import com.droidates.wallpapers.core.utils.LocalAdManager
import com.droidates.wallpapers.core.utils.LocalNetworkUtils
import com.droidates.wallpapers.core.viewmodel.DetailViewModel
import com.droidates.wallpapers.core.WallpaperApplication
import com.google.firebase.analytics.FirebaseAnalytics
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.compose.material.icons.rounded.Star
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.net.toUri
import com.droidates.wallpapers.core.ui.theme.overlaySurfaceStrong
import com.droidates.wallpapers.core.ui.theme.detailGradientTop
import com.droidates.wallpapers.core.ui.theme.detailGradientMid
import com.droidates.wallpapers.core.ui.theme.detailGradientBottom

private const val TAG = "DetailScreen"
private const val VERBOSE_LOGGING = false

private inline fun debugLog(message: () -> String) {
    if (AppConfig.IS_DEBUG && VERBOSE_LOGGING) {
        Log.d(TAG, message())
    }
}

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
    
    // State to force reload images when wallpaper ID changes (now driven by single load pipeline)
    // Keep as an int so keys can depend on it if needed.
    val refreshTrigger = remember { mutableIntStateOf(0) }
    
    val context = LocalContext.current
    val activity = context.findActivity()
    val view = LocalView.current
    // RemoteConfig removed as part of remote config cleanup
    
    // Favorite button pop. Spring-driven, so a rapid second tap retargets the same
    // animation instead of being swallowed by a fixed 300ms timer.
    val favoriteBounce = rememberFavoriteBounce()

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
    
    // Make sure the ViewModel knows which source screen we're coming from
    LaunchedEffect(sourceScreen) {
        viewModel.setSourceScreen(sourceScreen)
    }

    LaunchedEffect(sortOption) {
        viewModel.setSortOption(sortOption)
    }
    
    // Create a scope for async operations
    val coroutineScope = rememberCoroutineScope()
    
    // State for notification permission dialog
    var showNotificationPermissionDialog by remember { mutableStateOf(false) }

    // Notification permission launcher
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        // Just mark as requested regardless of outcome
        preferencesManager?.setHasRequestedNotificationPermission(true)
        debugLog { "Notification permission granted: $isGranted" }
    }

    // Show ad on detail screen open if conditions are met
    LaunchedEffect(Unit) {
        // Using logged count for debugging purposes only
        debugLog { "Current visit count in AdManager: ${adManager.getDetailScreenVisitCount()}" }

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
            debugLog { "Returning from premium screen" }
            isReturningFromPremiumScreen = true
        }
    }

    // Handle no internet connection
    if (!isConnected) {
        debugLog { "No internet connection" }
        NoInternetConnectionScreen(
            onRetryClick = {
                debugLog { "Retry clicked - checking connection and refreshing" }
                if (networkUtils.isNetworkAvailable()) {
                    viewModel.loadWallpaper(sourceScreen, wallpaperId)
                }
            }
        )
        return
    }

    // Image loading state - explicitly set to true initially to show loading when coming from notification


    // Track if we're coming from a notification
    val isFromNotification = remember { source == "notification" }

    val scope = rememberCoroutineScope()
    var downloadCompleteWallpaper by remember { mutableStateOf<Wallpaper?>(null) }

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
    
    // Animated favorite button colour (scale now comes from favoriteBounce).
    val animatedFavoriteColor by animateColorAsState(
        targetValue = if (wallpaper?.isFavorite == true) Color.Red else Color.White,
        animationSpec = tween(durationMillis = 300, easing = EaseOutCubic),
        label = "favoriteColor"
    )
    val isLoading by viewModel.isLoading.collectAsState()
    
    var isPreviewMode by remember { mutableStateOf(false) }
    var isLockScreenPreview by remember { mutableStateOf(false) }
    var showSetWallpaperSheet by remember { mutableStateOf(false) }
    var showUnlockDialog by remember { mutableStateOf(false) }
    var showFeedbackDialog by remember { mutableStateOf(false) }
    var showReportDialog by remember { mutableStateOf(false) }
    var showPermissionSettingsDialog by remember { mutableStateOf(false) }
    var showPermissionRationaleDialog by remember { mutableStateOf(false) }
    var showDownloadQualitySheet by remember { mutableStateOf(false) }
    var isLoadingRewardAd by remember { mutableStateOf(true) }
    var rewardAdLoadFailedInSheet by remember { mutableStateOf(false) }
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
    
    // P0: Single authoritative wallpaper-load pipeline.
    // - resets all transient UI state once
    // - bumps refresh trigger once
    // - fires analytics once
    // - loads wallpaper once (IO)
    LaunchedEffect(wallpaperId, sourceScreen) {
        debugLog { "Loading wallpaper: $wallpaperId from source: $sourceScreen" }

        thumbnailLoaded = false
        highResLoaded = false
        showHighRes = false
        imageLoadingCompleted = false
        isImageLoading = true
        isUiVisible = true
        isCacheDetected = false
        loadingStartTime = 0L

        refreshTrigger.intValue = refreshTrigger.intValue + 1

        // Analytics should not block UI; do minimal work here.
        viewModel.incrementViews(sourceScreen, wallpaperId)
        runCatching {
            val app = context.applicationContext as? WallpaperApplication
            val params = Bundle().apply {
                putString("wallpaper_id", wallpaperId)
                putString("source", sourceScreen)
            }
            app?.analytics?.logEvent("wallpaper_viewed", params)
        }.onFailure { e ->
            Log.e("DetailScreen", "Error logging analytics: ${e.message}")
        }

        scope.launch(Dispatchers.IO) {
            runCatching {
                viewModel.loadWallpaper(sourceScreen, wallpaperId, updateFlow = true)
            }.onFailure { e ->
                Log.e("DetailScreen", "Error during loadWallpaper: ${e.message}")
            }
        }
    }
    
    // The wallpaper loading will be handled by the ViewModel

    // Use fixed colors instead of animated colors
    val statIconColor = MaterialTheme.colorScheme.primary

    // Track UI visibility state based on whether full image is loaded
    var isUiReady by remember { mutableStateOf(false) }
    
    // Check and update hasWatchedAd state based on premium status and unlock status
    LaunchedEffect(wallpaper, isPremiumUser) {
        // For premium users, always consider content as unlocked
        if (isPremiumUser) {
            hasWatchedAd = true
            debugLog { "Premium user - content is unlocked" }
        } else if (wallpaper?.exclusive == true) {
            // Unlocks are written to DataStore by addUnlockedWallpaper() but used to be
            // read back only from DetailViewModel's in-memory set, which lives in a
            // companion object behind a 2-minute window. So a user who watched a full
            // rewarded ad lost the unlock as soon as the app was killed — or after two
            // minutes of reading — and was asked to watch another ad for content they
            // had already paid for with their attention.
            //
            // Check the persisted set first and fall back to the in-memory one, which
            // still covers the moments between the reward callback and the DataStore
            // write landing.
            hasWatchedAd = userPreferences.isWallpaperUnlocked(wallpaperId) ||
                viewModel.isWallpaperUnlocked(wallpaperId)
            debugLog { "Regular user exclusive unlock (persisted or session): $hasWatchedAd" }
        } else {
            // Non-exclusive content is always available
            hasWatchedAd = true
            debugLog { "Non-exclusive content unlocked" }
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

    // Legacy storage permission, only meaningful on API <= 28. From Android 10 (Q) on,
    // every path here goes through MediaStore — writing and reading back our own files
    // needs no permission — so nothing is requested. READ_MEDIA_IMAGES must NOT be used:
    // Play's Photo and Video Permissions policy rejects it for apps that don't browse
    // the user's gallery.
    // Session gate state. pendingGatedAction holds the work the user was trying to do
    // so it can resume after they watch a rewarded ad.
    var showLimitGate by remember { mutableStateOf(false) }
    var pendingGatedAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    val permissionToRequest = Manifest.permission.WRITE_EXTERNAL_STORAGE
    val needsRuntimePermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            when (viewModel.permissionRequestType) {
                PermissionRequestType.DOWNLOAD -> {
                    wallpaper?.let { wall ->
                        val startDownload = {
                            adManager.recordWallpaperAction()
                            viewModel.downloadWallpaper(
                                context = context,
                                wallpaper = wall,
                                showToast = false,
                                onComplete = { downloadCompleteWallpaper = wall }
                            )
                        }
                        // Ad first, then download — see runDownload for why the old
                        // post-download ordering could lose the completion callback.
                        val act = activity
                        if (act != null && !isPremiumUser) {
                            debugLog { "Showing ad before download for non-premium user" }
                            adManager.showInterstitialThen(act) { startDownload() }
                        } else {
                            debugLog { "User is premium or no activity, skipping pre-download ad" }
                            startDownload()
                        }
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
                showPermissionSettingsDialog = true
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

    fun onPermissionGranted(type: PermissionRequestType, onGranted: () -> Unit = {}) {
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

    fun checkAndRequestPermission(type: PermissionRequestType, onGranted: () -> Unit = {}) {
        debugLog { "Checking permission for: $type" }
        viewModel.permissionRequestType = type

        // Android 10+: MediaStore covers download, share and edit alike — no permission.
        if (!needsRuntimePermission) {
            debugLog { "Skipping storage permission on Android 10+ (MediaStore)" }
            onPermissionGranted(type, onGranted)
            return
        }

        when {
            ContextCompat.checkSelfPermission(
                context,
                permissionToRequest
            ) == PackageManager.PERMISSION_GRANTED -> {
                debugLog { "Permission already granted for: $type" }
                onPermissionGranted(type, onGranted)
            }

            // `context as Activity` crashed here in production (IllegalStateException /
            // ClassCastException): the Compose LocalContext is not always the Activity —
            // several OEM skins hand back a ContextWrapper. findActivity() unwraps the
            // wrapper chain instead of casting, and when there is genuinely no Activity
            // we fall through to requesting the permission, which is what the rationale
            // branch would have led to anyway.
            activity != null &&
                shouldShowRequestPermissionRationale(activity, permissionToRequest) -> {
                debugLog { "Showing permission rationale for: $type" }
                showPermissionRationaleDialog = true
            }

            else -> {
                // First time asking for permission
                debugLog { "Requesting permission for first time: $type" }
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

        // Preload only interstitial on launch; reward ad is loaded on demand
        isLoadingRewardAd = false
        adManager.loadInterstitialAd()
    }

    // Clean up FLAG_SECURE when leaving screen
    DisposableEffect(Unit) {
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
    



    fun cloudFrontFitInUrl(url: String, width: Int, height: Int): String {
        val marker = ".cloudfront.net/"
        val markerIndex = url.indexOf(marker)
        if (markerIndex == -1) return url

        val domainEnd = markerIndex + marker.length
        var path = url.substring(domainEnd).trimStart('/')
        if (path.startsWith("fit-in/")) {
            val segments = path.split("/")
            path = if (segments.size > 2) {
                segments.drop(2).joinToString("/")
            } else {
                path
            }
        }
        return url.substring(0, domainEnd) + "fit-in/${width}x${height}/$path"
    }

    /**
     * @param skipInterstitial set when the user has *already* watched a rewarded ad to
     * unlock this download. Showing an interstitial straight after a rewarded ad means
     * two full-screen ads back to back for a single action, which reads as punishing
     * the user for engaging with the reward.
     */
    fun runDownload(imageUrl: String, skipInterstitial: Boolean = false) {
        checkAndRequestPermission(PermissionRequestType.DOWNLOAD) {
            wallpaper?.let { wall ->
                // The actual download, run only once the ad (if any) is out of the way.
                val startDownload = {
                    adManager.recordWallpaperAction()
                    viewModel.downloadWallpaper(
                        context = context,
                        wallpaper = wall,
                        imageUrlOverride = imageUrl,
                        showToast = false,
                        onComplete = {
                            downloadCompleteWallpaper = wall

                            // Successful download — counts toward asking for notification
                            // permission at a moment the user is happy.
                            NotificationPermissionPrompt.recordSuccess(context)
                        }
                    )
                }

                // Ad FIRST, then the download. Showing it afterwards used to race the
                // activity teardown and could drop the completion callback entirely.
                val act = activity
                if (act != null && !isPremiumUser && !skipInterstitial) {
                    debugLog { "Showing ad before download for non-premium user" }
                    adManager.showInterstitialThen(act) { startDownload() }
                } else {
                    debugLog { "No pre-download ad (premium, reward already watched, or no activity)" }
                    startDownload()
                }
            }
        }
    }

    fun downloadStandardQuality() {
        wallpaper?.let { wall ->
            val standardUrl = cloudFrontFitInUrl(wall.imageUrl, width = 1080, height = 1920)

            // Session gate. Only the free standard download is gated —
            // downloadOriginalQuality already costs a rewarded ad, and charging twice
            // for one download is exactly the kind of trade that loses users.
            if (!adManager.hasFreeActionsLeft()) {
                pendingGatedAction = { runDownload(standardUrl, skipInterstitial = true) }
                showLimitGate = true
                return
            }

            Toast.makeText(context, "Starting standard download...", Toast.LENGTH_SHORT).show()
            runDownload(standardUrl)
        }
    }

    fun downloadOriginalQuality() {
        wallpaper?.let { wall ->
            if (isLoadingRewardAd) return
            Toast.makeText(context, "Preparing original quality download...", Toast.LENGTH_SHORT).show()
            if (isPremiumUser) {
                showDownloadQualitySheet = false
                runDownload(wall.imageUrl)
                return
            }

            isLoadingRewardAd = true
            rewardAdLoadFailedInSheet = false
            var rewardGranted = false
            adManager.loadRewardAd(
                onAdLoaded = {
                    isLoadingRewardAd = false
                    showDownloadQualitySheet = false
                    val hostActivity = activity
                    if (hostActivity == null) {
                        showDownloadQualitySheet = true
                        rewardAdLoadFailedInSheet = true
                        return@loadRewardAd
                    }
                    adManager.showRewardAd(
                        activity = hostActivity,
                        // Only record the reward here. Starting the download from
                        // onRewarded runs it while the ad is still on screen and the
                        // activity is backgrounded, which loses the download and drops
                        // the user back on the quality sheet.
                        onRewarded = { rewardGranted = true },
                        onAdDismissed = {
                            if (rewardGranted) {
                                // Reward already earned — do not stack an interstitial.
                                runDownload(wall.imageUrl, skipInterstitial = true)
                            } else {
                                Toast.makeText(
                                    context,
                                    "Watch full ad to unlock original quality.",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    )
                },
                onAdFailed = {
                    isLoadingRewardAd = false
                    showDownloadQualitySheet = true
                    rewardAdLoadFailedInSheet = true
                }
            )
        }
    }

    // Update the handleDownloadClick function with quality selection
    fun handleDownloadClick() {
        if (wallpaper?.exclusive == true && !hasWatchedAd && !isPremiumUser) {
            showUnlockDialog = true
        } else {
            showDownloadQualitySheet = true
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

    // The actual wallpaper work, split out so the gate and the interstitial can both
    // defer it without duplicating either path.
    fun runWallpaperSet(option: WallpaperSetOption) {
        wallpaper?.let { wall ->
            adManager.recordWallpaperAction()

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

                                // No post-apply ad here: the interstitial already ran
                                // before this work started (see handleWallpaperSet).

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

                        // Show toast after completion
                        val message = when (option) {
                            WallpaperSetOption.HOME_SCREEN -> "Set as home screen wallpaper"
                            WallpaperSetOption.LOCK_SCREEN -> "Set as lock screen wallpaper"
                            WallpaperSetOption.BOTH_SCREENS -> "Set as home and lock screen wallpaper"
                            else -> "Wallpaper set successfully"
                        }
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()

                        // The user just got what they came for — best moment to ask
                        // for notification permission (see NotificationPermissionPrompt).
                        NotificationPermissionPrompt.recordSuccess(context)
                    }
                )
            }
        }
    }

    // Handle wallpaper setting with ad
    //
    // Ad ordering: the interstitial is shown BEFORE the wallpaper work starts. The
    // apply paths below hand off to the system wallpaper picker, and an ad fired on
    // the way back used to land on a half-restored activity — the exact case where
    // the dismissal callback is skipped.
    fun handleWallpaperSet(option: WallpaperSetOption) {
        wallpaper?.let { wall ->
            // First check if this is exclusive content that requires watching an ad
            // Skip for premium users
            if (wall.exclusive && !hasWatchedAd && !isPremiumUser) {
                showUnlockDialog = true
                return
            }

            // Session gate: after the free allowance the user is offered premium or a
            // rewarded ad. Checked before the interstitial so they are never shown a
            // full-screen ad and then immediately asked to watch another.
            if (!adManager.hasFreeActionsLeft()) {
                pendingGatedAction = { runWallpaperSet(option) }
                showLimitGate = true
                return
            }

            val act = activity
            if (act != null && !isPremiumUser) {
                adManager.showInterstitialThen(act) { runWallpaperSet(option) }
            } else {
                runWallpaperSet(option)
            }
        }
    }

    // P0: removed duplicate wallpaperId load effect (handled above)

    // Shows the POST_NOTIFICATIONS dialog once, and only after the user has had a
    // successful download/apply. Dormant otherwise.
    NotificationPermissionEffect()

    // Track initial entry to the screen for logging
    val initialEntry = remember { mutableStateOf(true) }
    if (initialEntry.value) {
        initialEntry.value = false
    }
    
    val backgroundColor by viewModel.backgroundColor.collectAsState()
    val downloadProgress by viewModel.downloadProgress.collectAsState()
    val isDownloading by viewModel.isDownloading.collectAsState()
    val isSettingWallpaper by viewModel.isSettingWallpaper.collectAsState()
    val isSharing by viewModel.isSharing.collectAsState()

    // Track metadata loading state in a way that prevents UI flickering
    val localDownloads by viewModel.localDownloads.collectAsState()

    // Ad-free offer. Triggered off interstitialShownCount so it appears just after the
    // user has sat through a full-screen ad — the moment ad fatigue is highest and a
    // rewarded trade is most welcome. Offered at most once per session.
    val interstitialShownCount by adManager.interstitialShownCount.collectAsState()
    var adFreeOfferShownThisSession by rememberSaveable { mutableStateOf(false) }
    var showAdFreeOffer by remember { mutableStateOf(false) }

    LaunchedEffect(interstitialShownCount) {
        if (interstitialShownCount >= 2 &&
            !adFreeOfferShownThisSession &&
            !isPremiumUser &&
            !adManager.isAdFreeActive()
        ) {
            // Warm a rewarded ad first. showRewardAd() silently falls through to its
            // dismiss callback when nothing is loaded, so offering without one ready
            // would spend the session's single offer on a dialog that does nothing.
            if (!adManager.isRewardedAdLoaded()) adManager.loadRewardAd()
            // Let the interstitial finish dismissing before stacking a dialog on top.
            kotlinx.coroutines.delay(1500)
            if (adManager.isRewardedAdLoaded()) {
                adFreeOfferShownThisSession = true
                showAdFreeOffer = true
            }
        }
    }
    if (showLimitGate) {
        WallpaperLimitGateDialog(
            adManager = adManager,
            activity = activity,
            freeActionsUsed = adManager.sessionActionsUsed(),
            onGoPremium = { navigationState.navigateToPremium() },
            onDismiss = {
                showLimitGate = false
                pendingGatedAction = null
            },
            onProceed = {
                val action = pendingGatedAction
                pendingGatedAction = null
                action?.invoke()
            }
        )
    }

    if (showAdFreeOffer) {
        AdFreeOfferDialog(
            adManager = adManager,
            activity = activity,
            onDismiss = { showAdFreeOffer = false }
        )
    }

    // Download button phase: spinner while in flight, then a checkmark once the
    // download actually succeeded. Keyed off a counter so a second download in the
    // same session still flashes confirmation.
    // NOTE: the viewmodel increments the success counter *before* it clears
    // isDownloading, so these must be resolved in one effect — handling them
    // separately let the isDownloading transition overwrite Done and eat the
    // checkmark.
    val downloadSuccessCount by viewModel.downloadSuccessCount.collectAsState()
    var downloadPhase by remember { mutableStateOf(ActionPhase.Idle) }
    var pendingSuccess by remember { mutableStateOf(false) }
    LaunchedEffect(downloadSuccessCount) {
        if (downloadSuccessCount > 0) pendingSuccess = true
    }
    LaunchedEffect(isDownloading, pendingSuccess) {
        downloadPhase = when {
            // Success outranks the in-flight flag, whichever order they arrive in.
            pendingSuccess -> ActionPhase.Done
            isDownloading -> ActionPhase.Working
            else -> ActionPhase.Idle
        }
        if (pendingSuccess) {
            kotlinx.coroutines.delay(1200)
            pendingSuccess = false
            downloadPhase = if (isDownloading) ActionPhase.Working else ActionPhase.Idle
        }
    }
    val localViews by viewModel.localViews.collectAsState()

    // REMOVED: This was causing progress indicator to disappear before image loads
    // The image loading state should only be controlled by AsyncImage callbacks
    // LaunchedEffect(wallpaper) { ... } - REMOVED

    // Keep only the safety timeout; do NOT reset thumbnail/high-res state again on wallpaper emissions.
    LaunchedEffect(wallpaper?.id) {
        val id = wallpaper?.id ?: return@LaunchedEffect
        if (id != wallpaperId) return@LaunchedEffect
        scope.launch {
            // Safety net only. This used to fire after 3s and claim the image had
            // loaded (highResLoaded = true), which hid the progress indicator while a
            // large wallpaper was still downloading — the user was left looking at the
            // low-res thumbnail with no indication anything was happening.
            // 30s is long enough for a multi-MB image on a slow connection, and we no
            // longer lie about the result: only the spinner is cleared.
            delay(30_000)
            if (isImageLoading) {
                debugLog { "Safety timeout: clearing stuck loading indicator for $id" }
                isImageLoading = false
            }
        }
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
            debugLog { "Back press navigate back from source: $sourceScreen" }
            navigationState.navigateBack()
        }
    }

    // Add showInfoDialog state
    var showInfoDialog by remember { mutableStateOf(false) }

    // Vertical swipe navigation (in-memory; no NavController route changes per swipe)
    var swipeOffset by remember { mutableFloatStateOf(0f) }
    var swipeDirection by remember { mutableIntStateOf(0) }
    val animatedSwipeOffset = remember { Animatable(0f) }
    var isSwipeInProgress by remember { mutableStateOf(false) }
    var isAnimatingSwipe by remember { mutableStateOf(false) }
    var dragVelocity by remember { mutableFloatStateOf(0f) }
    var lastDragTime by remember { mutableLongStateOf(0L) }
    val swipeThreshold = screenHeight * 0.15f
    val previewThreshold = screenHeight * 0.05f
    val effectiveWallpaperId = wallpaper?.id ?: wallpaperId

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
                    // GRADIENT BLUE FIX: Create gradient background to prevent black flash
                    val gradientBrush = remember {
                        Brush.verticalGradient(
                            colors = listOf(
                                detailGradientTop,
                                detailGradientMid,
                                detailGradientBottom
                            )
                        )
                    }
                    
                    // Prepare image loading - ensure gradient background is shown immediately
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(gradientBrush)
                            .pointerInput(effectiveWallpaperId, sourceScreen) {
                                detectVerticalDragGestures(
                                    onDragStart = {
                                        if (isAnimatingSwipe) return@detectVerticalDragGestures
                                        isSwipeInProgress = true
                                        swipeDirection = 0
                                        swipeOffset = 0f
                                        dragVelocity = 0f
                                        lastDragTime = System.currentTimeMillis()
                                        scope.launch(Dispatchers.IO) {
                                            viewModel.ensureCollectionLoaded(
                                                effectiveWallpaperId,
                                                sourceScreen
                                            )
                                        }
                                    },
                                    onDragEnd = {
                                        val nextId = viewModel.getNextWallpaperId()
                                        val prevId = viewModel.getPreviousWallpaperId()
                                        val dragDistance = abs(swipeOffset)
                                        val velocityThreshold = 0.8f
                                        val shouldComplete = dragDistance > swipeThreshold ||
                                            (dragDistance > previewThreshold && abs(dragVelocity) > velocityThreshold)
                                        val hasAdjacent = (swipeDirection == -1 && nextId != null) ||
                                            (swipeDirection == 1 && prevId != null)

                                        if (shouldComplete && hasAdjacent) {
                                            isTransitioningWallpaper = true
                                            isAnimatingSwipe = true
                                            scope.launch {
                                                try {
                                                    val target = if (swipeDirection == -1) -screenHeight else screenHeight
                                                    animatedSwipeOffset.animateTo(
                                                        targetValue = target,
                                                        animationSpec = tween(150, easing = FastOutSlowInEasing)
                                                    )
                                                    if (swipeDirection == -1) {
                                                        viewModel.loadNextWallpaper()
                                                    } else {
                                                        viewModel.loadPreviousWallpaper()
                                                    }
                                                    highResLoaded = false
                                                    showHighRes = false
                                                    imageLoadingCompleted = false
                                                    animatedSwipeOffset.snapTo(0f)
                                                    swipeOffset = 0f
                                                    isTransitioningWallpaper = false
                                                } finally {
                                                    isAnimatingSwipe = false
                                                    isSwipeInProgress = false
                                                }
                                            }
                                        } else {
                                            isAnimatingSwipe = true
                                            scope.launch {
                                                try {
                                                    animatedSwipeOffset.animateTo(
                                                        0f,
                                                        animationSpec = tween(120, easing = FastOutSlowInEasing)
                                                    )
                                                    swipeOffset = 0f
                                                } finally {
                                                    isAnimatingSwipe = false
                                                    isSwipeInProgress = false
                                                }
                                            }
                                        }
                                    },
                                    onDragCancel = {
                                        scope.launch { animatedSwipeOffset.snapTo(0f) }
                                        swipeOffset = 0f
                                        isSwipeInProgress = false
                                    },
                                    onVerticalDrag = { change, dragAmount ->
                                        if (isAnimatingSwipe) return@detectVerticalDragGestures
                                        swipeOffset += dragAmount
                                        val now = System.currentTimeMillis()
                                        val delta = (now - lastDragTime).coerceAtLeast(1L)
                                        dragVelocity = dragAmount / delta
                                        lastDragTime = now
                                        swipeDirection = if (swipeOffset < 0f) -1 else 1
                                        val canSwipe = (swipeDirection == -1 && viewModel.getNextWallpaperId() != null) ||
                                            (swipeDirection == 1 && viewModel.getPreviousWallpaperId() != null)
                                        if (!canSwipe) {
                                            val maxResistance = screenHeight * 0.1f
                                            swipeOffset = swipeOffset.coerceIn(-maxResistance, maxResistance)
                                        }
                                        scope.launch { animatedSwipeOffset.snapTo(swipeOffset) }
                                        change.consume()
                                    }
                                )
                            }
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                if (!isSwipeInProgress && !isAnimatingSwipe) {
                                    if (isInfoContainerExpanded) {
                                        isInfoContainerExpanded = false
                                    }
                                }
                            }
                        ) {
                            // Handle wallpaper changes with simplified state management
                            LaunchedEffect(currentWallpaper.id) {
                                // Reset local states for new wallpaper.
                                // isImageLoading starts TRUE: the new wallpaper's high-res has not
                                // been fetched yet, so the spinner must be visible from the moment
                                // the page changes. Setting it false here left a gap — Coil's
                                // onLoading only fires once the request actually starts, so the
                                // user saw no indicator at all on a cache miss.
                                thumbnailLoaded = false
                                highResLoaded = false
                                highResLoadingStarted = false
                                showHighRes = false
                                imageLoadingCompleted = false
                                isImageLoading = true
                                loadingStartTime = System.currentTimeMillis()
                                debugLog { "Reset local loading states for wallpaper: ${currentWallpaper.id}" }
                            }

                            // Container with normal background + swipe transform
                                        Box(
                                            modifier = Modifier
                                            .fillMaxSize()
                                            .background(MaterialTheme.colorScheme.background)
                                            .graphicsLayer {
                                                translationY = animatedSwipeOffset.value.toSafeTranslation()
                                                val progress = if (screenHeight > 0f) {
                                                    (abs(animatedSwipeOffset.value) / screenHeight).coerceIn(0f, 1f)
                                                } else 0f
                                                val scale = (1f - progress * 0.05f).toSafeScale(0.85f, 1f)
                                                scaleX = scale
                                                scaleY = scale
                                            }
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null // No ripple effect
                                ) {
                                    if (!isSwipeInProgress && !isAnimatingSwipe) {
                                        if (isInfoContainerExpanded) {
                                            isInfoContainerExpanded = false
                                        } else {
                                            isUiVisible = !isUiVisible
                                        }
                                    }
                                }
                            ) {
                                // HERO ANIMATION FIX: NO GRADIENT BACKGROUND 
                                // Only the shared element thumbnail should be visible to allow proper hero animation
                                // PERF FIX: Removed composable-body Log.d that ran every recomposition

                                // Neutral backdrop behind the image layers. Swiping to a page whose
                                // bitmap has not decoded yet used to expose the window background as
                                // a black flash; a surface-coloured fill makes that moment read as
                                // an intentional placeholder instead of a glitch.
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                )

                                // 2. IMMEDIATE THUMBNAIL LOADING: Start loading immediately with enhanced fallback
                                val thumbnailUrl = remember(currentWallpaper) {
                                    val baseUrl = wallpaper?.thumbnail
                                        ?: currentWallpaper.thumbnail
                                        ?: currentWallpaper.imageUrl
                                    cloudFrontFitInUrl(baseUrl, width = 540, height = 960)
                                }
                                
                                // ULTRA-FAST THUMBNAIL: Show thumbnail immediately without complex animations
                                thumbnailUrl?.let { url ->
                                    val thumbnailRequest = remember(url) {
                                        ImageRequest.Builder(context)
                                            .data(url)
                                            .memoryCacheKey("thumb_${currentWallpaper?.id}")
                                            .diskCacheKey("thumb_${currentWallpaper?.id}")
                                            .crossfade(false)
                                            // CDN already caps this at 540x960; bound the decode too.
                                            .size(540, 960)
                                            .precision(Precision.INEXACT)
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
                                            debugLog { "Thumbnail loading for ${currentWallpaper?.id}" }
                                        },
                                        onSuccess = { 
                                            // Thumbnail loaded successfully
                                            isInitialized = true
                                            thumbnailLoaded = true
                                            debugLog { "Thumbnail loaded for ${currentWallpaper?.id}" }
                                        },
                                        onError = { 
                                            Log.e(TAG, "Thumbnail load failed for ${currentWallpaper?.id}")
                                        }
                                    )
                                }
                                        
                                // 3. HIGH-RES IMAGE: Load when wallpaper data is available with enhanced progress
                                val displayWallpaper = remember(currentWallpaper) {
                                    wallpaper ?: currentWallpaper
                                }
                                
                                displayWallpaper?.let { wp ->
                                    // PERFORMANCE FIX: Use optimized ImageRequest with proper caching
                                    // Some very large PNG originals return HTTP 413 from the CDN
                                    // even at full-screen size, but succeed at a smaller one. On
                                    // failure we retry once at half size instead of showing nothing.
                                    var highResScale by remember(wp.id) { mutableStateOf(1f) }
                                    val imageRequest = remember(wp.imageUrl, screenWidth, screenHeight, highResScale) {
                                        ImageRequest.Builder(context)
                                            // Ask CloudFront to resize instead of fetching the raw
                                            // original. Some originals (notably Pixel 7a PNGs and
                                            // Pixel 8/8a JPEGs) are large enough that the CDN answers
                                            // HTTP 413 Payload Too Large and the load fails outright.
                                            // A screen-sized render is also all we can display.
                                            .data(
                                                ImageUtils.cloudFrontFitInUrl(
                                                    wp.imageUrl,
                                                    width = (screenWidth * highResScale).toInt().coerceAtLeast(1),
                                                    height = (screenHeight * highResScale).toInt().coerceAtLeast(1)
                                                )
                                            )
                                            .memoryCacheKey(wp.id) // Use wallpaper ID as cache key
                                            .diskCacheKey(wp.id)
                                            .size(
                                                Size(
                                                    screenWidth.coerceAtLeast(1),
                                                    screenHeight.toInt().coerceAtLeast(1)
                                                )
                                            )
                                            .crossfade(false) // Disable crossfade for faster loading
                                            // Hardware bitmaps live in GPU memory and cannot exceed
                                            // GL_MAX_TEXTURE_SIZE. Large source images (4-6MB+, often
                                            // 4000px+ on a side) blow past that and Coil fails the
                                            // request outright — which is why big wallpapers showed
                                            // only the thumbnail. Software bitmaps have no such limit.
                                            .allowHardware(false)
                                            // Never decode larger than the screen. Without this a huge
                                            // source would be decoded at full resolution first.
                                            .precision(Precision.INEXACT)
                                                .build()
                                    }
                                                
                                            AsyncImage(
                                                model = imageRequest,
                                        contentDescription = "High resolution wallpaper",
                                                contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize(),
                                        onLoading = {
                                            debugLog { "High-res loading for ${wp.id}" }
                                            // Only start loading state if not already completed for this wallpaper
                                            if (!imageLoadingCompleted) {
                                                isImageLoading = true
                                                highResLoadingStarted = true
                                                loadingStartTime = System.currentTimeMillis()
                                            }
                                        },
                                        onSuccess = {
                                            val loadTime = if (loadingStartTime != 0L) System.currentTimeMillis() - loadingStartTime else -1L
                                            val isCached = loadTime in 0L..99L

                                            debugLog {
                                                "High-res success (${if (isCached) "cache" else "network"}) for ${wp.id} in ${if (loadTime >= 0) "${loadTime}ms" else "N/A"}"
                                            }
                                            
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
                                                    debugLog { "Cache hit: loading state reset after short delay" }
                                                }
                                            } else {
                                                // For non-cached images, hide loading immediately
                                                isImageLoading = false
                                                debugLog { "Network load: loading state reset immediately" }
                                            }
                                            
                                            // RELIABILITY FIX: Add a safety timeout to ensure indicator never gets stuck
                                            scope.launch {
                                                delay(1000) // Safety timeout of 1 second
                                                if (isImageLoading) {
                                                    debugLog { "Safety timeout: force reset loading state" }
                                                    isImageLoading = false
                                                }
                                            }
                                        },
                                        onError = { state ->
                                            if (highResScale > 0.5f) {
                                                // First failure: retry smaller (fixes CDN 413 on
                                                // oversized PNGs) and keep the spinner running.
                                                Log.w("DetailScreen", "High-res failed at full size for ${wp.id}, retrying smaller")
                                                highResScale = 0.5f
                                                return@AsyncImage
                                            }
                                            // Handle error state
                                            highResLoaded = false
                                            showHighRes = false
                                            isImageLoading = false
                                            imageLoadingCompleted = true // Mark as completed even on error
                                            // Log the actual cause — without it this failure is
                                            // impossible to diagnose from a bug report.
                                            Log.e(
                                                "DetailScreen",
                                                "High-res load error for ${wp.id} (${wp.imageUrl})",
                                                state.result.throwable
                                            )
                                }
                                    )
                            }
                            
                                val shouldShowProgress = isImageLoading && !highResLoaded
                                
                                if (shouldShowProgress) {
                                    debugLog { "Loading indicator shown for ${currentWallpaper?.id}" }
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

                            // Adjacent wallpaper preview while swiping (uses cached thumbnails)
                            if (isSwipeInProgress) {
                                val nextId = viewModel.getNextWallpaperId()
                                val prevId = viewModel.getPreviousWallpaperId()
                                if (swipeDirection == -1 && nextId != null && swipeOffset < 0f) {
                                    viewModel.getWallpaperById(nextId)?.let { nextWp ->
                                        val previewUrl = cloudFrontFitInUrl(
                                            nextWp.thumbnail ?: nextWp.imageUrl,
                                            width = 540,
                                            height = 960
                                        )
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .graphicsLayer {
                                                    translationY = screenHeight + animatedSwipeOffset.value
                                                    alpha = (abs(animatedSwipeOffset.value) / (screenHeight * 0.5f))
                                                        .coerceIn(0f, 1f)
                                                }
                                        ) {
                                            AsyncImage(
                                                model = ImageRequest.Builder(context)
                                                    .data(previewUrl)
                                                    .size(Size(540, 960))
                                                    .crossfade(false)
                                                    .allowHardware(true)
                                                    .build(),
                                                contentDescription = "Next wallpaper preview",
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                    }
                                }
                                if (swipeDirection == 1 && prevId != null && swipeOffset > 0f) {
                                    viewModel.getWallpaperById(prevId)?.let { prevWp ->
                                        val previewUrl = cloudFrontFitInUrl(
                                            prevWp.thumbnail ?: prevWp.imageUrl,
                                            width = 540,
                                            height = 960
                                        )
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .graphicsLayer {
                                                    translationY = -screenHeight + animatedSwipeOffset.value
                                                    alpha = (abs(animatedSwipeOffset.value) / (screenHeight * 0.5f))
                                                        .coerceIn(0f, 1f)
                                                }
                                        ) {
                                            AsyncImage(
                                                model = ImageRequest.Builder(context)
                                                    .data(previewUrl)
                                                    .size(Size(540, 960))
                                                    .crossfade(false)
                                                    .allowHardware(true)
                                                    .build(),
                                                contentDescription = "Previous wallpaper preview",
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                    }
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
                                        debugLog { "Back button clicked from source: $sourceScreen" }
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
                    AnimatedVisibility(
                        visible = !isInfoContainerExpanded && !isPreviewMode,
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
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
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
                                Row(verticalAlignment = Alignment.CenterVertically) {
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
                                cornerRadius = 32.dp,
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
                                            shape = RoundedCornerShape(32.dp)
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
                                                        StatItem(
                                                            icon = Icons.Rounded.ArrowCircleDown,
                                                            value = "${StatFormatter.formatStatValue(localDownloads ?: currentWallpaper.downloads)} downloads",
                                                            iconColor = Color.White
                                                        )
                                                        StatItem(
                                                            icon = Icons.Rounded.AspectRatio,
                                                            value = currentWallpaper.dimensions.ifBlank { "Not available" },
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
                                                        StatItem(
                                                            icon = Icons.Rounded.RemoveRedEye,
                                                            value = "${StatFormatter.formatStatValue(localViews ?: currentWallpaper.views)} views",
                                                            iconColor = Color.White
                                                        )
                                                        StatItem(
                                                            icon = Icons.Outlined.Storage,
                                                            value = currentWallpaper.size.ifBlank { "Not available" },
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
                                                        label = "download",
                                                        testTag = "detail_action_download",
                                                        onClick = { handleDownloadClick() },
                                                        phase = downloadPhase
                                                    )
                                                    ActionButton(
                                                        icon = if (currentWallpaper.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                                        label = "favorite",
                                                        testTag = "detail_action_favorite",
                                                        onClick = { 
                                                            // Haptic feedback
                                                            view.performHapticFeedback(
                                                                if (currentWallpaper.isFavorite) HapticFeedbackConstants.CONTEXT_CLICK 
                                                                else HapticFeedbackConstants.KEYBOARD_TAP
                                                            )
                                                            
                                                            // Trigger the pop
                                                            favoriteBounce.pop()
                                                            
                                                            // Toggle favorite
                                                            viewModel.toggleFavorite()
                                                        },
                                                        tint = animatedFavoriteColor,
                                                        scale = favoriteBounce.scale
                                                    )
                                                    ActionButton(
                                                        icon = Icons.Rounded.FormatPaint,
                                                        label = "set wallpaper",
                                                        testTag = "detail_action_set",
                                                        onClick = { handleSetWallpaperClick() },
                                                        isLoading = isSettingWallpaper && !isDownloading && !isSharing
                                                    )
                                                    ActionButton(
                                                        icon = Icons.Rounded.IosShare,
                                                        label = "share",
                                                        testTag = "detail_action_share",
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
                                                        label = "edit",
                                                        testTag = "detail_action_edit",
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
                }

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

        downloadCompleteWallpaper?.let { savedWallpaper ->
            DownloadCompleteActionToast(
                message = "Wallpaper saved",
                onOpen = {
                    openDownloadedWallpaperInGallery(context, savedWallpaper)
                    downloadCompleteWallpaper = null
                },
                onDismiss = { downloadCompleteWallpaper = null },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }

    if (showSetWallpaperSheet) {
        SetWallpaperBottomSheet(
            onDismiss = { showSetWallpaperSheet = false },
            onOptionSelected = { option ->
                // Close the sheet BEFORE the interstitial. The ad now runs ahead of the
                // apply, so leaving the sheet up means the user dismisses the ad and
                // lands back on the sheet with nothing applied.
                showSetWallpaperSheet = false
                handleWallpaperSet(option)
            },
            isSettingWallpaper = isSettingWallpaper,
            progress = downloadProgress,
            showExternalOption = true
        )
    }

    if (showDownloadQualitySheet) {
        DownloadQualityBottomSheet(
            onDismiss = {
                if (!isLoadingRewardAd) {
                    rewardAdLoadFailedInSheet = false
                    showDownloadQualitySheet = false
                }
            },
            isLoading = isDownloading || isLoadingRewardAd,
            showRewardAdError = rewardAdLoadFailedInSheet,
            onStandardSelected = {
                rewardAdLoadFailedInSheet = false
                showDownloadQualitySheet = false
                downloadStandardQuality()
            },
            onOriginalSelected = {
                downloadOriginalQuality()
            },
            onUpgrade = {
                rewardAdLoadFailedInSheet = false
                showDownloadQualitySheet = false
                navigationState.navigateToPremium()
            },
            onTryAfter = {
                rewardAdLoadFailedInSheet = false
                showDownloadQualitySheet = false
            }
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
                            debugLog { "Ad loading timed out after 15 seconds" }
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
                                debugLog { "User rewarded" }
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
                                debugLog { "Rewarded ad closed" }
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
            },
            onUpgrade = {
                debugLog { "Upgrade button clicked, navigating to premium" }
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
                                "${AppConfig.APP_NAME} - Report a Wallpaper"
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
                            debugLog { "Opened email in Gmail" }
                            return@launch
                        } else {
                            // Gmail not found, fall back to any email app
                            val fallbackIntent = Intent(Intent.ACTION_SENDTO).apply {
                                data = Uri.parse("mailto:${AppConfig.SUPPORT_EMAIL}")
                                putExtra(
                                    Intent.EXTRA_SUBJECT,
                                    "${AppConfig.APP_NAME} - Report a Wallpaper"
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

    if (showPermissionSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionSettingsDialog = false },
            title = { Text("Permission Required") },
            text = { Text("Storage permission is required to download wallpapers. Please enable it in app settings.") },
            confirmButton = {
                TextButton(onClick = {
                    showPermissionSettingsDialog = false
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    })
                }) { Text("Open Settings") }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionSettingsDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showPermissionRationaleDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionRationaleDialog = false },
            title = { Text("Permission Required") },
            text = { Text("Storage permission is required to download and share wallpapers.") },
            confirmButton = {
                TextButton(onClick = {
                    showPermissionRationaleDialog = false
                    permissionLauncher.launch(permissionToRequest)
                }) { Text("Grant Permission") }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionRationaleDialog = false }) { Text("Cancel") }
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

// Enhanced ActionButton implementation for bottom info container
@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun ActionButton(
    icon: ImageVector,
    label: String,
    testTag: String? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
    isLoading: Boolean = false,
    scale: Float = 1f,
    /** Set to [ActionPhase.Done] to flash a confirmation checkmark. */
    phase: ActionPhase? = null
) {
    val actionModifier = if (testTag != null) modifier.testTag(testTag) else modifier
    Surface(
        modifier = actionModifier
            .size(48.dp) // COMPACT: Smaller size for better layout
            .scale(scale) // Apply scale animation
            .shadow(
                elevation = 8.dp, // COMPACT: Reduced elevation
                shape = CircleShape,
                spotColor = Color.Black.copy(alpha = 0.5f),
                ambientColor = Color.Black.copy(alpha = 0.38f)
            )
            .clickable(onClick = onClick),
        shape = CircleShape,
        color = overlaySurfaceStrong.copy(alpha = 0.9f)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            ActionButtonContent(
                icon = icon,
                label = label,
                phase = phase ?: if (isLoading) ActionPhase.Working else ActionPhase.Idle,
                tint = tint
            )
        }
    }
}

enum class PermissionRequestType {
    DOWNLOAD, SHARE, EDIT
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DownloadQualityBottomSheet(
    onDismiss: () -> Unit,
    isLoading: Boolean,
    showRewardAdError: Boolean,
    onStandardSelected: () -> Unit,
    onOriginalSelected: () -> Unit,
    onUpgrade: () -> Unit,
    onTryAfter: () -> Unit
) {
    val modalBottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = modalBottomSheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        dragHandle = { BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.onSurfaceVariant) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when {
                isLoading -> {
                    Spacer(modifier = Modifier.height(8.dp))
                    LoadingIndicator(
                        modifier = Modifier.size(72.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Loading reward ad...",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                showRewardAdError -> {
                    Text(
                        text = "Reward ad unavailable",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "We could not load the reward ad. Check that you have not disabled ads. Purchase premium so you do not have to watch ads, or try again after some time.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Button(
                        onClick = onUpgrade,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Purchase Premium")
                    }
                    OutlinedButton(
                        onClick = onTryAfter,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Try After")
                    }
                }

                else -> {
                    Text(
                        text = "Download quality",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "Choose Standard for faster save, or watch a reward ad to download Original quality.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedButton(
                        onClick = onStandardSelected,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.Start,
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text("Standard (1080x1920)")
                            Text(
                                text = "Best for most devices, saves data, downloads faster",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Button(
                        onClick = onOriginalSelected,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.Start,
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text("Original (Watch Ad)")
                            Text(
                                text = "High quality (ad required)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun openDownloadedWallpaperInGallery(context: Context, wallpaper: Wallpaper) {
    val fileName = "${wallpaper.wallpaperName}_${wallpaper.id.takeLast(4)}.jpg"
    val targetUri = findDownloadedWallpaperUri(context, fileName)
        ?: MediaStore.Images.Media.EXTERNAL_CONTENT_URI

    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(targetUri, "image/*")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        )
    }.onFailure {
        Toast.makeText(context, "Unable to open gallery app", Toast.LENGTH_SHORT).show()
    }
}

private fun findDownloadedWallpaperUri(context: Context, fileName: String): Uri? {
    val resolver = context.contentResolver
    val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    val relativePath = "${Environment.DIRECTORY_PICTURES}/${AppConfig.DOWNLOAD_FOLDER_NAME}"
    val relativePathWithSlash = "$relativePath/"
    val selection = "${MediaStore.Images.Media.DISPLAY_NAME} = ? AND (${MediaStore.Images.Media.RELATIVE_PATH} = ? OR ${MediaStore.Images.Media.RELATIVE_PATH} = ?)"
    val selectionArgs = arrayOf(fileName, relativePath, relativePathWithSlash)
    val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC, ${MediaStore.Images.Media.DATE_MODIFIED} DESC"

    // MediaStore queries are not safe to run unguarded: RELATIVE_PATH does not exist
    // below API 29, and some OEM MediaStore implementations throw SQLiteException on
    // this selection even above it. The caller already falls back to the whole images
    // collection, so returning null degrades to "open the gallery" rather than crashing.
    return runCatching {
        resolver.query(
            collection,
            arrayOf(MediaStore.Images.Media._ID),
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
                Uri.withAppendedPath(collection, id.toString())
            } else {
                null
            }
        }
    }.onFailure {
        Log.w("DetailScreen", "MediaStore lookup for $fileName failed", it)
    }.getOrNull()
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
            debugLog { "Opened wallpaper picker in Google Photos" }
            return
        } catch (e: Exception) {
            debugLog { "Google Photos not available, showing chooser: ${e.message}" }
            intent.setPackage(null) // Reset package
        }

        // Create a chooser as last resort
        val chooserIntent = Intent.createChooser(intent, "Set as wallpaper using")
        chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        try {
            context.startActivity(chooserIntent)
            debugLog { "Opened wallpaper picker using chooser dialog" }
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
                            text = "This is an exclusive wallpaper designed by DroidAtes. Our exclusive wallpapers feature high-quality artwork with premium design elements specifically created for ${AppConfig.DEVICE_FAMILY_NAME} devices.",
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
