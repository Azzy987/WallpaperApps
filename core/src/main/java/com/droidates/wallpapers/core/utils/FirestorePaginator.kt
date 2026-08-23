package com.droidates.wallpapers.core.utils

import android.content.Context
import android.util.Log
import androidx.tracing.Trace
import com.droidates.wallpapers.core.model.Wallpaper
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import com.droidates.wallpapers.core.utils.PaginationUtils.PAGE_SIZE

private const val TAG = "FirestorePaginator"

class FirestorePaginator(
    private val firestore: FirebaseFirestore,
    private val baseQuery: Query,
    private val _wallpapers: MutableStateFlow<List<Wallpaper>>,
    private val _isLoading: MutableStateFlow<Boolean>,
    private val _hasReachedEnd: MutableStateFlow<Boolean>
) {
    private val paginatorScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var lastDocument: DocumentSnapshot? = null
    private var hasMoreData = true
    private var consecutiveNullDocumentAttempts = 0
    private val maxNullDocumentAttempts = 3

    /**
     * Guards against duplicate initial reads. Callers are driven by Compose
     * effects that can re-fire before the first query returns; without this the
     * same page is fetched (and billed) twice. Reset in the load's finally block.
     */
    private val isLoadingInitial = java.util.concurrent.atomic.AtomicBoolean(false)

    fun loadInitialWallpapers(silentRefresh: Boolean = false) {
        if (!isLoadingInitial.compareAndSet(false, true)) {
            Log.d(TAG, "Skipping duplicate initial load - one already in flight")
            return
        }

        if (!silentRefresh) {
            _isLoading.value = true
            _wallpapers.value = emptyList()
        }
        lastDocument = null
        hasMoreData = true
        _hasReachedEnd.value = false
        consecutiveNullDocumentAttempts = 0

        Log.d("FirestorePaginator", "Loading initial wallpapers with query: ${baseQuery}")

        val traceCookie = System.identityHashCode(this)
        Trace.beginAsyncSection("firestore_initial_wallpapers", traceCookie)
        paginatorScope.launch {
            try {
                val snapshot = withContext(Dispatchers.IO) {
                    baseQuery.limit(PAGE_SIZE.toLong()).get().await()
                }
                val wallpapers = withContext(Dispatchers.Default) {
                    snapshot.documents.mapNotNull { doc ->
                        try {
                            doc.toWallpaper()
                        } catch (e: Exception) {
                            Log.e("FirestorePaginator", "Error converting document to wallpaper: ${e.message}")
                            null
                        }
                    }
                }

                Log.d("FirestorePaginator", "Loaded ${wallpapers.size} initial wallpapers")

                // Always update the cursor; only replace UI list if not a silent refresh
                val newLastDoc = snapshot.documents.lastOrNull()
                lastDocument = newLastDoc
                hasMoreData = wallpapers.size == PAGE_SIZE
                _hasReachedEnd.value = !hasMoreData
                // Always apply the first page so new Firestore uploads appear after cache/silent refresh.
                _wallpapers.value = wallpapers
                _isLoading.value = false

                Log.d("FirestorePaginator", "Initial load complete. hasMoreData: $hasMoreData, hasReachedEnd: ${_hasReachedEnd.value}, lastDocumentId: ${newLastDoc?.id ?: "null"}")
            } catch (e: Exception) {
                try {
                    Log.e("FirestorePaginator", "Error loading initial wallpapers: ${e.message}")
                    _isLoading.value = false
                } finally {
                    isLoadingInitial.set(false)
                    Trace.endAsyncSection("firestore_initial_wallpapers", traceCookie)
                }
                return@launch
            }
            isLoadingInitial.set(false)
            Trace.endAsyncSection("firestore_initial_wallpapers", traceCookie)
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
        val traceCookie = System.identityHashCode(this) xor currentLastDoc.id.hashCode()
        Trace.beginAsyncSection("firestore_next_wallpapers", traceCookie)
        try {
            val snapshot = withContext(Dispatchers.IO) {
                baseQuery
                    .startAfter(currentLastDoc)
                    .limit(PAGE_SIZE.toLong())
                    .get()
                    .await()
            }
            val newWallpapers = withContext(Dispatchers.Default) {
                snapshot.documents.mapNotNull { doc ->
                    try {
                        doc.toWallpaper()
                    } catch (e: Exception) {
                        Log.e("FirestorePaginator", "Error converting document to wallpaper: ${e.message}")
                        null
                    }
                }
            }

            Log.d("FirestorePaginator", "Loaded ${newWallpapers.size} additional wallpapers")

            _wallpapers.value = _wallpapers.value + newWallpapers
            val newLastDoc = snapshot.documents.lastOrNull()
            lastDocument = newLastDoc
            hasMoreData = newWallpapers.size == PAGE_SIZE
            _hasReachedEnd.value = !hasMoreData
            _isLoading.value = false

            Log.d("FirestorePaginator", "Additional load complete. Total wallpapers: ${_wallpapers.value.size}, hasMoreData: $hasMoreData, hasReachedEnd: ${_hasReachedEnd.value}, lastDocumentId: ${newLastDoc?.id ?: "null"}")
        } catch (e: Exception) {
            Log.e("FirestorePaginator", "Error loading more wallpapers: ${e.message}")
            _isLoading.value = false
        } finally {
            Trace.endAsyncSection("firestore_next_wallpapers", traceCookie)
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
        isLoadingInitial.set(false)
    }
}

// Extension function to convert DocumentSnapshot to Wallpaper
fun DocumentSnapshot.toWallpaper(): Wallpaper {
    return Wallpaper.fromDocument(this)
}
