package com.droidates.wallpapers.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.droidates.wallpapers.navigation.NavigationState
import com.droidates.wallpapers.viewmodel.EditWallpaperViewModel
import com.droidates.wallpapers.viewmodel.WallpaperType
import kotlinx.coroutines.delay
import com.droidates.wallpapers.ui.components.EditOption
import com.droidates.wallpapers.ui.components.ImageFilter
import com.droidates.wallpapers.ui.components.SetWallpaperBottomSheet
import com.droidates.wallpapers.ui.components.UnlockDialog
import com.droidates.wallpapers.ui.components.WallpaperSetOption
import com.droidates.wallpapers.ui.components.edit.ColorMatrixManager
import android.app.Activity
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import com.droidates.wallpapers.data.preferences.LocalUserPreferences
import com.droidates.wallpapers.viewmodel.DetailViewModel
import com.droidates.wallpapers.utils.LocalAdManager
import androidx.activity.compose.BackHandler
import com.droidates.wallpapers.ui.components.edit.EditWallpaperTopBar
import com.droidates.wallpapers.ui.components.edit.EditWallpaperBottomBar
import com.droidates.wallpapers.ui.components.edit.EditWallpaperLoadingIndicator
import com.droidates.wallpapers.ui.components.edit.EditWallpaperImage
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditWallpaperScreen(
    wallpaperId: String,
    navigationState: NavigationState,
    viewModel: EditWallpaperViewModel = hiltViewModel(),
    detailViewModel: DetailViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val adManager = LocalAdManager.current
    
    val wallpaper by viewModel.wallpaper.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val successMessage by viewModel.successMessage.collectAsState()
    val scope = rememberCoroutineScope()
    
    // Get premium status from AdManager
    val isPremiumUser by adManager.isPremiumUser.collectAsState()
    
    // Check if this wallpaper is already unlocked in DetailViewModel or UserPreferences
    var hasWatchedAd by remember { mutableStateOf(false) }
    
    // Check if this wallpaper is already unlocked in DetailViewModel or UserPreferences
    LaunchedEffect(wallpaperId, wallpaper, isPremiumUser) {
        // Debug the wallpaper state
        Log.d("EditWallpaperScreen", "Wallpaper ID: $wallpaperId, isPremiumUser: $isPremiumUser, exclusive: ${wallpaper?.exclusive}")
        
        // Premium users always have access to exclusive content, regardless of whether it's exclusive or not
        if (isPremiumUser) {
            hasWatchedAd = true
            Log.d("EditWallpaperScreen", "User is premium, all filters unlocked")
        } else if (wallpaper?.exclusive == true) {
            // For non-premium users with exclusive wallpaper, check if unlocked
            hasWatchedAd = detailViewModel.isWallpaperUnlocked(wallpaperId)
            Log.d("EditWallpaperScreen", "Non-premium user with exclusive wallpaper, filters unlocked based on hasWatchedAd: $hasWatchedAd")
        } else {
            // For non-exclusive wallpapers, check if edit features unlocked in this session
            hasWatchedAd = EditWallpaperViewModel.isEditFeaturesUnlocked(wallpaperId)
            Log.d("EditWallpaperScreen", "Non-premium user with non-exclusive wallpaper, edit features unlocked: $hasWatchedAd")
        }
    }
    
    // Image editing states
    var brightness by remember { mutableFloatStateOf(0f) }  // Start at 0%
    var contrast by remember { mutableFloatStateOf(0f) }    // Start at 0%
    var saturation by remember { mutableFloatStateOf(0f) }  // Start at 0%
    var hue by remember { mutableFloatStateOf(0f) }         // Start at 0 degrees
    var opacity by remember { mutableFloatStateOf(1f) }     // Start at 100%
    var selectedFilter by remember { mutableStateOf<ImageFilter?>(null) }
    var selectedEditOption by remember { mutableStateOf(EditOption.FILTER) }
    
    // State for flipping
    var isFlippedHorizontally by remember { mutableStateOf(false) }
    
    // States for premium features
    var showUnlockDialog by remember { mutableStateOf(false) }
    var isLoadingRewardAd by remember { mutableStateOf(false) }
    
    // List of unlocked filters - Now we'll mark the first 5 as unlocked
    val unlockedFilters = remember {
        listOf(
            ImageFilter.GRAYSCALE,
            ImageFilter.SEPIA, 
            ImageFilter.VINTAGE, 
            ImageFilter.COOL,
            ImageFilter.WARM
        )
    }
    
    // Function to check if filter is locked (for proper typing)
    val isFilterLocked = { filter: ImageFilter? ->
        val result = if (filter == null) {
            Log.d("EditWallpaperScreen", "Filter is null (None), so it's unlocked")
            // "None" filter is always unlocked
            false
        } else if (isPremiumUser) {
            Log.d("EditWallpaperScreen", "User is premium, so filter '${filter.displayName}' is unlocked")
            // Premium users see no locks
            false
        } else if (hasWatchedAd) {
            Log.d("EditWallpaperScreen", "User has watched ad, so filter '${filter.displayName}' is unlocked for this session")
            // Users who watched an ad see no locks in this session
            false
        } else if (unlockedFilters.contains(filter)) {
            Log.d("EditWallpaperScreen", "Filter '${filter.displayName}' is in unlocked list")
            // Filter is in the list of unlocked filters
            false
        } else {
            Log.d("EditWallpaperScreen", "Filter '${filter.displayName}' is locked")
            // Filter should be locked
            true
        }
        
        Log.d("EditWallpaperScreen", "Final lock status for ${filter?.displayName ?: "None"}: $result")
        result
    }
    
    // Function to check if editing option is locked
    val isEditOptionLocked = { option: EditOption ->
        val result = if (isPremiumUser) {
            Log.d("EditWallpaperScreen", "User is premium, so edit option '${option.description}' is unlocked")
            // Premium users see no locks
            false
        } else if (hasWatchedAd) {
            Log.d("EditWallpaperScreen", "User has watched ad, so edit option '${option.description}' is unlocked for this session")
            // Users who watched an ad see no locks in this session
            false
        } else if (option == EditOption.FILTER) {
            Log.d("EditWallpaperScreen", "Edit option '${option.description}' is always unlocked")
            // Only filter is always unlocked (brightness is now locked too)
            false
        } else {
            Log.d("EditWallpaperScreen", "Edit option '${option.description}' is locked")
            // Option should be locked
            true
        }
        
        Log.d("EditWallpaperScreen", "Final lock status for edit option ${option.description}: $result")
        result
    }
    
    // Function to check if should show unlock dialog
    val shouldShowUnlockDialog = {
        // Only show unlock dialog for non-premium users who haven't watched an ad
        !isPremiumUser && !hasWatchedAd
    }
    
    // Local state for edited values
    var wallpaperName by remember { mutableStateOf("") }
    var isExclusive by remember { mutableStateOf(false) }
    var categories by remember { mutableStateOf("") }
    
    // Set wallpaper sheet state
    var showSetWallpaperSheet by remember { mutableStateOf(false) }
    
    // Add colorMatrix variable after other state variables
    var colorMatrix by remember { mutableStateOf(androidx.compose.ui.graphics.ColorMatrix()) }
    
    // Load reward ad when screen opens - simplified and non-blocking
    LaunchedEffect(Unit) {
        // Load ad in background without blocking UI
        scope.launch {
            try {
                adManager.loadRewardAd {
                    isLoadingRewardAd = false
                }
            } catch (e: Exception) {
                // Ignore ad loading errors to prevent screen delays
                isLoadingRewardAd = false
            }
        }
        
        // Minimal logging for performance
        Log.d("EditWallpaperScreen", "EditWallpaperScreen opened")
    }
    
    // Update local state when wallpaper is loaded
    LaunchedEffect(wallpaper) {
        wallpaper?.let {
            wallpaperName = it.wallpaperName
            isExclusive = it.exclusive
            categories = it.category
        }
    }
    
    // Update ViewModel when editing parameters change - debounced for performance
    LaunchedEffect(brightness, contrast, saturation, hue, opacity, selectedFilter) {
        // Debounce color matrix updates to avoid excessive calculations
        delay(50) // 50ms delay to batch rapid changes
        
        scope.launch(Dispatchers.Default) {
            try {
                // Create a new color matrix using ColorMatrixManager
                val newColorMatrix = ColorMatrixManager.createColorMatrix(
                    brightness = brightness,
                    contrast = contrast,
                    saturation = saturation,
                    hue = hue,
                    opacity = opacity,
                    selectedFilter = selectedFilter
                )
                
                // Update the colorMatrix state and sync with ViewModel
                colorMatrix = newColorMatrix
                viewModel.updateColorMatrix(newColorMatrix.values)
            } catch (e: Exception) {
                Log.e("EditWallpaperScreen", "Error updating color matrix: ${e.message}")
            }
        }
    }
    
    // Handle messages (only show navigation success)
    LaunchedEffect(successMessage) {
        successMessage?.let { message ->
            // Only navigate back on success, don't show additional toast
            delay(500) // Small delay for smooth transition
            navigationState.navigateBack()
        }
    }
    
    // Clear messages after showing
    LaunchedEffect(errorMessage, successMessage) {
        if (errorMessage != null || successMessage != null) {
            delay(2000)
            viewModel.clearMessages()
        }
    }
    
    // Handle back properly to ensure dialogs don't block navigation
    BackHandler {
        if (showUnlockDialog) {
            // If unlock dialog is showing, close it first
            showUnlockDialog = false
        } else if (showSetWallpaperSheet) {
            // If set wallpaper sheet is showing, close it first
            showSetWallpaperSheet = false
        } else {
            // Otherwise, proceed with normal back navigation
            navigationState.navigateBack()
        }
    }
    
    Scaffold(
        topBar = {
            EditWallpaperTopBar(
                onBackClick = {
                    navigationState.navigateBack()
                },
                onDownloadClick = {
                    viewModel.downloadEditedWallpaper(context)
                }
            )
        },
        bottomBar = {
            EditWallpaperBottomBar(
                selectedOption = selectedEditOption,
                onOptionSelected = { option ->
                    // Check if the option is locked and show dialog only when user explicitly selects it
                    if (isEditOptionLocked(option) && !isPremiumUser && !hasWatchedAd) {
                        showUnlockDialog = true
                        Log.d("EditWallpaperScreen", "Showing unlock dialog for locked edit option: ${option.description}")
                        // Don't update selectedEditOption if it's locked
                    } else {
                        // Handle special options directly
                        when (option) {
                            EditOption.FLIP -> {
                                // Toggle horizontal flip
                                isFlippedHorizontally = !isFlippedHorizontally
                                // Keep FLIP selected to show it's active
                                selectedEditOption = EditOption.FLIP
                            }
                            else -> {
                                // For regular options, just update selection
                                selectedEditOption = option
                            }
                        }
                    }
                },
                onResetClick = {
                    brightness = 0f
                    contrast = 0f
                    saturation = 0f
                    hue = 0f
                    opacity = 1f
                    selectedFilter = null
                    isFlippedHorizontally = false
                    selectedEditOption = EditOption.FILTER
                },
                onApplyClick = {
                    showSetWallpaperSheet = true
                },
                brightness = brightness,
                onBrightnessChange = { brightness = it },
                contrast = contrast,
                onContrastChange = { contrast = it },
                saturation = saturation,
                onSaturationChange = { saturation = it },
                hue = hue,
                onHueChange = { hue = it },
                opacity = opacity,
                onOpacityChange = { opacity = it },
                isEditOptionLocked = isEditOptionLocked,
                selectedFilter = selectedFilter,
                onFilterSelected = { filter ->
                    // Check if filter is locked before applying it
                    if (filter != null && isFilterLocked(filter) && !isPremiumUser && !hasWatchedAd) {
                        showUnlockDialog = true
                        Log.d("EditWallpaperScreen", "Showing unlock dialog for locked filter: ${filter.displayName}")
                        // Don't update selectedFilter if it's locked
                    } else {
                        // Filter is unlocked or user has premium/watched ad, apply it
                        selectedFilter = filter
                    }
                },
                isFilterLocked = isFilterLocked,
                wallpaperImageUrl = wallpaper?.imageUrl ?: "",
                wallpaperId = wallpaperId // Pass wallpaper ID for cache consistency
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Wallpaper preview with applied effects
            EditWallpaperImage(
                imageUrl = wallpaper?.imageUrl ?: "",
                wallpaperId = wallpaperId, // Pass wallpaper ID for cache consistency
                brightness = brightness,
                contrast = contrast,
                saturation = saturation,
                hue = hue,
                opacity = opacity,
                selectedFilter = selectedFilter,
                isFlippedHorizontally = isFlippedHorizontally,
                modifier = Modifier.fillMaxSize()
            )
            
            if (isLoading || isSaving) {
                EditWallpaperLoadingIndicator()
            }

            // Set wallpaper bottom sheet
            if (showSetWallpaperSheet) {
                SetWallpaperBottomSheet(
                    onDismiss = { showSetWallpaperSheet = false },
                    onOptionSelected = { option ->
                        showSetWallpaperSheet = false
                        when (option) {
                            WallpaperSetOption.HOME_SCREEN -> viewModel.setEditedWallpaper(WallpaperType.HOME)
                            WallpaperSetOption.LOCK_SCREEN -> viewModel.setEditedWallpaper(WallpaperType.LOCK)
                            WallpaperSetOption.BOTH_SCREENS -> viewModel.setEditedWallpaper(WallpaperType.BOTH)
                            else -> {} // Handle any other options (shouldn't occur as we hide EXTERNAL)
                        }
                    },
                    isSettingWallpaper = isSaving,
                    progress = 0f,
                    showExternalOption = false // Hide external option in edit screen
                )
            }

            if (showUnlockDialog) {
                UnlockDialog(
                    onDismiss = {
                        showUnlockDialog = false
                        // Reset to Filter option if they dismiss dialog
                        selectedEditOption = EditOption.FILTER
                    },
                    onWatchAd = {
                        // Start loading the ad
                        isLoadingRewardAd = true
                        
                        // Set a timeout for ad loading
                        scope.launch {
                            try {
                                delay(15000) // 15 second timeout
                                if (isLoadingRewardAd) {
                                    isLoadingRewardAd = false
                                    Toast.makeText(
                                        context,
                                        "Failed to load ad. Please try again later.",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            } catch (e: Exception) {
                                // Ignore cancellation exceptions
                            }
                        }
                        
                        adManager.loadRewardAd(
                            onAdLoaded = {
                                isLoadingRewardAd = false
                                adManager.showRewardAd(
                                    activity = context as Activity,
                                    onRewarded = {
                                        Log.d("EditWallpaperScreen", "User rewarded")
                                        hasWatchedAd = true
                                        
                                        // Mark this wallpaper's edit features as unlocked using the companion object
                                        viewModel.markCurrentWallpaperEditFeaturesUnlocked()
                                        
                                        // Also mark the dialog shown using the ViewModel
                                        viewModel.markUnlockDialogShown()
                                        
                                        // Dismiss the dialog after successful reward
                                        showUnlockDialog = false
                                    },
                                    onAdDismissed = {
                                        Log.d("EditWallpaperScreen", "Rewarded ad closed")
                                        // Make sure dialog is dismissed when ad is closed
                                        showUnlockDialog = false
                                    }
                                )
                            }
                        )
                    },
                    onUpgrade = {
                        showUnlockDialog = false
                        navigationState.navigateToPremium()
                    },
                    isLoading = isLoadingRewardAd,
                    loadingText = "Loading Ad...",
                    title = "Unlock Full Editor",
                    message = "Watch an ad to unlock all filters and editing features for this session."
                )
            }
        }
    }
}


