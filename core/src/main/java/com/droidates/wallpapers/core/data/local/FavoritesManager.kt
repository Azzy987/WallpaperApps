package com.droidates.wallpapers.core.data.local

import android.content.Context
import android.widget.Toast
import com.droidates.wallpapers.core.config.AppConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FavoritesManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs = context.getSharedPreferences(AppConfig.FAVORITES_PREFS_KEY, Context.MODE_PRIVATE)

    init {
        // Migrate old favorites from S25 app if they exist
        migrateOldFavorites()
    }

    private fun migrateOldFavorites() {
        val oldPrefs = context.getSharedPreferences("s25favorites", Context.MODE_PRIVATE)
        val oldFavorites = oldPrefs.getStringSet("s25favorites", null)

        if (oldFavorites != null && oldFavorites.isNotEmpty()) {
            // Copy to new preferences
            prefs.edit()
                .putStringSet(AppConfig.FAVORITES_PREFS_KEY, oldFavorites)
                .apply()

            // Clear old preferences
            oldPrefs.edit().clear().apply()
        }
    }

    fun toggleFavorite(wallpaperId: String): Boolean {
        val currentFavorites = getFavorites().toMutableSet()
        val newState = if (currentFavorites.contains(wallpaperId)) {
            currentFavorites.remove(wallpaperId)
            false
        } else {
            currentFavorites.add(wallpaperId)
            true
        }

        prefs.edit()
            .putStringSet(AppConfig.FAVORITES_PREFS_KEY, currentFavorites)
            .apply()

        val message = if (newState) "Added to favorites" else "Removed from favorites"
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()

        return newState
    }

    fun isFavorite(wallpaperId: String): Boolean {
        return getFavorites().contains(wallpaperId)
    }

    fun getFavorites(): Set<String> {
        return prefs.getStringSet(AppConfig.FAVORITES_PREFS_KEY, emptySet()) ?: emptySet()
    }
} 