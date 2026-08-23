package com.droidates.wallpapers.viewmodel

import android.app.WallpaperManager
import android.content.ClipData
import android.content.Context
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import com.droidates.wallpapers.BuildConfig
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.ui.graphics.Color
import androidx.core.content.FileProvider
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.droidates.wallpapers.R
import com.droidates.wallpapers.data.repository.FavoritesRepository
import com.droidates.wallpapers.model.Wallpaper
import com.droidates.wallpapers.ui.components.ToastManager
import com.droidates.wallpapers.ui.components.WallpaperSetOption
import com.droidates.wallpapers.ui.screens.PermissionRequestType
import com.droidates.wallpapers.ui.screens.ReportReason
import com.droidates.wallpapers.config.AppConfig
import com.droidates.wallpapers.utils.AdManager
import com.droidates.wallpapers.utils.ContextProvider
import com.droidates.wallpapers.utils.DownloadManager
import com.droidates.wallpapers.utils.ImageUtils
import com.droidates.wallpapers.utils.SortOption
import com.droidates.wallpapers.utils.toFirestoreSort
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.async
import kotlinx.coroutines.selects.select
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.ConcurrentHashMap
import java.util.Collections
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

    // Add a state flow for the current wallpaper ID
    private val _currentWallpaperIdFlow = MutableStateFlow<String?>(null)
    val currentWallpaperIdFlow = _currentWallpaperIdFlow.asStateFlow()

    // Flag to track when navigation is happening via swipe gesture
    private val _isNavigatingViaGesture = MutableStateFlow(false)
    val isNavigatingViaGesture: Boolean
        get() = _isNavigatingViaGesture.value

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
    
    // Track the current collection being used
    private var currentCollection: String? = null
    
    // Track category data
    private var categoryName: String? = null
    private var subcategory: String? = null
    
    // FIXED: Track sort option for consistent adjacent wallpaper loading
    private var currentSortOption: String = "LATEST"
    
    // Adjacent navigation indices
    private var nextIndex: Int = -1
    private var prevIndex: Int = -1
    private var nextWallpaperId: String? = null
    private var prevWallpaperId: String? = null
    
    /**
     * Sets the source screen to determine proper ordering for navigation
     */
    fun setSourceScreen(source: String) {
        sourceScreen = source
        
        // Extract category info from source screen if available
        if (source.startsWith("category:")) {
            val parts = source.split(":")
            if (parts.size > 1) categoryName = parts[1]
            if (parts.size > 2) subcategory = parts[2]
        } else if (source.startsWith(AppConfig.SOURCE_TRENDING)) {
            // Clear any previous category data when coming from trending
            categoryName = null
            subcategory = null
        } else {
            // Clear any previous category data for other screens too
            categoryName = null
            subcategory = null
        }
    }
    
    /**
     * FIXED: Sets the sort option for consistent adjacent wallpaper loading
     */
    fun setSortOption(sortOption: String) {
        Log.d("DetailViewModel", "Setting sort option: $sortOption")
        currentSortOption = sortOption
    }
    
    /**
     * Maps a sort option name string to the Firestore field and direction for ordering queries.
     */
    private fun getSortFieldAndDirection(sortOption: String): Pair<String, Query.Direction> {
        val option = runCatching { SortOption.valueOf(sortOption) }.getOrDefault(SortOption.LATEST)
        return option.toFirestoreSort()
    }

    /**
     * Increments the swipe count for analytics and ad serving
     */
    fun incrementSwipeCount() {
        adManager.incrementWallpaperSwipeCount()
        Log.d("DetailViewModel", "Swipe count incremented")
        
        // Check if we should show an ad after this swipe
        if (adManager.shouldShowAdForWallpaperSwipe()) {
            Log.d("DetailViewModel", "Triggering ad after wallpaper swipe")
            // The ad will be shown by the UI layer when they call showInterstitialAd
        }
    }

    // Cache the downloaded file path
    private var cachedWallpaperFile: File? = null

    // Add this flag
    private var currentWallpaperId: String? = null

    // Add properties for swipe navigation
    private val _adjacentWallpapers = MutableStateFlow<Map<String, Wallpaper>>(emptyMap())
    
    /**
     * Increments the views count for a wallpaper in Firebase
     */
    fun incrementViews(source: String, wallpaperId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // NPE PREVENTION: Check if coroutine is still active
                if (!isActive) return@launch
                
                val docRef = getWallpaperDocumentReference(source, wallpaperId)
                if (docRef != null && isActive) {
                    // Increment views field
                    firestore.runTransaction { transaction ->
                        val snapshot = transaction.get(docRef)
                        val currentViews = snapshot.getLong("views") ?: 0
                        transaction.update(docRef, "views", currentViews + 1)
                    }.await()
                    
                    // Check again before updating UI state
                    Log.d("DetailViewModel", "Views incremented for wallpaper: $wallpaperId")
                } else {
                    Log.w("DetailViewModel", "Could not find document reference for wallpaper: $wallpaperId")
                }
            } catch (e: Exception) {
                Log.e("DetailViewModel", "Error incrementing views for wallpaper: $wallpaperId", e)
            }
        }
    }
    
    /**
     * Increments the downloads count for a wallpaper in Firebase
     */
    fun incrementDownloads(source: String, wallpaperId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // NPE PREVENTION: Check if coroutine is still active
                if (!isActive) return@launch
                
                val docRef = getWallpaperDocumentReference(source, wallpaperId)
                if (docRef != null && isActive) {
                    // Increment downloads field
                    firestore.runTransaction { transaction ->
                        val snapshot = transaction.get(docRef)
                        val currentDownloads = snapshot.getLong("downloads") ?: 0
                        transaction.update(docRef, "downloads", currentDownloads + 1)
                    }.await()
                    
                    // Check again before logging
                    Log.d("DetailViewModel", "Downloads incremented for wallpaper: $wallpaperId")
                } else {
                    Log.w("DetailViewModel", "Could not find document reference for wallpaper: $wallpaperId")
                }
            } catch (e: Exception) {
                Log.e("DetailViewModel", "Error incrementing downloads for wallpaper: $wallpaperId", e)
            }
        }
    }
    
    /**
     * Helper function to get the correct document reference for a wallpaper
     */
    private suspend fun getWallpaperDocumentReference(source: String, wallpaperId: String): DocumentReference? {
        return try {
            when {
                source == AppConfig.COLLECTION_HOME || source == AppConfig.COLLECTION_HOME.lowercase() || source.equals("Apple", ignoreCase = true) -> {
                    firestore.collection(AppConfig.COLLECTION_HOME).document(wallpaperId)
                }
                source == AppConfig.COLLECTION_TRENDING || source.startsWith(AppConfig.SOURCE_TRENDING) -> {
                    firestore.collection(AppConfig.COLLECTION_TRENDING).document(wallpaperId)
                }
                source.startsWith("category:") -> {
                    // FIXED: Category wallpapers are stored in main collections, not subcollections
                    // Try to find in main collections first since that's where they actually are
                    val mainCollections = AppConfig.WALLPAPER_SEARCH_COLLECTIONS
                    for (collection in mainCollections) {
                        val docRef = firestore.collection(collection).document(wallpaperId)
                        val exists = try {
                            docRef.get().await().exists()
                        } catch (e: Exception) {
                            false
                        }
                        if (exists) {
                            Log.d("DetailViewModel", "Found category wallpaper $wallpaperId in collection: $collection")
                            return docRef
                        }
                    }
                    // Fallback: try the old category structure (probably won't exist)
                    val parts = source.split(":")
                    if (parts.size > 1) {
                        val categoryName = parts[1]
                        Log.w("DetailViewModel", "Trying fallback category structure for $wallpaperId")
                        firestore.collection(AppConfig.COLLECTION_CATEGORIES).document(categoryName)
                            .collection(AppConfig.COLLECTION_CATEGORY_WALLPAPERS).document(wallpaperId)
                    } else {
                        null
                    }
                }
                else -> {
                    // Try to find in main collections first
                    val mainCollections = AppConfig.WALLPAPER_SEARCH_COLLECTIONS
                    for (collection in mainCollections) {
                        val docRef = firestore.collection(collection).document(wallpaperId)
                        val exists = try {
                            docRef.get().await().exists()
                        } catch (e: Exception) {
                            false
                        }
                        if (exists) {
                            return docRef
                        }
                    }
                    null
                }
            }
        } catch (e: Exception) {
            Log.e("DetailViewModel", "Error getting document reference for $wallpaperId from $source", e)
            null
        }
    }
    val adjacentWallpapers = _adjacentWallpapers.asStateFlow()
    
    // Adjacent wallpapers for quick navigation
    private val adjacentWallpapersMap = ConcurrentHashMap<String, Wallpaper>()
    private val wallpaperIds = Collections.synchronizedList(mutableListOf<String>())
    private var currentIndex = -1
    
    // Track swipe navigation state
    private val _isLoadingAdjacentWallpapers = MutableStateFlow(false)
    val isLoadingAdjacentWallpapers = _isLoadingAdjacentWallpapers.asStateFlow()
    
    // Track next/previous wallpaper loading states
    private val _isLoadingNextWallpaper = MutableStateFlow(false)
    val isLoadingNextWallpaper = _isLoadingNextWallpaper.asStateFlow()
    
    private val _isLoadingPrevWallpaper = MutableStateFlow(false)
    val isLoadingPrevWallpaper = _isLoadingPrevWallpaper.asStateFlow()
    
    // Removed reference to undefined variable _swipeCount
    
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

    // Add metadata cache
    companion object {
        private val metadataCache = ConcurrentHashMap<String, Pair<String, String>>() // imageUrl -> (dimensions, size)
        private val unlockedWallpapers = Collections.synchronizedSet(mutableSetOf<String>()) // Set of unlocked wallpaper IDs
        private val unlockTimestamps = ConcurrentHashMap<String, Long>() // wallpaperId -> unlock timestamp
        private const val UNLOCK_DURATION_MS = 2 * 60 * 1000 // 2 minutes in milliseconds
        
        // Map wallpaper IDs to their metadata for faster lookup
        private val wallpaperMetadataMap = ConcurrentHashMap<String, Pair<String, String>>() // wallpaperId -> (dimensions, size)
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
            // FIXED: Reset view increment flag when loading new wallpaper for swipe navigation
            hasIncrementedViews = false
            
            try {
                // First check if there's cached metadata for this wallpaper ID
                wallpaperMetadataMap[wallpaperId]?.let { (dimensions, size) ->
                    Log.d("DetailViewModel", "Found cached metadata for wallpaper ID $wallpaperId: $dimensions, $size")
                    // Immediately set metadata without UI flicker
                    _imageDimensions.value = dimensions
                    _imageSize.value = size
                }
                
                // Check if we already loaded this wallpaper to prevent duplicate loading
                if (currentWallpaperId == wallpaperId && _wallpaper.value != null) {
                    Log.d("DetailViewModel", "Wallpaper $wallpaperId already loaded, using cached version")
                    _isLoading.value = false
                    
                    // METADATA FIX: Still ensure metadata is loaded even for cached wallpapers
                    _wallpaper.value?.let { wp ->
                        loadMetadataInBackground(wp)
                    }
                    return@launch
                }
                
                // Store current ID being loaded
                currentWallpaperId = wallpaperId
                
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
    /**
     * FIXED: Correctly map source screens to collections
     * This resolves the issue of wrong collection selection for swipe navigation
     */
    private fun determineCollectionFromSourceSync(): String {
        val collection = when {
            // FAVORITES FIX: Handle favorites source screen
            sourceScreen == AppConfig.SOURCE_FAVORITES -> AppConfig.SOURCE_FAVORITES // Special identifier for favorites
            
            // Home tab uses the main collection
            sourceScreen == AppConfig.SOURCE_HOME || sourceScreen.startsWith(AppConfig.SOURCE_HOME) -> {
                AppConfig.COLLECTION_HOME
            }

            // Category screen wallpapers are filtered from the main collection
            sourceScreen.startsWith(AppConfig.SOURCE_CATEGORY) -> AppConfig.COLLECTION_HOME

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
                
                // Update current wallpaper ID
                currentWallpaperId = wallpaperId
                _currentWallpaperIdFlow.value = wallpaperId
                
                // Load adjacent wallpapers from favorites
                loadAdjacentWallpapers(AppConfig.SOURCE_FAVORITES, wallpaperId)
                
                // Load metadata in background
                loadMetadataInBackground(wallpaper)
                
                _isLoading.value = false
                
                Log.d("DetailViewModel", "FAVORITES DEBUG: Successfully loaded wallpaper from favorites")
                
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
                // Get the wallpaper with timeout to improve responsiveness
                Log.d("DetailViewModel", "Attempting to load from primary collection: $collection")
                var wallpaperDoc = withTimeout(5000) {
                    firestore.collection(collection)
                        .document(wallpaperId)
                        .get()
                        .await()
                }
                
                // Only log in debug mode
                if (wallpaperDoc.exists()) {
                    if (BuildConfig.DEBUG) {
                        Log.d("DetailViewModel", "Found wallpaper in primary collection: $collection")
                    }
                } else {
                    if (BuildConfig.DEBUG) {
                        Log.d("DetailViewModel", "Wallpaper not found in primary collection: $collection")
                    }
                }
                
                // If document doesn't exist in the primary collection, only try the other valid collection
                // FIXED: Only check the two collections that actually exist (TrendingWallpapers and Apple)
                if (!wallpaperDoc.exists()) {
                    // Only try the other valid collection as fallback
                    val fallbackCollection = when (collection) {
                        AppConfig.COLLECTION_TRENDING -> AppConfig.COLLECTION_HOME
                        AppConfig.COLLECTION_HOME -> AppConfig.COLLECTION_TRENDING
                        else -> AppConfig.COLLECTION_TRENDING // Default fallback
                    }
                    
                    if (BuildConfig.DEBUG) {
                        Log.d("DetailViewModel", "Trying fallback collection: $fallbackCollection")
                    }
                    
                    val fallbackDoc = withTimeout(5000) {
                        firestore.collection(fallbackCollection)
                            .document(wallpaperId)
                            .get()
                            .await()
                    }
                    
                    if (fallbackDoc.exists()) {
                        if (BuildConfig.DEBUG) {
                            Log.d("DetailViewModel", "Found wallpaper in fallback collection")
                        }
                        wallpaperDoc = fallbackDoc
                        // Update the current collection to the one where we found the document
                        currentCollection = fallbackCollection
                    }
                }
                
                if (wallpaperDoc.exists()) {
                    // Process the wallpaper
                    val wallpaper = wallpaperDoc.toObject(Wallpaper::class.java)
                    if (wallpaper != null) {
                        // Create a new instance with the document ID
                        val wallpaperWithId = wallpaper.copy(id = wallpaperDoc.id)
                        _wallpaper.value = wallpaperWithId
                        
                        // Update the current ID if requested (affects navigation)
                        if (wallpaperId != currentWallpaperId) {
                            currentWallpaperId = wallpaperId
                            _currentWallpaperIdFlow.value = wallpaperId
                        }
                        
                        // Load metadata and adjacent wallpapers in parallel
                        val metadataJob = viewModelScope.launch { 
                            loadMetadataInBackground(wallpaperWithId) 
                        }
                        
                        val adjacentJob = viewModelScope.launch { 
                            val collectionToUse = currentCollection ?: determineCollectionFromSourceSync()
                            loadAdjacentWallpapers(collectionToUse, wallpaperId) 
                        }
                        
                        // Only wait for adjacent wallpapers to load, not metadata
                        adjacentJob.join()
                        
                        // OPTIMIZATION: Trigger prefetching of the next batch of wallpapers
                        // This ensures smooth scrolling experience by preloading wallpapers before the user needs them
                        prefetchNextBatch(3)
                        
                        // Make sure loading is finished
                        _isLoading.value = false
                    } else {
                        Log.e("DetailViewModel", "Wallpaper data is null for document: $wallpaperId")
                        _isLoading.value = false
                    }
                } else {
                    Log.e("DetailViewModel", "Wallpaper document does not exist in any collection: $wallpaperId")
                    _isLoading.value = false
                    // Try to load adjacent wallpapers anyway to show something
                    viewModelScope.launch {
                        // Try all collections in sequence
                        for (fallbackCollection in listOf(AppConfig.COLLECTION_TRENDING, "Wallpapers", "DepthEffectWallpapers")) {
                            Log.d("DetailViewModel", "Last resort: attempting to load adjacent wallpapers from $fallbackCollection")
                            loadAdjacentWallpapers(fallbackCollection, wallpaperId)
                            
                            // If we have wallpaper IDs now, we can stop
                            if (wallpaperIds.isNotEmpty()) {
                                Log.d("DetailViewModel", "Found ${wallpaperIds.size} wallpapers in $fallbackCollection")
                                break
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("DetailViewModel", "Error loading wallpaper: $wallpaperId", e)
                _isLoading.value = false
            }
        }
    }

    /**
     * Loads adjacent wallpapers for swipe navigation
     */
    private fun loadAdjacentWallpapers(
        collection: String,
        currentId: String
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _isLoadingAdjacentWallpapers.value = true
                
                Log.d("DetailViewModel", "FAVORITES DEBUG: loadAdjacentWallpapers called with collection='$collection', currentId='$currentId', sourceScreen='$sourceScreen'")
                
                // FAVORITES FIX: Handle favorites source specially
                if (collection == "favorites") {
                    Log.d("DetailViewModel", "FAVORITES DEBUG: Triggering loadAdjacentWallpapersFromFavorites")
                    loadAdjacentWallpapersFromFavorites(currentId)
                    return@launch
                }
                
                // Only skip if we truly have all the adjacent wallpapers already loaded
                val hasAdequateAdjacents = wallpaperIds.isNotEmpty() && 
                                          nextWallpaperId != null && 
                                          prevWallpaperId != null &&
                                          adjacentWallpapersMap.containsKey(nextWallpaperId) &&
                                          adjacentWallpapersMap.containsKey(prevWallpaperId)
                                          
                if (hasAdequateAdjacents) {
                    _isLoadingAdjacentWallpapers.value = false
                    return@launch
                }
                
                

                Log.d("DetailViewModel", "Loading adjacent wallpapers for collection: $collection, currentId: $currentId, source: $sourceScreen")
                
                // Create a unified query based on the collection and source screen
                var query: Query = firestore.collection(collection)
                
                // Apply category filtering only for Wallpapers collection from category screen
                if (collection == "Wallpapers" && sourceScreen.startsWith(AppConfig.SOURCE_CATEGORY)) {
                    // Extract category info from source screen if available
                    var extractedCategory: String? = null
                    var extractedSubcategory: String? = null
                    
                    if (sourceScreen.contains(":")) {
                        val parts = sourceScreen.split(":")
                        if (parts.size > 1) extractedCategory = parts[1]
                        if (parts.size > 2) extractedSubcategory = parts[2]
        
                    }
                    
                    // Use extracted info or fallback to saved values
                    val catToUse = extractedCategory ?: categoryName
                    val subcatToUse = extractedSubcategory ?: subcategory
                    
                    if (catToUse != null) {

                        query = query.whereEqualTo("category", catToUse)
                        
                        if (subcatToUse != null) {

                            query = query.whereEqualTo("subCategory", subcatToUse)
                        }
                    }
                }
                
                // FIXED: Apply user's selected sort option instead of hardcoded timestamp
                val (sortField, sortDirection) = getSortFieldAndDirection(currentSortOption)
                query = query.orderBy(sortField, sortDirection)
                Log.d("DetailViewModel", "Using sort option: $currentSortOption -> $sortField $sortDirection")
                
                // Execute the query with a timeout

                val wallpaperDocs = withTimeout(8000) {
                    query.get().await()
                }
                
                // Process the results
                if (!wallpaperDocs.isEmpty) {
    
                    
                    // Clear existing data
                    wallpaperIds.clear()
                    wallpaperIds.addAll(wallpaperDocs.documents.map { it.id })
                    
                    // Cache adjacent wallpapers for instant access
                    wallpaperDocs.documents.forEach { doc ->
                        val wp = doc.toObject(Wallpaper::class.java)
                        if (wp != null) {
                            adjacentWallpapersMap[doc.id] = wp.copy(id = doc.id)
                        }
                    }
                    
                    // Find index of current wallpaper
                    currentIndex = wallpaperIds.indexOf(currentId)
                    
                    
                    // Update next/previous wallpaper IDs
                    updateAdjacentIndices()
                    
                    // Log the adjacent wallpaper IDs
                    Log.d("DetailViewModel", "Loaded ${wallpaperIds.size} wallpapers, current index: $currentIndex")
                    Log.d("DetailViewModel", "Next wallpaper ID: $nextWallpaperId, Previous wallpaper ID: $prevWallpaperId")
                } else {
                    // Try other collections as fallback
                    val otherCollections = listOf("Wallpapers", AppConfig.COLLECTION_TRENDING, "DepthEffectWallpapers")
                        .filter { it != collection }
                    
                    for (otherCollection in otherCollections) {

                        
                        try {
                            val alternativeQuery = firestore.collection(otherCollection)
                                .orderBy(AppConfig.FIELD_TIMESTAMP, Query.Direction.DESCENDING)
                                .limit(20) // Just get a few to allow swiping
                            
                            val alternativeDocs = withTimeout(5000) {
                                alternativeQuery.get().await()
                            }
                            
                            if (!alternativeDocs.isEmpty) {
    
                                
                                // Clear existing data
                                wallpaperIds.clear()
                                wallpaperIds.addAll(alternativeDocs.documents.map { it.id })
                                
                                // Cache adjacent wallpapers for instant access
                                alternativeDocs.documents.forEach { doc ->
                                    val wp = doc.toObject(Wallpaper::class.java)
                                    if (wp != null) {
                                        adjacentWallpapersMap[doc.id] = wp.copy(id = doc.id)
                                    }
                                }
                                
                                // The current wallpaper might not be in this alternative collection
                                // So we'll just let the user swipe through what we found
                                currentIndex = 0
                                updateAdjacentIndices()
                                currentCollection = otherCollection
                                

                                break
                            }
                        } catch (e: Exception) {
                        }
                    }
                }
                
                _isLoadingAdjacentWallpapers.value = false
            } catch (e: Exception) {
                Log.e("DetailViewModel", "Error loading adjacent wallpapers: ${e.message}")
                _isLoadingAdjacentWallpapers.value = false
            }
        }
    }

    /**
     * Checks if the specified wallpaper is available in the cache
     * This is needed for the critical fix for cached wallpaper swiping
     */
    fun isWallpaperCached(wallpaperId: String): Boolean {
        // First check if it's in our adjacent wallpapers map
        if (adjacentWallpapersMap.containsKey(wallpaperId)) {

            return true
        }
        
        // Then check if it's the current wallpaper
        if (_wallpaper.value?.id == wallpaperId) {
            
            return true
        }
        
        // Check if it's in the wallpaperIds list
        if (wallpaperIds.contains(wallpaperId)) {
            // It's in our list but not loaded yet - we know about it but need to load it
            return false
        }
        
        return false
    }
    
    /**
     * SIMPLIFIED FIX: Ensures the collection is loaded and wallpaper context is available for swiping
     * CRITICAL FIX: Don't reset context during active swipes
     */
    suspend fun ensureCollectionLoaded(wallpaperId: String, sourceScreen: String) {
        withContext(Dispatchers.IO) {
            try {
                Log.d("SWIPE_CONTEXT", "Ensuring collection loaded for wallpaper: $wallpaperId, source: $sourceScreen")
                
                if (wallpaperIds.isNotEmpty() && wallpaperIds.contains(wallpaperId)) {
                    val existingIndex = wallpaperIds.indexOf(wallpaperId)
                    
                    // Don't override if we're in the middle of a swipe transition
                    if (currentIndex == -1) {
                        currentIndex = existingIndex
                        updateAdjacentIndices()
                    } else {
                        Log.d("SWIPE_CONTEXT", "Keeping existing index: $currentIndex (found at $existingIndex)")
                    }
                    
                    Log.d("SWIPE_CONTEXT", "Using existing context - current index: $currentIndex")
                    Log.d("SWIPE_SIMPLE", "Collection context ready for swiping")
                    return@withContext
                }
                
                // Determine the correct collection based on source screen
                val collection = when {
                    sourceScreen == AppConfig.SOURCE_FAVORITES -> AppConfig.SOURCE_FAVORITES
                    sourceScreen == AppConfig.SOURCE_HOME -> AppConfig.COLLECTION_HOME
                    sourceScreen.startsWith(AppConfig.SOURCE_TRENDING) -> AppConfig.COLLECTION_TRENDING
                    sourceScreen.startsWith(AppConfig.SOURCE_HOME) -> AppConfig.COLLECTION_HOME
                    sourceScreen.startsWith(AppConfig.SOURCE_CATEGORY) -> AppConfig.COLLECTION_HOME
                    else -> AppConfig.COLLECTION_TRENDING
                }

                Log.d("SWIPE_CONTEXT", "Loading collection: $collection")
                
                // Build query with user's selected sort option
                val (sortField, sortDirection) = getSortFieldAndDirection(currentSortOption)
                var query = firestore.collection(collection)
                    .orderBy(sortField, sortDirection)
                    .limit(100)  // Load more for better swipe experience
                Log.d("DetailViewModel", "ensureCollectionLoaded using sort: $currentSortOption -> $sortField $sortDirection")
                
                // Apply category filtering if needed
                if (sourceScreen.startsWith(AppConfig.SOURCE_CATEGORY) && sourceScreen.contains(":")) {
                    val parts = sourceScreen.split(":")
                    if (parts.size > 1) {
                        query = query.whereEqualTo("category", parts[1])
                        if (parts.size > 2 && parts[2] != "null" && parts[2] != "None") {
                            query = query.whereEqualTo("subCategory", parts[2])
                        }
                    }
                }
                
                // Execute query
                val querySnapshot = withTimeout(5000) {
                    query.get().await()
                }
                
                if (!querySnapshot.isEmpty) {
                    // Clear and populate wallpaper IDs
                    wallpaperIds.clear()
                    wallpaperIds.addAll(querySnapshot.documents.map { it.id })
                    
                    // Cache wallpapers for immediate access during swipe
                    adjacentWallpapersMap.clear()
                    querySnapshot.documents.forEach { doc ->
                        val wp = doc.toObject(Wallpaper::class.java)
                        if (wp != null) {
                            adjacentWallpapersMap[doc.id] = wp.copy(id = doc.id)
                        }
                    }
                    
                    // Set current index and update adjacent indices
                    currentIndex = wallpaperIds.indexOf(wallpaperId)
                    if (currentIndex == -1 && wallpaperIds.isNotEmpty()) {
                        // If current wallpaper not found, add it at the beginning
                        wallpaperIds.add(0, wallpaperId)
                        currentIndex = 0
                    }
                    
                    updateAdjacentIndices()
                    currentCollection = collection
                    
                    Log.d("SWIPE_CONTEXT", "Successfully loaded ${wallpaperIds.size} wallpapers")
                    Log.d("SWIPE_CONTEXT", "Current index: $currentIndex, Next: $nextWallpaperId, Prev: $prevWallpaperId")
                } else {
                    Log.w("SWIPE_CONTEXT", "No wallpapers found in collection: $collection")
                    // Try alternative collection as fallback
                    if (collection == AppConfig.COLLECTION_TRENDING) {
                        Log.d("SWIPE_CONTEXT", "Trying home collection as fallback")
                        ensureCollectionLoaded(wallpaperId, AppConfig.COLLECTION_HOME)
                    }
                }
            } catch (e: Exception) {
                Log.e("SWIPE_CONTEXT", "Error ensuring collection loaded: ${e.message}")
            }
        }
    }
    
    /**
     * NEW FIX: Synchronously prepares for a swipe gesture by ensuring all required context is loaded
     * This method uses a combination of synchronous and asynchronous operations to ensure
     * the swipe gesture works properly even for cached wallpapers
     * 
     * @param wallpaperId The ID of the wallpaper to prepare for
     * @param sourceScreen The source screen to determine collection type
     * @return Int - Number of adjacent wallpapers available (for logging)
     */
    fun prepareForSwipe(wallpaperId: String, sourceScreen: String): Int {
        
        // Launch async coroutine to prevent ANRs
        viewModelScope.launch(Dispatchers.IO) {
            try {
            ensureCollectionLoaded(wallpaperId, sourceScreen)
            setCurrentWallpaperById(wallpaperId)
                forceUpdateAdjacentWallpapers(forceFreshLoad = true)
                
                // Ensure next and previous wallpapers are preloaded safely
                nextWallpaperId?.let { nextId ->
                    if (!adjacentWallpapersMap.containsKey(nextId)) {
                        preloadWallpaper(nextId, highPriority = true)
                }
                }
                
                prevWallpaperId?.let { prevId ->
                    if (!adjacentWallpapersMap.containsKey(prevId)) {
                        preloadWallpaper(prevId, highPriority = true)
                }
            }
            
            // Verify the context and count adjacent wallpapers
            val nextId = getNextWallpaperId()
            val prevId = getPreviousWallpaperId()
                var adjacentCount = 0
            
            if (nextId != null) adjacentCount++
            if (prevId != null) adjacentCount++
            
            
            } catch (e: Exception) {
        }
        }
        return 0 // Return immediately
    }
    
    /**
     * FINAL FIX: Completely rebuilds the wallpaper context to ensure reliable swiping for cached wallpapers
     * This is the definitive solution to the cached wallpaper swipe issue
     * @param wallpaperId The ID of the current wallpaper
     * @param sourceScreen The source screen to determine collection type
     */
    fun completelyRebuildWallpaperContext(wallpaperId: String, sourceScreen: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                
                // Determine the collection based on the source screen
                val collection = when {
                    sourceScreen == AppConfig.SOURCE_FAVORITES -> AppConfig.SOURCE_FAVORITES
                    sourceScreen == AppConfig.SOURCE_HOME -> AppConfig.COLLECTION_HOME
                    sourceScreen.startsWith(AppConfig.SOURCE_TRENDING) -> AppConfig.COLLECTION_TRENDING
                    sourceScreen.startsWith(AppConfig.SOURCE_HOME) -> AppConfig.COLLECTION_HOME
                    sourceScreen.startsWith(AppConfig.SOURCE_DEPTH) -> "DepthEffectWallpapers"
                    else -> AppConfig.COLLECTION_TRENDING
                }

                // If wallpaper IDs are already loaded, we might already have the necessary context
                if (wallpaperIds.isNotEmpty()) {
                    
                    // Find the current wallpaper index if not already set
                    if (currentIndex == -1) {
                        val currentId = _wallpaper.value?.id
                        if (currentId != null) {
                            currentIndex = wallpaperIds.indexOf(currentId)
                            // Update adjacent indices based on the current index
                            updateAdjacentIndices()
                        }
                    }
                    
                    // If we have a valid index, we're good to go
                    if (currentIndex != -1) {
                        return@launch
                    }
                }
                
                val (sortField, sortDirection) = getSortFieldAndDirection(currentSortOption)
                var query = firestore.collection(collection)
                    .orderBy(sortField, sortDirection)
                    .limit(50) // Limit to 50 wallpapers to improve performance
                Log.d("DetailViewModel", "forceUpdateCollectionAndContext using sort: $currentSortOption -> $sortField $sortDirection")
                
                // Apply category filtering if coming from category screen
                if (collection == "Wallpapers" && sourceScreen.startsWith(AppConfig.SOURCE_CATEGORY)) {
                    if (sourceScreen.contains(":")) {
                        val parts = sourceScreen.split(":")
                        if (parts.size > 1) {
                            val category = parts[1]
                            query = query.whereEqualTo("category", category)
                            
                            if (parts.size > 2) {
                                val subcategory = parts[2]
                                query = query.whereEqualTo("subCategory", subcategory)
                            }
                        }
                    }
                }
                
                // Execute the query with a timeout
                val wallpaperDocs = withTimeout(5000) {
                    query.get().await()
                }
                
                if (!wallpaperDocs.isEmpty) {
                    
                    // Clear existing data
                    wallpaperIds.clear()
                    wallpaperIds.addAll(wallpaperDocs.documents.map { it.id })
                    
                    // Cache adjacent wallpapers for instant access
                    wallpaperDocs.documents.forEach { doc ->
                        val wp = doc.toObject(Wallpaper::class.java)
                        if (wp != null) {
                            adjacentWallpapersMap[doc.id] = wp.copy(id = doc.id)
                        }
                    }
                    
                    // Find the current wallpaper index
                    val currentId = _wallpaper.value?.id
                    if (currentId != null) {
                        currentIndex = wallpaperIds.indexOf(currentId)
                        currentCollection = collection
                        
                        // Update adjacent indices
                        updateAdjacentIndices()
                    } else {
                    }
                } else {
                }
            } catch (e: Exception) {
            }
        }
    }
    
    /**
     * Updates the next and previous wallpaper indices based on current index
     */
    private fun updateAdjacentIndices() {
        // Only update if we have a valid index
        if (currentIndex != -1 && wallpaperIds.isNotEmpty()) {
            // Calculate next and previous indices
            nextIndex = if (currentIndex < wallpaperIds.size - 1) currentIndex + 1 else -1
            prevIndex = if (currentIndex > 0) currentIndex - 1 else -1
            
            // Update wallpaper IDs
            nextWallpaperId = if (nextIndex != -1) wallpaperIds[nextIndex] else null
            prevWallpaperId = if (prevIndex != -1) wallpaperIds[prevIndex] else null
            
        } else {
            // Invalid index - reset everything
            nextIndex = -1
            prevIndex = -1
            nextWallpaperId = null
            prevWallpaperId = null
            
        }
    }

    /**
     * Navigates to the next wallpaper
     */
    fun navigateToNextWallpaper() {
        _isNavigatingViaGesture.value = true
        if (currentIndex != -1 && currentIndex < wallpaperIds.size - 1) {
            // Navigate to the next wallpaper
            val nextId = wallpaperIds[currentIndex + 1]
            currentWallpaperId = nextId
            _currentWallpaperIdFlow.value = nextId
            savedStateHandle["wallpaperId"] = nextId
            Log.d("DetailViewModel", "Navigating to next wallpaper: $nextId")
            
            // Update current index for proper swipe support
            currentIndex = currentIndex + 1
            Log.d("DetailViewModel", "Updated current index to: $currentIndex for next wallpaper")
            
            // Load adjacent wallpapers to update next/prev options
            val collectionToUse = currentCollection ?: determineCollectionFromSourceSync()
            loadAdjacentWallpapers(collectionToUse, nextId)
            
            // OPTIMIZATION: Prefetch the next batch of wallpapers for smoother scrolling
            prefetchNextBatch(3)
            
            // Removed reference to undefined variable _swipeCount
        } else if (currentIndex == wallpaperIds.size - 1 && wallpaperIds.isNotEmpty()) {
            // We're at the last wallpaper - don't wrap around
            Log.d("DetailViewModel", "At the last wallpaper, cannot go to next")
            // Optionally show a toast or visual indication that this is the last wallpaper
        }
        _isNavigatingViaGesture.value = false
    }

    /**
     * Navigates to the previous wallpaper
     */
    fun navigateToPreviousWallpaper() {
        _isNavigatingViaGesture.value = true
        if (currentIndex > 0) {
            // Navigate to the previous wallpaper
            val prevId = wallpaperIds[currentIndex - 1]
            currentWallpaperId = prevId
            _currentWallpaperIdFlow.value = prevId
            savedStateHandle["wallpaperId"] = prevId
            Log.d("DetailViewModel", "Navigating to previous wallpaper: $prevId")
            
            // Update current index for proper swipe support
            currentIndex = currentIndex - 1
            Log.d("DetailViewModel", "Updated current index to: $currentIndex for previous wallpaper")
            
            // Load adjacent wallpapers to update next/prev options
            val collectionToUse = currentCollection ?: determineCollectionFromSourceSync()
            loadAdjacentWallpapers(collectionToUse, prevId)
            
            // OPTIMIZATION: Prefetch the next batch of wallpapers for smoother scrolling
            prefetchNextBatch(3)
            
            // Removed reference to undefined variable _swipeCount
        } else if (currentIndex == 0 && wallpaperIds.isNotEmpty()) {
            // We're at the first wallpaper - don't wrap around
            Log.d("DetailViewModel", "At the first wallpaper, cannot go to previous")
            // Optionally show a toast or visual indication that this is the first wallpaper
        }
        _isNavigatingViaGesture.value = false
    }
    
    /**
     * Gets the ID of the next wallpaper without changing the current one
     */
    fun getNextWallpaperId(): String? {
        // Check if we have valid wallpaper IDs and a valid current index
        if (wallpaperIds.isEmpty()) {
            // Only log when empty - this is an error condition
            Log.w("DetailViewModel", "No wallpaper IDs available for next wallpaper")
            return null
        }
        
        if (currentIndex == -1) {
            // Only log when invalid - this is an error condition  
            Log.w("DetailViewModel", "Current index is invalid: $currentIndex")
            return null
        }
        
        return if (currentIndex < wallpaperIds.size - 1) {
            // Get the next wallpaper ID - no logging unless it's different from cached
            val nextId = wallpaperIds[currentIndex + 1]
            if (nextWallpaperId != nextId) {
                Log.d("DetailViewModel", "Next wallpaper ID changed: $nextId (index: ${currentIndex + 1})")
            }
            nextId
        } else {
            // Only log when we're at the end - this is useful debug info
            if (nextWallpaperId != null) {
            Log.d("DetailViewModel", "At the end of the list, no next wallpaper")
            }
            null
        }
    }

    /**
     * Gets the ID of the previous wallpaper without changing the current one
     */
    fun getPreviousWallpaperId(): String? {
        // Check if we have valid wallpaper IDs and a valid current index
        if (wallpaperIds.isEmpty()) {
            // Only log when empty - this is an error condition
            Log.w("DetailViewModel", "No wallpaper IDs available for previous wallpaper")
            return null
        }
        
        if (currentIndex == -1) {
            // Only log when invalid - this is an error condition
            Log.w("DetailViewModel", "Current index is invalid: $currentIndex")
            return null
        }
        
        return if (currentIndex > 0) {
            // Get the previous wallpaper ID - no logging unless it's different from cached
            val prevId = wallpaperIds[currentIndex - 1]
            if (prevWallpaperId != prevId) {
                Log.d("DetailViewModel", "Previous wallpaper ID changed: $prevId (index: ${currentIndex - 1})")
            }
            prevId
        } else {
            // Only log when we're at the beginning - this is useful debug info
            if (prevWallpaperId != null) {
            Log.d("DetailViewModel", "At the beginning of the list, no previous wallpaper")
            }
            null
        }
    }
    
    /**
     * Load metadata (dimensions, file size) in the background without affecting UI
     */
    private fun loadMetadataInBackground(wallpaper: Wallpaper) {
        // Cancel any existing metadata job
        metadataJob?.cancel()
        
        // Set loading state immediately if we don't have cached data
        val url = wallpaper.imageUrl
        if (!metadataCache.containsKey(url) && (wallpaper.dimensions.isNullOrEmpty() || wallpaper.size.isNullOrEmpty())) {
            _imageDimensions.value = "Loading..."
            _imageSize.value = "Loading..."
        }
        
        // Start new metadata job
        metadataJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                // First check for cached metadata by URL
                val url = wallpaper.imageUrl
                
                // Check if we already have metadata for this URL
                if (metadataCache.containsKey(url)) {
                    val (dimensions, size) = metadataCache[url]!!
                    Log.d("DetailViewModel", "Cached metadata for URL $url: $dimensions, $size")
                    
                    // Set the values on the main thread
                    withContext(Dispatchers.Main) {
                        _imageDimensions.value = dimensions
                        _imageSize.value = size
                    }
                    
                    // Also cache by wallpaper ID for faster lookup next time
                    wallpaperMetadataMap[wallpaperId] = dimensions to size
                    
                    return@launch
                }
                
                // Also check if wallpaper already has dimensions and size
                if (!wallpaper.dimensions.isNullOrEmpty() && !wallpaper.size.isNullOrEmpty()) {
                    Log.d("DetailViewModel", "Using dimensions and size from wallpaper: ${wallpaper.dimensions}, ${wallpaper.size}")
                    
                    withContext(Dispatchers.Main) {
                        _imageDimensions.value = wallpaper.dimensions
                        _imageSize.value = wallpaper.size
                    }
                    
                    // Cache values for future use
                    metadataCache[url] = wallpaper.dimensions to wallpaper.size
                    wallpaperMetadataMap[wallpaperId] = wallpaper.dimensions to wallpaper.size
                    
                    return@launch
                }
                
                // If not cached, calculate metadata
                Log.d("DetailViewModel", "Calculating metadata for URL: $url")
                
                // Calculate metadata in the background
                calculateImageMetadata(url, wallpaperId)
                
            } catch (e: Exception) {
                Log.e("DetailViewModel", "Error calculating image metadata: ${e.message}")
            }
        }
    }

    private fun processWallpaperMetadata(wallpaper: Wallpaper?) {
        wallpaper?.let { wp ->
            // First check if we have cached metadata for this specific wallpaper ID
            wallpaperMetadataMap[wallpaperId]?.let { (dimensions, size) ->
                _imageDimensions.value = dimensions
                _imageSize.value = size
                Log.d("DetailViewModel", "Loaded cached metadata for wallpaper ID $wallpaperId: $dimensions, $size")
                
                // Update the wallpaper object with the cached information
                _wallpaper.value = _wallpaper.value?.copy(
                    dimensions = dimensions,
                    size = size
                )
                
                // Early return if we have cached metadata
                incrementViewsAndCheckFavorite(wp)
                return@let
            }

            // If not found by ID, try by URL
            wp.imageUrl?.let { url ->
                if (url.isNotEmpty()) {
                    metadataCache[url]?.let { (dimensions, size) ->
                        _imageDimensions.value = dimensions
                        _imageSize.value = size
                        Log.d("DetailViewModel", "Loaded cached metadata by URL: $dimensions, $size")
                        
                        // Update the wallpaper object with the cached information
                        _wallpaper.value = _wallpaper.value?.copy(
                            dimensions = dimensions,
                            size = size
                        )
                        
                        // Also cache by wallpaper ID for future lookups
                        wallpaperMetadataMap[wallpaperId] = dimensions to size
                        
                        // Early return if we have URL-based cached metadata
                        incrementViewsAndCheckFavorite(wp)
                        return@let
                    }
                    
                    // Calculate metadata if not cached or if values are empty
                    if (_imageDimensions.value.isEmpty() || _imageSize.value.isEmpty()) {
                        calculateImageMetadata(url, wallpaperId)
                    }
                }
            }

            // Continue with incrementing views and checking favorites
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
            // Optimistically increment local views counter for instant UI feedback
            _localViews.value = (_localViews.value ?: 0) + 1
            incrementViews()
        }

        // Check if it's a favorite
        viewModelScope.launch {
            val isFavorite = favoritesRepository.isFavorite(wallpaperId)
            _wallpaper.value = wallpaper.copy(
                isFavorite = isFavorite,
                dimensions = _imageDimensions.value,
                size = _imageSize.value
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
                firestore.runTransaction { transaction ->
                        val snapshot = transaction.get(docRef)
                    val currentViews = snapshot.getLong("views") ?: 0
                        transaction.update(docRef, "views", currentViews + 1)
                }.await()
                hasIncrementedViews = true
                    Log.d("DetailViewModel", "Views incremented for wallpaper: $wallpaperId")
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
                val downloadDir = DownloadManager.getOnePlus7WallpapersFolder(context)
                if (downloadDir == null) {
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
                    
                    DownloadManager.downloadWallpaper(context, wallpaper.imageUrl, fileName)
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
                            Toast.makeText(context, "Wallpaper saved to gallery", Toast.LENGTH_SHORT).show()
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
                    Log.e("DetailViewModel", "Error downloading wallpaper", e)
                    withContext(Dispatchers.Main) {
                        toastManager.showToast(
                            message = "Error downloading wallpaper: ${e.message}",
                            icon = Icons.Default.Error,
                            duration = 3000
                        )
                        _isDownloading.value = false
                        _downloadProgress.value = 0f
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
                        downloadedFile = File(DownloadManager.getOnePlus7WallpapersFolder(context), fileName)
                        Log.d("DetailViewModel", "Download completed to public storage: ${downloadedFile?.absolutePath}, exists: ${downloadedFile?.exists()}, size: ${downloadedFile?.length()} bytes")
                    }
                }
            return downloadedFile
        } catch (e: Exception) {
            Log.e("DetailViewModel", "Error downloading wallpaper to public storage", e)
            return null
        }
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
                val file = cachedWallpaperFile ?: ensureWallpaperDownloaded(fileName, wallpaper.imageUrl, useCache = true)
                
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
                    val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                    Log.d("DetailViewModel", "Bitmap decoded, size: ${bitmap.width}x${bitmap.height}")
                    
                    val wallpaperManager = WallpaperManager.getInstance(context)
                    
                    val success = when (option) {
                        WallpaperSetOption.HOME_SCREEN -> {
                            Log.d("DetailViewModel", "Setting as home screen wallpaper")
                            try {
                                wallpaperManager.setBitmap(bitmap, null, true, WallpaperManager.FLAG_SYSTEM)
                                true
                            } catch (e: Exception) {
                                Log.e("DetailViewModel", "Error setting home screen wallpaper", e)
                                false
                            }
                        }
                        WallpaperSetOption.LOCK_SCREEN -> {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                Log.d("DetailViewModel", "Setting as lock screen wallpaper")
                                try {
                                    wallpaperManager.setBitmap(bitmap, null, true, WallpaperManager.FLAG_LOCK)
                                    true
                                } catch (e: Exception) {
                                    Log.e("DetailViewModel", "Error setting lock screen wallpaper", e)
                                    false
                                }
                            } else {
                                false
                            }
                        }
                        WallpaperSetOption.BOTH_SCREENS -> {
                            Log.d("DetailViewModel", "Setting as both home and lock screen wallpaper")
                            try {
                                wallpaperManager.setBitmap(bitmap)
                                true
                            } catch (e: Exception) {
                                Log.e("DetailViewModel", "Error setting wallpaper", e)
                                false
                            }
                        }
                        WallpaperSetOption.EXTERNAL -> {
                            Log.d("DetailViewModel", "Opening external wallpaper setter")
                            // For external, we just download and let the UI handle opening the gallery
                            true
                        }
                    }

                    bitmap.recycle()

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
                
                // For sharing, we need to download to a public directory to make it visible in gallery apps
                val file = ensureWallpaperDownloaded(fileName, wallpaper.imageUrl, useCache = false)
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
                    // Make sure the file is visible to other apps by scanning it
                    MediaScannerConnection.scanFile(
                        context,
                        arrayOf(file.absolutePath),
                        arrayOf("image/jpeg"),
                        null
                    )
                    
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

    // Track failed metadata URLs to avoid repeated attempts
    private val failedMetadataUrls = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    // Track in-flight metadata calculations to prevent duplicate concurrent calls
    private val metadataInFlight = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    /**
     * Calculates the dimensions and size of the image from the URL - PUBLIC for DetailScreen use
     */
    fun calculateImageMetadata(imageUrl: String, wallpaperId: String? = null) {
        if (imageUrl.isEmpty()) {
            Log.w("DetailViewModel", "Cannot calculate metadata for empty URL")
            setDefaultMetadata()
            return
        }

        // Skip if this URL has already failed
        if (failedMetadataUrls.contains(imageUrl)) {
            Log.d("DetailViewModel", "Skipping metadata calculation for previously failed URL: $imageUrl")
            setDefaultMetadata()
            return
        }

        // Guard against duplicate concurrent calls for the same wallpaper
        val inFlightKey = wallpaperId ?: imageUrl
        if (!metadataInFlight.add(inFlightKey)) {
            Log.d("DetailViewModel", "Metadata already in-flight for: $inFlightKey, skipping duplicate")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            var connection: java.net.URLConnection? = null
            var inputStream: java.io.InputStream? = null

            try {
                Log.d("DetailViewModel", "Starting metadata calculation for URL: $imageUrl")
                
                // Use withTimeout to prevent hanging
                withTimeout(15000L) { // Increased to 15 seconds
                    connection = java.net.URL(imageUrl).openConnection().apply {
                        connectTimeout = 8000 // Increased connect timeout
                        readTimeout = 8000 // Increased read timeout
                        setRequestProperty("User-Agent", "Mozilla/5.0 (Android)")
                        // Add accept headers for better compatibility
                        setRequestProperty("Accept", "image/*")
                    }
                    
                    Log.d("DetailViewModel", "Attempting to connect to: $imageUrl")
                    connection!!.connect()
                    Log.d("DetailViewModel", "Successfully connected to: $imageUrl")

                    // Check HTTP response code before attempting to read body
                    val responseCode = (connection as? java.net.HttpURLConnection)?.responseCode ?: 200
                    if (responseCode != 200) {
                        Log.w("DetailViewModel", "Cannot fetch image metadata: HTTP $responseCode for $imageUrl")
                        setDefaultMetadata()
                        return@withTimeout
                    }

                    // Get file size with better error handling
                    val fileSize = connection!!.contentLength
                    Log.d("DetailViewModel", "Content length: $fileSize bytes")

                    // Sanity check: skip decode if response is too small to be an image
                    if (fileSize in 1..999) {
                        Log.w("DetailViewModel", "Response too small ($fileSize bytes) to be an image, skipping decode: $imageUrl")
                        withContext(Dispatchers.Main) {
                            _imageSize.value = "$fileSize B"
                            _imageDimensions.value = "Dimensions unavailable"
                        }
                        return@withTimeout
                    }
                    
                    val formattedSize = when {
                        fileSize < 0 -> {
                            Log.w("DetailViewModel", "Content-Length not available, trying alternative method")
                            "Size unavailable"
                        }
                        fileSize < 1024 -> "$fileSize B"
                        fileSize < 1024 * 1024 -> String.format("%.1f KB", fileSize / 1024.0)
                        else -> String.format("%.2f MB", fileSize / (1024.0 * 1024.0))
                    }
                    
                    // Get image dimensions with improved handling
                    var dimensions = "Dimensions unavailable"
                    
                    try {
                        inputStream = connection!!.getInputStream()
                        Log.d("DetailViewModel", "Got input stream, decoding image bounds...")
                        
                        val options = BitmapFactory.Options().apply {
                            inJustDecodeBounds = true
                            inSampleSize = 1
                        }
                        
                        // Use mark/reset for better stream handling
                        if (inputStream!!.markSupported()) {
                            inputStream!!.mark(8192)
                        }
                        
                        BitmapFactory.decodeStream(inputStream, null, options)
                        
                        val width = options.outWidth
                        val height = options.outHeight
                        
                        Log.d("DetailViewModel", "Decoded dimensions: ${width}x${height}")
                        
                        if (width > 0 && height > 0) {
                            dimensions = "${width} × ${height}"
                            Log.d("DetailViewModel", "Successfully calculated dimensions: $dimensions")
                        } else {
                            Log.w("DetailViewModel", "Invalid dimensions: width=$width, height=$height")
                        }
                        
                    } catch (e: Exception) {
                        Log.e("DetailViewModel", "Failed to decode image dimensions: ${e.message}", e)
                        
                        // Try alternative approach with different options
                        try {
                            inputStream?.close()
                            inputStream = connection!!.getInputStream()
                            
                            val alternateOptions = BitmapFactory.Options().apply {
                                inJustDecodeBounds = true
                                inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
                            }
                            
                            val bitmap = BitmapFactory.decodeStream(inputStream, null, alternateOptions)
                            if (alternateOptions.outWidth > 0 && alternateOptions.outHeight > 0) {
                                dimensions = "${alternateOptions.outWidth} × ${alternateOptions.outHeight}"
                                Log.d("DetailViewModel", "Alternative method succeeded: $dimensions")
                            }
                        } catch (e2: Exception) {
                            Log.e("DetailViewModel", "Alternative dimension calculation also failed: ${e2.message}")
                        }
                    }
                    
                    // Update UI on main thread
                    withContext(Dispatchers.Main) {
                        _imageDimensions.value = dimensions
                        _imageSize.value = formattedSize
                        
                        Log.d("DetailViewModel", "Updated metadata - Dimensions: $dimensions, Size: $formattedSize")
                        
                        // Cache the metadata by URL
                        metadataCache[imageUrl] = dimensions to formattedSize
                        
                        // Also cache by wallpaper ID if available
                        wallpaperId?.let {
                            wallpaperMetadataMap[it] = dimensions to formattedSize
                        }
                        
                        // Update the wallpaper object with the new information
                        _wallpaper.value = _wallpaper.value?.copy(
                            dimensions = dimensions,
                            size = formattedSize
                        )
                    }
                }
                
            } catch (e: TimeoutCancellationException) {
                Log.e("DetailViewModel", "Metadata calculation timed out for URL: $imageUrl")
                failedMetadataUrls.add(imageUrl)
                withContext(Dispatchers.Main) {
                    _imageDimensions.value = "Timeout"
                    _imageSize.value = "Timeout"
                }
                
            } catch (e: java.net.UnknownHostException) {
                Log.e("DetailViewModel", "Network error - Unknown host: ${e.message}")
                withContext(Dispatchers.Main) {
                    _imageDimensions.value = "Network error"
                    _imageSize.value = "Network error"
                }
                
            } catch (e: java.net.SocketTimeoutException) {
                Log.e("DetailViewModel", "Connection timeout for URL: $imageUrl")
                failedMetadataUrls.add(imageUrl)
                withContext(Dispatchers.Main) {
                    _imageDimensions.value = "Connection timeout"
                    _imageSize.value = "Connection timeout"
                }
                
            } catch (e: Exception) {
                Log.e("DetailViewModel", "Error calculating image metadata for URL: $imageUrl", e)
                failedMetadataUrls.add(imageUrl)
                
                withContext(Dispatchers.Main) {
                    setDefaultMetadata()
                }
                
            } finally {
                // Cleanup resources
                try {
                    inputStream?.close()
                    (connection as? java.net.HttpURLConnection)?.disconnect()
                } catch (e: Exception) {
                    Log.w("DetailViewModel", "Error during cleanup: ${e.message}")
                }
                // Always release the in-flight lock so future calls can proceed
                metadataInFlight.remove(inFlightKey)
            }
        }
    }
    
    private fun setDefaultMetadata() {
        _imageDimensions.value = "Unknown dimensions"
        _imageSize.value = "Unknown size"
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
                        val currentViews = snapshot.getLong("views") ?: 0
                        transaction.update(docRef, "views", currentViews + 1)
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
                        val currentDownloads = snapshot.getLong("downloads") ?: 0
                        transaction.update(docRef, "downloads", currentDownloads + 1)
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
                val downloadDir = DownloadManager.getOnePlus7WallpapersFolder(context)
                if (downloadDir == null) {
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
                    Log.e("DetailViewModel", "Error in external download", e)
                    withContext(Dispatchers.Main) {
                        onComplete("")
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
    
    // Track prefetch batch operations to avoid redundant prefetching
    private var prefetchBatchInProgress = false
    private var lastPrefetchIndex = -1
    private var prefetchJob: Job? = null
    
    /**
     * OPTIMIZATION: Prefetch the next batch of wallpapers based on current position
     * This is called proactively to ensure smooth scrolling experience
     * @param batchSize Number of wallpapers to prefetch ahead and behind current position
     */
    fun prefetchNextBatch(batchSize: Int = 3) {
        // Cancel any existing prefetch job to avoid redundant operations
        prefetchJob?.cancel()
        
        // Start a new prefetch job
        prefetchJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                // Don't prefetch if we're already prefetching or if the current index is invalid
                if (prefetchBatchInProgress || currentIndex < 0 || wallpaperIds.isEmpty()) {
                    return@launch
                }
                
                // Don't prefetch if we're at the same position as last time
                if (lastPrefetchIndex == currentIndex) {
                    return@launch
                }
                
                prefetchBatchInProgress = true
                lastPrefetchIndex = currentIndex
                
                Log.d("PREFETCH", "Starting batch prefetch at index $currentIndex with batch size $batchSize")
                
                // Prefetch forward (next wallpapers)
                val forwardIds = mutableListOf<String>()
                for (i in 1..batchSize) {
                    val nextIndex = currentIndex + i
                    if (nextIndex < wallpaperIds.size) {
                        forwardIds.add(wallpaperIds[nextIndex])
                    }
                }
                
                // Prefetch backward (previous wallpapers) - lower priority
                val backwardIds = mutableListOf<String>()
                for (i in 1..batchSize) {
                    val prevIndex = currentIndex - i
                    if (prevIndex >= 0) {
                        backwardIds.add(wallpaperIds[prevIndex])
                    }
                }
                
                // Prefetch forward wallpapers first (higher priority)
                Log.d("PREFETCH", "Prefetching ${forwardIds.size} forward wallpapers: $forwardIds")
                forwardIds.forEachIndexed { index, id ->
                    // Add a small delay between requests to avoid overwhelming the system
                    if (index > 0) delay(100)
                    preloadWallpaper(id, highPriority = false)
                }
                
                // Then prefetch backward wallpapers (lower priority)
                Log.d("PREFETCH", "Prefetching ${backwardIds.size} backward wallpapers: $backwardIds")
                backwardIds.forEachIndexed { index, id ->
                    // Add a small delay between requests to avoid overwhelming the system
                    if (index > 0) delay(100)
                    preloadWallpaper(id, highPriority = false)
                }
                
                Log.d("PREFETCH", "Completed batch prefetch at index $currentIndex")
            } catch (e: Exception) {
                Log.e("PREFETCH", "Error during batch prefetch: ${e.message}")
            } finally {
                prefetchBatchInProgress = false
            }
        }
    }
    
    /**
     * Preloads wallpaper data to ensure smooth transitions
     * This is called during swipe animations before the actual load
     * @param wallpaperId The ID of the wallpaper to preload
     * @param highPriority If true, this preload is needed immediately for swiping
     */
    fun preloadWallpaper(wallpaperId: String, highPriority: Boolean = false) {
        // Start preloading immediately without waiting
        viewModelScope.launch(Dispatchers.IO) {
            try {
                Log.d("SWIPE_TRACKER", "PRELOAD: Preloading wallpaper: $wallpaperId, highPriority: $highPriority")
                
                // This ensures swipe has the proper IDs to work with
                if (!wallpaperIds.contains(wallpaperId)) {
                    if (nextWallpaperId == wallpaperId) {
                        // If it's the next wallpaper, add it after the current one
                        val insertPosition = if (currentIndex >= 0 && currentIndex < wallpaperIds.size) {
                            currentIndex + 1
                        } else {
                            wallpaperIds.size  // Add to end if current index is invalid
                        }
                        // Insert at the appropriate position
                        if (insertPosition <= wallpaperIds.size) {
                            wallpaperIds.add(insertPosition, wallpaperId)
                            Log.d("SWIPE_TRACKER", "Added missing next wallpaper ID to list at position $insertPosition")
                        } else {
                            wallpaperIds.add(wallpaperId)
                            Log.d("SWIPE_TRACKER", "Added missing next wallpaper ID to end of list")
                        }
                    } else if (prevWallpaperId == wallpaperId) {
                        // If it's the previous wallpaper, add it before the current one
                        val insertPosition = if (currentIndex > 0) {
                            currentIndex  // Insert at current index (shifting current right)
                        } else {
                            0  // Add to beginning if current index is invalid
                        }
                        wallpaperIds.add(insertPosition, wallpaperId)
                        // Update current index since we inserted before it
                        if (currentIndex >= 0) {
                            currentIndex++
                        }
                        Log.d("SWIPE_TRACKER", "Added missing previous wallpaper ID to list at position $insertPosition")
                    } else {
                        // If it's not next or previous (unlikely), add it to the end
                        wallpaperIds.add(wallpaperId)
                        Log.d("SWIPE_TRACKER", "Added missing wallpaper ID to end of list")
                    }
                    // Force update adjacent IDs after modifying the list
                    updateAdjacentIndices()
                }

                // FIX 2: For cached wallpapers, always update the index mapping without returning early
                if (adjacentWallpapersMap.containsKey(wallpaperId)) {
                    Log.d("SWIPE_TRACKER", "Found cached wallpaper: $wallpaperId")
                    val cachedWallpaper = adjacentWallpapersMap[wallpaperId]
                    cachedWallpaper?.let {
                        if (highPriority) {
                            // Only force index update for high priority requests (swipes)
                            // This is crucial for making cached wallpapers swipe correctly
                            val index = wallpaperIds.indexOf(wallpaperId)
                            if (index != -1 && index != currentIndex) {
                                Log.d("SWIPE_TRACKER", "Updating index mapping for cached wallpaper from $currentIndex to $index")
                                // Log current map state for debugging
                                Log.d("SWIPE_TRACKER", "Current IDs: current=${wallpaperIds.getOrNull(currentIndex)}, next=$nextWallpaperId, prev=$prevWallpaperId")
                            }
                        }
                    }
                    
                    // Don't return early for high priority requests - we want to proceed to ensure proper wallpaper loading
                    if (!highPriority) {
                        return@launch
                    }
                }
                
                // Ensure we have a valid collection to query
                val collection = currentCollection ?: AppConfig.COLLECTION_TRENDING
                
                // Fetch the wallpaper data from Firestore with a short timeout
                // This ensures we don't block the UI waiting for slow requests
                var wallpaperDoc: DocumentSnapshot? = null
                try {
                    withTimeout(1500) { // Short timeout to keep things responsive
                        wallpaperDoc = firestore.collection(collection)
                            .document(wallpaperId)
                            .get()
                            .await()
                    }
                } catch (e: TimeoutCancellationException) {
                    Log.w("DetailViewModel", "Preload Firestore timeout for wallpaper: $wallpaperId")
                    return@launch
                }
                
                if (wallpaperDoc?.exists() == true) {
                    val wallpaper = wallpaperDoc?.toObject(Wallpaper::class.java)
                    if (wallpaper != null) {
                        // Create a new Wallpaper instance with the correct ID
                        val wallpaperWithId = wallpaper.copy(id = wallpaperDoc?.id ?: wallpaperId)
                        
                        // Add to adjacent wallpapers map
                        adjacentWallpapersMap[wallpaperId] = wallpaperWithId
                        
                        Log.d("DetailViewModel", "Successfully preloaded wallpaper: $wallpaperId")
                    }
                } else {
                    Log.e("DetailViewModel", "Failed to preload wallpaper: $wallpaperId - document does not exist")
                }
                
            } catch (e: Exception) {
                Log.e("DetailViewModel", "Error during preload: ${e.message}")
            }
        }
    }
    
    /**
     * EMERGENCY FIX: Ultra-fast thumbnail preloading to bypass collection search delays
     * This method focuses solely on getting a visible image as quickly as possible
     * It uses parallel collection searches and direct UI updates for instant display
     */
    fun preloadThumbnailImmediately(wallpaperId: String) {
        
        if (wallpaperId.isEmpty()) return
        
        // SHORTCUT: First, check if we already have this wallpaper in our cache
        val cachedWallpaper = adjacentWallpapersMap[wallpaperId]
        if (cachedWallpaper != null) {
            
            // Since wallpaper is already cached, just update the current wallpaper flow
            // This provides immediate UI updates without waiting for Firestore
            _wallpaper.value = cachedWallpaper
            _currentWallpaperIdFlow.value = wallpaperId
            return
        }
        
        // PARALLEL OPTIMIZATION: Start multiple collection searches simultaneously
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Create a completed flag to avoid duplicate updates
                val found = AtomicBoolean(false)
                
                // Start simultaneous searches in BOTH collections instead of sequential
                val collections = AppConfig.WALLPAPER_SEARCH_COLLECTIONS
                
                // Launch parallel jobs
                collections.forEach { collection ->
                    launch {
                        try {
                            // Skip if another job already found the wallpaper
                            if (found.get()) return@launch
                            
                            val document = firestore.collection(collection).document(wallpaperId).get().await()
                            
                            // Skip if another job already found the wallpaper
                            if (found.get()) return@launch
                            
                            if (document.exists()) {
                                val wallpaper = document.toObject(Wallpaper::class.java)
                                if (wallpaper != null) {
                                    // Try to be the first to process this result
                                    if (found.compareAndSet(false, true)) {
                                        
                                        // Create a new Wallpaper instance with the correct ID
                                        val wallpaperWithId = wallpaper.copy(id = document.id)
                                        
                                        // Update cache and UI state
                                        adjacentWallpapersMap[wallpaperId] = wallpaperWithId
                                        _wallpaper.value = wallpaperWithId
                                        _currentWallpaperIdFlow.value = wallpaperId
                                    }
                                }
                            }
                        } catch (e: Exception) {
                        }
                    }
                }
                
            } catch (e: Exception) {
            }
        }
    }

    /**
     * Simple getter for Firestore instance
     */
    fun getFirestore(): FirebaseFirestore = firestore

    /**
     * Loads the next wallpaper directly without navigation - ENHANCED VERSION
     */
    fun loadNextWallpaper(): Boolean {
        val nextId = getNextWallpaperId()
        if (nextId == null) {
            Log.w("DetailViewModel", "No next wallpaper ID available")
            return false
        }
        
        Log.d("DetailViewModel", "Loading next wallpaper: $nextId")
            
        // Get wallpaper from cache or load it
        viewModelScope.launch {
            try {
                _isLoadingNextWallpaper.value = true
                
                val cachedWallpaper = adjacentWallpapersMap[nextId]
                if (cachedWallpaper != null) {
                    Log.d("DetailViewModel", "Using cached next wallpaper")
                    
                    // Add small delay for cached wallpapers to show loading briefly for better UX
                    delay(100)
                    
                    currentIndex = wallpaperIds.indexOf(nextId)
                    
                    // Update current wallpaper ID IMMEDIATELY
                    currentWallpaperId = nextId
                    _currentWallpaperIdFlow.value = nextId
                    
                    // Set the wallpaper data
                    _wallpaper.value = cachedWallpaper
                    
                    // Update adjacent indices AFTER setting current state
                    updateAdjacentIndices()
                    
                } else {
                    Log.d("DetailViewModel", "Loading next wallpaper from Firestore")
                    
                    currentIndex = wallpaperIds.indexOf(nextId)
                    currentWallpaperId = nextId
                    _currentWallpaperIdFlow.value = nextId
                    
                    // Then load wallpaper data
                    loadWallpaper(sourceScreen, nextId, updateFlow = false)
                    
                    // FIXED: Increment view count for swiped wallpaper (use private method with ID)
                    incrementViews()
                }
            } catch (e: Exception) {
                Log.e("DetailViewModel", "Error loading next wallpaper: ${e.message}")
            } finally {
                // Ensure loading state is always reset
                _isLoadingNextWallpaper.value = false
                // Also reset main loading state for consistency
                _isLoading.value = false
            }
        }
        return true
    }

    /**
     * Loads the previous wallpaper directly without navigation - ENHANCED VERSION
     */
    fun loadPreviousWallpaper(): Boolean {
        val prevId = getPreviousWallpaperId()
        if (prevId == null) {
            Log.w("DetailViewModel", "No previous wallpaper ID available")
            return false
        }
        
        Log.d("DetailViewModel", "Loading previous wallpaper: $prevId")
        
        // Get wallpaper from cache or load it
        viewModelScope.launch {
            try {
                _isLoadingPrevWallpaper.value = true
                
                val cachedWallpaper = adjacentWallpapersMap[prevId]
                if (cachedWallpaper != null) {
                    Log.d("DetailViewModel", "Using cached previous wallpaper")
                    
                    // Add small delay for cached wallpapers to show loading briefly for better UX
                    delay(100)
                    
                    currentIndex = wallpaperIds.indexOf(prevId)
        
                    // Update current wallpaper ID IMMEDIATELY
                    currentWallpaperId = prevId
                    _currentWallpaperIdFlow.value = prevId
                    
                    // Set the wallpaper data
                    _wallpaper.value = cachedWallpaper
                    
                    // Update adjacent indices AFTER setting current state
                    updateAdjacentIndices()
                    
                } else {
                    Log.d("DetailViewModel", "Loading previous wallpaper from Firestore")
                    
                    currentIndex = wallpaperIds.indexOf(prevId)
                    currentWallpaperId = prevId
                    _currentWallpaperIdFlow.value = prevId
                    
                    // Then load wallpaper data
                    loadWallpaper(sourceScreen, prevId, updateFlow = false)
                    
                    // FIXED: Increment view count for swiped wallpaper (use private method with ID)
                    incrementViews()
                }
            } catch (e: Exception) {
                Log.e("DetailViewModel", "Error loading previous wallpaper: ${e.message}")
            } finally {
                // Ensure loading state is always reset
                _isLoadingPrevWallpaper.value = false
                // Also reset main loading state for consistency
                _isLoading.value = false
            }
        }
        return true
    }
    
    /**
     * Get the thumbnail URL of the next or previous wallpaper
     */
    fun getAdjacentWallpaperThumbnail(isNext: Boolean): String? {
        // Determine which wallpaper ID to use
        val adjacentId = if (isNext) getNextWallpaperId() else getPreviousWallpaperId()
        
        // Return early if no adjacent wallpaper exists
        if (adjacentId == null) {
            Log.d("DetailViewModel", "No ${if (isNext) "next" else "previous"} wallpaper ID available")
            return null
        }
        
        // Get the index in the wallpaperIds list
        val index = if (isNext) currentIndex + 1 else currentIndex - 1
        
        // Check if the index is valid
        if (index < 0 || index >= wallpaperIds.size) {
            Log.d("DetailViewModel", "Invalid index for ${if (isNext) "next" else "previous"} wallpaper: $index")
            return null
        }
        
        // Attempt to get the adjacent wallpaper from the map
        val adjacentWallpaper = adjacentWallpapersMap[adjacentId]
        
        if (adjacentWallpaper != null) {
            Log.d("DetailViewModel", "Found ${if (isNext) "next" else "previous"} wallpaper in map: ${adjacentWallpaper.id}")
            return adjacentWallpaper.thumbnail
        }
        
        // If we couldn't find it in the map, use the current wallpaper's thumbnail as a fallback
        Log.d("DetailViewModel", "Using current wallpaper thumbnail as fallback for ${if (isNext) "next" else "previous"} wallpaper")
        return _wallpaper.value?.thumbnail
    }

    /**
     * Get a wallpaper by ID from adjacent wallpapers cache
     */
    fun getWallpaperById(wallpaperId: String?): Wallpaper? {
        if (wallpaperId == null) return null
        return adjacentWallpapersMap[wallpaperId]
    }
    

    
    /**
     * FINAL FIX: Directly forces update of adjacent wallpapers
     * This ensures we always have valid next/prev wallpapers for swiping
     */
    fun forceUpdateAdjacentWallpapers(forceFreshLoad: Boolean = false) {
        
        if (wallpaperIds.isEmpty()) {
            return
        }
        
        if (currentIndex == -1) {
            // Try to find the current wallpaper index if not set
            val currentWallpaperId = _wallpaper.value?.id
            if (currentWallpaperId != null) {
                // Find the index in our list
                currentIndex = wallpaperIds.indexOf(currentWallpaperId)
            } else {
                return
            }
        }
        
        // Recalculate next and previous indices
        updateAdjacentIndices()
        
        // Ensure all adjacent wallpapers are pre-loaded
        viewModelScope.launch(Dispatchers.IO) {
            try {
                
                // Get and update the next wallpaper ID
                if (nextIndex != -1 && nextIndex < wallpaperIds.size) {
                    nextWallpaperId = wallpaperIds[nextIndex]
                    // Force preload if not in cache
                    if (nextWallpaperId != null && !adjacentWallpapersMap.containsKey(nextWallpaperId)) {
                        preloadWallpaper(nextWallpaperId!!, highPriority = true)
                    }
                } else {
                    nextWallpaperId = null
                }
                
                // Get and update the previous wallpaper ID
                if (prevIndex != -1 && prevIndex < wallpaperIds.size) {
                    prevWallpaperId = wallpaperIds[prevIndex]
                    // Force preload if not in cache
                    if (prevWallpaperId != null && !adjacentWallpapersMap.containsKey(prevWallpaperId)) {
                        preloadWallpaper(prevWallpaperId!!, highPriority = true)
                    }
                } else {
                    prevWallpaperId = null
                }
                
            } catch (e: Exception) {
            }
        }
    }
    
    /**
     * Sets the current wallpaper by ID and updates the position in the collection
     */
    private fun setCurrentWallpaperById(wallpaperId: String) {
        currentIndex = wallpaperIds.indexOf(wallpaperId)
        if (currentIndex != -1) {
            updateAdjacentIndices()
        } else {
        }
    }

    /**
     * Reset swipe loading states - called when ads are dismissed during swipe operations
     * Enhanced version with proper timing and comprehensive state reset
     */
    fun resetSwipeLoadingStates() {
        viewModelScope.launch {
            // Add small delay to avoid conflicts with animation timing
            delay(200)
            
            // Reset all loading states
            _isLoadingNextWallpaper.value = false
            _isLoadingPrevWallpaper.value = false
            _isLoading.value = false
            
            // Reset adjacent wallpaper loading state
            _isLoadingAdjacentWallpapers.value = false
            
            Log.d("DetailViewModel", "ENHANCED: Reset all swipe loading states after ad dismissal")
        }
    }

    /**
     * FAVORITES FIX: Load adjacent wallpapers from favorites list for proper swipe navigation
     */
    private suspend fun loadAdjacentWallpapersFromFavorites(currentId: String) {
        try {
            // Collect favorites from the repository flow - Use first() to get current value
            val favoritesList = favoritesRepository.favorites.first()
            
            if (favoritesList.isEmpty()) {
                Log.d("DetailViewModel", "No favorites available for adjacent loading")
                _isLoadingAdjacentWallpapers.value = false
                return
            }
            
            // Create wallpaper IDs list from favorites
            wallpaperIds.clear()
            wallpaperIds.addAll(favoritesList.mapNotNull { it.id })
            
            // Find current index in favorites
            currentIndex = wallpaperIds.indexOf(currentId)
            
            if (currentIndex == -1) {
                Log.w("DetailViewModel", "Current wallpaper $currentId not found in favorites")
                _isLoadingAdjacentWallpapers.value = false
                return
            }
            
            // Clear and populate adjacent wallpapers map with favorites
            adjacentWallpapersMap.clear()
            favoritesList.forEach { wallpaper ->
                wallpaper.id?.let { id ->
                    adjacentWallpapersMap[id] = wallpaper
                }
            }
            
            // Update adjacent indices for proper navigation
            updateAdjacentIndices()
            
            Log.d("DetailViewModel", "FAVORITES: Loaded ${favoritesList.size} favorites for swipe navigation")
            Log.d("DetailViewModel", "FAVORITES: Current index: $currentIndex, Next: $nextWallpaperId, Prev: $prevWallpaperId")
            
        } catch (e: Exception) {
            Log.e("DetailViewModel", "Error loading adjacent wallpapers from favorites", e)
        } finally {
            _isLoadingAdjacentWallpapers.value = false
        }
    }
}