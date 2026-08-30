package com.droidates.wallpapers.core.ui.screens

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.automirrored.rounded.Help
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.droidates.wallpapers.core.R
import com.droidates.wallpapers.core.navigation.NavigationItem
import com.droidates.wallpapers.core.navigation.NavigationState
import com.droidates.wallpapers.core.navigation.Screen
import com.droidates.wallpapers.core.navigation.navigationItems
import com.droidates.wallpapers.core.ui.components.BottomNavigationBar
import com.droidates.wallpapers.core.ui.components.ClickableText
import com.droidates.wallpapers.core.ui.screens.tabs.CategoriesTabScreen
import com.droidates.wallpapers.core.ui.screens.tabs.FavoritesTabScreen
import com.droidates.wallpapers.core.ui.screens.tabs.HomeTabScreen
import com.droidates.wallpapers.core.ui.screens.tabs.TrendingTabScreen
import com.droidates.wallpapers.core.utils.LocalAdManager
import com.droidates.wallpapers.core.utils.SortOption
import com.droidates.wallpapers.core.utils.SortPreferences
import com.droidates.wallpapers.core.viewmodel.AuthViewModel
import com.droidates.wallpapers.core.viewmodel.PreferencesManagerViewModel
import android.util.Log
import androidx.compose.material.icons.rounded.WorkspacePremium
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.droidates.wallpapers.core.config.AppConfig

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MainScreen(
    navigationState: NavigationState,
) {
    // These ViewModels don't perform heavy operations in their init blocks
    val preferencesManagerViewModel = hiltViewModel<PreferencesManagerViewModel>()
    
    // Create AuthViewModel for profile management
    val authViewModel: AuthViewModel = hiltViewModel()
    val isSignedIn by authViewModel.isSignedIn.collectAsState()
    val userData by authViewModel.userData.collectAsState()
    val isPremiumUser by LocalAdManager.current.isPremiumUser.collectAsState()
    
    // Enhanced LaunchedEffect to manage user data refresh in background
    LaunchedEffect(isSignedIn) {
        if (isSignedIn) {
            withContext(Dispatchers.IO) {
                authViewModel.refreshUserData(forceServerFetch = false)
            }
        }
    }
    
    // Context for intent actions
    val context = LocalContext.current
    
    // Privacy policy is now handled entirely in MainActivity (PrivacyAcceptScreen).
    // MainScreen no longer needs to show any dialog for it.
    
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    
    // Get current tab from NavigationState
    val currentTab = navigationState.getCurrentTab()
    
    // Show sort button only on Home and Trending screens (tabs 0 and 2)
    val showSortButton = currentTab == 0 || currentTab == 2
    
    // Track sort button visibility with animation
    val sortButtonVisible = remember { mutableStateOf(showSortButton) }
    
    // Update sort button visibility with animation when tab changes
    LaunchedEffect(currentTab) {
        sortButtonVisible.value = showSortButton
    }
    
    // Track sort dialog state with the currently selected tab
    var showSortDialog by remember { mutableStateOf(false) }
    
    // Get current sort option from SharedPreferences based on the selected tab
    val isHomeTab = currentTab == 0
    
    // Default for the home screen; apps without launchYear data start on Latest.
    var currentSortOption by remember {
        mutableStateOf(
            if (AppConfig.SUPPORTS_LAUNCH_YEAR_SORT) SortOption.LAUNCH_YEAR else SortOption.LATEST
        )
    }
    var sortOptionLoaded by remember { mutableStateOf(false) }
    
    // Add state to trigger immediate sort changes
    var sortTrigger by remember { mutableStateOf(0) }
    
    // Load sort option only once to prevent multiple changes
    LaunchedEffect(Unit) {
        if (!sortOptionLoaded) {
            withContext(Dispatchers.IO) {
                val savedOption = SortPreferences.getSortOption(context, isHomeScreen = true)
                withContext(Dispatchers.Main) {
                    currentSortOption = savedOption
                    sortOptionLoaded = true
                }
            }
        }
    }
    
    // Update sort option when tab changes, but only after initial load
    LaunchedEffect(currentTab) {
        if (sortOptionLoaded) {
            withContext(Dispatchers.IO) {
                val savedOption = SortPreferences.getSortOption(context, isHomeScreen = isHomeTab)
                withContext(Dispatchers.Main) {
                    if (currentSortOption != savedOption) {
                        currentSortOption = savedOption
                    }
                }
            }
        }
    }
    
    // Set initial tab to Home (0) if it's not set
    LaunchedEffect(Unit) {
        if (currentTab == 0) {
            navigationState.setCurrentTab(0)
        }
    }
    
    if (showSortDialog) {
        SortDialog(
            currentSortOption = currentSortOption,
            onDismiss = { showSortDialog = false },
            // Also gated on the app actually having launchYear data.
            showLaunchYearOption = isHomeTab && AppConfig.SUPPORTS_LAUNCH_YEAR_SORT,
            onSortOptionSelected = { selectedOption ->
                // Update our local state
                currentSortOption = selectedOption
                
                // Save the selected option to SharedPreferences for the current tab
                SortPreferences.saveSortOption(context, selectedOption, isHomeScreen = isHomeTab)
                
                // Trigger immediate sort update by incrementing trigger
                sortTrigger++
                
                Log.d("MainScreen", "Sort option saved and triggered for tab $currentTab: $selectedOption")
            }
        )
    }
    
    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = AppConfig.TOOLBAR_TITLE,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .wrapContentSize(Alignment.Center),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    },
                    navigationIcon = {
                        // Enhanced profile icon with user photo and premium badge
                        IconButton(
                            onClick = {
                                    navigationState.navigateToSettings()
                            }
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.size(32.dp)
                            ) {
                                if (isSignedIn && userData?.photoUrl != null) {
                                    // Show user profile picture
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data(userData?.photoUrl)
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = "Profile picture",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                    )
                                } else {
                                    // Show default profile icon
                                Icon(
                                        imageVector = Icons.Default.AccountCircle,
                                        contentDescription = "Settings",
                                        tint = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                                
                                // Show premium badge overlay if user is premium and signed in
                                if (isSignedIn && isPremiumUser) {
                                        Box(
                                            modifier = Modifier
                                                .size(14.dp)
                                                .align(Alignment.TopEnd)
                                                .offset(x = 2.dp, y = (-2).dp)
                                                .background(
                                                    color = MaterialTheme.colorScheme.primary,
                                                    shape = CircleShape
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.WorkspacePremium,
                                                contentDescription = "Premium User",
                                                tint = MaterialTheme.colorScheme.onPrimary,
                                                modifier = Modifier.size(10.dp)
                                            )
                                    }
                                }
                            }
                        }
                    },
                    actions = {
                        AnimatedVisibility(
                            visible = sortButtonVisible.value,
                            enter = fadeIn() + expandHorizontally(
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessMedium
                                )
                            ),
                            exit = shrinkHorizontally() + fadeOut()
                        ) {
                            IconButton(onClick = { showSortDialog = true }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.Sort,
                                    contentDescription = "Sort",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                    },
                    scrollBehavior = scrollBehavior,
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        scrolledContainerColor = MaterialTheme.colorScheme.surface
                    )
                )
            },
            containerColor = MaterialTheme.colorScheme.background,
            content = { innerPadding ->
                MainScreenContent(
                    padding = innerPadding,
                    navigationState = navigationState,
                    scrollBehavior = scrollBehavior,
                    sortTrigger = sortTrigger,
                    currentSortOption = currentSortOption,
                )
            }
        )
    }
}

@Composable
fun SortDialog(
    currentSortOption: SortOption,
    onDismiss: () -> Unit,
    showLaunchYearOption: Boolean,
    onSortOptionSelected: (SortOption) -> Unit
) {
    // Initialize selectedOption with the current sort option
    var selectedOption by remember(currentSortOption) { mutableStateOf(currentSortOption) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Text(
                text = "Sort Wallpapers By",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectableGroup()
                    .padding(vertical = 8.dp)
            ) {
                SortOption.entries.forEach { option ->
                    // Skip LAUNCH_YEAR option if showLaunchYearOption is false
                    if (option != SortOption.LAUNCH_YEAR || showLaunchYearOption) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .selectable(
                                    selected = (option == selectedOption),
                                    onClick = { selectedOption = option },
                                    role = Role.RadioButton
                                )
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (option == selectedOption),
                                onClick = null // null because we're handling the click on the row
                            )
                            Text(
                                text = stringResource(option.displayNameResId),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(start = 16.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { 
                    onSortOptionSelected(selectedOption)
                    onDismiss()
                },
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Okay")
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(28.dp)
    )
}

