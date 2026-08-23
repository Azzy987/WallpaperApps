package com.droidates.wallpapers.core.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInOutQuad
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
fun GradientButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = 56.dp,
    cornerRadius: Dp = 16.dp,
    pulseWhenEnabled: Boolean = false,
    content: @Composable RowScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    val backgroundColor: Color = if (enabled) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }

    val buttonScale = remember { Animatable(1f) }
    LaunchedEffect(enabled, pulseWhenEnabled) {
        if (!pulseWhenEnabled || !enabled) {
            buttonScale.snapTo(1f)
            return@LaunchedEffect
        }
        delay(500)
        while (enabled && pulseWhenEnabled) {
            buttonScale.animateTo(1.05f, tween(800, easing = EaseInOutQuad))
            buttonScale.animateTo(1f, tween(800, easing = EaseInOutQuad))
            delay(300)
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .graphicsLayer {
                if (pulseWhenEnabled) {
                    scaleX = buttonScale.value
                    scaleY = buttonScale.value
                }
            }
            .clip(shape)
            .background(backgroundColor)
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(),
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}
