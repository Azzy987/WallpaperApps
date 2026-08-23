package com.droidates.wallpapers.core.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import com.droidates.wallpapers.core.ui.theme.rememberReducedMotion

@Composable
fun ShimmerBox(modifier: Modifier = Modifier) {
    val reducedMotion = rememberReducedMotion()
    val base = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    val highlight = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f)

    if (reducedMotion) {
        Box(modifier = modifier.background(base))
        return
    }

    val transition = rememberInfiniteTransition(label = "shimmer")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_progress"
    )

    val brush = Brush.linearGradient(
        colors = listOf(base, highlight, base),
        start = Offset(progress * 800f - 200f, 0f),
        end = Offset(progress * 800f + 200f, 400f)
    )

    Box(modifier = modifier.background(brush))
}
