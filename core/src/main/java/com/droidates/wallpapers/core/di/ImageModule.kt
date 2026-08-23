package com.droidates.wallpapers.core.di

import com.droidates.wallpapers.core.config.AppConfig
import android.content.Context
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import coil.util.DebugLogger
import kotlinx.coroutines.Dispatchers
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ImageModule {

    @Provides
    @Singleton
    fun provideImageLoader(
        @ApplicationContext context: Context
    ): ImageLoader {
        return ImageLoader.Builder(context)
            .memoryCache {
                MemoryCache.Builder(context)
                    .maxSizePercent(0.10) // HWUI FIX: Further reduced to 10% to prevent GC blocking issues
                    .weakReferencesEnabled(true)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("image_cache"))
                    .maxSizeBytes(200 * 1024 * 1024) // ULTRA FAST: Increased to 200MB for maximum disk caching
                    .cleanupDispatcher(Dispatchers.IO.limitedParallelism(1))
                    .build()
            }
            .okHttpClient {
                OkHttpClient.Builder()
                    .connectTimeout(8, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
                    .writeTimeout(15, TimeUnit.SECONDS)
                    .callTimeout(30, TimeUnit.SECONDS)
                    .retryOnConnectionFailure(true)
                    // ULTRA FAST: Minimized connection pool for immediate connections
                    .connectionPool(okhttp3.ConnectionPool(4, 1, TimeUnit.MINUTES))
                    .build()
            }
            .allowHardware(true) // Enable hardware acceleration
            .allowRgb565(false) // Better quality for wallpapers
            .respectCacheHeaders(false) // Ignore cache headers for aggressive caching
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .networkCachePolicy(CachePolicy.ENABLED)
            // PERFORMANCE: Enable crossfade for smoother loading
            .crossfade(150) // Reduced crossfade duration for faster perceived loading
            .dispatcher(Dispatchers.IO.limitedParallelism(3))
            .apply {
                if (AppConfig.IS_DEBUG) {
                    // Completely disable logging for speed
                    // logger(DebugLogger())
                }
            }
            .build()
    }
} 