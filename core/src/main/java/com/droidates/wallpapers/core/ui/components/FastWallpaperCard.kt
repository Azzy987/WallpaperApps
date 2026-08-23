package com.droidates.wallpapers.core.ui.components

import com.droidates.wallpapers.core.ui.components.detail.rememberFavoriteBounce
import com.droidates.wallpapers.core.config.AppConfig
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
import com.droidates.wallpapers.core.ui.theme.Material3Motion
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.input.pointer.pointerInput
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.request.ImageRequest
import coil.size.Precision
import coil.size.Scale
import coil.size.Size
import com.droidates.wallpapers.core.R
import com.droidates.wallpapers.core.model.Wallpaper
import com.droidates.wallpapers.core.navigation.NavigationState
import com.droidates.wallpapers.core.viewmodel.FavoritesViewModel
import androidx.compose.ui.text.font.FontWeight
import com.droidates.wallpapers.core.utils.LocalAdManager
import com.droidates.wallpapers.core.utils.toSafeScale
import com.droidates.wallpapers.core.ui.theme.badgeExclusiveContainer
import com.droidates.wallpapers.core.ui.theme.badgeExclusiveOnContainer
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
    isFavoriteOverride: Boolean? = null,
    onFavoriteToggle: ((Wallpaper) -> Unit)? = null,
    shouldShowAdOnOpen: Boolean = false,
    benchmarkTag: String? = null,
) {
    fun cloudFrontFitInUrl(url: String, width: Int, height: Int): String {
        val marker = ".cloudfront.net/"
        val markerIndex = url.indexOf(marker)
        if (markerIndex == -1) return url

        val domainEnd = markerIndex + marker.length
        var path = url.substring(domainEnd).trimStart('/')
        if (path.startsWith("fit-in/")) {
            val segments = path.split("/")
            path = if (segments.size > 2) segments.drop(2).joinToString("/") else path
        }
        return url.substring(0, domainEnd) + "fit-in/${width}x${height}/$path"
    }

    val adManager = LocalAdManager.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    
    val favoriteIdsState = if (isFavoriteOverride == null) {
        favoritesViewModel.favoriteIds.collectAsState()
    } else {
        null
    }
    val isFavorite = isFavoriteOverride ?: favoriteIdsState?.value?.contains(wallpaper.id) == true
    
    // State for long press preview dialog
    var showPreviewDialog by remember { mutableStateOf(false) }
    
    // Favorite pop. The Animatable is idle unless pop() is called, so this keeps the
    // property that mattered here before: no per-card animator ticking during scroll.
    val favoriteBounce = rememberFavoriteBounce()

    // PERF: Color is a simple conditional, no animation needed during scroll
    val favoriteColor = if (isFavorite) Color.Red else MaterialTheme.colorScheme.onSurfaceVariant
    
    val wallpaperName = wallpaper.wallpaperName
    val isExclusive = wallpaper.exclusive
    val thumbnailUrl = remember(wallpaper.thumbnail, wallpaper.imageUrl) {
        val base = wallpaper.thumbnail.ifBlank { wallpaper.imageUrl }
        cloudFrontFitInUrl(base, width = 360, height = 640)
    }
    val isDepthEffect = wallpaper.depthEffect == true || wallpaper.category == "Depth Effect"
    
    val rootModifier = if (benchmarkTag != null) {
        modifier.testTag(benchmarkTag)
    } else {
        modifier
    }

    Box(
        modifier = rootModifier
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
                                val subcategory = wallpaper.subCategory
                                
                                if (!subcategory.isNullOrEmpty()) {
                                    navigationState.navigateToDetail(wallpaper.id, sourceScreen, categoryName, subcategory, shouldShowAdOnOpen, sortOption)
                                } else {
                                    navigationState.navigateToDetail(wallpaper.id, sourceScreen, categoryName, shouldShowAd = shouldShowAdOnOpen, sortOption = sortOption)
                                }
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
                                .size(Size(360, 640))
                                .scale(Scale.FILL)
                                .precision(Precision.INEXACT)
                                .allowHardware(true)
                                .allowRgb565(true)
                                .crossfade(false)
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
                                    color = badgeExclusiveContainer,
                                    textColor = badgeExclusiveOnContainer
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
                        
                        // Trigger the pop
                        favoriteBounce.pop()
                        
                        // Toggle favorite
                        onFavoriteToggle?.invoke(wallpaper) ?: favoritesViewModel.toggleFavorite(wallpaper)
                    },
                    modifier = Modifier
                        .size(20.dp)
                        .graphicsLayer {
                            // CRASH FIX: Protect against NaN values in animations
                            val safeScale = favoriteBounce.scale.toSafeScale(min = 0.5f, max = 1.5f)
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
                                "Check out this amazing wallpaper \"${wallpaper.wallpaperName}\" from ${AppConfig.APP_NAME} App!"
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
