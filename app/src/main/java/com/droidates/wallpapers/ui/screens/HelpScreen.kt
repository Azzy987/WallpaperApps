package com.droidates.wallpapers.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.droidates.wallpapers.navigation.NavigationState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpScreen(navigationState: NavigationState) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Help") },
                navigationIcon = {
                    IconButton(onClick = { navigationState.navigateBack() }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            // Content will be added later
        }
    }
} 