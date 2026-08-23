package com.droidates.wallpapers.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

data class ButtonGroupOption(
    val text: String,
    val icon: ImageVector? = null,
    val value: String
)

@Composable
fun <T> ButtonGroup(
    options: List<ButtonGroupOption>,
    selectedValue: T,
    onSelectionChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp) // Spacing between buttons
    ) {
        options.forEachIndexed { index, option ->
            ConnectedButtonGroupItem(
                option = option,
                isSelected = option.value == selectedValue.toString(),
                onClick = { onSelectionChange(option.value) },
                position = when (index) {
                    0 -> ButtonPosition.START
                    options.size - 1 -> ButtonPosition.END
                    else -> ButtonPosition.MIDDLE
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

private enum class ButtonPosition {
    START, MIDDLE, END
}

@Composable
private fun ConnectedButtonGroupItem(
    option: ButtonGroupOption,
    isSelected: Boolean,
    onClick: () -> Unit,
    position: ButtonPosition,
    modifier: Modifier = Modifier
) {
    // Animated properties
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.05f else 1.0f,
        animationSpec = tween(durationMillis = 200),
        label = "scale"
    )
    
    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.primary
        } else {
            // More visible color for light theme
            MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
        },
        animationSpec = tween(durationMillis = 200),
        label = "backgroundColor"
    )
    
    val contentColor by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        animationSpec = tween(durationMillis = 200),
        label = "contentColor"
    )
    
    // Shape based on position and selection state
    val shape = if (isSelected) {
        // Selected button is always elliptical
        RoundedCornerShape(50)
    } else {
        // Unselected button shapes based on position
        when (position) {
            ButtonPosition.START -> RoundedCornerShape(
                topStart = 50.dp,
                bottomStart = 50.dp,
                topEnd = 20.dp,
                bottomEnd = 20.dp
            ) // Half ellipse from left, rounded square from right
            ButtonPosition.MIDDLE -> RoundedCornerShape(20.dp) // Rounded square
            ButtonPosition.END -> RoundedCornerShape(
                topStart = 20.dp,
                bottomStart = 20.dp,
                topEnd = 50.dp,
                bottomEnd = 50.dp
            ) // Rounded square from left, half ellipse from right
        }
    }
    
    Box(
        modifier = modifier
            .height(44.dp)
            .scale(scale)
            .background(
                color = backgroundColor,
                shape = shape
            )
            .clip(shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            option.icon?.let { icon ->
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(16.dp)
                )
            }
            Text(
                text = option.text,
                style = MaterialTheme.typography.labelLarge,
                color = contentColor,
                fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                modifier = if (option.icon != null) Modifier.padding(start = 4.dp) else Modifier
            )
        }
    }
}