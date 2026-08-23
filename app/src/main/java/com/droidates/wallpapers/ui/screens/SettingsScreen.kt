package com.droidates.wallpapers.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.droidates.wallpapers.R
import com.droidates.wallpapers.navigation.NavigationState
import com.droidates.wallpapers.ui.theme.ThemeMode
import com.droidates.wallpapers.viewmodel.AuthViewModel
import com.droidates.wallpapers.viewmodel.SettingsViewModel
import android.content.Intent
import android.os.Build
import android.net.Uri
import com.droidates.wallpapers.LocalToastManager
import com.droidates.wallpapers.utils.LocalAdManager
import android.util.Log
import androidx.compose.material.icons.rounded.WorkspacePremium
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.ChevronRight
import com.droidates.wallpapers.ui.components.ContentDialog
import com.droidates.wallpapers.utils.PolicyContent
import android.widget.Toast
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import com.droidates.wallpapers.ui.components.FeatureRequestDialog
import com.droidates.wallpapers.utils.RefreshRateManager
import android.app.Activity
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.droidates.wallpapers.config.AppConfig
import com.droidates.wallpapers.viewmodel.PremiumViewModel
import kotlinx.coroutines.delay
import androidx.compose.material3.LinearProgressIndicator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navigationState: NavigationState,
    viewModel: SettingsViewModel = hiltViewModel(),
    authViewModel: AuthViewModel = hiltViewModel(),
    premiumViewModel: PremiumViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val refreshRateManager = remember { RefreshRateManager(context) }
    val themeMode by viewModel.themeMode.collectAsState()
    val isSignedIn by authViewModel.isSignedIn.collectAsState()
    val userData by authViewModel.userData.collectAsState()
    val adManager = LocalAdManager.current
    val isPremiumUser by adManager.isPremiumUser.collectAsState()
    val purchaseState by premiumViewModel.purchaseState.collectAsState() // Track purchase state
    val toastManager = LocalToastManager.current
    
    // Add states for dialogs
    val showPrivacyPolicyDialog by viewModel.showPrivacyPolicyDialog.collectAsState()
    val showTermsOfUseDialog by viewModel.showTermsOfUseDialog.collectAsState()
    val showLicensesDialog by viewModel.showLicensesDialog.collectAsState()
    val showChangelogDialog by viewModel.showChangelogDialog.collectAsState()
    

    // Read feature request dialog states
    val showFeatureRequestDialog by viewModel.showFeatureRequestDialog.collectAsState()
    val isSubmittingFeatureRequest by viewModel.isSubmittingFeatureRequest.collectAsState()
    
    // Add state for refresh rate dialog
    var showRefreshRateDialog by remember { mutableStateOf(false) }
    
    // Get available refresh rates and current refresh rate
    val availableRefreshRates by remember { mutableStateOf(refreshRateManager.availableRefreshRates.value) }
    var currentRefreshRate by remember { mutableStateOf(refreshRateManager.currentRefreshRate.value) }
    
    // Update UI when refresh rate changes
    LaunchedEffect(Unit) {
        refreshRateManager.currentRefreshRate.collect { rate ->
            currentRefreshRate = rate
        }
    }
    
    // FIXED: Simple loading state that reacts to premium status changes
    var isPremiumStatusLoading by remember { mutableStateOf(false) }
    
    // Handle purchase state changes and other premium status updates
    LaunchedEffect(purchaseState) {
        when (purchaseState) {
            is PremiumViewModel.PurchaseState.Success -> {
                isPremiumStatusLoading = true
                
                // Refresh user data for successful purchases
                withContext(Dispatchers.IO) {
                    authViewModel.refreshUserData(forceServerFetch = true)
                }
                
                // Wait briefly then stop loading
                delay(1500)
                isPremiumStatusLoading = false
            }
            is PremiumViewModel.PurchaseState.InProgress -> {
                isPremiumStatusLoading = true
            }
            else -> {
                isPremiumStatusLoading = false
            }
        }
    }
    
    // FIXED: Also react to premium status changes directly
    LaunchedEffect(isPremiumUser) {
        // Brief loading state when premium status changes
        if (isPremiumStatusLoading) {
            delay(500)
            isPremiumStatusLoading = false
        }
    }
    
    // First launch effect to ensure user data is refreshed when the screen is opened
    LaunchedEffect(Unit) {
        if (isSignedIn) {
            authViewModel.refreshUserData() // Force refresh user data when settings screen opens
            adManager.checkPremiumStatus() // Also check premium status
        }
    }

    // Content & Data
    val cacheSize by viewModel.cacheSize.collectAsState()
    
    // Update cache size when the screen is shown
    LaunchedEffect(Unit) {
        viewModel.updateCacheSize(context)
    }
    
    // Check notification permission status
    val notificationPermissionGranted = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Always true for older Android versions
        }
    }

    // Permission request launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            toastManager.showToast(
                message = "Notification permission granted",
                icon = Icons.Default.Notifications
            )
        }
    }

    // Apply the preferred refresh rate to the current activity
    LaunchedEffect(currentRefreshRate) {
        (context as? Activity)?.window?.let { window ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                refreshRateManager.applyPreferredRefreshRate(window)
            } else {
                refreshRateManager.applyPreferredRefreshRateForOlderVersions(window)
            }
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = { navigationState.navigateBack() }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Profile Card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    shape = RoundedCornerShape(24.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Profile image with loading state
                        if (isSignedIn) {
                            // Only show loading state once during initial load, not on every refresh
                            if (userData?.photoUrl != null) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(userData?.photoUrl)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = "Profile picture",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(80.dp)
                                        .clip(CircleShape)
                                )
                            } else {
                                Image(
                                    imageVector = Icons.Default.AccountCircle,
                                    contentDescription = "Profile picture",
                                    modifier = Modifier
                                        .size(80.dp)
                                        .background(
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                            shape = CircleShape
                                        )
                                        .padding(16.dp)
                                )
                            }
                        } else {
                            Image(
                                imageVector = Icons.Default.AccountCircle,
                                contentDescription = "Profile picture",
                                modifier = Modifier
                                    .size(80.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                        shape = CircleShape
                                    )
                                    .padding(16.dp)
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        // User name or Guest - no loading state to prevent refresh
                        if (isSignedIn) {
                                // Show name with premium badge if applicable
                                if (isPremiumUser) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = userData?.displayName ?: "User",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        
                                        Spacer(modifier = Modifier.width(8.dp))
                                        
                                        // Premium badge
                                        Box(
                                            modifier = Modifier
                                                .size(20.dp)
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
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                } else {
                                    Text(
                                        text = userData?.displayName ?: "User",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                
                                // Show email
                                if (userData?.email?.isNotEmpty() == true) {
                                    Text(
                                        text = userData?.email ?: "",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                        textAlign = TextAlign.Center
                                    )
                            }
                        } else {
                            Text(
                                text = "Guest User",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        // Sign in/out button
                        Button(
                            onClick = {
                                if (isSignedIn) {
                                    authViewModel.signOut()
                                } else {
                                    authViewModel.signIn()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSignedIn) 
                                    MaterialTheme.colorScheme.error 
                                else 
                                    MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text(if (isSignedIn) "Sign Out" else "Sign In with Google")
                        }
                    }
                }
            }
            
            // Premium Card - FIXED: properly handle sign out case
            item {
                // Show loading state if premium status is being updated
                if (isPremiumStatusLoading) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Updating premium status...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    // FIXED: Check both signed in status AND premium status
                    if (isSignedIn && isPremiumUser) {
                    // Show Manage Subscription card for premium users
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { navigationState.navigateToPremium() },
                        shape = RoundedCornerShape(24.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Verified,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(48.dp)
                            )
                            
                            Spacer(modifier = Modifier.width(16.dp))
                            
                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Premium Active",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    
                                    Spacer(modifier = Modifier.width(8.dp))
                                    
                                    // Premium badge
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .background(
                                                color = MaterialTheme.colorScheme.primary,
                                                shape = CircleShape
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.WorkspacePremium,
                                            contentDescription = "Premium",
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                                
                                Spacer(modifier = Modifier.height(4.dp))
                                
                                // Show the plan type (Monthly, Yearly, Lifetime)
                                Text(
                                    text = "Plan: ${userData?.premiumType?.replaceFirstChar { it.uppercase() } ?: "Premium"}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                )
                                
                                // Show subscription dates (active since, expires on)
                                userData?.premiumSince?.let { since ->
                                    val dateFormat = java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault())
                                    Text(
                                        text = "Active since: ${dateFormat.format(since)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                    )
                                }
                                
                                // Show expiry date for non-lifetime subscriptions
                                if (userData?.premiumType != "lifetime") {
                                    userData?.premiumExpiry?.let { expiry ->
                                        val dateFormat = java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault())
                                        Text(
                                            text = "Expires on: ${dateFormat.format(expiry)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                        )
                                    }
                                }
                            }
                            
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                        }
                    }
                } else {
                        // FIXED: Show upgrade card when user is not signed in OR not premium
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { navigationState.navigateToPremium() },
                        shape = RoundedCornerShape(24.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                        colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.WorkspacePremium,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(48.dp)
                            )
                            
                            Spacer(modifier = Modifier.width(16.dp))
                            
                                Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Upgrade to Premium",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                
                                Spacer(modifier = Modifier.height(4.dp))
                                
                                Text(
                                    text = "Unlock all exclusive wallpapers and features",
                                    style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                                )
                            }
                            
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                            )
                            }
                        }
                    }
                }
            }
            
            // Appearance Settings
            item {
                Column {
                    // MATERIAL 3 EXPRESSIVE: Section title outside container with accent color
                    Text(
                        text = "Display",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
                    )
                    
                    // Theme Selection in grouped container (first item - rounded top)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(
                            topStart = 24.dp,
                            topEnd = 24.dp,
                            bottomStart = 8.dp,
                            bottomEnd = 8.dp
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp)
                        ) {
                            // Theme Selection with Button Groups
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Palette,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                
                                Text(
                                    text = "Theme",
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(start = 16.dp)
                                )
                            }
                            
                            // Connected theme buttons with Material 3 button group colors
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 40.dp), // Align with text above
                                horizontalArrangement = Arrangement.spacedBy(6.dp) // Increased spacing
                            ) {
                                // Auto Theme Button (Left capsule)
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { viewModel.setThemeMode(ThemeMode.SYSTEM) }
                                        .background(
                                            color = if (themeMode == ThemeMode.SYSTEM) 
                                                MaterialTheme.colorScheme.primary // Sign in button color for selected
                                            else MaterialTheme.colorScheme.secondaryContainer, // Upgrade to premium card color for unselected
                                            shape = if (themeMode == ThemeMode.SYSTEM)
                                                RoundedCornerShape(24.dp) // Full capsule when selected
                                            else RoundedCornerShape(
                                                topStart = 24.dp,
                                                bottomStart = 24.dp,
                                                topEnd = 8.dp,
                                                bottomEnd = 8.dp
                                            )
                                        )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 12.dp, horizontal = 8.dp),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.Brightness6,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                            tint = if (themeMode == ThemeMode.SYSTEM) 
                                                Color.White // White text for selected
                                            else MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Auto",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Medium,
                                            color = if (themeMode == ThemeMode.SYSTEM) 
                                                Color.White // White text for selected
                                            else MaterialTheme.colorScheme.onSecondaryContainer,
                                            maxLines = 1
                                        )
                                    }
                                }
                                
                                // Light Theme Button (Middle with slight rounding)
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { viewModel.setThemeMode(ThemeMode.LIGHT) }
                                        .background(
                                            color = if (themeMode == ThemeMode.LIGHT) 
                                                MaterialTheme.colorScheme.primary // Sign in button color for selected
                                            else MaterialTheme.colorScheme.secondaryContainer, // Upgrade to premium card color for unselected
                                            shape = if (themeMode == ThemeMode.LIGHT)
                                                RoundedCornerShape(24.dp) // Full capsule when selected
                                            else RoundedCornerShape(8.dp)
                                        )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 12.dp, horizontal = 8.dp),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.BrightnessHigh,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                            tint = if (themeMode == ThemeMode.LIGHT) 
                                                Color.White // White text for selected
                                            else MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Light",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Medium,
                                            color = if (themeMode == ThemeMode.LIGHT) 
                                                Color.White // White text for selected
                                            else MaterialTheme.colorScheme.onSecondaryContainer,
                                            maxLines = 1
                                        )
                                    }
                                }
                                
                                // Dark Theme Button (Right capsule)
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { viewModel.setThemeMode(ThemeMode.DARK) }
                                        .background(
                                            color = if (themeMode == ThemeMode.DARK) 
                                                MaterialTheme.colorScheme.primary // Sign in button color for selected
                                            else MaterialTheme.colorScheme.secondaryContainer, // Upgrade to premium card color for unselected
                                            shape = if (themeMode == ThemeMode.DARK)
                                                RoundedCornerShape(24.dp) // Full capsule when selected
                                            else RoundedCornerShape(
                                                topStart = 8.dp,
                                                bottomStart = 8.dp,
                                                topEnd = 24.dp,
                                                bottomEnd = 24.dp
                                            )
                                        )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 12.dp, horizontal = 8.dp),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.BrightnessLow,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                            tint = if (themeMode == ThemeMode.DARK) 
                                                Color.White // White text for selected
                                            else MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Dark",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Medium,
                                            color = if (themeMode == ThemeMode.DARK) 
                                                Color.White // White text for selected
                                            else MaterialTheme.colorScheme.onSecondaryContainer,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }
                    
                    // Small spacing between grouped containers
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Refresh Rate in grouped container (last item - rounded bottom)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(
                            topStart = 8.dp,
                            topEnd = 8.dp,
                            bottomStart = 24.dp,
                            bottomEnd = 24.dp
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp)
                        ) {
                            SettingsItemRow(
                                item = SettingsItem(
                                    title = "Refresh Rate",
                                    icon = Icons.Default.Speed,
                                    subtitle = when (currentRefreshRate) {
                                        RefreshRateManager.REFRESH_RATE_AUTO -> "Auto (System Default)"
                                        RefreshRateManager.REFRESH_RATE_60HZ -> "60Hz"
                                        RefreshRateManager.REFRESH_RATE_90HZ -> "90Hz"
                                        RefreshRateManager.REFRESH_RATE_120HZ -> "120Hz"
                                        else -> "${currentRefreshRate.toInt()}Hz"
                                    },
                                    onClick = { showRefreshRateDialog = true }
                                )
                            )
                        }
                    }
                }
            }
            
            // Content & Data
            item {
                Column {
                    // Section title outside container with accent color
                    Text(
                        text = "Content & Data",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
                    )
                    
                    // Clear Cache (first item - rounded top)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(
                            topStart = 24.dp,
                            topEnd = 24.dp,
                            bottomStart = 8.dp,
                            bottomEnd = 8.dp
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp)
                        ) {
                            SettingsItemRow(
                                item = SettingsItem(
                                    icon = Icons.Default.DeleteSweep,
                                    title = "Clear Cache",
                                    subtitle = "Clear temporary files ($cacheSize)",
                                    onClick = { 
                                        viewModel.clearCache(context)
                                    }
                                )
                            )
                        }
                    }
                    
                    // Small spacing between grouped containers
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Sync Favorites (last item - rounded bottom)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(
                            topStart = 8.dp,
                            topEnd = 8.dp,
                            bottomStart = 24.dp,
                            bottomEnd = 24.dp
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp)
                        ) {
                            SettingsItemRow(
                                item = SettingsItem(
                                    icon = Icons.Default.Sync,
                                    title = "Sync Favorites",
                                    subtitle = "Sync favorites across devices",
                                    onClick = { 
                                        if (isSignedIn) {
                                            viewModel.syncFavoritesViaRepository(context)
                                        } else {
                                            authViewModel.signIn()
                                        }
                                    }
                                )
                            )
                        }
                    }
                }
            }
            
            // Notification permission card - only show on Android 13+ (API 33+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                item {
                    Column {
                        // MATERIAL 3 EXPRESSIVE: Section title outside container with accent color
                        Text(
                            text = "Notifications",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
                        )
                        
                        // MATERIAL 3 EXPRESSIVE: Enhanced container
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(24.dp), // More rounded corner radius
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp) // Increased padding
                            ) {
                            
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (!notificationPermissionGranted) {
                                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                        } else {
                                            // Show toast when already enabled
                                            Toast.makeText(
                                                context,
                                                "Notifications are enabled. To disable, go to app settings.",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (notificationPermissionGranted) 
                                        Icons.Default.NotificationsActive 
                                    else 
                                        Icons.Default.Notifications,
                                    contentDescription = null,
                                    tint = if (notificationPermissionGranted)
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    else
                                        MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(start = 16.dp)
                                ) {
                                    Text(
                                        text = if (notificationPermissionGranted) 
                                            "Notifications Enabled" 
                                        else 
                                            "Enable Notifications",
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    
                                    Text(
                                        text = if (notificationPermissionGranted) {
                                            "Receive updates about new wallpapers"
                                        } else {
                                            "Get notified about new wallpapers and features"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                    )
                                }
                                
                                if (!notificationPermissionGranted) {
                                    Icon(
                                        imageVector = Icons.Default.ChevronRight,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
            
            // Info & More
            item {
                Column {
                    // Section title outside container with accent color
                    Text(
                        text = "Info & More",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
                    )
                    
                    // Rate App (first item - rounded top)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(
                            topStart = 24.dp,
                            topEnd = 24.dp,
                            bottomStart = 8.dp,
                            bottomEnd = 8.dp
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            SettingsItemRow(
                                item = SettingsItem(
                                    icon = Icons.Default.Star,
                                    title = "Rate App",
                                    subtitle = "Rate us on Google Play",
                                    onClick = { viewModel.openPlayStore(context) }
                                )
                            )
                        }
                    }
                    
                    // Middle items with consistent corners
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            SettingsItemRow(
                                item = SettingsItem(
                                    icon = Icons.Default.Share,
                                    title = "Share App",
                                    subtitle = "Share with friends",
                                    onClick = { viewModel.shareApp(context) }
                                )
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            SettingsItemRow(
                                item = SettingsItem(
                                    icon = Icons.Default.Apps,
                                    title = "More from Droidates",
                                    subtitle = "Discover our other apps",
                                    onClick = { viewModel.openDeveloperPage(context) }
                                )
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            SettingsItemRow(
                                item = SettingsItem(
                                    icon = Icons.Default.PrivacyTip,
                                    title = "Privacy Policy",
                                    onClick = { viewModel.showPrivacyPolicy() }
                                )
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            SettingsItemRow(
                                item = SettingsItem(
                                    icon = Icons.Default.Description,
                                    title = "Terms of Use",
                                    onClick = { viewModel.showTermsOfUse() }
                                )
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            SettingsItemRow(
                                item = SettingsItem(
                                    icon = Icons.Filled.NewReleases,
                                    title = stringResource(R.string.request_feature),
                                    subtitle = stringResource(R.string.request_feature_description),
                                    onClick = { viewModel.showFeatureRequest() }
                                )
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            SettingsItemRow(
                                item = SettingsItem(
                                    icon = Icons.AutoMirrored.Filled.Help,
                                    title = "Report Issues",
                                    subtitle = "Help us improve the app",
                                    onClick = {
                                        val deviceInfo = viewModel.getDeviceInfo()
                                        val intent = Intent(Intent.ACTION_SENDTO).apply {
                                            data = Uri.parse("mailto:${AppConfig.SUPPORT_EMAIL}")
                                            putExtra(Intent.EXTRA_SUBJECT, "${AppConfig.APP_NAME}: Issue Report")
                                            putExtra(Intent.EXTRA_TEXT, """
                                                Describe the issue you're experiencing:
                                                [Please type your issue here]

                                                ---------------------
                                                Device Information (auto-filled):
                                                $deviceInfo
                                            """.trimIndent())
                                        }
                                        context.startActivity(intent)
                                    }
                                )
                            )
                        }
                    }
                    
                    // About (last item - rounded bottom)
                    Spacer(modifier = Modifier.height(4.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(
                            topStart = 8.dp,
                            topEnd = 8.dp,
                            bottomStart = 24.dp,
                            bottomEnd = 24.dp
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            SettingsItemRow(
                                item = SettingsItem(
                                    icon = Icons.Default.Info,
                                    title = "About",
                                    subtitle = "Licenses & Credits",
                                    onClick = { viewModel.showLicenses() }
                                )
                            )
                        }
                    }
                }
            }
            
            // Version info
            item {
                Text(
                    text = "Version ${viewModel.appVersion}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.showChangelog() }
                        .padding(vertical = 16.dp),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
    
    
    // Refresh rate dialog
    if (showRefreshRateDialog) {
        RefreshRateDialog(
            availableRefreshRates = availableRefreshRates,
            selectedRefreshRate = currentRefreshRate,
            onRefreshRateSelected = { rate ->
                refreshRateManager.setPreferredRefreshRate(rate)
                // Apply immediately to current window
                (context as? Activity)?.window?.let { window ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        refreshRateManager.applyPreferredRefreshRate(window)
                    } else {
                        refreshRateManager.applyPreferredRefreshRateForOlderVersions(window)
                    }
                }
            },
            onDismiss = { showRefreshRateDialog = false }
        )
    }
    
    // Privacy Policy Dialog
    if (showPrivacyPolicyDialog) {
        ContentDialog(
            title = "Privacy Policy",
            content = PolicyContent.privacyPolicy,
            onDismiss = { viewModel.dismissPrivacyPolicy() }
        )
    }
    
    // Terms of Use Dialog
    if (showTermsOfUseDialog) {
        ContentDialog(
            title = "Terms of Use",
            content = PolicyContent.termsOfUse,
            onDismiss = { viewModel.dismissTermsOfUse() }
        )
    }
    
    // Licenses & Credits Dialog
    if (showLicensesDialog) {
        ContentDialog(
            title = "Licenses & Credits",
            content = PolicyContent.licensesAndCredits,
            onDismiss = { viewModel.dismissLicenses() }
        )
    }
    
    // Changelog Dialog
    if (showChangelogDialog) {
        ContentDialog(
            title = "Changelog",
            content = PolicyContent.changelog,
            onDismiss = { viewModel.dismissChangelog() }
        )
    }
    
    // Feature Request Dialog
    if (showFeatureRequestDialog) {
        val successMessage = stringResource(R.string.feature_request_submit_success)
        val errorMessage = stringResource(R.string.feature_request_submit_error)
        
        FeatureRequestDialog(
            isSubmitting = isSubmittingFeatureRequest,
            onDismiss = { viewModel.dismissFeatureRequest() },
            onSubmit = { title, description ->
                viewModel.submitFeatureRequest(
                    title = title,
                    description = description,
                    onSuccess = {
                        toastManager.showToast(
                            message = successMessage,
                            icon = Icons.Default.NewReleases
                        )
                    },
                    onError = { error ->
                        toastManager.showToast(
                            message = error.ifEmpty { errorMessage },
                            icon = Icons.Default.Error
                        )
                    }
                )
            }
        )
    }
}


data class SettingsItem(
    val icon: ImageVector,
    val title: String,
    val subtitle: String? = null,
    val onClick: () -> Unit
)

@Composable
fun SettingsSection(
    title: String,
    items: List<SettingsItem>
) {
    Column {
        // MATERIAL 3 EXPRESSIVE: Section title outside container with accent color
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
        )
        
        // MATERIAL 3 EXPRESSIVE: Enhanced container with increased corner radius
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp), // More rounded corner radius
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(20.dp) // Increased padding for better visual breathing
            ) {
                items.forEachIndexed { index, item ->
                    SettingsItemRow(item = item)
                    
                    // MATERIAL 3 EXPRESSIVE: Replace dividers with spacing
                    if (index < items.size - 1) {
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsItemRow(item: SettingsItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = item.onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp)
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyLarge
            )
            
            if (item.subtitle != null) {
                Text(
                    text = item.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        }
        
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            modifier = Modifier.size(20.dp)
        )
    }
}


/**
 * Dialog for refresh rate selection
 */
@Composable
fun RefreshRateDialog(
    availableRefreshRates: List<Float>,
    selectedRefreshRate: Float,
    onRefreshRateSelected: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    var tempSelectedRefreshRate by remember { mutableStateOf(selectedRefreshRate) }
    
    // Update the temp selected rate if the passed in selected rate changes
    LaunchedEffect(selectedRefreshRate) {
        tempSelectedRefreshRate = selectedRefreshRate
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Choose Refresh Rate",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
            ) {
                availableRefreshRates.forEach { rate ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = { tempSelectedRefreshRate = rate })
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = tempSelectedRefreshRate == rate,
                            onClick = { tempSelectedRefreshRate = rate }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = when (rate) {
                                RefreshRateManager.REFRESH_RATE_AUTO -> "Auto (System Default)"
                                RefreshRateManager.REFRESH_RATE_60HZ -> "60Hz"
                                RefreshRateManager.REFRESH_RATE_90HZ -> "90Hz"
                                RefreshRateManager.REFRESH_RATE_120HZ -> "120Hz"
                                else -> "${rate.toInt()}Hz"
                            },
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { 
                onRefreshRateSelected(tempSelectedRefreshRate)
                onDismiss()
            }) {
                Text("Apply")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}