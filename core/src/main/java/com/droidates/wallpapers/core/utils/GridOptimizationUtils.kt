package com.droidates.wallpapers.core.utils

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Utility class for optimizing LazyGrid performance
 */
object GridOptimizationUtils {
    
    private const val TAG = "GridOptimization"
    private const val VERBOSE_LOGGING = false // DISABLE EXCESSIVE LOGGING - causing launch lag
    
    /**
     * Determines the optimal prefetch distance based on device capabilities
     * Lower-end devices (4GB RAM or less) will use a smaller prefetch distance
     * to reduce memory pressure
     */
    @Composable
    fun rememberOptimalPrefetchDistance(): Int {
        val context = LocalContext.current
        
        return remember {
            calculateOptimalPrefetchDistance(context)
        }
    }
    
    /**
     * Calculates the optimal prefetch distance based on device memory
     * @return Prefetch distance in items
     */
    private fun calculateOptimalPrefetchDistance(context: Context): Int {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        
        // Get total RAM in MB
        val totalRam = memoryInfo.totalMem / (1024 * 1024)
        
        // Log the device memory for debugging
        if (VERBOSE_LOGGING) {
        Log.d(TAG, "Device total RAM: $totalRam MB")
        }
        
        return when {
            // For low-end devices (4GB or less)
            totalRam <= 4 * 1024 -> {
                if (VERBOSE_LOGGING) {
                Log.d(TAG, "Using conservative prefetch distance for low-memory device")
                }
                2 // Conservative prefetch
            }
            // For mid-range devices (4-6GB)
            totalRam <= 6 * 1024 -> {
                if (VERBOSE_LOGGING) {
                Log.d(TAG, "Using moderate prefetch distance for mid-range device")
                }
                4 // Moderate prefetch
            }
            // For high-end devices (more than 6GB)
            else -> {
                if (VERBOSE_LOGGING) {
                Log.d(TAG, "Using aggressive prefetch distance for high-memory device")
                }
                6 // Aggressive prefetch
            }
        }
    }
    
    /**
     * Calculates the fixed item height for wallpaper grid items based on screen width
     * This helps eliminate remeasure operations during scrolling
     * @param columnCount Number of columns in the grid
     * @param aspectRatio Desired aspect ratio of grid items (width/height)
     * @return Fixed height for grid items
     */
    @Composable
    fun calculateFixedItemHeight(
        columnCount: Int = 2,
        aspectRatio: Float = 0.75f, // Default aspect ratio (width/height)
        horizontalSpacing: Dp = 8.dp
    ): Dp {
        val context = LocalContext.current
        val density = LocalDensity.current
        
        return remember(columnCount, aspectRatio, horizontalSpacing) {
            // Get screen width in pixels
            val displayMetrics = context.resources.displayMetrics
            val screenWidthPx = displayMetrics.widthPixels
            
            // Calculate available width per item (accounting for spacing)
            val spacingPx = with(density) { horizontalSpacing.toPx() * (columnCount - 1) }
            val itemWidthPx = (screenWidthPx - spacingPx) / columnCount
            
            // Calculate height based on aspect ratio
            val itemHeightPx = itemWidthPx / aspectRatio
            
            // Convert back to Dp
            with(density) { itemHeightPx.toDp() }
        }
    }
    
    /**
     * Creates fixed-size grid cells with pre-calculated heights to eliminate remeasure operations
     * @param columnCount Number of columns in the grid
     * @param aspectRatio Desired aspect ratio of grid items (width/height)
     * @return GridCells.Fixed with optimized parameters
     */
    @Composable
    fun fixedSizeGridCells(columnCount: Int = 2): GridCells.Fixed {
        return remember(columnCount) {
            GridCells.Fixed(columnCount)
        }
    }
}
