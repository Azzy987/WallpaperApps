package com.droidates.wallpapers.utils

import android.content.Context
import android.os.Process
import android.util.Log
import com.droidates.wallpapers.BuildConfig
import com.droidates.wallpapers.data.repository.AuthRepository
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import javax.inject.Inject
import javax.inject.Singleton
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import coil.ImageLoader
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG = "AppInitializer"
private const val VERBOSE_LOGGING = false // DISABLE EXCESSIVE LOGGING - only keep critical startup logs

/**
 * Module to provide AppInitializer dependency
 */
@Module
@InstallIn(SingletonComponent::class)
object AppInitializerModule {
    
    @Provides
    @Singleton
    fun provideAppInitializer(
        @ApplicationContext context: Context,
        adManager: AdManager,
        authRepository: AuthRepository,
        preferencesManager: PreferencesManager
    ): AppInitializer {
        return AppInitializer(context, adManager, authRepository, preferencesManager)
    }
}

/**
 * Handles app initialization tasks using background coroutines
 * to move heavy operations off the UI thread for better launch performance
 */
@Singleton
class AppInitializer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val adManager: AdManager,
    private val authRepository: AuthRepository,
    private val preferencesManager: PreferencesManager
) {
    // Application scope for background tasks with robust error handling
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO + 
        CoroutineExceptionHandler { _, throwable ->
            if (throwable !is CancellationException) {
                Log.e(TAG, "Error in AppInitializer scope: ${throwable.message}", throwable)
            }
        }
    )
    
    // Track initialization state
    private val isInitialized = AtomicBoolean(false)
    
    // Cache of weak references to clearable objects
    private val memoryCaches = ConcurrentHashMap<String, WeakReference<Any>>()
    
    /**
     * Initialize components in the background using coroutines
     * Optimized to reduce impact on app startup time and UI responsiveness
     */
    fun initializeComponents() {
        if (isInitialized.getAndSet(true)) {
            // Already initialized, avoid duplicate work
            if (VERBOSE_LOGGING) {
            Log.d(TAG, "Components already initialized, skipping")
            }
            return
        }
        
        // Register PreferencesManager as a clearable cache
        registerMemoryCache("preferencesManager", preferencesManager)
        
        if (VERBOSE_LOGGING) {
        Log.d(TAG, "Starting background initialization with priority-based approach")
        }
        
        appScope.launch {
            try {
                // Initialize components in parallel with different priorities and dispatchers
                // This maximizes CPU utilization while minimizing main thread impact
                
                // High priority initialization - Firebase (needed for other services)
                val firebaseJob = launch(Dispatchers.IO) {
                    initializeFirebase()
                }
                
                // Wait for Firebase to complete before proceeding
                firebaseJob.join()
                
                // Medium priority tasks - run in parallel
                val fcmJob = launch(Dispatchers.IO) {
                    initializeFcm()
                }
                
                val adsJob = launch(Dispatchers.Default) {
                    // Only preload ads for non-premium users
                        if (!authRepository.isPremiumUser.value) {
                        preloadAds()
                        } else {
                            Log.d(TAG, "User is premium, skipping ad preload")
                    }
                }

                // Wait for important tasks to complete to ensure app functionality
                fcmJob.join()
                adsJob.join()
                
                if (VERBOSE_LOGGING) {
                Log.d(TAG, "Background initialization completed")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during background initialization", e)
            }
        }
    }
    
    /**
     * Initialize Firebase with error handling
     */
    private suspend fun initializeFirebase() {
        try {
            withContext(Dispatchers.IO) {
                if (FirebaseApp.getApps(context).isEmpty()) {
                    FirebaseApp.initializeApp(context)
                    if (VERBOSE_LOGGING) {
                    Log.d(TAG, "Firebase initialized")
                    }
                } else {
                    if (VERBOSE_LOGGING) {
                    Log.d(TAG, "Firebase already initialized")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing Firebase", e)
        }
    }
    
    /**
     * Initialize FCM with proper error handling
     */
    private suspend fun initializeFcm() {
        try {
            // Use suspendCancellableCoroutine to make this truly async
            val subscriptionResult = suspendCancellableCoroutine<Boolean> { continuation ->
                FirebaseMessaging.getInstance().subscribeToTopic("all_users")
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            Log.d(TAG, "Subscribed to all_users topic")
                            continuation.resume(true, null)
                        } else {
                            Log.e(TAG, "Failed to subscribe to all_users topic", task.exception)
                            continuation.resume(false, null)
                        }
                    }
            }
            
            if (subscriptionResult) {
                Log.d(TAG, "FCM subscription completed successfully")
            } else {
                Log.w(TAG, "FCM subscription failed, will retry later")
                // Schedule a retry later (could use WorkManager here)
            }
            
            // Get the FCM token for debugging in debug builds only
            if (BuildConfig.DEBUG) {
                try {
                    val tokenResult = suspendCancellableCoroutine<String?> { continuation ->
                        FirebaseMessaging.getInstance().token
                            .addOnCompleteListener { task ->
                                if (task.isSuccessful) {
                                    val token = task.result
                                    continuation.resume(token, null)
                                } else {
                                    continuation.resume(null, null)
                                }
                            }
                    }
                    
                    tokenResult?.let {
                        Log.d(TAG, "FCM Token: $it")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error getting FCM token", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing FCM", e)
        }
    }
    
    /**
     * Preload ads for better user experience
     */
    private suspend fun preloadAds() {
        try {
            // Use a short timeout to make sure we don't hang
            withTimeout(5000) {
                adManager.loadInterstitialAd()
                Log.d(TAG, "Preloaded interstitial ad")
            }
        } catch (e: TimeoutCancellationException) {
            Log.w(TAG, "Ad preloading timed out after 5 seconds")
        } catch (e: Exception) {
            Log.e(TAG, "Error preloading ads", e)
        }
    }
    
    /**
     * Register an object to be cleared during memory pressure events
     * Use this to add app-specific caches that should be cleared during low memory
     */
    fun registerMemoryCache(key: String, cache: Any) {
        memoryCaches[key] = WeakReference(cache)
        Log.d(TAG, "Registered memory cache with key: $key")
    }
    
    /**
     * Clears memory caches when the device is under critical memory pressure
     * This helps prevent OutOfMemoryErrors and app crashes
     */
    fun clearMemoryCaches() {
        try {
            val startTime = System.currentTimeMillis()
            Log.d(TAG, "Clearing all memory caches due to critical memory pressure")
            
            // Clear Coil image caches first - this will free the most memory
            // OPTIMIZATION: Use our enhanced ImageUtils instead of creating a new ImageLoader
            try {
                ImageUtils.clearImageCache(context)
                Log.d(TAG, "Image memory cache cleared via ImageUtils")
            } catch (e: Exception) {
                Log.e(TAG, "Error clearing image cache", e)
            }
            
            // Clear all registered memory caches
            val removedKeys = mutableListOf<String>()
            for ((key, cacheRef) in memoryCaches) {
                try {
                    val cache = cacheRef.get()
                    if (cache != null) {
                        when (cache) {
                            is ClearableCache -> cache.clearCache()
                            is MutableMap<*, *> -> cache.clear()
                            is MutableList<*> -> cache.clear()
                            is MutableCollection<*> -> cache.clear()
                        }
                        Log.d(TAG, "Cleared cache for key: $key")
                    } else {
                        // Reference has been garbage collected, remove from our map
                        removedKeys.add(key)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error clearing cache for key: $key", e)
                }
            }
            
            // Remove garbage collected references
            removedKeys.forEach { memoryCaches.remove(it) }
            
            // Hint to the garbage collector
            Runtime.getRuntime().gc()
            
            val duration = System.currentTimeMillis() - startTime
            Log.d(TAG, "Memory cache clearing completed in ${duration}ms")
        } catch (e: Exception) {
            Log.e(TAG, "Error during memory cache clearing", e)
        }
    }
    
    /**
     * Trims memory caches when the device is under moderate memory pressure
     * Less aggressive than clearMemoryCaches, only removes older/less important items
     */
    fun trimMemoryCaches() {
        try {
            Log.d(TAG, "Trimming memory caches due to moderate memory pressure")
            
            // Trim Coil image cache
            // OPTIMIZATION: Use our enhanced ImageUtils instead of creating a new ImageLoader
            try {
                ImageUtils.trimImageCache(context)
                Log.d(TAG, "Image cache trimmed via ImageUtils")
            } catch (e: Exception) {
                Log.e(TAG, "Error trimming image cache", e)
            }
            
            // Trim registered caches that support it
            for ((key, cacheRef) in memoryCaches) {
                try {
                    val cache = cacheRef.get()
                    if (cache != null) {
                        when (cache) {
                            is TrimmableCache -> {
                                cache.trimCache()
                                Log.d(TAG, "Trimmed cache for key: $key")
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error trimming cache for key: $key", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error trimming memory caches", e)
        }
    }
    
    /**
     * Cleanup method to cancel any ongoing work and flush preferences
     * OPTIMIZATION: Ensures all cached preferences are properly saved
     */
    fun cleanup() {
        // Cancel all background tasks
        appScope.cancel()
        
        // Clear memory caches
        memoryCaches.clear()
        
        try {
            // Flush any pending preference changes
            preferencesManager.cleanup()
            
            Log.d(TAG, "AppInitializer resources cleaned up")
        } catch (e: Exception) {
            Log.e(TAG, "Error during AppInitializer cleanup", e)
        }
    }
    
    /**
     * Interface for objects that can clear their caches
     */
    interface ClearableCache {
        fun clearCache()
    }
    
    /**
     * Interface for objects that can trim their caches
     */
    interface TrimmableCache {
        fun trimCache()
    }
}
