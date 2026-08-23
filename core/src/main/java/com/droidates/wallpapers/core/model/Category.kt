package com.droidates.wallpapers.core.model

import androidx.compose.runtime.Immutable

@Immutable
data class Category(
    val id: String = "",
    val name: String = "",
    val thumbnail: String = "",
    val categoryType: String = "",
    val subcategories: List<String> = emptyList()
) 