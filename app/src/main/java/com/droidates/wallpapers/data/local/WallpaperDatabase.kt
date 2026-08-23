package com.droidates.wallpapers.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.droidates.wallpapers.data.local.dao.FavoriteDao
import com.droidates.wallpapers.data.local.entity.FavoriteEntity

@Database(
    entities = [
        FavoriteEntity::class,
    ],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class WallpaperDatabase : RoomDatabase() {
    abstract val favoriteDao: FavoriteDao
}