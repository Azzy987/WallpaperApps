package com.droidates.wallpapers.core.utils

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Performance optimization utilities for smooth app experience across all devices
 */
object PerformanceOptimizations {
    
    // Device performance categories
    enum class DevicePerformance {
        HIGH_END,    // Flagship devices
        MID_RANGE,   // Most Android devices
        LOW_END      // Entry-level devices
    }
    
    /**
     * Determine device performance category based on RAM and CPU
     */
    fun getDevicePerformance(context: Context): DevicePerformance {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        
        val totalRamMB = memoryInfo.totalMem / (1024 * 1024)
        
        return when {
            totalRamMB >= 8192 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> DevicePerformance.HIGH_END
            totalRamMB >= 4096 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> DevicePerformance.MID_RANGE
            else -> DevicePerformance.LOW_END
        }
    }
    
    /**
     * Get optimal grid column count based on device performance
     */
    fun getOptimalColumnCount(performance: DevicePerformance, isTablet: Boolean = false): Int {
        return when {
            isTablet -> if (performance == DevicePerformance.HIGH_END) 4 else 3
            performance == DevicePerformance.LOW_END -> 2
            else -> 2
        }
    }
    
    /**
     * Get optimal image cache size based on device RAM
     */
    fun getOptimalCacheSize(performance: DevicePerformance): Pair<Double, Long> {
        return when (performance) {
            DevicePerformance.HIGH_END -> Pair(0.15, 100L * 1024 * 1024) // 15% RAM, 100MB disk
            DevicePerformance.MID_RANGE -> Pair(0.12, 75L * 1024 * 1024) // 12% RAM, 75MB disk
            DevicePerformance.LOW_END -> Pair(0.08, 50L * 1024 * 1024)   // 8% RAM, 50MB disk
        }
    }
    
    /**
     * Get optimal scroll buffer size for LazyGrids
     */
    fun getOptimalBufferSize(performance: DevicePerformance): Int {
        return when (performance) {
            DevicePerformance.HIGH_END -> 20    // Load more items ahead
            DevicePerformance.MID_RANGE -> 15   // Moderate buffering
            DevicePerformance.LOW_END -> 10     // Minimal buffering
        }
    }
    
    /**
     * Get optimal animation duration based on device performance
     */
    fun getOptimalAnimationDuration(performance: DevicePerformance): Int {
        return when (performance) {
            DevicePerformance.HIGH_END -> 300   // Smooth animations
            DevicePerformance.MID_RANGE -> 200  // Moderate animations
            DevicePerformance.LOW_END -> 150    // Quick animations
        }
    }
    
    /**
     * Should use hardware acceleration for this device
     */
    fun shouldUseHardwareAcceleration(performance: DevicePerformance): Boolean {
        return performance != DevicePerformance.LOW_END
    }
    
    /**
     * Get optimal crossfade duration for images
     */
    fun getOptimalCrossfadeDuration(performance: DevicePerformance): Int {
        return when (performance) {
            DevicePerformance.HIGH_END -> 300
            DevicePerformance.MID_RANGE -> 200
            DevicePerformance.LOW_END -> 100
        }
    }
}

/**
 * Composable helper to get device performance
 */
@Composable
fun rememberDevicePerformance(): PerformanceOptimizations.DevicePerformance {
    val context = LocalContext.current
    return remember {
        PerformanceOptimizations.getDevicePerformance(context)
    }
}