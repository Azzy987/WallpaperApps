package com.droidates.wallpapers.core.di

import android.content.Context
import com.droidates.wallpapers.core.utils.SystemServiceCache
import com.droidates.wallpapers.core.utils.PerformanceMonitor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Dagger Hilt module for providing system service related dependencies
 */
@Module
@InstallIn(SingletonComponent::class)
object SystemServiceModule {
    
    /**
     * Provides a singleton instance of SystemServiceCache
     * This ensures we only have one instance of the cache throughout the app
     */
    @Provides
    @Singleton
    fun provideSystemServiceCache(@ApplicationContext context: Context): SystemServiceCache {
        return SystemServiceCache(context)
    }

    /**
     * Provides a singleton instance of PerformanceMonitor
     * This tracks startup performance and frame skipping metrics
     */
    @Provides
    @Singleton
    fun providePerformanceMonitor(): PerformanceMonitor {
        return PerformanceMonitor()
    }
}
