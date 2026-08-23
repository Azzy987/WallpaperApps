package com.droidates.wallpapers.core.utils

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * ScrollOptimizer stub to remove unnecessary micro-scroll overhead and initial delays.
 */
object ScrollOptimizer {
    
    @Composable
    fun OptimizedLazyGridState(
        initialFirstVisibleItemIndex: Int = 0,
        initialFirstVisibleItemScrollOffset: Int = 0
    ): LazyGridState {
        return remember {
            LazyGridState(
                firstVisibleItemIndex = initialFirstVisibleItemIndex,
                firstVisibleItemScrollOffset = initialFirstVisibleItemScrollOffset
            )
        }
    }
    
    fun resetWarmUpState() {
        // No-op
    }
    
    fun isScrollSystemWarmedUp(): Boolean {
        return true
    }
}