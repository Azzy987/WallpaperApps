package com.droidates.wallpapers.notifications

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.droidates.wallpapers.MainActivity
import com.droidates.wallpapers.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL

/**
 * Helper class for testing notifications locally during development
 * This is not meant for production use, but for testing notification appearance
 */
object NotificationTestHelper {
    private const val TAG = "NotificationTestHelper"
    
    /**
     * Send a test text notification
     */
    fun sendTestTextNotification(
        context: Context,
        title: String = "Test Notification",
        message: String = "This is a test notification message that will appear in your notification drawer."
    ) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = context.getString(R.string.default_notification_channel_id)
        
        val notificationBuilder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(message)
            .setAutoCancel(true)
            .setColor(ContextCompat.getColor(context, R.color.notification_color))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        
        // Use big text style for expanded view
        val bigTextStyle = NotificationCompat.BigTextStyle()
            .bigText(message)
            .setBigContentTitle(title)
        
        notificationBuilder.setStyle(bigTextStyle)
        
        // Show the notification with a unique ID
        notificationManager.notify(System.currentTimeMillis().toInt(), notificationBuilder.build())
        
        Log.d(TAG, "Test text notification sent")
    }
    
    /**
     * Send a test notification that opens a specific wallpaper when tapped
     */
    fun sendTestNotificationWithWallpaperId(
        context: Context,
        title: String = "New Wallpaper Available",
        message: String = "Check out this beautiful new wallpaper!",
        wallpaperId: String
    ) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = context.getString(R.string.default_notification_channel_id)
        
        // Create intent to open the app with specific wallpaper
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("wallpaper_id", wallpaperId)
            putExtra("source", "notification")
        }
        
        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context,
            System.currentTimeMillis().toInt(), // Use current time as request code for uniqueness
            intent,
            pendingIntentFlags
        )
        
        val notificationBuilder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(message)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setColor(ContextCompat.getColor(context, R.color.notification_color))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        
        // Use big text style for expanded view
        val bigTextStyle = NotificationCompat.BigTextStyle()
            .bigText(message)
            .setBigContentTitle(title)
        
        notificationBuilder.setStyle(bigTextStyle)
        
        // Show the notification with a unique ID
        notificationManager.notify(System.currentTimeMillis().toInt(), notificationBuilder.build())
        
        Log.d(TAG, "Test notification sent with wallpaper ID: $wallpaperId")
    }
    
    /**
     * Send a test image notification
     */
    suspend fun sendTestImageNotification(
        context: Context,
        title: String = "New Wallpaper Available",
        message: String = "Check out this beautiful new wallpaper!",
        imageUrl: String = "https://picsum.photos/1080/1920", // Random image from Lorem Picsum
        wallpaperId: String? = null // Optional wallpaper ID to open when tapped
    ) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = context.getString(R.string.default_notification_channel_id)
        
        val notificationBuilder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(message)
            .setAutoCancel(true)
            .setColor(ContextCompat.getColor(context, R.color.notification_color))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        
        // Add pending intent if wallpaper ID is provided
        if (wallpaperId != null) {
            val intent = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra("wallpaper_id", wallpaperId)
                putExtra("source", "notification")
            }
            
            val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            
            val pendingIntent = PendingIntent.getActivity(
                context,
                System.currentTimeMillis().toInt(),
                intent,
                pendingIntentFlags
            )
            
            notificationBuilder.setContentIntent(pendingIntent)
        }
        
        try {
            // Download image in a background thread
            val bitmap = withContext(Dispatchers.IO) {
                val url = URL(imageUrl)
                BitmapFactory.decodeStream(url.openConnection().getInputStream())
            }
            
            // Create a big picture style notification
            val bigPictureStyle = NotificationCompat.BigPictureStyle()
                .bigPicture(bitmap)
                .setBigContentTitle(title)
                .setSummaryText(message)
            
            notificationBuilder.setStyle(bigPictureStyle)
                .setLargeIcon(bitmap)
            
            // Show the notification with a unique ID
            notificationManager.notify(System.currentTimeMillis().toInt(), notificationBuilder.build())
            
            Log.d(TAG, "Test image notification sent" + (wallpaperId?.let { " with wallpaper ID: $it" } ?: ""))
        } catch (e: Exception) {
            Log.e(TAG, "Error sending test image notification", e)
            
            // Fallback to text notification if image loading fails
            if (wallpaperId != null) {
                sendTestNotificationWithWallpaperId(context, title, "$message (Image loading failed)", wallpaperId)
            } else {
                sendTestTextNotification(context, title, "$message (Image loading failed)")
            }
        }
    }
} 