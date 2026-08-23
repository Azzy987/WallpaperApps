package com.droidates.wallpapers.utils

import android.os.Build
import android.util.Log
import android.view.Choreographer
import androidx.annotation.RequiresApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Performance monitoring utility to track startup metrics and frame skipping
 * Helps identify performance bottlenecks during app startup
 */
@Singleton
class PerformanceMonitor @Inject constructor() {

    private var isMonitoring = false
    private var startTime = 0L
    private var frameCallback: Choreographer.FrameCallback? = null

    companion object {
        private const val TAG = "PerformanceMonitor"
        private const val FRAME_THRESHOLD_MS = 16.67 // 60 FPS threshold
        private const val MONITORING_DURATION_MS = 5000L // Monitor for 5 seconds
    }

    /**
     * Start monitoring startup performance
     * Call this from MainActivity.onCreate()
     */
    fun startStartupMonitoring() {
        if (isMonitoring) return

        isMonitoring = true
        startTime = System.currentTimeMillis()

        // Log initial memory state to identify GC pressure
        logMemoryUsage("Startup Begin")

        Log.d(TAG, "Starting startup performance monitoring...")

        // Monitor frame skipping for startup period
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            startFrameMonitoring()
        }

        // Stop monitoring after duration
        GlobalScope.launch(Dispatchers.Main) {
            kotlinx.coroutines.delay(MONITORING_DURATION_MS)
            stopMonitoring()
        }
    }

    @RequiresApi(Build.VERSION_CODES.JELLY_BEAN)
    private fun startFrameMonitoring() {
        var lastFrameTime = System.nanoTime()
        var skippedFrameCount = 0
        var totalFrames = 0

        frameCallback = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                if (!isMonitoring) return

                val currentTime = System.nanoTime()
                val frameDuration = (currentTime - lastFrameTime) / 1_000_000.0 // Convert to ms

                totalFrames++

                if (frameDuration > FRAME_THRESHOLD_MS) {
                    skippedFrameCount++
                    val skippedFrames = (frameDuration / FRAME_THRESHOLD_MS).toInt() - 1

                    if (skippedFrames > 5) { // Only log significant frame skips
                        Log.i(TAG, "Choreographer: Skipped $skippedFrames frames! " +
                                "Frame took ${frameDuration.toInt()}ms (target: ${FRAME_THRESHOLD_MS.toInt()}ms)")
                    }
                }

                lastFrameTime = currentTime

                // Continue monitoring
                if (isMonitoring) {
                    Choreographer.getInstance().postFrameCallback(this)
                }
            }
        }

        Choreographer.getInstance().postFrameCallback(frameCallback!!)
    }

    private fun stopMonitoring() {
        if (!isMonitoring) return

        isMonitoring = false
        val totalTime = System.currentTimeMillis() - startTime

        // Log final memory state to compare GC impact
        logMemoryUsage("Startup Complete")

        Log.d(TAG, "Startup performance monitoring completed after ${totalTime}ms")

        frameCallback?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                Choreographer.getInstance().removeFrameCallback(it)
            }
        }
        frameCallback = null
    }

    /**
     * Log memory usage for performance analysis
     */
    fun logMemoryUsage(context: String) {
        val runtime = Runtime.getRuntime()
        val usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024
        val maxMemory = runtime.maxMemory() / 1024 / 1024
        val freeMemory = runtime.freeMemory() / 1024 / 1024

        Log.d(TAG, "Memory usage [$context]: ${usedMemory}MB used, ${freeMemory}MB free, ${maxMemory}MB max")
    }
}