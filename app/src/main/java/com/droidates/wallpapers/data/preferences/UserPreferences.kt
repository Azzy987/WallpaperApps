package com.droidates.wallpapers.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import com.droidates.wallpapers.utils.SortOption
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

// Define the DataStore at the file level
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

@Singleton
class UserPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.dataStore

    companion object {
        private val APP_OPEN_COUNT = intPreferencesKey("app_open_count")
        private val HAS_GIVEN_FEEDBACK = booleanPreferencesKey("has_given_feedback")
        private val LAST_FEEDBACK_PROMPT = longPreferencesKey("last_feedback_prompt")
        private val SORT_OPTION = stringPreferencesKey("sort_option")
        private val UNLOCKED_WALLPAPERS = stringPreferencesKey("unlocked_wallpapers")
        
        // TASK 15: App State Preservation keys
        private val CURRENT_TAB_INDEX = intPreferencesKey("current_tab_index")
        private val HOME_SCROLL_POSITION = intPreferencesKey("home_scroll_position")
        private val CATEGORIES_SCROLL_POSITION = intPreferencesKey("categories_scroll_position")
        private val TRENDING_SCROLL_POSITION = intPreferencesKey("trending_scroll_position")
        private val FAVORITES_SCROLL_POSITION = intPreferencesKey("favorites_scroll_position")
        private val LAST_VIEWED_WALLPAPER = stringPreferencesKey("last_viewed_wallpaper")
        private val LAST_VIEWED_CATEGORY = stringPreferencesKey("last_viewed_category")
        private val APP_STATE_TIMESTAMP = longPreferencesKey("app_state_timestamp")
    }

    val appOpenCount: Flow<Int> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[APP_OPEN_COUNT] ?: 0
        }

    val hasGivenFeedback: Flow<Boolean> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[HAS_GIVEN_FEEDBACK] ?: false
        }

    val lastFeedbackPrompt: Flow<Long> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[LAST_FEEDBACK_PROMPT] ?: 0L
        }

    val sortOption: Flow<SortOption> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            val sortOptionName = preferences[SORT_OPTION] ?: SortOption.LATEST.name
            try {
                SortOption.valueOf(sortOptionName)
            } catch (e: Exception) {
                SortOption.LATEST
            }
        }

    val unlockedWallpapers: Flow<Set<String>> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[UNLOCKED_WALLPAPERS]?.split(",")?.filter { it.isNotEmpty() }?.toSet() ?: emptySet()
        }

    // TASK 15: App State Preservation Flow properties
    val currentTabIndex: Flow<Int> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[CURRENT_TAB_INDEX] ?: 0
        }

    val homeScrollPosition: Flow<Int> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[HOME_SCROLL_POSITION] ?: 0
        }

    val categoriesScrollPosition: Flow<Int> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[CATEGORIES_SCROLL_POSITION] ?: 0
        }

    val trendingScrollPosition: Flow<Int> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[TRENDING_SCROLL_POSITION] ?: 0
        }

    val favoritesScrollPosition: Flow<Int> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[FAVORITES_SCROLL_POSITION] ?: 0
        }

    val lastViewedWallpaper: Flow<String?> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[LAST_VIEWED_WALLPAPER]
        }

    val lastViewedCategory: Flow<String?> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[LAST_VIEWED_CATEGORY]
        }

    val appStateTimestamp: Flow<Long> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[APP_STATE_TIMESTAMP] ?: 0L
        }

    suspend fun incrementAppOpenCount() {
        dataStore.edit { preferences ->
            val currentCount = preferences[APP_OPEN_COUNT] ?: 0
            preferences[APP_OPEN_COUNT] = currentCount + 1
        }
    }

    suspend fun setHasGivenFeedback(hasGiven: Boolean) {
        dataStore.edit { preferences ->
            preferences[HAS_GIVEN_FEEDBACK] = hasGiven
        }
    }

    suspend fun setLastFeedbackPrompt(timestamp: Long) {
        dataStore.edit { preferences ->
            preferences[LAST_FEEDBACK_PROMPT] = timestamp
        }
    }

    suspend fun setSortOption(option: SortOption) {
        dataStore.edit { preferences ->
            preferences[SORT_OPTION] = option.name
        }
    }

    suspend fun addUnlockedWallpaper(wallpaperId: String) {
        dataStore.edit { preferences ->
            val currentUnlocked = preferences[UNLOCKED_WALLPAPERS] ?: ""
            val unlockedSet = currentUnlocked.split(",").filter { it.isNotEmpty() }.toMutableSet()
            unlockedSet.add(wallpaperId)
            preferences[UNLOCKED_WALLPAPERS] = unlockedSet.joinToString(",")
        }
    }

    suspend fun isWallpaperUnlocked(wallpaperId: String): Boolean {
        val prefs = dataStore.data.firstOrNull() ?: return false
        val currentUnlocked = prefs[UNLOCKED_WALLPAPERS] ?: ""
        return currentUnlocked.split(",").contains(wallpaperId)
    }
    
    /**
     * Get the current app open count synchronously with a default value of 0
     * This is useful for UI code that needs the value immediately without suspending
     */
    suspend fun getAppOpenCount(): Int {
        return dataStore.data.firstOrNull()?.get(APP_OPEN_COUNT) ?: 0
    }

    // TASK 15: App State Preservation methods
    
    /**
     * Save current tab index for state preservation
     */
    suspend fun saveCurrentTabIndex(tabIndex: Int) {
        dataStore.edit { preferences ->
            preferences[CURRENT_TAB_INDEX] = tabIndex
            preferences[APP_STATE_TIMESTAMP] = System.currentTimeMillis()
        }
    }

    /**
     * Save scroll position for a specific tab
     */
    suspend fun saveScrollPosition(tabIndex: Int, scrollPosition: Int) {
        dataStore.edit { preferences ->
            when (tabIndex) {
                0 -> preferences[HOME_SCROLL_POSITION] = scrollPosition
                1 -> preferences[CATEGORIES_SCROLL_POSITION] = scrollPosition
                2 -> preferences[TRENDING_SCROLL_POSITION] = scrollPosition
                3 -> preferences[FAVORITES_SCROLL_POSITION] = scrollPosition
            }
            preferences[APP_STATE_TIMESTAMP] = System.currentTimeMillis()
        }
    }

    /**
     * Save last viewed wallpaper for state preservation
     */
    suspend fun saveLastViewedWallpaper(wallpaperId: String) {
        dataStore.edit { preferences ->
            preferences[LAST_VIEWED_WALLPAPER] = wallpaperId
            preferences[APP_STATE_TIMESTAMP] = System.currentTimeMillis()
        }
    }

    /**
     * Save last viewed category for state preservation
     */
    suspend fun saveLastViewedCategory(category: String) {
        dataStore.edit { preferences ->
            preferences[LAST_VIEWED_CATEGORY] = category
            preferences[APP_STATE_TIMESTAMP] = System.currentTimeMillis()
        }
    }

    /**
     * Get current tab index synchronously for immediate use
     */
    suspend fun getCurrentTabIndex(): Int {
        return dataStore.data.firstOrNull()?.get(CURRENT_TAB_INDEX) ?: 0
    }

    /**
     * Get scroll position for a specific tab synchronously
     */
    suspend fun getScrollPosition(tabIndex: Int): Int {
        val prefs = dataStore.data.firstOrNull() ?: return 0
        return when (tabIndex) {
            0 -> prefs[HOME_SCROLL_POSITION] ?: 0
            1 -> prefs[CATEGORIES_SCROLL_POSITION] ?: 0
            2 -> prefs[TRENDING_SCROLL_POSITION] ?: 0
            3 -> prefs[FAVORITES_SCROLL_POSITION] ?: 0
            else -> 0
        }
    }

    /**
     * Check if app state is recent (within last 24 hours) to determine if we should restore it
     */
    suspend fun isAppStateRecent(): Boolean {
        val timestamp = dataStore.data.firstOrNull()?.get(APP_STATE_TIMESTAMP) ?: 0L
        val currentTime = System.currentTimeMillis()
        val twentyFourHoursInMillis = 24 * 60 * 60 * 1000L
        return (currentTime - timestamp) < twentyFourHoursInMillis
    }

    /**
     * Clear app state (useful for testing or when state becomes invalid)
     */
    suspend fun clearAppState() {
        dataStore.edit { preferences ->
            preferences.remove(CURRENT_TAB_INDEX)
            preferences.remove(HOME_SCROLL_POSITION)
            preferences.remove(CATEGORIES_SCROLL_POSITION)
            preferences.remove(TRENDING_SCROLL_POSITION)
            preferences.remove(FAVORITES_SCROLL_POSITION)
            preferences.remove(LAST_VIEWED_WALLPAPER)
            preferences.remove(LAST_VIEWED_CATEGORY)
            preferences.remove(APP_STATE_TIMESTAMP)
        }
    }
} 