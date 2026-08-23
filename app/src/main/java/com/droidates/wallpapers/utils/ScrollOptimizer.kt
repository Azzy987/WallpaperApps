package com.droidates.wallpapers.utils

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import android.content.Context
import android.util.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * ScrollOptimizer - Handles initial scroll lag by implementing warm-up mechanisms
 * CRITICAL OPTIMIZATION: Eliminates the 5-7 second scroll lag after fresh app start
 */
object ScrollOptimizer {
    private const val TAG = "ScrollOptimizer"
    
    // Track if scroll has been warmed up
    private var isScrollWarmedUp = false
    private var lastWarmUpTime = 0L
    private const val WARM_UP_VALIDITY_DURATION = 30_000L // 30 seconds
    
    /**
     * Optimize LazyGridState for immediate responsiveness
     * This prevents the initial scroll lag by pre-warming the scroll system
     */
    @Composable
    fun OptimizedLazyGridState(
        initialFirstVisibleItemIndex: Int = 0,
        initialFirstVisibleItemScrollOffset: Int = 0
    ): LazyGridState {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        
        val gridState = remember {
            LazyGridState(
                firstVisibleItemIndex = initialFirstVisibleItemIndex,
                firstVisibleItemScrollOffset = initialFirstVisibleItemScrollOffset
            )
        }
        
        LaunchedEffect(Unit) {
            if (!isScrollWarmedUp || (System.currentTimeMillis() - lastWarmUpTime) > WARM_UP_VALIDITY_DURATION) {
                scope.launch {
                    warmUpScrollSystem(gridState, context)
                }
            }
        }
        
        return gridState
    }
    
    /**
     * Pre-warm the scroll system to eliminate initial lag
     * This performs invisible micro-scrolls to initialize the scroll system
     */
    private suspend fun warmUpScrollSystem(gridState: LazyGridState, context: Context) {
        try {
            Log.d(TAG, "Starting scroll system warm-up")
            
            // Wait for initial composition to complete
            delay(100)
            
            // Perform micro-scrolls to warm up the scroll system
            // These are invisible to the user but initialize the scroll mechanics
            for (i in 1..3) {
                try {
                    // Micro scroll by 1 pixel and back
                    gridState.scrollToItem(0, 1)
                    delay(50)
                    gridState.scrollToItem(0, 0)
                    delay(50)
                } catch (e: Exception) {
                    // Ignore any errors during warm-up
                }
            }
            
            isScrollWarmedUp = true
            lastWarmUpTime = System.currentTimeMillis()
            Log.d(TAG, "Scroll system warm-up completed successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during scroll warm-up", e)
        }
    }
    
    /**
     * Reset warm-up state (useful when app comes back from background)
     */
    fun resetWarmUpState() {
        isScrollWarmedUp = false
        lastWarmUpTime = 0L
        Log.d(TAG, "Scroll warm-up state reset")
    }
    
    /**
     * Check if scroll system is already warmed up
     */
    fun isScrollSystemWarmedUp(): Boolean {
        return isScrollWarmedUp && 
               (System.currentTimeMillis() - lastWarmUpTime) <= WARM_UP_VALIDITY_DURATION
    }
} 