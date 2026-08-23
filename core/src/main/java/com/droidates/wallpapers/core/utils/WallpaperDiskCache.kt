package com.droidates.wallpapers.core.utils

import android.content.Context
import com.droidates.wallpapers.core.model.Wallpaper

/**
 * Simplified memory-only WallpaperDiskCache to eliminate slow disk/JSON serialization latency
 */
class WallpaperDiskCache private constructor(private val appContext: Context) {
    companion object {
        private const val TAG = "WallpaperDiskCache"

        @Volatile
        private var INSTANCE: WallpaperDiskCache? = null

        @Volatile
        private var memoryCache: List<Wallpaper>? = null

        private val collectionMemoryCache = HashMap<String, Pair<List<Wallpaper>, Long>>()

        fun getInstance(context: Context): WallpaperDiskCache {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: WallpaperDiskCache(context.applicationContext).also { INSTANCE = it }
            }
        }

        fun clearMemoryCache() {
            synchronized(this) {
                memoryCache = null
                collectionMemoryCache.clear()
            }
        }
    }
    
    fun getCacheStats(): String {
        return "Memory Cache Active"
    }
    
    suspend fun saveWallpapers(wallpapers: List<Wallpaper>) {
        synchronized(Companion) {
            memoryCache = wallpapers.toList()
        }
    }
    
    fun loadWallpapersSync(): List<Wallpaper>? {
        synchronized(Companion) {
            return memoryCache
        }
    }
    
    fun loadWallpapersAsync(callback: (List<Wallpaper>?) -> Unit) {
        synchronized(Companion) {
            callback(memoryCache)
        }
    }
    
    suspend fun saveCollectionPage(collectionKey: String, wallpapers: List<Wallpaper>) {
        val nowMs = System.currentTimeMillis()
        synchronized(Companion) {
            collectionMemoryCache[collectionKey] = Pair(wallpapers.toList(), nowMs)
        }
    }

    fun loadCollectionPage(collectionKey: String): List<Wallpaper>? {
        val nowMs = System.currentTimeMillis()
        synchronized(Companion) {
            collectionMemoryCache[collectionKey]?.let { (cached, savedAt) ->
                if (nowMs - savedAt < 60 * 60 * 1000L) {
                    return cached
                } else {
                    collectionMemoryCache.remove(collectionKey)
                }
            }
        }
        return null
    }

    fun invalidateCollectionCache(collectionKey: String) {
        synchronized(Companion) {
            collectionMemoryCache.remove(collectionKey)
        }
    }

    suspend fun clearCache() {
        synchronized(Companion) {
            memoryCache = null
            collectionMemoryCache.clear()
        }
    }
    
    fun cacheFileExists(): Boolean {
        return false
    }
    
    fun preloadCache() {
        // No-op
    }
}
