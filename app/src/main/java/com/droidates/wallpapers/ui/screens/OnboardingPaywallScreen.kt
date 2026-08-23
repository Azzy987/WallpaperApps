package com.droidates.wallpapers.ui.screens

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.EaseInOutCubic
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.droidates.wallpapers.R
import com.droidates.wallpapers.config.AppConfig
import com.droidates.wallpapers.model.Banner
import com.droidates.wallpapers.model.PlanType
import com.droidates.wallpapers.ui.components.PolicyBottomSheet
import com.droidates.wallpapers.utils.PolicyContent
import com.droidates.wallpapers.viewmodel.HomeViewModel
import com.droidates.wallpapers.viewmodel.PremiumViewModel
import com.droidates.wallpapers.viewmodel.PremiumViewModel.PurchaseState
import kotlinx.coroutines.delay
import kotlin.math.absoluteValue

@OptIn(
    ExperimentalFoundationApi::class,
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class
)
@Composable
fun OnboardingPaywallScreen(
    onContinue: () -> Unit,
    onDismiss: () -> Unit,
    premiumViewModel: PremiumViewModel,
    homeViewModel: HomeViewModel = hiltViewModel(),
    authViewModel: com.droidates.wallpapers.viewmodel.AuthViewModel = hiltViewModel(),
    // When non-null a close (×) button is shown in the header (e.g. when opened from Settings)
    onBack: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val plans by premiumViewModel.premiumPlans.collectAsState()
    val purchaseState by premiumViewModel.purchaseState.collectAsState()
    val lifetimePlan = remember(plans) { plans.find { it.type == PlanType.LIFETIME } }
    val displayPrice = lifetimePlan?.formattedPrice ?: AppConfig.BILLING_LIFETIME_FALLBACK_PRICE
    val paywallBanners by homeViewModel.paywallBanners.collectAsState()
    val isSignedIn by authViewModel.isSignedIn.collectAsState()
    val signInState by authViewModel.signInState.collectAsState(
        initial = com.droidates.wallpapers.viewmodel.AuthViewModel.SignInState.Idle
    )

    LaunchedEffect(Unit) {
        if (paywallBanners.isEmpty()) homeViewModel.debugLoadBanners()
    }

    // Allow back gesture/button when opened from Settings
    if (onBack != null) {
        BackHandler { onBack() }
    }

    var showPrivacySheet by remember { mutableStateOf(false) }
    var showTermsSheet by remember { mutableStateOf(false) }

    var purchaseInitiated by remember { mutableStateOf(false) }
    // true = waiting for sign-in to complete before launching purchase
    var pendingPurchaseAfterSignIn by remember { mutableStateOf(false) }
    var purchaseErrorMessage by remember { mutableStateOf<String?>(null) }

    // Once sign-in completes, launch the purchase if one was pending
    LaunchedEffect(isSignedIn, pendingPurchaseAfterSignIn) {
        if (pendingPurchaseAfterSignIn && isSignedIn) {
            pendingPurchaseAfterSignIn = false
            purchaseInitiated = true
            premiumViewModel.purchasePlan(context as android.app.Activity)
        }
    }

    LaunchedEffect(purchaseState) {
        if (!purchaseInitiated) return@LaunchedEffect
        when (purchaseState) {
            is PurchaseState.Success -> onContinue()
            is PurchaseState.Failed -> {
                // Show error inline — do NOT dismiss the paywall on failure
                purchaseInitiated = false
                purchaseErrorMessage = (purchaseState as PurchaseState.Failed).message
            }
            else -> {}
        }
    }
    // Auto-clear error after 4 seconds
    LaunchedEffect(purchaseErrorMessage) {
        if (purchaseErrorMessage != null) {
            delay(4000)
            purchaseErrorMessage = null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        // ── Header ───────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp)
        ) {
            Column(modifier = Modifier.align(Alignment.CenterStart)) {
                Text(
                    text = "GO PREMIUM",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
                Text(
                    text = "Unlock Everything",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.ExtraBold
                    ),
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Join 70,000+ wallpaper lovers",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (onBack != null) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .size(36.dp)
                        .background(
                            MaterialTheme.colorScheme.onBackground.copy(alpha = 0.10f),
                            CircleShape
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // ── Carousel ─────────────────────────────────────────────────────
        WallpaperCarousel(
            exclusiveBanners = paywallBanners,
            modifier = Modifier.weight(1.6f)
        )

        Spacer(modifier = Modifier.height(12.dp))

        // ── Feature checklist — outside the container ─────────────────────
        Column(
            modifier = Modifier.padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            PaywallFeatureRow(
                text = "Ad-free experience",
                icon = painterResource(R.drawable.no_ads),
                delayMs = 300
            )
            PaywallFeatureRow(
                text = "500+ Exclusive iPhone wallpapers",
                icon = painterResource(R.drawable.exclusive_wallpapers),
                delayMs = 600
            )
            PaywallFeatureRow(
                text = "Unlock all premium filters",
                icon = painterResource(R.drawable.premium_features),
                delayMs = 900
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ── Bottom sheet container: pricing + actions ─────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = 24.dp,
                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                    clip = false,
                    ambientColor = Color.Black.copy(alpha = 0.3f),
                    spotColor = Color.Black.copy(alpha = 0.4f)
                )
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .navigationBarsPadding()
                .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 4.dp)
        ) {
            PriceOptionCard(displayPrice = displayPrice)

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "No subscription · No renewal · No ads",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            if (purchaseErrorMessage != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = purchaseErrorMessage!!,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Button(
                onClick = {
                    if (!isSignedIn) {
                        // Must be signed in to purchase — trigger Google sign-in first,
                        // then auto-launch purchase once sign-in succeeds
                        pendingPurchaseAfterSignIn = true
                        authViewModel.signIn()
                    } else {
                        purchaseInitiated = true
                        premiumViewModel.purchasePlan(context as Activity)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                enabled = purchaseState != PurchaseState.InProgress && !pendingPurchaseAfterSignIn
            ) {
                if (purchaseState == PurchaseState.InProgress || pendingPurchaseAfterSignIn) {
                    androidx.compose.material3.CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (pendingPurchaseAfterSignIn) "Signing in..." else "Processing...",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                } else {
                    Text(
                        text = "Pay Once, Own Forever — $displayPrice",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .clickable { onDismiss() }
                    .padding(vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Maybe Later",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = { showPrivacySheet = true },
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                ) {
                    Text(
                        "Privacy Policy",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    "·",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall
                )
                TextButton(
                    onClick = { showTermsSheet = true },
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                ) {
                    Text(
                        "Terms",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    "·",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall
                )
                TextButton(
                    onClick = { premiumViewModel.restorePurchases() },
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                ) {
                    Text(
                        "Restore",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (showPrivacySheet) {
        PolicyBottomSheet(
            title = "Privacy Policy",
            content = PolicyContent.privacyPolicy,
            onDismiss = { showPrivacySheet = false }
        )
    }
    if (showTermsSheet) {
        PolicyBottomSheet(
            title = "Terms of Use",
            content = PolicyContent.termsOfUse,
            onDismiss = { showTermsSheet = false }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun WallpaperCarousel(
    exclusiveBanners: List<Banner> = emptyList(),
    modifier: Modifier = Modifier
) {
    // ── Shimmer skeleton while loading ───────────────────────────────────
    if (exclusiveBanners.isEmpty()) {
        val shimmerColors = listOf(
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        )
        val transition = rememberInfiniteTransition(label = "shimmer")
        val translateAnim by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1000f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing)
            ),
            label = "shimmer_offset"
        )
        val shimmerBrush = Brush.linearGradient(
            colors = shimmerColors,
            start = Offset(translateAnim - 400f, translateAnim - 400f),
            end = Offset(translateAnim, translateAnim)
        )
        Column(modifier = modifier) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(shimmerBrush)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(3) {
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 3.dp)
                            .size(6.dp, 6.dp)
                            .background(
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(3.dp)
                            )
                    )
                }
            }
        }
        return
    }

    // ── Live carousel with card-stack depth effect ───────────────────────
    val pageCount = exclusiveBanners.size
    val pagerState = rememberPagerState { pageCount }

    // Auto-scroll — slow and smooth
    LaunchedEffect(pageCount) {
        while (true) {
            delay(2800)
            val next = (pagerState.currentPage + 1) % pageCount
            pagerState.animateScrollToPage(
                page = next,
                animationSpec = tween(durationMillis = 800, easing = EaseInOutCubic)
            )
        }
    }

    Column(modifier = modifier) {
        HorizontalPager(
            state = pagerState,
            // Show ~15% of adjacent pages on each side
            contentPadding = PaddingValues(horizontal = 48.dp),
            pageSpacing = 12.dp,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) { page ->
            // pageOffset: 0 = this page is the center,
            // positive = this page is to the LEFT of current, negative = to the RIGHT
            val rawOffset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
            val absOffset = rawOffset.absoluteValue.coerceIn(0f, 1f)

            // Stack depth: center card is on top (full scale + high shadow),
            // adjacent cards are slightly scaled down and pushed back.
            // All cards keep the same layout size — only the graphicsLayer shadow depth changes.
            val targetScale = 1f - absOffset * 0.06f       // 1.0 → 0.94 (very subtle)

            val animScale by animateFloatAsState(
                targetValue = targetScale,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow
                ),
                label = "cardScale_$page"
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = animScale
                        scaleY = animScale
                        shape = RoundedCornerShape(24.dp)
                        clip = true
                    }
            ) {
                AsyncImage(
                    model = exclusiveBanners[page].bannerUrl,
                    contentDescription = "Exclusive wallpaper ${page + 1}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                // EXCLUSIVE badge — top-right corner
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 10.dp, end = 10.dp)
                        .background(Color(0xFFFFD700), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "✦ EXCLUSIVE",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF1A1A1A),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 9.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Animated pill dot indicators
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(pageCount) { index ->
                val isSelected = pagerState.currentPage == index
                val dotWidth by animateFloatAsState(
                    targetValue = if (isSelected) 18f else 6f,
                    animationSpec = tween(300),
                    label = "dotWidth_$index"
                )
                Box(
                    modifier = Modifier
                        .padding(horizontal = 3.dp)
                        .size(dotWidth.dp, 6.dp)
                        .background(
                            color = if (isSelected)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(3.dp)
                        )
                )
            }
        }
    }
}

@Composable
internal fun PriceOptionCard(displayPrice: String) {
    Box(modifier = Modifier.fillMaxWidth()) {
        // Card body — top padding makes room for the badge sitting on the border
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
                .border(
                    width = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(16.dp)
                )
                .background(
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                    RoundedCornerShape(16.dp)
                )
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Checkmark confirms plan is included
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Lifetime Access",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "No subscriptions, ever.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = buildAnnotatedString {
                            withStyle(
                                SpanStyle(
                                    textDecoration = TextDecoration.LineThrough,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 12.sp
                                )
                            ) { append("₹239") }
                            append("  ")
                            withStyle(
                                SpanStyle(
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 22.sp
                                )
                            ) { append(displayPrice) }
                        }
                    )
                    Text(
                        text = "one-time",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // "BEST OFFER" badge centred on the top border line
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .background(Color(0xFFFFD700), RoundedCornerShape(6.dp))
                .padding(horizontal = 10.dp, vertical = 3.dp)
        ) {
            Text(
                text = "✦  BEST OFFER  ✦",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF1A1A1A),
                fontWeight = FontWeight.ExtraBold,
                fontSize = 9.sp
            )
        }
    }
}

@Composable
private fun PaywallFeatureRow(text: String, icon: Painter, delayMs: Int) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(delayMs.toLong())
        visible = true
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(800)) +
                slideInHorizontally(
                    initialOffsetX = { -80 },
                    animationSpec = tween(800, easing = EaseOutCubic)
                )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                painter = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
    }
}
