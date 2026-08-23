package com.droidates.wallpapers.core.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest

/**
 * A composable that displays a filter option with preview image and optional lock overlay.
 * Used in the EditWallpaperScreen to show available image filters.
 *
 * @param name The name of the filter
 * @param isSelected Whether the filter is currently selected
 * @param onClick Callback to invoke when the filter is clicked
 * @param previewMatrix The color matrix to apply to the preview image
 * @param wallpaperUrl The URL of the wallpaper to use for preview
 * @param wallpaperId The ID of the wallpaper for cache consistency with DetailScreen
 * @param isLocked Whether the filter is locked (requires premium or ad watch)
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FilterItem(
    name: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    previewMatrix: ColorMatrix,
    wallpaperUrl: String?,
    wallpaperId: String = "",
    isLocked: Boolean = false
) {
    val context = LocalContext.current
    
    // Track loading state for each filter preview
    var isImageLoading by remember { mutableStateOf(true) }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(80.dp)
            .clickable(onClick = onClick)
            .padding(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(70.dp)
                .clip(RoundedCornerShape(24.dp))
                .border(
                    width = if (isSelected) 3.dp else 1.dp,
                    color = if (isSelected) MaterialTheme.colorScheme.primary 
                           else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(24.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            // FIXED: Use same cache keys as DetailScreen for instant loading
            val previewRequest = remember(wallpaperUrl, wallpaperId) {
                wallpaperUrl?.let { url ->
                    ImageRequest.Builder(context)
                        .data(url)
                        // Use same cache keys as DetailScreen to reuse cached images instantly
                        .memoryCacheKey(wallpaperId.ifEmpty { url })
                        .diskCacheKey(wallpaperId.ifEmpty { url })
                        .crossfade(false)
                        .allowHardware(true)
                        .allowRgb565(true) // Allow RGB565 for previews to save memory
                        .build()
                }
            }
            
            AsyncImage(
                model = previewRequest,
                contentDescription = name,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop,
                colorFilter = ColorFilter.colorMatrix(previewMatrix),
                onLoading = { isImageLoading = true },
                onSuccess = { isImageLoading = false },
                onError = { isImageLoading = false }
            )
            
            // Show loading indicator for filter previews
            if (isImageLoading) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(Color.Black.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    LoadingIndicator(
                        modifier = Modifier.size(16.dp),
                        color = Color.White
                    )
                }
            }
            
            // Show lock overlay if filter is locked
            if (isLocked) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(Color.Black.copy(alpha = 0.7f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Locked",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        
        Text(
            text = name,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * Overloaded FilterItem that works with the ImageFilter enum.
 * 
 * @param filter The ImageFilter to display (can be null for "None" option)
 * @param imageUrl The URL of the wallpaper to use for preview
 * @param wallpaperId The ID of the wallpaper for cache consistency with DetailScreen
 * @param isSelected Whether this filter is currently selected
 * @param onClick Callback for when the filter is clicked
 * @param isLocked Whether the filter is locked (requires premium or ad watch)
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FilterItem(
    filter: ImageFilter?,
    imageUrl: String,
    wallpaperId: String = "",
    isSelected: Boolean,
    onClick: () -> Unit,
    isLocked: Boolean = false
) {
    // For null filter (None option), use identity matrix
    val matrix = filter?.matrix ?: ColorMatrix()
    val name = filter?.displayName ?: "None"
    
    FilterItem(
        name = name,
        isSelected = isSelected,
        onClick = onClick,
        previewMatrix = matrix,
        wallpaperUrl = imageUrl,
        wallpaperId = wallpaperId,
        isLocked = isLocked
    )
} 