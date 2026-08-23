package com.droidates.wallpapers.core.model

import androidx.compose.runtime.Immutable

@Immutable
data class Banner(
    val wallpaperId: String = "",
    val bannerUrl: String = "",
    val bannerName: String = "",
    val depthEffect: Boolean = false,
    val exclusive: Boolean = false,
    val bannerType: String = "wallpaper",  // "wallpaper" or "app_promo"
    val appUrl: String = ""               // only used when bannerType == "app_promo"
)