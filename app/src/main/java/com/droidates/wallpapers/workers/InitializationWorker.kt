package com.droidates.wallpapers.workers

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.droidates.wallpapers.BuildConfig
import com.google.android.gms.ads.MobileAds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private const val TAG = "InitializationWorker"

/**
 * Simplified background worker that handles basic heavy initialization tasks
 * This eliminates complex dependencies to prevent WorkManager constructor issues
 */
class InitializationWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        return@withContext try {
            Log.d(TAG, "Starting simplified background initialization")

            // Initialize AdMob in background - this is the main heavy task
            initializeAdMob()

            // Add a small delay to prevent blocking main thread
            delay(1000)

            Log.d(TAG, "Simplified background initialization completed successfully")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Background initialization failed", e)
            // Return retry for transient failures, failure for permanent ones
            if (e is OutOfMemoryError || runAttemptCount >= 3) {
                Result.failure()
            } else {
                Result.retry()
            }
        }
    }

    /**
     * Initialize AdMob SDK in background to avoid blocking main thread
     * ANR FIX: Use Handler instead of withContext to prevent blocking
     */
    private suspend fun initializeAdMob() {
        try {
            Log.d(TAG, "Initializing AdMob SDK in background")
            // ANR FIX: Post to main thread without blocking using Handler
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                MobileAds.initialize(applicationContext) { initStatus ->
                    Log.d(TAG, "AdMob initialization completed: ${initStatus.adapterStatusMap}")
                }
            }

            // Add a small delay to ensure initialization is complete
            delay(500)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize AdMob", e)
            throw e
        }
    }

}