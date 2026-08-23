package com.droidates.wallpapers.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.animation.core.*


import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import com.droidates.wallpapers.ui.theme.Material3Motion
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import android.view.HapticFeedbackConstants
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.input.pointer.pointerInput
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.request.ImageRequest
import com.droidates.wallpapers.R
import com.droidates.wallpapers.model.Wallpaper
import com.droidates.wallpapers.navigation.NavigationState
import com.droidates.wallpapers.viewmodel.FavoritesViewModel
import androidx.compose.ui.text.font.FontWeight
import com.droidates.wallpapers.utils.LocalAdManager
import com.droidates.wallpapers.utils.toSafeScale
import android.content.Intent
import android.app.WallpaperManager
import android.widget.Toast
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import coil.ImageLoader
import android.graphics.drawable.BitmapDrawable
import android.content.ClipData
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.os.Build
import androidx.core.content.FileProvider
import java.io.File
import android.util.Log
import java.io.FileOutputStream


/**
 * UltraFastWallpaperCard - Completely optimized for zero scroll lag
 * CRITICAL OPTIMIZATION: Removes all heavy operations that cause initial scroll lag
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FastWallpaperCard(
    modifier: Modifier = Modifier,
    wallpaper: Wallpaper,
    favoritesViewModel: FavoritesViewModel,
    navigationState: NavigationState,
    sourceScreen: String = "home",
    categoryName: String? = null,
    sortOption: String = "LATEST",
) {
    val adManager = LocalAdManager.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    
    // This eliminates expensive collectAsState operations during initial scroll
    val favoriteIds by favoritesViewModel.favoriteIds.collectAsState()
    val isFavorite = favoriteIds.contains(wallpaper.id)
    
    // State for long press preview dialog
    var showPreviewDialog by remember { mutableStateOf(false) }
    
    // Animation states for favorite button - only animate on explicit interaction
    var favoriteScale by remember { mutableStateOf(1f) }
    var triggerFavoriteAnimation by remember { mutableStateOf(false) }

    // PERF: Use snap (no interpolation) when not actively animating to avoid
    // per-card ValueAnimator instances running during scroll
    val animatedFavoriteScale by animateFloatAsState(
        targetValue = favoriteScale,
        animationSpec = if (triggerFavoriteAnimation) Material3Motion.buttonPressSpec() else snap(),
        label = "favoriteScale"
    )

    // PERF: Color is a simple conditional, no animation needed during scroll
    val favoriteColor = if (isFavorite) Color.Red else MaterialTheme.colorScheme.onSurfaceVariant

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
    
    val wallpaperName = wallpaper.wallpaperName
    val isExclusive = wallpaper.exclusive
    val thumbnailUrl = wallpaper.thumbnail
    val isDepthEffect = wallpaper.depthEffect == true || wallpaper.category == "Depth Effect"
    
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(10.dp)
    ) {
        Column {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.8f)
                    .pointerInput(wallpaper.id) {
                        detectTapGestures(
                            onTap = {
                                Log.d("FastWallpaperCard", "TAP: Starting navigation for ${wallpaper.id}")
                                val subcategory = wallpaper.subCategory
                                val shouldShowAd = adManager.shouldShowAd()
                                
                                if (!subcategory.isNullOrEmpty()) {
                                    navigationState.navigateToDetail(wallpaper.id, sourceScreen, categoryName, subcategory, shouldShowAd, sortOption)
                                } else {
                                    navigationState.navigateToDetail(wallpaper.id, sourceScreen, categoryName, shouldShowAd = shouldShowAd, sortOption = sortOption)
                                }
                                Log.d("FastWallpaperCard", "TAP: Navigation call completed for ${wallpaper.id}")
                            },
                            onLongPress = {
                                // Show long press preview dialog
                                showPreviewDialog = true
                            }
                        )
                    },
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    // ULTRA-FAST LOADING: AsyncImage avoids subcomposition overhead during scroll
                    var painterState by remember { mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Empty) }

                    AsyncImage(
                        model = remember(thumbnailUrl) {
                            ImageRequest.Builder(context)
                                .data(thumbnailUrl)
                                .memoryCacheKey("thumb_${wallpaper.id}")
                                .diskCacheKey("thumb_${wallpaper.id}")
                                .crossfade(150) // Smooth fade-in without janky pop
                                .build()
                        },
                        contentDescription = wallpaperName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(12.dp)),
                        onState = { painterState = it }
                    )

                    // Show placeholder while loading
                    if (painterState is AsyncImagePainter.State.Loading || painterState is AsyncImagePainter.State.Empty) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Layers,
                                contentDescription = "Loading",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }

                    // Show error state
                    if (painterState is AsyncImagePainter.State.Error) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.errorContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Layers,
                                contentDescription = "Error loading image",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    // OPTIMIZATION: Only render badges when actually needed, using simple conditions
                    if (isExclusive || isDepthEffect) {
                        Column(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (isExclusive) {
                                UltraFastBadge(
                                    title = "Exclusive",
                                    icon = Icons.Rounded.Star,
                                    color = Color(0xFFFFD700),
                                    textColor = Color(0xFF1A1A1A)
                                )
                            }
                            
                            if (isDepthEffect) {
                                UltraFastBadge(
                                    title = "Depth",
                                    icon = Icons.Rounded.Layers,
                                    color = MaterialTheme.colorScheme.secondary,
                                    textColor = MaterialTheme.colorScheme.onSecondary
                                )
                            }
                        }
                    }
                }
            }
            
            // OPTIMIZATION: Simple row layout without complex state management
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 6.dp, start = 4.dp, end = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = wallpaperName,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                
                // Enhanced favorite button with haptics, animation, and Material 3 morphing
                Surface(
                    onClick = { 
                        // Haptic feedback
                        view.performHapticFeedback(
                            if (isFavorite) HapticFeedbackConstants.CONTEXT_CLICK 
                            else HapticFeedbackConstants.VIRTUAL_KEY
                        )
                        
                        // Trigger animation
                        triggerFavoriteAnimation = true
                        
                        // Toggle favorite
                        favoritesViewModel.toggleFavorite(wallpaper)
                    },
                    modifier = Modifier
                        .size(20.dp)
                        .graphicsLayer {
                            // CRASH FIX: Protect against NaN values in animations
                            val safeScale = animatedFavoriteScale.toSafeScale(min = 0.5f, max = 1.5f)
                            scaleX = safeScale
                            scaleY = safeScale
                        },
                    // PERF: Simple conditional shape, no continuous animator
                    shape = RoundedCornerShape(if (isFavorite) 14.dp else 8.dp),
                    color = Color.Transparent
                ) {
                    Icon(
                        painter = painterResource(
                            id = if (isFavorite) R.drawable.ic_favorite_rounded_filled 
                                 else R.drawable.ic_favorite_rounded_outlined
                        ),
                        contentDescription = if (isFavorite) 
                            stringResource(R.string.remove_from_favorites) 
                        else 
                            stringResource(R.string.add_to_favorites),
                        tint = favoriteColor,
                        modifier = Modifier
                            .size(18.dp)
                    )
                }
            }
        }
        
        // Show long press preview dialog
        if (showPreviewDialog) {
            WallpaperPreviewDialog(
                wallpaper = wallpaper,
                onDismiss = { showPreviewDialog = false },
                onApplyWallpaper = {
                    scope.launch {
                        setWallpaperAsync(context, wallpaper)
                        // Don't auto-dismiss on apply - let user see the result
                    }
                },
                onShareWallpaper = {
                    scope.launch {
                        shareWallpaperAsync(context, wallpaper)
                        // Don't auto-dismiss on share - let user see the result
                    }
                },
                favoritesViewModel = favoritesViewModel,
                adManager = adManager,
                navigationState = navigationState,
                sourceScreen = sourceScreen,
                categoryName = categoryName,
                sortOption = sortOption
            )
        }
    }
}

/**
 * Ultra-fast badge component with minimal composition overhead
 */
@Composable
private fun UltraFastBadge(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    textColor: Color
) {
    Box(
        modifier = Modifier
            .background(
                color = color,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = textColor,
                modifier = Modifier.size(12.dp)
            )
            Text(
                text = title,
                color = textColor,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * Apply wallpaper function using same logic as DetailScreen
 */
private suspend fun setWallpaperAsync(context: android.content.Context, wallpaper: Wallpaper) {
    try {
        withContext(Dispatchers.Main) {
            Toast.makeText(context, "Preparing wallpaper...", Toast.LENGTH_SHORT).show()
        }
        
        withContext(Dispatchers.IO) {
            val imageLoader = ImageLoader(context)
            val request = ImageRequest.Builder(context)
                .data(wallpaper.imageUrl)
                .build()
            
            val result = imageLoader.execute(request)
            val drawable = result.drawable
            
            if (drawable is BitmapDrawable) {
                val wallpaperManager = WallpaperManager.getInstance(context)
                
                // Apply to both home and lock screen like DetailScreen
                try {
                    wallpaperManager.setBitmap(drawable.bitmap)
                    
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Wallpaper applied successfully!", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    android.util.Log.e("WallpaperCard", "Error setting wallpaper", e)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Error setting wallpaper: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            } else {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Failed to load wallpaper image", Toast.LENGTH_SHORT).show()
                }
            }
        }
    } catch (e: Exception) {
        android.util.Log.e("WallpaperCard", "Error applying wallpaper", e)
        withContext(Dispatchers.Main) {
            Toast.makeText(context, "Failed to apply wallpaper", Toast.LENGTH_SHORT).show()
        }
    }
}

/**
 * Share wallpaper function using same logic as DetailScreen
 */
private suspend fun shareWallpaperAsync(context: android.content.Context, wallpaper: Wallpaper) {
    try {
        withContext(Dispatchers.Main) {
            Toast.makeText(context, "Preparing to share...", Toast.LENGTH_SHORT).show()
        }
        
        withContext(Dispatchers.IO) {
            try {
                // Use jpg extension for better compatibility like DetailScreen
                val fileName = "${wallpaper.wallpaperName}_${wallpaper.id.takeLast(4)}.jpg"
                
                val imageLoader = ImageLoader(context)
                val request = ImageRequest.Builder(context)
                    .data(wallpaper.imageUrl)
                    .build()
                
                val result = imageLoader.execute(request)
                val drawable = result.drawable
                
                if (drawable is BitmapDrawable) {
                    // Save to app's cache directory for sharing (FileProvider friendly)
                    val cacheDir = File(context.cacheDir, "shared_wallpapers")
                    if (!cacheDir.exists()) {
                        cacheDir.mkdirs()
                    }
                    
                    val file = File(cacheDir, fileName)
                    
                    // Save bitmap to file
                    FileOutputStream(file).use { out ->
                        drawable.bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 95, out)
                    }
                    
                    // Make file visible to other apps by scanning it
                    MediaScannerConnection.scanFile(
                        context,
                        arrayOf(file.absolutePath),
                        arrayOf("image/jpeg"),
                        null
                    )
                    
                    // Create URI using FileProvider like DetailScreen
                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.provider",
                        file
                    )
                    
                    // Create and start share intent with image + text like DetailScreen
                    withContext(Dispatchers.Main) {
                        val shareIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            putExtra(Intent.EXTRA_STREAM, uri)
                            
                            // Set MIME type - very important for proper previews
                            type = "image/jpeg"
                            
                            // Set a title for the share - helps with previews
                            putExtra(Intent.EXTRA_TITLE, wallpaper.wallpaperName)
                            
                            // Android 10+ specific configuration for better sharing experience
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                // Setting clipData is crucial for showing thumbnails in modern Android versions
                                val clipData = ClipData.newUri(context.contentResolver, "Wallpaper Image", uri)
                                this.clipData = clipData
                            }
                            
                            // Add subject and text like DetailScreen
                            putExtra(Intent.EXTRA_SUBJECT, "Check out this wallpaper!")
                            putExtra(
                                Intent.EXTRA_TEXT,
                                "Check out this amazing wallpaper \"${wallpaper.wallpaperName}\" from OnePlus 7 Wallpapers App!"
                            )
                        }
                        
                        // Explicitly grant permission to receiving apps like DetailScreen
                        val resInfoList = context.packageManager.queryIntentActivities(
                            shareIntent, PackageManager.MATCH_DEFAULT_ONLY
                        )
                        for (resolveInfo in resInfoList) {
                            val packageName = resolveInfo.activityInfo.packageName
                            context.grantUriPermission(
                                packageName, uri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION
                            )
                        }
                        
                        try {
                            context.startActivity(Intent.createChooser(shareIntent, "Share Wallpaper"))
                            Toast.makeText(context, "Wallpaper shared successfully!", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            android.util.Log.e("WallpaperCard", "Error sharing wallpaper", e)
                            Toast.makeText(context, "No apps available to share", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Failed to load wallpaper for sharing", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("WallpaperCard", "Error preparing wallpaper for sharing", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Error preparing wallpaper: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    } catch (e: Exception) {
        android.util.Log.e("WallpaperCard", "Error in shareWallpaper", e)
        withContext(Dispatchers.Main) {
            Toast.makeText(context, "Error sharing wallpaper", Toast.LENGTH_SHORT).show()
        }
    }
}

/**
 * Modern Ripple Breathing Animation - Elegant circular ripples
 * Uses two breathing circles that create a ripple effect
 */
@Composable
private fun Material3AnimatedPlaceholder() {
    val infiniteTransition = rememberInfiniteTransition(label = "rippleBreathing")
    
    // Main circle breathing scale
    val innerCircleScale by infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = Material3Motion.emphasizedEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "innerCircleScale"
    )
    
    // Outer ripple circle scale - slightly offset timing for ripple effect
    val outerCircleScale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = Material3Motion.emphasizedDecelerate),
            repeatMode = RepeatMode.Reverse
        ),
        label = "outerCircleScale"
    )
    
    // Alpha animation for ripple effect
    val innerCircleAlpha by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = Material3Motion.emphasizedEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "innerCircleAlpha"
    )
    
    val outerCircleAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = Material3Motion.emphasizedDecelerate),
            repeatMode = RepeatMode.Reverse
        ),
        label = "outerCircleAlpha"
    )
    
    // Background gradient animation
    val backgroundPulse by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = Material3Motion.legacyEmphasized),
            repeatMode = RepeatMode.Reverse
        ),
        label = "backgroundPulse"
    )
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.08f + backgroundPulse * 0.04f),
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.06f),
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f)
                    ),
                    radius = 400f + backgroundPulse * 150f
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        // Ripple circles effect
        Box(
            contentAlignment = Alignment.Center
        ) {
            // Outer ripple circle (light color)
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .graphicsLayer {
                        // CRASH FIX: Protect against NaN values in infinite animations
                        val safeOuterScale = outerCircleScale.toSafeScale(min = 0.8f, max = 1.5f)
                        scaleX = safeOuterScale
                        scaleY = safeOuterScale
                        alpha = outerCircleAlpha
                    }
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                        shape = androidx.compose.foundation.shape.CircleShape
                    )
            )

            // Inner main circle (filled, darker)
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .graphicsLayer {
                        // CRASH FIX: Protect against NaN values in infinite animations
                        val safeInnerScale = innerCircleScale.toSafeScale(min = 0.5f, max = 1.2f)
                        scaleX = safeInnerScale
                        scaleY = safeInnerScale
                        alpha = innerCircleAlpha
                    }
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                        shape = androidx.compose.foundation.shape.CircleShape
                    )
            )
        }
    }
} 