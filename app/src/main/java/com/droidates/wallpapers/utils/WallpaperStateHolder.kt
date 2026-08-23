package com.droidates.wallpapers.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import com.droidates.wallpapers.model.Wallpaper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Centralized state holder for wallpaper data
 * This class helps prevent duplicate subscriptions to the same data sources
 * and provides optimized state management for Compose UI
 */
class WallpaperStateHolder private constructor() {
    
    // Singleton instance
    companion object {
        @Volatile
        private var instance: WallpaperStateHolder? = null
        
        fun getInstance(): WallpaperStateHolder {
            return instance ?: synchronized(this) {
                instance ?: WallpaperStateHolder().also { instance = it }
            }
        }
    }
    
    // Cache of active subscriptions to prevent duplicate subscriptions
    private val activeSubscriptions = mutableMapOf<String, StateFlow<*>>()
    
    /**
     * Get a cached state flow or create a new one if it doesn't exist
     * This prevents duplicate subscriptions to the same data source
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> getOrCreateStateFlow(key: String, createFlow: () -> StateFlow<T>): StateFlow<T> {
        return synchronized(activeSubscriptions) {
            (activeSubscriptions[key] as? StateFlow<T>) ?: createFlow().also {
                activeSubscriptions[key] = it
            }
        }
    }
    
    /**
     * Release a subscription when it's no longer needed
     */
    fun releaseSubscription(key: String) {
        synchronized(activeSubscriptions) {
            activeSubscriptions.remove(key)
        }
    }
}

/**
 * Composable function to collect a Flow as state with optimized recomposition behavior
 * This function also manages the lifecycle of the subscription
 */
@Composable
fun <T> Flow<T>.collectAsOptimizedState(key: String, initial: T): State<T> {
    val stateHolder = remember { WallpaperStateHolder.getInstance() }
    
    // Remember the flow to prevent recreating it on recomposition
    val flow = remember(key) {
        this
    }
    
    // Collect the flow as state
    val state = flow.collectAsState(initial = initial)
    
    // Clean up the subscription when the composable leaves the composition
    DisposableEffect(key) {
        onDispose {
            stateHolder.releaseSubscription(key)
        }
    }
    
    return state
}

/**
 * Extension function to collect a boolean value with optimized recomposition
 */
@Composable
fun Flow<Boolean>.collectAsOptimizedState(key: String, initial: Boolean): State<Boolean> {
    return collectAsOptimizedState(key, initial)
}

/**
 * Extension function to collect a list of wallpapers with optimized recomposition
 */
@Composable
fun Flow<List<Wallpaper>>.collectAsOptimizedWallpaperState(key: String): State<List<Wallpaper>> {
    return collectAsOptimizedState(key, emptyList())
}
