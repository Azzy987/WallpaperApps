package com.droidates.wallpapers.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * A composable that displays a slider for editing wallpaper properties.
 * Used in the EditWallpaperScreen for adjusting various image properties.
 *
 * @param value The current value of the slider
 * @param onValueChange Callback to invoke when the value changes
 * @param valueRange The range of values the slider can take
 * @param title The title to display above the slider
 * @param icon The icon to display next to the title
 */
@Composable
fun EditSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    title: String,
    icon: ImageVector
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = icon, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = title, fontWeight = FontWeight.Bold)
            }
            
            // Calculate percentage based on the value and range
            val percentage = when (title) {
                "Opacity" -> (value * 100).toInt() // Just show percentage without sign
                else -> {
                    // For brightness, contrast, saturation, and hue
                    (value * 100).toInt()
                }
            }
            
            // Show percentage with sign for non-blur values
            val displayText = when (title) {
                "Opacity" -> "$percentage%" // Just show percentage without sign
                else -> {
                    if (percentage > 0) "+$percentage%"
                    else if (percentage < 0) "$percentage%"
                    else "0%" // Just show 0% without sign
                }
            }
            
            Text(
                text = displayText,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
        }
        
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
        )
    }
} 