package com.droidates.wallpapers.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.droidates.wallpapers.model.Category
import com.droidates.wallpapers.navigation.NavigationState
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import com.droidates.wallpapers.utils.ImageUtils
import java.util.Locale
// Removed accompanist placeholder imports - using built-in alternatives

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CategoryCard(
    category: Category,
    navigationState: NavigationState,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1.8f)
            .padding(16.dp)
    ) {
        // Bottom card (shadow effect)
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .offset(y = 32.dp, x = 16.dp)
                .graphicsLayer {
                    rotationZ = -5f
                    alpha = 0.3f
                },
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {}

        // Middle card
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .offset(y = 16.dp, x = 8.dp)
                .graphicsLayer {
                    rotationZ = -2.5f
                    alpha = 0.6f
                },
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {}

        // Main card
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .clickable { navigationState.navigateToCategory(category.name) }
                .graphicsLayer {
                    rotationZ = 0f
                    shadowElevation = 16.dp.toPx()
                },
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Track image loading state for shimmer effect
                var isImageLoaded by remember { mutableStateOf(false) }
                
                // Use AsyncImage with simple loading state
                AsyncImage(
                    model = ImageUtils.createThumbnailRequest(
                        context = LocalContext.current,
                        url = category.thumbnail,
                        thumbnailUrl = category.thumbnail
                    ),
                    contentDescription = category.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    onLoading = { isImageLoaded = false },
                    onSuccess = { isImageLoaded = true },
                    onError = { isImageLoaded = true }
                )
                
                // Blue gradient loading background (same as DetailScreen)
                if (!isImageLoaded) {
                    val gradientBrush = remember {
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF1A1A2E), // Deep blue
                                Color(0xFF16213E), // Navy blue
                                Color(0xFF0F3460), // Dark blue
                                Color(0xFF533483)  // Purple-blue
                            )
                        )
                    }
                    
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(24.dp))
                            .background(gradientBrush),
                        contentAlignment = Alignment.Center
                    ) {
                        // Material 3 Expressive Loading Indicator
                        LoadingIndicator(
                            modifier = Modifier.size(40.dp),
                            color = Color.White.copy(alpha = 0.9f)
                        )
                    }
                }
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.7f)
                                )
                            )
                        )
                        .padding(vertical = 24.dp, horizontal = 8.dp)
                ) {
                    Text(
                        text = category.name.uppercase(Locale.getDefault()),
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
        }
    }
} 