package com.droidates.wallpapers.ui.components.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Info
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.painterResource
import com.droidates.wallpapers.R
import com.droidates.wallpapers.ui.components.ActionButton

/**
 * A component that displays a row of action buttons for the wallpaper detail screen.
 * Actions include download, favorite, edit, share, and info.
 *
 * @param onDownloadClick Callback for download button click
 * @param onFavoriteClick Callback for favorite button click
 * @param onEditClick Callback for edit button click
 * @param onShareClick Callback for share button click
 * @param onInfoClick Callback for info button click
 * @param isDownloading Whether the wallpaper is currently being downloaded
 * @param isFavorite Whether the wallpaper is marked as favorite
 * @param modifier Optional modifier for the component
 */
@Composable
fun WallpaperActionsRow(
    onDownloadClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    onEditClick: () -> Unit,
    onShareClick: () -> Unit,
    onInfoClick: () -> Unit,
    isDownloading: Boolean = false,
    isFavorite: Boolean = false,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        // Download button
        ActionButton(
            icon = Icons.Default.Download,
            onClick = onDownloadClick,
            isLoading = isDownloading
        )
        
        // Favorite button
        ActionButton(
            painter = painterResource(
                id = if (isFavorite) R.drawable.ic_favorite_rounded_filled 
                     else R.drawable.ic_favorite_rounded_outlined
            ),
            onClick = onFavoriteClick,
            tint = if (isFavorite) Color.Red else Color.White
        )
        
        // Edit button
        ActionButton(
            icon = Icons.Default.Edit,
            onClick = onEditClick
        )
        
        // Share button
        ActionButton(
            icon = Icons.Default.Share,
            onClick = onShareClick
        )
        
        // Info button
        ActionButton(
            icon = Icons.Outlined.Info,
            onClick = onInfoClick
        )
    }
} 