package com.droidates.wallpapers.utils

import android.content.Context
import android.util.Log
import com.droidates.wallpapers.model.Wallpaper
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.lang.reflect.Type
import java.io.File
import java.io.IOException
import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Disk cache for wallpapers to eliminate lag when reopening app after being cleared from recents
 * 
 * CRITICAL OPTIMIZATION: This class provides immediate data access during app restart
 * by maintaining a persistent cache of wallpaper data on disk.
 * 
 * OPTIMIZATION: Implemented as a singleton to prevent multiple instances and redundant loading
 */
class WallpaperDiskCache private constructor(private val appContext: Context) {
    companion object {
        private const val TAG = "WallpaperDiskCache"
        private const val VERBOSE_LOGGING = false // DISABLE EXCESSIVE LOGGING - causing launch lag
        private const val CACHE_FILE = "wallpapers_cache.json"

        // TTL for per-collection caches: 1 hour. Avoids stale data while cutting re-fetches.
        private const val COLLECTION_CACHE_TTL_MS = 60 * 60 * 1000L // 1 hour

        @Volatile
        private var INSTANCE: WallpaperDiskCache? = null

        // Memory cache to prevent repeated disk reads
        @Volatile
        private var memoryCache: List<Wallpaper>? = null

        // Per-collection in-memory caches: collectionKey -> (wallpapers, savedAtMs)
        private val collectionMemoryCache = HashMap<String, Pair<List<Wallpaper>, Long>>()

        fun getInstance(context: Context): WallpaperDiskCache {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: WallpaperDiskCache(context.applicationContext).also { INSTANCE = it }
            }
        }

        // Clear the static memory cache (useful for testing or forced refresh)
        fun clearMemoryCache() {
            synchronized(this) {
                memoryCache = null
                collectionMemoryCache.clear()
                Log.d(TAG, "Memory cache cleared")
            }
        }
    }
    // OPTIMIZATION: Dedicated single thread for file operations to avoid blocking other threads
    private val diskDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    
    // Coroutine scope for background operations with SupervisorJob to prevent cancellation propagation
    private val backgroundScope = CoroutineScope(SupervisorJob() + diskDispatcher)
    private val gson = Gson()
    
    // OPTIMIZATION: Track cache hit/miss metrics
    private var cacheHits = 0
    private var cacheMisses = 0
    
    fun getCacheStats(): String {
        val hitRate = if (cacheHits + cacheMisses > 0) {
            (cacheHits * 100 / (cacheHits + cacheMisses))
        } else {
            0
        }
        return "Cache stats - Hits: $cacheHits, Misses: $cacheMisses, Hit rate: ${hitRate}%"
    }
    
    /**
     * Save wallpapers to both memory and disk cache asynchronously
     * @param wallpapers The wallpapers to cache
     */
    suspend fun saveWallpapers(wallpapers: List<Wallpaper>) {
        if (wallpapers.isEmpty()) return
        
        // Update memory cache immediately 
        synchronized(Companion) {
            memoryCache = wallpapers.toList()
            // Log.d(TAG, "Updated memory cache with ${wallpapers.size} wallpapers")
        }
        
        // Save to disk asynchronously
        withContext(diskDispatcher) {
            try {
                val json = gson.toJson(wallpapers)
                val file = File(appContext.cacheDir, CACHE_FILE)
                file.writeText(json)
                // Log.d(TAG, "Saved ${wallpapers.size} wallpapers to disk cache")
            } catch (e: IOException) {
                Log.e(TAG, "Failed to save wallpapers to disk cache", e)
            }
        }
    }
    
    /**
     * Load wallpapers from memory cache first, falling back to disk cache
     * OPTIMIZATION: This method should NOT be called from the main thread to avoid StrictMode violations
     * @return The cached wallpapers or null if no cache exists
     */
    fun loadWallpapersSync(): List<Wallpaper>? {
        // Check memory cache first for instant access
        synchronized(Companion) {
            memoryCache?.let {
                cacheHits++
                // Log.d(TAG, "Using memory cache: ${it.size} wallpapers")
                return it
            }
        }
        
        cacheMisses++
        
        // Fall back to disk cache
        try {
            val file = File(appContext.cacheDir, CACHE_FILE)
            if (!file.exists() || file.length() == 0L) {
                // Log.d(TAG, "No wallpaper cache file found")
                return null
            }
            
            val json = file.readText()
            val type: Type = object : TypeToken<List<Wallpaper>>() {}.type
            val wallpapers: List<Wallpaper> = gson.fromJson(json, type)
            
            // Update memory cache for future access
            synchronized(Companion) {
                memoryCache = wallpapers
            }
            
            // Log.d(TAG, "Loaded ${wallpapers.size} wallpapers from disk cache")
            return wallpapers
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load wallpapers from disk cache", e)
            
            // If there was a problem with the cache file, delete it to avoid future issues
            try {
                val file = File(appContext.cacheDir, CACHE_FILE)
                if (file.exists()) {
                    val deleted = file.delete()
                    if (deleted) {
                        // Log.d(TAG, "Deleted corrupted cache file")
                    } else {
                        Log.w(TAG, "Failed to delete corrupted cache file")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete corrupted cache file", e)
            }
            
            return null
        }
    }
    
    /**
     * Load wallpapers asynchronously from cache (memory first, then disk)
     * OPTIMIZATION: This should be the preferred method for main thread calls
     * @param callback Function to receive the loaded wallpapers
     */
    fun loadWallpapersAsync(callback: (List<Wallpaper>?) -> Unit) {
        // Check memory cache first - can safely return on main thread
        synchronized(Companion) {
            memoryCache?.let {
                cacheHits++
                // Log.d(TAG, "[ASYNC] Using memory cache: ${it.size} wallpapers")
                callback(it)
                return
            }
        }
        
        cacheMisses++
        
        // No memory cache, load from disk using coroutines
        backgroundScope.launch {
            try {
                val wallpapers = loadWallpapersSync()
                callback(wallpapers)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load wallpapers async: ${e.message}")
                callback(null)
            }
        }
    }
    
    // -------------------------------------------------------------------------
    // Per-collection cache — used by HomeViewModel / TrendingViewModel
    // -------------------------------------------------------------------------

    /**
     * Cache the first page of wallpapers for a specific Firestore collection.
     * Only the first page (initial load) is cached — pagination pages are NOT
     * cached because they depend on a cursor document that changes per-session.
     *
     * @param collectionKey  A short stable key, e.g. "home" or "trending"
     * @param wallpapers     The wallpapers to persist (first page)
     */
    suspend fun saveCollectionPage(collectionKey: String, wallpapers: List<Wallpaper>) {
        if (wallpapers.isEmpty()) return
        val nowMs = System.currentTimeMillis()

        // Update memory cache immediately
        synchronized(Companion) {
            collectionMemoryCache[collectionKey] = Pair(wallpapers.toList(), nowMs)
        }

        // Persist to disk asynchronously
        withContext(diskDispatcher) {
            try {
                val wrapper = CollectionCacheWrapper(wallpapers, nowMs)
                val json = gson.toJson(wrapper)
                File(appContext.cacheDir, "wallpapers_${collectionKey}.json").writeText(json)
            } catch (e: IOException) {
                Log.e(TAG, "Failed to save collection cache for $collectionKey", e)
            }
        }
    }

    /**
     * Load the cached first page for a collection.
     * Returns null if no cache exists or the cache is older than [COLLECTION_CACHE_TTL_MS].
     *
     * @param collectionKey  Must match the key used in [saveCollectionPage]
     */
    fun loadCollectionPage(collectionKey: String): List<Wallpaper>? {
        val nowMs = System.currentTimeMillis()

        // Memory cache first
        synchronized(Companion) {
            collectionMemoryCache[collectionKey]?.let { (cached, savedAt) ->
                if (nowMs - savedAt < COLLECTION_CACHE_TTL_MS) {
                    return cached
                } else {
                    collectionMemoryCache.remove(collectionKey)
                }
            }
        }

        // Disk cache fallback
        return try {
            val file = File(appContext.cacheDir, "wallpapers_${collectionKey}.json")
            if (!file.exists() || file.length() == 0L) return null

            val wrapper = gson.fromJson(file.readText(), CollectionCacheWrapper::class.java)
                ?: return null

            if (nowMs - wrapper.savedAtMs >= COLLECTION_CACHE_TTL_MS) {
                file.delete()
                return null
            }

            val type: java.lang.reflect.Type =
                object : TypeToken<List<Wallpaper>>() {}.type
            val wallpapers: List<Wallpaper> = gson.fromJson(gson.toJson(wrapper.wallpapers), type)

            // Warm memory cache
            synchronized(Companion) {
                collectionMemoryCache[collectionKey] = Pair(wallpapers, wrapper.savedAtMs)
            }
            wallpapers
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load collection cache for $collectionKey", e)
            try { File(appContext.cacheDir, "wallpapers_${collectionKey}.json").delete() } catch (_: Exception) {}
            null
        }
    }

    /** Invalidate the per-collection cache (e.g. on pull-to-refresh or sort change) */
    fun invalidateCollectionCache(collectionKey: String) {
        synchronized(Companion) { collectionMemoryCache.remove(collectionKey) }
        try { File(appContext.cacheDir, "wallpapers_${collectionKey}.json").delete() } catch (_: Exception) {}
    }

    /** Simple wrapper stored on disk so we can check the TTL without loading all wallpapers */
    private data class CollectionCacheWrapper(
        val wallpapers: List<Wallpaper>,
        val savedAtMs: Long
    )

    /**
     * Clear both the memory and disk cache
     */
    suspend fun clearCache() {
        // Clear memory cache immediately
        synchronized(Companion) {
            memoryCache = null
            // Log.d(TAG, "Cleared memory cache")
        }
        
        // Clear disk cache asynchronously
        withContext(diskDispatcher) {
            try {
                val file = File(appContext.cacheDir, CACHE_FILE)
                if (file.exists()) {
                    val deleted = file.delete()
                    if (deleted) {
                        // Log.d(TAG, "Cleared wallpaper disk cache")
                    } else {
                        Log.w(TAG, "Failed to clear wallpaper disk cache")
                    }
                } else {
                    // Log.d(TAG, "No cache file exists to clear")
                }
                
                // Reset stats
                cacheHits = 0
                cacheMisses = 0
            } catch (e: Exception) {
                Log.e(TAG, "Failed to clear wallpaper disk cache", e)
            }
        }
    }
    
    /**
     * Check if a cache file exists without loading its contents
     * This is a lightweight operation that can be called from any thread
     */
    fun cacheFileExists(): Boolean {
        return try {
            val file = File(appContext.cacheDir, CACHE_FILE)
            file.exists() && file.length() > 0
        } catch (e: Exception) {
            Log.e(TAG, "Error checking cache file existence: ${e.message}")
            false
        }
    }
    
    /**
     * Preload cache into memory on a background thread
     * This should be called during application initialization
     */
    fun preloadCache() {
        if (memoryCache != null) {
            // Log.d(TAG, "Cache already preloaded in memory")
            return
        }
        
        backgroundScope.launch {
            try {
                loadWallpapersSync()?.let {
                    // Log.d(TAG, "Successfully preloaded ${it.size} wallpapers into memory cache")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to preload cache: ${e.message}")
            }
        }
    }

}
