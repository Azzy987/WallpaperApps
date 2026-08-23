package com.droidates.wallpapers.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.droidates.wallpapers.model.Wallpaper

@Entity(tableName = "oneplus7favorites")
data class FavoriteEntity(
    @PrimaryKey
    val wallpaperId: String,
    val wallpaperName: String,
    val imageUrl: String,
    val thumbnail: String,
    val category: String,
    val downloads: Int,
    val views: Int,
    val dimensions: String,
    val size: String,
    val exclusive: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun toWallpaper(): Wallpaper {
        return Wallpaper(
            id = wallpaperId,
            wallpaperName = wallpaperName,
            imageUrl = imageUrl,
            thumbnail = thumbnail,
            category = category,
            downloads = downloads,
            views = views,
            dimensions = dimensions,
            size = size,
            exclusive = exclusive,
            isFavorite = true
        )
    }

    companion object {
        fun fromWallpaper(wallpaper: Wallpaper): FavoriteEntity {
            return FavoriteEntity(
                wallpaperId = wallpaper.id,
                wallpaperName = wallpaper.wallpaperName,
                imageUrl = wallpaper.imageUrl,
                thumbnail = wallpaper.thumbnail,
                category = wallpaper.category,
                downloads = wallpaper.downloads,
                views = wallpaper.views,
                dimensions = wallpaper.dimensions,
                size = wallpaper.size,
                exclusive = wallpaper.exclusive
            )
        }
    }
} 