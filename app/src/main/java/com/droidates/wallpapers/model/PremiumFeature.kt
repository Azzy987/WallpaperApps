package com.droidates.wallpapers.model

import androidx.compose.ui.graphics.vector.ImageVector

data class PremiumFeature(
    val title: String,
    val description: String,
    val icon: ImageVector? = null,
    val iconDrawableRes: Int? = null
) 