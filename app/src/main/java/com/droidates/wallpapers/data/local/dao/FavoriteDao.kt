package com.droidates.wallpapers.data.local.dao

import androidx.room.*
import com.droidates.wallpapers.data.local.entity.FavoriteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM oneplus7favorites ORDER BY timestamp DESC")
    fun getAllFavorites(): Flow<List<FavoriteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavorite(favorite: FavoriteEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(favorites: List<FavoriteEntity>)

    @Query("DELETE FROM oneplus7favorites WHERE wallpaperId = :wallpaperId")
    suspend fun deleteFavorite(wallpaperId: String)

    @Query("SELECT EXISTS(SELECT 1 FROM oneplus7favorites WHERE wallpaperId = :wallpaperId)")
    suspend fun isFavorite(wallpaperId: String): Boolean

    @Query("SELECT wallpaperId FROM oneplus7favorites")
    suspend fun getAllFavoriteIds(): List<String>
} 