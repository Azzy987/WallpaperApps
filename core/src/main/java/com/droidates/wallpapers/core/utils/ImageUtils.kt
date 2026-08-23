package com.droidates.wallpapers.core.utils

import com.droidates.wallpapers.core.config.AppConfig
import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.util.Log
import androidx.compose.ui.graphics.Color
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Scale
import coil.size.Size
import coil.transform.Transformation
import android.graphics.Bitmap
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicBlur
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import android.os.SystemClock
import androidx.compose.ui.graphics.toArgb

/**
 * Utility class for image loading and caching
 * Optimized for performance and reduced memory usage
 */
object ImageUtils {
    private const val TAG = "ImageUtils"
    private const val DEBUG_LOGGING = false
    
    /**
     * Custom logger for Coil that filters out excessive INFO level logs
     * OPTIMIZATION: This significantly reduces log spam during image loading
     */
    class CoilCustomLogger : coil.util.Logger {
        // Set minimum log level to WARN to filter out INFO level cache hit messages
        override var level: Int = Log.WARN
        
        override fun log(tag: String, priority: Int, message: String?, throwable: Throwable?) {
            // Filter out common INFO level logs that cause excessive logging
            if (priority == Log.INFO && message != null && (
                message.contains("Memory cache hit") || 
                message.contains("Disk cache hit") || 
                message.contains("Fetching image") ||
                message.contains("Transformed") ||
                message.contains("Decoded") ||
                message.contains("Successfully loaded")
            )) {
                return
            }
            
            // Only log messages at or above our set level
            if (priority >= level) {
                message?.let { Log.println(priority, tag, it) }
                throwable?.let { Log.println(priority, tag, it.toString()) }
            }
        }
    }
    
    // Default dimensions for thumbnails
    private const val THUMBNAIL_WIDTH = 400
    private const val THUMBNAIL_HEIGHT = 600
    
    // Quality settings for progressive loading
    private const val LOW_QUALITY_SIZE = 50  // Extremely small for instant loading
    private const val MEDIUM_QUALITY_SIZE = 150
    
    // Memory cache size constants
    private const val DEFAULT_MEMORY_PERCENTAGE = 0.25 // 25% of available memory
    private const val THUMBNAIL_CROSSFADE_DURATION = 150 // Faster crossfade for thumbnails
    
    // OPTIMIZATION: Limit concurrent image preloads to prevent overloading
    private const val MAX_CONCURRENT_PRELOADS = 5
    private val preloadCount = AtomicInteger(0)
    
    // Cache size constants
    private const val DISK_CACHE_SIZE_STANDARD = 100 * 1024 * 1024 // 100MB default
    private const val DISK_CACHE_SIZE_HEAVY_USER = 200 * 1024 * 1024 // 200MB for heavy users
    private const val DISK_CACHE_SIZE_LIGHT_USER = 50 * 1024 * 1024 // 50MB for light users
    
    // Cache for image loader
    private var cachedImageLoader: ImageLoader? = null
    private val imageLoaderMutex = Mutex()
    
    // Cache for generated cache keys to avoid repeated hashing
    private val cacheKeyCache = ConcurrentHashMap<String, String>()
    
    // User behavior tracking
    private var totalImagesViewed = AtomicInteger(0)
    private var totalDetailViews = AtomicInteger(0)
    private var lastCacheSizeAdjustment = 0L
    
    // OPTIMIZATION: Track active preloads to avoid redundant work
    // Using a more efficient structure with a fixed size that automatically evicts old entries
    private class PreloadTracker(private val maxSize: Int = 200) {
        // Make these internal so they can be accessed directly in non-suspend contexts
        internal val entries = ConcurrentHashMap<String, Long>()
        internal val accessOrder = ConcurrentLinkedQueue<String>()
        private val mutex = Mutex()
        
        /**
         * Check if a URL was recently preloaded and mark it as preloaded if not
         * @return true if the URL should be preloaded, false if it was recently preloaded
         */
        suspend fun shouldPreload(key: String, currentTime: Long, minInterval: Long = 10_000): Boolean {
            // Fast check without locking
            val existingTime = entries[key]
            if (existingTime != null && currentTime - existingTime <= minInterval) {
                return false
            }
            
            // Need to update, use mutex for thread safety
            return mutex.withLock {
                // Double-check after acquiring lock
                val lastTime = entries[key]
                if (lastTime != null && currentTime - lastTime <= minInterval) {
                    false
                } else {
                    // Update the entry
                    entries[key] = currentTime
                    
                    // Update access order
                    accessOrder.remove(key)
                    accessOrder.add(key)
                    
                    // Evict oldest if needed
                    if (entries.size > maxSize) {
                        val oldest = accessOrder.poll()
                        if (oldest != null) {
                            entries.remove(oldest)
                        }
                    }
                    
                    true
                }
            }
        }
        
        /**
         * Clear entries older than the specified age
         */
        suspend fun clearOldEntries(currentTime: Long, maxAge: Long = 30_000) {
            mutex.withLock {
                val iterator = entries.entries.iterator()
                while (iterator.hasNext()) {
                    val entry = iterator.next()
                    if (currentTime - entry.value > maxAge) {
                        iterator.remove()
                        accessOrder.remove(entry.key)
                    }
                }
            }
        }
        
        /**
         * Get the current size of the tracker
         */
        fun size(): Int = entries.size
        
        /**
         * Clear all entries
         */
        suspend fun clear() {
            mutex.withLock {
                entries.clear()
                accessOrder.clear()
            }
        }
    }
    
    // Create the preload tracker instance
    private val preloadTracker = PreloadTracker(300)
    
    // OPTIMIZATION: Mutex for preloading to ensure thread safety
    private val preloadMutex = Mutex()
    
    // OPTIMIZATION: Batch preloading queue to reduce thread creation overhead
    private data class PreloadRequest(
        val context: Context,
        val url: String,
        val thumbnailUrl: String = "",
        val priority: Int = 0,
        val timestamp: Long = SystemClock.elapsedRealtime()
    )
    
    // Queue for batched preloading
    private val preloadQueue = ConcurrentLinkedQueue<PreloadRequest>()
    
    // Flag to track if the batch processor is running
    private val isBatchProcessorRunning = AtomicBoolean(false)
    
    /**
     * Generates a consistent cache key for the given URL
     * OPTIMIZATION: Uses a cache to avoid repeated hashing operations
     */
    private fun generateCacheKey(url: String): String {
        // Check if we already have a cached key for this URL
        cacheKeyCache[url]?.let { return it }
        
        // Generate a new key if not in cache
        val key = try {
            // Use MD5 for consistent hashing
            val md = MessageDigest.getInstance("MD5")
            val digest = md.digest(url.toByteArray())
            digest.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            // Fallback to a simple hash if MD5 fails
            url.hashCode().toString()
        }
        
        // Cache the result to avoid repeated computation
        cacheKeyCache[url] = key
        return key
    }
    
    /**
     * Simple logging function that only logs in debug mode
     */
    private fun log(message: String) {
        if (AppConfig.IS_DEBUG && DEBUG_LOGGING) {
            Log.d(TAG, message)
        }
    }
    
    /**
     * Creates an optimized image request for thumbnails
     * OPTIMIZATION: Uses a fast temporary cache key if needed during startup
     */
    fun createThumbnailRequest(
        context: Context,
        url: String,
        thumbnailUrl: String
    ): ImageRequest {
        // Generate a consistent cache key
        val cacheKey = "thumb_${generateCacheKey(url.ifEmpty { thumbnailUrl })}"
        
        return ImageRequest.Builder(context)
            .data(if (url.isNotEmpty()) url else thumbnailUrl)
            .memoryCacheKey(cacheKey)
            .diskCachePolicy(CachePolicy.ENABLED)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .crossfade(true)
            .crossfade(150) // Quick crossfade for thumbnails
            .placeholder(ColorDrawable(Color.DarkGray.toArgb()))
            .error(ColorDrawable(Color.DarkGray.toArgb()))
            .build()
    }
    
    /**
     * Creates an optimized image request for full wallpapers
     * OPTIMIZATION: Uses a fast temporary cache key if needed during startup
     */
    fun createWallpaperRequest(
        context: Context,
        url: String,
        thumbnailUrl: String = ""
    ): ImageRequest {
        // Ensure we have a valid URL
        if (url.isEmpty()) {
            // Fallback to thumbnail or placeholder
            return if (thumbnailUrl.isNotEmpty()) {
                createThumbnailRequest(context, "", thumbnailUrl)
            } else {
                ImageRequest.Builder(context)
                    .data(ColorDrawable(Color.DarkGray.toArgb()))
                    .build()
            }
        }
        
        // Generate a consistent cache key
        val cacheKey = "full_${generateCacheKey(url)}"
        
        // Build the optimized request for full wallpapers
        val requestBuilder = ImageRequest.Builder(context)
            .data(url)
            .memoryCacheKey(cacheKey)
            .diskCachePolicy(CachePolicy.ENABLED)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .crossfade(true)
            .crossfade(200) // Slightly longer crossfade for full images
            .placeholder(ColorDrawable(Color.Black.toArgb()))
            .fallback(ColorDrawable(Color.Black.toArgb()))
            .error(ColorDrawable(Color.Black.toArgb()))
        
        // Track image views for adaptive caching
        requestBuilder.listener(
            onSuccess = { _, _ -> totalImagesViewed.incrementAndGet() }
        )
        
        return requestBuilder.build()
    }
    
    /**
     * Creates an optimized image request for detail view (high-res) with prioritization option
     * OPTIMIZATION: Uses a fast temporary cache key if needed during startup
     */
    fun createDetailImageRequest(
        context: Context,
        url: String,
        thumbnailUrl: String = "",
        prioritize: Boolean = true
    ): ImageRequest {
        // Ensure we have a valid URL
        if (url.isEmpty()) {
            // Fallback to thumbnail or placeholder
            return if (thumbnailUrl.isNotEmpty()) {
                createThumbnailRequest(context, "", thumbnailUrl)
            } else {
                ImageRequest.Builder(context)
                    .data(ColorDrawable(Color.DarkGray.toArgb()))
                    .build()
            }
        }
        
        // Generate a consistent cache key
        val cacheKey = "detail_${generateCacheKey(url)}"
        
        // Build the optimized request for detail view
        val requestBuilder = ImageRequest.Builder(context)
            .data(url)
            .memoryCacheKey(cacheKey)
            .diskCachePolicy(CachePolicy.ENABLED)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .crossfade(true)
            .crossfade(250) // Longer crossfade for smoother transitions
            .placeholder(ColorDrawable(Color.Black.toArgb()))
            .fallback(ColorDrawable(Color.Black.toArgb()))
            .error(ColorDrawable(Color.Black.toArgb()))
            
        // OPTIMIZATION: Implement progressive image loading with quality levels
        // This shows lower-quality images first for faster perceived performance
        if (thumbnailUrl.isNotEmpty()) {
            // Since this version of Coil doesn't support the thumbnail() method directly,
            // we'll implement a custom solution using a listener
            if (prioritize) {
                requestBuilder.listener(
                    onStart = {
                        log("Detail image loading started: $url")
                        
                        // Preload the thumbnail immediately when the full image starts loading
                        CoroutineScope(Dispatchers.IO).launch {
                            try {
                                // Create a low-quality thumbnail request
                                val thumbnailRequest = ImageRequest.Builder(context)
                                    .data(thumbnailUrl)
                                    .memoryCacheKey("thumb_${generateCacheKey(thumbnailUrl)}")
                                    .diskCachePolicy(CachePolicy.ENABLED)
                                    .memoryCachePolicy(CachePolicy.ENABLED)
                                    .build()
                                
                                // Execute the thumbnail request to ensure it's in cache
                                getImageLoader(context).execute(thumbnailRequest)
                                
                                // Log the progressive loading in debug mode
                                if (AppConfig.IS_DEBUG && DEBUG_LOGGING) {
                                    Log.d(TAG, "Progressive loading enabled for detail view: $url with thumbnail: $thumbnailUrl")
                                }
                            } catch (e: Exception) {
                                // Ignore errors during thumbnail preloading
                            }
                        }
                    },
                    onSuccess = { _, _ -> log("Detail image loaded successfully: $url") },
                    onError = { _, error -> log("Detail image load error: ${error.throwable.message}") }
                )
            } else {
                requestBuilder.listener(
                    onStart = { 
                        // Preload the thumbnail immediately when the full image starts loading
                        CoroutineScope(Dispatchers.IO).launch {
                            try {
                                // Create a low-quality thumbnail request
                                val thumbnailRequest = ImageRequest.Builder(context)
                                    .data(thumbnailUrl)
                                    .memoryCacheKey("thumb_${generateCacheKey(thumbnailUrl)}")
                                    .diskCachePolicy(CachePolicy.ENABLED)
                                    .memoryCachePolicy(CachePolicy.ENABLED)
                                    .build()
                                
                                // Execute the thumbnail request to ensure it's in cache
                                getImageLoader(context).execute(thumbnailRequest)
                                
                                // Log the progressive loading in debug mode
                                if (AppConfig.IS_DEBUG && DEBUG_LOGGING) {
                                    Log.d(TAG, "Progressive loading enabled for detail view: $url with thumbnail: $thumbnailUrl")
                                }
                            } catch (e: Exception) {
                                // Ignore errors during thumbnail preloading
                            }
                        }
                    }
                )
            }
        }
        
        // Add higher priority for detail views when requested - handled in progressive loading code
        // If no thumbnail URL is provided but prioritize is true, add the listener
        if (prioritize && thumbnailUrl.isEmpty()) {
            requestBuilder.listener(
                onStart = { log("Detail image loading started: $url") },
                onSuccess = { _, _ -> 
                    // Track detail view for adaptive caching
                    totalDetailViews.incrementAndGet()
                    log("Detail image loaded successfully: $url")
                },
                onError = { _, error -> log("Detail image load error: ${error.throwable.message}") }
            )
        }
        
        return requestBuilder.build()
    }
    
    /**
     * Creates an optimized image loading request specifically for WallpaperCard thumbnails
     * MAJOR OPTIMIZATION: Completely eliminated progressive loading for ultimate grid performance
     */

    /**
     * Rewrites a CloudFront URL so the CDN resizes the image server-side
     * (`/fit-in/<w>x<h>/`). Without this the device downloads and decodes the full
     * original — several MB and often 4000px+ on a side — just to draw a small view.
     *
     * Returns the URL unchanged if it is not a CloudFront URL.
     */
    fun cloudFrontFitInUrl(url: String, width: Int, height: Int): String {
        val marker = ".cloudfront.net/"
        val markerIndex = url.indexOf(marker)
        if (markerIndex == -1) return url

        val domainEnd = markerIndex + marker.length
        var path = url.substring(domainEnd).trimStart('/')
        if (path.startsWith("fit-in/")) {
            val segments = path.split("/")
            path = if (segments.size > 2) segments.drop(2).joinToString("/") else path
        }
        return url.substring(0, domainEnd) + "fit-in/${width}x${height}/$path"
    }

    /**
     * Banner image request: CDN-resized to banner proportions, decoded in software.
     *
     * Hardware bitmaps are GPU textures bound by GL_MAX_TEXTURE_SIZE, so a very large
     * source fails to load entirely. Banners are wide, not portrait — sizing them
     * 300x450 (the grid thumbnail shape) also cropped them badly.
     */
    fun createBannerImageRequest(context: Context, url: String): ImageRequest {
        val sized = cloudFrontFitInUrl(url, BANNER_WIDTH, BANNER_HEIGHT)
        return ImageRequest.Builder(context)
            .data(sized)
            .memoryCacheKey("banner_${url.hashCode()}")
            .diskCacheKey("banner_${url.hashCode()}")
            .size(BANNER_WIDTH, BANNER_HEIGHT)
            .scale(Scale.FILL)
            .crossfade(100)
            .allowHardware(false)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .build()
    }

    private const val BANNER_WIDTH = 1080
    private const val BANNER_HEIGHT = 608

    fun createProgressiveImageRequest(
        context: Context,
        url: String,
        thumbnailUrl: String = "",
        prioritize: Boolean = false
    ): ImageRequest {
        // MAJOR OPTIMIZATION: Always use thumbnail URL for grid performance
        val effectiveUrl = if (thumbnailUrl.isNotEmpty()) thumbnailUrl else url
        
        // MAJOR OPTIMIZATION: Simplified cache key generation
        val cacheKey = "thumb_${effectiveUrl.hashCode()}"
        
        // MAJOR OPTIMIZATION: Ultra-minimal image request for maximum performance
            return ImageRequest.Builder(context)
            .data(effectiveUrl)
            .memoryCacheKey(cacheKey)
            .crossfade(100) // Extremely fast crossfade
                .scale(Scale.FILL)
            .size(300, 450) // Smaller fixed size for faster loading
            .allowHardware(true)
            // MAJOR OPTIMIZATION: Only essential caching
            .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
            // MAJOR OPTIMIZATION: Minimal placeholder overhead
            .placeholder(null) // No placeholder for maximum speed
            .error(null) // No error drawable for maximum speed
                .build()
    }
    
    /**
     * Preloads an image into the cache
     * OPTIMIZATION: Uses batched preloading to reduce thread creation overhead
     */
    fun preloadImage(
        context: Context,
        url: String,
        thumbnailUrl: String = "",
        priority: Int = 0
    ) {
        // Add to preload queue
        preloadQueue.add(PreloadRequest(context, url, thumbnailUrl, priority))
        
        // Start batch processor if not already running
        startBatchProcessor()
    }
    
    /**
     * Starts the batch processor if it's not already running
     * OPTIMIZATION: This reduces thread creation by processing multiple requests in a single coroutine
     */
    private fun startBatchProcessor() {
        if (isBatchProcessorRunning.compareAndSet(false, true)) {
            // Launch in a new coroutine
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    // Process batched preloads
                    processBatchedPreloads()
                } finally {
                    // Reset flag when done
                    isBatchProcessorRunning.set(false)
                }
            }
        }
    }
    
    /**
     * Process batched preload requests
     * OPTIMIZATION: Processes multiple requests in a single coroutine to reduce overhead
     */
    private suspend fun processBatchedPreloads() {
        // Get the cached image loader once for all requests
        val imageLoader = getImageLoader(preloadQueue.peek()?.context ?: return)
        
        // Process up to MAX_CONCURRENT_PRELOADS at a time
        val batchSize = minOf(MAX_CONCURRENT_PRELOADS, preloadQueue.size)
        val batch = mutableListOf<PreloadRequest>()
        
        // Extract batch from queue
        for (i in 0 until batchSize) {
            val request = preloadQueue.poll() ?: break
            batch.add(request)
        }
        
        // Sort by priority (higher first)
        batch.sortByDescending { it.priority }
        
        // Process each request
        val currentTime = SystemClock.elapsedRealtime()
        batch.forEach { request ->
            // Skip if already preloaded recently
            if (preloadTracker.shouldPreload(request.url, currentTime)) {
                try {
                    // Process thumbnail first if available
                    if (request.thumbnailUrl.isNotEmpty()) {
                        processPreloadItem(
                            imageLoader,
                            request.context,
                            request.thumbnailUrl,
                            "thumb_",
                            currentTime
                        )
                    }
                    
                    // Then process full image
                    processPreloadItem(
                        imageLoader,
                        request.context,
                        request.url,
                        "full_", 
                        currentTime
                    )
                } catch (e: Exception) {
                    // Ignore errors during preloading
                    if (AppConfig.IS_DEBUG) {
                        Log.e(TAG, "Error preloading image: ${e.message}")
                    }
                }
            }
        }
        
        // Periodically clean up old preload entries
        cleanupPreloadEntries()
    }
    
    /**
     * Process a single preload item
     * OPTIMIZATION: Reuses code for both thumbnail and full image preloading
     * OPTIMIZATION: Uses optimized preload tracker to eliminate redundant ConcurrentHashMap lookups
     */
    private suspend fun processPreloadItem(
        imageLoader: ImageLoader,
        context: Context,
        url: String,
        keyPrefix: String,
        currentTime: Long
    ) {
        // Skip if URL is empty
        if (url.isEmpty()) return
        
        // Create a request with the appropriate cache key
        val request = ImageRequest.Builder(context)
            .data(url)
            .memoryCacheKey("${keyPrefix}${generateCacheKey(url)}")
            .diskCachePolicy(CachePolicy.ENABLED)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .build()
        
        // Execute the request to ensure it's in cache
        imageLoader.execute(request)
    }
    
    /**
     * Cleans up old preload entries to prevent memory leaks
     * OPTIMIZATION: Uses optimized preload tracker with automatic eviction
     */
    private suspend fun cleanupPreloadEntries() {
        try {
            val currentTime = SystemClock.elapsedRealtime()
            
            // Clear entries older than 30 seconds
            preloadTracker.clearOldEntries(currentTime, 30_000)
            
            // Log cleanup in debug mode
            if (AppConfig.IS_DEBUG && DEBUG_LOGGING) {
                Log.d(TAG, "Cleaned up preload entries. Current size: ${preloadTracker.size()}")
            }
        } catch (e: Exception) {
            // Ignore errors during cleanup
        }
    }
    
    /**
     * Get or create a cached ImageLoader instance with optimized memory settings
     * OPTIMIZATION: This prevents creating multiple ImageLoader instances and configures
     * memory usage based on device capabilities
     */
    private suspend fun getImageLoader(context: Context): ImageLoader {
        // Fast path - return existing loader if available
        cachedImageLoader?.let { return it }
        
        // Slow path - create and cache a new loader with mutex protection
        return imageLoaderMutex.withLock {
            // Double-check after acquiring lock
            cachedImageLoader?.let { return it }
            
            // Calculate optimal memory cache size based on device memory
            val memoryCacheSize = calculateMemoryCacheSize(context)
            
            log("Configuring ImageLoader with memory cache size: ${memoryCacheSize / (1024 * 1024)}MB")
            
            // Create new ImageLoader with optimized settings
            val newLoader = ImageLoader.Builder(context)
                .memoryCache {
                    // Configure memory cache with calculated size
                    MemoryCache.Builder(context)
                        .maxSizePercent(0.0) // We're setting absolute size below
                        .maxSizeBytes(memoryCacheSize)
                        .build()
                }
                .diskCache {
                    // Create disk cache with size based on user behavior
                    DiskCache.Builder()
                        .directory(context.cacheDir.resolve("image_cache"))
                        .maxSizeBytes(calculateDiskCacheSize())
                        .build()
                }
                .crossfade(false) // We handle crossfade in the ImageRequest
                .allowHardware(true) // Use hardware acceleration when possible
                .respectCacheHeaders(false) // Override server cache headers for better caching
                // Add a custom logger that only logs errors and warnings, not successful cache hits
                .logger(CoilCustomLogger())
                .build()
            
            // Cache and return
            cachedImageLoader = newLoader
            newLoader
        }
    }
    
    /**
     * Calculate memory cache size based on device capabilities and user behavior patterns
     * OPTIMIZATION: This adjusts the memory cache size based on available device memory and usage patterns
     */
    private fun calculateMemoryCacheSize(context: Context): Int {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val memoryClass = activityManager.memoryClass
        
        // Base cache size on device memory class
        // For low-memory devices (<=4GB RAM), use a more conservative allocation
        var cacheSize = if (memoryClass <= 128) {
            // Low memory device - use 10% of available app memory
            (1024 * 1024 * memoryClass / 10)
        } else {
            // High memory device - use 15% of available app memory
            (1024 * 1024 * memoryClass / 7)
        }
        
        // Adjust based on user behavior patterns
        val imagesViewed = totalImagesViewed.get()
        val detailViews = totalDetailViews.get()
        
        // Calculate user engagement score (0-100)
        val engagementScore = minOf(100, (imagesViewed + detailViews * 2) / 3)
        
        when {
            engagementScore > 70 -> {
                // Heavy user - allocate more memory cache
                cacheSize = (cacheSize * 1.5).toInt()
                log("High engagement user (score: $engagementScore) - increasing memory cache")
            }
            engagementScore < 30 -> {
                // Light user - reduce memory cache
                cacheSize = (cacheSize * 0.8).toInt()
                log("Low engagement user (score: $engagementScore) - reducing memory cache")
            }
            else -> {
                log("Medium engagement user (score: $engagementScore) - using standard memory cache")
            }
        }
        
        // Ensure we have reasonable bounds based on device capabilities
        val minCache = 5 * 1024 * 1024  // 5MB minimum
        val maxCache = if (memoryClass <= 128) {
            40 * 1024 * 1024  // 40MB max for low-memory devices
        } else {
            60 * 1024 * 1024  // 60MB max for high-memory devices
        }
        
        return cacheSize.coerceIn(minCache, maxCache)
    }
    
    /**
     * Calculate disk cache size based on user behavior patterns and device storage
     * OPTIMIZATION: This adjusts the disk cache size based on how the user interacts with the app and available storage
     */
    private fun calculateDiskCacheSize(): Long {
        val imagesViewed = totalImagesViewed.get()
        val detailViews = totalDetailViews.get()
        
        // Calculate user engagement score (0-100)
        val engagementScore = minOf(100, (imagesViewed + detailViews * 2) / 3)
        
        // Default to standard size
        var cacheSize = DISK_CACHE_SIZE_STANDARD.toLong()
        
        // Adjust based on engagement score
        when {
            engagementScore > 70 -> {
                // Heavy user - allocate more disk cache
                cacheSize = DISK_CACHE_SIZE_HEAVY_USER.toLong()
                log("High engagement user (score: $engagementScore) - using larger disk cache: ${cacheSize / (1024 * 1024)}MB")
            }
            engagementScore < 30 -> {
                // Light user - reduce disk cache to save space
                cacheSize = DISK_CACHE_SIZE_LIGHT_USER.toLong()
                log("Low engagement user (score: $engagementScore) - using smaller disk cache: ${cacheSize / (1024 * 1024)}MB")
            }
            else -> {
                log("Medium engagement user (score: $engagementScore) - using standard disk cache: ${cacheSize / (1024 * 1024)}MB")
            }
        }
        
        // Check if the user has viewed many wallpapers in detail view
        // This indicates they likely appreciate high-quality images and would benefit from a larger cache
        if (detailViews > imagesViewed / 2 && engagementScore >= 50) {
            // User spends significant time in detail view - increase cache size by 20%
            cacheSize = (cacheSize * 1.2).toLong()
            log("Detail view heavy user - increasing disk cache by 20% to: ${cacheSize / (1024 * 1024)}MB")
        }
        
        return cacheSize
    }
    
    /**
     * Track when a thumbnail image is viewed to adjust cache behavior
     * Call this when a thumbnail is successfully loaded in the UI
     */
    fun trackThumbnailView() {
        totalImagesViewed.incrementAndGet()
    }
    
    /**
     * Track when a detail image is viewed to adjust cache behavior
     * Call this when a detail image is successfully loaded in the UI
     */
    fun trackDetailView() {
        totalDetailViews.incrementAndGet()
    }
    
    /**
     * Trims the image cache during moderate memory pressure
     * OPTIMIZATION: Implements AppInitializer.TrimmableCache interface for memory management
     */
    fun trimImageCache(context: Context) {
        try {
            // Clear older entries from the cache key cache if it's large
            if (cacheKeyCache.size > 500) {
                // Keep only the most recent 200 entries
                val keysToRemove = cacheKeyCache.keys.toList().dropLast(200)
                keysToRemove.forEach { cacheKeyCache.remove(it) }
            }
            
            // Clear older preload entries - launch in a coroutine since it's a suspend function
            CoroutineScope(Dispatchers.IO).launch {
                cleanupPreloadEntries()
            }
            
            // Periodically adjust cache sizes based on user behavior
            val currentTime = SystemClock.elapsedRealtime()
            if (currentTime - lastCacheSizeAdjustment > 3600000) { // Once per hour
                lastCacheSizeAdjustment = currentTime
                
                // If we have a cached image loader, update its memory cache size
                cachedImageLoader?.let { loader ->
                    val newSize = calculateMemoryCacheSize(context)
                    // Use reflection to call resize method if available
                    try {
                        val memoryCache = loader.memoryCache
                        if (memoryCache != null) {
                            val resizeMethod = memoryCache.javaClass.getMethod("resize", Long::class.java)
                            resizeMethod.invoke(memoryCache, newSize.toLong())
                            log("Adjusted memory cache size to: ${newSize / (1024 * 1024)}MB based on user behavior")
                        }
                    } catch (e: Exception) {
                        // Ignore if resize method is not available
                        log("Could not resize memory cache: ${e.message}")
                    }
                }
            }
            
            log("Image cache trimmed due to memory pressure")
        } catch (e: Exception) {
            if (AppConfig.IS_DEBUG) {
                Log.e(TAG, "Error trimming image cache", e)
            }
        }
    }
    
    /**
     * Completely clears the image cache
     * Used when low memory is detected or when user manually clears cache
     */
    fun clearImageCache(context: Context) {
        try {
            // Clear the cache key cache
            cacheKeyCache.clear()
            
            // Clear preload tracker
            CoroutineScope(Dispatchers.IO).launch {
                preloadTracker.clear()
            }
            
            // Clear the preload queue
            preloadQueue.clear()
            
            // Reset counters
            preloadCount.set(0)
            totalImagesViewed.set(0)
            totalDetailViews.set(0)
            
            // If we have a cached image loader, clear its caches
            cachedImageLoader?.let { loader ->
                // Clear memory cache
                loader.memoryCache?.clear()
                
                // Clear disk cache asynchronously
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        // Use reflection to access and clear disk cache
                        val diskCacheField = loader.javaClass.getDeclaredField("diskCache")
                        diskCacheField.isAccessible = true
                        val diskCache = diskCacheField.get(loader) as? DiskCache
                        diskCache?.clear()
                    } catch (e: Exception) {
                        // Fallback: create a new image loader
                        imageLoaderMutex.withLock {
                            cachedImageLoader = null
                        }
                    }
                }
            }
            
            // Reset the cached image loader to force recreation
            CoroutineScope(Dispatchers.IO).launch {
                imageLoaderMutex.withLock {
                    cachedImageLoader = null
                }
            }
            
            log("Image cache completely cleared")
        } catch (e: Exception) {
            if (AppConfig.IS_DEBUG) {
                Log.e(TAG, "Error clearing image cache", e)
            }
        }
    }

}
