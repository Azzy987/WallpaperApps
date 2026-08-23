package com.droidates.wallpapers.core.utils

import android.util.Log
import androidx.compose.foundation.lazy.grid.LazyGridState
import kotlinx.coroutines.delay


object PaginationUtils {
    private const val LOAD_THRESHOLD = 10  // Increased threshold to load earlier when scrolling
    const val PAGE_SIZE = 20
    
    // PERFORMANCE OPTIMIZATION: Initial page size for faster startup
    const val INITIAL_PAGE_SIZE = 20  // Optimal initial load for balance between speed and content
    
    // PERFORMANCE OPTIMIZATION: Use smaller prefetch size to reduce memory usage
    // This is especially important for 4GB RAM devices
    const val PREFETCH_SIZE = 14  // Smaller prefetch size for better performance

    // Debounce is tracked per-list, not globally across the whole app.
    private val lastLoadTimeByKey = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private const val DEBOUNCE_TIME_MS = 300L  // 300ms debounce period for faster response

    fun shouldLoadMore(
        debounceKey: String,
        gridState: LazyGridState,
        itemCount: Int,
        isLoading: Boolean,
        hasReachedEnd: Boolean
    ): Boolean {
        val currentTime = System.currentTimeMillis()
        val lastLoadTime = lastLoadTimeByKey[debounceKey] ?: 0L
        
        // Skip check if we're within debounce period
        if (currentTime - lastLoadTime < DEBOUNCE_TIME_MS) {
            return false
        }

        // Don't try to load more if already loading
        if (isLoading) {
            return false
        }
        
        // If we've reached the end, don't load more
        if (hasReachedEnd) {
            return false
        }

        // If the list is empty, there's nothing to paginate
        if (itemCount == 0) {
            return false
        }
        
        // For small lists, don't trigger pagination unless scrolled to the end
        if (itemCount <= LOAD_THRESHOLD) {
            val layoutInfo = gridState.layoutInfo
            val lastVisibleItemIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            
            // Only load more if actually at the end of a small list
            val shouldLoad = lastVisibleItemIndex >= itemCount - 1
            
            if (shouldLoad) {
                lastLoadTimeByKey[debounceKey] = currentTime
            }
            return shouldLoad
        }

        // Standard pagination for larger lists
        val lastVisibleItem = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
        val shouldLoad = lastVisibleItem >= itemCount - LOAD_THRESHOLD

        if (shouldLoad) {
            lastLoadTimeByKey[debounceKey] = currentTime  // Update last load time
        }
        return shouldLoad
    }
}
