package com.droidates.wallpapers.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/** Solid brand accent — orange for light/dark, system dynamic for system mode. */
@Composable
fun accentColor(): Color = MaterialTheme.colorScheme.primary

@Composable
fun accentOnColor(): Color = MaterialTheme.colorScheme.onPrimary

@Composable
fun accentGradientBrush(): Brush {
    val primary = MaterialTheme.colorScheme.primary
    return Brush.linearGradient(listOf(primary, primary))
}

@Composable
fun accentGradientOnColor(): Color = MaterialTheme.colorScheme.onPrimary

@Composable
fun paywallBackdropScrim(): Brush {
    val dark = isSystemInDarkTheme()
    return if (dark) {
        Brush.verticalGradient(
            colorStops = arrayOf(
                0f to Color.Black.copy(alpha = 0.45f),
                0.35f to Color.Black.copy(alpha = 0.25f),
                0.55f to Color.Black.copy(alpha = 0.55f),
                1f to Color.Black.copy(alpha = 0.82f)
            )
        )
    } else {
        Brush.verticalGradient(
            colorStops = arrayOf(
                0f to Color.White.copy(alpha = 0.55f),
                0.35f to Color.White.copy(alpha = 0.35f),
                0.55f to Color.White.copy(alpha = 0.62f),
                1f to Color(0xFFEFF1F8).copy(alpha = 0.92f)
            )
        )
    }
}
