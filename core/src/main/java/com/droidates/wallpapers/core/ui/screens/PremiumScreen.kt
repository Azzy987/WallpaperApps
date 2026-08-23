@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class
)

package com.droidates.wallpapers.core.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Downloading
import androidx.compose.material.icons.rounded.FilterAlt
import androidx.compose.material.icons.rounded.ImageSearch
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.droidates.wallpapers.core.model.PlanType
import com.droidates.wallpapers.core.model.PremiumFeature
import com.droidates.wallpapers.core.model.PremiumPlan
import com.droidates.wallpapers.core.navigation.NavigationState
import com.droidates.wallpapers.core.viewmodel.AuthViewModel
import com.droidates.wallpapers.core.viewmodel.HomeViewModel
import com.droidates.wallpapers.core.viewmodel.PremiumViewModel
import com.droidates.wallpapers.core.viewmodel.PremiumViewModel.PurchaseState
import com.droidates.wallpapers.core.ui.theme.rememberReducedMotion
import kotlinx.coroutines.delay
import java.util.*
import kotlinx.coroutines.isActive
import kotlin.math.absoluteValue

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PremiumScreen(
    navigationState: NavigationState,
    premiumViewModel: PremiumViewModel = hiltViewModel(),
    authViewModel: AuthViewModel = hiltViewModel(),
    homeViewModel: HomeViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val isPremium by premiumViewModel.isPremium.collectAsState()
    val userData by authViewModel.userData.collectAsState()
    val successMessage by premiumViewModel.successMessage.collectAsState()
    val paywallBanners by homeViewModel.paywallBanners.collectAsState()
    val reducedMotion = rememberReducedMotion()

    LaunchedEffect(Unit) {
        if (paywallBanners.isEmpty()) homeViewModel.debugLoadBanners()
    }

    BackHandler { navigationState.navigateBack() }

    val premiumTypeFormatted = userData?.premiumType?.let {
        when (it.lowercase()) {
            "monthly" -> "Monthly"
            "yearly" -> "Yearly"
            "lifetime" -> "Lifetime"
            else -> it.replaceFirstChar { c ->
                if (c.isLowerCase()) c.titlecase(Locale.getDefault()) else c.toString()
            }
        }
    } ?: "Lifetime"

    val premiumSinceFormatted = userData?.premiumSince?.let { sinceDate ->
        java.text.SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault()).format(sinceDate)
    } ?: "—"

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (isPremium) {
            PremiumActiveScreen(
                premiumTypeFormatted = premiumTypeFormatted,
                premiumSinceFormatted = premiumSinceFormatted,
                isLifetime = userData?.premiumType?.lowercase() == "lifetime",
                paywallBanners = paywallBanners,
                reducedMotion = reducedMotion,
                onBack = { navigationState.navigateBack() },
                onManageSubscription = {
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        data = Uri.parse("https://play.google.com/store/account/subscriptions")
                    }
                    context.startActivity(intent)
                }
            )
        } else {
            OnboardingPaywallScreen(
                onContinue = { navigationState.navigateBack() },
                onDismiss = { navigationState.navigateBack() },
                premiumViewModel = premiumViewModel,
                onBack = { navigationState.navigateBack() }
            )
        }
    }

    LaunchedEffect(successMessage) {
        successMessage?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PremiumActiveScreen(
    premiumTypeFormatted: String,
    premiumSinceFormatted: String,
    isLifetime: Boolean,
    paywallBanners: List<com.droidates.wallpapers.core.model.Banner>,
    reducedMotion: Boolean,
    onBack: () -> Unit,
    onManageSubscription: () -> Unit
) {
    // Entry animations
    val headerAlpha = remember { Animatable(0f) }
    val headerSlide = remember { Animatable(-30f) }
    val badgeScale = remember { Animatable(0f) }
    val contentAlpha = remember { Animatable(0f) }

    LaunchedEffect(reducedMotion) {
        headerAlpha.animateTo(1f, tween(if (reducedMotion) 120 else 500, easing = EaseOutCubic))
        headerSlide.animateTo(0f, tween(if (reducedMotion) 120 else 500, easing = EaseOutCubic))
    }
    LaunchedEffect(reducedMotion) {
        delay(if (reducedMotion) 0 else 200)
        badgeScale.animateTo(
            1f,
            spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
        )
    }
    LaunchedEffect(reducedMotion) {
        delay(if (reducedMotion) 0 else 400)
        contentAlpha.animateTo(1f, tween(if (reducedMotion) 120 else 600, easing = EaseOutCubic))
    }

    // Pulsing glow behind crown
    val glowAlpha = if (reducedMotion) 0.18f else {
        val infiniteTransition = rememberInfiniteTransition(label = "glow")
        infiniteTransition.animateFloat(
            initialValue = 0.15f,
            targetValue = 0.35f,
            animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing)),
            label = "glowAlpha"
        ).value
    }

    // Resolve theme tokens once so composables below can use them
    val primary = MaterialTheme.colorScheme.primary
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val onBackground = MaterialTheme.colorScheme.onBackground
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val surfaceContainer = MaterialTheme.colorScheme.surfaceContainer
    val surfaceContainerHigh = MaterialTheme.colorScheme.surfaceContainerHigh
    val outline = MaterialTheme.colorScheme.outline

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // ── Top bar ──────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp)
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(36.dp)
                    .background(onBackground.copy(alpha = 0.08f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = onBackground,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // ── Hero: animated crown + "You're Premium" ──────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    alpha = headerAlpha.value
                    translationY = headerSlide.value
                }
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(contentAlignment = Alignment.Center) {
                // Pulsing halo uses primary color
                Box(
                    modifier = Modifier
                        .size(110.dp)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(primary.copy(alpha = glowAlpha), Color.Transparent)
                            ),
                            CircleShape
                        )
                )
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .graphicsLayer { scaleX = badgeScale.value; scaleY = badgeScale.value }
                        .background(primaryContainer.copy(alpha = 0.3f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.WorkspacePremium,
                        contentDescription = null,
                        tint = primary,
                        modifier = Modifier.size(44.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "You're Premium",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
                color = onBackground
            )
            Spacer(modifier = Modifier.height(6.dp))
            // Access type badge using primary container
            Box(
                modifier = Modifier
                    .background(primaryContainer, RoundedCornerShape(20.dp))
                    .padding(horizontal = 16.dp, vertical = 5.dp)
            ) {
                Text(
                    text = "✦  $premiumTypeFormatted Access  ✦",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.5.sp
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Member since $premiumSinceFormatted",
                style = MaterialTheme.typography.bodySmall,
                color = onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ── Carousel (banners) ────────────────────────────────────────────
        if (paywallBanners.isNotEmpty()) {
            Box(modifier = Modifier.graphicsLayer { alpha = contentAlpha.value }) {
                PremiumBannerCarousel(
                    banners = paywallBanners,
                    reducedMotion = reducedMotion,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        // ── Features grid ─────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .graphicsLayer { alpha = contentAlpha.value }
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Everything Unlocked",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = primary,
                modifier = Modifier.padding(bottom = 4.dp)
            )

            val features = listOf(
                Triple(Icons.Rounded.ImageSearch, "500+ Exclusive Wallpapers", "Full library access"),
                Triple(Icons.Rounded.Star, "Ad-Free Experience", "No interruptions, ever"),
                Triple(Icons.Rounded.FilterAlt, "All Premium Filters", "Every series & category"),
                Triple(Icons.Rounded.Downloading, "HD Downloads", "Full-resolution saves"),
                Triple(Icons.Rounded.VerifiedUser, "Priority Support", "Direct help when needed"),
                Triple(Icons.Rounded.CheckCircle, "Future Updates", "New drops, always free"),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    features.take(3).forEachIndexed { i, (icon, title, sub) ->
                        PremiumFeatureCard(
                            icon = icon, title = title, subtitle = sub,
                            delayMs = i * 100,
                            containerColor = surfaceContainer,
                            iconTint = primary,
                            iconBg = primaryContainer.copy(alpha = 0.4f),
                            titleColor = onBackground,
                            subtitleColor = onSurfaceVariant
                        )
                    }
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    features.drop(3).forEachIndexed { i, (icon, title, sub) ->
                        PremiumFeatureCard(
                            icon = icon, title = title, subtitle = sub,
                            delayMs = (i + 3) * 100,
                            containerColor = surfaceContainer,
                            iconTint = primary,
                            iconBg = primaryContainer.copy(alpha = 0.4f),
                            titleColor = onBackground,
                            subtitleColor = onSurfaceVariant
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ── Member details card ───────────────────────────────────────────
        Column(
            modifier = Modifier
                .graphicsLayer { alpha = contentAlpha.value }
                .padding(horizontal = 20.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(surfaceContainerHigh)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Top accent line using primary
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color.Transparent, primary, Color.Transparent)
                        )
                    )
            )
            Spacer(modifier = Modifier.height(4.dp))
            MemberDetailRow(
                label = "Plan Type",
                value = premiumTypeFormatted,
                valueColor = primary,
                labelColor = onSurfaceVariant
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(outline.copy(alpha = 0.3f))
            )
            MemberDetailRow(
                label = "Member Since",
                value = premiumSinceFormatted,
                valueColor = onBackground,
                labelColor = onSurfaceVariant
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(outline.copy(alpha = 0.3f))
            )
            MemberDetailRow(
                label = "Expires",
                value = if (isLifetime) "Never (Lifetime)" else "See Play Store",
                valueColor = if (isLifetime) primary else onBackground,
                labelColor = onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ── Manage subscription (non-lifetime only) ───────────────────────
        if (!isLifetime) {
            Button(
                onClick = onManageSubscription,
                modifier = Modifier
                    .graphicsLayer { alpha = contentAlpha.value }
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            ) {
                Text(
                    text = "Manage Subscription",
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "To cancel, go to Google Play → Account → Subscriptions",
                style = MaterialTheme.typography.labelSmall,
                color = onSurfaceVariant.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun MemberDetailRow(
    label: String,
    value: String,
    valueColor: Color,
    labelColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = labelColor
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = valueColor
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PremiumBannerCarousel(
    banners: List<com.droidates.wallpapers.core.model.Banner>,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier
) {
    val pagerState = rememberPagerState { banners.size }
    val primary = MaterialTheme.colorScheme.primary
    val onBackground = MaterialTheme.colorScheme.onBackground
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val onPrimaryContainer = MaterialTheme.colorScheme.onPrimaryContainer

    LaunchedEffect(banners.size, reducedMotion) {
        if (reducedMotion || banners.size <= 1) return@LaunchedEffect
        while (isActive) {
            delay(3000)
            if (pagerState.isScrollInProgress) continue
            val next = (pagerState.currentPage + 1) % banners.size
            pagerState.animateScrollToPage(next, animationSpec = tween(700))
        }
    }

    Column(modifier = modifier) {
        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(horizontal = 40.dp),
            pageSpacing = 10.dp,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) { page ->
            val rawOffset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
            val absOffset = rawOffset.absoluteValue.coerceIn(0f, 1f)
            val scale by animateFloatAsState(
                targetValue = 1f - absOffset * 0.07f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow
                ),
                label = "scale_$page"
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale; scaleY = scale
                        shape = RoundedCornerShape(20.dp); clip = true
                    }
            ) {
                AsyncImage(
                    model = banners[page].bannerUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.35f))
                            )
                        )
                )
                // Badge using primaryContainer so it follows theme
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .background(primaryContainer, RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "✦ EXCLUSIVE",
                        style = MaterialTheme.typography.labelSmall,
                        color = onPrimaryContainer,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 9.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(banners.size) { index ->
                val isSelected = pagerState.currentPage == index
                val dotWidth by animateFloatAsState(
                    targetValue = if (isSelected) 16f else 5f,
                    animationSpec = tween(300),
                    label = "dot_$index"
                )
                Box(
                    modifier = Modifier
                        .padding(horizontal = 2.dp)
                        .size(dotWidth.dp, 5.dp)
                        .background(
                            if (isSelected) primary else onBackground.copy(alpha = 0.2f),
                            RoundedCornerShape(3.dp)
                        )
                )
            }
        }
    }
}

@Composable
private fun PremiumFeatureCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    delayMs: Int,
    containerColor: Color,
    iconTint: Color,
    iconBg: Color,
    titleColor: Color,
    subtitleColor: Color
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(delayMs.toLong() + 400)
        visible = true
    }
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(400),
        label = "featureAlpha_$title"
    )
    val slideY by animateFloatAsState(
        targetValue = if (visible) 0f else 16f,
        animationSpec = tween(400, easing = EaseOutCubic),
        label = "featureSlide_$title"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { this.alpha = alpha; translationY = slideY }
            .clip(RoundedCornerShape(14.dp))
            .background(containerColor)
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .background(iconBg, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = titleColor,
                    maxLines = 2
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = subtitleColor
                )
            }
        }
    }
}

// ── Legacy composables kept for backward-compat call sites ──────────────────

@Composable
fun SubscriptionDetailRow(title: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun FeatureGridItem(feature: PremiumFeature) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                when {
                    feature.iconDrawableRes != null -> Icon(
                        painter = androidx.compose.ui.res.painterResource(id = feature.iconDrawableRes),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    feature.icon != null -> Icon(
                        imageVector = feature.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = feature.title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun PlanCard(
    plan: PremiumPlan,
    isSelected: Boolean,
    onPlanSelected: () -> Unit,
    modifier: Modifier = Modifier
) {
    val planTypeText = when (plan.type) {
        PlanType.LIFETIME -> "Lifetime"
    }
    androidx.compose.material3.Card(
        modifier = modifier
            .height(150.dp)
            .padding(horizontal = 4.dp),
        shape = RoundedCornerShape(20.dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.surfaceVariant
            else MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
        ),
        onClick = onPlanSelected
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = plan.formattedPrice ?: plan.price,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                        RoundedCornerShape(4.dp)
                    )
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = planTypeText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}
