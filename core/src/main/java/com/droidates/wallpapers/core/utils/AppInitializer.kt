package com.droidates.wallpapers.core.utils

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Singleton
import javax.inject.Inject

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
        preferencesManager: PreferencesManager
    ): AppInitializer {
        return AppInitializer(context, preferencesManager)
    }
}

/**
 * Simplified AppInitializer that contains core cache interface definitions
 * Heavy initializations are deferred or handled by InitializationWorker
 */
@Singleton
class AppInitializer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferencesManager: PreferencesManager
) {
    fun initializeComponents() {
        // No-op: all heavy initializations are deferred or handled by InitializationWorker/Hilt lazily
    }
    
    fun cleanup() {
        try {
            preferencesManager.cleanup()
        } catch (e: Exception) {
            // Ignore cleanup exceptions during process termination
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

