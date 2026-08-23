package com.droidates.wallpapers.viewmodel


import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import kotlinx.coroutines.launch
import androidx.lifecycle.viewModelScope
import com.droidates.wallpapers.model.Banner
import com.droidates.wallpapers.model.Wallpaper
import com.droidates.wallpapers.utils.FirestorePaginator
import com.droidates.wallpapers.utils.SortOption
import com.droidates.wallpapers.utils.SortPreferences
import com.droidates.wallpapers.utils.toFirestoreSort
import com.droidates.wallpapers.config.AppConfig
import com.droidates.wallpapers.utils.WallpaperDiskCache
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await

private const val TAG = "HomeViewModel"
private const val VERBOSE_LOGGING = false
private const val CACHE_KEY_HOME = "home"

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val firestore: FirebaseFirestore,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _wallpapers = MutableStateFlow<List<Wallpaper>>(emptyList())
    val wallpapers = _wallpapers.asStateFlow()

    private val _banners = MutableStateFlow<List<Banner>>(emptyList())
    val banners = _banners.asStateFlow()

    private val _paywallBanners = MutableStateFlow<List<Banner>>(emptyList())
    val paywallBanners = _paywallBanners.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _hasReachedEnd = MutableStateFlow(false)
    val hasReachedEnd = _hasReachedEnd.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private val _currentSortOption = MutableStateFlow(SortOption.LAUNCH_YEAR)
    val currentSortOption = _currentSortOption.asStateFlow()

    private var paginator: FirestorePaginator? = null
    
    // Track initialization states
    private var hasInitiallyLoaded = false
    private var hasLoadedSortOption = false
    private var hasSavedToCache = false
    
    init {
        // All initialization is completely deferred until the tab becomes visible
        // This eliminates ALL launch lag from this ViewModel

        // Persist first-page results to disk cache once after Firestore load
        // so the next app open skips the Firestore round-trip entirely.
        viewModelScope.launch(Dispatchers.IO) {
            _wallpapers.collect { wallpapers ->
                if (wallpapers.isNotEmpty() && !hasSavedToCache
                    && _currentSortOption.value == SortOption.LAUNCH_YEAR
                ) {
                    hasSavedToCache = true
                    WallpaperDiskCache.getInstance(context).saveCollectionPage(CACHE_KEY_HOME, wallpapers)
                    Log.d(TAG, "Saved ${wallpapers.size} home wallpapers to disk cache")
                    return@collect
                }
            }
        }

        if (VERBOSE_LOGGING) {
            Log.d(TAG, "HomeViewModel constructed with zero initialization work")
        }
    }
    
    /**
     * CRITICAL: Ultra-lazy loading - only load when tab becomes visible
     * This is the ONLY place where any work happens
     * THEME CHANGE FIX: Allow reloading if data is empty (activity recreation)
     */
    fun loadInitialDataIfNeeded() {
        // THEME CHANGE FIX: Check if data is empty due to activity recreation
        val needsReload = !hasInitiallyLoaded || _wallpapers.value.isEmpty()

        if (!needsReload) return

        hasInitiallyLoaded = true

        if (VERBOSE_LOGGING) {
            Log.d(TAG, "Starting ultra-lazy initialization (reload: ${_wallpapers.value.isEmpty()})")
        }

        viewModelScope.launch(Dispatchers.IO) {
            _error.value = null
            _isLoading.value = true

            // Step 1: Load sort option on background thread (ultra-fast)
            if (!hasLoadedSortOption) {
                try {
                    val savedSortOption = SortPreferences.getSortOption(context, isHomeScreen = true)
                    Log.d(TAG, "SortPreferences returned: $savedSortOption")
                    _currentSortOption.value = savedSortOption
                    hasLoadedSortOption = true

                    if (VERBOSE_LOGGING) {
                        Log.d(TAG, "Sort option loaded: $savedSortOption")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error loading sort option, falling back to ViewModel default.", e)
                    _currentSortOption.value = SortOption.LAUNCH_YEAR
                    hasLoadedSortOption = true
                }
            }

            // Step 2: Try disk cache before hitting Firestore (only for default sort, first page)
            val diskCache = WallpaperDiskCache.getInstance(context)
            val cachedWallpapers = diskCache.loadCollectionPage(CACHE_KEY_HOME)
            if (cachedWallpapers != null && _currentSortOption.value == SortOption.LAUNCH_YEAR) {
                Log.d(TAG, "Serving ${cachedWallpapers.size} home wallpapers from cache (no Firestore read)")
                _wallpapers.value = cachedWallpapers
                _isLoading.value = false
                // Create paginator and run initial load in background to establish the Firestore
                // cursor (lastDocument), so subsequent loadMore calls work correctly.
                val effectiveSortOption = _currentSortOption.value
                if (paginator == null) {
                    paginator = createPaginator(effectiveSortOption)
                }
                launch(Dispatchers.IO) { paginator?.loadInitialWallpapers(silentRefresh = true) }
                // Load banners in parallel — they are lightweight and always fresh
                launch(Dispatchers.IO) { loadBanners() }
                return@launch
            }

            // Step 3: Create paginator with loaded sort option (recreate if needed)
            if (paginator == null || _wallpapers.value.isEmpty()) {
                val effectiveSortOption = if (hasLoadedSortOption) _currentSortOption.value else SortOption.LAUNCH_YEAR
                paginator = createPaginator(effectiveSortOption)
                Log.d(TAG, "Paginator created with sort option: $effectiveSortOption")
            }

            // Step 4: Load data in parallel for maximum speed
            try {
                launch(Dispatchers.IO) { paginator?.loadInitialWallpapers() }
                launch(Dispatchers.IO) { loadBanners() }
            } catch (e: Exception) {
                _error.value = "Couldn't load wallpapers. Check your connection and try again."
                _isLoading.value = false
            }
        }
    }
    
    fun retryLoad() {
        hasInitiallyLoaded = false
        loadInitialDataIfNeeded()
    }

    private fun createPaginator(sortOption: SortOption): FirestorePaginator {
        val (sortField, sortDirection) = sortOption.toFirestoreSort()
        
        val baseQuery = firestore.collection(AppConfig.COLLECTION_HOME)
            .orderBy(sortField, sortDirection)
        
        return FirestorePaginator(
            firestore = firestore,
            baseQuery = baseQuery,
            _wallpapers = _wallpapers,
            _isLoading = _isLoading,
            _hasReachedEnd = _hasReachedEnd
        )
    }
    
    fun setSortOption(option: SortOption) {
        if (_currentSortOption.value == option) return

        // Immediate UI feedback
        _isLoading.value = true
        _wallpapers.value = emptyList()
        _hasReachedEnd.value = false

        // Sort changed — cache is now stale for the new sort order
        hasSavedToCache = false
        WallpaperDiskCache.getInstance(context).invalidateCollectionCache(CACHE_KEY_HOME)

        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    SortPreferences.saveSortOption(context, option, isHomeScreen = true)
                } catch (e: Exception) {
                    // Continue on error - don't block UI
                    if (VERBOSE_LOGGING) {
                        Log.e(TAG, "Error saving sort option", e)
                    }
                }
            }

            _currentSortOption.value = option
            paginator = createPaginator(option)
            paginator?.loadInitialWallpapers()
        }
    }
    
    fun applySortOption(option: SortOption) {
        setSortOption(option)
    }

    fun loadMoreWallpapers() {
        if (_isLoading.value) return // Prevent multiple simultaneous loads
        
        viewModelScope.launch {
            paginator?.loadMoreWallpapers()
        }
    }

    fun refreshWallpapers(forceRefresh: Boolean = false) {
        if (_isLoading.value && !forceRefresh) return

        // Invalidate cache so fresh Firestore data is fetched and re-cached
        hasSavedToCache = false
        WallpaperDiskCache.getInstance(context).invalidateCollectionCache(CACHE_KEY_HOME)

        viewModelScope.launch {
            // Reset paginator with current sort option
            paginator = createPaginator(_currentSortOption.value)
            paginator?.loadInitialWallpapers()
        }
    }

    fun refreshBanners(forceRefresh: Boolean = false) {
        if (_isLoading.value && !forceRefresh) return
        
        if (VERBOSE_LOGGING) {
            Log.d(TAG, "Refresh banners requested (force: $forceRefresh)")
        }
        
        viewModelScope.launch {
            loadBanners()
        }
    }
    
    // Debug function to manually trigger banner loading
    fun debugLoadBanners() {
        if (VERBOSE_LOGGING) {
            Log.d(TAG, "DEBUG: Manually triggering banner loading...")
        }
        viewModelScope.launch {
            loadBanners()
            loadPaywallBanners()
        }
    }

    private suspend fun loadPaywallBanners() {
        try {
            withContext(Dispatchers.IO) {
                val documents = firestore.collection(AppConfig.COLLECTION_PAYWALL_BANNERS).get().await()
                val bannerList = documents.mapNotNull { doc ->
                    val url = doc.getString("wallpaperUrl") ?: return@mapNotNull null
                    Banner(wallpaperId = doc.id, bannerUrl = url, bannerName = "", depthEffect = false, exclusive = true)
                }
                _paywallBanners.value = bannerList
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading paywall banners", e)
        }
    }

    private suspend fun loadBanners() {
        if (VERBOSE_LOGGING) {
            Log.d(TAG, "Starting banner loading process...")
        }
        
        try {
            withContext(Dispatchers.IO) {
                if (VERBOSE_LOGGING) {
                    Log.d(TAG, "Attempting to fetch banners from: Banners/IPhoneWallpapers/OnePlus7WallpapersBanners")
                }

                val task = firestore
                    .collection(AppConfig.COLLECTION_BANNERS)
                    .document(AppConfig.DOCUMENT_BANNERS_APP)
                    .collection(AppConfig.COLLECTION_BANNERS_SUB)
                    .orderBy("bannerName")
                    .get()
                    
                if (VERBOSE_LOGGING) {
                    Log.d(TAG, "Firestore query created, awaiting response...")
                }
                
                val documents = task.await()
                
                if (VERBOSE_LOGGING) {
                    Log.d(TAG, "Firestore response received. Document count: ${documents.size()}")
                    Log.d(TAG, "Document IDs: ${documents.map { it.id }}")
                }
                
                val bannerList = documents.mapNotNull { doc ->
                    try {
                        if (VERBOSE_LOGGING) {
                            Log.d(TAG, "Processing banner document: ${doc.id}")
                            Log.d(TAG, "Document data: ${doc.data}")
                        }

                        val banner = Banner(
                            wallpaperId = doc.id,
                            bannerUrl = doc.getString("bannerUrl") ?: "",
                            bannerName = doc.getString("bannerName") ?: "",
                            depthEffect = doc.getBoolean("depthEffect") ?: false,
                            exclusive = doc.getBoolean("exclusive") ?: false,
                            bannerType = doc.getString("bannerType") ?: "wallpaper",
                            appUrl = doc.getString("appUrl") ?: ""
                        )

                        if (VERBOSE_LOGGING) {
                            Log.d(TAG, "Successfully parsed banner: ${banner.bannerName} (${banner.wallpaperId})")
                        }

                        banner
                    } catch (e: Exception) {
                        if (VERBOSE_LOGGING) {
                            Log.e(TAG, "Error parsing banner document: ${doc.id}", e)
                        }
                        null
                    }
                }

                // ANR FIX: Update state directly (StateFlow is thread-safe)
                _banners.value = bannerList
                if (VERBOSE_LOGGING) {
                    Log.d(TAG, "Loaded ${bannerList.size} banners from Banners/IPhoneWallpapers/OnePlus7WallpapersBanners")
                }
            }
        } catch (e: Exception) {
            if (VERBOSE_LOGGING) {
                Log.e(TAG, "Error loading banners from new structure: ${e.message}", e)
                Log.d(TAG, "Trying fallback to old banner structure...")
            }
            
            // Fallback: Try the old banner structure
            try {
                withContext(Dispatchers.IO) {
                    val fallbackTask = firestore.collection(AppConfig.COLLECTION_BANNERS).get()
                    val fallbackDocuments = fallbackTask.await()
                    
                    if (VERBOSE_LOGGING) {
                        Log.d(TAG, "Fallback query successful. Document count: ${fallbackDocuments.size()}")
                    }
                    
                    val fallbackBannerList = fallbackDocuments.mapNotNull { doc ->
                        try {
                            Banner(
                                wallpaperId = doc.id,
                                bannerUrl = doc.getString("bannerUrl") ?: "",
                                bannerName = doc.getString("bannerName") ?: "",
                                depthEffect = doc.getBoolean("depthEffect") ?: false,
                                exclusive = doc.getBoolean("exclusive") ?: false
                            )
                        } catch (e: Exception) {
                            if (VERBOSE_LOGGING) {
                                Log.e(TAG, "Error parsing fallback banner: ${doc.id}", e)
                            }
                            null
                        }
                    }

                    // ANR FIX: Update state directly (StateFlow is thread-safe)
                    _banners.value = fallbackBannerList
                    if (VERBOSE_LOGGING) {
                        Log.d(TAG, "Loaded ${fallbackBannerList.size} banners from FALLBACK structure")
                    }
                }
            } catch (fallbackException: Exception) {
                if (VERBOSE_LOGGING) {
                    Log.e(TAG, "Both new and fallback banner loading failed", fallbackException)
                }
                // Don't crash - just show empty banners
                // ANR FIX: Update state directly (StateFlow is thread-safe)
                _banners.value = emptyList()
            }
        }
    }
} 