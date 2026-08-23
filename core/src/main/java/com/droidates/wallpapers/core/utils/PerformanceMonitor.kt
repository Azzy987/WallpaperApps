package com.droidates.wallpapers.core.utils

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Simplified PerformanceMonitor stub to eliminate Choreographer frame callback overhead.
 */
@Singleton
class PerformanceMonitor @Inject constructor() {
    fun startStartupMonitoring() {
        // No-op
    }

    fun shutdown() {
        // No-op
    }

    fun logMemoryUsage(context: String) {
        // No-op
    }
}

