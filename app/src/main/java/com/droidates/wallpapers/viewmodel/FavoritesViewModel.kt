package com.droidates.wallpapers.viewmodel

import android.content.Context
import android.util.Log
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Star
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.droidates.wallpapers.data.repository.FavoritesRepository
import com.droidates.wallpapers.model.Wallpaper
import com.droidates.wallpapers.ui.components.ToastManager
import com.droidates.wallpapers.utils.ContextProvider
import com.droidates.wallpapers.utils.AdManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.droidates.wallpapers.config.AppConfig
import com.droidates.wallpapers.utils.userDocRef
import javax.inject.Inject

private const val TAG = "FavoritesViewModel"

@HiltViewModel
class FavoritesViewModel @Inject constructor(
    private val favoritesRepository: FavoritesRepository,
    private val contextProvider: ContextProvider,
    private val toastManager: ToastManager,
    private val firebaseAuth: FirebaseAuth,
    private val adManager: AdManager
) : ViewModel() {

    private val context: Context
        get() = contextProvider.getContext()

    private val _favoriteWallpapers = MutableStateFlow<List<Wallpaper>>(emptyList())
    val favoriteWallpapers = _favoriteWallpapers.asStateFlow()

    private val _favoriteIds = MutableStateFlow<Set<String>>(emptySet())
    val favoriteIds = _favoriteIds.asStateFlow()

    // Syncing state for UI indication
    private val _isSyncing = MutableStateFlow(false)
    val isSyncing = _isSyncing.asStateFlow()

    val favoriteIdsFromWallpapers = favoriteWallpapers.map { wallpapers ->
        wallpapers.mapNotNull { it.id }.toSet()
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptySet()
    )

    // Add state for premium prompt dialog
    private val _showPremiumPrompt = MutableStateFlow(false)
    val showPremiumPrompt = _showPremiumPrompt.asStateFlow()
    
    // Add loading state for initial load
    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    init {
        viewModelScope.launch {
            favoritesRepository.favorites.collect { favorites ->
                _favoriteWallpapers.value = favorites
                _favoriteIds.value = favorites.mapNotNull { it.id }.toSet()
            }
        }
    }
    
    /**
     * Load favorites from Firestore when the FavoritesTabScreen is shown.
     * This ensures favorites are loaded when a user signs in on a new device.
     */
    fun loadFavoritesFromFirestore() {
        val currentUser = firebaseAuth.currentUser ?: run {
            Log.d(TAG, "No signed-in user, skipping loading favorites")
            return
        }
        
        // PREMIUM OPTIMIZATION: Only load favorites from Firestore for premium users
        val isPremium = adManager.isPremiumUser.value
        if (!isPremium) {
            Log.d(TAG, "User is not premium, skipping Firestore favorites sync")
            return
        }
        
        _isLoading.value = true
        
        viewModelScope.launch {
            try {
                Log.d(TAG, "Loading favorites from Firestore for user: ${currentUser.uid}")
                val success = favoritesRepository.loadFavoritesFromFirestore(forceRefresh = true)
                
                if (success) {
                    // Show a subtle toast indicating favorites were loaded from the cloud
                    toastManager.showToast(
                        message = "Favorites synced from cloud",
                        icon = Icons.Default.Sync
                    )
                    Log.d(TAG, "Successfully loaded favorites from Firestore")
                } else {
                    Log.d(TAG, "No favorites loaded from Firestore")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading favorites from Firestore: ${e.message}", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun toggleFavorite(wallpaper: Wallpaper) {
        viewModelScope.launch {
            val wallpaperId = wallpaper.id ?: ""
            val wasFavorite = favoritesRepository.isFavorite(wallpaperId)
            
            if (wasFavorite) {
                _favoriteIds.value = _favoriteIds.value - wallpaperId
            } else {
                _favoriteIds.value = _favoriteIds.value + wallpaperId
            }
            
            // Then update the database
            favoritesRepository.toggleFavorite(wallpaper)
            
            if (!wasFavorite) {
                toastManager.showToast(
                    message = "Added to favorites",
                    icon = Icons.Default.Favorite
                )
                
                // Don't auto-sync to cloud anymore - let user explicitly sync when needed
            } else {
                toastManager.showToast(
                    message = "Removed from favorites",
                    icon = Icons.Default.FavoriteBorder
                )
            }
        }
    }
    
    fun syncFavoritesToCloud() {
        val currentUser = firebaseAuth.currentUser ?: run {
            toastManager.showToast(
                message = "Please sign in to sync favorites",
                icon = Icons.Default.Error
            )
            return
        }
        
        val favorites = _favoriteWallpapers.value
        if (favorites.isEmpty()) {
            toastManager.showToast(
                message = "No favorites to sync",
                icon = Icons.Default.Sync
            )
            return
        }
        
        // Check if user is premium before allowing sync
        isPremiumCheck { isPremium ->
            if (!isPremium) {
                // User is not premium, show toast and return
                toastManager.showToast(
                    message = "Premium subscription required to sync favorites",
                    icon = Icons.Default.Star
                )
                
                // Trigger premium prompt dialog
                _showPremiumPrompt.value = true
                return@isPremiumCheck
            }
            
            // User is premium, proceed with sync
            _isSyncing.value = true
            toastManager.showToast(
                message = "Syncing favorites...",
                icon = Icons.Default.Sync
            )
            
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val db = FirebaseFirestore.getInstance()
                    val userDoc = db.userDocRef(currentUser.uid)
                    
                    // Using an array field approach for better performance and scalability
                    val favoriteIds = favorites.mapNotNull { it.id }
                    
                    // Update the user document with the array of favorite IDs
                    userDoc.update("favorites", favoriteIds).await()
                    Log.d(TAG, "Synced ${favoriteIds.size} favorites to cloud")
                    
                    withContext(Dispatchers.Main) {
                        toastManager.showToast(
                            message = "Favorites synced successfully",
                            icon = Icons.Default.Sync
                        )
                        _isSyncing.value = false
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error syncing favorites: $e")
                    withContext(Dispatchers.Main) {
                        toastManager.showToast(
                            message = "Error syncing favorites: ${e.message}",
                            icon = Icons.Default.Error
                        )
                        _isSyncing.value = false
                    }
                }
            }
        }
    }
    
    // Reset premium prompt
    fun resetPremiumPrompt() {
        _showPremiumPrompt.value = false
    }
    
    // Helper method to check premium status
    private fun isPremiumCheck(callback: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                // Use first() to get the current value from the StateFlow
                val isPremium = adManager.isPremiumUser.first()
                callback(isPremium)
            } catch (e: Exception) {
                Log.e(TAG, "Error checking premium status: $e")
                callback(false) // Assume not premium on error
            }
        }
    }
    
    // This method is no longer used as we don't auto-sync individual wallpapers
    private fun syncWallpaperToCloud(wallpaper: Wallpaper) {
        // Method kept but not used, to avoid breaking existing code references
        Log.d(TAG, "Auto-sync disabled, use explicit sync instead")
    }

    private fun updateFavoritesInFirestore(favorites: List<String>) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        
        viewModelScope.launch {
            try {
                // We don't need to store the count separately, it can be calculated from the array
                FirebaseFirestore.getInstance()
                    .userDocRef(userId)
                    .update(
                        mapOf(
                            "favorites" to favorites
                        )
                    )
                    .await()
                
                Log.d("FavoritesViewModel", "Favorites updated in Firestore: ${favorites.size} items")
            } catch (e: Exception) {
                Log.e("FavoritesViewModel", "Error updating favorites in Firestore", e)
            }
        }
    }
} 