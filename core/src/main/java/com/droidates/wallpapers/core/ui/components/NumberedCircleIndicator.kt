package com.droidates.wallpapers.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * A composable that displays a circle indicator with a number.
 *
 * @param color The color of the circle.
 * @param number The number to display next to the circle.
 */
@Composable
fun NumberedCircleIndicator(color: Color, number: String) {
    Box(contentAlignment = Alignment.Center) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(color, CircleShape)
            )
            Text(
                text = number,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White
            )
        }
    }
} 