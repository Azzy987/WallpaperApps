package com.droidates.wallpapers.core.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.droidates.wallpapers.core.utils.LocalNetworkUtils
import com.droidates.wallpapers.core.utils.NetworkUtils

/**
 * Pull to refresh component that shows a network status bar and refresh indicator
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PullToRefreshContent(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    enabled: Boolean = true,
    networkUtils: NetworkUtils = LocalNetworkUtils.current,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    // Get network status
    val isConnected by networkUtils.isConnected.collectAsState()
    val isSlowConnection by networkUtils.isSlowConnection.collectAsState()
    
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = { if (enabled) onRefresh() },
        state = rememberPullToRefreshState(),
        modifier = modifier.fillMaxSize()
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            content()
            // Network status bar at the top
            NetworkStatusBar(
                isConnected = isConnected,
                isSlowConnection = isSlowConnection,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    }
} 