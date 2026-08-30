package com.droidates.wallpapers.core.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.StringRes
import com.droidates.wallpapers.core.R
import com.droidates.wallpapers.core.config.AppConfig
import com.google.firebase.firestore.Query

enum class SortOption(@StringRes val displayNameResId: Int) {
    LAUNCH_YEAR(R.string.sort_launch_year),
    LATEST(R.string.sort_latest),
    VIEWS(R.string.sort_views),
    DOWNLOADS(R.string.sort_downloads)
}

/** Maps a SortOption to the Firestore field name and sort direction to use in queries. */
fun SortOption.toFirestoreSort(): Pair<String, Query.Direction> = when (this) {
    SortOption.LAUNCH_YEAR -> "launchYear" to Query.Direction.DESCENDING
    SortOption.LATEST      -> "timestamp"  to Query.Direction.DESCENDING
    SortOption.VIEWS       -> "views"      to Query.Direction.DESCENDING
    SortOption.DOWNLOADS   -> "downloads"  to Query.Direction.DESCENDING
}

/**
 * Helper class for persisting sort preferences.
 */
object SortPreferences {
    private const val PREF_NAME = "sort_preferences"
    private const val KEY_HOME_SORT_OPTION = "home_sort_option"
    private const val KEY_TRENDING_SORT_OPTION = "trending_sort_option"
    
    /**
     * Get SharedPreferences instance.
     */
    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }
    
    /**
     * Save sort option for a specific screen.
     */
    fun saveSortOption(context: Context, sortOption: SortOption, isHomeScreen: Boolean) {
        val key = if (isHomeScreen) KEY_HOME_SORT_OPTION else KEY_TRENDING_SORT_OPTION
        getPrefs(context).edit().putString(key, sortOption.name).apply()
    }
    
    /**
     * Get saved sort option for a specific screen.
     * Defaults to LAUNCH_YEAR for home screen and LATEST for trending screen if not found or on first launch.
     */
    fun getSortOption(context: Context, isHomeScreen: Boolean): SortOption {
        // Apps whose wallpapers have no `launchYear` cannot sort by it: Firestore's
        // orderBy drops documents missing the field, so the Home tab would come back
        // empty. Those apps default to Latest instead.
        val homeDefault =
            if (AppConfig.SUPPORTS_LAUNCH_YEAR_SORT) SortOption.LAUNCH_YEAR else SortOption.LATEST
        val defaultSortOption = if (isHomeScreen) homeDefault else SortOption.LATEST
        val key = if (isHomeScreen) KEY_HOME_SORT_OPTION else KEY_TRENDING_SORT_OPTION
        val prefs = getPrefs(context)

        // If the preference doesn't exist (e.g., first launch), save the default
        if (!prefs.contains(key)) {
            saveSortOption(context, defaultSortOption, isHomeScreen)
        }

        val savedOption = getPrefs(context).getString(key, defaultSortOption.name) ?: defaultSortOption.name
        val resolved = try {
            SortOption.valueOf(savedOption)
        } catch (e: IllegalArgumentException) {
            defaultSortOption
        }
        // A LAUNCH_YEAR preference can already be on disk from an earlier install or a
        // shared prefs restore; honouring it would silently empty the grid.
        return if (resolved == SortOption.LAUNCH_YEAR && !AppConfig.SUPPORTS_LAUNCH_YEAR_SORT) {
            defaultSortOption
        } else {
            resolved
        }
    }

    /**
     * Observe sort option changes for reactive updates.
     * This enables reactive monitoring instead of polling.
     */
    fun observeSortOption(context: Context, isHomeScreen: Boolean, callback: (SortOption) -> Unit) {
        val key = if (isHomeScreen) KEY_HOME_SORT_OPTION else KEY_TRENDING_SORT_OPTION
        val prefs = getPrefs(context)

        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, changedKey ->
            if (changedKey == key) {
                val newSortOption = getSortOption(context, isHomeScreen)
                callback(newSortOption)
            }
        }

        prefs.registerOnSharedPreferenceChangeListener(listener)
    }
} 