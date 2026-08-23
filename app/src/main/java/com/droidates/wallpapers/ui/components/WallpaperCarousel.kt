package com.droidates.wallpapers.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.*
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import com.droidates.wallpapers.utils.toSafeScale
import com.droidates.wallpapers.utils.toSafeAlpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import com.droidates.wallpapers.model.Banner
import com.droidates.wallpapers.navigation.NavigationState
// Removed accompanist placeholder imports - using built-in alternatives
import com.droidates.wallpapers.utils.ImageUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import android.util.Log
import androidx.compose.foundation.layout.Row
import java.util.*
import kotlin.math.absoluteValue
import kotlinx.coroutines.launch
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WallpaperCarousel(
    banners: List<Banner>,
    navigationState: NavigationState,
) {
    val context = LocalContext.current
    val adManager = com.droidates.wallpapers.utils.LocalAdManager.current

    if (banners.isNotEmpty()) {
        val pagerState = rememberPagerState(initialPage = 2) { banners.size }
        val coroutineScope = rememberCoroutineScope()

        // Auto-scroll with smoother animation
        LaunchedEffect(pagerState) {
            try {
                while (true) {
                    delay(6000)
                    val nextPage = (pagerState.currentPage + 1) % banners.size
                    pagerState.animateScrollToPage(
                        page = nextPage,
                        animationSpec = tween(
                            durationMillis = 500,
                            easing = FastOutSlowInEasing
                        )
                    )
                }
            } catch (e: CancellationException) {
                // Normal cancellation, ignore
            } catch (e: Exception) {
                Log.e("WallpaperCarousel", "Error during auto-scroll", e)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            HorizontalPager(
                state = pagerState,
                pageSpacing = 8.dp,
                contentPadding = PaddingValues(horizontal = 32.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            ) { page ->
                // Simple scaling for current page without 3D rotation
                // CRASH FIX: Add NaN safety check for Android 9 compatibility
                val pageOffset = try {
                    (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                } catch (e: Exception) {
                    0f
                }

                // Calculate scale and alpha with built-in safety
                val scale = lerp(
                    start = 0.9f,
                    stop = 1f,
                    fraction = 1f - pageOffset.absoluteValue.coerceIn(0f, 1f)
                ).toSafeScale(min = 0.8f, max = 1.1f)

                val alpha = lerp(
                    start = 0.7f,
                    stop = 1f,
                    fraction = 1f - pageOffset.absoluteValue.coerceIn(0f, 1f)
                ).toSafeAlpha()

                val isAppPromo = banners[page].bannerType == "app_promo"

                Card(
                    modifier = Modifier
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            this.alpha = alpha
                        }
                        .fillMaxSize()
                        .clickable {
                            if (isAppPromo) {
                                val intent =
                                    Intent(Intent.ACTION_VIEW, Uri.parse(banners[page].appUrl))
                                context.startActivity(intent)
                            } else {
                                banners[page].wallpaperId.let { id ->
                                    val activity = context.findActivity()
                                    if (activity != null) {
                                        coroutineScope.launch {
                                            adManager.navigateWithAdCheck(activity) {
                                                navigationState.navigateToDetail(id, "home")
                                            }
                                        }
                                    } else {
                                        navigationState.navigateToDetail(id, "home")
                                    }
                                }
                            }
                        },
                    shape = RoundedCornerShape(28.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Box {
                        var isImageLoaded by remember { mutableStateOf(false) }

                        // Background image
                        AsyncImage(
                            model = ImageUtils.createProgressiveImageRequest(
                                context = LocalContext.current,
                                url = banners[page].bannerUrl,
                                thumbnailUrl = banners[page].bannerUrl,
                                prioritize = true
                            ),
                            contentDescription = banners[page].bannerName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                            onLoading = { isImageLoaded = false },
                            onSuccess = { isImageLoaded = true },
                            onError = { isImageLoaded = true }
                        )

                        if (!isImageLoaded) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                                contentAlignment = Alignment.Center
                            ) {
                                LoadingIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        // Top-end badges (EXCLUSIVE / DEPTH)
                        Column(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (banners[page].exclusive) {
                                Box(
                                    modifier = Modifier
                                        .background(
                                            Color(0xFFFFD700).copy(alpha = 0.8f),
                                            RoundedCornerShape(4.dp)
                                        )
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            Icons.Rounded.Star,
                                            null,
                                            tint = Color.Black,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Text(
                                            "EXCLUSIVE",
                                            color = Color.Black,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                            if (banners[page].depthEffect) {
                                Box(
                                    modifier = Modifier
                                        .background(
                                            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.8f),
                                            RoundedCornerShape(4.dp)
                                        )
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            Icons.Rounded.Layers,
                                            null,
                                            tint = Color.White,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Text(
                                            "DEPTH",
                                            color = Color.White,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }

                        // For app_promo: "Install Now" button centered
                        if (isAppPromo) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Button(
                                    onClick = {
                                        val intent = Intent(
                                            Intent.ACTION_VIEW,
                                            Uri.parse(banners[page].appUrl)
                                        )
                                        context.startActivity(intent)
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color.White,
                                        contentColor = Color.Black
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(
                                        horizontal = 20.dp,
                                        vertical = 8.dp
                                    )
                                ) {
                                    Text(
                                        "Install Now",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }

                        // Bottom: gradient strip + banner name only
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            Color.Black.copy(alpha = 0.65f)
                                        )
                                    )
                                )
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = banners[page].bannerName.uppercase(),
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                letterSpacing = 1.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            // Indicator dots — below the pager
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(banners.size) { index ->
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 3.dp)
                            .size(if (pagerState.currentPage == index) 18.dp else 6.dp, 6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(
                                if (pagerState.currentPage == index)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onBackground.copy(alpha = 0.25f)
                            )
                            .clickable {
                                coroutineScope.launch { pagerState.animateScrollToPage(index) }
                            }
                    )
                }
            }
        }
    }
}

private fun Context.findActivity(): Activity? {
        var context = this
        while (context is ContextWrapper) {
            if (context is Activity) {
                return context
            }
            context = context.baseContext
        }
        return null
    }
