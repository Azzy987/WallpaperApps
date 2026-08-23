package com.droidates.wallpapers.ui.components.detail

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.RemoveRedEye
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.droidates.wallpapers.model.Wallpaper
import com.droidates.wallpapers.utils.StatFormatter

/**
 * A component that displays detailed information about a wallpaper.
 * Includes title, category, stats (downloads, views, etc.), and premium badge if applicable.
 *
 * @param wallpaper The wallpaper data to display
 * @param modifier Optional modifier for the component
 * @param onInfoClick Callback for when the "INFO" button is clicked
 */
@Composable
fun WallpaperDetailInfo(
    wallpaper: Wallpaper,
    modifier: Modifier = Modifier,
    onInfoClick: () -> Unit = {}
) {
    Column(modifier = modifier) {
        // Title and category row with badges
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = wallpaper.wallpaperName,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = wallpaper.category,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Show exclusive or depth effect badge
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (wallpaper.exclusive) {
                    WallpaperBadge(
                        text = "EXCLUSIVE",
                        icon = Icons.Rounded.Star,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                if (wallpaper.depthEffect == true) {
                    WallpaperBadge(
                        text = "DEPTH",
                        icon = Icons.Rounded.Layers,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Stats row with updated order
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left column: Downloads, Dimensions, Info
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                StatItem(
                    icon = Icons.Outlined.Info,
                    value = "${StatFormatter.formatStatValue(wallpaper.downloads)} downloads",
                    iconColor = MaterialTheme.colorScheme.primary
                )
                StatItem(
                    icon = Icons.Rounded.AspectRatio,
                    value = wallpaper.dimensions,
                    iconColor = MaterialTheme.colorScheme.primary
                )
                StatItem(
                    icon = Icons.Outlined.Info,
                    value = "INFO",
                    iconColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { onInfoClick() }
                )
            }

            // Right column: Views, Size
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                StatItem(
                    icon = Icons.Rounded.RemoveRedEye,
                    value = "${StatFormatter.formatStatValue(wallpaper.views)} views",
                    iconColor = MaterialTheme.colorScheme.primary
                )
                StatItem(
                    icon = Icons.Outlined.Storage,
                    value = wallpaper.size,
                    iconColor = MaterialTheme.colorScheme.primary
                )
                // The Report option is moved out of this component
            }
        }
    }
}

@Composable
fun WallpaperBadge(
    text: String,
    icon: ImageVector,
    color: androidx.compose.ui.graphics.Color
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(24.dp))
            .background(color.copy(alpha = 0.2f))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = text,
                color = color,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
        }
    }
} 