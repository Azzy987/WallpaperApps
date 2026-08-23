package com.droidates.wallpapers.core.navigation

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.droidates.wallpapers.core.config.AppConfig
import com.droidates.wallpapers.core.data.preferences.UserPreferences
import kotlinx.coroutines.withContext

// Debug constants
private const val NAV_DEBUG_TAG = "NavigationLifecycle"
private const val VERBOSE_LOGGING = false // DISABLE EXCESSIVE LOGGING - only keep critical logs
private const val TAG = "Navigation"

// Navigation constants
private const val NAVIGATION_DEBOUNCE_TIMEOUT = 200L // Reduced to improve responsiveness

class NavigationState(
    val navController: NavHostController,
    private val coroutineScope: CoroutineScope,
    private val userPreferences: UserPreferences? = null // TASK 15: Add UserPreferences for state preservation
) {
    var currentTabIndex by mutableStateOf(0)
        private set
    
    // TASK 17 FIX: Track the tab from which navigation originated for all screens
    private var originTab by mutableStateOf(0) // Track origin tab for all navigation
    private var categoryOriginTab by mutableStateOf(1) // Default to Categories tab
    
    // Track if navigation is in progress to prevent double navigation
    private var isNavigationInProgress = false
    private var navigationJob: Job? = null
    private var hasAppliedLaunchTab = false
    
    // Current and previous routes for better navigation management
    private var _currentRoute: String? by mutableStateOf(null)
    private var _previousRoute: String? by mutableStateOf(null)
    
    init {
        // Set up a listener to track navigation changes
        navController.addOnDestinationChangedListener { _, destination, _ ->
            if (VERBOSE_LOGGING) {
                Log.d(TAG, "Destination changed: ${destination.route}")
                Log.d(TAG, "Previous route was: $_currentRoute")
            }
            
            // Update previous and current route tracking
            _previousRoute = _currentRoute
            _currentRoute = destination.route
            
            if (VERBOSE_LOGGING) {
                Log.d(TAG, "Updated routes - Current: $_currentRoute, Previous: $_previousRoute")
            }
        }
    }
    
    // Get current route
    val currentRoute: String?
        get() = navController.currentBackStackEntry?.destination?.route
    
    // Get previous route
    val previousRoute: String?
        get() = navController.previousBackStackEntry?.destination?.route
    
    // Set current tab
    fun setCurrentTab(index: Int) {
        if (VERBOSE_LOGGING) {
            Log.d(TAG, "Setting current tab to: $index")
        }
        currentTabIndex = index
        // Tab index is in-memory + rememberSaveable only (rotation). Not persisted across app restarts.
    }
    
    // Get current tab
    fun getCurrentTab(): Int {
        return currentTabIndex
    }

    /** Cold start / new task: always open on Home (tab 0), not the last visited tab. */
    fun ensureHomeTabOnLaunch() {
        if (hasAppliedLaunchTab) return
        hasAppliedLaunchTab = true
        currentTabIndex = 0
        userPreferences?.let { prefs ->
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    prefs.saveCurrentTabIndex(0)
                } catch (e: Exception) {
                    Log.e(TAG, "Error clearing saved tab index: ${e.message}")
                }
            }
        }
    }

    // TASK 15: Save scroll position for current tab
    fun saveScrollPosition(scrollPosition: Int) {
        userPreferences?.let { prefs ->
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    prefs.saveScrollPosition(currentTabIndex, scrollPosition)
                    if (VERBOSE_LOGGING) {
                        Log.d(TAG, "Saved scroll position $scrollPosition for tab $currentTabIndex")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error saving scroll position: ${e.message}")
                }
            }
        }
    }

    // TASK 15: Get saved scroll position for a specific tab
    suspend fun getSavedScrollPosition(tabIndex: Int): Int {
        return userPreferences?.let { prefs ->
            try {
                prefs.getScrollPosition(tabIndex)
            } catch (e: Exception) {
                Log.e(TAG, "Error getting saved scroll position for tab $tabIndex: ${e.message}")
                0
            }
        } ?: 0
    }
    
    // Navigate back
    fun navigateBack() {
        navigationJob?.cancel()
        isNavigationInProgress = false

        try {
            val currentEntry = navController.currentBackStackEntry
            val currentRoute = currentEntry?.destination?.route
            val previousRoute = navController.previousBackStackEntry?.destination?.route
            
            if (VERBOSE_LOGGING) {
                Log.d(TAG, "Navigate back from $currentRoute to $previousRoute")
            }
            
            val canGoBack = navController.previousBackStackEntry != null

            // Check if we're in a detail screen going back to a category screen
            if (currentRoute?.startsWith("detail") == true && previousRoute?.startsWith("category") == true) {
                if (VERBOSE_LOGGING) {
                    Log.d(TAG, "Popping back from detail to category screen: $previousRoute")
                }
                // Pop back to the category screen — do NOT call setCurrentTab, which would
                // switch the tab and replace the CategoryScreen underneath.
                if (canGoBack) navController.popBackStack()
            }
            // Check if we're in an edit screen
            else if (currentRoute?.startsWith("edit") == true) {
                if (VERBOSE_LOGGING) {
                    Log.d(TAG, "Popping back from edit to detail screen")
                }
                // If we're in an edit screen, go back to the detail screen
                if (canGoBack) navController.popBackStack()
            }
            // Check if we're in a detail screen from elsewhere
            else if (isDetailRoute(currentRoute)) {
                val source = currentEntry?.arguments?.getString("source")
                val tabToRestore = source?.let { tabIndexForSource(it) }?.takeIf { it >= 0 } ?: originTab
                Log.d(TAG, "Popping back from detail (source=$source) to tab: $tabToRestore")
                setCurrentTab(tabToRestore)
                originTab = tabToRestore
                if (canGoBack) navController.popBackStack()
            }
            // Check if we're in a category screen
            else if (currentRoute?.startsWith("category") == true) {
                if (VERBOSE_LOGGING) {
                    Log.d(TAG, "Popping back from category to tab: $categoryOriginTab")
                }
                // TASK 17 FIX: When backing from category, restore the origin tab
                if (canGoBack) navController.popBackStack()
                // Set the tab to the one from which category was opened
                setCurrentTab(categoryOriginTab)
            }
            // For all other cases, use standard back navigation
            else {
                if (VERBOSE_LOGGING) {
                    Log.d(TAG, "Standard back navigation")
                }
                if (canGoBack) navController.popBackStack()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during back navigation: ${e.message}")
            endNavigationDebounce()
        }
    }

    // Navigate to edit screen
    fun navigateToEdit(wallpaperId: String) {
        if (isNavigationInProgress) {
            if (VERBOSE_LOGGING) {
                Log.d(NAV_DEBUG_TAG, "Navigation already in progress, ignoring request")
            }
            return
        }
        
        if (VERBOSE_LOGGING) {
            Log.d(TAG, "Navigating to edit with wallpaper ID: $wallpaperId")
        }
        
        startNavigationDebounce()
        
        try {
            val route = Screen.Edit.createRoute(wallpaperId)
            navController.navigate(route) {
                launchSingleTop = true
            }
            
            if (VERBOSE_LOGGING) {
                Log.d(NAV_DEBUG_TAG, "Navigation to edit complete")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error navigating to edit: ${e.message}")
            endNavigationDebounce()
        }
    }

    // Navigate to category screen
    fun navigateToCategory(categoryName: String) {
        if (isNavigationInProgress) {
            if (VERBOSE_LOGGING) {
                Log.d(NAV_DEBUG_TAG, "Navigation already in progress, ignoring request")
            }
            return
        }
        
        // TASK 17 FIX: Store current tab as origin for proper back navigation
        categoryOriginTab = currentTabIndex
        
        if (VERBOSE_LOGGING) {
            Log.d(TAG, "Navigating to category: $categoryName from tab: $currentTabIndex")
        }
        
        startNavigationDebounce()
        
        try {
            val route = Screen.Category.createRoute(categoryName)
            navController.navigate(route) {
                launchSingleTop = true
            }
            
            if (VERBOSE_LOGGING) {
                Log.d(NAV_DEBUG_TAG, "Navigation to category complete")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error navigating to category: ${e.message}")
            endNavigationDebounce()
        }
    }
    
    // Navigate to settings screen
    fun navigateToSettings() {
        if (isNavigationInProgress) {
            if (VERBOSE_LOGGING) {
                Log.d(NAV_DEBUG_TAG, "Navigation already in progress, ignoring request")
            }
            return
        }
        
        if (VERBOSE_LOGGING) {
            Log.d(TAG, "Navigating to settings")
        }
        
        startNavigationDebounce()
        
        try {
            navController.navigate(Screen.Settings.route) {
                launchSingleTop = true
            }
            
            if (VERBOSE_LOGGING) {
                Log.d(NAV_DEBUG_TAG, "Navigation to settings complete")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error navigating to settings: ${e.message}")
            endNavigationDebounce()
        }
    }
    
    // Navigate to help screen
    fun navigateToHelp() {
        if (isNavigationInProgress) {
            if (VERBOSE_LOGGING) {
                Log.d(NAV_DEBUG_TAG, "Navigation already in progress, ignoring request")
            }
            return
        }
        
        if (VERBOSE_LOGGING) {
            Log.d(TAG, "Navigating to help")
        }
        
        startNavigationDebounce()
        
        try {
            navController.navigate(Screen.Help.route) {
                launchSingleTop = true
            }
            
            if (VERBOSE_LOGGING) {
                Log.d(NAV_DEBUG_TAG, "Navigation to help complete")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error navigating to help: ${e.message}")
            endNavigationDebounce()
        }
    }
    
    // Navigate to categories tab
    fun navigateToCategories() {
        if (isNavigationInProgress) {
            if (VERBOSE_LOGGING) {
                Log.d(NAV_DEBUG_TAG, "Navigation already in progress, ignoring request")
            }
            return
        }
        
        if (VERBOSE_LOGGING) {
            Log.d(TAG, "Navigating to categories tab")
        }
        
        startNavigationDebounce()
        
        try {
            navController.navigate(Screen.Categories.route) {
                popUpTo(navController.graph.startDestinationId) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
            
            if (VERBOSE_LOGGING) {
                Log.d(NAV_DEBUG_TAG, "Navigation to categories complete")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error navigating to categories: ${e.message}")
            endNavigationDebounce()
        }
    }
    
    // Navigate to premium screen
    fun navigateToPremium() {
        if (isNavigationInProgress) {
            if (VERBOSE_LOGGING) {
                Log.d(NAV_DEBUG_TAG, "Navigation already in progress, ignoring request")
            }
            return
        }
        
        if (VERBOSE_LOGGING) {
            Log.d(TAG, "Navigating to premium screen")
        }
        
        startNavigationDebounce()
        
        try {
            navController.navigate(Screen.Premium.route) {
                launchSingleTop = true
            }
            
            if (VERBOSE_LOGGING) {
                Log.d(NAV_DEBUG_TAG, "Navigation to premium complete")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error navigating to premium: ${e.message}")
            endNavigationDebounce()
        }
    }
    
    // Navigate to detail screen with image preloading
    fun navigateToDetail(wallpaperId: String, sourceScreen: String, categoryName: String? = null, subcategory: String? = null, shouldShowAd: Boolean = false, sortOption: String = "LATEST") {
        if (isNavigationInProgress) {
            if (VERBOSE_LOGGING) {
                Log.d(NAV_DEBUG_TAG, "Navigation already in progress, ignoring request")
            }
            return
        }
        
        // Check if we're already on the detail screen for this wallpaper BEFORE activating debounce
        try {
            val currentRoute = navController.currentBackStackEntry?.destination?.route
            if (currentRoute?.startsWith("detail/$wallpaperId") == true) {
                if (VERBOSE_LOGGING) {
                    Log.d(TAG, "Already on detail screen for this wallpaper, ignoring navigation")
                }
                return
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error checking current route", e)
        }
        
        originTab = tabIndexForSource(sourceScreen).takeIf { it >= 0 } ?: currentTabIndex
        setCurrentTab(originTab)

        if (VERBOSE_LOGGING) {
            Log.d(TAG, "Starting navigation to detail with wallpaperId=$wallpaperId, sourceScreen=$sourceScreen from tab: $originTab")
        }

        startNavigationDebounce()
        
        try {
            // Format the source with category name and subcategory if available
            var formattedSource = sourceScreen
            if (sourceScreen == "category" && categoryName != null) {
                formattedSource = if (subcategory != null) {
                    // Include both category and subcategory
                    "category:$categoryName:$subcategory"
                } else {
                    // Just include category
                    "category:$categoryName"
                }
                
                // Always log this information for debugging
                if (VERBOSE_LOGGING) Log.d(TAG, "Adding category info to source: $formattedSource")
            }
            
            val route = Screen.Detail.createRoute(wallpaperId, formattedSource, shouldShowAd, sortOption)
            
            // Always log the route for debugging purposes
            if (VERBOSE_LOGGING) Log.d(TAG, "Navigating with route: $route")
            
            navController.navigate(route) {
                launchSingleTop = true
            }
            
            if (VERBOSE_LOGGING) {
                Log.d(NAV_DEBUG_TAG, "Navigation to detail complete for id: $wallpaperId from source: $formattedSource")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during detail navigation: ${e.message}")
            endNavigationDebounce()
        }
    }
    
    // Start navigation debounce to prevent double navigation
    private fun startNavigationDebounce() {
        isNavigationInProgress = true
        
        if (VERBOSE_LOGGING) {
            Log.d(NAV_DEBUG_TAG, "Starting navigation (debounce active)")
        }
        
        navigationJob?.cancel()
        navigationJob = coroutineScope.launch(Dispatchers.Main) {
            try {
                delay(NAVIGATION_DEBOUNCE_TIMEOUT)
                endNavigationDebounce()
            } catch (e: Exception) {
                Log.e(TAG, "Error in navigation debounce: ${e.message}")
                endNavigationDebounce()
            }
        }
    }
    
    // End navigation debounce
    private fun endNavigationDebounce() {
        isNavigationInProgress = false
        
        if (VERBOSE_LOGGING) {
            Log.d(TAG, "Navigation debounce complete, ready for next navigation")
            Log.d(NAV_DEBUG_TAG, "Navigation debounce ended - ready for next navigation")
        }
    }

    private fun isDetailRoute(route: String?): Boolean {
        return route != null && route.startsWith("detail")
    }

    private fun tabIndexForSource(sourceScreen: String): Int {
        return when {
            sourceScreen == AppConfig.SOURCE_HOME ||
                sourceScreen.startsWith("${AppConfig.SOURCE_HOME}:") -> 0
            sourceScreen == AppConfig.SOURCE_TRENDING ||
                sourceScreen.startsWith("${AppConfig.SOURCE_TRENDING}:") -> 2
            sourceScreen == AppConfig.SOURCE_FAVORITES ||
                sourceScreen.startsWith("${AppConfig.SOURCE_FAVORITES}:") -> 3
            else -> -1
        }
    }

}

@Composable
fun rememberNavigationState(
    navController: NavHostController,
    coroutineScope: CoroutineScope,
    userPreferences: UserPreferences? = null // TASK 15: Add UserPreferences parameter
): NavigationState {
    return remember(navController, coroutineScope, userPreferences) {
        NavigationState(navController, coroutineScope, userPreferences)
    }
}