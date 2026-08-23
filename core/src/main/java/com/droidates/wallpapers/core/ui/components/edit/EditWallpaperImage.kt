package com.droidates.wallpapers.core.ui.components.edit

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.droidates.wallpapers.core.ui.components.ImageFilter
import com.droidates.wallpapers.core.ui.components.edit.ColorMatrixManager
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import kotlin.math.abs

/**
 * A component that displays the wallpaper image with applied color adjustments.
 *
 * @param imageUrl The URL of the wallpaper image
 * @param wallpaperId The ID of the wallpaper for cache consistency with DetailScreen
 * @param brightness The brightness adjustment value (-1f to 1f)
 * @param contrast The contrast adjustment value (-1f to 1f)
 * @param saturation The saturation adjustment value (-1f to 1f)
 * @param hue The hue adjustment value (-1f to 1f)
 * @param opacity The opacity value (0.1f to 1f)
 * @param selectedFilter The currently selected filter to apply (can be null)
 * @param isFlippedHorizontally Whether the image is flipped horizontally
 * @param modifier Optional modifier for customizing the layout
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun EditWallpaperImage(
    imageUrl: String,
    wallpaperId: String = "",
    brightness: Float,
    contrast: Float,
    saturation: Float,
    hue: Float,
    opacity: Float,
    selectedFilter: ImageFilter? = null,
    isFlippedHorizontally: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    // Track loading state
    var isImageLoading by remember { mutableStateOf(true) }
    
    // Simplified approach: directly use scaleX instead of rotation animation
    val scaleX = animateFloatAsState(
        targetValue = if (isFlippedHorizontally) -1f else 1f,
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "scaleXAnimation"
    )
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // FIXED: Smooth crossfade animation using AnimatedContent
        AnimatedContent(
            targetState = imageUrl,
            transitionSpec = {
                fadeIn(
                    animationSpec = tween(durationMillis = 300)
                ) togetherWith fadeOut(
                    animationSpec = tween(durationMillis = 300)
                )
            },
            label = "EditWallpaperCrossfade"
        ) { currentImageUrl ->
            // FIXED: Use the same cache keys as DetailScreen to reuse cached images
            val imageRequest = remember(currentImageUrl, wallpaperId) {
                ImageRequest.Builder(context)
                    .data(currentImageUrl)
                    .memoryCacheKey(wallpaperId.ifEmpty { currentImageUrl }) // Use wallpaper ID for cache consistency
                    .diskCacheKey(wallpaperId.ifEmpty { currentImageUrl })
                    .crossfade(false) // Disable Coil crossfade, use AnimatedContent
                    .allowHardware(true)
                    .allowRgb565(false)
                    .build()
            }
            
            AsyncImage(
                model = imageRequest,
                contentDescription = "Wallpaper",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(24.dp))
                    .graphicsLayer(
                        // Simply scale horizontally for flip effect
                        scaleX = scaleX.value
                    ),
                colorFilter = ColorFilter.colorMatrix(
                    ColorMatrixManager.createColorMatrix(
                        brightness = brightness,
                        contrast = contrast,
                        saturation = saturation,
                        hue = hue,
                        opacity = opacity,
                        selectedFilter = selectedFilter
                    )
                ),
                onLoading = { isImageLoading = true },
                onSuccess = { 
                    isImageLoading = false
                },
                onError = { 
                    isImageLoading = false
                }
            )
        }
        
        // Show loading indicator until image loads
        if (isImageLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(
                            Color.Black.copy(alpha = 0.5f),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    LoadingIndicator(
                        modifier = Modifier.size(48.dp),
                        color = Color.White
                    )
                }
            }
        }
    }
} 