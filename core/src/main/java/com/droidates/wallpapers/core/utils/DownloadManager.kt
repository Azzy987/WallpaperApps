package com.droidates.wallpapers.core.utils

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
import android.content.ContentUris
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.flow.FlowCollector
import com.droidates.wallpapers.core.config.AppConfig

object DownloadManager {
    private const val TAG = "DownloadManager"
    // getter, not a cached val — resolves from the installed AppSpec on every use
    private val APP_FOLDER_NAME: String get() = AppConfig.DOWNLOAD_FOLDER_NAME

    class DownloadHttpException(
        val code: Int,
        url: String
    ) : IOException("Download failed with code: $code for $url")
    
    // Shared OkHttpClient to prevent resource leaks
    private val sharedOkHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }
    
    fun getWallpapersFolder(context: Context): File {
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
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            findExistingMediaStoreUri(context, fileName) != null
        } else {
            File(getWallpapersFolder(context), fileName).exists()
        }
    }

    fun downloadWallpaper(context: Context, url: String, fileName: String): Flow<Float> = flow {
        try {
            // For Android 10 (API 29) and above, use MediaStore
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                if (touchExistingMediaStoreItem(context, fileName)) {
                    emit(1f)
                } else {
                    downloadUsingMediaStore(context, url, fileName, this)
                }
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
        val now = System.currentTimeMillis()
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/" + APP_FOLDER_NAME)
            put(MediaStore.Images.Media.DATE_TAKEN, now)
            put(MediaStore.Images.Media.DATE_ADDED, now / 1000)
            put(MediaStore.Images.Media.DATE_MODIFIED, now / 1000)
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
                val request = buildImageRequest(url)

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw DownloadHttpException(response.code, url)

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
            contentValues.put(MediaStore.Images.Media.DATE_TAKEN, now)
            contentValues.put(MediaStore.Images.Media.DATE_ADDED, now / 1000)
            contentValues.put(MediaStore.Images.Media.DATE_MODIFIED, now / 1000)
            resolver.update(imageUri, contentValues, null, null)

            Log.d(TAG, "Download completed successfully using MediaStore: $imageUri")
            flow.emit(1f)
        } catch (e: Exception) {
            // Delete the record if something fails
            resolver.delete(imageUri, null, null)
            throw e
        }
    }

    private fun findExistingMediaStoreUri(context: Context, fileName: String): Uri? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null

        val resolver = context.contentResolver
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val expectedRelativePath = "${Environment.DIRECTORY_PICTURES}/$APP_FOLDER_NAME/"
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.RELATIVE_PATH
        )
        val selection = "${MediaStore.Images.Media.DISPLAY_NAME} = ?"
        val sortOrder = "${MediaStore.Images.Media.DATE_MODIFIED} DESC"

        return runCatching {
            resolver.query(collection, projection, selection, arrayOf(fileName), sortOrder)
                ?.use { cursor ->
                    val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                    val relativePathColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.RELATIVE_PATH)

                    while (cursor.moveToNext()) {
                        val relativePath = cursor.getString(relativePathColumn).orEmpty()
                        if (relativePath == expectedRelativePath) {
                            val id = cursor.getLong(idColumn)
                            return@use ContentUris.withAppendedId(collection, id)
                        }
                    }
                    null
                }
        }.getOrNull()
    }

    private fun touchExistingMediaStoreItem(context: Context, fileName: String): Boolean {
        val existingUri = findExistingMediaStoreUri(context, fileName) ?: return false
        val now = System.currentTimeMillis()
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DATE_TAKEN, now)
            put(MediaStore.Images.Media.DATE_ADDED, now / 1000)
            put(MediaStore.Images.Media.DATE_MODIFIED, now / 1000)
        }
        runCatching {
            context.contentResolver.update(existingUri, values, null, null)
            Log.d(TAG, "Wallpaper already exists; updated MediaStore recents timestamp: $existingUri")
        }.onFailure { error ->
            Log.w(TAG, "Wallpaper already exists, but MediaStore timestamp update failed: $existingUri", error)
        }
        return true
    }

    private suspend fun downloadUsingFileAccess(
        context: Context,
        url: String,
        fileName: String,
        flow: FlowCollector<Float>
    ) {
        val folder = getWallpapersFolder(context)
        val file = File(folder, fileName)

        if (file.exists()) {
            file.setLastModified(System.currentTimeMillis())
            notifyGallery(context, file)
            Log.d(TAG, "Wallpaper already exists; updated file timestamp: ${file.absolutePath}")
            flow.emit(1f)
            return
        }
        
        Log.d(TAG, "Downloading wallpaper to: ${file.absolutePath}")

        try {
            val client = sharedOkHttpClient
            val request = buildImageRequest(url)

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw DownloadHttpException(response.code, url)

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
            applyImageRequestHeaders(connection)
            connection.connect()

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw DownloadHttpException(connection.responseCode, url)
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

    private fun buildImageRequest(url: String): Request {
        return Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Android) ${AppConfig.DOWNLOAD_FOLDER_NAME}/${AppConfig.VERSION_NAME}")
            .header("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
            .header("Accept-Encoding", "identity")
            .build()
    }

    private fun applyImageRequestHeaders(connection: HttpURLConnection) {
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android) ${AppConfig.DOWNLOAD_FOLDER_NAME}/${AppConfig.VERSION_NAME}")
        connection.setRequestProperty("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
        connection.setRequestProperty("Accept-Encoding", "identity")
    }
}
