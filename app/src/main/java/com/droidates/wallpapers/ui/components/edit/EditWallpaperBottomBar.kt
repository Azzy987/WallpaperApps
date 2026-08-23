package com.droidates.wallpapers.ui.components.edit

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.BrightnessLow
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.Palette
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.droidates.wallpapers.ui.components.EditOption
import com.droidates.wallpapers.ui.components.EditOptionItem
import com.droidates.wallpapers.ui.components.EditSlider
import com.droidates.wallpapers.ui.components.FilterItem
import com.droidates.wallpapers.ui.components.ImageFilter
import com.droidates.wallpapers.ui.components.edit.EditWallpaperActionButtons

/**
 * Bottom bar for the edit wallpaper screen.
 * Contains edit options and action buttons.
 *
 * @param selectedOption The currently selected edit option
 * @param onOptionSelected Callback when an edit option is selected
 * @param onResetClick Callback when reset button is clicked
 * @param onApplyClick Callback when apply button is clicked
 * @param brightness The current brightness value
 * @param onBrightnessChange Callback when brightness changes
 * @param contrast The current contrast value
 * @param onContrastChange Callback when contrast changes
 * @param saturation The current saturation value
 * @param onSaturationChange Callback when saturation changes
 * @param hue The current hue value
 * @param onHueChange Callback when hue changes
 * @param opacity The current opacity value
 * @param onOpacityChange Callback when opacity changes
 * @param isEditOptionLocked Function to check if an edit option is locked
 * @param selectedFilter The currently selected filter
 * @param onFilterSelected Callback when a filter is selected
 * @param isFilterLocked Function to check if a filter is locked 
 * @param wallpaperImageUrl The URL of the current wallpaper image
 * @param wallpaperId The ID of the wallpaper for cache consistency with DetailScreen
 */
@Composable
fun EditWallpaperBottomBar(
    selectedOption: EditOption,
    onOptionSelected: (EditOption) -> Unit,
    onResetClick: () -> Unit,
    onApplyClick: () -> Unit,
    brightness: Float,
    onBrightnessChange: (Float) -> Unit,
    contrast: Float,
    onContrastChange: (Float) -> Unit,
    saturation: Float,
    onSaturationChange: (Float) -> Unit,
    hue: Float,
    onHueChange: (Float) -> Unit,
    opacity: Float,
    onOpacityChange: (Float) -> Unit,
    isEditOptionLocked: (EditOption) -> Boolean,
    selectedFilter: ImageFilter? = null,
    onFilterSelected: (ImageFilter?) -> Unit = {},
    isFilterLocked: (ImageFilter?) -> Boolean = { false },
    wallpaperImageUrl: String = "",
    wallpaperId: String = ""
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.Black.copy(alpha = 0.7f)
                    )
                )
            )
            .padding(16.dp)
    ) {
        // Edit options
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(horizontal = 8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(EditOption.entries.toTypedArray()) { option ->
                val lockStatus = isEditOptionLocked(option)
                EditOptionItem(
                    option = option,
                    isSelected = selectedOption == option,
                    onClick = { onOptionSelected(option) },
                    isLocked = lockStatus
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Filter Grid - Show when FILTER option is selected
        AnimatedVisibility(
            visible = selectedOption == EditOption.FILTER,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // "None" option first
                item {
                    FilterItem(
                        filter = null,
                        imageUrl = wallpaperImageUrl,
                        wallpaperId = wallpaperId,
                        isSelected = selectedFilter == null,
                        onClick = { onFilterSelected(null) },
                        isLocked = false
                    )
                }
                
                // Other filters (skip NONE filter)
                val filters = ImageFilter.values().filter { it != ImageFilter.NONE }
                items(filters) { filter ->
                    FilterItem(
                        filter = filter,
                        imageUrl = wallpaperImageUrl,
                        wallpaperId = wallpaperId,
                        isSelected = selectedFilter == filter,
                        onClick = { onFilterSelected(filter) },
                        isLocked = isFilterLocked(filter)
                    )
                }
            }
        }
        
        // Edit sliders
        AnimatedVisibility(
            visible = selectedOption != EditOption.FILTER,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column {
                when (selectedOption) {
                    EditOption.BRIGHTNESS -> {
                        EditSlider(
                            value = brightness,
                            onValueChange = onBrightnessChange,
                            valueRange = -1f..1f,
                            title = "Brightness",
                            icon = Icons.Default.BrightnessHigh
                        )
                    }
                    EditOption.CONTRAST -> {
                        EditSlider(
                            value = contrast,
                            onValueChange = onContrastChange,
                            valueRange = -1f..1f,
                            title = "Contrast",
                            icon = Icons.Default.BrightnessLow
                        )
                    }
                    EditOption.SATURATION -> {
                        EditSlider(
                            value = saturation,
                            onValueChange = onSaturationChange,
                            valueRange = -1f..1f,
                            title = "Saturation",
                            icon = Icons.Default.ColorLens
                        )
                    }
                    EditOption.HUE -> {
                        EditSlider(
                            value = hue,
                            onValueChange = onHueChange,
                            valueRange = -1f..1f,
                            title = "Hue",
                            icon = Icons.Default.Palette
                        )
                    }
                    EditOption.OPACITY -> {
                        EditSlider(
                            value = opacity,
                            onValueChange = onOpacityChange,
                            valueRange = 0.1f..1f,
                            title = "Opacity",
                            icon = Icons.Default.Opacity
                        )
                    }
                    EditOption.FLIP -> {
                        // For FLIP, we don't show any sliders
                        // This is handled directly in the EditWallpaperScreen
                    }
                    else -> {}
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Action buttons
        EditWallpaperActionButtons(
            onResetClick = onResetClick,
            onApplyClick = onApplyClick
        )
    }
} 