package com.droidates.wallpapers.core.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import com.droidates.wallpapers.core.navigation.NavigationState
import com.droidates.wallpapers.core.utils.AdManager
import com.droidates.wallpapers.core.utils.LocalAdManager

/**
 * A component that watches the AdManager and shows the UpgradePromptDialog when needed.
 * This should be placed at the root of the app.
 */
@Composable
fun UpgradePromptHandler(
    navigationState: NavigationState,
    adManager: AdManager = LocalAdManager.current
) {
    val showUpgradePrompt by adManager.showUpgradePrompt.collectAsState()
    
    if (showUpgradePrompt) {
        UpgradePromptDialog(
            onDismiss = { adManager.dismissUpgradePrompt() },
            onUpgrade = {
                adManager.dismissUpgradePrompt()
                navigationState.navigateToPremium()
            }
        )
    }
} 