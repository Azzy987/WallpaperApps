package com.droidates.wallpapers.core.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.droidates.wallpapers.core.utils.MobileAdsInitializer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private const val TAG = "InitializationWorker"

/**
 * Background worker for deferred heavy initialization (FCM, AdMob).
 */
class InitializationWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        return@withContext try {
            Log.d(TAG, "Starting simplified background initialization")

            try {
                // Day-to-day reminders are sent on-device by EngagementNotificationWorker,
                // and the FCM receiver service has been removed. These subscriptions are
                // kept deliberately: they cost nothing, and they keep the install
                // reachable if push is ever re-enabled (e.g. notify-on-new-upload).
                // Every app shares one Firebase project, so "all_users" reaches EVERY
                // wallpaper app — FCM_TOPIC_APP targets just this one.
                val messaging = com.google.firebase.messaging.FirebaseMessaging.getInstance()
                listOf("all_users", com.droidates.wallpapers.core.config.AppConfig.FCM_TOPIC_APP)
                    .forEach { topic ->
                        messaging.subscribeToTopic(topic)
                            .addOnCompleteListener { task ->
                                if (task.isSuccessful) {
                                    Log.d(TAG, "FCM subscription to $topic successful")
                                } else {
                                    Log.e(TAG, "FCM subscription to $topic failed", task.exception)
                                }
                            }
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to subscribe to FCM topic", e)
            }

            initializeAdMob()

            Log.d(TAG, "Simplified background initialization completed successfully")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Background initialization failed", e)
            if (e is OutOfMemoryError || runAttemptCount >= 3) {
                Result.failure()
            } else {
                Result.retry()
            }
        }
    }

    /**
     * Initialize AdMob on a background thread (never post to the main looper).
     */
    private suspend fun initializeAdMob() {
        delay(8_000)
        Log.d(TAG, "Initializing AdMob SDK in background")
        MobileAdsInitializer.ensureInitialized(applicationContext)
    }
}
