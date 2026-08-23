package com.droidates.wallpapers.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * A composable that displays a circular indicator.
 *
 * @param isSelected Whether the indicator is selected.
 * @param modifier Optional modifier for customizing the layout.
 * @param selectedColor Color to use when the indicator is selected.
 * @param unselectedColor Color to use when the indicator is not selected.
 */
@Composable
fun CircleIndicator(
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    selectedColor: Color = MaterialTheme.colorScheme.primary,
    unselectedColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
) {
    Box(
        modifier = modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(if (isSelected) selectedColor else unselectedColor)
    )
} 