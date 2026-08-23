package com.droidates.wallpapers.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import com.droidates.wallpapers.model.Category
import com.droidates.wallpapers.model.Wallpaper
import com.droidates.wallpapers.data.local.FavoritesManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.DocumentSnapshot
import com.droidates.wallpapers.utils.FirestorePaginator
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import com.google.firebase.Timestamp
import android.util.Log
import kotlinx.coroutines.delay
import android.content.Context
import com.droidates.wallpapers.utils.SortOption
import dagger.hilt.android.qualifiers.ApplicationContext
import com.droidates.wallpapers.config.AppConfig

@HiltViewModel
class CategoryViewModel @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val favoritesManager: FavoritesManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    companion object {
        private const val TAG = "CategoryViewModel"
    }

    private val _category = MutableStateFlow<Category?>(null)
    val category = _category.asStateFlow()

    private val _categoryWallpapers = MutableStateFlow<List<Wallpaper>>(emptyList())
    val categoryWallpapers = _categoryWallpapers.asStateFlow()

    private val _favoriteIds = MutableStateFlow<Set<String>>(emptySet())

    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()

    private val _hasReachedEnd = MutableStateFlow(false)
    val hasReachedEnd = _hasReachedEnd.asStateFlow()

    // Add a state flow for available series
    private val _availableSeries = MutableStateFlow<List<String>>(emptyList())
    val availableSeries: StateFlow<List<String>> = _availableSeries

    private val _subcategories = MutableStateFlow<List<String>>(emptyList())
    val subcategories = _subcategories.asStateFlow()

    // Expose currentCategory as a StateFlow for UI to track
    private val _currentCategory = MutableStateFlow<String?>(null)
    val currentCategory = _currentCategory.asStateFlow()

    // Expose current subcategory as a StateFlow for UI to track
    private val _currentSubcategory = MutableStateFlow<String?>(null)
    val currentSubcategory = _currentSubcategory.asStateFlow()

    // Keep these as private vars for internal usage
    private var currentSeries: String? = null
    
    private val subcategoryStates = java.util.concurrent.ConcurrentHashMap<String, SubcategoryState>()
    private val stateJobs = java.util.concurrent.ConcurrentHashMap<String, kotlinx.coroutines.Job>()
    
    // Helper to convert null subcategory to a safe key
    private fun getSubcategoryKey(subcategory: String?): String = subcategory ?: "ALL_SUBCATEGORIES"
    
    private fun getSubcategoryState(subcategory: String?): SubcategoryState {
        val key = getSubcategoryKey(subcategory)
        return subcategoryStates.getOrPut(key) {
            SubcategoryState().also { state ->
                // For the default subcategory (null), sync with categoryWallpapers
                if (subcategory == null) {
                    // Cancel any existing job for this subcategory
                    stateJobs[key]?.cancel()
                    
                    // Create a single job that combines all state flows
                    stateJobs[key] = viewModelScope.launch {
                        kotlinx.coroutines.flow.combine(
                            state.wallpapers,
                            state.isLoading, 
                            state.hasReachedEnd
                        ) { wallpapers, loading, hasReached ->
                            Triple(wallpapers, loading, hasReached)
                        }.collect { (wallpapers, loading, hasReached) ->
                            _categoryWallpapers.value = wallpapers
                            _isLoading.value = loading
                            _hasReachedEnd.value = hasReached
                        }
                    }
                }
            }
        }
    }

    fun wallpapersForSubcategory(subcategory: String?): StateFlow<List<Wallpaper>> {
        return getSubcategoryState(subcategory).wallpapers
    }

    fun isLoadingForSubcategory(subcategory: String?): StateFlow<Boolean> {
        return getSubcategoryState(subcategory).isLoading
    }

    fun hasReachedEndForSubcategory(subcategory: String?): StateFlow<Boolean> {
        return getSubcategoryState(subcategory).hasReachedEnd
    }
    
    // Simplified Apple series handling for iPhone subcategories
    fun wallpapersForSeries(series: String?): StateFlow<List<Wallpaper>> {
        return getSubcategoryState(null).wallpapers // Use default subcategory state
    }

    fun isLoadingForSeries(series: String?): StateFlow<Boolean> {
        return getSubcategoryState(null).isLoading // Use default subcategory state
    }

    fun hasReachedEndForSeries(series: String?): StateFlow<Boolean> {
        return getSubcategoryState(null).hasReachedEnd // Use default subcategory state
    }
    
    fun loadMoreWallpapersForSeries(series: String?) {
        viewModelScope.launch {
            loadMoreWallpapers() // Use simplified approach
        }
    }

    // Simplified Apple series filtering for iPhone subcategories
    fun filterBySeries(series: String?) {
        Log.d(TAG, "Filtering by series: $series")
        currentSeries = series
        
        val categoryName = _currentCategory.value ?: return
        
        // Only Apple category supports series filtering
        if (!categoryName.equals(AppConfig.COLLECTION_HOME, ignoreCase = true)) {
            Log.d(TAG, "Series filtering only available for Apple category")
            return
        }
        
        // Clear paginator cache when series filter changes
        paginatorCache.clear()
        Log.d(TAG, "Cleared paginator cache for series filter")
        
        val state = getSubcategoryState(null)
        state.isLoading.value = true
        state.wallpapers.value = emptyList()
        state.hasReachedEnd.value = false
        
        // Create new paginator with series filter
        val baseQuery = if (series != null && series != "All Series") {
            Log.d(TAG, "Creating series filter query for series: $series")
            firestore.collection(AppConfig.COLLECTION_HOME)
                .whereEqualTo("series", series)
                .orderBy(AppConfig.FIELD_TIMESTAMP, Query.Direction.DESCENDING)
        } else {
            Log.d(TAG, "Creating query for all Apple wallpapers")
            firestore.collection(AppConfig.COLLECTION_HOME)
                .orderBy(AppConfig.FIELD_TIMESTAMP, Query.Direction.DESCENDING)
        }
        
        paginator = FirestorePaginator(
            firestore = firestore,
            baseQuery = baseQuery,
            _wallpapers = state.wallpapers,
            _isLoading = state.isLoading,
            _hasReachedEnd = state.hasReachedEnd
        )
        
        // Cache the new paginator
        val key = getSubcategoryKey(null)
        paginatorCache[key] = paginator
        
        // Load initial wallpapers with series filter
        paginator?.loadInitialWallpapers()
        
        Log.d(TAG, "Series filter applied with query-level filtering and cached")
    }

    // Paginator instances for each subcategory - will be initialized when subcategory is selected
    private val paginatorCache = java.util.concurrent.ConcurrentHashMap<String, FirestorePaginator?>()
    
    // Current paginator - will be initialized when subcategory is selected
    private var paginator: FirestorePaginator? = null

    // Fetch category details including subcategories
    suspend fun fetchCategoryDetails(categoryName: String) {
        try {
            _isLoading.value = true
            
            val categoryDoc = firestore.collection(AppConfig.COLLECTION_CATEGORIES)
                .whereEqualTo("name", categoryName)
                .get()
                .await()
            
            if (!categoryDoc.isEmpty) {
                val doc = categoryDoc.documents.first()
                // Use safer cast approach 
                val rawList = doc.get("subcategories")
                val subcategoriesList = if (rawList is List<*>) {
                    rawList.mapNotNull { it as? String }
                } else {
                    emptyList()
                }
                _subcategories.value = subcategoriesList
                
                Log.d(TAG, "Category $categoryName has ${subcategoriesList.size} subcategories: $subcategoriesList")
            } else {
                _subcategories.value = emptyList()
                Log.d(TAG, "Category $categoryName not found or has no subcategories")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching category details", e)
            _subcategories.value = emptyList()
        }
    }

    // Fetch wallpapers by category name
    fun fetchCategoryByName(categoryName: String) {
        Log.d(TAG, "Fetching category: $categoryName (Current category: ${_currentCategory.value})")
        
        // PERSISTENCE FIX: Only clear data if we're truly switching to a different category
        val isNewCategory = categoryName != _currentCategory.value
        
        // Always load if new category
        if (isNewCategory) {
            Log.d(TAG, "Loading category: $categoryName (isNewCategory: $isNewCategory)")
            // Clear wallpapers when switching to a different category
            subcategoryStates.clear()
            paginatorCache.clear()
            _categoryWallpapers.value = emptyList()
            _isLoading.value = true
            _hasReachedEnd.value = false
        
            // Set the current category
            _currentCategory.value = categoryName
            currentSeries = null
            _currentSubcategory.value = null
        
            // Fetch category details to get subcategories
            viewModelScope.launch {
                fetchCategoryDetails(categoryName)
                filterBySubcategory(null)
            }

            // If Apple category, load available series
            if (categoryName.equals(AppConfig.COLLECTION_HOME, ignoreCase = true)) {
                loadAvailableSeries()
            } else {
                // Clear available series for non-Apple categories
                _availableSeries.value = emptyList()
            }
        }
    }
    
    /**
     * Load all available Apple series from Firestore
     */
    private fun loadAvailableSeries() {
        viewModelScope.launch {
            try {
                Log.d(TAG, "Loading available Apple series")

                // Use a map to store series with their launch years for sorting
                val seriesWithYear = mutableMapOf<String, Int?>()

                val wallpaperDocs = firestore.collection(AppConfig.COLLECTION_HOME)
                    .get()
                    .await()

                if (!wallpaperDocs.isEmpty) {
                    for (doc in wallpaperDocs.documents) {
                        try {
                            val series = doc.getString("series")
                            val launchYear = doc.getLong("launchYear")?.toInt()
                            
                            if (!series.isNullOrBlank()) {
                                // Store the series with its launch year (keep the highest year if multiple entries)
                                val existingYear = seriesWithYear[series]
                                if (existingYear == null || (launchYear != null && launchYear > existingYear)) {
                                    seriesWithYear[series] = launchYear
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error extracting series", e)
                        }
                    }

                    // Sort series by launch year (newest first), then alphabetically for those without launch year
                    val sortedSeries = seriesWithYear.entries.sortedWith { entry1, entry2 ->
                        val year1 = entry1.value
                        val year2 = entry2.value
                        
                        when {
                            // Both have launch years - sort by year (newest first)
                            year1 != null && year2 != null -> year2.compareTo(year1)
                            // Only first has launch year - it comes first
                            year1 != null && year2 == null -> -1
                            // Only second has launch year - it comes first
                            year1 == null && year2 != null -> 1
                            // Neither has launch year - sort alphabetically
                            else -> entry1.key.compareTo(entry2.key)
                        }
                    }.map { it.key }

                    // Update available series with "All Series" first, then sorted series
                    _availableSeries.value = listOf("All Series") + sortedSeries
                    Log.d(TAG, "Available series sorted by launch year: ${_availableSeries.value}")
                } else {
                    _availableSeries.value = listOf("All Series")
                    Log.d(TAG, "No wallpapers found, setting default series")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading series", e)
                _availableSeries.value = listOf("All Series")
            }
        }
    }


    // Filter wallpapers by subcategory - enhanced with caching
    fun filterBySubcategory(subcategory: String?) {
        Log.d(TAG, "Filtering by subcategory: $subcategory")
        _currentSubcategory.value = subcategory
        
        val categoryName = _currentCategory.value ?: return
        
        // Check if we have a cached paginator for this subcategory
        val key = getSubcategoryKey(subcategory)
        if (paginatorCache.containsKey(key)) {
            Log.d(TAG, "Using cached paginator for subcategory: $subcategory")
            paginator = paginatorCache[key]
            return
        }
        
        // No cached paginator, need to create a new one
        Log.d(TAG, "No cached paginator for subcategory: $subcategory, creating a new one")
        val state = getSubcategoryState(subcategory)
        state.isLoading.value = true
        state.wallpapers.value = emptyList()
        state.hasReachedEnd.value = false
        
        // Create new paginator with subcategory filter
        paginator = when {
            categoryName.equals(AppConfig.COLLECTION_HOME, ignoreCase = true) -> {
                Log.d(TAG, "Creating Apple paginator with subcategory: $subcategory and current series: $currentSeries")
                val baseQuery = when {
                    currentSeries != null && currentSeries != "All Series" -> {
                        // If a series is selected, always filter by that series regardless of subcategory
                        Log.d(TAG, "Using current series filter: $currentSeries")
                        firestore.collection(AppConfig.COLLECTION_HOME)
                            .whereEqualTo("series", currentSeries)
                            .orderBy(AppConfig.FIELD_TIMESTAMP, Query.Direction.DESCENDING)
                    }
                    subcategory != null -> {
                        // If no series selected but subcategory is selected, filter by subcategory
                        Log.d(TAG, "Using subcategory filter: $subcategory")
                        firestore.collection(AppConfig.COLLECTION_HOME)
                            .whereEqualTo("series", subcategory)
                            .orderBy(AppConfig.FIELD_TIMESTAMP, Query.Direction.DESCENDING)
                    }
                    else -> {
                        // No filters, show all Apple wallpapers
                        Log.d(TAG, "No filters, showing all Apple wallpapers")
                        firestore.collection(AppConfig.COLLECTION_HOME)
                            .orderBy(AppConfig.FIELD_TIMESTAMP, Query.Direction.DESCENDING)
                    }
                }
                FirestorePaginator(
                    firestore = firestore,
                    baseQuery = baseQuery,
                    _wallpapers = state.wallpapers,
                    _isLoading = state.isLoading,
                    _hasReachedEnd = state.hasReachedEnd
                )
            }
            
            categoryName.equals("Depth Effect", ignoreCase = true) -> {
                Log.d(TAG, "Creating Depth Effect paginator with subcategory filter: $subcategory")
                val baseQuery = if (subcategory != null) {
                    firestore.collection(AppConfig.COLLECTION_TRENDING)
                        .whereEqualTo("depthEffect", true)
                        .whereEqualTo("subCategory", subcategory)
                } else {
                    firestore.collection(AppConfig.COLLECTION_TRENDING)
                        .whereEqualTo("depthEffect", true)
                }
                FirestorePaginator(
                    firestore = firestore,
                    baseQuery = baseQuery,
                    _wallpapers = state.wallpapers,
                    _isLoading = state.isLoading,
                    _hasReachedEnd = state.hasReachedEnd
                )
            }
            
            else -> {
                Log.d(TAG, "Creating TrendingWallpapers paginator with category: $categoryName and subcategory filter: $subcategory")
                val baseQuery = if (subcategory != null) {
                    firestore.collection(AppConfig.COLLECTION_TRENDING)
                        .whereEqualTo("category", categoryName)
                        .whereEqualTo("subCategory", subcategory)
                        .orderBy(AppConfig.FIELD_TIMESTAMP, Query.Direction.DESCENDING)
                } else {
                    firestore.collection(AppConfig.COLLECTION_TRENDING)
                        .whereEqualTo("category", categoryName)
                        .orderBy(AppConfig.FIELD_TIMESTAMP, Query.Direction.DESCENDING)
                }
                FirestorePaginator(
                    firestore = firestore,
                    baseQuery = baseQuery,
                    _wallpapers = state.wallpapers,
                    _isLoading = state.isLoading,
                    _hasReachedEnd = state.hasReachedEnd
                )
            }
        }
        
        // Cache the paginator
        paginatorCache[key] = paginator
        
        // Load wallpapers with the filter applied at the query level
        paginator?.loadInitialWallpapers()
        
        // Apple category handled with simplified approach - no complex sync needed
        
        Log.d(TAG, "Subcategory filter applied using enhanced FirestorePaginator with caching")
    }

    fun loadMoreWallpapers() {
        viewModelScope.launch {
            val currentSub = _currentSubcategory.value
            Log.d(TAG, "Loading more wallpapers for subcategory: $currentSub")
            
            // Use the specific paginator for the current subcategory
            val key = getSubcategoryKey(currentSub)
            val targetPaginator = paginatorCache[key] ?: paginator
            targetPaginator?.loadMoreWallpapers()
        }
    }
    
    // Load more wallpapers for a specific subcategory (used by HorizontalPager)
    fun loadMoreWallpapersForSubcategory(subcategory: String?) {
        viewModelScope.launch {
            Log.d(TAG, "Loading more wallpapers for specific subcategory: $subcategory")
            val key = getSubcategoryKey(subcategory)
            val targetPaginator = paginatorCache[key]
            if (targetPaginator != null) {
                targetPaginator.loadMoreWallpapers()
            } else {
                // If no cached paginator exists, create one and load
                Log.d(TAG, "No cached paginator for subcategory: $subcategory, creating new one")
                filterBySubcategory(subcategory)
            }
        }
    }

    // MAJOR SIMPLIFICATION: Simplified refresh method
    fun refresh() {
        Log.d(TAG, "Refreshing category: ${_currentCategory.value}")
        
        _currentCategory.value ?: return
        
        // Reset filters and reload
        currentSeries = null
        _currentSubcategory.value = null
        
        // SWIPE "ALL" FIX: Force refresh by clearing state first
        val state = getSubcategoryState(null)
        state.wallpapers.value = emptyList()
        state.hasReachedEnd.value = false
        state.isLoading.value = true
        
        // Force re-creation of paginator for fresh data
        paginatorCache.remove(getSubcategoryKey(null))
        filterBySubcategory(null)
    }
    
    /**
     * MAJOR SIMPLIFICATION: Simplified refresh with subcategory
     */
    fun refreshWithSubcategory(subcategory: String) {
        Log.d(TAG, "Refreshing category with subcategory: $subcategory")
        
        // Reset other filters
        currentSeries = null
                        
        // Apply subcategory filter
        val state = getSubcategoryState(subcategory)
        state.wallpapers.value = emptyList()
        state.hasReachedEnd.value = false
        state.isLoading.value = true
        paginatorCache.remove(subcategory)
        filterBySubcategory(subcategory)
    }
                    
    /**
     * Clear wallpapers immediately - used to prevent wrong wallpapers showing
     */
    fun clearWallpapers() {
        Log.d(TAG, "Clearing wallpapers immediately")
        subcategoryStates.clear()
    }
    
    /**
     * Preload data for a specific subcategory if not already loaded
     * This is used to ensure smooth paging between subcategories
     */
    fun preloadSubcategoryData(subcategory: String?) {
        val categoryName = _currentCategory.value ?: return
        
        // Skip if already loaded or loading
        if (paginatorCache.containsKey(subcategory)) {
            Log.d(TAG, "Subcategory $subcategory already has a paginator, skipping preload")
            return
        }
        
        Log.d(TAG, "Preloading data for subcategory: $subcategory")
        
        // Create a temporary paginator
        val state = getSubcategoryState(subcategory)
        val tempPaginator = when {
            categoryName.equals(AppConfig.COLLECTION_HOME, ignoreCase = true) -> {
                val baseQuery = when {
                    currentSeries != null && currentSeries != "All Series" -> {
                        // If a series is selected, always filter by that series regardless of subcategory
                        firestore.collection(AppConfig.COLLECTION_HOME)
                            .whereEqualTo("series", currentSeries)
                            .orderBy(AppConfig.FIELD_TIMESTAMP, Query.Direction.DESCENDING)
                    }
                    subcategory != null -> {
                        // If no series selected but subcategory is selected, filter by subcategory
                        firestore.collection(AppConfig.COLLECTION_HOME)
                            .whereEqualTo("series", subcategory)
                            .orderBy(AppConfig.FIELD_TIMESTAMP, Query.Direction.DESCENDING)
                    }
                    else -> {
                        // No filters, show all Apple wallpapers
                        firestore.collection(AppConfig.COLLECTION_HOME)
                            .orderBy(AppConfig.FIELD_TIMESTAMP, Query.Direction.DESCENDING)
                    }
                }
                FirestorePaginator(
                    firestore = firestore,
                    baseQuery = baseQuery,
                    _wallpapers = state.wallpapers,
                    _isLoading = state.isLoading,
                    _hasReachedEnd = state.hasReachedEnd
                )
            }
            
            categoryName.equals("Depth Effect", ignoreCase = true) -> {
                val baseQuery = if (subcategory != null) {
                    firestore.collection(AppConfig.COLLECTION_TRENDING)
                        .whereEqualTo("depthEffect", true)
                        .whereEqualTo("subCategory", subcategory)
                } else {
                    firestore.collection(AppConfig.COLLECTION_TRENDING)
                        .whereEqualTo("depthEffect", true)
                }
                FirestorePaginator(
                    firestore = firestore,
                    baseQuery = baseQuery,
                    _wallpapers = state.wallpapers,
                    _isLoading = state.isLoading,
                    _hasReachedEnd = state.hasReachedEnd
                )
            }
            
            else -> {
                val baseQuery = if (subcategory != null) {
                    firestore.collection(AppConfig.COLLECTION_TRENDING)
                        .whereEqualTo("category", categoryName)
                        .whereEqualTo("subCategory", subcategory)
                        .orderBy(AppConfig.FIELD_TIMESTAMP, Query.Direction.DESCENDING)
                } else {
                    firestore.collection(AppConfig.COLLECTION_TRENDING)
                        .whereEqualTo("category", categoryName)
                        .orderBy(AppConfig.FIELD_TIMESTAMP, Query.Direction.DESCENDING)
                }
                FirestorePaginator(
                    firestore = firestore,
                    baseQuery = baseQuery,
                    _wallpapers = state.wallpapers,
                    _isLoading = state.isLoading,
                    _hasReachedEnd = state.hasReachedEnd
                )
            }
        }
        
        // Load initial wallpapers
        tempPaginator.loadInitialWallpapers()
        
        // Cache the paginator
        paginatorCache[getSubcategoryKey(subcategory)] = tempPaginator
        
        Log.d(TAG, "Preloaded and cached paginator for subcategory: $subcategory")
    }
    
    /**
     * Preload adjacent subcategories for smooth paging
     */
    fun preloadAdjacentSubcategories(currentSubcategory: String?) {
        val validSubcategories = _subcategories.value.filter { it != "none" }
        if (validSubcategories.isEmpty()) return
        
        // Create a list with "All" (null) + valid subcategories
        val allOptions = listOf(null) + validSubcategories
        
        // Find the index of the current subcategory
        val currentIndex = allOptions.indexOf(currentSubcategory)
        if (currentIndex == -1) return
        
        // Preload previous subcategory (circular)
        val prevIndex = if (currentIndex > 0) currentIndex - 1 else allOptions.size - 1
        val prevSubcategory = allOptions[prevIndex]
        preloadSubcategoryData(prevSubcategory)
        
        // Preload next subcategory (circular)
        val nextIndex = if (currentIndex < allOptions.size - 1) currentIndex + 1 else 0
        val nextSubcategory = allOptions[nextIndex]
        preloadSubcategoryData(nextSubcategory)
        
        Log.d(TAG, "Preloaded adjacent subcategories: prev=$prevSubcategory, next=$nextSubcategory")
    }
    
    /**
     * Check if data is cached for a subcategory
     */
    fun hasCacheFor(sub: String?): Boolean = getSubcategoryState(sub).wallpapers.value.isNotEmpty()
    
    /**
     * Update wallpapers with favorite status
     */
    private suspend fun updateWallpapersWithFavorites(wallpapers: List<Wallpaper>): List<Wallpaper> {
        val favoriteIds = favoritesManager.getFavorites()
        return wallpapers.map { wallpaper ->
            wallpaper.copy(isFavorite = favoriteIds.contains(wallpaper.id))
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        // Cancel all state synchronization jobs to prevent memory leaks
        stateJobs.values.forEach { job ->
            job.cancel()
        }
        stateJobs.clear()
        subcategoryStates.clear()
        paginatorCache.clear()
        Log.d(TAG, "CategoryViewModel cleared - all jobs cancelled and caches cleared")
    }
    
    data class SubcategoryState(
        val wallpapers: MutableStateFlow<List<Wallpaper>> = MutableStateFlow(emptyList()),
        val isLoading: MutableStateFlow<Boolean> = MutableStateFlow(true), // Start with true for Apple category to prevent flash
        val hasReachedEnd: MutableStateFlow<Boolean> = MutableStateFlow(false)
    )
}
