package com.droidates.wallpapers.core.ui.components.detail

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.runtime.remember
import com.droidates.wallpapers.core.utils.ImageUtils

/**
 * A combined wallpaper preview component that switches between HomeScreenPreview and LockScreenPreview.
 * 
 * @param isLockScreenPreview Whether to show lock screen (true) or home screen (false)
 * @param imageUrl The URL of the wallpaper image to display as background
 * @param wallpaperId The ID of the wallpaper for cache consistency with DetailScreen
 * @param onClosePreview Callback to close the preview mode
 * @param onToggleLockScreenPreview Callback to toggle between lock screen and home screen previews
 */
@Composable
fun WallpaperPreview(
    isLockScreenPreview: Boolean,
    imageUrl: String,
    wallpaperId: String = "",
    onClosePreview: () -> Unit,
    onToggleLockScreenPreview: () -> Unit
) {
    val context = LocalContext.current
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            // Add clickable to close preview on tap
            .clickable { onClosePreview() }
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
            label = "WallpaperCrossfade"
        ) { currentImageUrl ->
            // FIXED: Use same cache keys as DetailScreen for instant loading
            val previewRequest = remember(currentImageUrl, wallpaperId) {
                ImageRequest.Builder(context)
                    .data(currentImageUrl)
                    // Use same cache keys as DetailScreen to reuse cached images instantly
                    .memoryCacheKey(wallpaperId.ifEmpty { currentImageUrl })
                    .diskCacheKey(wallpaperId.ifEmpty { currentImageUrl })
                    .crossfade(false) // Disable Coil crossfade, use AnimatedContent
                    .allowHardware(true)
                    .allowRgb565(false)
                    .build()
            }
            
            AsyncImage(
                model = previewRequest,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Top controls row with close button and screen type selector
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(16.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Close button
            IconButton(
                onClick = onClosePreview,
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        color = Color.Black.copy(alpha = 0.6f),
                        shape = CircleShape
                    )
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close preview",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }

            // Screen type selector
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(4.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp)
            ) {
                // Lock Screen button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            if (isLockScreenPreview) MaterialTheme.colorScheme.primary
                            else Color.Transparent
                        )
                        .clickable { if (!isLockScreenPreview) onToggleLockScreenPreview() }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "Lock",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White
                    )
                }

                // Home Screen button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            if (!isLockScreenPreview) MaterialTheme.colorScheme.primary
                            else Color.Transparent
                        )
                        .clickable { if (isLockScreenPreview) onToggleLockScreenPreview() }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "Home",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White
                    )
                }
            }
        }

        // Appropriate screen preview based on selection
        AnimatedVisibility(
            visible = !isLockScreenPreview,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            HomeScreenPreview()
        }
        
        AnimatedVisibility(
            visible = isLockScreenPreview,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            LockScreenPreview()
        }
    }
} 