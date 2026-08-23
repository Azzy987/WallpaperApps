package com.droidates.wallpapers.core.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.droidates.wallpapers.core.data.local.dao.FavoriteDao
import com.droidates.wallpapers.core.data.local.entity.FavoriteEntity

@Database(
    entities = [
        FavoriteEntity::class,
    ],
    // Live apps shipped versions 1, 2 and 3 — see LegacyFavoritesMigration.
    version = DB_VERSION,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class WallpaperDatabase : RoomDatabase() {
    abstract val favoriteDao: FavoriteDao
}