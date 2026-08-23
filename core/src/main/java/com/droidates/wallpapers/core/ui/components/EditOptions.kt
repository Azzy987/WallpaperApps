package com.droidates.wallpapers.core.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Enum representing different editing options available for wallpapers.
 * Each option has an associated icon and description.
 */
enum class EditOption(
    val icon: ImageVector,
    val description: String
) {
    FILTER(
        icon = Icons.Default.FilterVintage,
        description = "Filters"
    ),
    BRIGHTNESS(
        icon = Icons.Default.BrightnessHigh,
        description = "Brightness"
    ),
    CONTRAST(
        icon = Icons.Default.BrightnessLow,
        description = "Contrast"
    ),
    SATURATION(
        icon = Icons.Default.ColorLens,
        description = "Saturation"
    ),
    HUE(
        icon = Icons.Default.Palette,
        description = "Hue"
    ),
    OPACITY(
        icon = Icons.Default.Opacity,
        description = "Opacity"
    ),
    FLIP(
        icon = Icons.Default.Flip,
        description = "Flip"
    )
}

/**
 * Enum representing different image filters that can be applied to wallpapers.
 * Each filter has an associated name and color matrix for the filter effect.
 */
enum class ImageFilter(
    val displayName: String,
    val matrix: ColorMatrix
) {
    NONE(
        displayName = "None",
        matrix = ColorMatrix()
    ),
    GRAYSCALE(
        displayName = "Grayscale",
        matrix = ColorMatrix().apply {
            setToSaturation(0f)
        }
    ),
    SEPIA(
        displayName = "Sepia",
        matrix = ColorMatrix().apply {
            setToSaturation(0f)
            setToScale(1.0f, 0.95f, 0.82f, 1.0f)
        }
    ),
    VINTAGE(
        displayName = "Vintage",
        matrix = ColorMatrix().apply {
            setToScale(1.0f, 0.95f, 0.82f, 1.0f)
            setToSaturation(0.7f)
        }
    ),
    COOL(
        displayName = "Cool",
        matrix = ColorMatrix().apply {
            setToScale(1.0f, 1.0f, 1.2f, 1.0f)
        }
    ),
    WARM(
        displayName = "Warm",
        matrix = ColorMatrix().apply {
            setToScale(1.2f, 1.0f, 1.0f, 1.0f)
        }
    ),
    INVERT(
        displayName = "Invert",
        matrix = ColorMatrix(
            floatArrayOf(
                -1f, 0f, 0f, 0f, 255f,
                0f, -1f, 0f, 0f, 255f,
                0f, 0f, -1f, 0f, 255f,
                0f, 0f, 0f, 1f, 0f
            )
        )
    ),
    CYBERPUNK(
        displayName = "CyberPunk",
        matrix = ColorMatrix().apply {
            setToScale(0.8f, 0.9f, 1.5f, 1.0f)
            setToSaturation(1.5f)
        }
    ),
    NEON(
        displayName = "Neon",
        matrix = ColorMatrix().apply {
            setToScale(1.3f, 0.5f, 1.3f, 1.0f)
            setToSaturation(1.4f)
        }
    ),
    RETROWAVE(
        displayName = "Retrowave",
        matrix = ColorMatrix().apply {
            setToScale(1.2f, 0.8f, 1.3f, 1.0f)
            setToSaturation(1.2f)
        }
    ),
    MIDNIGHT(
        displayName = "Midnight",
        matrix = ColorMatrix().apply {
            setToScale(0.7f, 0.8f, 1.3f, 1.0f)
            setToSaturation(0.9f)
        }
    ),
    SYNTHWAVE(
        displayName = "Synthwave",
        matrix = ColorMatrix().apply {
            setToScale(1.3f, 0.7f, 1.4f, 1.0f)
            setToSaturation(1.3f)
        }
    ),
    LOFI(
        displayName = "Lo-Fi",
        matrix = ColorMatrix().apply {
            setToScale(1.1f, 1.1f, 1.0f, 1.0f)
            setToSaturation(1.2f)
        }
    ),
    MOON(
        displayName = "Moon",
        matrix = ColorMatrix().apply {
            setToSaturation(0.3f)
            setToScale(1.1f, 1.1f, 1.1f, 1.0f)
        }
    ),
    SUNSET(
        displayName = "Sunset",
        matrix = ColorMatrix().apply {
            setToScale(1.4f, 0.8f, 0.6f, 1.0f)
            setToSaturation(1.1f)
        }
    ),
    APOCALYPSE(
        displayName = "Apocalypse",
        matrix = ColorMatrix().apply {
            setToScale(1.5f, 0.7f, 0.4f, 1.0f)
            setToSaturation(0.8f)
        }
    ),
    DYSTOPIA(
        displayName = "Dystopia",
        matrix = ColorMatrix().apply {
            setToScale(0.6f, 0.7f, 0.8f, 1.0f)
            setToSaturation(0.6f)
        }
    ),
    MATRIX(
        displayName = "Matrix",
        matrix = ColorMatrix().apply {
            setToScale(0.5f, 1.4f, 0.5f, 1.0f)
            setToSaturation(1.1f)
        }
    ),
    ARCTIC(
        displayName = "Arctic",
        matrix = ColorMatrix().apply {
            setToScale(0.8f, 1.0f, 1.4f, 1.0f)
            setToSaturation(0.8f)
        }
    ),
    DESERT(
        displayName = "Desert",
        matrix = ColorMatrix().apply {
            setToScale(1.6f, 1.2f, 0.8f, 1.0f)
            setToSaturation(0.9f)
        }
    ),
    VAPORWAVE(
        displayName = "Vaporwave",
        matrix = ColorMatrix().apply {
            setToScale(1.1f, 0.9f, 1.4f, 1.0f)
            setToSaturation(1.2f)
        }
    )
} 