package com.droidates.wallpapers.core.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.droidates.wallpapers.core.model.Category
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.droidates.wallpapers.core.config.AppConfig
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

@HiltViewModel
class CategoriesViewModel @Inject constructor(
    private val firestore: FirebaseFirestore
) : ViewModel() {
    
    private val _categories = MutableStateFlow<List<Category>>(emptyList())
    val categories = _categories.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    // Track if data has been loaded to prevent duplicate loading
    private var hasInitiallyLoaded = false

    // Track if tab is visible for persistence
    private var isTabVisible = false

    // In-memory cache: categories survive config changes without re-fetching
    private var cachedCategories: List<Category>? = null

    init {
        // Data will be loaded when the tab becomes visible
    }
    
    fun setTabVisibility(visible: Boolean) {
        isTabVisible = visible
        if (visible && !hasInitiallyLoaded) {
            loadInitialDataIfNeeded()
        }
    }
    
    fun loadInitialDataIfNeeded() {
        if (!hasInitiallyLoaded) {
            hasInitiallyLoaded = true

            // Serve from memory cache instantly — no Firestore read needed
            cachedCategories?.let {
                _categories.value = it
                return
            }

            fetchCategories(forceRefresh = false)
        }
    }

    private fun fetchCategories(forceRefresh: Boolean) {
        _isLoading.value = true

        // Two server-side filtered queries instead of downloading all documents and
        // filtering in Kotlin. Reduces reads to only what is displayed.
        val source = if (forceRefresh) Source.SERVER else Source.DEFAULT

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val results = mutableListOf<Category>()

                fun parseDoc(doc: com.google.firebase.firestore.DocumentSnapshot): Category? = try {
                    Category(
                        id = doc.id,
                        name = doc.getString("name") ?: "",
                        thumbnail = doc.getString("thumbnail") ?: "",
                        categoryType = doc.getString("categoryType") ?: "",
                        subcategories = doc.get("subcategories") as? List<String> ?: emptyList()
                    )
                } catch (e: Exception) {
                    Log.e("CategoriesViewModel", "Error converting category doc ${doc.id}", e)
                    null
                }

                // This app's own brand category first (e.g. "Google" for Pixel apps,
                // "Apple" for iPhone apps) — only ever this app's brand, never another's.
                val brandDocs = firestore.collection(AppConfig.COLLECTION_CATEGORIES)
                    .whereEqualTo("categoryType", "brand")
                    .whereEqualTo("name", AppConfig.CATEGORY_BRAND_NAME)
                    .get(source)
                    .await()
                brandDocs.documents.mapNotNullTo(results) { parseDoc(it) }

                // …followed by the shared "main" categories every wallpaper app shows.
                val mainDocs = firestore.collection(AppConfig.COLLECTION_CATEGORIES)
                    .whereEqualTo("categoryType", "main")
                    .get(source)
                    .await()
                mainDocs.documents.mapNotNullTo(results) { parseDoc(it) }

                Log.d("CategoriesViewModel", "Loaded ${results.size} categories via server-side filter")
                cachedCategories = results
                _categories.value = results
            } catch (e: Exception) {
                Log.e("CategoriesViewModel", "Error fetching categories", e)
                // On failure keep any previously cached value so screen isn't blank
                if (_categories.value.isEmpty()) {
                    _categories.value = emptyList()
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    // Function to refresh categories from the network
    fun loadCategories() {
        cachedCategories = null
        fetchCategories(forceRefresh = true)
    }
} 