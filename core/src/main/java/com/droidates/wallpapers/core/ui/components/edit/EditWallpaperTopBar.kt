package com.droidates.wallpapers.core.ui.components.edit

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight

/**
 * Top app bar for the edit wallpaper screen.
 * Displays a back button, title, and download button.
 *
 * @param onBackClick Callback to invoke when the back button is clicked
 * @param onDownloadClick Callback to invoke when the download button is clicked
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditWallpaperTopBar(
    onBackClick: () -> Unit,
    onDownloadClick: () -> Unit = {}
) {
    TopAppBar(
        title = {
            Text(
                text = "Edit Wallpaper",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold
                )
            )
        },
        navigationIcon = {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back"
                )
            }
        },
        actions = {
            IconButton(onClick = onDownloadClick) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = "Download"
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent
        )
    )
} 