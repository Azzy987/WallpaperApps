package com.droidates.wallpapers.core.di

import android.content.Context
import androidx.room.Room
import com.droidates.wallpapers.core.data.local.WallpaperDatabase
import com.droidates.wallpapers.core.data.local.dao.FavoriteDao
import com.droidates.wallpapers.core.data.repository.FavoritesRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import com.droidates.wallpapers.core.data.repository.AuthRepository

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideWallpaperDatabase(
        @ApplicationContext context: Context
    ): WallpaperDatabase {
        return Room.databaseBuilder(
            context,
            WallpaperDatabase::class.java,
            com.droidates.wallpapers.core.config.AppConfig.DATABASE_NAME
        )
        // Rename the app's pre-:core favourites table instead of letting Room wipe it.
        // Covers every version a live app shipped with (1, 2 and 3).
        .addMigrations(
            *com.droidates.wallpapers.core.data.local.legacyFavoritesMigrations(
                com.droidates.wallpapers.core.config.AppConfig.LEGACY_FAVORITES_TABLE
            )
        )
        .setQueryExecutor { command ->
            // Use background thread pool for all database operations
            java.util.concurrent.Executors.newSingleThreadExecutor { runnable ->
                Thread(runnable, "DatabaseThread").apply {
                    isDaemon = true
                    priority = Thread.NORM_PRIORITY - 1 // Slightly lower priority
                }
            }.execute(command)
        }
        .setTransactionExecutor { command ->
            // Separate thread for transactions to avoid blocking queries
            java.util.concurrent.Executors.newSingleThreadExecutor { runnable ->
                Thread(runnable, "DatabaseTransactionThread").apply {
                    isDaemon = true
                    priority = Thread.NORM_PRIORITY
                }
            }.execute(command)
        }
        .build()
    }




    @Provides
    @Singleton
    fun provideFavoriteDao(database: WallpaperDatabase): FavoriteDao {
        return database.favoriteDao
    }

    @Provides
    @Singleton
    fun provideFavoritesRepository(
        favoriteDao: FavoriteDao,
        firebaseAuth: FirebaseAuth,
        firestore: FirebaseFirestore,
        authRepository: AuthRepository
    ): FavoritesRepository {
        return FavoritesRepository(favoriteDao, firebaseAuth, firestore, authRepository)
    }
} 