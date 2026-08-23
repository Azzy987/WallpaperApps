package com.droidates.wallpapers.viewmodel

import android.app.WallpaperManager
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.droidates.wallpapers.model.Wallpaper
import com.droidates.wallpapers.ui.components.ImageFilter
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.droidates.wallpapers.config.AppConfig
import javax.inject.Inject
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.droidates.wallpapers.utils.ContextProvider
import com.droidates.wallpapers.utils.DateTimeFormatter

enum class WallpaperType {
    HOME, LOCK, BOTH
}

@HiltViewModel
class EditWallpaperViewModel @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val contextProvider: ContextProvider,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    
    companion object {
        // Static set to track which wallpapers have been unlocked in the current app session
        private val unlockedEditFeatures = HashSet<String>()
        
        // Static flag to track if unlock dialog has been shown in this session PER WALLPAPER
        private val shownUnlockDialogForWallpapers = HashSet<String>()
        
        // Method to check if a wallpaper's edit features have been unlocked in this session
        fun isEditFeaturesUnlocked(wallpaperId: String): Boolean {
            return unlockedEditFeatures.contains(wallpaperId)
        }
        
        // Method to mark edit features as unlocked for a specific wallpaper
        fun markEditFeaturesUnlocked(wallpaperId: String) {
            unlockedEditFeatures.add(wallpaperId)
            shownUnlockDialogForWallpapers.add(wallpaperId)
            Log.d("EditWallpaperViewModel", "Marked edit features unlocked for wallpaper: $wallpaperId")
        }
        
        // Method to check if unlock dialog has been shown for this specific wallpaper
        fun hasDialogBeenShown(wallpaperId: String? = null): Boolean {
            return if (wallpaperId != null) {
                shownUnlockDialogForWallpapers.contains(wallpaperId)
            } else {
                false // By default, allow dialog to show for any wallpaper
            }
        }
        
        // Reset unlock dialog state for testing
        fun resetDialogState() {
            shownUnlockDialogForWallpapers.clear()
        }
    }
    
    private val context: Context
        get() = contextProvider.getContext()
    
    private val wallpaperId: String = savedStateHandle.get<String>("wallpaperId") ?: ""
    
    private val _wallpaper = MutableStateFlow<Wallpaper?>(null)
    val wallpaper = _wallpaper.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()
    
    private val _isSaving = MutableStateFlow(false)
    val isSaving = _isSaving.asStateFlow()
    
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()
    
    private val _successMessage = MutableStateFlow<String?>(null)
    val successMessage = _successMessage.asStateFlow()
    
    private var wallpaperDocRef: DocumentReference? = null
    private var currentColorMatrix: FloatArray? = null
    private var currentBlurRadius: Float = 0f
    private var sourceCollection: String = AppConfig.COLLECTION_TRENDING // Track which collection the wallpaper came from
    
    // We now use a per-wallpaper tracking mechanism instead of a global flag
    private var _hasShownUnlockDialogForCurrentWallpaper = false
    
    // Use the companion object's property through a computed property
    val hasShownUnlockDialog: Boolean
        get() = _hasShownUnlockDialogForCurrentWallpaper || hasDialogBeenShown(wallpaperId)

    // Method to mark that unlock dialog has been shown for current wallpaper
    fun markUnlockDialogShown() {
        Log.d("EditWallpaperViewModel", "Setting hasShownUnlockDialog to true for wallpaper: $wallpaperId")
        _hasShownUnlockDialogForCurrentWallpaper = true
        if (wallpaperId.isNotEmpty()) {
            shownUnlockDialogForWallpapers.add(wallpaperId)
        }
    }
    
    // Method to mark edit features as unlocked for current wallpaper
    fun markCurrentWallpaperEditFeaturesUnlocked() {
        if (wallpaperId.isNotEmpty()) {
            markEditFeaturesUnlocked(wallpaperId)
        }
    }
    
    init {
        if (wallpaperId.isNotEmpty()) {
            loadWallpaper()
        }
    }
    
    fun loadWallpaper() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            try {
                // PERFORMANCE: Use true parallel execution with select
                val mainCollections = AppConfig.WALLPAPER_SEARCH_COLLECTIONS
                
                // Create parallel deferred tasks
                val deferredResults = mainCollections.map { collection ->
                    async {
                        try {
                            val docRef = firestore.collection(collection).document(wallpaperId)
                            val document = docRef.get().await()
                            
                            if (document != null && document.exists()) {
                                val wallpaper = document.toObject(Wallpaper::class.java)?.copy(id = document.id)
                                Triple(collection, docRef, wallpaper)
                            } else {
                                null
                            }
                        } catch (e: Exception) {
                            Log.w("EditWallpaperViewModel", "Error checking collection $collection: ${e.message}")
                            null
                        }
                    }
                }
                
                // Use awaitAll for true parallel execution - get first non-null result
                val results = awaitAll(*deferredResults.toTypedArray())
                val successResult = results.firstOrNull { it != null }
                
                if (successResult != null) {
                    val (collection, docRef, wallpaper) = successResult
                    wallpaperDocRef = docRef
                    sourceCollection = collection // Store the source collection
                    
                    // Set wallpaper with collection-specific defaults
                    if (collection == AppConfig.COLLECTION_HOME) {
                        wallpaper?.let {
                            _wallpaper.value = it.copy(
                                source = "Official", 
                                exclusive = false
                            )
                        }
                    } else {
                        _wallpaper.value = wallpaper
                    }
                    
                    Log.d("EditWallpaperViewModel", "Found wallpaper in $collection collection")
                } else {
                    // PERFORMANCE: Only check categories if really necessary and limit scope
                    Log.d("EditWallpaperViewModel", "Not found in main collections, checking Categories (limited)")
                    
                    // Check only the most common categories in parallel
                    val commonCategories = listOf("Nature", "Abstract", "Technology", "Space", "Animals")
                    val categoryDeferredResults = commonCategories.map { categoryName ->
                        async {
                            try {
                                val docRef = firestore.collection(AppConfig.COLLECTION_CATEGORIES).document(categoryName)
                                    .collection(AppConfig.COLLECTION_CATEGORY_WALLPAPERS).document(wallpaperId)
                                val document = docRef.get().await()
                                
                                if (document != null && document.exists()) {
                                    val wallpaper = document.toObject(Wallpaper::class.java)?.copy(id = document.id)
                                    Pair(docRef, wallpaper)
                                } else {
                                    null
                                }
                            } catch (e: Exception) {
                                Log.w("EditWallpaperViewModel", "Error checking category $categoryName: ${e.message}")
                                null
                            }
                        }
                    }
                    
                    val categoryResults = awaitAll(*categoryDeferredResults.toTypedArray())
                    val categoryResult = categoryResults.firstOrNull { it != null }
                    
                    if (categoryResult != null) {
                        val (docRef, wallpaper) = categoryResult
                        wallpaperDocRef = docRef
                        _wallpaper.value = wallpaper
                        Log.d("EditWallpaperViewModel", "Found wallpaper in Categories")
                    } else {
                        _errorMessage.value = "Wallpaper not found"
                        Log.e("EditWallpaperViewModel", "Wallpaper not found in any collection: $wallpaperId")
                    }
                }
            } catch (e: Exception) {
                _errorMessage.value = "Error loading wallpaper: ${e.message}"
                Log.e("EditWallpaperViewModel", "Error loading wallpaper", e)
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    private suspend fun setWallpaper(bitmap: Bitmap, type: WallpaperType): Boolean = withContext(Dispatchers.IO) {
        try {
            val wallpaperManager = WallpaperManager.getInstance(context)
            
            when (type) {
                WallpaperType.HOME -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        wallpaperManager.setBitmap(bitmap, null, true, WallpaperManager.FLAG_SYSTEM)
                    } else {
                        wallpaperManager.setBitmap(bitmap)
                    }
                }
                WallpaperType.LOCK -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        wallpaperManager.setBitmap(bitmap, null, true, WallpaperManager.FLAG_LOCK)
                    } else {
                        return@withContext false
                    }
                }
                WallpaperType.BOTH -> {
                    wallpaperManager.setBitmap(bitmap)
                }
            }
            
            return@withContext true
        } catch (e: Exception) {
            Log.e("EditWallpaperViewModel", "Error setting wallpaper", e)
            return@withContext false
        }
    }
    
    private suspend fun saveImageToGallery(context: Context, bitmap: Bitmap): Uri? = withContext(Dispatchers.IO) {
        try {
            // Use DateTimeFormatter utility class instead of direct SimpleDateFormat
            val timestamp = DateTimeFormatter.formatTimestamp(Date(), "yyyyMMdd_HHmmss")
            val filename = "OnePlus7_Edited_$timestamp.jpg"
            var fos: OutputStream? = null
            var imageUri: Uri? = null
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/OnePlus7Wallpapers")
                }
                
                val contentResolver = context.contentResolver
                imageUri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                fos = imageUri?.let { contentResolver.openOutputStream(it) }
            } else {
                val imagesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).toString() + "/OnePlus7Wallpapers"
                val dir = File(imagesDir)
                if (!dir.exists()) dir.mkdirs()
                val image = File(imagesDir, filename)
                fos = FileOutputStream(image)
                imageUri = Uri.fromFile(image)
            }
            
            fos?.use {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it)
            }
            
            return@withContext imageUri
        } catch (e: Exception) {
            Log.e("EditWallpaperViewModel", "Error saving image to gallery", e)
            return@withContext null
        }
    }
    
    fun clearMessages() {
        _errorMessage.value = null
        _successMessage.value = null
    }
    
    private fun Drawable.toBitmap(): Bitmap? {
        return when (this) {
            is BitmapDrawable -> this.bitmap
            else -> {
                val width = this.intrinsicWidth.takeIf { it > 0 } ?: 1
                val height = this.intrinsicHeight.takeIf { it > 0 } ?: 1
                
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(bitmap)
                this.setBounds(0, 0, canvas.width, canvas.height)
                this.draw(canvas)
                bitmap
            }
        }
    }
    
    /**
     * Increments the download count for a wallpaper in Firebase
     */
    private fun incrementDownloads() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                wallpaperDocRef?.let { docRef ->
                    firestore.runTransaction { transaction ->
                        val snapshot = transaction.get(docRef)
                        val currentDownloads = snapshot.getLong("downloads") ?: 0
                        transaction.update(docRef, "downloads", currentDownloads + 1)
                    }.await()
                    
                    Log.d("EditWallpaperViewModel", "Downloads incremented for wallpaper: $wallpaperId")
                } ?: run {
                    Log.w("EditWallpaperViewModel", "No document reference available for download increment")
                }
            } catch (e: Exception) {
                Log.e("EditWallpaperViewModel", "Error incrementing downloads for wallpaper: $wallpaperId", e)
            }
        }
    }
    
    fun downloadEditedWallpaper(context: Context) {
        // Prevent duplicate calls
        if (_isSaving.value) {
            Log.d("EditWallpaperViewModel", "Download already in progress")
            return
        }
        
        viewModelScope.launch {
            try {
                _isSaving.value = true
                _errorMessage.value = null
                _successMessage.value = null
                
                wallpaper.value?.let { currentWallpaper ->
                    val bitmap = getEditedBitmap(currentWallpaper.imageUrl)
                    
                    if (bitmap != null) {
                        val uri = saveImageToGallery(context, bitmap)
                        if (uri != null) {
                            // FIXED: Increment download count when wallpaper is successfully downloaded
                            incrementDownloads()
                            
                            // Show single toast
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "Wallpaper saved to gallery", Toast.LENGTH_SHORT).show()
                            }
                            // Don't set success message to prevent LaunchedEffect from showing another toast
                        } else {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "Failed to save wallpaper", Toast.LENGTH_SHORT).show()
                            }
                            // Don't set error message to prevent LaunchedEffect from showing another toast
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Failed to process image", Toast.LENGTH_SHORT).show()
                        }
                        // Don't set error message to prevent LaunchedEffect from showing another toast
                    }
                } ?: run {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "No wallpaper selected", Toast.LENGTH_SHORT).show()
                    }
                    // Don't set error message to prevent LaunchedEffect from showing another toast
                }
            } catch (e: Exception) {
                Log.e("EditWallpaperViewModel", "Error downloading wallpaper", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
                // Don't set error message to prevent LaunchedEffect from showing another toast
            } finally {
                _isSaving.value = false
            }
        }
    }
    
    fun setEditedWallpaper(type: WallpaperType) {
        // Prevent duplicate calls
        if (_isSaving.value) {
            Log.d("EditWallpaperViewModel", "Setting wallpaper already in progress")
            return
        }
        
        viewModelScope.launch {
            try {
                _isSaving.value = true
                _errorMessage.value = null
                _successMessage.value = null
                
                wallpaper.value?.let { currentWallpaper ->
                    val bitmap = getEditedBitmap(currentWallpaper.imageUrl)
                    
                    if (bitmap != null) {
                        val success = setWallpaper(bitmap, type)
                        if (success) {
                            // Show single toast
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "Wallpaper set successfully", Toast.LENGTH_SHORT).show()
                            }
                            // Don't set success message to prevent LaunchedEffect from showing another toast 
                        } else {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "Failed to set wallpaper", Toast.LENGTH_SHORT).show()
                            }
                            // Don't set error message to prevent LaunchedEffect from showing another toast
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Failed to process image", Toast.LENGTH_SHORT).show()
                        }
                        // Don't set error message to prevent LaunchedEffect from showing another toast
                    }
                } ?: run {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "No wallpaper selected", Toast.LENGTH_SHORT).show()
                    }
                    // Don't set error message to prevent LaunchedEffect from showing another toast
                }
            } catch (e: Exception) {
                Log.e("EditWallpaperViewModel", "Error setting wallpaper", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
                // Don't set error message to prevent LaunchedEffect from showing another toast
            } finally {
                _isSaving.value = false
            }
        }
    }
    
    fun updateBlurRadius(radius: Float) {
        currentBlurRadius = radius
    }
    
    fun updateColorMatrix(values: FloatArray) {
        currentColorMatrix = values.clone()
    }
    
    private suspend fun getEditedBitmap(imageUrl: String): Bitmap? = withContext(Dispatchers.IO) {
        try {
            Log.d("EditWallpaperViewModel", "Getting edited bitmap for $imageUrl with blur: $currentBlurRadius")
            val loader = ImageLoader(context)
            val request = ImageRequest.Builder(context)
                .data(imageUrl)
                .allowHardware(false)
                .build()
                
            val result = (loader.execute(request) as? SuccessResult)?.drawable
            val originalBitmap = result?.toBitmap() ?: return@withContext null
            
            // Calculate target dimensions while maintaining aspect ratio
            val targetWidth = 1080 // Standard FHD width
            val targetHeight = (targetWidth.toFloat() / originalBitmap.width * originalBitmap.height).toInt()
            
            // Scale the bitmap to target dimensions
            val scaledBitmap = Bitmap.createScaledBitmap(originalBitmap, targetWidth, targetHeight, true)
            
            // Create a bitmap to work with
            val workingBitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(workingBitmap)
            
            // Create paint with color matrix
            val paint = android.graphics.Paint().apply {
                isAntiAlias = true
                
                // Apply color matrix if available
                currentColorMatrix?.let { matrix ->
                    colorFilter = ColorMatrixColorFilter(ColorMatrix(matrix))
                }
            }
            
            // Draw the bitmap with color effects
            canvas.drawBitmap(scaledBitmap, 0f, 0f, paint)
            
            // Apply blur if needed
            var finalBitmap = workingBitmap
            if (currentBlurRadius > 0f) {
                try {
                    // Create a new bitmap for blur
                    val blurredBitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                    val blurCanvas = android.graphics.Canvas(blurredBitmap)
                    
                    // Create a strong blur effect
                    val blurPaint = android.graphics.Paint().apply {
                        isAntiAlias = true
                        maskFilter = android.graphics.BlurMaskFilter(
                            currentBlurRadius * 15f, // Increased multiplier for more visible blur
                            android.graphics.BlurMaskFilter.Blur.NORMAL
                        )
                    }
                    
                    // Apply blur
                    blurCanvas.drawBitmap(workingBitmap, 0f, 0f, blurPaint)
                    
                    // Clean up
                    workingBitmap.recycle()
                    finalBitmap = blurredBitmap
                    
                    Log.d("EditWallpaperViewModel", "Blur applied with radius: ${currentBlurRadius * 15f}")
                } catch (e: Exception) {
                    Log.e("EditWallpaperViewModel", "Error applying blur", e)
                    // Continue with the non-blurred bitmap
                }
            }
            
            // Apply rounded corners
            val outputBitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
            val outputCanvas = android.graphics.Canvas(outputBitmap)
            
            // Create path for rounded corners
            val path = android.graphics.Path()
            val rect = android.graphics.RectF(0f, 0f, targetWidth.toFloat(), targetHeight.toFloat())
            val cornerRadius = 30f
            path.addRoundRect(rect, cornerRadius, cornerRadius, android.graphics.Path.Direction.CW)
            
            // Apply clipping path
            outputCanvas.clipPath(path)
            
            // Draw the final bitmap
            outputCanvas.drawBitmap(finalBitmap, 0f, 0f, null)
            
            // Clean up
            if (finalBitmap != outputBitmap) {
                finalBitmap.recycle()
            }
            if (scaledBitmap != finalBitmap && scaledBitmap != workingBitmap) {
                scaledBitmap.recycle()
            }
            
            return@withContext outputBitmap
        } catch (e: Exception) {
            Log.e("EditWallpaperViewModel", "Error creating edited bitmap", e)
            return@withContext null
        }
    }
} 