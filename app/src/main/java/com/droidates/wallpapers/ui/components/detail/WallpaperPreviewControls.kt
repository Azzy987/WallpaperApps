package com.droidates.wallpapers.ui.components.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A component that displays preview mode controls for wallpaper detail screen.
 * Allows switching between home screen and lock screen preview modes.
 *
 * @param isPreviewMode Whether preview mode is enabled
 * @param isLockScreenPreview Whether showing lock screen preview (false = home screen)
 * @param onTogglePreviewMode Callback when preview mode is toggled
 * @param onToggleLockScreenPreview Callback when lock/home screen preview is toggled
 * @param modifier Optional modifier for the component
 */
@Composable
fun WallpaperPreviewControls(
    isPreviewMode: Boolean,
    isLockScreenPreview: Boolean,
    onTogglePreviewMode: () -> Unit,
    onToggleLockScreenPreview: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Preview mode toggle button
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(32.dp))
                .background(Color.Black.copy(alpha = 0.6f))
                .clickable { onTogglePreviewMode() }
                .padding(horizontal = 16.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.ArrowUpward,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isPreviewMode) "Exit Preview" else "Preview Mode",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        
        // Only show preview type toggle when in preview mode
        if (isPreviewMode) {
            Spacer(modifier = Modifier.height(12.dp))
            
            // Home/Lock screen toggle
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(32.dp))
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                // Home screen option
                PreviewModeOption(
                    icon = Icons.Default.Home,
                    label = "Home",
                    isSelected = !isLockScreenPreview,
                    onClick = { if (isLockScreenPreview) onToggleLockScreenPreview() }
                )
                
                // Lock screen option
                PreviewModeOption(
                    icon = Icons.Default.Lock,
                    label = "Lock",
                    isSelected = isLockScreenPreview,
                    onClick = { if (!isLockScreenPreview) onToggleLockScreenPreview() }
                )
            }
        }
    }
}

/**
 * A selectable option in the preview mode controls.
 *
 * @param icon The icon to display
 * @param label The text label to display
 * @param isSelected Whether this option is currently selected
 * @param onClick Callback when this option is clicked
 */
@Composable
private fun PreviewModeOption(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(28.dp))
            .clickable(onClick = onClick)
            .background(if (isSelected) Color.White.copy(alpha = 0.2f) else Color.Transparent)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
} 