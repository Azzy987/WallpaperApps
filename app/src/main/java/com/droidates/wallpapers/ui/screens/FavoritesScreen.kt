package com.droidates.wallpapers.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Sync
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.runtime.snapshotFlow
import com.droidates.wallpapers.R
import com.droidates.wallpapers.viewmodel.AuthViewModel
import com.droidates.wallpapers.viewmodel.FavoritesViewModel
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FavoritesScreen(
    modifier: Modifier = Modifier,
    scrollBehavior: TopAppBarScrollBehavior,
    authViewModel: AuthViewModel = hiltViewModel(),
    favoritesViewModel: FavoritesViewModel = hiltViewModel()
) {
    val isSignedIn by authViewModel.isSignedIn.collectAsState()
    val signInState by authViewModel.signInState.collectAsState(initial = AuthViewModel.SignInState.Idle)
    val favorites by favoritesViewModel.favoriteWallpapers.collectAsState()
    val isSyncing by favoritesViewModel.isSyncing.collectAsState(initial = false)
    val hasFavorites = favorites.isNotEmpty()
    
    // State for FAB visibility based on scroll
    val listState = rememberLazyGridState()
    
    // Show sign in button only if:
    // 1. User is not signed in
    // 2. Not scrolled down
    val showSignInButton = remember {
        derivedStateOf {
            !isSignedIn && 
            listState.firstVisibleItemIndex <= 2 // Show only at the top of the list
        }
    }
    
    // Show sync button only if:
    // 1. User is signed in
    // 2. Has favorites
    val showSyncButton = remember {
        derivedStateOf {
            isSignedIn && 
            hasFavorites && 
            !isSyncing
        }
    }
    
    // Refresh auth state when screen is focused
    LaunchedEffect(Unit) {
        if (isSignedIn) {
            authViewModel.refreshUserData()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (hasFavorites) {
            // Show favorites grid
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(8.dp),
                state = listState,
                modifier = Modifier.fillMaxSize()
            ) {
                items(favorites.size) { index ->
                    // Display favorite wallpaper items
                    // This is just a placeholder - in a real implementation, use your wallpaper card component
                    Box(
                        modifier = Modifier
                            .padding(8.dp)
                            .height(200.dp)
                    ) {
                        // Your wallpaper card goes here
                        Text("Favorite ${index + 1}")
                    }
                }
            }
            
            // Show sync button if signed in and has favorites
            AnimatedVisibility(
                visible = isSignedIn && hasFavorites && !isSyncing,
                enter = fadeIn() + slideInVertically { it },
                exit = fadeOut() + slideOutVertically { it },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
            ) {
                FloatingActionButton(
                    onClick = { 
                        favoritesViewModel.syncFavoritesToCloud() 
                    },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    if (isSyncing) {
                        LoadingIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = "Sync Favorites"
                        )
                    }
                }
            }
        } else {
            // Show empty state
            EmptyFavoritesScreen(isSignedIn)
        }
        
        // Sign in FAB with Google icon - only show when needed
        AnimatedVisibility(
            visible = showSignInButton.value,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
        ) {
            FloatingActionButton(
                onClick = { authViewModel.signIn() },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                if (signInState is AuthViewModel.SignInState.Loading) {
                    LoadingIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    ) {
                        // Use Google icon from resource
                        Icon(
                            imageVector = ImageVector.vectorResource(id = R.drawable.google),
                            contentDescription = "Google Sign In"
                        )
                        Text("Sign in with Google")
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyFavoritesScreen(isSignedIn: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_favorite_rounded_outlined),
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = if (isSignedIn) "No favorites yet" else "Sign in to save favorites",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )
        
        if (!isSignedIn) {
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedButton(
                onClick = { /* Sign-in action handled by FAB */ },
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = ImageVector.vectorResource(id = R.drawable.google),
                        contentDescription = "Google Sign In",
                        modifier = Modifier.size(18.dp)
                    )
                    Text("Sign in with Google")
                }
            }
        }
    }
} 