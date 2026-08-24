package com.droidates.wallpapers.core

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.runtime.*
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.droidates.wallpapers.core.data.preferences.LocalUserPreferences
import com.droidates.wallpapers.core.navigation.NavGraph
import com.droidates.wallpapers.core.ui.theme.WallpaperAppTheme
import com.droidates.wallpapers.core.utils.AdManager
import com.droidates.wallpapers.core.utils.ConsentManager
import com.droidates.wallpapers.core.utils.MobileAdsInitializer
import com.droidates.wallpapers.core.utils.LocalAdManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.compose.runtime.LaunchedEffect
import com.droidates.wallpapers.core.navigation.Screen
import com.droidates.wallpapers.core.notifications.EngagementNotificationWorker
import androidx.compose.runtime.collectAsState
import com.droidates.wallpapers.core.data.preferences.ThemePreferences
import com.droidates.wallpapers.core.data.preferences.UserPreferences
import com.droidates.wallpapers.core.ui.theme.ThemeMode
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import androidx.lifecycle.lifecycleScope
import com.droidates.wallpapers.core.navigation.rememberNavigationState
import androidx.activity.result.contract.ActivityResultContracts
import android.util.Log
import com.droidates.wallpapers.core.ui.components.UpdateDialog
import com.droidates.wallpapers.core.utils.PreferencesManager
import com.droidates.wallpapers.core.utils.UpdateManager
import com.droidates.wallpapers.core.ui.components.ErrorBoundary
import androidx.activity.compose.BackHandler
import kotlinx.coroutines.delay
import com.droidates.wallpapers.core.ui.screens.OnboardingScreen
import com.droidates.wallpapers.core.ui.screens.OnboardingPaywallScreen
import com.droidates.wallpapers.core.ui.screens.PrivacyAcceptScreen
import com.droidates.wallpapers.core.viewmodel.PremiumViewModel
// LocalRemoteConfig removed as part of remote config cleanup
import android.app.Activity
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.droidates.wallpapers.core.workers.InitializationWorker
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.DisposableEffect
import com.droidates.wallpapers.core.viewmodel.AuthViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import com.droidates.wallpapers.core.ui.components.ToastHost
import com.droidates.wallpapers.core.ui.components.ToastManager
import com.droidates.wallpapers.core.utils.LocalNetworkUtils
import com.droidates.wallpapers.core.utils.NetworkUtils
import com.droidates.wallpapers.core.ui.screens.CategoryScreenScrollStates
import com.droidates.wallpapers.core.config.AppConfig
import androidx.core.animation.doOnEnd
import androidx.core.app.FrameMetricsAggregator
import android.util.SparseIntArray
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG = "MainActivity"

private const val VERBOSE_LOGGING = false


@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    companion object {
        private const val INIT_WORK_NAME = "delayed_mobile_ads_initialization"
        private val initWorkScheduled = AtomicBoolean(false)
    }

    @Inject
    lateinit var adManager: AdManager

    @Inject
    lateinit var themePreferences: ThemePreferences
    
    @Inject
    lateinit var preferencesManager: PreferencesManager
    
    @Inject
    lateinit var updateManager: UpdateManager

    @Inject
    lateinit var userPreferences: UserPreferences
    
    @Inject
    lateinit var toastManager: ToastManager
    
    @Inject
    lateinit var networkUtils: NetworkUtils

    @Inject
    lateinit var firestore: com.google.firebase.firestore.FirebaseFirestore

    @Inject
    lateinit var performanceMonitor: com.droidates.wallpapers.core.utils.PerformanceMonitor
    
    private var pendingWallpaperId: String? = null

    /** Category to open from a notification tap; consumed once the NavGraph is ready. */
    private val pendingCategory = MutableStateFlow<String?>(null)

    private var initJob: Job? = null
    private var backPressedTime: Long = 0
    private val backToExitTimeThreshold = 2000L
    private var frameMetricsAggregator: FrameMetricsAggregator? = null

    /**
     * CRITICAL: Ultra-minimal notification processing on background thread
     */
    private fun processNotificationIntent(intent: Intent?) {
        if (intent == null) return
        
        try {
            val notificationId = intent.getStringExtra("notification_id")
            val notificationType = intent.getStringExtra("notification_type")
            
        } catch (e: Exception) {
            if (VERBOSE_LOGGING) {
                Log.e(TAG, "Error processing notification intent", e)
            }
        }
    }
    
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // The activity is singleTop, so a notification tap while it is already running
        // arrives here rather than in onCreate.
        setIntent(intent)
        handleNotificationIntent(intent.extras)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Install splash screen with proper Android 12+ API implementation
        val splashScreen = installSplashScreen()
        
        super.onCreate(savedInstanceState)

        // Start monitoring startup performance immediately
        performanceMonitor.startStartupMonitoring()
        networkUtils.initializeNetworkTracking()

        // Re-engagement reminders: KEEP policy means this is a no-op once scheduled.
        // recordAppOpen suppresses the reminder for active users.
        EngagementNotificationWorker.recordAppOpen(this)
        EngagementNotificationWorker.schedule(this)
        frameMetricsAggregator = FrameMetricsAggregator(FrameMetricsAggregator.TOTAL_DURATION).apply {
            add(this@MainActivity)
        }

        // Ultra-fast splash screen - minimal visibility to reduce lag
        var isAppReady by mutableStateOf(false)

        // Keep splash visible for very short duration
        splashScreen.setKeepOnScreenCondition {
            !isAppReady
        }

        // OPTIMIZED: No artificial delay - let app load naturally as fast as possible
        // The onboarding check will set isAppReady = true once complete
        // This eliminates the 100ms artificial delay for instant startup
        
        // Set up splash screen exit animation
        splashScreen.setOnExitAnimationListener { splashScreenView ->
            // Custom exit animation - fade out with scale
            val fadeOut = android.animation.ObjectAnimator.ofFloat(
                splashScreenView.view, 
                android.view.View.ALPHA, 
                1f, 0f
            ).apply {
                duration = 400L
                interpolator = android.view.animation.AccelerateInterpolator()
            }
            
            val scaleOut = android.animation.ObjectAnimator.ofFloat(
                splashScreenView.view,
                android.view.View.SCALE_X,
                1f, 0.9f
            ).apply {
                duration = 400L
                interpolator = android.view.animation.AccelerateInterpolator()
            }
            
            val scaleOutY = android.animation.ObjectAnimator.ofFloat(
                splashScreenView.view,
                android.view.View.SCALE_Y,
                1f, 0.9f
            ).apply {
                duration = 400L
                interpolator = android.view.animation.AccelerateInterpolator()
            }
            
            android.animation.AnimatorSet().apply {
                playTogether(fadeOut, scaleOut, scaleOutY)
                doOnEnd { 
                    splashScreenView.remove()
                }
                start()
            }
        }
        
        if (VERBOSE_LOGGING) {
            Log.d(TAG, "MainActivity onCreate() - Natural splash screen timing")
        }

        // Ad consent (UMP). Must run before MobileAds initializes so the SDK picks up
        // the stored choice: EEA/UK users without a consent signal get non-personalised
        // ads at a much lower eCPM, and several mediation networks decline to bid.
        //
        // Outside those regions UMP reports NOT_REQUIRED and this is a no-op, so it adds
        // nothing to startup for most traffic. It never blocks the UI — the callback only
        // nudges ad initialization once the choice settles.
        ConsentManager.gather(this) {
            lifecycleScope.launch {
                if (ConsentManager.canRequestAds(applicationContext)) {
                    MobileAdsInitializer.ensureInitialized(applicationContext)
                }
            }
        }

        // CRITICAL: Request 120Hz refresh rate using multiple approaches for maximum compatibility
        try {
            // Find the highest refresh rate mode available
            @Suppress("DEPRECATION")
            val display = windowManager.defaultDisplay
            val supportedModes = display.supportedModes
            val highestRefreshRate = supportedModes.maxByOrNull { it.refreshRate }

            if (highestRefreshRate != null && highestRefreshRate.refreshRate >= 90f) {
                val targetFps = highestRefreshRate.refreshRate

                // Approach 1: Set preferred display mode (legacy, works on some devices)
                window.attributes = window.attributes.apply {
                    preferredDisplayModeId = highestRefreshRate.modeId

                    // Also set preferredRefreshRate for additional compatibility
                    preferredRefreshRate = targetFps
                }

                if (VERBOSE_LOGGING) {
                    Log.d(TAG, "Requested ${targetFps}Hz via preferredDisplayModeId + preferredRefreshRate")
                }
            }
        } catch (e: Exception) {
            if (VERBOSE_LOGGING) {
                Log.e(TAG, "Error setting refresh rate", e)
            }
        }

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
            ),
            navigationBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
            )
        )
        
        // OPTIMIZED: Delay heavy initialization to reduce startup lag from WebView loading
        // WebView initialization (140MB Chromium) only happens when first ad is requested
        // This eliminates the 500ms WebView lag from appearing during startup
        lifecycleScope.launch(Dispatchers.IO) {
            delay(3000) // Increased to 3s - gives UI time to fully render before WebView loads
            if (initWorkScheduled.compareAndSet(false, true)) {
                val initWorkRequest = OneTimeWorkRequestBuilder<InitializationWorker>().build()
                WorkManager.getInstance(this@MainActivity).enqueueUniqueWork(
                    INIT_WORK_NAME,
                    ExistingWorkPolicy.KEEP,
                    initWorkRequest
                )
            }
        }

        // REMOVED: AdManager.initialize() - ads will load lazily when first requested
        // This prevents WebView initialization from blocking startup entirely

        // OPTIMIZATION: Defer notification processing until after UI is fully loaded
        // Move to background thread to avoid blocking startup
        initJob = lifecycleScope.launch(Dispatchers.IO) {
            delay(500) // Reduced from 1s to 500ms for faster responsiveness
            processNotificationIntent(intent)
        }
        
        setContent {
            val navController = rememberNavController()
            val scope = rememberCoroutineScope()
            val navigationState = rememberNavigationState(navController, scope, userPreferences)

            LaunchedEffect(Unit) {
                navigationState.ensureHomeTabOnLaunch()
            }

            val themeMode by themePreferences.themeMode.collectAsState(initial = ThemeMode.SYSTEM)
            
            var showUpdateDialog by remember { mutableStateOf(false) }
            var updateMessage by remember { mutableStateOf("") }
            var isMandatoryUpdate by remember { mutableStateOf(false) }
            var showOnboarding by remember { mutableStateOf(false) }
            var onboardingCheckCompleted by remember { mutableStateOf(false) }
            var updateAttempted by remember { mutableStateOf(false) }
            var pendingUpdateMessage by remember { mutableStateOf("") }
            var pendingMandatoryUpdate by remember { mutableStateOf(false) }
            var showPostOnboardingPaywall by remember { mutableStateOf(false) }
            var showPrivacyAcceptScreen by remember { mutableStateOf(false) }
            
            
            LaunchedEffect(Unit) {
                withContext(Dispatchers.IO) {
                    try {
                        // Check if onboarding has been completed
                        val isCompleted = preferencesManager.isOnboardingCompleted()
                        val privacyAccepted = preferencesManager.isPrivacyPolicyAccepted()
                        // ANR FIX: Post to main thread without blocking
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            if (!isCompleted) {
                                showOnboarding = true
                            } else if (!privacyAccepted) {
                                showPrivacyAcceptScreen = true
                            }
                            onboardingCheckCompleted = true
                            // Mark app as ready when onboarding check is complete
                            isAppReady = true

                            if (VERBOSE_LOGGING) {
                                Log.d(TAG, "Onboarding setup - Show: $showOnboarding, Completed: $isCompleted")
                            }
                        }
                    } catch (e: Exception) {
                        // On error, default to showing onboarding to be safe
                        // ANR FIX: Post to main thread without blocking
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            showOnboarding = true
                            onboardingCheckCompleted = true
                            // Mark app as ready even on error
                            isAppReady = true
                            Log.e(TAG, "Error checking onboarding status, defaulting to show onboarding", e)
                        }
                    }
                }
            }

            // Check for app updates automatically on startup and periodically
            LaunchedEffect(onboardingCheckCompleted) {
                if (onboardingCheckCompleted && !showOnboarding) {
                    // OPTIMIZED: Initial check after minimal delay for faster user experience
                    delay(1000) // Reduced from 5s to 1s

                    fun performUpdateCheck() {
                        updateManager.checkForUpdates(this@MainActivity) { message, isMandatory ->
                            // Always update the state variables
                            updateMessage = message
                            isMandatoryUpdate = isMandatory
                            showUpdateDialog = true

                            if (VERBOSE_LOGGING) {
                                Log.d(TAG, "Update check - Mandatory: $isMandatory, Message: $message")
                            }
                        }
                    }

                    // Perform initial check
                    performUpdateCheck()

                    // OPTIONAL: Periodic check every 5 minutes (uncomment if needed)
                    // while (true) {
                    //     delay(5 * 60 * 1000) // 5 minutes
                    //     if (!showUpdateDialog) { // Only check if no dialog is currently showing
                    //         performUpdateCheck()
                    //     }
                    // }
                }
            }
            
            val authViewModel: AuthViewModel = hiltViewModel()
            
            val signInLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.StartActivityForResult()
            ) { result ->
                try {
                    if (VERBOSE_LOGGING) {
                        Log.d(TAG, "Sign in result received with code: ${result.resultCode}")
                    }
                    if (result.resultCode == RESULT_OK) {
                        authViewModel.handleSignInResult(result.data)
                    } else {
                        if (VERBOSE_LOGGING) {
                            Log.e(TAG, "Sign in failed with result code: ${result.resultCode}")
                        }
                        showErrorToast("Google sign-in failed. Please try again.")
                    }
                } catch (e: Exception) {
                    if (VERBOSE_LOGGING) {
                        Log.e(TAG, "Error handling sign-in result", e)
                    }
                    showErrorToast("Error during sign-in: ${e.message}")
                }
            }

            DisposableEffect(authViewModel) {
                AuthViewModel.signInLauncher = signInLauncher
                onDispose {
                    AuthViewModel.signInLauncher = null
                }
            }

            // Monitor app lifecycle to detect when user returns from Play Store without updating
            DisposableEffect(this@MainActivity.lifecycle) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME && updateAttempted) {
                        // User returned from Play Store - check if they actually updated
                        lifecycleScope.launch(Dispatchers.IO) {
                            try {
                                // Small delay to ensure app is fully resumed
                                delay(1000)

                                // Check current version against Firebase again
                                val currentVersionCode = updateManager.getCurrentAppVersionCode(this@MainActivity)

                                firestore.collection(AppConfig.COLLECTION_APP_UPDATE)
                                    .document(AppConfig.DOCUMENT_APP_UPDATE)
                                    .get()
                                    .addOnSuccessListener { document ->
                                        if (document.exists()) {
                                            val requiredVersionCode = document.getLong("version")?.toInt() ?: 0

                                            // If update is still needed, show dialog again
                                            if (requiredVersionCode > currentVersionCode) {
                                                lifecycleScope.launch(Dispatchers.Main) {
                                                    updateMessage = pendingUpdateMessage
                                                    isMandatoryUpdate = pendingMandatoryUpdate
                                                    showUpdateDialog = true
                                                    updateAttempted = false // Reset for next attempt

                                                    if (VERBOSE_LOGGING) {
                                                        Log.d(TAG, "User returned without updating - showing dialog again")
                                                    }
                                                }
                                            } else {
                                                // User actually updated - reset tracking
                                                updateAttempted = false
                                                if (VERBOSE_LOGGING) {
                                                    Log.d(TAG, "User successfully updated app")
                                                }
                                            }
                                        }
                                    }
                            } catch (e: Exception) {
                                Log.e(TAG, "Error checking update status on resume", e)
                                // Reset tracking on error
                                updateAttempted = false
                            }
                        }
                    }
                }

                this@MainActivity.lifecycle.addObserver(observer)
                onDispose {
                    this@MainActivity.lifecycle.removeObserver(observer)
                }
            }

            WallpaperAppTheme(themeMode = themeMode) {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .semantics { testTagsAsResourceId = true },
                    color = MaterialTheme.colorScheme.background
                ) {
                    CompositionLocalProvider(
                        LocalAdManager provides adManager,
                        LocalUserPreferences provides userPreferences,
                        LocalNetworkUtils provides networkUtils,
                        LocalToastManager provides toastManager
                    ) {
                        ErrorBoundary {
                            if (showOnboarding) {
                                OnboardingScreen(
                                    onComplete = {
                                        showOnboarding = false
                                        preferencesManager.setOnboardingCompleted(true)
                                        // Show privacy policy acceptance if not yet accepted
                                        if (!preferencesManager.isPrivacyPolicyAccepted()) {
                                            showPrivacyAcceptScreen = true
                                        } else if (!preferencesManager.isPaywallShownAfterOnboarding()) {
                                            showPostOnboardingPaywall = true
                                        }
                                    },
                                    preferencesManager = preferencesManager
                                )
                            } else if (showPrivacyAcceptScreen) {
                                PrivacyAcceptScreen(
                                    onAccept = {
                                        preferencesManager.setPrivacyPolicyAccepted(true)
                                        showPrivacyAcceptScreen = false
                                        if (!preferencesManager.isPaywallShownAfterOnboarding()) {
                                            showPostOnboardingPaywall = true
                                        }
                                    },
                                    onDecline = {
                                        // User declined — close the app
                                        finish()
                                    }
                                )
                            } else if (showPostOnboardingPaywall) {
                                OnboardingPaywallScreen(
                                    onContinue = {
                                        preferencesManager.setPaywallShownAfterOnboarding(true)
                                        showPostOnboardingPaywall = false
                                    },
                                    onDismiss = {
                                        preferencesManager.setPaywallShownAfterOnboarding(true)
                                        showPostOnboardingPaywall = false
                                    }
                                )
                            } else if (onboardingCheckCompleted) {
                                navController.currentBackStackEntryAsState()
                                val canPopNavStack = navController.previousBackStackEntry != null
                                BackHandler(enabled = !canPopNavStack) {
                                    val currentTime = System.currentTimeMillis()
                                    if (currentTime - backPressedTime < backToExitTimeThreshold) {
                                        finish()
                                    } else {
                                        backPressedTime = currentTime
                                        showErrorToast("Press back again to exit")
                                    }
                                }
                                // Deep-link from an engagement notification. Runs once the
                                // NavGraph exists, then clears so it doesn't re-fire on
                                // rotation or when returning from background.
                                val categoryToOpen by pendingCategory.collectAsState()
                                LaunchedEffect(categoryToOpen) {
                                    categoryToOpen?.let { name ->
                                        // Category names are route path segments, so a name
                                        // containing "/" or spaces must be encoded.
                                        val encoded = java.net.URLEncoder.encode(name, "UTF-8")
                                            .replace("+", "%20")
                                        runCatching {
                                            navController.navigate(Screen.Category.createRoute(encoded))
                                        }.onFailure {
                                            Log.e(TAG, "Could not open category '$name' from notification", it)
                                        }
                                        pendingCategory.value = null
                                    }
                                }

                                NavGraph(
                                    navController = navController,
                                    navigationState = navigationState
                                )
                            }
                            // If onboarding check not completed yet, show blank screen to prevent flashing
                        }
                        
                        // Update checking moved to WorkManager for better performance
                        // No heavy operations in the main UI thread
                        
                        if (showUpdateDialog) {
                            UpdateDialog(
                                updateMessage = updateMessage,
                                isMandatory = isMandatoryUpdate,
                                onCancel = {
                                    if (isMandatoryUpdate) {
                                        // For mandatory updates, close the app when user cancels
                                        finish()
                                    } else {
                                        // For optional updates, just dismiss the dialog
                                        showUpdateDialog = false
                                    }
                                },
                                onUpdate = {
                                    // Track that user attempted to update
                                    updateAttempted = true
                                    pendingUpdateMessage = updateMessage
                                    pendingMandatoryUpdate = isMandatoryUpdate
                                    showUpdateDialog = false
                                    updateManager.openPlayStore(this@MainActivity)
                                }
                            )
                        }
                    }
                }
            }
            
            ToastHost(toastManager = toastManager)
        }

        // OPTIMIZATION: Ultra-lightweight notification processing with minimal delay
        intent?.extras?.let { extras ->
            lifecycleScope.launch(Dispatchers.IO) {
                delay(300) // Reduced from 500ms to 300ms for instant response
                handleNotificationIntent(extras)
            }
        }
    }
    
    /**
     * CRITICAL: Ultra-minimal notification intent processing
     */
    private fun handleNotificationIntent(extras: Bundle?) {
        if (extras == null) return

        try {
            val wallpaperId = extras.getString("wallpaper_id")
            if (wallpaperId != null) {
                pendingWallpaperId = wallpaperId
                if (VERBOSE_LOGGING) {
                    Log.d(TAG, "Notification wallpaper ID: $wallpaperId")
                }
            }

            // Engagement notifications deep-link to a category.
            extras.getString(EngagementNotificationWorker.EXTRA_CATEGORY_NAME)
                ?.takeIf { it.isNotBlank() }
                ?.let {
                    pendingCategory.value = it
                    if (VERBOSE_LOGGING) {
                        Log.d(TAG, "Notification category: $it")
                    }
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling notification intent", e)
        }
    }

    private fun showErrorToast(message: String) {
        try {
            toastManager.showToast(
                message = message,
                icon = Icons.Default.Info
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error showing toast", e)
        }
    }

    override fun onDestroy() {
        frameMetricsAggregator?.remove(this)
        frameMetricsAggregator = null
        super.onDestroy()
        initJob?.cancel()
        
        // Clear scroll positions when the app is destroyed (not just backgrounded)
        if (isFinishing) {
            try {
                CategoryScreenScrollStates.clearScrollPositions()
                if (VERBOSE_LOGGING) {
                    Log.d(TAG, "Cleared CategoryScreen scroll positions on app destroy")
                }
                
                // FIXED: Also clear CategoriesTabScreen scroll states to ensure it starts from top
                try {
                    // Clear the static scroll state object using the clear function
                    val categoriesTabScrollStatesClass = Class.forName("com.droidates.wallpapers.core.ui.screens.tabs.CategoriesTabScreen\$CategoriesTabScrollStates")
                    val clearMethod = categoriesTabScrollStatesClass.getDeclaredMethod("clearScrollStates")
                    clearMethod.isAccessible = true
                    clearMethod.invoke(null)
                    
                    if (VERBOSE_LOGGING) {
                        Log.d(TAG, "Cleared CategoriesTabScreen scroll states on app destroy")
                    }
                } catch (e: Exception) {
                    if (VERBOSE_LOGGING) {
                        Log.e(TAG, "Error clearing CategoriesTabScreen scroll states", e)
                    }
                }
            } catch (e: Exception) {
                if (VERBOSE_LOGGING) {
                    Log.e(TAG, "Error clearing scroll positions", e)
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        val metrics = frameMetricsAggregator?.metrics ?: return
        val totalDuration = metrics.getOrNull(FrameMetricsAggregator.TOTAL_INDEX)
        val jankyFrames = countJankyFrames(totalDuration)
        if (jankyFrames > 0 && VERBOSE_LOGGING) {
            Log.d(TAG, "Jank telemetry: $jankyFrames frames slower than 16ms")
        }
    }

    private fun countJankyFrames(metric: SparseIntArray?): Int {
        if (metric == null) return 0
        var count = 0
        for (i in 0 until metric.size()) {
            val frameDurationBucketMs = metric.keyAt(i)
            if (frameDurationBucketMs > 16) {
                count += metric.valueAt(i)
            }
        }
        return count
    }
}

// Create a compositionLocal for the toast manager
val LocalToastManager = compositionLocalOf<ToastManager> {
    error("No ToastManager provided")
}
