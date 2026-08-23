package com.droidates.wallpapers.utils

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

object ScrollUtils {
    private const val SHOW_SCROLL_TO_TOP_THRESHOLD = 800

    @Composable
    fun rememberScrollToTopState(gridState: LazyGridState): Boolean {
        val showScrollToTop by remember {
            derivedStateOf {
                gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset > SHOW_SCROLL_TO_TOP_THRESHOLD
            }
        }
        return showScrollToTop
    }

    suspend fun smoothScrollToTop(gridState: LazyGridState) {
        gridState.scrollToItem(
            index = 0,
            scrollOffset = 0
        )
    }

    fun scrollToTop(scope: CoroutineScope, gridState: LazyGridState) {
        scope.launch {
            smoothScrollToTop(gridState)
        }
    }
} 