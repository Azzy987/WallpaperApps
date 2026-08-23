package com.droidates.wallpapers.utils

import android.content.Context
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import android.util.Log
import java.io.InputStream
import android.media.MediaScannerConnection
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.flow.FlowCollector
import com.droidates.wallpapers.config.AppConfig

object DownloadManager {
    private const val TAG = "DownloadManager"
    private val APP_FOLDER_NAME = AppConfig.DOWNLOAD_FOLDER_NAME
    
    // Shared OkHttpClient to prevent resource leaks
    private val sharedOkHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }
    
    fun getOnePlus7WallpapersFolder(context: Context): File {
        // Use Pictures directory with app name subfolder
        val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        val appDir = File(picturesDir, APP_FOLDER_NAME)
        if (!appDir.exists()) {
            val created = appDir.mkdirs()
            Log.d(TAG, "Created $APP_FOLDER_NAME directory: $created")
        }
        Log.d(TAG, "Wallpapers directory path: ${appDir.absolutePath}")
        return appDir
    }

    fun getCacheFile(context: Context, fileName: String): File {
        val cacheDir = context.cacheDir
        return File(cacheDir, fileName)
    }

    fun isWallpaperDownloaded(context: Context, fileName: String): Boolean {
        // Always return false to force a new download every time
        return false
    }

    fun downloadWallpaper(context: Context, url: String, fileName: String): Flow<Float> = flow {
        try {
            // For Android 10 (API 29) and above, use MediaStore
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                downloadUsingMediaStore(context, url, fileName, this)
            } else {
                // For older Android versions, use direct file access
                downloadUsingFileAccess(context, url, fileName, this)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading wallpaper (Ask Gemini)", e)
            throw e
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun downloadUsingMediaStore(
        context: Context,
        url: String,
        fileName: String,
        flow: FlowCollector<Float>
    ) {
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/" + APP_FOLDER_NAME)
            // Mark as pending to avoid media scanner from scanning it before it's ready
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }

        val resolver = context.contentResolver
        val imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            ?: throw IOException("Failed to create new MediaStore record")

        try {
            resolver.openOutputStream(imageUri)?.use { outputStream ->
                // Download image from URL
                val client = sharedOkHttpClient
                val request = Request.Builder().url(url).build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("Download failed with code: ${response.code}")

                    val body = response.body ?: throw IOException("Empty response")
                    val contentLength = body.contentLength()
                    var bytesWritten = 0L

                    body.byteStream().use { input ->
                        val buffer = ByteArray(8192)
                        var bytes: Int
                        while (input.read(buffer).also { bytes = it } != -1) {
                            outputStream.write(buffer, 0, bytes)
                            bytesWritten += bytes
                            flow.emit(bytesWritten.toFloat() / contentLength)
                        }
                    }
                }
            } ?: throw IOException("Failed to open output stream for URI: $imageUri")

            // Clear the pending flag now that the download is complete
            contentValues.clear()
            contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(imageUri, contentValues, null, null)

            Log.d(TAG, "Download completed successfully using MediaStore: $imageUri")
            flow.emit(1f)
        } catch (e: Exception) {
            // Delete the record if something fails
            resolver.delete(imageUri, null, null)
            throw e
        }
    }

    private suspend fun downloadUsingFileAccess(
        context: Context,
        url: String,
        fileName: String,
        flow: FlowCollector<Float>
    ) {
        val folder = getOnePlus7WallpapersFolder(context)
        val file = File(folder, fileName)
        
        // Delete existing file if it exists to ensure a fresh download
        if (file.exists()) {
            file.delete()
            Log.d(TAG, "Deleted existing file to force re-download: ${file.absolutePath}")
        }
        
        Log.d(TAG, "Downloading wallpaper to: ${file.absolutePath}")

        try {
            val client = sharedOkHttpClient
            val request = Request.Builder().url(url).build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Download failed")

                val body = response.body ?: throw IOException("Empty response")
                val contentLength = body.contentLength()
                var bytesWritten = 0L

                FileOutputStream(file).use { output ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(8192)
                        var bytes: Int
                        while (input.read(buffer).also { bytes = it } != -1) {
                            output.write(buffer, 0, bytes)
                            bytesWritten += bytes
                            flow.emit(bytesWritten.toFloat() / contentLength)
                        }
                    }
                }
            }
            
            // Make the file visible in gallery
            notifyGallery(context, file)
            
            Log.d(TAG, "Download completed successfully: ${file.exists()}, size: ${file.length()} bytes")
            flow.emit(1f)
        } catch (e: Exception) {
            throw e
        }
    }
    
    private fun notifyGallery(context: Context, file: File) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // For Android 10+ (API 29+), we don't need to do anything here
                // because downloadUsingMediaStore already inserts into MediaStore
                Log.d(TAG, "Android 10+ - Media store insertion already handled")
            } else {
                // For older Android versions, use MediaScannerConnection
                try {
                    MediaScannerConnection.scanFile(
                        context,
                        arrayOf(file.absolutePath),
                        arrayOf("image/jpeg"),
                        null
                    )
                    Log.d(TAG, "Media scanner scan completed for: ${file.absolutePath}")
                } catch (e: Exception) {
                    Log.e(TAG, "Error scanning file with MediaScannerConnection", e)
                }
            }
            
            Log.d(TAG, "Notified gallery about new file: ${file.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Error notifying gallery", e)
        }
    }
    
    /**
     * Download a file to the app's cache directory
     * @param context The context to use
     * @param url The URL to download from
     * @param fileName The name to save the file as in the cache
     * @param forceRedownload Whether to force re-download even if the file exists
     * @return A Flow of download progress
     */
    suspend fun downloadToCache(
        context: Context,
        url: String,
        fileName: String,
        forceRedownload: Boolean = true
    ): Flow<Float> = flow {
        val cacheDir = context.cacheDir
        val file = File(cacheDir, fileName)

        // If the file exists and we don't need to force re-download
        if (file.exists() && !forceRedownload && file.length() > 0) {
            Log.d("DownloadManager", "File already exists and using cache: $fileName (${file.length()} bytes)")
            // Return complete progress immediately since we're using the cached file
            emit(1f)
            return@flow
        }
        
        // Delete existing file if we need to re-download
        if (file.exists() && forceRedownload) {
            Log.d("DownloadManager", "Deleted existing cache file to force re-download: ${file.absolutePath}")
            file.delete()
        }

        Log.d("DownloadManager", "Downloading wallpaper to cache: ${file.absolutePath}")

        try {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connect()

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("HTTP error code: ${connection.responseCode}")
            }

            val contentLength = connection.contentLength
            var downloadedBytes = 0

            FileOutputStream(file).use { fos ->
                connection.inputStream.use { inputStream ->
                    val buffer = ByteArray(4096)
                    var bytesRead: Int

                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        fos.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead

                        // Emit download progress
                        val progress = if (contentLength > 0) {
                            downloadedBytes.toFloat() / contentLength.toFloat()
                        } else {
                            0f
                        }
                        emit(progress.coerceIn(0f, 1f))
                    }
                }
            }

            // Verify file was downloaded successfully
            val success = file.exists() && file.length() > 0
            Log.d("DownloadManager", "Cache download completed successfully: $success, size: ${file.length()} bytes")

            // Emit completion
            emit(1f)
        } catch (e: Exception) {
            Log.e("DownloadManager", "Error downloading to cache: ${e.message}")
            throw e
        }
    }.flowOn(Dispatchers.IO)
} 