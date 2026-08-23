package com.droidates.wallpapers.core.data.repository

import android.util.Log
import com.droidates.wallpapers.core.data.local.dao.FavoriteDao
import com.droidates.wallpapers.core.data.local.entity.FavoriteEntity
import com.droidates.wallpapers.core.model.Wallpaper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

import com.droidates.wallpapers.core.config.AppConfig
import com.droidates.wallpapers.core.utils.userDocRef

private const val TAG = "FavoritesRepository"

@Singleton
class FavoritesRepository @Inject constructor(
    private val favoriteDao: FavoriteDao,
    private val firebaseAuth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val authRepository: AuthRepository // Added AuthRepository dependency
) {
    // Flag to track if favorites have already been loaded from Firestore
    private var hasSyncedFromFirestore = false
    
    // Track current user ID to detect user changes
    private var currentSyncedUserId: String? = null
    
    val favorites: Flow<List<Wallpaper>> = favoriteDao.getAllFavorites()
        .map { entities -> entities.map { it.toWallpaper() } }

    suspend fun toggleFavorite(wallpaper: Wallpaper) {
        wallpaper.id.let { id ->
            if (favoriteDao.isFavorite(id)) {
                favoriteDao.deleteFavorite(id)
            } else {
                favoriteDao.insertFavorite(FavoriteEntity.fromWallpaper(wallpaper))
            }
        }
    }

    suspend fun isFavorite(wallpaperId: String): Boolean {
        return favoriteDao.isFavorite(wallpaperId)
    }
    
    /**
     * Load favorites from Firestore when user signs in
     * This will run once per user session
     * @param forceRefresh Force refresh even if already loaded for this user
     * @return true if favorites were loaded, false if they were already loaded or user is not signed in
     */
    suspend fun loadFavoritesFromFirestore(forceRefresh: Boolean = false): Boolean {
        // Check if user is signed in
        val currentUser = firebaseAuth.currentUser ?: run {
            Log.d(TAG, "No signed-in user, skipping loading favorites from Firestore")
            return false
        }
        
        // PREMIUM OPTIMIZATION: Only fetch favorites for premium users
        val isPremium = authRepository.isPremiumUser.value
        if (!isPremium) {
            Log.d(TAG, "User is not premium, skipping Firestore favorites fetch")
            return false
        }
        
        // Check if we've already loaded favorites for this user
        if (hasSyncedFromFirestore && currentUser.uid == currentSyncedUserId && !forceRefresh) {
            Log.d(TAG, "Favorites already loaded from Firestore for user ${currentUser.uid}, skipping")
            return false
        }
        
        try {
            Log.d(TAG, "Loading favorites from Firestore for user ${currentUser.uid}")
            
            // Get the user document
            val userDoc = firestore.userDocRef(currentUser.uid)
                .get()
                .await()
            
            if (!userDoc.exists()) {
                Log.d(TAG, "User document doesn't exist in Firestore")
                
                // Still mark as synced to avoid repeated attempts
                hasSyncedFromFirestore = true
                currentSyncedUserId = currentUser.uid
                return false
            }
            
            // Get the favorites array
            val favoriteIds = userDoc.get("favorites") as? List<String>
            
            if (favoriteIds.isNullOrEmpty()) {
                Log.d(TAG, "No favorites found in Firestore")
                hasSyncedFromFirestore = true
                currentSyncedUserId = currentUser.uid
                return false
            }
            
            Log.d(TAG, "Found ${favoriteIds.size} favorites in Firestore")

            // Batch fetch using whereIn — Firestore whereIn supports up to 30 items per query.
            // Split into chunks of 30 and query each collection once per chunk instead of
            // one document read per wallpaper (N+1 → batch).
            val collections = AppConfig.WALLPAPER_SEARCH_COLLECTIONS
            val loadedWallpapers = mutableListOf<Wallpaper>()
            val foundIds = mutableSetOf<String>()

            for (collection in collections) {
                // Only query for IDs not already found in a previous collection
                val remainingIds = favoriteIds.filter { it !in foundIds }
                if (remainingIds.isEmpty()) break

                // whereIn supports max 30 items — chunk accordingly
                remainingIds.chunked(30).forEach { chunk ->
                    try {
                        val snapshot = firestore.collection(collection)
                            .whereIn(com.google.firebase.firestore.FieldPath.documentId(), chunk)
                            .get()
                            .await()

                        snapshot.documents.forEach { doc ->
                            if (doc.exists()) {
                                val wallpaper = Wallpaper.fromDocument(doc).copy(isFavorite = true)
                                loadedWallpapers.add(wallpaper)
                                foundIds.add(doc.id)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error batch-loading favorites from collection $collection", e)
                    }
                }
            }

            val notFound = favoriteIds.filter { it !in foundIds }
            if (notFound.isNotEmpty()) {
                Log.d(TAG, "Could not find ${notFound.size} wallpaper(s) in any collection")
            }
            
            // Insert all loaded wallpapers into the database
            val entities = loadedWallpapers.map { FavoriteEntity.fromWallpaper(it) }
            
            if (entities.isNotEmpty()) {
                Log.d(TAG, "Inserting ${entities.size} favorites into local database")
                favoriteDao.insertAll(entities)
            }
            
            // Mark as loaded for this user
            hasSyncedFromFirestore = true
            currentSyncedUserId = currentUser.uid
            return true
            
        } catch (e: Exception) {
            Log.e(TAG, "Error loading favorites from Firestore", e)
            return false
        }
    }
    
    /**
     * Get list of favorite wallpaper IDs
     */
    suspend fun getFavoriteIds(): List<String> {
        return favoriteDao.getAllFavoriteIds()
    }
    
    /**
     * Reset the sync state when user signs out or signs in.
     * This ensures that favorites will be loaded from Firestore again when the user signs in.
     * Runs on IO dispatcher to avoid blocking main thread.
     */
    suspend fun resetSyncState() = withContext(Dispatchers.IO) {
        Log.d(TAG, "Resetting favorites sync state")
        hasSyncedFromFirestore = false
        currentSyncedUserId = null
    }

    // Sync favorites to cloud - returns success status
    suspend fun syncToCloud(userId: String): Boolean {
        Log.d(TAG, "Syncing favorites to cloud for user: $userId")
        
        return try {
            // Get local favorites
            val favoriteIds = getFavoriteIds()
            
            // Update Firestore
            val db = FirebaseFirestore.getInstance()
            db.userDocRef(userId)
                .update("favorites", favoriteIds)
                .await()
            
            Log.d(TAG, "Successfully synced ${favoriteIds.size} favorites to cloud")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing favorites to cloud", e)
            false
        }
    }
} 