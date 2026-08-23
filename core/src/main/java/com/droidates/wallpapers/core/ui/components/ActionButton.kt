package com.droidates.wallpapers.core.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp

/**
 * A composable component that displays a circular action button with an icon and optional loading state.
 * Used in the DetailScreen for various actions like download, favorite, share, etc.
 *
 * @param icon The icon to display when not in loading state
 * @param onClick The callback to invoke when the button is clicked
 * @param modifier Optional modifier for the component
 * @param tint The color to tint the icon with
 * @param isLoading Whether to show a loading indicator instead of the icon
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ActionButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
    isLoading: Boolean = false
) {
    Surface(
        modifier = modifier
            .size(48.dp)
            .shadow(
                elevation = 10.dp,
                shape = CircleShape,
                spotColor = Color(0x75000000), // 75% black
                ambientColor = Color(0x75000000)
            )
            .clickable(onClick = onClick),
        shape = CircleShape,
        color = Color(0xFF0F161E) // Dark background color from Figma
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (isLoading) {
LoadingIndicator(
                    modifier = Modifier.size(24.dp),
                    color = Color.White
                )
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

/**
 * Overloaded ActionButton that accepts a Painter instead of ImageVector.
 * Used for custom drawable resources like rounded favorite icons.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ActionButton(
    painter: Painter,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
    isLoading: Boolean = false
) {
    Surface(
        modifier = modifier
            .size(48.dp)
            .shadow(
                elevation = 10.dp,
                shape = CircleShape,
                spotColor = Color(0x75000000), // 75% black
                ambientColor = Color(0x75000000)
            )
            .clickable(onClick = onClick),
        shape = CircleShape,
        color = Color(0xFF0F161E) // Dark background color from Figma
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (isLoading) {
                LoadingIndicator(
                    modifier = Modifier.size(24.dp),
                    color = Color.White
                )
            } else {
                Icon(
                    painter = painter,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
} 