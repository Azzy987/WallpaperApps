package com.droidates.wallpapers.utils

import android.content.Context
import android.util.Log
import com.droidates.wallpapers.model.Wallpaper
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import com.droidates.wallpapers.utils.PaginationUtils.PAGE_SIZE

private const val TAG = "FirestorePaginator"

class FirestorePaginator(
    private val firestore: FirebaseFirestore,
    private val baseQuery: Query,
    private val _wallpapers: MutableStateFlow<List<Wallpaper>>,
    private val _isLoading: MutableStateFlow<Boolean>,
    private val _hasReachedEnd: MutableStateFlow<Boolean>
) {
    private var lastDocument: DocumentSnapshot? = null
    private var hasMoreData = true
    private var consecutiveNullDocumentAttempts = 0
    private val maxNullDocumentAttempts = 3

    fun loadInitialWallpapers(silentRefresh: Boolean = false) {
        if (!silentRefresh) {
            _isLoading.value = true
            _wallpapers.value = emptyList()
        }
        lastDocument = null
        hasMoreData = true
        _hasReachedEnd.value = false
        consecutiveNullDocumentAttempts = 0

        Log.d("FirestorePaginator", "Loading initial wallpapers with query: ${baseQuery}")

        baseQuery
            .limit(PAGE_SIZE.toLong())
            .get()
            .addOnSuccessListener { snapshot ->
                val wallpapers = snapshot.documents.mapNotNull { doc ->
                    try {
                        doc.toWallpaper()
                    } catch (e: Exception) {
                        Log.e("FirestorePaginator", "Error converting document to wallpaper: ${e.message}")
                        null
                    }
                }

                Log.d("FirestorePaginator", "Loaded ${wallpapers.size} initial wallpapers")

                // Always update the cursor; only replace UI list if not a silent refresh
                val newLastDoc = snapshot.documents.lastOrNull()
                lastDocument = newLastDoc
                hasMoreData = wallpapers.size == PAGE_SIZE
                _hasReachedEnd.value = !hasMoreData
                if (!silentRefresh) {
                    _wallpapers.value = wallpapers
                }
                _isLoading.value = false

                Log.d("FirestorePaginator", "Initial load complete. hasMoreData: $hasMoreData, hasReachedEnd: ${_hasReachedEnd.value}, lastDocumentId: ${newLastDoc?.id ?: "null"}")
            }
            .addOnFailureListener { e ->
                Log.e("FirestorePaginator", "Error loading initial wallpapers: ${e.message}")
                _isLoading.value = false
            }
    }

    suspend fun loadMoreWallpapers() {
        if (_isLoading.value || !hasMoreData) {
            Log.d("FirestorePaginator", "Skipping loadMore - isLoading: ${_isLoading.value}, hasMoreData: $hasMoreData")
            return
        }
        
        _isLoading.value = true
        Log.d("FirestorePaginator", "Loading more wallpapers")

        // Small delay to prevent rapid successive calls
        delay(300)

        val currentLastDoc = lastDocument
        if (currentLastDoc == null) {
            consecutiveNullDocumentAttempts++
            Log.w("FirestorePaginator", "loadMoreWallpapers called but lastDocument is null - attempt $consecutiveNullDocumentAttempts/$maxNullDocumentAttempts")
            
            if (consecutiveNullDocumentAttempts >= maxNullDocumentAttempts) {
                Log.e("FirestorePaginator", "Too many consecutive null document attempts - marking as reached end")
                _hasReachedEnd.value = true
                hasMoreData = false
            }
            
            _isLoading.value = false
            return
        }
        
        // Reset counter on successful pagination attempt
        consecutiveNullDocumentAttempts = 0
        
        Log.d("FirestorePaginator", "Starting pagination from document: ${currentLastDoc.id}")
        baseQuery
            .startAfter(currentLastDoc)
            .limit(PAGE_SIZE.toLong())
            .get()
            .addOnSuccessListener { snapshot ->
                    val newWallpapers = snapshot.documents.mapNotNull { doc ->
                        try {
                            doc.toWallpaper()
                        } catch (e: Exception) {
                            Log.e("FirestorePaginator", "Error converting document to wallpaper: ${e.message}")
                            null
                        }
                    }
                    
                    Log.d("FirestorePaginator", "Loaded ${newWallpapers.size} additional wallpapers")
                    
                    _wallpapers.value += newWallpapers
                    val newLastDoc = snapshot.documents.lastOrNull()
                    lastDocument = newLastDoc
                    hasMoreData = newWallpapers.size == PAGE_SIZE
                    _hasReachedEnd.value = !hasMoreData
                    _isLoading.value = false
                    
                    Log.d("FirestorePaginator", "Additional load complete. Total wallpapers: ${_wallpapers.value.size}, hasMoreData: $hasMoreData, hasReachedEnd: ${_hasReachedEnd.value}, lastDocumentId: ${newLastDoc?.id ?: "null"}")
                }
                .addOnFailureListener { e ->
                    Log.e("FirestorePaginator", "Error loading more wallpapers: ${e.message}")
                    _isLoading.value = false
                }
    }
    
    /**
     * Reset the paginator to initial state - useful when switching filters
     */
    fun reset() {
        Log.d("FirestorePaginator", "Resetting paginator state")
        lastDocument = null
        hasMoreData = true
        _hasReachedEnd.value = false
        _isLoading.value = false
        _wallpapers.value = emptyList()
        consecutiveNullDocumentAttempts = 0
    }
}

// Extension function to convert DocumentSnapshot to Wallpaper
fun DocumentSnapshot.toWallpaper(): Wallpaper {
    return Wallpaper.fromDocument(this)
}