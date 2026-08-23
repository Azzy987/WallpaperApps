package com.droidates.wallpapers.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.droidates.wallpapers.utils.LocalNetworkUtils
import com.droidates.wallpapers.utils.NetworkUtils

/**
 * Pull to refresh component that shows a network status bar and refresh indicator
 */
@OptIn(ExperimentalMaterialApi::class)
@Composable
fun PullToRefreshContent(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    enabled: Boolean = true,
    networkUtils: NetworkUtils = LocalNetworkUtils.current,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    // Pull to refresh state
    val pullRefreshState = rememberPullRefreshState(
        refreshing = isRefreshing,
        onRefresh = onRefresh
    )
    
    // Get network status
    val isConnected by networkUtils.isConnected.collectAsState()
    val isSlowConnection by networkUtils.isSlowConnection.collectAsState()
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .pullRefresh(pullRefreshState, enabled = enabled)
    ) {
        // Main content
        content()
        
        // Network status bar at the top
        NetworkStatusBar(
            isConnected = isConnected,
            isSlowConnection = isSlowConnection,
            modifier = Modifier.align(Alignment.TopCenter)
        )
        
        // Standard pull-to-refresh indicator that animates while dragging
        PullRefreshIndicator(
            refreshing = isRefreshing,
            state = pullRefreshState,
            modifier = Modifier.align(Alignment.TopCenter),
            backgroundColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary
        )
    }
} 