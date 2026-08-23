package com.droidates.wallpapers.model

data class Category(
    val id: String = "",
    val name: String = "",
    val thumbnail: String = "",
    val categoryType: String = "",
    val subcategories: List<String> = emptyList()
) 