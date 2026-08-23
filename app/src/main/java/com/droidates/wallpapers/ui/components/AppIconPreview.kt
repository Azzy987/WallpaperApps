package com.droidates.wallpapers.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

/**
 * A composable that displays an app icon preview.
 *
 * @param drawableResId Optional drawable resource ID for the icon.
 * @param imageVector Optional image vector for the icon.
 * @param contentDescription Description of the icon for accessibility.
 */
@Composable
fun AppIconPreview(
    drawableResId: Int,
    contentDescription: String
) {
    Box(
        modifier = Modifier
            .padding(8.dp)
            .size(54.dp),
        contentAlignment = Alignment.Center
    ) {

                Image(
                    painter = painterResource(id = drawableResId),
                    contentDescription = contentDescription,
                    modifier = Modifier.fillMaxSize()
                )

    }
} 