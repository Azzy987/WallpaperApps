package com.droidates.wallpapers.core.ui.screens

import com.droidates.wallpapers.core.config.AppConfig
import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInOutCubic
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.droidates.wallpapers.core.R
import com.droidates.wallpapers.core.ui.components.GradientButton
import com.droidates.wallpapers.core.ui.components.PolicyBottomSheet
import com.droidates.wallpapers.core.ui.theme.accentOnColor
import com.droidates.wallpapers.core.ui.theme.rememberReducedMotion
import com.droidates.wallpapers.core.utils.PolicyContent
import com.droidates.wallpapers.core.utils.PreferencesManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class, ExperimentalAnimationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    preferencesManager: PreferencesManager
) {
    val coroutineScope = rememberCoroutineScope()
    val pagerState = rememberPagerState { 3 }
    val reducedMotion = rememberReducedMotion()

    var showPrivacySheet by remember { mutableStateOf(false) }

    val storagePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        coroutineScope.launch {
            pagerState.animateScrollToPage(2)
        }
    }

    val backgroundImageAlpha = remember { Animatable(if (reducedMotion) 1f else 0f) }

    LaunchedEffect(reducedMotion) {
        if (reducedMotion) {
            backgroundImageAlpha.snapTo(1f)
        } else {
            backgroundImageAlpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(800, easing = EaseInOutCubic)
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = pagerState.currentPage,
            transitionSpec = {
                if (reducedMotion) {
                    fadeIn(tween(80)) togetherWith fadeOut(tween(80))
                } else {
                    fadeIn(animationSpec = tween(1000, easing = EaseInOutCubic)) togetherWith
                        fadeOut(animationSpec = tween(1000, easing = EaseInOutCubic))
                }
            },
            label = "onboarding_background"
        ) { page ->
            Box(modifier = Modifier.fillMaxSize()) {
                Image(
                    painter = painterResource(
                        id = when (page) {
                            0 -> R.drawable.onboarding_screen1
                            1 -> R.drawable.onboarding_screen2
                            else -> R.drawable.onboarding_screen3
                        }
                    ),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = backgroundImageAlpha.value }
                )
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            pageSpacing = 0.dp,
            userScrollEnabled = false
        ) { }

        AnimatedContent(
            targetState = pagerState.currentPage,
            transitionSpec = {
                if (reducedMotion) {
                    fadeIn(tween(80)) togetherWith fadeOut(tween(80))
                } else {
                    fadeIn(animationSpec = tween(800, easing = EaseInOutCubic)) togetherWith
                        fadeOut(animationSpec = tween(500, easing = EaseInOutCubic))
                }
            },
            label = "onboarding_content"
        ) { page ->
            when (page) {
                0 -> OnboardingPage(
                    title = "Welcome to ${AppConfig.APP_NAME}",
                    description = "${AppConfig.ONBOARDING_TAGLINE}\n" +
                        "Browse the latest drops, combo sets, and depth looks.\n" +
                        "Curated in HD for home and lock screen.",
                    buttonText = "Next",
                    pageNumber = 1,
                    totalPages = 3,
                    onButtonClick = {
                        coroutineScope.launch { pagerState.animateScrollToPage(1) }
                    },
                    isVisible = true,
                    backgroundAlpha = backgroundImageAlpha.value,
                    reducedMotion = reducedMotion,
                    onPrivacyClick = { showPrivacySheet = true }
                )
                1 -> OnboardingPage(
                    title = "Combo & Duo Wallpapers",
                    description = buildAnnotatedString {
                        append("Matching pairs and dual-screen sets.\n")
                        append("Saved straight to your gallery in one tap.")
                    },
                    buttonText = "Continue",
                    pageNumber = 2,
                    totalPages = 3,
                    onButtonClick = {
                        // Android 10+ saves via MediaStore with no permission at all, so
                        // there is nothing to ask for — just move on. Only legacy devices
                        // still need the write permission.
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                            storagePermissionLauncher.launch(
                                Manifest.permission.WRITE_EXTERNAL_STORAGE
                            )
                        } else {
                            coroutineScope.launch { pagerState.animateScrollToPage(2) }
                        }
                    },
                    isVisible = true,
                    backgroundAlpha = backgroundImageAlpha.value,
                    reducedMotion = reducedMotion
                )
                else -> OnboardingPage(
                    title = "Depth Effect Wallpapers",
                    description = "Layered lock-screen looks with real depth.\n" +
                        "Preview how wallpapers sit behind the clock.\n" +
                        "Save favorites and apply them in one tap.",
                    buttonText = "Get Started",
                    pageNumber = 3,
                    totalPages = 3,
                    onButtonClick = {
                        preferencesManager.setOnboardingCompleted(true)
                        onComplete()
                    },
                    isVisible = true,
                    backgroundAlpha = backgroundImageAlpha.value,
                    reducedMotion = reducedMotion
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .statusBarsPadding(),
            contentAlignment = Alignment.TopEnd
        ) {
            TextButton(
                onClick = {
                    preferencesManager.setOnboardingCompleted(true)
                    onComplete()
                },
                modifier = Modifier
                    .background(
                        color = Color.Black.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(16.dp)
                    )
                    .padding(horizontal = 8.dp)
            ) {
                Text(
                    text = "Skip",
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
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
}

@Composable
private fun OnboardingPage(
    title: String,
    description: Any,
    buttonText: String,
    pageNumber: Int,
    totalPages: Int,
    onButtonClick: () -> Unit,
    isVisible: Boolean,
    backgroundAlpha: Float = 1f,
    reducedMotion: Boolean = false,
    onPrivacyClick: (() -> Unit)? = null
) {
    val containerAlpha = remember { Animatable(if (reducedMotion) 1f else 0f) }
    val titleAlpha = remember { Animatable(if (reducedMotion) 1f else 0f) }
    val descriptionAlpha = remember { Animatable(if (reducedMotion) 1f else 0f) }
    val buttonAlpha = remember { Animatable(if (reducedMotion) 1f else 0f) }
    val indicatorAlpha = remember { Animatable(if (reducedMotion) 1f else 0f) }

    LaunchedEffect(isVisible, backgroundAlpha, reducedMotion) {
        if (!isVisible) return@LaunchedEffect

        if (reducedMotion || backgroundAlpha > 0.9f) {
            if (reducedMotion) {
                containerAlpha.snapTo(1f)
                titleAlpha.snapTo(1f)
                descriptionAlpha.snapTo(1f)
                buttonAlpha.snapTo(1f)
                indicatorAlpha.snapTo(1f)
                return@LaunchedEffect
            }

            containerAlpha.snapTo(0f)
            titleAlpha.snapTo(0f)
            descriptionAlpha.snapTo(0f)
            buttonAlpha.snapTo(0f)
            indicatorAlpha.snapTo(0f)

            delay(300)
            containerAlpha.animateTo(1f, tween(700, easing = EaseOutCubic))

            delay(200)
            titleAlpha.animateTo(1f, tween(700, easing = EaseOutCubic))

            delay(150)
            descriptionAlpha.animateTo(1f, tween(700, easing = EaseOutCubic))

            delay(100)
            buttonAlpha.animateTo(1f, tween(500, easing = EaseOutCubic))

            delay(50)
            indicatorAlpha.animateTo(1f, tween(400, easing = EaseOutCubic))
        }
    }

    val cardCornerRadius = 44.dp
    val ctaCornerRadius = 20.dp
    val accent = MaterialTheme.colorScheme.primary

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 32.dp)
                .navigationBarsPadding()
                .graphicsLayer {
                    alpha = containerAlpha.value
                    translationY = if (reducedMotion) 0f else (1f - containerAlpha.value) * 50f
                }
                .background(
                    color = Color(0xCC0D0D12),
                    shape = RoundedCornerShape(cardCornerRadius)
                )
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            accent.copy(alpha = 0.5f),
                            accent.copy(alpha = 0.15f)
                        )
                    ),
                    shape = RoundedCornerShape(cardCornerRadius)
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .padding(bottom = 16.dp)
                        .graphicsLayer {
                            alpha = titleAlpha.value
                            translationY = if (reducedMotion) 0f else (1f - titleAlpha.value) * 20f
                        }
                )

                when (description) {
                    is String -> {
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White.copy(alpha = 0.85f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .padding(bottom = 32.dp)
                                .graphicsLayer {
                                    alpha = descriptionAlpha.value
                                    translationY = if (reducedMotion) 0f else (1f - descriptionAlpha.value) * 15f
                                }
                        )
                    }
                    is AnnotatedString -> {
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White.copy(alpha = 0.85f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .padding(bottom = 32.dp)
                                .graphicsLayer {
                                    alpha = descriptionAlpha.value
                                    translationY = if (reducedMotion) 0f else (1f - descriptionAlpha.value) * 15f
                                }
                        )
                    }
                }

                GradientButton(
                    onClick = onButtonClick,
                    modifier = Modifier
                        .graphicsLayer {
                            alpha = buttonAlpha.value
                            translationY = if (reducedMotion) 0f else (1f - buttonAlpha.value) * 10f
                        },
                    height = 56.dp,
                    cornerRadius = ctaCornerRadius,
                    pulseWhenEnabled = isVisible && buttonAlpha.value > 0.9f
                ) {
                    Text(
                        text = buttonText,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = accentOnColor()
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = accentOnColor()
                    )
                }

                Column(
                    modifier = Modifier
                        .padding(top = 24.dp)
                        .graphicsLayer { alpha = indicatorAlpha.value },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "$pageNumber/$totalPages",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    Row(horizontalArrangement = Arrangement.Center) {
                        repeat(totalPages) { index ->
                            val isSelected = index == pageNumber - 1
                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 4.dp)
                                    .size(if (isSelected) 10.dp else 8.dp)
                                    .background(
                                        color = if (isSelected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            Color.White.copy(alpha = 0.4f)
                                        },
                                        shape = CircleShape
                                    )
                            )
                        }
                    }

                    if (onPrivacyClick != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        val annotated = buildAnnotatedString {
                            append("By continuing you agree to our ")
                            withStyle(
                                style = SpanStyle(
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            ) {
                                append("Privacy Policy")
                            }
                        }
                        Text(
                            text = annotated,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.75f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.clickable { onPrivacyClick() }
                        )
                    }
                }
            }
        }
    }
}
