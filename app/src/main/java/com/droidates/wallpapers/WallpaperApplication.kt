package com.droidates.wallpapers

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ComponentCallbacks2
import android.content.Context
import android.os.Build
import android.os.StrictMode
import android.util.Log
import com.droidates.wallpapers.utils.AdManager
import com.droidates.wallpapers.utils.PreferencesManager
import com.droidates.wallpapers.utils.RefreshRateManager
import com.droidates.wallpapers.utils.SystemServiceCache
import com.droidates.wallpapers.utils.RefreshRateLifecycleObserver
import com.droidates.wallpapers.utils.NetworkUtils
import com.google.firebase.FirebaseApp
import com.google.android.gms.ads.MobileAds
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.analytics.FirebaseAnalytics
import dagger.hilt.android.HiltAndroidApp
import com.droidates.wallpapers.utils.AppInitializer
import kotlinx.coroutines.*
import javax.inject.Inject
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.util.DebugLogger
import com.droidates.wallpapers.ui.components.ToastManager
import com.droidates.wallpapers.utils.PerformanceOptimizations
import androidx.work.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import androidx.hilt.work.HiltWorkerFactory
import com.droidates.wallpapers.BuildConfig
import com.droidates.wallpapers.R

private const val TAG = "WallpaperApplication"

private const val VERBOSE_LOGGING = false

/**
 * Optimized Application class for smooth performance
 */
@HiltAndroidApp
class WallpaperApplication : Application(), ImageLoaderFactory, ComponentCallbacks2, Configuration.Provider {
    
    @Inject 
    lateinit var systemServiceCache: SystemServiceCache
    
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    private val toastManager by lazy { ToastManager() }

    // Firebase Analytics instance (initialized lazily)
    val analytics: FirebaseAnalytics by lazy {
        FirebaseAnalytics.getInstance(this)
    }
    
    private val applicationScope = CoroutineScope(
        SupervisorJob() + 
        Dispatchers.Default +
        CoroutineExceptionHandler { _, throwable ->
            if (throwable !is CancellationException && VERBOSE_LOGGING) {
                Log.e(TAG, "Application scope error: ${throwable.message}", throwable)
            }
        }
    )
    
    private val isInitialized = AtomicBoolean(false)
    private val notificationChannelsCreated = AtomicBoolean(false)
    private val isHandlingMemoryTrim = AtomicBoolean(false)
    private val imageLoaderInitialized = AtomicBoolean(false)
    
    private val imageLoader by lazy {
        val devicePerformance = PerformanceOptimizations.getDevicePerformance(this)
        val (memoryPercent, diskCacheSize) = PerformanceOptimizations.getOptimalCacheSize(devicePerformance)
        val crossfadeDuration = PerformanceOptimizations.getOptimalCrossfadeDuration(devicePerformance)
        
        val loader = ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(memoryPercent)
                    .weakReferencesEnabled(true)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(diskCacheSize)
                    .build()
            }
            .crossfade(crossfadeDuration)
            .respectCacheHeaders(false) // Aggressive caching for wallpapers
            .apply {
                if (BuildConfig.DEBUG && VERBOSE_LOGGING) {
                    logger(DebugLogger())
                }
                // Enable hardware acceleration on capable devices
                if (PerformanceOptimizations.shouldUseHardwareAcceleration(devicePerformance)) {
                    allowHardware(true)
                }
            }
            .build()
        
        imageLoaderInitialized.set(true)
        loader
    }
    
    override fun onCreate() {
        super.onCreate()

        // REMOVED: registerComponentCallbacks(this) - Application class already registers itself automatically
        // Calling it again causes infinite recursion in onLowMemory on some devices (Android 15 Redmi 13C)
        scheduleUltraDelayedBackgroundInitialization()
    }
    
    private fun scheduleUltraDelayedBackgroundInitialization() {
        if (isInitialized.compareAndSet(false, true)) {
            // Disabled old BackgroundInitializationWorker to prevent conflicts
            // New InitializationWorker with native ad support is used instead from MainActivity
            Log.d(TAG, "Background initialization handled by MainActivity InitializationWorker")
        }
    }
    
    /**
     * Create notification channels only when absolutely necessary
     */
    private suspend fun createNotificationChannelsIfNeeded() {
        if (notificationChannelsCreated.getAndSet(true)) {
            return // Already created
        }
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                
                // OPTIMIZATION: Check if channels already exist to avoid unnecessary work
                val existingChannels = notificationManager.notificationChannels?.map { it.id } ?: emptyList()
                
                val defaultChannelId = getString(R.string.default_notification_channel_id)
                val promoChannelId = getString(R.string.promo_notification_channel_id)
                
                if (existingChannels.contains(defaultChannelId) && existingChannels.contains(promoChannelId)) {
                    return
                }
                
                // Create missing channels
            if (!existingChannels.contains(defaultChannelId)) {
                val defaultChannel = NotificationChannel(
                    defaultChannelId,
                        getString(R.string.default_notification_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                        description = getString(R.string.default_notification_channel_description)
                    }
                    notificationManager.createNotificationChannel(defaultChannel)
                }
                
            if (!existingChannels.contains(promoChannelId)) {
                val promoChannel = NotificationChannel(
                    promoChannelId,
                        getString(R.string.promo_notification_channel_name),
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                        description = getString(R.string.promo_notification_channel_description)
                    }
                    notificationManager.createNotificationChannel(promoChannel)
                }
            }
        } catch (e: Exception) {
            if (VERBOSE_LOGGING) {
                Log.e(TAG, "Error creating notification channels", e)
            }
        }
    }
    
    /**
     * CRITICAL: StrictMode completely disabled for smooth performance
     */
    private fun enableStrictMode() {
        // StrictMode violations cause lag spikes and are not essential for this wallpaper app
    }
    
    /**
     * Provide the ultra-optimized ImageLoader
     */
    override fun newImageLoader(): ImageLoader = imageLoader
    
    /**
     * CRITICAL FIX: Override onLowMemory to prevent StackOverflowError
     * ComponentCallbacks2 interface requires this method to be implemented
     * Without it, some Android devices (especially Android 15) can enter infinite recursion
     */
    override fun onLowMemory() {
        // CRASH FIX: Guard against recursive calls
        if (!isHandlingMemoryTrim.compareAndSet(false, true)) {
            if (VERBOSE_LOGGING) {
                Log.w(TAG, "onLowMemory called recursively, skipping")
            }
            return
        }

        try {
            super.onLowMemory()

            // Clear image cache to free memory
            if (imageLoaderInitialized.get()) {
                imageLoader.memoryCache?.clear()
                imageLoader.diskCache?.clear()
            }

            // Suggest garbage collection
            System.gc()

            if (VERBOSE_LOGGING) {
                Log.d(TAG, "onLowMemory: Cleared caches and suggested GC")
            }
        } catch (e: Exception) {
            if (VERBOSE_LOGGING) {
                Log.e(TAG, "Error handling onLowMemory", e)
            }
        } finally {
            isHandlingMemoryTrim.set(false)
        }
    }

    override fun onTrimMemory(level: Int) {
        if (!isHandlingMemoryTrim.compareAndSet(false, true)) {
            if (VERBOSE_LOGGING) {
                Log.w(TAG, "onTrimMemory called recursively, skipping")
            }
            return
        }

        try {
            super.onTrimMemory(level)

            when (level) {
                ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE,
                ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW,
                ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> {
                    try {
                        if (imageLoaderInitialized.get()) {
                            imageLoader.memoryCache?.clear()
                        }

                        if (level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL) {
                            System.gc()
                        }
                    } catch (e: Exception) {
                        if (VERBOSE_LOGGING) {
                            Log.e(TAG, "Error clearing memory cache during trim", e)
                        }
                    }
                }
                ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> {
                    try {
                        if (imageLoaderInitialized.get()) {
                            imageLoader.memoryCache?.clear()
                        }
                    } catch (e: Exception) {
                        if (VERBOSE_LOGGING) {
                            Log.e(TAG, "Error clearing memory cache when UI hidden", e)
                        }
                    }
                }
            }
        } finally {
            isHandlingMemoryTrim.set(false)
        }
    }
    
    override fun getWorkManagerConfiguration(): Configuration {
        return Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(if (BuildConfig.DEBUG) Log.DEBUG else Log.ERROR)
            .build()
    }
    
    private val isHandlingConfigurationChange = AtomicBoolean(false)
    
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        if (!isHandlingConfigurationChange.compareAndSet(false, true)) {
            if (VERBOSE_LOGGING) {
                Log.w(TAG, "onConfigurationChanged called recursively, skipping")
            }
            return
        }
        
        try {
            super.onConfigurationChanged(newConfig)
            if (VERBOSE_LOGGING) {
                Log.d(TAG, "Configuration changed safely handled")
            }
        } catch (e: Exception) {
            if (VERBOSE_LOGGING) {
                Log.e(TAG, "Error handling configuration change", e)
            }
        } finally {
            isHandlingConfigurationChange.set(false)
        }
    }
    
    override fun onTerminate() {
        super.onTerminate()
        applicationScope.cancel()
        // REMOVED: unregisterComponentCallbacks(this) - we never manually registered, so no need to unregister
    }
}