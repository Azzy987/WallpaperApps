package com.droidates.wallpapers.utils

import android.app.Activity
import android.content.Context
import android.util.Log
import android.os.Handler
import android.os.Looper
import com.droidates.wallpapers.BuildConfig
import com.droidates.wallpapers.data.repository.AuthRepository
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.google.android.gms.ads.AdLoader
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicBoolean
import android.os.SystemClock

@Singleton
class AdManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authRepository: AuthRepository,
    private val adaptiveAdLoadingManager: AdaptiveAdLoadingManager,
    private val navigationPredictionManager: NavigationPredictionManager,
    private val circuitBreakerManager: CircuitBreakerManager
) {
    companion object {
        private const val TAG = "AdManager"
        
        // Use production ads only - IDs defined in AppConfig
        const val INTERSTITIAL_AD_ID = com.droidates.wallpapers.config.AppConfig.AD_INTERSTITIAL_ID
        const val REWARDED_AD_ID = com.droidates.wallpapers.config.AppConfig.AD_REWARDED_ID
        const val NATIVE_AD_ID = com.droidates.wallpapers.config.AppConfig.AD_NATIVE_ID
        
        // Cache invalidation timeouts (in milliseconds)
        private const val DEFAULT_CACHE_TIMEOUT = 1800000L // 30 minutes
        private const val LOW_MEMORY_CACHE_TIMEOUT = 900000L // 15 minutes
        private const val AGGRESSIVE_CACHE_TIMEOUT = 3600000L // 60 minutes
        
        // OPTIMIZATION: Reduced timeout for faster user experience
        private const val AD_LOAD_TIMEOUT = 15000L // Reduced from 20s to 15s
        
        // OPTIMIZATION: Enhanced retry control with adaptive backoff
        private const val MAX_RETRIES = 3
        private const val BASE_RETRY_DELAY_MS = 3000L // Reduced from 5000ms
        private const val RETRY_DELAY_MS = 3000L // Same as BASE_RETRY_DELAY_MS for backward compatibility
        
        // OPTIMIZATION: Cache control constants
        private const val CACHE_EXPIRY_TIME = 30 * 60 * 1000L // 30 minutes
        private const val PRELOAD_COOLDOWN = 60 * 1000L // 1 minute between preloads
    }

    // OPTIMIZATION: Add mutex for thread safety
    private val adLoadMutex = Mutex()

    // ANR FIX: Handler for posting to main thread without blocking
    private val mainHandler = Handler(Looper.getMainLooper())

    // Track initialization status
    private val isInitialized = AtomicBoolean(false)
    private val _initializationComplete = MutableStateFlow(false)

    // SCROLL FIX: Global scroll state to pause ad preloading during scroll
    private val _isGlobalScrollActive = MutableStateFlow(false)
    val isGlobalScrollActive: StateFlow<Boolean> = _isGlobalScrollActive

    // Track when scroll stopped to add cooldown period
    private var lastScrollStopTime = 0L
    private val SCROLL_STOP_COOLDOWN = 500L // 500ms cooldown after scroll stops

    /**
     * SCROLL PERFORMANCE FIX: Set scroll state to pause ad preloading
     * Called by HomeTabScreen when scroll starts/stops
     */
    fun setGlobalScrollActive(isScrolling: Boolean) {
        _isGlobalScrollActive.value = isScrolling
        if (!isScrolling) {
            // Record when scroll stopped for cooldown period
            lastScrollStopTime = SystemClock.elapsedRealtime()
        }
    }

    /**
     * Check if we should block ad loading (during scroll or cooldown period)
     */
    private fun shouldBlockAdLoading(): Boolean {
        if (_isGlobalScrollActive.value) return true

        // Check cooldown period after scroll stops
        val timeSinceScrollStop = SystemClock.elapsedRealtime() - lastScrollStopTime
        return timeSinceScrollStop < SCROLL_STOP_COOLDOWN
    }

    // OPTIMIZATION: Use a controlled scope with error handling
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob() + 
        CoroutineExceptionHandler { _, throwable ->
            if (throwable !is CancellationException) {
                Log.e(TAG, "Error in AdManager coroutine: ${throwable.message}", throwable)
            }
        }
    )
    
    // OPTIMIZATION: Track ad loading state with thread-safe primitives
    private var interstitialAd: InterstitialAd? = null
    private val isLoadingInterstitial = AtomicBoolean(false)
    private var interstitialAdLoadTime: Long = 0 // Track when the ad was loaded
    
    private var rewardedAd: RewardedAd? = null
    private val isLoadingRewarded = AtomicBoolean(false)
    private var rewardedAdLoadTime: Long = 0 // Track when the ad was loaded

    // Native ad management with stable caching
    private val nativeAdPool = mutableListOf<NativeAd>()
    private val nativeAdPositionCache = LinkedHashMap<Int, NativeAd>() // Position-specific cache
    private val isLoadingNativeAd = AtomicBoolean(false)
    private val maxNativeAdPoolSize = 3 // Reduced pool size to limit memory usage
    private val maxCachedPositions = 10 // Cache ads for up to 10 positions (enough for 220 wallpapers)
    private var lastNativeAdLoadTime = 0L
    private val nativeAdRetryCount = AtomicInteger(0)
    private val MIN_NATIVE_AD_LOAD_INTERVAL = 3000L // Reduced to 3 seconds
    
    // OPTIMIZATION: Track retry count
    private val interstitialRetryCount = AtomicInteger(0)
    private val rewardedRetryCount = AtomicInteger(0)
    
    // OPTIMIZATION: Track last load time
    private var lastInterstitialLoadAttemptTime = 0L
    private var lastRewardedLoadAttemptTime = 0L
    private val MIN_LOAD_INTERVAL = 10000L // Minimum 10 seconds between load attempts
    private val MIN_REWARDED_LOAD_INTERVAL = 2000L // Reduced: 2 seconds for rewarded ads (was 10 seconds)
    
    private var adLoadTimeoutJob: Job? = null
    private var detailScreenVisitCount = 0
    private var wallpaperSwipeCount = 0
    private var adsShownCount = 0
    private val KEY_UPGRADE_PROMPT_SHOWN = "upgrade_prompt_shown"
    private val KEY_ADS_SHOWN_COUNT = "ads_shown_count"
    
    // Premium user state
    private val _isPremiumUser = MutableStateFlow(false)
    val isPremiumUser: StateFlow<Boolean> = _isPremiumUser

    // State to trigger showing upgrade prompt in the UI
    private val _showUpgradePrompt = MutableStateFlow(false)
    val showUpgradePrompt: StateFlow<Boolean> = _showUpgradePrompt

    // Interstitial ad display count
    private val _interstitialShownCount = MutableStateFlow(0)
    val interstitialShownCount: StateFlow<Int> = _interstitialShownCount
    
    // Callback for when interstitial ad is dismissed
    private var interstitialAdDismissCallback: (() -> Unit)? = null

    // Callback for resetting wallpaper loading states after ad dismissal during swipe
    private var swipeAdDismissCallback: (() -> Unit)? = null
    
    /**
     * Set callback to reset wallpaper loading states when ad is dismissed during swipe
     */
    fun setSwipeAdDismissCallback(callback: (() -> Unit)?) {
        swipeAdDismissCallback = callback
    }

    // Cache the frequency value to avoid excessive logging
    private var lastFrequencyValue: Int = -1

    // Flag to reduce excessive logging
    private var shouldLogPremiumStatus = true
    
    // OPTIMIZATION: Track if MobileAds have been initialized at the Application level
    private var mobileAdsInitializedOutside = false




    init {
        // MAJOR OPTIMIZATION: Completely eliminate all initialization work from init block
        // This makes AdManager construction instant and prevents any startup delays
        detailScreenVisitCount = 0
        
        // MAJOR OPTIMIZATION: Only check premium status on-demand, not during construction
        // This eliminates expensive repository access during app startup
        Log.d(TAG, "AdManager constructed with minimal overhead - all initialization deferred")
        
        // MAJOR OPTIMIZATION: Remove cache invalidation checker from init
        // This will be started only when ads are actually needed
    }
    
    // OPTIMIZATION: New method to be called from outside to signal that MobileAds were already initialized
    fun setMobileAdsInitialized() {
        mobileAdsInitializedOutside = true
        isInitialized.set(true)
        _initializationComplete.value = true
    }
    
    /**
     * Start a periodic job to check for and invalidate stale ad caches
     */
    private fun startCacheInvalidationChecker() {
        coroutineScope.launch {
            while (true) {
                delay(300000) // Check every 5 minutes
                invalidateStaleAdCaches()
            }
        }
    }
    
    /**
     * Invalidate any ad caches that have exceeded their timeout period
     * This helps prevent showing stale ads and manages memory usage
     */
    private fun invalidateStaleAdCaches() {
        // Skip for premium users
        if (_isPremiumUser.value) return
        
        val currentTime = System.currentTimeMillis()
        
        // Determine appropriate timeout based on device conditions
        val cacheTimeout = when {
            adaptiveAdLoadingManager.isLowMemoryDevice() -> LOW_MEMORY_CACHE_TIMEOUT
            adaptiveAdLoadingManager.adLoadingStrategy.value == AdaptiveAdLoadingManager.AdLoadingStrategy.AGGRESSIVE -> 
                AGGRESSIVE_CACHE_TIMEOUT
            else -> DEFAULT_CACHE_TIMEOUT
        }
        
        // Check interstitial ad
        if (interstitialAd != null && currentTime - interstitialAdLoadTime > cacheTimeout) {
            Log.d(TAG, "Invalidating stale interstitial ad cache (age: ${(currentTime - interstitialAdLoadTime) / 1000}s)")
            interstitialAd = null

            // Preload a fresh ad if conditions are good and strategy is not AGGRESSIVE
            if (adaptiveAdLoadingManager.shouldPreloadAds() &&
                adaptiveAdLoadingManager.adLoadingStrategy.value != AdaptiveAdLoadingManager.AdLoadingStrategy.AGGRESSIVE) {
                coroutineScope.launch {
                    delay(1000) // Short delay before reloading
                    loadInterstitialAd()
                }
            }
        }

        // Check rewarded ad — null it out but do NOT auto-reload
        if (rewardedAd != null && currentTime - rewardedAdLoadTime > cacheTimeout) {
            Log.d(TAG, "Invalidating stale rewarded ad cache (age: ${(currentTime - rewardedAdLoadTime) / 1000}s)")
            rewardedAd = null
        }
    }
    
    /**
     * Record a screen navigation event to improve ad prediction accuracy
     * @param screenType The type of screen being navigated to
     */
    fun recordScreenNavigation(screenType: NavigationPredictionManager.ScreenType) {
        // Skip if user is premium
        if (_isPremiumUser.value) return

        // Record the navigation event
        navigationPredictionManager.recordNavigation(screenType)
    }
    

    // OPTIMIZATION: New public method for lazy initialization - only call when needed
    fun initialize() {
        if (isInitialized.get()) {
            Log.d(TAG, "MobileAds already initialized")
            return
        }
        
        try {
            // MAJOR OPTIMIZATION: Now start premium status checking when initialize is called
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    initializePremiumStatus()
                } catch (e: Exception) {
                    Log.e(TAG, "Error checking premium status", e)
                }
            }
            
            // MAJOR OPTIMIZATION: Start cache invalidation checker only when needed
            startCacheInvalidationChecker()
            
            // Start monitoring network and device conditions
            adaptiveAdLoadingManager.startMonitoring()
            Log.d(TAG, "Started adaptive ad loading monitoring")
            
            // CRITICAL: Configure MobileAds to disable audio features that cause scroll lag
            val requestConfiguration = com.google.android.gms.ads.RequestConfiguration.Builder()
                .apply {
                    if (BuildConfig.DEBUG) {
                        setTestDeviceIds(listOf("D924F12F1F4ABB2E998E0FD14C6133F8"))
                    }
                }
                .build()

            MobileAds.setRequestConfiguration(requestConfiguration)

            // Initialize MobileAds
            MobileAds.initialize(context) { initializationStatus ->
                val statusMap = initializationStatus.adapterStatusMap
                for ((adapter, status) in statusMap) {
                    Log.d(TAG, String.format("Adapter: %s, Status: %s", adapter, status.initializationState))
                }
                
                isInitialized.set(true)
                _initializationComplete.value = true
                
                // Preload ads if user is not premium (but only after full initialization)
                coroutineScope.launch {
                    delay(5000) // Extended delay to prevent WebView blocking main thread during startup
                if (!_isPremiumUser.value) {
                    preloadAds()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during AdManager initialization", e)
        }
    }
    
    private fun initializePremiumStatus() {
        coroutineScope.launch {
            authRepository.isPremiumUser.collect { isPremium ->
                val oldValue = _isPremiumUser.value
                _isPremiumUser.value = isPremium
                
                // Only log when status changes
                if (oldValue != isPremium && BuildConfig.DEBUG) {
                    Log.d(TAG, "Premium status set to: $isPremium")
                }
                
                // If premium status changed to false, we might need to initialize ads
                if (!isPremium && oldValue && _initializationComplete.value) {
                    // Now we need ads
                    preloadAds()
                }
            }
        }
    }

    // Public version that can be called from outside
    fun checkPremiumStatus() {
        coroutineScope.launch {
            try {
                // Get the current value first
                val isPremium = authRepository.isPremiumUser.first()
                val currentValue = _isPremiumUser.value
                
                if (currentValue != isPremium) {
                    if (BuildConfig.DEBUG) {
                        Log.d(TAG, "Premium status changed from $currentValue to $isPremium")
                    }
                    _isPremiumUser.value = isPremium
                    
                    // If status changed, force a re-check from repository
                    authRepository.refreshPremiumStatus()
                    
                    // If user is now premium, release ad resources
                    if (isPremium) {
                        releaseAdResources()
                    } else if (!currentValue && !isPremium && isInitialized.get()) {
                        // They remain non-premium, but we should ensure ads are loaded
                        preloadAds()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking premium status", e)
            }
        }
    }
    
    // OPTIMIZATION: New method to release ad resources when user becomes premium
    private fun releaseAdResources() {
        interstitialAd = null
        rewardedAd = null
        // Clear native ad pool and destroy native ads to free memory
        synchronized(nativeAdPool) {
            nativeAdPool.forEach { it.destroy() }
            nativeAdPool.clear()
        }
        // Clear cached position-specific ads
        synchronized(nativeAdPositionCache) {
            nativeAdPositionCache.values.forEach { it.destroy() }
            nativeAdPositionCache.clear()
        }
        isLoadingInterstitial.set(false)
        isLoadingRewarded.set(false)
        isLoadingNativeAd.set(false)
        interstitialRetryCount.set(0)
        rewardedRetryCount.set(0)
        nativeAdRetryCount.set(0)
        adLoadTimeoutJob?.cancel()
        adLoadTimeoutJob = null
    }
    
    fun cleanup() {
        // Cancel all coroutines when the manager is no longer needed
        coroutineScope.cancel()
        releaseAdResources()
    }
    
    private fun preloadAds() {
        // Ads are enabled by default for non-premium users
        if (_isPremiumUser.value) return
        
        // Ensure we're initialized before trying to load ads
        if (!isInitialized.get()) {
            initialize()
            return
        }
        
        // OPTIMIZATION: Use adaptive decision to determine if we should preload ads
        if (!adaptiveAdLoadingManager.shouldPreloadAds()) {
            Log.d(TAG, "Skipping ad preloading based on current network/device conditions")
            return
        }
        
        // Load ads with a slight delay to ensure initialization is complete
        coroutineScope.launch {
            // Use adaptive delay based on network conditions
            val adaptiveDelay = when (adaptiveAdLoadingManager.adLoadingStrategy.value) {
                AdaptiveAdLoadingManager.AdLoadingStrategy.MINIMAL -> 2000L
                AdaptiveAdLoadingManager.AdLoadingStrategy.REDUCED -> 1500L
                AdaptiveAdLoadingManager.AdLoadingStrategy.STANDARD -> 1000L
                AdaptiveAdLoadingManager.AdLoadingStrategy.AGGRESSIVE -> 500L
            }
            
            Log.d(TAG, "Preloading ads with strategy: ${adaptiveAdLoadingManager.adLoadingStrategy.value} after ${adaptiveDelay}ms delay")
            delay(adaptiveDelay)
            
            // OPTIMIZATION: Use intelligent preloading based on navigation prediction
            intelligentPreloadAds()

            // Also preload native ads for grid display - heavily delayed to prevent WebView blocking
            delay(5000) // Extended delay to prevent WebView initialization blocking main thread
            loadNativeAd()
        }
    }
    
    /**
     * Intelligently preload ads based on predicted user navigation patterns
     * This method uses the NavigationPredictionManager to determine which ads to preload
     * and when to preload them based on user behavior patterns
     */
    private fun intelligentPreloadAds() {
        // Skip if user is premium
        if (_isPremiumUser.value) return
        
        // Get current navigation prediction
        val prediction = navigationPredictionManager.currentPrediction.value
        
        if (prediction == null) {
            // No prediction available, use standard preloading
            Log.d(TAG, "No navigation prediction available, using standard preloading")
            loadInterstitialAd()
            return
        }
        
        // Determine which ads to preload based on predicted next screen
        when (prediction.predictedScreen) {
            NavigationPredictionManager.ScreenType.DETAIL -> {
                // User likely to navigate to detail screen, preload interstitial ad
                Log.d(TAG, "Prediction: User likely to navigate to DETAIL screen (confidence: ${prediction.confidence}), " +
                        "preloading interstitial ad")
                loadInterstitialAd()
            }

            NavigationPredictionManager.ScreenType.PREMIUM -> {
                // User likely to navigate to premium screen, preload interstitial ad
                Log.d(TAG, "Prediction: User likely to navigate to PREMIUM screen (confidence: ${prediction.confidence}), " +
                        "preloading interstitial ad")
                loadInterstitialAd()
            }

            NavigationPredictionManager.ScreenType.EDIT_WALLPAPER -> {
                // User likely to navigate to edit screen, preload interstitial ad
                Log.d(TAG, "Prediction: User likely to navigate to EDIT_WALLPAPER screen (confidence: ${prediction.confidence}), " +
                        "preloading interstitial ad")
                loadInterstitialAd()
            }

            else -> {
                // For other screens, use standard preloading based on confidence
                if (prediction.confidence > 0.7f) {
                    Log.d(TAG, "Prediction: User likely to navigate to ${prediction.predictedScreen} screen " +
                            "(confidence: ${prediction.confidence}), using standard preloading")
                    loadInterstitialAd()
                } else {
                    // Low confidence prediction, just load interstitial
                    Log.d(TAG, "Low confidence prediction (${prediction.confidence}), loading only interstitial ad")
                    loadInterstitialAd()
                }
            }
        }
    }

    fun getAdFrequency(): Int {
        // Default ad frequency - show ad every 5 visits (previously managed by RemoteConfig)
        val frequency = 5
        
        // Only log when the value changes to reduce log spam
        if (lastFrequencyValue != frequency) {
            Log.d(TAG, "Ad frequency set to: $frequency (default value)")
            lastFrequencyValue = frequency
        }
        
        return frequency
    }

    fun shouldShowAd(): Boolean {
        // Check premium status directly from StateFlow
        if (_isPremiumUser.value) {
            return false
        }
        
        // Default: ads are enabled for all non-premium users
        
        // Check if we've reached the threshold based on the frequency
        val frequency = getAdFrequency()
        val shouldShow = if (frequency > 0) {
            detailScreenVisitCount % frequency == 0 && detailScreenVisitCount > 0 && isInterstitialAdLoaded()
        } else {
            false
        }
        
        // If we should show an ad but it's not loaded, try to load one
        if (detailScreenVisitCount > 0 && detailScreenVisitCount % frequency == 0 && !isInterstitialAdLoaded()) {
            loadInterstitialAd()
        }
        
        return shouldShow
    }

    fun loadInterstitialAd(onAdLoaded: () -> Unit = {}) {
        // Run checks on current thread before moving to background
        // Skip if user is premium
        if (_isPremiumUser.value) {
            return
        }

        // SCROLL FIX: Block ALL ad loading during scroll to prevent audio queries
        if (shouldBlockAdLoading()) {
            // Silent blocking to reduce log spam during scroll
            return
        }
        
        // Check if circuit breaker allows this component to operate
        if (!circuitBreakerManager.isAllowed(CircuitBreakerManager.AdComponent.INTERSTITIAL)) {
            Log.w(TAG, "Interstitial ad loading blocked by circuit breaker")
            return
        }
        
        // Skip if already loading
        if (isLoadingInterstitial.getAndSet(true)) {
            return
        }
        
        // Skip if an ad is already loaded
        if (interstitialAd != null) {
            onAdLoaded()
            isLoadingInterstitial.set(false)
            return
        }
        
        // OPTIMIZATION: Use adaptive loading parameters based on network and device conditions
        val adStrategy = adaptiveAdLoadingManager.adLoadingStrategy.value
        Log.d(TAG, "Loading interstitial ad with strategy: $adStrategy")
        
        // Check if we're trying to load too frequently - use adaptive cooldown
        val currentTime = SystemClock.elapsedRealtime()
        if (currentTime - lastInterstitialLoadAttemptTime < MIN_LOAD_INTERVAL) {
            Log.d(TAG, "Skipping interstitial load - too soon since last attempt")
            isLoadingInterstitial.set(false)
            return
        }
        
        lastInterstitialLoadAttemptTime = currentTime
        
        // OPTIMIZATION: Use adaptive retry count based on network and device conditions
        val maxRetries = adaptiveAdLoadingManager.getRecommendedRetryCount()
        if (interstitialRetryCount.get() >= maxRetries) {
            Log.d(TAG, "Max retry count reached for interstitial ad: $maxRetries")
            isLoadingInterstitial.set(false)
            return
        }
        
        // Start loading
        isLoadingInterstitial.set(true)
        
        // Move the actual loading to a background thread
        coroutineScope.launch(Dispatchers.IO) {
            Log.d(TAG, "Loading interstitial ad on background thread")
            
            // OPTIMIZATION: Set adaptive timeout based on network and device conditions
            val adaptiveTimeout = adaptiveAdLoadingManager.getRecommendedAdTimeout()
            Log.d(TAG, "Using adaptive timeout for interstitial ad: ${adaptiveTimeout}ms")
            
            adLoadTimeoutJob?.cancel()
            adLoadTimeoutJob = launch {
                delay(adaptiveTimeout)
                if (isLoadingInterstitial.get()) {
                    Log.w(TAG, "Interstitial ad load timed out after ${adaptiveTimeout}ms")
                    isLoadingInterstitial.set(false)
                }
            }
            
            // ANR FIX: Post to main thread with delay to prevent blocking
            // This allows UI frames to process between ad operations
            mainHandler.postDelayed({
                try {
                    Log.d(TAG, "Creating ad request and starting load process")

                    // FIXED: Create ad request with proper configuration for back button prevention
                    val adRequest = AdRequest.Builder()
                        .build()

                    InterstitialAd.load(
                        context,
                        INTERSTITIAL_AD_ID,
                        adRequest,
                        object : InterstitialAdLoadCallback() {
                            override fun onAdLoaded(ad: InterstitialAd) {
                                Log.d(TAG, "Interstitial ad loaded successfully!")
                                adLoadTimeoutJob?.cancel()
                                interstitialAd = ad
                                interstitialAdLoadTime = System.currentTimeMillis() // Record load time for cache invalidation
                                isLoadingInterstitial.set(false)
                                interstitialRetryCount.set(0) // Reset retry count on success
                                
                                // Record success with circuit breaker
                                circuitBreakerManager.recordSuccess(CircuitBreakerManager.AdComponent.INTERSTITIAL, 0L)
                                
                                onAdLoaded()
                            }

                            override fun onAdFailedToLoad(error: LoadAdError) {
                                Log.e(TAG, "Interstitial ad failed to load. Error code: ${error.code}, message: ${error.message}")
                                adLoadTimeoutJob?.cancel()
                                interstitialAd = null
                                isLoadingInterstitial.set(false)
                                
                                // Record failure with circuit breaker
                                circuitBreakerManager.recordError(
                                    CircuitBreakerManager.AdComponent.INTERSTITIAL,
                                    "load_failed_${error.code}"
                                )
                                
                                // OPTIMIZATION: Use adaptive retry logic based on network and device conditions
                                val retries = interstitialRetryCount.incrementAndGet()
                                val adaptiveMaxRetries = adaptiveAdLoadingManager.getRecommendedRetryCount()
                                
                                if (retries < adaptiveMaxRetries) {
                                    // Use adaptive retry delay with exponential backoff
                                    val baseRetryDelay = adaptiveAdLoadingManager.getRecommendedRetryDelay()
                                    val backoffDelay = baseRetryDelay * (1 shl (retries - 1))
                                    
                                    Log.d(TAG, "Will retry interstitial ad load (attempt $retries of $adaptiveMaxRetries) after ${backoffDelay}ms")
                                    
                                    // ANR FIX: Retry loading after a delay without blocking main thread
                                    coroutineScope.launch {
                                        delay(backoffDelay.toLong())
                                        Log.d(TAG, "Retrying interstitial ad load after failure (attempt $retries)")
                                        loadInterstitialAd(onAdLoaded)
                                    }
                                } else {
                                    Log.w(TAG, "Max retry attempts ($MAX_RETRIES) reached for interstitial ad")
                                }
                            }
                        }
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Exception during interstitial ad loading: ${e.message}", e)
                    adLoadTimeoutJob?.cancel()
                    isLoadingInterstitial.set(false)
                }
            }, 100) // ANR FIX: 100ms delay allows UI to remain responsive
        }
    }

    /**
     * Preload interstitial ad for a specific activity
     * This method is optimized to avoid excessive preloading
     */
    fun preloadInterstitialAd() {
        // Skip for premium users
        if (_isPremiumUser.value) return
        
        // Skip if already loading or loaded
        if (isLoadingInterstitial.get() || interstitialAd != null) return
        
        // Skip if we've reached max retries
        if (interstitialRetryCount.get() >= MAX_RETRIES) return
        
        // Skip if too soon after last attempt
        val currentTime = SystemClock.elapsedRealtime()
        if (currentTime - lastInterstitialLoadAttemptTime < MIN_LOAD_INTERVAL) return
        
        // Only load if ad display is enabled
        if (!isInterstitialEnabled()) return
        
        // Load the ad
        loadInterstitialAd()
    }

    /**
     * Check if interstitial ads are enabled
     */
    fun isInterstitialEnabled(): Boolean {
        if (_isPremiumUser.value) return false
        
        // OPTIMIZATION: Always return true for non-premium users instead of checking remote config
        return true
    }

    /**
     * Check if an interstitial ad is loaded and ready to show
     */
    fun isInterstitialAdLoaded(): Boolean {
        return interstitialAd != null
    }

    /**
     * Check if the user is premium
     */
    fun isPremium(): Boolean {
        return _isPremiumUser.value
    }

    /**
     * Dismisses the upgrade prompt
     */
    fun dismissUpgradePrompt() {
        if (_showUpgradePrompt.value) {
            _showUpgradePrompt.value = false
        }
    }

    /**
     * Show upgrade prompt manually
     */
    fun showUpgradePrompt() {
        _showUpgradePrompt.value = true
    }

    /**
     * Get the current detail screen visit count
     */
    fun getDetailScreenVisitCount(): Int {
        return detailScreenVisitCount
    }

    /**
     * Increment the detail screen visit counter
     */
    fun incrementDetailScreenVisitCount() {
        detailScreenVisitCount++
        Log.d(TAG, "Detail screen visit count incremented to: $detailScreenVisitCount")
    }
    
    /**
     * Increment the wallpaper swipe counter
     */
    fun incrementWallpaperSwipeCount() {
        wallpaperSwipeCount++
        Log.d(TAG, "Wallpaper swipe count incremented to: $wallpaperSwipeCount")
    }
    
    /**
     * Check if an ad should be shown for the detail screen
     * Uses RAM-only counter that resets when app is closed
     */
    fun shouldShowAdForDetailScreen(): Boolean {
        // Disable interstitials if premium user
        if (_isPremiumUser.value) {
            return false
        }
        
        // Default: ads are enabled for all non-premium users
        
        // Check if we've reached the threshold based on the frequency
        val frequency = getAdFrequency()
        val shouldShow = if (frequency > 0) {
            detailScreenVisitCount % frequency == 0 && detailScreenVisitCount > 0 && isInterstitialAdLoaded()
        } else {
            false
        }
        
        // If we should show an ad but it's not loaded, try to load one
        if (detailScreenVisitCount > 0 && detailScreenVisitCount % frequency == 0 && !isInterstitialAdLoaded()) {
            loadInterstitialAd()
        }
        
        return shouldShow
    }
    
    /**
     * Check if an ad should be shown for wallpaper swipes
     * Shows ad every 5 swipes for non-premium users
     */
    fun shouldShowAdForWallpaperSwipe(): Boolean {
        // Disable interstitials if premium user
        if (_isPremiumUser.value) {
            return false
        }
        
        // Show ad every 5 swipes (changed from 4 as requested)
        val swipeFrequency = 5
        val shouldShow = wallpaperSwipeCount % swipeFrequency == 0 && wallpaperSwipeCount > 0 && isInterstitialAdLoaded()
        
        // If we should show an ad but it's not loaded, try to load one
        if (wallpaperSwipeCount > 0 && wallpaperSwipeCount % swipeFrequency == 0 && !isInterstitialAdLoaded()) {
            loadInterstitialAd()
        }
        
        // Performance optimization: removed frequent debug log
        return shouldShow
    }

    /**
     * Navigate with ad check - shows ad if needed then calls navigation
     */
    suspend fun navigateWithAdCheck(
        activity: Activity,
        navigationCallback: () -> Unit
    ) {
        // If premium user, navigate directly
        if (_isPremiumUser.value) {
            navigationCallback()
            return
        }
        
        // If ad should show and is loaded, show it
        if (shouldShowAdForDetailScreen() && isInterstitialAdLoaded()) {
            var completed = false
            val job = coroutineScope.launch {
                delay(1000) // Wait 1 second for ad to show
                if (!completed) {
                    // ANR FIX: Post to main thread without blocking
                    mainHandler.post {
                        navigationCallback()
                    }
                    completed = true
                }
            }

            // ANR FIX: Post to main thread with delay to prevent blocking
            mainHandler.postDelayed({
                try {
                    showInterstitialAd(
                        activity = activity,
                        onAdDismissed = {
                            if (!completed) {
                                completed = true
                                job.cancel()
                                navigationCallback()
                            }
                        },
                        onAdFailedToShow = {
                            if (!completed) {
                                completed = true
                                job.cancel()
                                navigationCallback()
                            }
                        }
                    )
                } catch (e: Exception) {
                    // Something went wrong with ad, just navigate
                    Log.e(TAG, "Error showing ad, navigating directly: ${e.message}")
                    if (!completed) {
                        completed = true
                        job.cancel()
                        navigationCallback()
                    }
                }
            }, 100) // ANR FIX: 100ms delay allows UI to remain responsive
        } else {
            // No ad to show, navigate directly
            navigationCallback()
        }
    }

    /**
     * Show interstitial ad if loaded, otherwise load and show
     */
    fun showInterstitialAd(
        activity: Activity,
        onAdDismissed: () -> Unit = {},
        onAdFailedToShow: () -> Unit = {},
        showPremiumPrompt: Boolean = false
    ) {
        // CRASH FIX: Check if activity is valid before showing ad
        if (activity.isDestroyed || activity.isFinishing) {
            Log.w(TAG, "Activity is destroyed/finishing, cannot show interstitial ad")
            onAdFailedToShow()
            return
        }
        
        // Skip for premium users
        if (_isPremiumUser.value) {
            onAdDismissed()
            return
        }
        
        // Store the callbacks
        interstitialAdDismissCallback = onAdDismissed
        
        // Get the loaded ad
        val ad = interstitialAd
        
        if (ad != null) {
            try {
                // FIXED: Implement proper back button prevention

                ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                    override fun onAdDismissedFullScreenContent() {
                        Log.d(TAG, "Interstitial ad dismissed")
                        interstitialAd = null
                        
                        // CRASH FIX: Check if activity is still valid before proceeding
                        if (activity.isDestroyed || activity.isFinishing) {
                            Log.w(TAG, "Activity is destroyed/finishing, skipping ad dismissed callback")
                            return
                        }
                        
                        // Increment counter
                        adsShownCount++
                        val preferences = context.getSharedPreferences("ad_manager_prefs", Context.MODE_PRIVATE)
                        preferences.edit().putInt(KEY_ADS_SHOWN_COUNT, adsShownCount).apply()
                        
                        // Show upgrade prompt after 3-4 ads
                        val shouldShowUpgradePrompt = adsShownCount >= 3 && 
                                                    !isPremium() && 
                                                    !preferences.getBoolean(KEY_UPGRADE_PROMPT_SHOWN, false)
                        
                        if (shouldShowUpgradePrompt || showPremiumPrompt) {
                            _showUpgradePrompt.value = true
                            // Mark as shown to avoid showing too often
                            preferences.edit().putBoolean(KEY_UPGRADE_PROMPT_SHOWN, true).apply()
                        }
                        
                        // CRASH FIX: Safely call callback with try-catch
                        try {
                            interstitialAdDismissCallback?.invoke()
                        } catch (e: Exception) {
                            Log.e(TAG, "Error in ad dismiss callback: ${e.message}")
                        }
                        
                        // SWIPE FIX: Reset wallpaper loading states if ad was shown during swipe
                        try {
                            swipeAdDismissCallback?.invoke()
                        } catch (e: Exception) {
                            Log.e(TAG, "Error in swipe ad dismiss callback: ${e.message}")
                        }
                        
                        // Load next ad with some delay
                        coroutineScope.launch {
                            delay(5000) // 5 second delay to avoid immediate reloading
                            loadInterstitialAd()
                        }
                    }
                    
                    override fun onAdFailedToShowFullScreenContent(error: AdError) {
                        Log.e(TAG, "Failed to show interstitial ad: ${error.message}")
                        interstitialAd = null
                        
                        // CRASH FIX: Check if activity is still valid before proceeding
                        if (activity.isDestroyed || activity.isFinishing) {
                            Log.w(TAG, "Activity is destroyed/finishing, skipping ad failed callback")
                            return
                        }
                        
                        // CRASH FIX: Safely call callback with try-catch
                        try {
                            onAdFailedToShow()
                        } catch (e: Exception) {
                            Log.e(TAG, "Error in ad failed callback: ${e.message}")
                        }
                        
                        // Try to reload the ad after a delay
                        coroutineScope.launch {
                            delay(5000)
                            loadInterstitialAd()
                        }
                    }

                    override fun onAdShowedFullScreenContent() {
                        Log.d(TAG, "Interstitial ad shown with back button prevention")
                        _interstitialShownCount.value++
                    }
                }
                
                // Show the ad
                ad.show(activity)
                
            } catch (e: Exception) {
                // Something went wrong showing the ad
                Log.e(TAG, "Error showing interstitial ad: ${e.message}", e)
                interstitialAd = null
                onAdFailedToShow()
                
                // Try to reload
                coroutineScope.launch {
                    delay(5000)
                    loadInterstitialAd()
                }
            }
        } else {
            // No ad available
            Log.d(TAG, "No interstitial ad available to show")
            onAdFailedToShow()
            
            // Try to load an ad for next time
            if (!isLoadingInterstitial.get()) {
                loadInterstitialAd()
            }
        }
    }
    
    /**
     * Loads a rewarded ad with adaptive parameters based on network and device conditions
     * @param onAdLoaded Callback when ad is loaded successfully
     */
    fun loadRewardAd(onAdLoaded: () -> Unit = {}) {
        // Skip if user is premium
        if (_isPremiumUser.value) {
            return
        }

        // SCROLL FIX: Block ALL ad loading during scroll to prevent audio queries
        if (shouldBlockAdLoading()) {
            // Silent blocking to reduce log spam during scroll
            return
        }
        
        // Check if circuit breaker allows this component to operate
        if (!circuitBreakerManager.isAllowed(CircuitBreakerManager.AdComponent.REWARDED)) {
            Log.w(TAG, "Rewarded ad loading blocked by circuit breaker")
            return
        }
        
        // Skip if already loading
        if (isLoadingRewarded.getAndSet(true)) {
            return
        }
        
        // Skip if an ad is already loaded
        if (rewardedAd != null) {
            onAdLoaded()
            isLoadingRewarded.set(false)
            return
        }
        
        // OPTIMIZATION: Use adaptive loading parameters based on network and device conditions
        val adStrategy = adaptiveAdLoadingManager.adLoadingStrategy.value
        Log.d(TAG, "Loading rewarded ad with strategy: $adStrategy")
        
        // Check if we're trying to load too frequently - use adaptive cooldown
        val currentTime = SystemClock.elapsedRealtime()
        if (currentTime - lastRewardedLoadAttemptTime < MIN_REWARDED_LOAD_INTERVAL) { // Fixed: Use reduced cooldown for rewarded ads
            Log.d(TAG, "Skipping rewarded ad load - too soon since last attempt")
            isLoadingRewarded.set(false)
            return
        }
        
        lastRewardedLoadAttemptTime = currentTime
        
        // OPTIMIZATION: Use adaptive retry count based on network and device conditions
        val maxRetries = adaptiveAdLoadingManager.getRecommendedRetryCount()
        if (rewardedRetryCount.get() >= maxRetries) {
            Log.d(TAG, "Max retry count reached for rewarded ad: $maxRetries")
            isLoadingRewarded.set(false)
            return
        }
        
        // Move to background thread
        coroutineScope.launch(Dispatchers.IO) {
            // OPTIMIZATION: Set adaptive timeout based on network and device conditions
            val adaptiveTimeout = adaptiveAdLoadingManager.getRecommendedAdTimeout()
            Log.d(TAG, "Using adaptive timeout for rewarded ad: ${adaptiveTimeout}ms")
            
            val timeoutJob = launch {
                delay(adaptiveTimeout)
                if (isLoadingRewarded.get()) {
                    Log.w(TAG, "Rewarded ad load timed out after ${adaptiveTimeout}ms")
                    isLoadingRewarded.set(false)
                }
            }
            
            // ANR FIX: Post to main thread with delay to prevent blocking
            mainHandler.postDelayed({
                try {
                    RewardedAd.load(
                        context,
                        REWARDED_AD_ID,
                        AdRequest.Builder().build(),
                        object : RewardedAdLoadCallback() {
                            override fun onAdLoaded(ad: RewardedAd) {
                                Log.d(TAG, "Rewarded ad loaded successfully")
                                timeoutJob.cancel()
                                rewardedAd = ad
                                isLoadingRewarded.set(false)
                                rewardedRetryCount.set(0) // Reset retry count on success
                                onAdLoaded()
                            }

                            override fun onAdFailedToLoad(error: LoadAdError) {
                                Log.e(TAG, "Rewarded ad failed to load. Error code: ${error.code}, message: ${error.message}")
                                timeoutJob.cancel()
                                rewardedAd = null
                                isLoadingRewarded.set(false)

                                // OPTIMIZATION: Use adaptive retry logic based on network and device conditions
                                val retries = rewardedRetryCount.incrementAndGet()
                                val adaptiveMaxRetries = adaptiveAdLoadingManager.getRecommendedRetryCount()

                                if (retries < adaptiveMaxRetries) {
                                    // Use adaptive retry delay with exponential backoff
                                    val baseRetryDelay = adaptiveAdLoadingManager.getRecommendedRetryDelay()
                                    val backoffDelay = baseRetryDelay * (1 shl (retries - 1))

                                    Log.d(TAG, "Will retry rewarded ad load (attempt $retries of $adaptiveMaxRetries) after ${backoffDelay}ms")

                                    // ANR FIX: Retry without blocking main thread
                                    coroutineScope.launch {
                                        delay(backoffDelay)
                                        loadRewardAd(onAdLoaded)
                                    }
                                }
                            }
                        }
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error loading rewarded ad: ${e.message}")
                    timeoutJob.cancel()
                    isLoadingRewarded.set(false)
                }
            }, 100) // ANR FIX: 100ms delay allows UI to remain responsive
        }
    }
    
    /**
     * Show a rewarded ad
     * @param activity Activity context for showing ad
     * @param onRewarded Callback when user completes watching ad
     * @param onAdDismissed Callback when ad is dismissed
     */
    fun showRewardAd(
        activity: Activity,
        onRewarded: () -> Unit = {},
        onAdDismissed: () -> Unit = {}
    ) {
        // CRASH FIX: Check if activity is valid before showing ad
        if (activity.isDestroyed || activity.isFinishing) {
            Log.w(TAG, "Activity is destroyed/finishing, cannot show reward ad")
            onAdDismissed()
            return
        }
        
        // Skip if user is premium
        if (_isPremiumUser.value) {
            onRewarded()
            onAdDismissed()
            return
        }
        
        // Check if ad is loaded
        val ad = rewardedAd
        if (ad == null) {
            Log.d(TAG, "No rewarded ad available")
            onAdDismissed()
            
            // Try to load for next time
            if (!isLoadingRewarded.get()) {
                loadRewardAd()
            }
            return
        }
        
        try {
            // Set callbacks
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    Log.d(TAG, "Rewarded ad dismissed")
                    rewardedAd = null
                    
                    // CRASH FIX: Check if activity is still valid before proceeding
                    if (activity.isDestroyed || activity.isFinishing) {
                        Log.w(TAG, "Activity is destroyed/finishing, skipping reward ad dismissed callback")
                        return
                    }
                    
                    // CRASH FIX: Safely call callback with try-catch
                    try {
                    onAdDismissed()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error in reward ad dismiss callback: ${e.message}")
                    }
                    
                    // Load next ad
                    coroutineScope.launch {
                        delay(5000)
                        loadRewardAd()
                    }
                }
                
                override fun onAdFailedToShowFullScreenContent(error: AdError) {
                    Log.e(TAG, "Failed to show rewarded ad: ${error.message}")
                    rewardedAd = null
                    
                    // CRASH FIX: Check if activity is still valid before proceeding
                    if (activity.isDestroyed || activity.isFinishing) {
                        Log.w(TAG, "Activity is destroyed/finishing, skipping reward ad failed callback")
                        return
                    }
                    
                    // CRASH FIX: Safely call callback with try-catch
                    try {
                    onAdDismissed()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error in reward ad failed callback: ${e.message}")
                    }
                    
                    // Try to reload
                    coroutineScope.launch {
                        delay(2000)
                        loadRewardAd()
                    }
                }
                
                override fun onAdShowedFullScreenContent() {
                    Log.d(TAG, "Rewarded ad shown")
                }
            }
            
            // Show ad with reward callback
            ad.show(activity) { rewardItem ->
                Log.d(TAG, "User earned reward: ${rewardItem.amount} ${rewardItem.type}")
                onRewarded()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error showing rewarded ad: ${e.message}")
            rewardedAd = null
            onAdDismissed()
            
            // Try to reload
            coroutineScope.launch {
                delay(2000)
                loadRewardAd()
            }
        }
    }

    // CRITICAL FIX: Track preloading job to prevent concurrent execution
    private var preloadingJob: Job? = null

    /**
     * SCROLL PERFORMANCE FIX: Aggressively preload native ads to fill the pool
     * Pauses during scroll to prevent audio queries blocking main thread
     * Professional approach used by Instagram/TikTok/Pinterest
     * CRITICAL: Only ONE preloading coroutine can run at a time
     */
    fun aggressivelyPreloadNativeAds() {
        if (_isPremiumUser.value) return

        // CRITICAL FIX: Cancel any existing preloading to prevent concurrent execution
        preloadingJob?.cancel()

        preloadingJob = coroutineScope.launch {
            // Wait 5 seconds after HomeTabScreen loads to avoid blocking startup
            // This prevents WebView init and ad loading from causing frame drops
            delay(5000) // 5s delay ensures UI is fully stable before heavy ad operations

            Log.d(TAG, "Starting scroll-aware native ad preloading to fill pool (target: $maxNativeAdPoolSize ads)")

            // Load ads one by one with delays, pausing during scroll
            repeat(maxNativeAdPoolSize) { index ->
                // PERFORMANCE FIX: Wait for scroll to stop with reduced log spam
                var scrollCheckCount = 0
                while (_isGlobalScrollActive.value) {
                    // Only log every 10 checks (every 2 seconds) to reduce log overhead
                    if (scrollCheckCount % 10 == 0) {
                        Log.d(TAG, "Pausing ad preloading - user is scrolling")
                    }
                    scrollCheckCount++
                    delay(200) // Check every 200ms if scroll has stopped
                }

                if (nativeAdPool.size < maxNativeAdPoolSize) {
                    Log.d(TAG, "Loading ad ${index + 1}/$maxNativeAdPoolSize (pool size: ${nativeAdPool.size})")
                    loadNativeAd(bypassScrollCheck = true) // Bypass cooldown - we have our own scroll detection

                    // ULTRA-FAST initial loading: First 3 ads load with minimal delay
                    // Then slower for remaining ads to respect AdMob rate limits
                    val adDelay = if (index < 3) 200 else 600
                    delay(adDelay.toLong())
                } else {
                    // Pool is full, stop preloading
                    Log.d(TAG, "Native ad pool filled successfully with ${nativeAdPool.size} ads")
                    return@launch
                }
            }
        }
    }

    /**
     * Load a native ad for grid display
     * Follows AdMob guidelines for native ad implementation
     * @param bypassScrollCheck Set to true during aggressive preload to skip scroll cooldown check
     */
    fun loadNativeAd(onAdLoaded: (NativeAd) -> Unit = {}, bypassScrollCheck: Boolean = false) {
        // Skip if user is premium
        if (_isPremiumUser.value) {
            return
        }

        // SCROLL FIX: Block ALL ad loading during scroll to prevent audio queries
        // BUT: Skip this check during aggressive preload which has its own scroll detection
        if (!bypassScrollCheck && shouldBlockAdLoading()) {
            // Silent blocking to reduce log spam during scroll
            return
        }

        // Skip if we have enough ads in pool
        if (nativeAdPool.size >= maxNativeAdPoolSize) {
            if (nativeAdPool.isNotEmpty()) {
                onAdLoaded(nativeAdPool.first())
            }
            return
        }

        // Skip if already loading
        if (isLoadingNativeAd.getAndSet(true)) {
            return
        }

        // Check load frequency
        val currentTime = SystemClock.elapsedRealtime()
        if (currentTime - lastNativeAdLoadTime < MIN_NATIVE_AD_LOAD_INTERVAL) {
            isLoadingNativeAd.set(false)
            return
        }

        lastNativeAdLoadTime = currentTime

        // Check retry count
        if (nativeAdRetryCount.get() >= MAX_RETRIES) {
            isLoadingNativeAd.set(false)
            return
        }

        // Removed debug log to improve performance

        // ANR FIX: Post to main thread with delay to prevent blocking
        mainHandler.postDelayed({
            try {
                // OPTIMIZATION: Use adaptive loading parameters based on network and device conditions
                val adStrategy = adaptiveAdLoadingManager.adLoadingStrategy.value

                // OPTIMIZATION: Use adaptive retry count based on network and device conditions
                val maxRetries = adaptiveAdLoadingManager.getRecommendedRetryCount()

                // OPTIMIZATION: Set adaptive timeout based on network and device conditions
                val adaptiveTimeout = adaptiveAdLoadingManager.getRecommendedAdTimeout()

                // SCROLL PERFORMANCE FIX: Completely disable media content and audio queries
                // This prevents audio system calls that cause scroll stuttering
                val videoOptions = com.google.android.gms.ads.VideoOptions.Builder()
                    .setStartMuted(true) // Mute all videos
                    .setCustomControlsRequested(false) // No custom controls
                    .setClickToExpandRequested(false) // No video expansion
                    .build()

                val nativeAdOptions = NativeAdOptions.Builder()
                    .setVideoOptions(videoOptions)
                    .setRequestMultipleImages(true) // Prefer multiple static images over video
                    .setReturnUrlsForImageAssets(false) // Use drawable assets for better performance
                    .setMediaAspectRatio(com.google.android.gms.ads.nativead.NativeAdOptions.NATIVE_MEDIA_ASPECT_RATIO_LANDSCAPE) // Prefer landscape images (no video)
                    .setAdChoicesPlacement(com.google.android.gms.ads.nativead.NativeAdOptions.ADCHOICES_TOP_LEFT) // Standard placement
                    .build()

                // CRITICAL: Create AdRequest that explicitly disables audio/media
                val adRequest = AdRequest.Builder()
                    .build()

                val adLoader = AdLoader.Builder(context, NATIVE_AD_ID)
                        .withNativeAdOptions(nativeAdOptions) // Apply options FIRST before forNativeAd
                        .forNativeAd { nativeAd ->
                            // Native ad loaded successfully
                            isLoadingNativeAd.set(false)
                            nativeAdRetryCount.set(0)

                            // Add to pool
                            synchronized(nativeAdPool) {
                                if (nativeAdPool.size < maxNativeAdPoolSize) {
                                    nativeAdPool.add(nativeAd)
                                    // Native ad added to pool
                                } else {
                                    // Pool is full, destroy the oldest ad and add new one
                                    val oldAd = nativeAdPool.removeAt(0)
                                    oldAd.destroy()
                                    nativeAdPool.add(nativeAd)
                                    // Native ad pool full, replaced oldest ad
                                }
                            }

                            onAdLoaded(nativeAd)

                            // Preload another ad if pool is not full
                            if (nativeAdPool.size < maxNativeAdPoolSize) {
                                coroutineScope.launch {
                                    delay(2000) // Wait before loading next ad
                                    loadNativeAd()
                                }
                            }
                        }
                        .withAdListener(object : com.google.android.gms.ads.AdListener() {
                            override fun onAdFailedToLoad(error: LoadAdError) {
                                // Native ad failed to load
                                isLoadingNativeAd.set(false)

                                val retries = nativeAdRetryCount.incrementAndGet()
                                if (retries < MAX_RETRIES) {
                                    val retryDelay = BASE_RETRY_DELAY_MS * (1 shl (retries - 1))
                                    // Will retry native ad load

                                    coroutineScope.launch {
                                        delay(retryDelay)
                                        loadNativeAd(onAdLoaded)
                                    }
                                }
                            }
                        })
                        .build()

                adLoader.loadAd(AdRequest.Builder().build())

            } catch (e: Exception) {
                Log.e(TAG, "Exception during native ad loading: ${e.message}", e)
                isLoadingNativeAd.set(false)
            }
        }, 100) // ANR FIX: 100ms delay allows UI to remain responsive
    }

    /**
     * SCROLL PERFORMANCE FIX: Get pre-cached native ad without any blocking operations
     * Only returns ads that are already loaded - no synchronous loading during scroll
     */
    fun getPreCachedNativeAd(adIndex: Int): NativeAd? {
        if (_isPremiumUser.value) return null
        // Only return pre-cached ads - no loading during scroll
        return nativeAdPositionCache[adIndex]
    }

    /**
     * SCROLL PERFORMANCE FIX: Get a cached native ad for specific position
     * NEVER loads ads on-demand to prevent scroll stuttering
     * Uses preloaded ad pool only - professional-grade approach like Instagram/TikTok
     */
    fun getCachedNativeAd(adIndex: Int): NativeAd? {
        if (_isPremiumUser.value) return null

        // Check if we already have a cached ad for this position
        synchronized(nativeAdPositionCache) {
            val cachedAd = nativeAdPositionCache[adIndex]
            if (cachedAd != null) {
                return cachedAd
            }
        }

        // No cached ad for this position, get one from pool
        synchronized(nativeAdPool) {
            if (nativeAdPool.isNotEmpty()) {
                val ad = nativeAdPool.removeAt(0)

                // Cache it for this position
                synchronized(nativeAdPositionCache) {
                    // Remove oldest cached position if we're at max
                    if (nativeAdPositionCache.size >= maxCachedPositions) {
                        val oldestKey = nativeAdPositionCache.keys.firstOrNull()
                        if (oldestKey != null) {
                            val oldAd = nativeAdPositionCache.remove(oldestKey)
                            oldAd?.destroy()
                        }
                    }
                    nativeAdPositionCache[adIndex] = ad
                }

                // CRITICAL: Trigger aggressive refill when pool gets low
                if (nativeAdPool.size < 5) { // Restart preloading when pool drops below 5 ads
                    if (BuildConfig.DEBUG) {
                        Log.d(TAG, "Pool running low (${nativeAdPool.size} ads), restarting aggressive preload")
                    }
                    coroutineScope.launch {
                        delay(100) // Minimal delay to avoid blocking UI
                        // Restart the aggressive preloading to refill pool quickly
                        aggressivelyPreloadNativeAds()
                    }
                }

                return ad
            }
        }

        // SCROLL PERFORMANCE FIX: NEVER load ads on-demand during scroll
        // Only return null - ads will be loaded by aggressive preloading
        // This eliminates 100% of scroll stutters from WebView initialization
        return null
    }

    /**
     * Get a native ad from the pool (legacy method for backward compatibility)
     * Returns null if no ads available
     */
    fun getNativeAd(): NativeAd? {
        if (_isPremiumUser.value) return null

        synchronized(nativeAdPool) {
            if (nativeAdPool.isNotEmpty()) {
                val ad = nativeAdPool.removeAt(0)

                // Trigger loading of replacement ad
                if (nativeAdPool.size < maxNativeAdPoolSize) {
                    coroutineScope.launch {
                        delay(1000) // Small delay before loading replacement
                        loadNativeAd()
                    }
                }

                return ad
            }
        }

        // No ad available, trigger loading
        if (!isLoadingNativeAd.get()) {
            loadNativeAd()
        }
        return null
    }

    /**
     * SCROLL PERFORMANCE FIX: Pause/destroy native ad video decoders when navigating away
     * from grid screens. This frees ExoPlayer/MediaCodec/AudioTrack resources that cause
     * frame drops during scroll and detail screen usage.
     */
    fun pauseNativeAdVideos() {
        // Pause videos in position-cached ads
        synchronized(nativeAdPositionCache) {
            nativeAdPositionCache.values.forEach { ad ->
                try {
                    ad.mediaContent?.let { media ->
                        if (media.hasVideoContent()) {
                            media.videoController?.pause()
                        }
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "Error pausing native ad video: ${e.message}")
                }
            }
        }
        // Pause videos in pool ads
        synchronized(nativeAdPool) {
            nativeAdPool.forEach { ad ->
                try {
                    ad.mediaContent?.let { media ->
                        if (media.hasVideoContent()) {
                            media.videoController?.pause()
                        }
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "Error pausing native ad video: ${e.message}")
                }
            }
        }
        Log.d(TAG, "Paused all native ad video decoders")
    }

    /**
     * Check if native ads are available
     */
    fun hasNativeAd(): Boolean {
        return !_isPremiumUser.value && nativeAdPool.isNotEmpty()
    }

    /**
     * Get native ad pool size for debugging
     */
    fun getNativeAdPoolSize(): Int {
        return nativeAdPool.size
    }


}