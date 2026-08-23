package com.droidates.wallpapers.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.lazy.grid.LazyGridState
import com.google.android.gms.ads.nativead.NativeAd
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import android.os.Bundle

/**
 * PERFORMANCE FIXED: Pure Compose Native Ad Card like Instagram/TikTok
 * Never hides ads during scroll - only prevents background audio queries
 * Uses intelligent loading management to prevent performance issues
 */
@Composable
fun NativeAdCard(
    modifier: Modifier = Modifier,
    nativeAd: NativeAd
) {
    // NEVER hide ads during scroll - always show them like major apps do

    // SCROLL PERFORMANCE FIX: Immediately pause any video content in the ad on composition
    // This prevents ExoPlayer/MediaCodec from running during scroll, eliminating frame drops
    LaunchedEffect(nativeAd) {
        nativeAd.mediaContent?.let { media ->
            if (media.hasVideoContent()) {
                media.videoController?.pause()
            }
        }
    }

    // PERFORMANCE: Extract ad data once and cache to prevent recompositions
    val headline = remember(nativeAd) { nativeAd.headline ?: "Sponsored Content" }
    val body = remember(nativeAd) { nativeAd.body ?: "" }
    val callToAction = remember(nativeAd) { nativeAd.callToAction ?: "Learn More" }
    val adIcon = remember(nativeAd) { nativeAd.icon?.drawable }

    // CRITICAL: Pure Compose implementation - NO AndroidView, NO NativeAdView
    // PERFORMANCE: No animations to reduce recompositions during scroll
    Card(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 220.dp)
            .clickable {
                try {
                    nativeAd.performClick(Bundle().apply {
                        putString("asset_id", "3003") // Generic click tracking
                    })
                } catch (e: Exception) {
                    // Silent failure - no logging during production
                }
            },
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 6.dp
        ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // ADMOB COMPLIANCE: Show actual ad images as required by guidelines
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    // First try to show the main ad image (if available)
                    val adImages = remember(nativeAd) { nativeAd.images }
                    val mainImage = remember(adImages) {
                        adImages?.firstOrNull()?.drawable
                    }

                    when {
                        mainImage != null -> {
                            // Show the actual ad image (AdMob requirement)
                            val mainImageBitmap = remember(mainImage) {
                                try {
                                    mainImage.toBitmap().asImageBitmap()
                                } catch (e: Exception) {
                                    null
                                }
                            }

                            if (mainImageBitmap != null) {
                                Image(
                                    bitmap = mainImageBitmap,
                                    contentDescription = "Ad Image",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                // Fallback to gradient with icon
                                AdImageFallback(adIcon)
                            }
                        }

                        adIcon != null -> {
                            // Show ad icon with subtle background
                            AdImageFallback(adIcon)
                        }

                        else -> {
                            // Last resort fallback
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Ad",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Ad content section
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Text content
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        // Headline
                        Text(
                            text = headline,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        if (body.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = body,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // CTA Button
                    Button(
                        onClick = {
                            try {
                                nativeAd.performClick(Bundle().apply {
                                    putString("asset_id", "3003") // CTA click tracking
                                })
                            } catch (e: Exception) {
                                // Silent failure - no logging during production
                            }
                        },
                        modifier = Modifier.height(40.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        contentPadding = PaddingValues(horizontal = 16.dp)
                    ) {
                        Text(
                            text = callToAction,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }

            // Ad indicator overlay (required by AdMob guidelines)
            Surface(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp),
                shape = RoundedCornerShape(4.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Info,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Ad",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }
}

@Composable
private fun AdImageFallback(adIcon: android.graphics.drawable.Drawable?) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        val adIconBitmap = remember(adIcon) {
            try {
                adIcon?.toBitmap()?.asImageBitmap()
            } catch (e: Exception) {
                null
            }
        }

        if (adIconBitmap != null) {
            Image(
                bitmap = adIconBitmap,
                contentDescription = "Ad Icon",
                modifier = Modifier.size(48.dp),
                contentScale = ContentScale.Fit
            )
        } else {
            Text(
                text = "📱",
                fontSize = 28.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * CRITICAL: Lightweight placeholder card shown during scrolling
 * Prevents any audio queries or WebView operations that cause lag
 */
@Composable
private fun ScrollPlaceholderCard(
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 200.dp),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            // Minimal content to prevent any system calls
            Text(
                text = "Sponsored",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}

/**
 * DEPRECATED: Use NativeAdCard directly instead
 * Major apps like Instagram/TikTok never hide ads during scroll
 */
@Deprecated("Use NativeAdCard directly - apps should never hide ads during scroll")
@Composable
fun ViewportAwareNativeAdCard(
    modifier: Modifier = Modifier,
    nativeAd: NativeAd,
    @Suppress("UNUSED_PARAMETER") itemIndex: Int = 0,
    @Suppress("UNUSED_PARAMETER") lazyGridState: LazyGridState? = null,
    @Suppress("UNUSED_PARAMETER") preloadOffset: Int = 0
) {
    // FIXED: Always show the ad like Instagram/TikTok - never hide during scroll
    NativeAdCard(
        modifier = modifier,
        nativeAd = nativeAd
    )
}

