package com.droidates.wallpapers.core.viewmodel

import android.app.WallpaperManager
import android.content.ClipData
import android.content.Context
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.ui.graphics.Color
import androidx.core.content.FileProvider
import androidx.tracing.traceAsync
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.droidates.wallpapers.core.R
import com.droidates.wallpapers.core.data.repository.FavoritesRepository
import com.droidates.wallpapers.core.model.Wallpaper
import com.droidates.wallpapers.core.ui.components.ToastManager
import com.droidates.wallpapers.core.ui.components.WallpaperSetOption
import com.droidates.wallpapers.core.ui.screens.PermissionRequestType
import com.droidates.wallpapers.core.ui.screens.ReportReason
import com.droidates.wallpapers.core.config.AppConfig
import com.droidates.wallpapers.core.utils.SortOption
import com.droidates.wallpapers.core.utils.toFirestoreSort
import com.google.firebase.firestore.Query
import com.droidates.wallpapers.core.utils.AdManager
import com.droidates.wallpapers.core.utils.ContextProvider
import com.droidates.wallpapers.core.utils.DownloadManager
import com.droidates.wallpapers.core.utils.ImageUtils
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.Collections
import java.util.Locale
import javax.inject.Inject



@HiltViewModel
class DetailViewModel @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val favoritesRepository: FavoritesRepository,
    private val contextProvider: ContextProvider,
    private val toastManager: ToastManager,
    private val adManager: AdManager,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val context: Context
        get() = contextProvider.getContext()

    private val _wallpaper = MutableStateFlow<Wallpaper?>(null)
    val wallpaper = _wallpaper.asStateFlow()

    // Local stat counters for instant UI feedback (incremented optimistically)
    private val _localDownloads = MutableStateFlow<Int?>(null)
    val localDownloads = _localDownloads.asStateFlow()

    private val _localViews = MutableStateFlow<Int?>(null)
    val localViews = _localViews.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()

    // GRADIENT BLUE FIX: Initialize with gradient blue instead of black to eliminate flash
    private val _backgroundColor = MutableStateFlow(Color(0xFF1A1A2E)) // Deep blue from gradient
    val backgroundColor = _backgroundColor.asStateFlow()

    private val _loadingProgress = MutableStateFlow(0f)

    private val _downloadProgress = MutableStateFlow(0f)
    val downloadProgress = _downloadProgress.asStateFlow()

    private val _isDownloading = MutableStateFlow(false)
    val isDownloading = _isDownloading.asStateFlow()

    private val _isDownloaded = MutableStateFlow(false)
    val isDownloaded = _isDownloaded.asStateFlow()

    private val _isSettingWallpaper = MutableStateFlow(false)
    val isSettingWallpaper = _isSettingWallpaper.asStateFlow()

    private val _isSharing = MutableStateFlow(false)
    val isSharing = _isSharing.asStateFlow()

    private val _isEditing = MutableStateFlow(false)
    val isEditing = _isEditing.asStateFlow()

    // Image metadata variables
    private val _imageDimensions = MutableStateFlow("")
    val imageDimensions: StateFlow<String> = _imageDimensions
    
    private val _imageSize = MutableStateFlow("")
    val imageSize: StateFlow<String> = _imageSize
    
    /**
     * Set image dimensions for display
     */
    fun setImageDimensions(dimensions: String) {
        _imageDimensions.value = dimensions
    }
    
    /**
     * Set image size for display
     */
    fun setImageSize(size: String) {
        _imageSize.value = size
    }

    // Source screen for proper ordering
    private var sourceScreen: String = AppConfig.SOURCE_HOME
    private var currentSortOption: String = "LATEST"

    // Swipe navigation context (IDs + in-memory cache for smooth vertical swipes)
    private val adjacentWallpapersMap = ConcurrentHashMap<String, Wallpaper>()
    private val wallpaperIds = Collections.synchronizedList(mutableListOf<String>())
    private var currentIndex = -1
    private var currentCollection: String? = null
    private var nextWallpaperId: String? = null
    private var prevWallpaperId: String? = null
    private var nextIndex = -1
    private var prevIndex = -1

    private val _isLoadingAdjacentWallpapers = MutableStateFlow(false)
    val isLoadingAdjacentWallpapers = _isLoadingAdjacentWallpapers.asStateFlow()

    /**
     * Sets the source screen to determine proper ordering for navigation
     */
    fun setSourceScreen(source: String) {
        sourceScreen = source
    }

    fun setSortOption(sortOption: String) {
        currentSortOption = sortOption
    }

    private fun parseSortOption(): SortOption = runCatching {
        SortOption.valueOf(currentSortOption)
    }.getOrDefault(SortOption.LATEST)
    
    // Cache the downloaded file path
    private var cachedWallpaperFile: File? = null

    // Add this flag
    private var currentWallpaperId: String? = null
    private var lastViewIncrementKey: String? = null

    /**
     * Increments the views count for a wallpaper in Firebase
     */
    fun incrementViews(source: String, wallpaperId: String) {
        val incrementKey = counterKey(source, wallpaperId)
        if (lastViewIncrementKey == incrementKey) {
            Log.d("DetailViewModel", "Skipping duplicate view increment for wallpaper: $wallpaperId")
            return
        }
        lastViewIncrementKey = incrementKey
        _localViews.value = _localViews.value?.plus(1)

        incrementCounter(source, wallpaperId, "views", "Views")
    }
    
    /**
     * Increments the downloads count for a wallpaper in Firebase
     */
    fun incrementDownloads(source: String, wallpaperId: String) {
        _localDownloads.value = _localDownloads.value?.plus(1)
        incrementCounter(source, wallpaperId, "downloads", "Downloads")
    }

    private fun incrementCounter(
        source: String,
        wallpaperId: String,
        fieldName: String,
        label: String
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // NPE PREVENTION: Check if coroutine is still active
                if (!isActive) return@launch
                
                val docRef = getWallpaperDocumentReference(source, wallpaperId)
                if (docRef != null && isActive) {
                    val updated = withTimeout(3000L) {
                        firestore.runTransaction { transaction ->
                            val snapshot = transaction.get(docRef)
                            if (!snapshot.exists()) {
                                false
                            } else {
                                transaction.update(docRef, fieldName, FieldValue.increment(1))
                                true
                            }
                        }.await()
                    }
                    
                    if (updated) {
                        Log.d("DetailViewModel", "$label incremented for wallpaper: $wallpaperId")
                    } else {
                        Log.w("DetailViewModel", "Skipped $fieldName increment because document does not exist: ${docRef.path}")
                    }
                } else {
                    Log.w("DetailViewModel", "Could not resolve document reference for wallpaper: $wallpaperId")
                }
            } catch (e: TimeoutCancellationException) {
                Log.w("DetailViewModel", "$label increment timed out for wallpaper: $wallpaperId")
            } catch (e: Exception) {
                Log.e("DetailViewModel", "Error incrementing $fieldName for wallpaper: $wallpaperId", e)
            }
        }
    }

    private fun counterKey(source: String, wallpaperId: String): String = "$source|$wallpaperId"
    
    /**
     * Helper function to get the correct document reference for a wallpaper
     */
    private suspend fun getWallpaperDocumentReference(source: String, wallpaperId: String): DocumentReference? {
        return try {
            firestore.collection(resolveWallpaperCollection(source)).document(wallpaperId)
        } catch (e: Exception) {
            Log.e("DetailViewModel", "Error getting document reference for $wallpaperId from $source", e)
            null
        }
    }

    private fun resolveWallpaperCollection(source: String): String {
        return when {
            source == AppConfig.SOURCE_HOME ||
                source.startsWith("${AppConfig.SOURCE_HOME}:") ||
                source.equals(AppConfig.COLLECTION_HOME, ignoreCase = true) -> AppConfig.COLLECTION_HOME

            source == AppConfig.COLLECTION_TRENDING ||
                source == AppConfig.SOURCE_TRENDING ||
                source.startsWith("${AppConfig.SOURCE_TRENDING}:") -> AppConfig.COLLECTION_TRENDING

            source.startsWith("${AppConfig.SOURCE_CATEGORY}:") -> {
                val categoryName = source.split(":").getOrNull(1)
                if (categoryName.equals(AppConfig.COLLECTION_HOME, ignoreCase = true)) {
                    AppConfig.COLLECTION_HOME
                } else {
                    AppConfig.COLLECTION_TRENDING
                }
            }

            else -> AppConfig.COLLECTION_TRENDING
        }
    }
    private var isSettingWallpaperInProgress = false

    // GRADIENT BLUE FIX: Use gradient blue color instead of dark gray
    private val initialBackgroundColor = Color(0xFF1A1A2E) // Deep blue from gradient

    // Add this at class level
    private var loadingJob: Job? = null
    private var metadataJob: Job? = null

    var permissionRequestType: PermissionRequestType = PermissionRequestType.DOWNLOAD

    private var hasIncrementedViews = false

    private val wallpaperId: String = checkNotNull(savedStateHandle["wallpaperId"]) {
        "wallpaperId parameter wasn't found. Please make sure it's passed in navigation."
    }

    // Removed: wallpaperDocRef is no longer needed since incrementViews() now uses getWallpaperDocumentReference()

    companion object {
        private val unlockedWallpapers = Collections.synchronizedSet(mutableSetOf<String>()) // Set of unlocked wallpaper IDs
        private val unlockTimestamps = ConcurrentHashMap<String, Long>() // wallpaperId -> unlock timestamp
        private const val UNLOCK_DURATION_MS = 2 * 60 * 1000 // 2 minutes in milliseconds
    }

    init {
        _backgroundColor.value = initialBackgroundColor
        // Observe Room DB favorites so isFavorite stays in sync with FavoritesViewModel changes
        viewModelScope.launch {
            favoritesRepository.favorites.collect { favorites ->
                val favoriteIds = favorites.mapNotNull { it.id }.toSet()
                _wallpaper.value?.let { current ->
                    val newIsFavorite = favoriteIds.contains(current.id)
                    if (current.isFavorite != newIsFavorite) {
                        _wallpaper.value = current.copy(isFavorite = newIsFavorite)
                    }
                }
            }
        }
        // Clear ad-watch unlock cache when user signs out (premium → false).
        // Without this, wallpapers unlocked during a premium session remain accessible
        // in-memory after sign-out until the app is restarted.
        viewModelScope.launch {
            var previousIsPremium = adManager.isPremiumUser.value
            adManager.isPremiumUser.collect { isPremium ->
                if (previousIsPremium && !isPremium) {
                    unlockedWallpapers.clear()
                    unlockTimestamps.clear()
                    Log.d("DetailViewModel", "User signed out — unlock cache cleared")
                }
                previousIsPremium = isPremium
            }
        }
    }

    /**
     * Load wallpaper details with source screen parameter for proper ordering
     */
    fun loadWallpaper(sourceScreen: String, wallpaperId: String, updateFlow: Boolean = false) {
        Log.d("DetailViewModel", "Loading wallpaper: $wallpaperId from source: $sourceScreen")
        setSourceScreen(sourceScreen)
        
        // Cancel any existing loading job
        loadingJob?.cancel()
        
        // Start new loading job
        loadingJob = viewModelScope.launch {
            _isLoading.value = true
            // Reset view increment flag when loading a new wallpaper.
            hasIncrementedViews = false
            
            try {
                // Check if we already loaded this wallpaper to prevent duplicate loading
                if (currentWallpaperId == wallpaperId && _wallpaper.value != null) {
                    Log.d("DetailViewModel", "Wallpaper $wallpaperId already loaded, using cached version")
                    _isLoading.value = false
                    
                    _wallpaper.value?.let { wp ->
                        loadMetadataInBackground(wp)
                    }
                    return@launch
                }
                
                // Store current ID being loaded
                currentWallpaperId = wallpaperId
                _localDownloads.value = null
                _localViews.value = null
                
                // FAVORITES FIX: Use the proper collection determination function
                determineCollectionFromSource(sourceScreen, wallpaperId)
                
            } catch (e: Exception) {
                Log.e("DetailViewModel", "Error in loadWallpaper: ${e.message}")
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Synchronously determines the collection name based on current source screen
     * This is used when we need the collection name immediately without async operations
     */
    private fun determineCollectionFromSourceSync(): String {
        val collection = when {
            // FAVORITES FIX: Handle favorites source screen
            sourceScreen == AppConfig.SOURCE_FAVORITES -> AppConfig.SOURCE_FAVORITES // Special identifier for favorites
            
            // Home tab uses the main collection
            sourceScreen == AppConfig.SOURCE_HOME || sourceScreen.startsWith(AppConfig.SOURCE_HOME) -> {
                AppConfig.COLLECTION_HOME
            }

            // Apple category is stored in Apple; all other category wallpapers are in TrendingWallpapers.
            sourceScreen.startsWith(AppConfig.SOURCE_CATEGORY) -> {
                val sourceCategory = sourceScreen.split(":").getOrNull(1)
                if (sourceCategory.equals(AppConfig.COLLECTION_HOME, ignoreCase = true)) {
                    AppConfig.COLLECTION_HOME
                } else {
                    AppConfig.COLLECTION_TRENDING
                }
            }

            // All other wallpapers (trending, etc.) are in TrendingWallpapers
            else -> {
                AppConfig.COLLECTION_TRENDING
            }
        }
        
        Log.d("DetailViewModel", "FAVORITES DEBUG: determineCollectionFromSourceSync - sourceScreen='$sourceScreen' -> collection='$collection'")
        return collection
    }
    
    /**
     * Determines the correct collection name based on source screen and loads adjacent wallpapers
     */
    private fun determineCollectionFromSource(sourceScreen: String, wallpaperId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Reset state for clean loading
                _wallpaper.value = null
                
                // FAVORITES FIX: Use centralized collection determination that handles favorites
                val collectionName = determineCollectionFromSourceSync()
                
                Log.d("DetailViewModel", "FAVORITES DEBUG: determineCollectionFromSource - determined collection='$collectionName' for sourceScreen='$sourceScreen'")
                
                // FAVORITES FIX: Handle favorites specially - don't try to load from Firestore
                if (collectionName == AppConfig.SOURCE_FAVORITES) {
                    Log.d("DetailViewModel", "FAVORITES DEBUG: Favorites detected - loading from local repository")
                    // For favorites, we need to load the wallpaper from local favorites first
                    loadWallpaperFromFavorites(wallpaperId)
                    return@launch
                }
                
                // Load the wallpaper data from the appropriate Firestore collection
                loadWallpaperData(collectionName, wallpaperId)
                
                // We don't need to load adjacent wallpapers here anymore as loadWallpaperData will handle it
            } catch (e: Exception) {
                Log.e("DetailViewModel", "Error determining collection: ${e.message}")
                _isLoading.value = false
            }
        }
    }

    /**
     * FAVORITES FIX: Load wallpaper from local favorites repository
     */
    private suspend fun loadWallpaperFromFavorites(wallpaperId: String) {
        try {
            Log.d("DetailViewModel", "FAVORITES DEBUG: Loading wallpaper $wallpaperId from favorites")
            
            // Get the current favorites list
            val favoritesList = favoritesRepository.favorites.first()
            
            // Find the wallpaper in favorites
            val wallpaper = favoritesList.find { it.id == wallpaperId }
            
            if (wallpaper != null) {
                Log.d("DetailViewModel", "FAVORITES DEBUG: Found wallpaper in favorites: ${wallpaper.wallpaperName}")
                
                // Set the wallpaper data
                _wallpaper.value = wallpaper
                seedLocalStats(wallpaper)
                
                // Update current wallpaper ID
                currentWallpaperId = wallpaperId
                // Load metadata in background
                loadMetadataInBackground(wallpaper)
                
                _isLoading.value = false
                
                loadAdjacentWallpapersFromFavorites(wallpaperId)

                if (AppConfig.IS_DEBUG) {
                    Log.d("DetailViewModel", "FAVORITES DEBUG: Successfully loaded wallpaper from favorites")
                }

            } else {
                Log.e("DetailViewModel", "FAVORITES DEBUG: Wallpaper $wallpaperId not found in favorites")
                _isLoading.value = false
            }
            
        } catch (e: Exception) {
            Log.e("DetailViewModel", "FAVORITES DEBUG: Error loading wallpaper from favorites: ${e.message}")
            _isLoading.value = false
        }
    }

    /**
     * Loads wallpaper data from Firestore
     */
    private fun loadWallpaperData(collection: String, wallpaperId: String) {
        // Add a global log to track every call to this function
        Log.d("WALLPAPER_DEBUG", "Attempting to load wallpaper: $wallpaperId from collection: $collection")

        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Search the resolved collection first, then fall back to the other known
                // wallpaper collections. A banner or deep link only carries a document id,
                // not the collection it lives in, so the same id can point at a wallpaper in
                // either "Apple" (home) or "TrendingWallpapers". Searching both makes the
                // lookup independent of which tab the source guessed.
                val (foundDoc, foundCollection) = traceAsync("detail_wallpaper_doc_load", wallpaperId.hashCode()) {
                    findWallpaperDocument(collection, wallpaperId)
                }

                if (foundDoc != null) {
                    if (AppConfig.IS_DEBUG) {
                        Log.d("DetailViewModel", "Found wallpaper in collection: $foundCollection")
                    }
                    val wallpaperWithId = Wallpaper.fromDocument(foundDoc)
                    if (wallpaperWithId.imageUrl.isNotBlank() || wallpaperWithId.thumbnail.isNotBlank()) {
                        _wallpaper.value = wallpaperWithId
                        seedLocalStats(wallpaperWithId)

                        // Update the current ID if requested (affects navigation)
                        if (wallpaperId != currentWallpaperId) {
                            currentWallpaperId = wallpaperId
                        }

                        loadMetadataInBackground(wallpaperWithId)
                        // Use the collection the doc was actually found in so that
                        // swiping to adjacent wallpapers queries the right collection.
                        loadAdjacentWallpapers(foundCollection, wallpaperId)

                        // Make sure loading is finished
                        _isLoading.value = false
                    } else {
                        Log.e("DetailViewModel", "Wallpaper data is null for document: $wallpaperId")
                        _isLoading.value = false
                    }
                } else {
                    Log.e("DetailViewModel", "Wallpaper document not found in any collection: $wallpaperId")
                    _isLoading.value = false
                }
            } catch (e: Exception) {
                Log.e("DetailViewModel", "Error loading wallpaper: $wallpaperId", e)
                _isLoading.value = false
            }
        }
    }

    /**
     * Looks up a wallpaper document by id, trying [primaryCollection] first and then any
     * remaining collections in [AppConfig.WALLPAPER_SEARCH_COLLECTIONS]. Returns the first
     * existing document with the collection it was found in, or (null, primaryCollection)
     * if the id exists in none of them.
     */
    private suspend fun findWallpaperDocument(
        primaryCollection: String,
        wallpaperId: String
    ): Pair<com.google.firebase.firestore.DocumentSnapshot?, String> {
        // Primary collection first, then the rest — de-duplicated, order preserved.
        val collectionsToSearch = (listOf(primaryCollection) + AppConfig.WALLPAPER_SEARCH_COLLECTIONS)
            .distinct()

        for (col in collectionsToSearch) {
            try {
                val doc = withTimeout(5000) {
                    firestore.collection(col)
                        .document(wallpaperId)
                        .get()
                        .await()
                }
                if (doc.exists()) {
                    return doc to col
                } else if (AppConfig.IS_DEBUG) {
                    Log.d("DetailViewModel", "Wallpaper not found in collection: $col")
                }
            } catch (e: Exception) {
                // A failure in one collection shouldn't abort the search — try the next.
                Log.e("DetailViewModel", "Error querying collection $col for $wallpaperId: ${e.message}")
            }
        }
        return null to primaryCollection
    }

    private fun seedLocalStats(wallpaper: Wallpaper) {
        _localDownloads.value = wallpaper.downloads
        _localViews.value = wallpaper.views + if (lastViewIncrementKey == counterKey(sourceScreen, wallpaper.id)) 1 else 0
    }

    private fun applyFirestoreMetadata(wallpaper: Wallpaper) {
        _imageDimensions.value = wallpaper.dimensions.ifBlank { "Not available" }
        _imageSize.value = wallpaper.size.ifBlank { "Not available" }
    }

    private fun loadMetadataInBackground(wallpaper: Wallpaper) {
        metadataJob?.cancel()
        applyFirestoreMetadata(wallpaper)
    }

    private fun processWallpaperMetadata(wallpaper: Wallpaper?) {
        wallpaper?.let { wp ->
            applyFirestoreMetadata(wp)
            incrementViewsAndCheckFavorite(wp)
        }
    }
    
    // Helper function to handle common tasks after metadata processing
    private fun incrementViewsAndCheckFavorite(wallpaper: Wallpaper) {
        // Seed local counters from the wallpaper data for instant UI display
        if (_localDownloads.value == null) _localDownloads.value = wallpaper.downloads
        if (_localViews.value == null) _localViews.value = wallpaper.views

        // Increment views after successfully loading the wallpaper
        if (!hasIncrementedViews) {
            hasIncrementedViews = true
            incrementViews(sourceScreen, wallpaper.id)
        }

        // Check if it's a favorite
        viewModelScope.launch {
            val isFavorite = favoritesRepository.isFavorite(wallpaperId)
            _wallpaper.value = wallpaper.copy(
                isFavorite = isFavorite
            )
        }
    }

    private fun incrementViews() {
        if (hasIncrementedViews) return

        viewModelScope.launch {
            try {
                // FIXED: Use the same logic as public incrementViews method
                // Get the correct document reference using source and wallpaper ID
                val docRef = getWallpaperDocumentReference(sourceScreen, wallpaperId)
                if (docRef != null) {
                    val updated = firestore.runTransaction { transaction ->
                        val snapshot = transaction.get(docRef)
                        if (!snapshot.exists()) {
                            false
                        } else {
                            transaction.update(docRef, "views", FieldValue.increment(1))
                            true
                        }
                    }.await()
                    hasIncrementedViews = true
                    if (updated) {
                        Log.d("DetailViewModel", "Views incremented for wallpaper: $wallpaperId")
                    } else {
                        Log.w("DetailViewModel", "Skipped view increment because document does not exist: ${docRef.path}")
                    }
                } else {
                    Log.w("DetailViewModel", "Could not find document reference for wallpaper: $wallpaperId from source: $sourceScreen")
                }
            } catch (e: CancellationException) {
                // Expected: ViewModel was cleared before the coroutine finished (user navigated away)
                throw e  // Re-throw so coroutine machinery handles it properly
            } catch (e: Exception) {
                Log.e("DetailViewModel", "Error incrementing views for wallpaper: $wallpaperId (Ask Gemini)", e)
            }
        }
    }

    fun toggleFavorite() {
        viewModelScope.launch {
            _wallpaper.value?.let { currentWallpaper ->
                try {
                    favoritesRepository.toggleFavorite(currentWallpaper)
                    // Update the wallpaper object with new favorite status
                    _wallpaper.value = currentWallpaper.copy(
                        isFavorite = !currentWallpaper.isFavorite
                    )
                    
                    // Show feedback to user
                    val message = if (currentWallpaper.isFavorite) {
                        "Removed from favorites"
                    } else {
                        "Added to favorites"
                    }
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Log.e("DetailViewModel", "Error toggling favorite", e)
                    Toast.makeText(context, "Error updating favorites", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun downloadWallpaper(
        context: Context,
        wallpaper: Wallpaper,
        imageUrlOverride: String? = null,
        showToast: Boolean = true,
        onComplete: () -> Unit = {}
    ) {
        if (isDownloading.value) {
            // Avoid multiple concurrent downloads
            Log.d("DetailViewModel", "Download already in progress, ignoring duplicate request")
            return
        }
        
        // Set downloading flag at the very beginning
        _isDownloading.value = true
        _downloadProgress.value = 0f
                
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Use jpg extension for better compatibility
                val fileName = "${wallpaper.wallpaperName}_${wallpaper.id.takeLast(4)}.jpg"
                
                // Ensure download directory exists
                val downloadDir = DownloadManager.getWallpapersFolder(context)
                if (!downloadDir.exists()) {
                    Log.e("DetailViewModel", "Failed to create download directory")
                    if (showToast) {
                        withContext(Dispatchers.Main) {
                            toastManager.showToast(
                                message = "Failed to create download directory",
                                icon = Icons.Default.Error,
                                duration = 3000
                            )
                        }
                    }
                    _isDownloading.value = false
                    return@launch
                }
                
                // Create the file object (we'll download regardless if it exists)
                val file = File(downloadDir, fileName)
                
                // Download the wallpaper regardless if it exists already
                try {
                    var downloadSuccess = false
                    
                    val targetUrl = imageUrlOverride?.takeIf { it.isNotBlank() } ?: wallpaper.imageUrl
                    DownloadManager.downloadWallpaper(context, targetUrl, fileName)
                        .collect { progress ->
                            _downloadProgress.value = progress
                            if (progress >= 1f) {
                                downloadSuccess = true
                            }
                        }
                    
                    if (downloadSuccess) {
                        Log.d("DetailViewModel", "Download completed successfully: ${file.absolutePath}")
                        
                        // ANALYTICS: Increment download count in Firestore
                        val currentWallpaper = _wallpaper.value
                        if (currentWallpaper != null) {
                            incrementDownloads(sourceScreen, currentWallpaper.id)
                        }
                        
                        // Notify media scanner to make the image appear in gallery
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            // For Android 10+ (API 29+), we don't need to do anything here
                            // because MediaStore already handles scanning
                            Log.d("DetailViewModel", "Android 10+ - Media store insertion already handled")
                        } else {
                            // For older Android versions, use MediaScannerConnection
                            MediaScannerConnection.scanFile(
                                context,
                                arrayOf(file.absolutePath),
                                arrayOf("image/jpeg"),
                                null
                            )
                            Log.d("DetailViewModel", "Media scanner scan completed for: ${file.absolutePath}")
                        }
                        
                        withContext(Dispatchers.Main) {
                            if (showToast) {
                                Toast.makeText(context, "Wallpaper saved to gallery", Toast.LENGTH_SHORT).show()
                            }
                            // Reset the downloading state after success
                            _isDownloading.value = false
                            // Call the completion callback
                            onComplete()
                        }
                    } else {
                        Log.e("DetailViewModel", "Download failed")
                        withContext(Dispatchers.Main) {
                            toastManager.showToast(
                                message = "Download failed",
                                icon = Icons.Default.Error,
                                duration = 3000
                            )
                            _isDownloading.value = false
                            _downloadProgress.value = 0f
                        }
                    }
                } catch (e: Exception) {
                    Log.w("DetailViewModel", "Original wallpaper download failed, trying thumbnail fallback", e)
                    if (wallpaper.thumbnail.isBlank() || wallpaper.thumbnail == wallpaper.imageUrl) {
                        withContext(Dispatchers.Main) {
                            toastManager.showToast(
                                message = "Error downloading wallpaper",
                                icon = Icons.Default.Error,
                                duration = 3000
                            )
                            _isDownloading.value = false
                            _downloadProgress.value = 0f
                        }
                        return@launch
                    }
                    try {
                        var fallbackSuccess = false
                        DownloadManager.downloadWallpaper(context, wallpaper.thumbnail, fileName)
                            .collect { progress ->
                                _downloadProgress.value = progress
                                if (progress >= 1f) {
                                    fallbackSuccess = true
                                }
                            }

                        withContext(Dispatchers.Main) {
                            if (fallbackSuccess) {
                                if (showToast) {
                                    Toast.makeText(context, "Wallpaper saved to gallery", Toast.LENGTH_SHORT).show()
                                }
                                incrementDownloads(sourceScreen, wallpaper.id)
                                onComplete()
                            } else {
                                toastManager.showToast(
                                    message = "Download failed",
                                    icon = Icons.Default.Error,
                                    duration = 3000
                                )
                            }
                            _isDownloading.value = false
                            _downloadProgress.value = 0f
                        }
                    } catch (fallbackError: Exception) {
                        Log.e("DetailViewModel", "Thumbnail fallback download failed", fallbackError)
                        withContext(Dispatchers.Main) {
                            toastManager.showToast(
                                message = "Error downloading wallpaper",
                                icon = Icons.Default.Error,
                                duration = 3000
                            )
                            _isDownloading.value = false
                            _downloadProgress.value = 0f
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("DetailViewModel", "Unexpected error in downloadWallpaper", e)
                withContext(Dispatchers.Main) {
                    toastManager.showToast(
                        message = "Unexpected error: ${e.message}",
                        icon = Icons.Default.Error,
                        duration = 3000
                    )
                    _isDownloading.value = false
                }
            }
        }
    }

    private suspend fun ensureWallpaperDownloaded(fileName: String, url: String, useCache: Boolean = false): File? {
        Log.d("DetailViewModel", "Downloading wallpaper: $fileName, useCache: $useCache")
        
        // If using cache, download to cache
        if (useCache) {
            try {
                var downloadedFile: File? = null
                Log.d("DetailViewModel", "Downloading file to cache: $url")
                DownloadManager.downloadToCache(context, url, fileName)
                    .collect { progress ->
                        _downloadProgress.value = progress
                        if (progress >= 1f) {
                            downloadedFile = DownloadManager.getCacheFile(context, fileName)
                            Log.d("DetailViewModel", "Download to cache completed: ${downloadedFile?.absolutePath}, exists: ${downloadedFile?.exists()}, size: ${downloadedFile?.length()} bytes")
                        }
                    }
                return downloadedFile
            } catch (e: Exception) {
                Log.e("DetailViewModel", "Error downloading wallpaper to cache", e)
                return null
            }
        }
        
        // If not using cache, download to public storage
        try {
            var downloadedFile: File? = null
            Log.d("DetailViewModel", "Downloading file to public storage: $url")
            DownloadManager.downloadWallpaper(context, url, fileName)
                .collect { progress ->
                    _downloadProgress.value = progress
                    if (progress >= 1f) {
                        downloadedFile = File(DownloadManager.getWallpapersFolder(context), fileName)
                        Log.d("DetailViewModel", "Download completed to public storage: ${downloadedFile?.absolutePath}, exists: ${downloadedFile?.exists()}, size: ${downloadedFile?.length()} bytes")
                    }
                }
            return downloadedFile
        } catch (e: Exception) {
            Log.e("DetailViewModel", "Error downloading wallpaper to public storage", e)
            return null
        }
    }

    private suspend fun ensureWallpaperDownloaded(
        fileName: String,
        wallpaper: Wallpaper,
        useCache: Boolean = false
    ): File? {
        ensureWallpaperDownloaded(fileName, wallpaper.imageUrl, useCache)?.let { return it }

        val fallbackUrl = wallpaper.thumbnail
        if (fallbackUrl.isNotBlank() && fallbackUrl != wallpaper.imageUrl) {
            Log.w("DetailViewModel", "Primary wallpaper URL failed, falling back to thumbnail: ${wallpaper.id}")
            return ensureWallpaperDownloaded(fileName, fallbackUrl, useCache)
        }

        return null
    }

    fun setWallpaper(
        context: Context,
        wallpaper: Wallpaper,
        option: WallpaperSetOption,
        onComplete: () -> Unit = {}
    ) {
        if (isSettingWallpaperInProgress) {
            Log.d("DetailViewModel", "Wallpaper setting already in progress, ignoring request")
            return
        }
        
        // Set the flag immediately to prevent UI changes
        isSettingWallpaperInProgress = true
        _isSettingWallpaper.value = true
        
        viewModelScope.launch {
            try {
                Log.d("DetailViewModel", "Starting to set wallpaper with option: $option")
                // Use jpg extension for better compatibility
                val fileName = "${wallpaper.wallpaperName}_${wallpaper.id.takeLast(4)}.jpg"
                
                // Use cache for setting wallpaper to prevent UI blinking
                val file = cachedWallpaperFile ?: ensureWallpaperDownloaded(fileName, wallpaper, useCache = true)
                
                if (file == null) {
                    withContext(Dispatchers.Main) {
                        onComplete() // Call onComplete even on failure
                    }
                    return@launch
                }

                cachedWallpaperFile = file
                Log.d("DetailViewModel", "Wallpaper file prepared: ${file.absolutePath}, size: ${file.length()} bytes")

                // Use a separate scope for the actual wallpaper setting to prevent UI thread blocking
                withContext(Dispatchers.Default) {
                    val wallpaperManager = WallpaperManager.getInstance(context)

                    // Stream the file straight to the system instead of decoding it here.
                    // setStream() lets WallpaperManager do its own scaling in the system
                    // process, so a 4K image never becomes a ~32 MB Bitmap in ours — this
                    // is what OOMs on 4-6 GB devices, and what Play Console flags as
                    // "BitmapFactory without downsampling".
                    //
                    // Downsampling on our side can't win here: desiredMinimumWidth is about
                    // twice the screen width for parallax, so inSampleSize would stay at 1
                    // for exactly the large images that cause the problem.
                    fun stream(flags: Int): Boolean = try {
                        file.inputStream().use { input ->
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                wallpaperManager.setStream(input, null, true, flags)
                            } else {
                                wallpaperManager.setStream(input)
                            }
                        }
                        true
                    } catch (e: Exception) {
                        Log.e("DetailViewModel", "Error setting wallpaper (flags=$flags)", e)
                        false
                    } catch (e: OutOfMemoryError) {
                        Log.e("DetailViewModel", "OOM setting wallpaper (flags=$flags)", e)
                        false
                    }

                    val success = when (option) {
                        WallpaperSetOption.HOME_SCREEN -> {
                            Log.d("DetailViewModel", "Setting as home screen wallpaper")
                            stream(WallpaperManager.FLAG_SYSTEM)
                        }
                        WallpaperSetOption.LOCK_SCREEN -> {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                Log.d("DetailViewModel", "Setting as lock screen wallpaper")
                                stream(WallpaperManager.FLAG_LOCK)
                            } else {
                                // FLAG_LOCK did not exist before N; there is no lock-only API.
                                false
                            }
                        }
                        WallpaperSetOption.BOTH_SCREENS -> {
                            Log.d("DetailViewModel", "Setting as both home and lock screen wallpaper")
                            stream(WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK)
                        }
                        WallpaperSetOption.EXTERNAL -> {
                            Log.d("DetailViewModel", "Opening external wallpaper setter")
                            // For external, we just download and let the UI handle opening the gallery
                            true
                        }
                    }

                    if (success) {
                        Log.d("DetailViewModel", "Wallpaper set successfully")
                        withContext(Dispatchers.Main) {
                            _localDownloads.value = (_localDownloads.value ?: 0) + 1
                            incrementDownloads(sourceScreen, wallpaper.id)
                            onComplete() // Show ad after successful wallpaper set
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("DetailViewModel", "Error setting wallpaper", e)
                withContext(Dispatchers.Main) {
                    onComplete() // Call onComplete even on error
                }
            } finally {
                // Add a longer delay before resetting UI state to prevent blinking
                delay(500)
                Log.d("DetailViewModel", "Finished setting wallpaper process")
                
                // Reset the flags on the main thread to ensure UI consistency
                withContext(Dispatchers.Main) {
                    isSettingWallpaperInProgress = false
                    _isSettingWallpaper.value = false
                }
            }
        }
    }

    fun shareWallpaper(
        context: Context,
        wallpaper: Wallpaper,
        onComplete: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _isSharing.value = true
            
            try {
                // Use jpg extension for better compatibility
                val fileName = "${wallpaper.wallpaperName}_${wallpaper.id.takeLast(4)}.jpg"
                
                // First ensure the file is downloaded - use regular storage for sharing
                Log.d("DetailViewModel", "Starting share process for: $fileName")
                
                // Share from app cache through FileProvider. This avoids public-storage writes
                // and still grants the receiving app temporary read access.
                val file = ensureWallpaperDownloaded(fileName, wallpaper, useCache = true)
                if (file == null) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Failed to prepare wallpaper for sharing", Toast.LENGTH_SHORT).show()
                    }
                    _isSharing.value = false
                    return@launch
                }
                
                // Log the file path to debug
                Log.d("DetailViewModel", "Sharing file: ${file.absolutePath}, exists: ${file.exists()}, size: ${file.length()} bytes")
                
                try {
                    // Create URI using FileProvider
                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.provider",  // Match the authority in AndroidManifest.xml
                        file
                    )
                    
                    Log.d("DetailViewModel", "Created URI for sharing: $uri")
                    
                    // Create and start share intent
                    val shareIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        putExtra(Intent.EXTRA_STREAM, uri)
                        
                        // Set MIME type - very important for proper previews
                        type = "image/jpeg"
                        
                        // Set a title for the share - helps with previews
                        putExtra(Intent.EXTRA_TITLE, wallpaper.wallpaperName)
                        
                        // Android 10+ specific configuration for better sharing experience
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            // Setting clipData is crucial for showing thumbnails in modern Android versions
                            val clipData = ClipData.newUri(context.contentResolver, "Wallpaper Image", uri)
                            this.clipData = clipData
                        }
                        
                        // Add subject and text
                        putExtra(Intent.EXTRA_SUBJECT, "Check out this wallpaper!")
                        putExtra(
                            Intent.EXTRA_TEXT,
                            "Check out this amazing wallpaper \"${wallpaper.wallpaperName}\"${AppConfig.SHARE_WALLPAPER_TEXT_SUFFIX}https://play.google.com/store/apps/details?id=${context.packageName}"
                        )
                    }

                    // Explicitly grant permission to receiving apps
                    val resInfoList = context.packageManager.queryIntentActivities(
                        shareIntent, PackageManager.MATCH_DEFAULT_ONLY
                    )
                    for (resolveInfo in resInfoList) {
                        val packageName = resolveInfo.activityInfo.packageName
                        context.grantUriPermission(
                            packageName, uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    }

                    // Create a chooser with a clear title
                    val chooserIntent = Intent.createChooser(shareIntent, "Share Wallpaper via")
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

                    // Use a callback to show the ad when returning to the app
                    withContext(Dispatchers.Main) {
                        try {
                            context.startActivity(chooserIntent)
                            Log.d("DetailViewModel", "Share intent started successfully")
                            delay(1000) // Wait for user to return
                            onComplete() // Show ad after delay
                        } catch (e: Exception) {
                            Log.e("DetailViewModel", "Error starting share activity", e)
                            Toast.makeText(context, "Error sharing: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    Log.e("DetailViewModel", "Error creating share intent", e)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Error sharing: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("DetailViewModel", "Error in shareWallpaper", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Error sharing wallpaper", Toast.LENGTH_SHORT).show()
                }
            } finally {
                _isSharing.value = false
            }
        }
    }

    // Update resetState to cancel loading job
    fun resetState() {
        loadingJob?.cancel()
        _wallpaper.value = null
        _isLoading.value = true
        _loadingProgress.value = 0f
    }

    override fun onCleared() {
        super.onCleared()
        loadingJob?.cancel()
    }

    /**
     * Check if a wallpaper is unlocked (either permanently or within the time window)
     */
    fun isWallpaperUnlocked(wallpaperId: String): Boolean {
        // Check if it's permanently unlocked
        if (unlockedWallpapers.contains(wallpaperId)) {
            return true
        }
        
        // Check if it's temporarily unlocked (within time window)
        val unlockTime = unlockTimestamps[wallpaperId] ?: 0L
        val currentTime = System.currentTimeMillis()
        val isWithinTimeWindow = (currentTime - unlockTime) < UNLOCK_DURATION_MS
        
        Log.d("DetailViewModel", "Wallpaper $wallpaperId unlock status: ${isWithinTimeWindow}, unlocked at: $unlockTime, current: $currentTime")
        
        return isWithinTimeWindow
    }
    
    /**
     * Mark a wallpaper as unlocked
     */
    fun markWallpaperAsUnlocked(wallpaperId: String) {
        unlockedWallpapers.add(wallpaperId)
        unlockTimestamps[wallpaperId] = System.currentTimeMillis()
        Log.d("DetailViewModel", "Marked wallpaper $wallpaperId as unlocked at ${unlockTimestamps[wallpaperId]}")
    }

    /**
     * Set the wallpaper setting state
     */
    fun setIsSettingWallpaper(isSettingWallpaper: Boolean) {
        _isSettingWallpaper.value = isSettingWallpaper
    }

    /**
     * Compatibility entry point for older callers. Metadata now comes only from Firestore fields
     * on the loaded Wallpaper document.
     */
    fun calculateImageMetadata(imageUrl: String, wallpaperId: String? = null) {
        _wallpaper.value?.let(::applyFirestoreMetadata) ?: setDefaultMetadata()
    }
    
    private fun setDefaultMetadata() {
        _imageDimensions.value = "Not available"
        _imageSize.value = "Not available"
    }

    private fun incrementViewCount(docRef: DocumentReference) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Add a debug log
                Log.d("DetailViewModel", "Starting view count increment for wallpaper: ${docRef.id}")
                
                // Use withTimeout to ensure operation completes even if navigation happens quickly
                withTimeout(3000L) { // 3 seconds max
                    firestore.runTransaction { transaction ->
                        val snapshot = transaction.get(docRef)
                        if (snapshot.exists()) {
                            transaction.update(docRef, "views", FieldValue.increment(1))
                        }
                    }.await()
                    
                    Log.d("DetailViewModel", "View count incremented for wallpaper: ${docRef.id}")
                }
            } catch (e: Exception) {
                // Log but don't crash if view count increment fails
                if (e is TimeoutCancellationException) {
                    Log.e("DetailViewModel", "View count increment timed out for ${docRef.id}")
                } else {
                    Log.e("DetailViewModel", "Error incrementing view count", e)
                }
            }
        }
    }

    /**
     * Increments the download count for a wallpaper in Firestore
     * Uses a shorter timeout to ensure the operation completes even if user navigates away quickly
     */
    private fun incrementDownloadCount(docRef: DocumentReference) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Add a debug log
                Log.d("DetailViewModel", "Starting download count increment for wallpaper: ${docRef.id}")
                
                // Use withTimeout to ensure operation completes even if navigation happens quickly
                withTimeout(3000L) { // 3 seconds max
                    firestore.runTransaction { transaction ->
                        val snapshot = transaction.get(docRef)
                        if (snapshot.exists()) {
                            transaction.update(docRef, "downloads", FieldValue.increment(1))
                        }
                    }.await()
                    
                    Log.d("DetailViewModel", "Download count incremented for wallpaper: ${docRef.id}")
                }
            } catch (e: Exception) {
                // Log but don't crash if download count increment fails
                if (e is TimeoutCancellationException) {
                    Log.e("DetailViewModel", "Download count increment timed out for ${docRef.id}")
                } else {
                    Log.e("DetailViewModel", "Error incrementing download count", e)
                }
            }
        }
    }

    /**
     * Special download function for External option that doesn't update isDownloading state
     * to prevent UI conflicts with the Apply button
     */
    fun downloadWallpaperForExternal(
        context: Context,
        wallpaper: Wallpaper,
        onComplete: (String) -> Unit
    ) {
        // Don't set isDownloading flag to avoid UI conflict with Apply button
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Use jpg extension for better compatibility
                val fileName = "${wallpaper.wallpaperName}_${wallpaper.id.takeLast(4)}.jpg"
                
                // Ensure download directory exists
                val downloadDir = DownloadManager.getWallpapersFolder(context)
                if (!downloadDir.exists()) {
                    Log.e("DetailViewModel", "Failed to create download directory")
                    withContext(Dispatchers.Main) {
                        onComplete("")
                    }
                    return@launch
                }
                
                // Check if file already exists
                val file = File(downloadDir, fileName)

                
                // Download the wallpaper
                try {
                    var downloadSuccess = false
                    
                    DownloadManager.downloadWallpaper(context, wallpaper.imageUrl, fileName)
                        .collect { progress ->
                            // Don't update UI progress to avoid conflict
                            if (progress >= 1f) {
                                downloadSuccess = true
                            }
                        }
                    
                    if (downloadSuccess) {
                        Log.d("DetailViewModel", "External download completed successfully: ${file.absolutePath}")
                        
                        // ANALYTICS: Increment download count in Firestore
                        incrementDownloads(sourceScreen, wallpaper.id)
                        
                        // Make sure the file is visible in gallery - using multiple methods
                        try {
                            // Method 1: MediaScannerConnection with callback
                            MediaScannerConnection.scanFile(
                                context,
                                arrayOf(file.absolutePath),
                                arrayOf("image/jpeg"),
                                object : MediaScannerConnection.OnScanCompletedListener {
                                    override fun onScanCompleted(path: String?, uri: Uri?) {
                                        Log.d("DetailViewModel", "MediaScanner completed for: $path with URI: $uri")
                                    }
                                }
                            )
                            
                            // Method 2: Direct MediaStore insertion for Android 10+
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                val contentValues = ContentValues().apply {
                                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                                    put(MediaStore.Images.Media.IS_PENDING, 0)
                                    put(MediaStore.Images.Media.DATA, file.absolutePath)
                                    put(MediaStore.Images.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
                                    put(MediaStore.Images.Media.DATE_MODIFIED, System.currentTimeMillis() / 1000)
                                }
                                
                                val resolver = context.contentResolver
                                resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                            }
                            
                            // Notify gallery for older devices
                            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                                MediaScannerConnection.scanFile(
                                    context,
                                    arrayOf(file.absolutePath),
                                    arrayOf("image/jpeg"),
                                    null
                                )
                                Log.d("DetailViewModel", "Media scanner scan completed for: ${file.absolutePath}")
                            }
                        } catch (e: Exception) {
                            Log.e("DetailViewModel", "Error updating gallery", e)
                        }
                        
                        withContext(Dispatchers.Main) {
                            onComplete(file.absolutePath)
                        }
                    } else {
                        Log.e("DetailViewModel", "External download failed")
                        withContext(Dispatchers.Main) {
                            onComplete("")
                        }
                    }
                } catch (e: Exception) {
                    Log.w("DetailViewModel", "Original external download failed, trying thumbnail fallback", e)
                    if (wallpaper.thumbnail.isBlank() || wallpaper.thumbnail == wallpaper.imageUrl) {
                        withContext(Dispatchers.Main) {
                            onComplete("")
                        }
                        return@launch
                    }
                    try {
                        var fallbackSuccess = false
                        DownloadManager.downloadWallpaper(context, wallpaper.thumbnail, fileName)
                            .collect { progress ->
                                if (progress >= 1f) {
                                    fallbackSuccess = true
                                }
                            }
                        withContext(Dispatchers.Main) {
                            onComplete(if (fallbackSuccess) file.absolutePath else "")
                        }
                    } catch (fallbackError: Exception) {
                        Log.e("DetailViewModel", "Thumbnail fallback external download failed", fallbackError)
                        withContext(Dispatchers.Main) {
                            onComplete("")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("DetailViewModel", "Unexpected error in external download", e)
                withContext(Dispatchers.Main) {
                    onComplete("")
                }
            }
        }
    }

    private fun updateAdjacentIndices() {
        if (currentIndex != -1 && wallpaperIds.isNotEmpty()) {
            nextIndex = if (currentIndex < wallpaperIds.size - 1) currentIndex + 1 else -1
            prevIndex = if (currentIndex > 0) currentIndex - 1 else -1
            nextWallpaperId = if (nextIndex != -1) wallpaperIds[nextIndex] else null
            prevWallpaperId = if (prevIndex != -1) wallpaperIds[prevIndex] else null
        } else {
            nextIndex = -1
            prevIndex = -1
            nextWallpaperId = null
            prevWallpaperId = null
        }
    }

    private fun loadAdjacentWallpapers(collection: String, currentId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            loadAdjacentWallpapersBlocking(collection, currentId)
        }
    }

    private suspend fun loadAdjacentWallpapersBlocking(collection: String, currentId: String) {
        withContext(Dispatchers.IO) {
            try {
                _isLoadingAdjacentWallpapers.value = true
                if (collection == AppConfig.SOURCE_FAVORITES) {
                    loadAdjacentWallpapersFromFavorites(currentId)
                } else {
                    val (sortField, sortDirection) = parseSortOption().toFirestoreSort()
                    var query: Query = firestore.collection(collection)
                        .orderBy(sortField, sortDirection)
                        .limit(80)

                    if (sourceScreen.startsWith(AppConfig.SOURCE_CATEGORY) && sourceScreen.contains(":")) {
                        val parts = sourceScreen.split(":")
                        if (parts.size > 1) {
                            query = query.whereEqualTo("category", parts[1])
                            if (parts.size > 2 && parts[2] != "null" && parts[2].isNotBlank()) {
                                query = query.whereEqualTo("subCategory", parts[2])
                            }
                        }
                    }

                    val docs = withTimeout(8000) { query.get().await() }
                    if (!docs.isEmpty) {
                        wallpaperIds.clear()
                        wallpaperIds.addAll(docs.documents.map { it.id })
                        adjacentWallpapersMap.clear()
                        docs.documents.forEach { doc ->
                            adjacentWallpapersMap[doc.id] = Wallpaper.fromDocument(doc)
                        }

                        currentIndex = wallpaperIds.indexOf(currentId)
                        if (currentIndex == -1) {
                            wallpaperIds.add(0, currentId)
                            currentIndex = 0
                        }
                        currentCollection = collection
                        updateAdjacentIndices()
                    }
                }
            } catch (e: Exception) {
                if (AppConfig.IS_DEBUG) {
                    Log.e("DetailViewModel", "Error loading adjacent wallpapers: ${e.message}")
                }
            } finally {
                _isLoadingAdjacentWallpapers.value = false
            }
        }
    }

    private suspend fun loadAdjacentWallpapersFromFavorites(currentId: String) {
        try {
            val favoritesList = favoritesRepository.favorites.first()
            if (favoritesList.isEmpty()) return

            wallpaperIds.clear()
            wallpaperIds.addAll(favoritesList.mapNotNull { it.id })
            currentIndex = wallpaperIds.indexOf(currentId)
            if (currentIndex == -1) return

            adjacentWallpapersMap.clear()
            favoritesList.forEach { wp ->
                wp.id?.let { id -> adjacentWallpapersMap[id] = wp }
            }
            updateAdjacentIndices()
        } finally {
            _isLoadingAdjacentWallpapers.value = false
        }
    }

    suspend fun ensureCollectionLoaded(wallpaperId: String, source: String) {
        if (wallpaperIds.isNotEmpty() && wallpaperIds.contains(wallpaperId)) {
            if (currentIndex == -1) {
                currentIndex = wallpaperIds.indexOf(wallpaperId)
                updateAdjacentIndices()
            }
            return
        }
        val collection = determineCollectionFromSourceSync()
        if (collection == AppConfig.SOURCE_FAVORITES) {
            loadAdjacentWallpapersFromFavorites(wallpaperId)
        } else {
            loadAdjacentWallpapersBlocking(collection, wallpaperId)
        }
    }

    fun getNextWallpaperId(): String? {
        if (wallpaperIds.isEmpty() || currentIndex == -1) return null
        return if (currentIndex < wallpaperIds.size - 1) wallpaperIds[currentIndex + 1] else null
    }

    fun getPreviousWallpaperId(): String? {
        if (wallpaperIds.isEmpty() || currentIndex == -1) return null
        return if (currentIndex > 0) wallpaperIds[currentIndex - 1] else null
    }

    fun getWallpaperById(id: String?): Wallpaper? = id?.let { adjacentWallpapersMap[it] ?: _wallpaper.value?.takeIf { it.id == id } }

    fun getAdjacentWallpaperThumbnail(isNext: Boolean): String? {
        val adjacentId = (if (isNext) getNextWallpaperId() else getPreviousWallpaperId()) ?: return null
        val wp = getWallpaperById(adjacentId) ?: return null
        return wp.thumbnail ?: wp.imageUrl
    }

    fun loadNextWallpaper(): Boolean {
        val nextId = getNextWallpaperId() ?: return false
        viewModelScope.launch {
            runCatching {
                val cached = adjacentWallpapersMap[nextId]
                if (cached != null) {
                    currentIndex = wallpaperIds.indexOf(nextId)
                    currentWallpaperId = nextId
                    _wallpaper.value = cached
                    seedLocalStats(cached)
                    hasIncrementedViews = false
                    updateAdjacentIndices()
                    incrementViews(sourceScreen, nextId)
                    loadMetadataInBackground(cached)
                } else {
                    currentWallpaperId = nextId
                    loadWallpaper(sourceScreen, nextId, updateFlow = false)
                }
            }
        }
        return true
    }

    fun loadPreviousWallpaper(): Boolean {
        val prevId = getPreviousWallpaperId() ?: return false
        viewModelScope.launch {
            runCatching {
                val cached = adjacentWallpapersMap[prevId]
                if (cached != null) {
                    currentIndex = wallpaperIds.indexOf(prevId)
                    currentWallpaperId = prevId
                    _wallpaper.value = cached
                    seedLocalStats(cached)
                    hasIncrementedViews = false
                    updateAdjacentIndices()
                    incrementViews(sourceScreen, prevId)
                    loadMetadataInBackground(cached)
                } else {
                    currentWallpaperId = prevId
                    loadWallpaper(sourceScreen, prevId, updateFlow = false)
                }
            }
        }
        return true
    }

}
