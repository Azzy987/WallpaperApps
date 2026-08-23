package com.droidates.wallpapers.viewmodel

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.droidates.wallpapers.data.preferences.ThemePreferences
import com.droidates.wallpapers.ui.theme.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import android.util.Log
import coil.Coil
import com.droidates.wallpapers.BuildConfig
import com.droidates.wallpapers.data.repository.AuthRepository
import coil.annotation.ExperimentalCoilApi
import com.droidates.wallpapers.config.AppConfig
import com.droidates.wallpapers.utils.userDocRef
import com.droidates.wallpapers.utils.PolicyContent
import com.google.firebase.firestore.FirebaseFirestore
import java.util.UUID
import java.util.Date

@OptIn(ExperimentalCoilApi::class)
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val themePreferences: ThemePreferences,
    private val authRepository: AuthRepository,
    private val favoritesRepository: com.droidates.wallpapers.data.repository.FavoritesRepository
) : ViewModel() {
    
    val themeMode = themePreferences.themeMode
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ThemeMode.SYSTEM
        )

    // Add MutableStateFlow for cache size
    private val _cacheSize = MutableStateFlow("0 B")
    val cacheSize = _cacheSize.asStateFlow()
    
    // Add MutableStateFlow for showing dialogs
    private val _showPrivacyPolicyDialog = MutableStateFlow(false)
    val showPrivacyPolicyDialog = _showPrivacyPolicyDialog.asStateFlow()
    
    private val _showTermsOfUseDialog = MutableStateFlow(false)
    val showTermsOfUseDialog = _showTermsOfUseDialog.asStateFlow()
    
    private val _showLicensesDialog = MutableStateFlow(false)
    val showLicensesDialog = _showLicensesDialog.asStateFlow()
    
    private val _showChangelogDialog = MutableStateFlow(false)
    val showChangelogDialog = _showChangelogDialog.asStateFlow()
    
    private val _showFeatureRequestDialog = MutableStateFlow(false)
    val showFeatureRequestDialog = _showFeatureRequestDialog.asStateFlow()
    
    private val _isSubmittingFeatureRequest = MutableStateFlow(false)
    val isSubmittingFeatureRequest = _isSubmittingFeatureRequest.asStateFlow()
    
    // App version info
    val appVersion = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
    
    // Calculate cache size and update _cacheSize
    fun updateCacheSize(context: Context) {
        calculateCacheSize(context)
    }
    
    // Calculate cache size in a readable format
    private fun calculateCacheSize(context: Context) {
        viewModelScope.launch {
            try {
                val imageLoader = Coil.imageLoader(context)
                val diskCacheSize = imageLoader.diskCache?.size ?: 0
                val memoryCacheSize = imageLoader.memoryCache?.size ?: 0
                
                val totalSize = diskCacheSize + memoryCacheSize
                _cacheSize.value = formatSize(totalSize)
            } catch (e: Exception) {
                Log.e("SettingsViewModel", "Error calculating cache size", e)
                _cacheSize.value = "Unknown"
            }
        }
    }
    
    // Format size in bytes to a readable format
    private fun formatSize(size: Long): String {
        if (size <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB")
        val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
        return "%.1f %s".format(size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            themePreferences.setThemeMode(mode)
        }
    }

    fun clearCache(context: Context) {
        viewModelScope.launch {
            try {
                // Show toast before clearing
                android.widget.Toast.makeText(context, "Clearing cache...", android.widget.Toast.LENGTH_SHORT).show()
                
                // Clear Coil image cache
                val imageLoader = Coil.imageLoader(context)
                imageLoader.memoryCache?.clear()
                imageLoader.diskCache?.clear()
                
                // Clear disk cache
                val cacheDir = context.cacheDir
                if (cacheDir.exists()) {
                    cacheDir.deleteRecursively()
                }
                
                // Clear memory cache
                System.gc()
                
                // Update cache size
                calculateCacheSize(context)
                
                // Show toast message
                android.widget.Toast.makeText(context, "Cache cleared successfully", android.widget.Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Log.e("SettingsViewModel", "Error clearing cache", e)
                android.widget.Toast.makeText(context, "Failed to clear cache", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun syncFavorites(context: Context) {
        viewModelScope.launch {
            try {
                // Show toast before syncing
                android.widget.Toast.makeText(context, "Syncing favorites...", android.widget.Toast.LENGTH_SHORT).show()
                
                // Get user ID
                val userId = authRepository.getCurrentUser()?.id
                
                if (userId.isNullOrEmpty()) {
                    android.widget.Toast.makeText(context, "You need to be signed in to sync favorites", android.widget.Toast.LENGTH_LONG).show()
                    return@launch
                }
                
                // Get local favorites
                val favoriteIds = favoritesRepository.getFavoriteIds()
                
                // Update Firestore using nested collection structure
                val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                db.userDocRef(userId)
                    .update("favorites", favoriteIds)
                    .await()
                
                // Show success toast
                android.widget.Toast.makeText(context, "Favorites synced successfully", android.widget.Toast.LENGTH_SHORT).show()
                Log.d("SettingsViewModel", "Favorites synced: ${favoriteIds.size} items")
                
            } catch (e: Exception) {
                Log.e("SettingsViewModel", "Error syncing favorites", e)
                android.widget.Toast.makeText(context, "Failed to sync favorites: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    // New method to sync favorites using the repository directly
    fun syncFavoritesViaRepository(context: Context) {
        viewModelScope.launch {
            try {
                // Get user ID first
                val userId = authRepository.getCurrentUser()?.id
                
                if (userId.isNullOrEmpty()) {
                    android.widget.Toast.makeText(context, "You need to be signed in to sync favorites", android.widget.Toast.LENGTH_LONG).show()
                    return@launch
                }
                
                // Check premium status before syncing
                val isPremium = authRepository.isPremiumUser.first()
                
                if (!isPremium) {
                    android.widget.Toast.makeText(context, "Premium subscription required to sync favorites", android.widget.Toast.LENGTH_LONG).show()
                    return@launch
                }
                
                // Show toast before syncing
                android.widget.Toast.makeText(context, "Syncing favorites...", android.widget.Toast.LENGTH_SHORT).show()
                
                // Use the repository's method to sync to cloud
                val success = favoritesRepository.syncToCloud(userId)
                
                if (success) {
                    // Show success toast
                    android.widget.Toast.makeText(context, "Favorites synced successfully", android.widget.Toast.LENGTH_SHORT).show()
                } else {
                    android.widget.Toast.makeText(context, "Error syncing favorites", android.widget.Toast.LENGTH_LONG).show()
                }
                
            } catch (e: Exception) {
                Log.e("SettingsViewModel", "Error syncing favorites", e)
                android.widget.Toast.makeText(context, "Failed to sync favorites: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    fun openPlayStore(context: Context) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("market://details?id=${context.packageName}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e("SettingsViewModel", "Error opening Play Store", e)
        }
    }

    fun shareApp(context: Context) {
        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, AppConfig.SHARE_APP_SUBJECT)
                putExtra(Intent.EXTRA_TEXT, AppConfig.SHARE_APP_TEXT_PREFIX + "https://play.google.com/store/apps/details?id=${context.packageName}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(Intent.createChooser(intent, "Share App"))
        } catch (e: Exception) {
            Log.e("SettingsViewModel", "Error sharing app", e)
        }
    }

    fun openDeveloperPage(context: Context) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse(AppConfig.URL_DEVELOPER_PAGE)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e("SettingsViewModel", "Error opening developer page", e)
        }
    }

    // Methods to toggle dialog visibility
    fun showPrivacyPolicy() {
        _showPrivacyPolicyDialog.value = true
    }
    
    fun dismissPrivacyPolicy() {
        _showPrivacyPolicyDialog.value = false
    }
    
    fun showTermsOfUse() {
        _showTermsOfUseDialog.value = true
    }
    
    fun dismissTermsOfUse() {
        _showTermsOfUseDialog.value = false
    }
    
    fun showLicenses() {
        _showLicensesDialog.value = true
    }
    
    fun dismissLicenses() {
        _showLicensesDialog.value = false
    }
    
    fun showChangelog() {
        _showChangelogDialog.value = true
    }
    
    fun dismissChangelog() {
        _showChangelogDialog.value = false
    }
    
    fun showFeatureRequest() {
        _showFeatureRequestDialog.value = true
    }
    
    fun dismissFeatureRequest() {
        _showFeatureRequestDialog.value = false
    }
    
    // Submit feature request to Firestore
    fun submitFeatureRequest(title: String, description: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        if (title.isBlank() || description.isBlank()) {
            onError("Please fill in all fields")
            return
        }
        
        _isSubmittingFeatureRequest.value = true
        
        viewModelScope.launch {
            try {
                val db = FirebaseFirestore.getInstance()
                val requestId = UUID.randomUUID().toString()
                
                val user = authRepository.getCurrentUser()
                val userId = user?.id ?: "anonymous"
                val userEmail = user?.email ?: "anonymous"
                
                val requestData = hashMapOf(
                    "id" to requestId,
                    "title" to title,
                    "description" to description,
                    "userId" to userId,
                    "userEmail" to userEmail,
                    "timestamp" to Date(),
                    "deviceInfo" to getDeviceInfo(),
                    "appVersion" to appVersion,
                    "status" to "new"
                )
                
                // Add to RequestedUpdates collection
                db.collection(AppConfig.COLLECTION_FEATURE_REQUESTS)
                    .document(requestId)
                    .set(requestData)
                    .await()
                
                _isSubmittingFeatureRequest.value = false
                onSuccess()
                dismissFeatureRequest()
                
            } catch (e: Exception) {
                Log.e("SettingsViewModel", "Error submitting feature request", e)
                _isSubmittingFeatureRequest.value = false
                onError(e.message ?: "Unknown error")
            }
        }
    }
    
    // Legacy methods for external URLs - kept for backward compatibility
    fun openPrivacyPolicy(context: Context) {
        showPrivacyPolicy()
    }

    fun openTermsOfUse(context: Context) {
        showTermsOfUse()
    }
    
    // Method to get device info for bug reports
    fun getDeviceInfo(): String {
        return PolicyContent.getDeviceInfo() + "\nApp version: $appVersion"
    }
} 