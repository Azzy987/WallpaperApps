package com.droidates.wallpapers.core.ui.screens

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.droidates.wallpapers.core.R
import com.droidates.wallpapers.core.config.AppConfig
import com.droidates.wallpapers.core.model.PlanType
import com.droidates.wallpapers.core.ui.components.GradientButton
import com.droidates.wallpapers.core.ui.components.PaywallWallpaperGrid
import com.droidates.wallpapers.core.ui.components.PolicyBottomSheet
import com.droidates.wallpapers.core.ui.theme.accentOnColor
import com.droidates.wallpapers.core.ui.theme.badgeExclusiveContainer
import com.droidates.wallpapers.core.ui.theme.badgeExclusiveOnContainer
import com.droidates.wallpapers.core.utils.PolicyContent
import com.droidates.wallpapers.core.viewmodel.HomeViewModel
import com.droidates.wallpapers.core.viewmodel.PremiumViewModel
import com.droidates.wallpapers.core.viewmodel.PremiumViewModel.PurchaseState
import kotlinx.coroutines.delay

private val PaywallSheetTopRadius = 44.dp
private const val PaywallSheetMaxHeightFraction = 0.88f

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun OnboardingPaywallScreen(
    onContinue: () -> Unit,
    onDismiss: () -> Unit,
    premiumViewModel: PremiumViewModel = hiltViewModel(),
    homeViewModel: HomeViewModel = hiltViewModel(),
    authViewModel: com.droidates.wallpapers.core.viewmodel.AuthViewModel = hiltViewModel(),
    onBack: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val plans by premiumViewModel.premiumPlans.collectAsState()
    val purchaseState by premiumViewModel.purchaseState.collectAsState()
    val lifetimePlan = remember(plans) { plans.find { it.type == PlanType.LIFETIME } }
    val displayPrice = lifetimePlan?.formattedPrice ?: AppConfig.BILLING_LIFETIME_FALLBACK_PRICE
    val paywallBanners by homeViewModel.paywallBanners.collectAsState()
    val backdropUrls = remember(paywallBanners) {
        paywallBanners.map { it.bannerUrl }.filter { it.isNotEmpty() }
    }
    val isSignedIn by authViewModel.isSignedIn.collectAsState()

    LaunchedEffect(Unit) {
        homeViewModel.ensurePaywallBannersLoaded()
    }

    val closeAction = onBack ?: onDismiss
    if (onBack != null) {
        BackHandler { onBack() }
    }

    var showPrivacySheet by remember { mutableStateOf(false) }
    var showTermsSheet by remember { mutableStateOf(false) }
    var purchaseInitiated by remember { mutableStateOf(false) }
    var pendingPurchaseAfterSignIn by remember { mutableStateOf(false) }
    var purchaseErrorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(isSignedIn, pendingPurchaseAfterSignIn) {
        if (pendingPurchaseAfterSignIn && isSignedIn) {
            pendingPurchaseAfterSignIn = false
            purchaseInitiated = true
            premiumViewModel.purchasePlan(context as Activity)
        }
    }

    LaunchedEffect(purchaseState) {
        if (!purchaseInitiated) return@LaunchedEffect
        when (purchaseState) {
            is PurchaseState.Success -> onContinue()
            is PurchaseState.Failed -> {
                purchaseInitiated = false
                purchaseErrorMessage = (purchaseState as PurchaseState.Failed).message
            }
            else -> {}
        }
    }

    LaunchedEffect(purchaseErrorMessage) {
        if (purchaseErrorMessage != null) {
            delay(4000)
            purchaseErrorMessage = null
        }
    }

    val purchaseEnabled =
        purchaseState != PurchaseState.InProgress && !pendingPurchaseAfterSignIn

    val sheetShape = RoundedCornerShape(
        topStart = PaywallSheetTopRadius,
        topEnd = PaywallSheetTopRadius
    )

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val sheetMaxHeight = maxHeight * PaywallSheetMaxHeightFraction

        PaywallWallpaperGrid(
            urls = backdropUrls,
            modifier = Modifier.fillMaxSize()
        )

        IconButton(
            onClick = closeAction,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(12.dp)
                .size(40.dp)
                .background(Color.Black.copy(alpha = 0.45f), CircleShape)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close",
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .wrapContentHeight()
                .heightIn(max = sheetMaxHeight)
                .clip(sheetShape)
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
                Text(
                    text = "GO PREMIUM",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Unlock Everything",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "Join 70,000+ wallpaper lovers",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 6.dp, bottom = 16.dp)
                )

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    PaywallFeatureRow(
                        text = "Ad-free experience",
                        icon = painterResource(R.drawable.no_ads)
                    )
                    PaywallFeatureRow(
                        text = "500+ curated wallpapers",
                        icon = painterResource(R.drawable.exclusive_wallpapers)
                    )
                    PaywallFeatureRow(
                        text = "Unlock all premium filters",
                        icon = painterResource(R.drawable.premium_features)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                PriceOptionCard(displayPrice = displayPrice)

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "No subscription · No renewal · No ads",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                if (purchaseErrorMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = purchaseErrorMessage!!,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                GradientButton(
                    onClick = {
                        if (!isSignedIn) {
                            pendingPurchaseAfterSignIn = true
                            authViewModel.signIn()
                        } else {
                            purchaseInitiated = true
                            premiumViewModel.purchasePlan(context as Activity)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = purchaseEnabled,
                    height = 54.dp,
                    cornerRadius = 16.dp,
                    pulseWhenEnabled = true
                ) {
                    if (purchaseState == PurchaseState.InProgress || pendingPurchaseAfterSignIn) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = accentOnColor(),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = if (pendingPurchaseAfterSignIn) "Signing in..." else "Processing...",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = accentOnColor()
                        )
                    } else {
                        Text(
                            text = "Pay Once, Own Forever — $displayPrice",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = accentOnColor()
                        )
                    }
                }

                if (onBack == null) {
                    Text(
                        text = "Maybe Later",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(top = 10.dp)
                            .clickable { onDismiss() }
                            .padding(vertical = 8.dp)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = { premiumViewModel.restorePurchases() },
                        contentPadding = PaddingValues(horizontal = 6.dp)
                    ) {
                        Text(
                            "Restore",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text("·", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(
                        onClick = { showTermsSheet = true },
                        contentPadding = PaddingValues(horizontal = 6.dp)
                    ) {
                        Text(
                            "Terms",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text("·", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(
                        onClick = { showPrivacySheet = true },
                        contentPadding = PaddingValues(horizontal = 6.dp)
                    ) {
                        Text(
                            "Privacy Policy",
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

@Composable
internal fun PriceOptionCard(displayPrice: String) {
    Box(modifier = Modifier.fillMaxWidth()) {
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
                            ) { append(AppConfig.BILLING_LIFETIME_COMPARE_PRICE) }
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
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .background(badgeExclusiveContainer, RoundedCornerShape(6.dp))
                .padding(horizontal = 10.dp, vertical = 3.dp)
        ) {
            Text(
                text = "✦  BEST OFFER  ✦",
                style = MaterialTheme.typography.labelSmall,
                color = badgeExclusiveOnContainer,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 9.sp
            )
        }
    }
}

@Composable
private fun PaywallFeatureRow(text: String, icon: Painter) {
    Row(
        modifier = Modifier.fillMaxWidth(),
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
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
