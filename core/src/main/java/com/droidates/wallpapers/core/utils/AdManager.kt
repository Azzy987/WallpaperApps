package com.droidates.wallpapers.core.utils

import com.droidates.wallpapers.core.config.AppConfig
import android.app.Activity
import android.content.Context
import android.util.Log
import android.os.Handler
import android.os.Looper
import com.droidates.wallpapers.core.data.repository.AuthRepository
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback
import com.google.android.libraries.ads.mobile.sdk.common.AdRequest
import com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAd
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAdEventCallback
import com.google.android.libraries.ads.mobile.sdk.rewarded.OnUserEarnedRewardListener
import com.google.android.libraries.ads.mobile.sdk.rewarded.RewardItem
import com.google.android.libraries.ads.mobile.sdk.rewarded.RewardedAd
import com.google.android.libraries.ads.mobile.sdk.rewarded.RewardedAdEventCallback
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

        /** Default length of the ad-free window earned by watching a rewarded ad. */
        const val AD_FREE_WINDOW_MS = 30 * 60 * 1000L // 30 minutes

        /**
         * How long showInterstitialThen waits for an ad callback before running the
         * user's action regardless. Covers the activity-destroyed guards inside
         * showInterstitialAd, which return without invoking any callback. Generous
         * enough that a normally-dismissed ad always wins the race.
         */
        private const val AD_CALLBACK_TIMEOUT_MS = 12000L

        private const val KEY_AD_FREE_UNTIL = "ad_free_until"
        
        // Use production ads only - IDs defined in AppConfig
        // Resolved from the installed AppSpec at call time, so no longer `const`.
        val INTERSTITIAL_AD_ID: String get() = AppConfig.AD_INTERSTITIAL_ID
        val REWARDED_AD_ID: String get() = AppConfig.AD_REWARDED_ID
        
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

        /**
         * A frequency-cap NO_FILL means AdMob has decided this user has already
         * seen enough ads — the answer will not change within the retry window,
         * so the exponential backoff ladder is pure wasted work (and gets
         * blocked by our own cooldown anyway). Detected via the error
         * description because the SDK reports it as a generic NO_FILL.
         */
        fun isFrequencyCapError(error: LoadAdError): Boolean =
            error.code == LoadAdError.ErrorCode.NO_FILL &&
                error.message.contains("frequency cap", ignoreCase = true)
    }

    // OPTIMIZATION: Add mutex for thread safety
    private val adLoadMutex = Mutex()

    // ANR FIX: Handler for posting to main thread without blocking
    private val mainHandler = Handler(Looper.getMainLooper())

    // Track initialization status
    private val isInitialized = AtomicBoolean(false)
    private val _initializationComplete = MutableStateFlow(false)

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
    
    // ── Per-session wallpaper action gate ──────────────────────────────────
    // After this many downloads/applies in one session the user is offered premium
    // or a rewarded ad. Deliberately per-SESSION and not lifetime: a lifetime cap
    // turns into a hard paywall on day three for exactly the people who use the app
    // most, while a session cap only ever interrupts a single long browsing run.
    //
    // Three, not two: trying a couple of wallpapers back to back is ordinary
    // browsing in a wallpaper app, and interrupting that before the user has felt
    // any value is what makes people uninstall rather than pay.
    private val FREE_ACTIONS_PER_SESSION = 3

    // Not persisted — process death IS the session boundary, so a plain counter in
    // memory is the whole implementation.
    private var sessionActionCount = 0

    /** True when the user still has free wallpaper actions left this session. */
    fun hasFreeActionsLeft(): Boolean =
        _isPremiumUser.value || isAdFreeActive() || sessionActionCount < FREE_ACTIONS_PER_SESSION

    /**
     * Record a completed download/apply. Premium users and users inside an earned
     * ad-free window are never counted, so their state cannot drift toward the gate.
     */
    fun recordWallpaperAction() {
        if (_isPremiumUser.value || isAdFreeActive()) return
        sessionActionCount++
        Log.d(TAG, "Wallpaper action $sessionActionCount/$FREE_ACTIONS_PER_SESSION this session")
    }

    /** Actions used so far this session, for UI copy on the gate dialog. */
    fun sessionActionsUsed(): Int = sessionActionCount

    /**
     * Clear the gate after the user watches a rewarded ad. The ad-free window
     * granted alongside this is what makes the reward feel worth watching — see
     * grantAdFreeWindow.
     */
    fun resetSessionActions() {
        sessionActionCount = 0
        Log.d(TAG, "Session action count reset")
    }

    // Callback for when interstitial ad is dismissed
    private var interstitialAdDismissCallback: (() -> Unit)? = null
    private var lastInterstitialCallbackUptimeMs: Long = 0L

    // ── Ad-free window (earned by watching a rewarded ad) ───────────────────
    // Persisted, because a window the user paid attention for must survive the app
    // being backgrounded or killed — losing it is exactly the broken trade that
    // makes people stop watching rewarded ads at all.
    private val adFreePrefs by lazy {
        // init{} is intentionally free of startup work, so the stored window is read
        // here on first access rather than during construction.
        context.getSharedPreferences("ad_manager_prefs", Context.MODE_PRIVATE).also { prefs ->
            _adFreeUntil.value = prefs.getLong(KEY_AD_FREE_UNTIL, 0L)
        }
    }
    private val _adFreeUntil = MutableStateFlow(0L)
    /** Epoch millis until which interstitials are suppressed; 0 when inactive. */
    val adFreeUntil: StateFlow<Long> = _adFreeUntil

    /** True while an earned ad-free window is still running. */
    fun isAdFreeActive(): Boolean {
        adFreePrefs // force the lazy restore before reading the flow
        return System.currentTimeMillis() < _adFreeUntil.value
    }

    /** Minutes left in the current window, 0 when none is active. */
    fun adFreeMinutesRemaining(): Int {
        val remaining = _adFreeUntil.value - System.currentTimeMillis()
        return if (remaining > 0) ((remaining + 59_999) / 60_000).toInt() else 0
    }

    /**
     * Start (or extend) the ad-free window after a rewarded ad completes.
     * Extending rather than overwriting means a user who watches again near the end
     * of a window is not silently robbed of the time they just earned.
     */
    fun grantAdFreeWindow(durationMillis: Long = AD_FREE_WINDOW_MS) {
        val base = maxOf(System.currentTimeMillis(), _adFreeUntil.value)
        val until = base + durationMillis
        _adFreeUntil.value = until
        adFreePrefs.edit().putLong(KEY_AD_FREE_UNTIL, until).apply()
        Log.d(TAG, "Ad-free window granted until $until (${adFreeMinutesRemaining()} min)")
    }

    // Cache the frequency value to avoid excessive logging
    private var lastFrequencyValue: Int = -1

    // Flag to reduce excessive logging
    private var shouldLogPremiumStatus = true
    
    // OPTIMIZATION: Track if MobileAds have been initialized at the Application level
    private var mobileAdsInitializedOutside = false

    private fun runInterstitialCompletionSafely(callback: () -> Unit) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastInterstitialCallbackUptimeMs < 250L) {
            return
        }
        lastInterstitialCallbackUptimeMs = now
        mainHandler.postDelayed({
            runCatching(callback).onFailure { error ->
                Log.e(TAG, "Error executing interstitial completion callback: ${error.message}")
            }
        }, 120L)
    }




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
        MobileAdsInitializer.markInitialized()
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
        if (isInitialized.get() || MobileAdsInitializer.isInitialized()) {
            if (!isInitialized.get()) {
                isInitialized.set(true)
                _initializationComplete.value = true
            }
            Log.d(TAG, "MobileAds already initialized")
            return
        }

        coroutineScope.launch {
            try {
                launch { initializePremiumStatus() }
                startCacheInvalidationChecker()
                adaptiveAdLoadingManager.startMonitoring()
                Log.d(TAG, "Started adaptive ad loading monitoring")

                MobileAdsInitializer.ensureInitialized(context)

                isInitialized.set(true)
                _initializationComplete.value = true

                delay(5000)
                // Consent gate: in the EEA/UK a request sent before the user has made a
                // choice is a compliance problem, and an unconsented request is worth
                // little anyway. Outside those regions canRequestAds() is true from the
                // start, so this changes nothing for most traffic.
                if (!_isPremiumUser.value && ConsentManager.canRequestAds(context)) {
                    preloadAds()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during AdManager initialization", e)
            }
        }
    }
    
    private fun initializePremiumStatus() {
        coroutineScope.launch {
            authRepository.isPremiumUser.collect { isPremium ->
                val oldValue = _isPremiumUser.value
                _isPremiumUser.value = isPremium
                
                // Only log when status changes
                if (oldValue != isPremium && AppConfig.IS_DEBUG) {
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
                    if (AppConfig.IS_DEBUG) {
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
        isLoadingInterstitial.set(false)
        isLoadingRewarded.set(false)
        interstitialRetryCount.set(0)
        rewardedRetryCount.set(0)
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
        // One interstitial per 8 detail views. Browsing a wallpaper app means opening
        // many wallpapers per session, so every-5 fired several times in a single sitting
        // — the interstitial eCPM ($2.04) is a fraction of rewarded ($5.93), so trading
        // some of that volume for retention and opt-in rewarded views is the better deal.
        val frequency = 8
        
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
        
        // An earned ad-free window suppresses interstitials only. Banners stay (they
        // cost the user nothing) and rewarded stays available (it is opt-in, and is how
        // the window was earned in the first place).
        if (isAdFreeActive()) {
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
     * @param bypassThrottle set for the refill immediately after an ad was shown. The
     * MIN_LOAD_INTERVAL throttle exists to stop retry storms on FAILED loads; a
     * successful show that consumed the cached ad is not that case, and honouring the
     * throttle there leaves the next wallpaper action with no ad to show.
     */
    fun loadInterstitialAd(onAdLoaded: () -> Unit = {}, bypassThrottle: Boolean = false) {
        // Run checks on current thread before moving to background
        // Skip if user is premium
        if (_isPremiumUser.value) {
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
        if (!bypassThrottle && currentTime - lastInterstitialLoadAttemptTime < MIN_LOAD_INTERVAL) {
            Log.d(TAG, "Skipping interstitial load - too soon since last attempt")
            isLoadingInterstitial.set(false)
            return
        }
        
        lastInterstitialLoadAttemptTime = currentTime
        
        // OPTIMIZATION: Use adaptive retry count based on network and device conditions
        val maxRetries = adaptiveAdLoadingManager.getRecommendedRetryCount()
        if (interstitialRetryCount.get() >= maxRetries) {
            if (bypassThrottle) {
                // Refill after a successful show. The retry budget tracks CONSECUTIVE
                // load failures, and an ad that just displayed proves ads are working —
                // so the old count is stale. Without this reset a user who hits three
                // failed loads early (a tunnel, a dropped connection) gets NO further
                // interstitials for the entire session, which silently kills the
                // revenue this ad-first flow is built around.
                Log.d(TAG, "Resetting stale retry count after a successful ad show")
                interstitialRetryCount.set(0)
            } else {
                Log.d(TAG, "Max retry count reached for interstitial ad: $maxRetries")
                isLoadingInterstitial.set(false)
                return
            }
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
                    // The Next-Gen SDK throws if an ad is requested before initialize()
                    // completes; SDK init is deferred to a background worker, so this can
                    // genuinely lose the race on a cold start.
                    if (!MobileAdsInitializer.isInitialized()) {
                        Log.d(TAG, "Ads SDK not initialized yet; deferring interstitial load")
                        adLoadTimeoutJob?.cancel()
                        isLoadingInterstitial.set(false)
                        coroutineScope.launch {
                            MobileAdsInitializer.ensureInitialized(context)
                            loadInterstitialAd(onAdLoaded)
                        }
                        return@postDelayed
                    }

                    Log.d(TAG, "Creating ad request and starting load process")

                    // Next-Gen: the ad unit id is part of the request, and load() takes no
                    // Context — the SDK uses the one supplied at initialization.
                    val adRequest = AdRequest.Builder(INTERSTITIAL_AD_ID)
                        .build()

                    InterstitialAd.load(
                        adRequest,
                        object : AdLoadCallback<InterstitialAd> {
                            override fun onAdLoaded(ad: InterstitialAd) {
                                Log.d(TAG, "Interstitial ad loaded successfully!")
                                adLoadTimeoutJob?.cancel()
                                interstitialAd = ad
                                interstitialAdLoadTime = System.currentTimeMillis() // Record load time for cache invalidation
                                isLoadingInterstitial.set(false)
                                interstitialRetryCount.set(0) // Reset retry count on success
                                
                                // Record success with circuit breaker
                                circuitBreakerManager.recordSuccess(CircuitBreakerManager.AdComponent.INTERSTITIAL, 0L)

                                // The Next-Gen SDK invokes this on a background dispatcher
                                // (the old InterstitialAdLoadCallback was main-thread), and
                                // callers use this to drive navigation/UI — so hop back.
                                mainHandler.post { onAdLoaded() }
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
                                
                                // A frequency cap will not lift within the retry window, so
                                // retrying only burns cycles and log noise. Wait for the next
                                // natural request instead.
                                if (isFrequencyCapError(error)) {
                                    Log.d(TAG, "Interstitial frequency cap reached - skipping retries until next request")
                                    interstitialRetryCount.set(0)
                                    return
                                }

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
     * Whether a rewarded ad is ready to show right now.
     *
     * showRewardAd() falls through to onAdDismissed() when nothing is loaded, so any
     * UI that *offers* a rewarded ad must check this first — otherwise the user accepts
     * the offer, sees nothing happen, and the offer is spent.
     */
    fun isRewardedAdLoaded(): Boolean {
        return rewardedAd != null
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
     * Check if an ad should be shown for the detail screen
     * Uses RAM-only counter that resets when app is closed
     */
    fun shouldShowAdForDetailScreen(): Boolean {
        // Disable interstitials if premium user
        if (_isPremiumUser.value) {
            return false
        }
        
        // An earned ad-free window suppresses interstitials only. Banners stay (they
        // cost the user nothing) and rewarded stays available (it is opt-in, and is how
        // the window was earned in the first place).
        if (isAdFreeActive()) {
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
    /**
     * Show an interstitial FIRST, then run [action] once it is dismissed.
     *
     * Ads used to fire after the download/apply had already happened. That ordering
     * loses work: onAdDismissedFullScreenContent bails out early when the activity is
     * gone, so an ad shown while returning from the system wallpaper picker could
     * skip the completion callback entirely. Showing the ad while the app is still
     * foregrounded and stable, then starting the work on dismissal, removes that
     * whole class of failure.
     *
     * The action is guaranteed to run EXACTLY once on every path — ad dismissed, ad
     * failed to show, no ad loaded, or an exception. A failed ad must never cost the
     * user their download. [runOnceGuard] enforces the "exactly" half, because
     * showInterstitialAd can reach both a failure callback and an exception handler
     * for a single call.
     */
    fun showInterstitialThen(activity: Activity, action: () -> Unit) {
        // Premium and earned ad-free windows skip straight to the work.
        if (_isPremiumUser.value || isAdFreeActive()) {
            action()
            return
        }

        val hasRun = AtomicBoolean(false)
        // Deliberately NOT routed through runInterstitialCompletionSafely: that helper
        // debounces calls within 250ms, and the dismissal path already spends that
        // budget invoking this guard — a second hop would be swallowed and the user's
        // download would never start. hasRun already guarantees single execution, so
        // the debounce buys nothing here. Posting to the main looper still lets the ad
        // finish tearing down before the action touches the UI.
        val runOnceGuard = {
            if (hasRun.compareAndSet(false, true)) {
                mainHandler.postDelayed({
                    runCatching(action).onFailure { error ->
                        Log.e(TAG, "Error running post-interstitial action: ${error.message}", error)
                    }
                }, 120L)
            }
        }

        if (!isInterstitialAdLoaded()) {
            // Nothing to show: do the work now and warm an ad for next time rather
            // than making the user wait on a network round trip.
            loadInterstitialAd()
            runOnceGuard()
            return
        }

        showInterstitialAd(
            activity = activity,
            onAdDismissed = runOnceGuard,
            onAdFailedToShow = runOnceGuard
        )

        // Safety net: the two activity-destroyed guards inside showInterstitialAd
        // return without invoking either callback. Without this the user's action
        // would be dropped silently in exactly the case ad-first was meant to fix.
        coroutineScope.launch {
            delay(AD_CALLBACK_TIMEOUT_MS)
            if (!hasRun.get()) {
                if (activity.isDestroyed || activity.isFinishing) {
                    // The screen the action belonged to is gone. Running the download
                    // now would write against a dead context, so drop it and let the
                    // user retry on a live screen.
                    Log.w(TAG, "Interstitial callback missing and activity gone; dropping action")
                    hasRun.set(true)
                } else {
                    Log.w(TAG, "Interstitial callback never arrived; running action anyway")
                    withContext(Dispatchers.Main) { runOnceGuard() }
                }
            }
        }
    }

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

                ad.adEventCallback = object : InterstitialAdEventCallback {
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
                        interstitialAdDismissCallback?.let { callback ->
                            runInterstitialCompletionSafely(callback)
                        }
                        
                        // Reload promptly: every wallpaper action (download, then
                        // apply) is expected to show its own ad, so the next one must
                        // already be in flight by the time the user taps again. The old
                        // 5s wait meant an apply straight after a download found nothing
                        // loaded and silently skipped its ad.
                        coroutineScope.launch {
                            delay(500)
                            loadInterstitialAd(bypassThrottle = true)
                        }
                    }
                    
                    override fun onAdFailedToShowFullScreenContent(error: FullScreenContentError) {
                        Log.e(TAG, "Failed to show interstitial ad: ${error.message}")
                        interstitialAd = null
                        
                        // CRASH FIX: Check if activity is still valid before proceeding
                        if (activity.isDestroyed || activity.isFinishing) {
                            Log.w(TAG, "Activity is destroyed/finishing, skipping ad failed callback")
                            return
                        }
                        
                        // CRASH FIX: Safely call callback with try-catch
                        runInterstitialCompletionSafely(onAdFailedToShow)
                        
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
     * @param onAdFailed Callback when no rewarded ad can be loaded for this request
     */
    fun loadRewardAd(
        onAdLoaded: () -> Unit = {},
        onAdFailed: () -> Unit = {}
    ) {
        val callbackDelivered = AtomicBoolean(false)
        fun deliverLoaded() {
            if (callbackDelivered.compareAndSet(false, true)) {
                mainHandler.post { onAdLoaded() }
            }
        }
        fun deliverFailed() {
            if (callbackDelivered.compareAndSet(false, true)) {
                mainHandler.post { onAdFailed() }
            }
        }

        // Skip if user is premium
        if (_isPremiumUser.value) {
            return
        }

        // Check if circuit breaker allows this component to operate
        if (!circuitBreakerManager.isAllowed(CircuitBreakerManager.AdComponent.REWARDED)) {
            Log.w(TAG, "Rewarded ad loading blocked by circuit breaker")
            deliverFailed()
            return
        }
        
        // Skip if already loading
        if (isLoadingRewarded.getAndSet(true)) {
            coroutineScope.launch {
                val startedAt = SystemClock.elapsedRealtime()
                while (
                    rewardedAd == null &&
                    isLoadingRewarded.get() &&
                    SystemClock.elapsedRealtime() - startedAt < AD_LOAD_TIMEOUT
                ) {
                    delay(250)
                }
                if (rewardedAd != null) {
                    deliverLoaded()
                } else {
                    deliverFailed()
                }
            }
            return
        }
        
        // Skip if an ad is already loaded
        if (rewardedAd != null) {
            deliverLoaded()
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
            deliverFailed()
            return
        }
        
        lastRewardedLoadAttemptTime = currentTime
        
        // OPTIMIZATION: Use adaptive retry count based on network and device conditions
        val maxRetries = adaptiveAdLoadingManager.getRecommendedRetryCount()
        if (rewardedRetryCount.get() >= maxRetries) {
            Log.d(TAG, "Max retry count reached for rewarded ad: $maxRetries")
            rewardedRetryCount.set(0)
            isLoadingRewarded.set(false)
            deliverFailed()
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
                    deliverFailed()
                }
            }
            
            // ANR FIX: Post to main thread with delay to prevent blocking
            mainHandler.postDelayed({
                try {
                    // Same init guard as the interstitial: loading before the Next-Gen SDK
                    // is initialized throws rather than failing softly.
                    if (!MobileAdsInitializer.isInitialized()) {
                        Log.d(TAG, "Ads SDK not initialized yet; deferring rewarded load")
                        timeoutJob.cancel()
                        isLoadingRewarded.set(false)
                        coroutineScope.launch {
                            MobileAdsInitializer.ensureInitialized(context)
                            loadRewardAd(onAdLoaded, onAdFailed)
                        }
                        return@postDelayed
                    }

                    RewardedAd.load(
                        AdRequest.Builder(REWARDED_AD_ID).build(),
                        object : AdLoadCallback<RewardedAd> {
                            override fun onAdLoaded(ad: RewardedAd) {
                                Log.d(TAG, "Rewarded ad loaded successfully")
                                timeoutJob.cancel()
                                rewardedAd = ad
                                isLoadingRewarded.set(false)
                                rewardedRetryCount.set(0) // Reset retry count on success
                                deliverLoaded()
                            }

                            override fun onAdFailedToLoad(error: LoadAdError) {
                                Log.e(TAG, "Rewarded ad failed to load. Error code: ${error.code}, message: ${error.message}")
                                timeoutJob.cancel()
                                rewardedAd = null
                                isLoadingRewarded.set(false)

                                // A frequency cap will not lift within the retry window. Report
                                // the failure immediately so the caller's UI stops waiting.
                                if (isFrequencyCapError(error)) {
                                    Log.d(TAG, "Rewarded frequency cap reached - skipping retries until next request")
                                    rewardedRetryCount.set(0)
                                    deliverFailed()
                                    return
                                }

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
                                        loadRewardAd(onAdLoaded, onAdFailed)
                                    }
                                } else {
                                    rewardedRetryCount.set(0)
                                    deliverFailed()
                                }
                            }
                        }
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error loading rewarded ad: ${e.message}")
                    timeoutJob.cancel()
                    isLoadingRewarded.set(false)
                    deliverFailed()
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
            ad.adEventCallback = object : RewardedAdEventCallback {
                override fun onAdDismissedFullScreenContent() {
                    Log.d(TAG, "Rewarded ad dismissed")
                    rewardedAd = null
                    
                    // CRASH FIX: Check if activity is still valid before proceeding
                    if (activity.isDestroyed || activity.isFinishing) {
                        Log.w(TAG, "Activity is destroyed/finishing, skipping reward ad dismissed callback")
                        return
                    }
                    
                    // Next-Gen SDK callbacks are not guaranteed to be on the main thread,
                    // and this callback navigates / updates Compose state.
                    mainHandler.post {
                        runCatching { onAdDismissed() }.onFailure { e ->
                            Log.e(TAG, "Error in reward ad dismiss callback: ${e.message}")
                        }
                    }

                    // Load next ad
                    coroutineScope.launch {
                        delay(5000)
                        loadRewardAd()
                    }
                }
                
                override fun onAdFailedToShowFullScreenContent(error: FullScreenContentError) {
                    Log.e(TAG, "Failed to show rewarded ad: ${error.message}")
                    rewardedAd = null
                    
                    // CRASH FIX: Check if activity is still valid before proceeding
                    if (activity.isDestroyed || activity.isFinishing) {
                        Log.w(TAG, "Activity is destroyed/finishing, skipping reward ad failed callback")
                        return
                    }
                    
                    mainHandler.post {
                        runCatching { onAdDismissed() }.onFailure { e ->
                            Log.e(TAG, "Error in reward ad failed callback: ${e.message}")
                        }
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
                // Grants the HD download, which touches Compose state — force main thread.
                mainHandler.post { onRewarded() }
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

}
