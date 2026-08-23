package com.droidates.wallpapers.core.utils

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.droidates.wallpapers.core.utils.MobileAdsInitializer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.util.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withTimeout

/**
 * Ultra-conservative background initialization for lower-end devices
 * Ensures no interference with UI smoothness even on 60Hz/6GB RAM devices
 */
class BackgroundInitializationWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val TAG = "BackgroundInitWorker"
        const val WORK_NAME = "background_initialization"
        
        private const val VERBOSE_LOGGING = false
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            if (VERBOSE_LOGGING) {
                Log.d(TAG, "Starting ultra-conservative background initialization for lower-end devices")
            }
            
            delay(5000) // Extra 5 seconds on top of the 15-second WorkManager delay
            
            val availableMemory = Runtime.getRuntime().freeMemory()
            val totalMemory = Runtime.getRuntime().totalMemory()
            val memoryUsagePercent = ((totalMemory - availableMemory).toFloat() / totalMemory) * 100
            
            if (memoryUsagePercent > 80f) {
                if (VERBOSE_LOGGING) {
                    Log.w(TAG, "High memory usage detected ($memoryUsagePercent%), deferring initialization")
                }
                // Return retry to try again later when memory pressure is lower
                return@withContext Result.retry()
            }
            
            withTimeout(30000) { // 30 second timeout to prevent hanging
                if (VERBOSE_LOGGING) {
                    Log.d(TAG, "Initializing MobileAds SDK with conservative settings...")
                }
                
                // Use NonCancellable to ensure completion once started
                withContext(NonCancellable) {
                    MobileAdsInitializer.ensureInitialized(applicationContext)
                    if (VERBOSE_LOGGING) {
                        Log.d(TAG, "MobileAds initialization completed")
                    }
                }
                
                delay(2000)
            }
            
            if (VERBOSE_LOGGING) {
                Log.d(TAG, "Ultra-conservative background initialization completed successfully")
            }
            
            Result.success()
        } catch (e: Exception) {
            if (VERBOSE_LOGGING) {
                Log.e(TAG, "Background initialization failed", e)
            }
            
            // This ensures eventual initialization without blocking the app
            Result.retry()
        }
    }
} 
