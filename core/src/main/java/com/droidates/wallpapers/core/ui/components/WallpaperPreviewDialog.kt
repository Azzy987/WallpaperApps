package com.droidates.wallpapers.core.ui.components

import androidx.compose.animation.core.*
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import com.droidates.wallpapers.core.ui.theme.Material3Motion
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import android.view.HapticFeedbackConstants
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.painterResource
import android.widget.Toast
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.droidates.wallpapers.core.model.Wallpaper
import com.droidates.wallpapers.core.viewmodel.FavoritesViewModel
import com.droidates.wallpapers.core.utils.AdManager
import com.droidates.wallpapers.core.R

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WallpaperPreviewDialog(
    wallpaper: Wallpaper,
    onDismiss: () -> Unit,
    onApplyWallpaper: () -> Unit,
    onShareWallpaper: () -> Unit,
    favoritesViewModel: FavoritesViewModel,
    adManager: AdManager?,
    navigationState: com.droidates.wallpapers.core.navigation.NavigationState? = null,
    sourceScreen: String = "home",
    categoryName: String? = null,
    sortOption: String = "LATEST"
) {
    // State for progress indicators and bottom sheet
    var isSharing by remember { mutableStateOf(false) }
    var isApplying by remember { mutableStateOf(false) }
    var showSetWallpaperSheet by remember { mutableStateOf(false) }
    
    // Animation states for favorite button
    var favoriteScale by remember { mutableStateOf(1f) }
    var triggerFavoriteAnimation by remember { mutableStateOf(false) }
    
    val view = LocalView.current
    
    // Reset progress indicators after a delay
    LaunchedEffect(isSharing) {
        if (isSharing) {
            kotlinx.coroutines.delay(3000) // Reset after 3 seconds
            isSharing = false
        }
    }
    
    LaunchedEffect(isApplying) {
        if (isApplying) {
            kotlinx.coroutines.delay(5000) // Reset after 5 seconds (wallpaper setting takes longer)
            isApplying = false
        }
    }
    val context = LocalContext.current
    
    // Animation for scale
    val scale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(Material3Motion.DURATION_SHORT, easing = Material3Motion.emphasizedDecelerate),
        label = "scale"
    )
    
    // Check if wallpaper is favorite
    val favoriteIds by favoritesViewModel.favoriteIds.collectAsState()
    val isFavorite = favoriteIds.contains(wallpaper.id)
    
    // Animated favorite button properties
    val animatedFavoriteScale by animateFloatAsState(
        targetValue = favoriteScale,
        animationSpec = Material3Motion.emphasizedSpring,
        label = "favoriteScale"
    )
    
    val animatedFavoriteColor by animateColorAsState(
        targetValue = if (isFavorite) Color.Red else Color.White,
        animationSpec = tween(durationMillis = 300, easing = EaseOutCubic),
        label = "favoriteColor"
    )
    
    // Reset scale after animation
    LaunchedEffect(triggerFavoriteAnimation) {
        if (triggerFavoriteAnimation) {
            favoriteScale = 1.2f
            kotlinx.coroutines.delay(150)
            favoriteScale = 1f
            kotlinx.coroutines.delay(150)
            triggerFavoriteAnimation = false
        }
    }
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f))
                .clickable { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            AnimatedVisibility(
                visible = true,
                enter = scaleIn(
                    animationSpec = Material3Motion.sharedElementSpec(),
                    initialScale = 0.8f
                ) + fadeIn(
                    animationSpec = tween(
                        durationMillis = Material3Motion.DURATION_MEDIUM,
                        easing = Material3Motion.emphasizedDecelerate
                    )
                ),
                exit = scaleOut(
                    animationSpec = Material3Motion.sharedElementSpec(),
                    targetScale = 0.8f
                ) + fadeOut(
                    animationSpec = tween(
                        durationMillis = Material3Motion.DURATION_SHORT,
                        easing = Material3Motion.emphasizedAccelerate
                    )
                )
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp), // Reduced dialog padding
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // Container with padding like FastWallpaperCard
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(24.dp)) // Consistent corner radius
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                            .padding(8.dp) // Reduced internal padding
                            .scale(scale)
                            .clickable { /* Prevent click from propagating */ }
                    ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Wallpaper Preview Card - 20% smaller than previous size
                        Card(
                            modifier = Modifier
                                .width(277.dp) // 346 * 0.8 = 276.8 ≈ 277
                                .height(346.dp) // 432 * 0.8 = 345.6 ≈ 346
                                .clickable {
                                    // Navigate to DetailScreen when tapping the wallpaper
                                    navigationState?.let { navState ->
                                        onDismiss() // Close the dialog first
                                        
                                        val subcategory = wallpaper.subCategory
                                        val shouldShowAd = adManager?.shouldShowAd() ?: false
                                        
                                        if (!subcategory.isNullOrEmpty()) {
                                            navState.navigateToDetail(
                                                wallpaper.id, 
                                                sourceScreen, 
                                                categoryName, 
                                                subcategory, 
                                                shouldShowAd, 
                                                sortOption
                                            )
                                        } else {
                                            navState.navigateToDetail(
                                                wallpaper.id, 
                                                sourceScreen, 
                                                categoryName, 
                                                shouldShowAd = shouldShowAd, 
                                                sortOption = sortOption
                                            )
                                        }
                                    }
                                },
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(wallpaper.thumbnail) // Use thumbnail instead of imageUrl
                                    .crossfade(true)
                                    .build(),
                                contentDescription = wallpaper.wallpaperName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(24.dp))
                            )
                        }
                        
                        // Wallpaper name outside the image like FastWallpaperCard
                        Text(
                            text = wallpaper.wallpaperName,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .width(277.dp)
                                .padding(top = 12.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(32.dp))
                
                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Close Button (moved to left)
                    ActionButton(
                        icon = Icons.Default.Close,
                        label = "Close",
                        tint = Color.White,
                        onClick = onDismiss,
                        isLoading = false,
                        scale = 1f
                    )
                    
                    // Enhanced Favorite Button with animations and haptics
                    ActionButton(
                        painter = painterResource(
                            id = if (isFavorite) R.drawable.ic_favorite_rounded_filled 
                                 else R.drawable.ic_favorite_rounded_outlined
                        ),
                        label = if (isFavorite) "Favorited" else "Favorite",
                        tint = animatedFavoriteColor,
                        onClick = {
                            // Haptic feedback
                            view.performHapticFeedback(
                                if (isFavorite) HapticFeedbackConstants.CONTEXT_CLICK 
                                else HapticFeedbackConstants.KEYBOARD_TAP
                            )
                            
                            // Trigger animation
                            triggerFavoriteAnimation = true
                            
                            // Toggle favorite
                            favoritesViewModel.toggleFavorite(wallpaper)
                            
                            // Show normal toast message
                            val message = if (isFavorite) {
                                "Removed from favorites 💔"
                            } else {
                                "Added to favorites ❤️"
                            }
                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                        },
                        isLoading = false,
                        scale = animatedFavoriteScale
                    )
                    
                    // Share Button
                    ActionButton(
                        icon = Icons.Rounded.IosShare,
                        label = "Share",
                        tint = Color.White,
                        onClick = {
                            isSharing = true
                            onShareWallpaper()
                        },
                        isLoading = isSharing,
                        scale = 1f
                    )
                    
                    // Apply Button
                    ActionButton(
                        icon = Icons.Rounded.FormatPaint,
                        label = "Apply",
                        tint = Color.White,
                        onClick = {
                            showSetWallpaperSheet = true
                        },
                        isLoading = isApplying,
                        scale = 1f
                    )
                }
            }
            }
        }
        
        // Bottom sheet for wallpaper apply options
        if (showSetWallpaperSheet) {
            SetWallpaperBottomSheet(
                onDismiss = { showSetWallpaperSheet = false },
                onOptionSelected = { option ->
                    isApplying = true
                    // Call the original onApplyWallpaper with the selected option
                    onApplyWallpaper()
                    showSetWallpaperSheet = false
                },
                isSettingWallpaper = isApplying,
                progress = 0f,
                showExternalOption = true
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit,
    isLoading: Boolean = false,
    scale: Float = 1f
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            modifier = Modifier
                .size(48.dp)
                .scale(scale)
                .clickable(
                    onClick = onClick,
                    enabled = !isLoading
                ),
            shape = CircleShape,
            color = Color(0xFF1A1A2E).copy(alpha = 0.9f),
            shadowElevation = 8.dp
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                if (isLoading) {
                    LoadingIndicator(
                        modifier = Modifier.size(22.dp),
                        color = Color.White
                    )
                } else {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = tint,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ActionButton(
    painter: androidx.compose.ui.graphics.painter.Painter,
    label: String,
    tint: Color,
    onClick: () -> Unit,
    isLoading: Boolean = false,
    scale: Float = 1f
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            modifier = Modifier
                .size(48.dp)
                .scale(scale)
                .clickable(
                    onClick = onClick,
                    enabled = !isLoading
                ),
            shape = CircleShape,
            color = Color(0xFF1A1A2E).copy(alpha = 0.9f),
            shadowElevation = 8.dp
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                if (isLoading) {
                    LoadingIndicator(
                        modifier = Modifier.size(22.dp),
                        color = Color.White
                    )
                } else {
                    Icon(
                        painter = painter,
                        contentDescription = label,
                        tint = tint,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}