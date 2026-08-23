package com.droidates.wallpapers.core.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.droidates.wallpapers.core.MainActivity
import com.droidates.wallpapers.core.R
import com.droidates.wallpapers.core.config.AppConfig
import com.droidates.wallpapers.core.utils.AppIcons
import java.util.concurrent.TimeUnit

/**
 * Periodic re-engagement notification — no server, no Firebase Blaze, no cost.
 *
 * Every few days it posts one rotating message that deep-links straight into the
 * matching category, so the copy and the destination always agree.
 *
 * WorkManager decides the exact moment (Android batches background work to save
 * battery), so treat the interval as "roughly every N days", not a precise clock.
 */
class EngagementNotificationWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            if (!NotificationManagerCompat.from(applicationContext).areNotificationsEnabled()) {
                // User turned notifications off — don't burn work quota retrying.
                return Result.success()
            }

            val prefs = applicationContext.getSharedPreferences(
                AppConfig.PREFS_NAME, Context.MODE_PRIVATE
            )

            // Only skip if the app is open right now-ish; otherwise the reminder fires
            // on schedule. A wider window would mute it for regular users, which
            // defeats the point of a every-2-3-day nudge.
            val lastOpen = prefs.getLong(KEY_LAST_APP_OPEN, 0L)
            if (lastOpen > 0 && System.currentTimeMillis() - lastOpen < QUIET_WINDOW_MS) {
                Log.d(TAG, "App used within the last few hours — skipping this reminder")
                return Result.success()
            }

            val lastShown = prefs.getLong(KEY_LAST_SHOWN, 0L)
            if (lastShown > 0 && System.currentTimeMillis() - lastShown < MIN_GAP_MS) {
                Log.d(TAG, "Last reminder was too recent — skipping")
                return Result.success()
            }

            val shown = prefs.getInt(KEY_SHOWN_COUNT, 0)
            val message = EngagementMessages.next(shown)
            showNotification(message)
            prefs.edit()
                .putInt(KEY_SHOWN_COUNT, shown + 1)
                .putLong(KEY_LAST_SHOWN, System.currentTimeMillis())
                .apply()

            Log.d(TAG, "Posted engagement notification: ${message.id}")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to post engagement notification", e)
            Result.success() // never retry-storm over a notification
        }
    }

    private fun showNotification(message: EngagementMessage) {
        val context = applicationContext
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Wallpaper Suggestions",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Occasional wallpaper ideas picked for you"
            }
            manager.createNotificationChannel(channel)
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(EXTRA_CATEGORY_NAME, message.categoryName)
            putExtra("source", "engagement_notification")
        }

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        // Distinct request code per message, otherwise a cached PendingIntent would
        // reuse the previous category.
        val pendingIntent = PendingIntent.getActivity(
            context, message.id.hashCode(), intent, flags
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(AppIcons.launcher(context))
            .setContentTitle(message.title)
            .setContentText(message.displayBody)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message.displayBody))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            // POST_NOTIFICATIONS revoked between the check above and here.
            Log.w(TAG, "Notification permission denied", e)
        }
    }

    companion object {
        private const val TAG = "EngagementNotification"
        private const val CHANNEL_ID = "wallpaper_suggestions"
        private const val NOTIFICATION_ID = 2001
        private const val WORK_NAME = "engagement_notification_work"

        const val EXTRA_CATEGORY_NAME = "engagement_category"

        private const val KEY_SHOWN_COUNT = "engagement_shown_count"
        const val KEY_LAST_APP_OPEN = "engagement_last_app_open"

        /**
         * Skip only if the app was open very recently — a notification landing while
         * someone is already browsing is just noise.
         */
        private val QUIET_WINDOW_MS = TimeUnit.HOURS.toMillis(6)

        /** Cadence of the reminder. WorkManager treats this as approximate. */
        private const val INTERVAL_DAYS = 2L

        /**
         * WorkManager may fire a periodic worker early within its flex window; this
         * guarantees at least ~2 days between two actual notifications.
         */
        private val MIN_GAP_MS = TimeUnit.DAYS.toMillis(2)
        private const val KEY_LAST_SHOWN = "engagement_last_shown"

        /**
         * Schedules the recurring reminder. Safe to call on every launch —
         * [ExistingPeriodicWorkPolicy.KEEP] leaves an existing schedule alone.
         */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<EngagementNotificationWorker>(
                INTERVAL_DAYS, TimeUnit.DAYS
            )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiresBatteryNotLow(true)
                        .build()
                )
                // Start a day in, so a fresh installer isn't pinged immediately.
                .setInitialDelay(1, TimeUnit.DAYS)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
            Log.d(TAG, "Engagement notifications scheduled every $INTERVAL_DAYS days")
        }

        /** Records that the user opened the app, used by the quiet-window check. */
        fun recordAppOpen(context: Context) {
            context.getSharedPreferences(AppConfig.PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putLong(KEY_LAST_APP_OPEN, System.currentTimeMillis())
                .apply()
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
