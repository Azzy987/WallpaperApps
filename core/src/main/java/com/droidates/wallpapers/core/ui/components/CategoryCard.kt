package com.droidates.wallpapers.core.ui.components

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
import coil.request.ImageRequest
import coil.size.Scale
import coil.size.Size
import com.droidates.wallpapers.core.model.Category
import com.droidates.wallpapers.core.navigation.NavigationState
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import java.util.Locale
// Removed accompanist placeholder imports - using built-in alternatives

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CategoryCard(
    category: Category,
    navigationState: NavigationState,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val cardShape = RoundedCornerShape(24.dp)
    val imageRequest = remember(category.thumbnail) {
        ImageRequest.Builder(context)
            .data(category.thumbnail)
            .crossfade(true)
            .size(Size(720, 400))
            .scale(Scale.FILL)
            .build()
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1.8f)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .clickable { navigationState.navigateToCategory(category.name) },
            shape = cardShape,
            color = MaterialTheme.colorScheme.surface
            ,
            tonalElevation = 2.dp,
            shadowElevation = 4.dp
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Track image loading state for shimmer effect
                var isImageLoaded by remember { mutableStateOf(false) }
                
                // Use AsyncImage with simple loading state
                AsyncImage(
                    model = imageRequest,
                    contentDescription = category.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(cardShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    onLoading = { isImageLoaded = false },
                    onSuccess = { isImageLoaded = true },
                    onError = { isImageLoaded = true }
                )
                
                // Use a lightweight static placeholder to avoid per-item animation cost while scrolling.
                if (!isImageLoaded) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(cardShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f))
                    )
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