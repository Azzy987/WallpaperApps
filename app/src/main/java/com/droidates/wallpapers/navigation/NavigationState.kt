package com.droidates.wallpapers.navigation

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
import com.droidates.wallpapers.data.preferences.UserPreferences
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
        
        // TASK 15: Save tab state for preservation
        userPreferences?.let { prefs ->
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    prefs.saveCurrentTabIndex(index)
                    if (VERBOSE_LOGGING) {
                        Log.d(TAG, "Saved current tab index: $index")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error saving current tab index: ${e.message}")
                }
            }
        }
    }
    
    // Get current tab
    fun getCurrentTab(): Int {
        return currentTabIndex
    }

    fun restoreAppState() {
        // Force Home tab (0) always - no preference restoration for tab state
                        currentTabIndex = 0
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
        if (isNavigationInProgress) {
            if (VERBOSE_LOGGING) {
                Log.d(NAV_DEBUG_TAG, "Navigation already in progress, ignoring request")
            }
            return
        }
        
        startNavigationDebounce()
        
        try {
            val currentRoute = navController.currentBackStackEntry?.destination?.route
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
            else if (currentRoute?.startsWith("detail") == true) {
                Log.d(TAG, "Popping back from detail to origin tab: $originTab")
                // For detail screen, go back and restore the origin tab
                if (canGoBack) navController.popBackStack()
                // Restore the tab from which detail was opened
                setCurrentTab(originTab)
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
        
        // CRITICAL FIX: Store current tab as origin for proper back navigation
        originTab = currentTabIndex
        
        // Add explicit debug logging always, regardless of verbose setting
        Log.d(TAG, "Starting navigation to detail with wallpaperId=$wallpaperId, sourceScreen=$sourceScreen, categoryName=$categoryName, subcategory=$subcategory from tab: $currentTabIndex")
        
        startNavigationDebounce()
        
        try {
            // Check if we're already on the detail screen for this wallpaper
            val currentRoute = navController.currentBackStackEntry?.destination?.route
            if (currentRoute?.startsWith("detail/$wallpaperId") == true) {
                if (VERBOSE_LOGGING) {
                    Log.d(TAG, "Already on detail screen for this wallpaper, ignoring navigation")
                }
                return
            }
            
            // Execute navigation immediately without debounce
            
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
                Log.d(TAG, "Adding category info to source: $formattedSource")
            }
            
            val route = Screen.Detail.createRoute(wallpaperId, formattedSource, shouldShowAd, sortOption)
            
            // Always log the route for debugging purposes
            Log.d(TAG, "Navigating with route: $route")
            
            // Only clear navigation history if NOT navigating from a category screen
            // This ensures we can go back to the specific category when pressing back
            if (sourceScreen.startsWith("category")) {
                navController.navigate(route) {
                    // Don't pop up the stack for category -> detail navigation
                    // This preserves the category screen in the back stack
                    launchSingleTop = true
                }
                
                if (VERBOSE_LOGGING) {
                    Log.d(TAG, "Navigating to detail from category, preserving back stack")
                }
            } else {
                // For other sources, pop back to (but not including) the start destination,
                // then push the detail screen on top. This keeps the start destination in
                // the back stack so the user can always press Back to return home.
                navController.navigate(route) {
                    popUpTo(navController.graph.startDestinationId) {
                        inclusive = false
                        saveState = false
                    }
                    launchSingleTop = true
                }
            }
            
            if (VERBOSE_LOGGING) {
                Log.d(NAV_DEBUG_TAG, "Navigation to detail complete for id: $wallpaperId from source: $formattedSource")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during detail navigation: ${e.message}")
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

    // Helper method to get Activity from Context
    private fun android.content.Context.findActivity(): android.app.Activity? {
        var context = this
        while (context is android.content.ContextWrapper) {
            if (context is android.app.Activity) {
                return context
            }
            context = context.baseContext
        }
        return null
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