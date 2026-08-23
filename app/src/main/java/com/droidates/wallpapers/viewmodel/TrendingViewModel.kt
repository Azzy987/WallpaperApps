package com.droidates.wallpapers.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.droidates.wallpapers.model.Wallpaper
import com.droidates.wallpapers.utils.FirestorePaginator
import com.droidates.wallpapers.utils.SortOption
import com.droidates.wallpapers.utils.SortPreferences
import com.droidates.wallpapers.utils.toFirestoreSort
import com.droidates.wallpapers.config.AppConfig
import com.droidates.wallpapers.utils.WallpaperDiskCache
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import kotlinx.coroutines.launch
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private const val TAG = "TrendingViewModel"
private const val VERBOSE_LOGGING = false
private const val CACHE_KEY_TRENDING = "trending"

@HiltViewModel
class TrendingViewModel @Inject constructor(
    private val firestore: FirebaseFirestore,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _trendingWallpapers = MutableStateFlow<List<Wallpaper>>(emptyList())
    val trendingWallpapers = _trendingWallpapers.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _hasReachedEnd = MutableStateFlow(false)
    val hasReachedEnd = _hasReachedEnd.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private val _currentSortOption = MutableStateFlow(SortOption.LATEST)
    val currentSortOption: StateFlow<SortOption> = _currentSortOption.asStateFlow()

    private var paginator: FirestorePaginator? = null
    
    // Track initialization states
    private var hasInitiallyLoaded = false
    private var hasLoadedSortOption = false
    private var hasSavedToCache = false

    init {
        // All initialization is completely deferred until the tab becomes visible
        // This eliminates ALL launch lag from this ViewModel

        // Persist first-page results to disk cache once after Firestore load
        viewModelScope.launch(Dispatchers.IO) {
            _trendingWallpapers.collect { wallpapers ->
                if (wallpapers.isNotEmpty() && !hasSavedToCache
                    && _currentSortOption.value == SortOption.LATEST
                ) {
                    hasSavedToCache = true
                    WallpaperDiskCache.getInstance(context).saveCollectionPage(CACHE_KEY_TRENDING, wallpapers)
                    Log.d(TAG, "Saved ${wallpapers.size} trending wallpapers to disk cache")
                    return@collect
                }
            }
        }

        if (VERBOSE_LOGGING) {
            Log.d(TAG, "TrendingViewModel constructed with zero initialization work")
        }
    }
    
    /**
     * CRITICAL: Ultra-lazy loading - only load when tab becomes visible
     * This is the ONLY place where any work happens
     * THEME CHANGE FIX: Allow reloading if data is empty (activity recreation)
     */
    fun loadInitialDataIfNeeded() {
        // THEME CHANGE FIX: Check if data is empty due to activity recreation
        val needsReload = !hasInitiallyLoaded || _trendingWallpapers.value.isEmpty()
        
        if (!needsReload) return
        
        hasInitiallyLoaded = true
        
        if (VERBOSE_LOGGING) {
            Log.d(TAG, "Starting ultra-lazy initialization (reload: ${_trendingWallpapers.value.isEmpty()})")
        }
        
        viewModelScope.launch {
            _error.value = null
            _isLoading.value = true

            // Step 1: Load sort option on background thread (ultra-fast)
            if (!hasLoadedSortOption) {
                withContext(Dispatchers.IO) {
                    try {
                        val savedSortOption = SortPreferences.getSortOption(context, isHomeScreen = false)
                        withContext(Dispatchers.Main) {
                            _currentSortOption.value = savedSortOption
                            hasLoadedSortOption = true

                            if (VERBOSE_LOGGING) {
                                Log.d(TAG, "Sort option loaded: $savedSortOption")
                            }
                        }
                    } catch (e: Exception) {
                        _currentSortOption.value = SortOption.LATEST
                        hasLoadedSortOption = true
                    }
                }
            }

            // Step 2: Try disk cache before hitting Firestore (only for default sort, first page)
            val diskCache = WallpaperDiskCache.getInstance(context)
            val cachedWallpapers = withContext(Dispatchers.IO) {
                diskCache.loadCollectionPage(CACHE_KEY_TRENDING)
            }
            if (cachedWallpapers != null && _currentSortOption.value == SortOption.LATEST) {
                Log.d(TAG, "Serving ${cachedWallpapers.size} trending wallpapers from cache (no Firestore read)")
                _trendingWallpapers.value = cachedWallpapers
                _isLoading.value = false
                // Run initial load in background to establish the Firestore cursor (lastDocument),
                // so subsequent loadMore calls work correctly.
                if (paginator == null) {
                    paginator = createPaginator(_currentSortOption.value)
                }
                launch(Dispatchers.IO) { paginator?.loadInitialWallpapers(silentRefresh = true) }
                return@launch
            }

            // Step 3: Create paginator with loaded sort option (recreate if needed)
            if (paginator == null || _trendingWallpapers.value.isEmpty()) {
                paginator = createPaginator(_currentSortOption.value)
            }

            // Step 4: Load data from Firestore
            try {
                paginator?.loadInitialWallpapers()
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
        
        val baseQuery = firestore.collection(AppConfig.COLLECTION_TRENDING)
            .orderBy(sortField, sortDirection)
        
        return FirestorePaginator(
            firestore = firestore,
            baseQuery = baseQuery,
            _wallpapers = _trendingWallpapers,
            _isLoading = _isLoading,
            _hasReachedEnd = _hasReachedEnd
        )
    }
    
    fun setSortOption(option: SortOption) {
        if (_currentSortOption.value == option) return

        // Immediate UI feedback
        _isLoading.value = true
        _trendingWallpapers.value = emptyList()
        _hasReachedEnd.value = false

        // Sort changed — invalidate cache
        hasSavedToCache = false
        WallpaperDiskCache.getInstance(context).invalidateCollectionCache(CACHE_KEY_TRENDING)

        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    SortPreferences.saveSortOption(context, option, isHomeScreen = false)
                } catch (e: Exception) {
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

        // Invalidate cache so fresh data is fetched and re-cached
        hasSavedToCache = false
        WallpaperDiskCache.getInstance(context).invalidateCollectionCache(CACHE_KEY_TRENDING)

        viewModelScope.launch {
            paginator = createPaginator(_currentSortOption.value)
            paginator?.loadInitialWallpapers()
        }
    }
} 