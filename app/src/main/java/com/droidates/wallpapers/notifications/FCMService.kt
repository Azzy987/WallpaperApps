package com.droidates.wallpapers.notifications

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.droidates.wallpapers.MainActivity
import com.droidates.wallpapers.R
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class FCMService : FirebaseMessagingService() {
    
    companion object {
        private const val TAG = "FCMService"
        private const val NOTIFICATION_ID = 1000
    }
    
    /**
     * Called when a new FCM token is generated
     */
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "New FCM token: $token")
        // Here you would typically send this token to your server
    }
    
    /**
     * Called when a message is received
     */
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(TAG, "From: ${remoteMessage.from}")
        
        // Extract data from payload first
        val data = remoteMessage.data
        val imageUrl = data["image_url"]
        val channelId = data["channel_id"] ?: getString(R.string.default_notification_channel_id)
        val wallpaperId = data["wallpaper_id"]
        val notificationType = data["type"] ?: "general"
        
        // Check if message contains a notification payload
        if (remoteMessage.notification != null) {
            remoteMessage.notification?.let { notification ->
                Log.d(TAG, "Notification Message Body: ${notification.body}")
                
                // Handle different notification types
                when (notificationType) {
                    "promo" -> {
                        // Use the promo channel for promotional notifications
                        sendNotification(
                            title = notification.title ?: "Promotion",
                            message = notification.body ?: "Check out our latest offer!",
                            imageUrl = imageUrl,
                            channelId = getString(R.string.promo_notification_channel_id),
                            wallpaperId = wallpaperId
                        )
                    }
                    else -> {
                        // Use the default channel for general notifications
                        sendNotification(
                            title = notification.title ?: "Notification",
                            message = notification.body ?: "",
                            imageUrl = imageUrl,
                            channelId = channelId,
                            wallpaperId = wallpaperId
                        )
                    }
                }
            }
        } else if (data.isNotEmpty()) {
            // Handle data-only message (no notification payload)
            val title = data["title"] ?: "New Wallpaper"
            val message = data["message"] ?: "Check out this wallpaper!"
            
            // Handle different notification types
            when (notificationType) {
                "promo" -> {
                    sendNotification(
                        title = title,
                        message = message,
                        imageUrl = imageUrl,
                        channelId = getString(R.string.promo_notification_channel_id),
                        wallpaperId = wallpaperId
                    )
                }
                else -> {
                    sendNotification(
                        title = title,
                        message = message,
                        imageUrl = imageUrl,
                        channelId = channelId,
                        wallpaperId = wallpaperId
                    )
                }
            }
        }
    }
    
    /**
     * Create and show a notification with the provided details
     */
    private fun sendNotification(
        title: String,
        message: String,
        imageUrl: String?,
        channelId: String,
        wallpaperId: String?
    ) {
        // Create intent to open the app when notification is clicked
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            // Pass wallpaper ID if available
            wallpaperId?.let {
                putExtra("wallpaper_id", it)
                putExtra("source", "notification")
            }
        }
        
        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            pendingIntentFlags
        )
        
        // Get notification sound
        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        
        // Start building the notification
        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(message)
            .setAutoCancel(true)
            .setSound(defaultSoundUri)
            .setContentIntent(pendingIntent)
            .setColor(ContextCompat.getColor(this, R.color.notification_color))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        
        // If there's an image URL, load it and add to the notification
        if (!imageUrl.isNullOrEmpty()) {
            // Use a separate thread to download the image
            val executor = Executors.newSingleThreadExecutor()
            executor.execute {
                try {
                    val bitmap = getBitmapFromUrl(imageUrl)
                    bitmap?.let {
                        // Create a big picture style notification
                        val bigPictureStyle = NotificationCompat.BigPictureStyle()
                            .bigPicture(it)
                            .setBigContentTitle(title)
                            .setSummaryText(message)
                        
                        // Explicitly use the Bitmap version of bigLargeIcon to avoid ambiguity
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            bigPictureStyle.bigLargeIcon(it as Bitmap?)
                        }
                        
                        // Set the style and large icon
                        notificationBuilder.setStyle(bigPictureStyle)
                            .setLargeIcon(it)
                        
                        // Show notification on the main thread
                        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                        notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build())
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error loading notification image", e)
                    // Show notification without image if there's an error
                    showTextNotification(notificationBuilder)
                }
            }
        } else {
            // For text-only notifications, use big text style
            val bigTextStyle = NotificationCompat.BigTextStyle()
                .bigText(message)
                .setBigContentTitle(title)
            
            notificationBuilder.setStyle(bigTextStyle)
            
            // Show the notification
            showTextNotification(notificationBuilder)
        }
    }
    
    /**
     * Show a text-only notification
     */
    private fun showTextNotification(notificationBuilder: NotificationCompat.Builder) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build())
    }
    
    /**
     * Download an image from a URL and convert it to a Bitmap
     */
    private fun getBitmapFromUrl(imageUrl: String): Bitmap? {
        return try {
            val url = URL(imageUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.doInput = true
            connection.connectTimeout = 10000 // 10 seconds timeout
            connection.readTimeout = 15000 // 15 seconds read timeout
            connection.connect()
            
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                Log.e(TAG, "HTTP error code: ${connection.responseCode}")
                return null
            }
            
            val input: InputStream = connection.inputStream
            val options = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val bitmap = BitmapFactory.decodeStream(input, null, options)
            if (bitmap == null) {
                Log.e(TAG, "Failed to decode bitmap from stream")
            }
            bitmap
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading image: ${e.message}", e)
            null
        }
    }
} 