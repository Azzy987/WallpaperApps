package com.droidates.wallpapers.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.droidates.wallpapers.R
import com.droidates.wallpapers.utils.PreferencesManager
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.border
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.foundation.clickable
import com.droidates.wallpapers.ui.components.PolicyBottomSheet
import com.droidates.wallpapers.utils.PolicyContent

@OptIn(ExperimentalFoundationApi::class, ExperimentalAnimationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    preferencesManager: PreferencesManager
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val pagerState = rememberPagerState { 3 }

    var showPrivacySheet by remember { mutableStateOf(false) }

    // Permission launchers
    val storagePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        // Move to next page regardless of permission result
        coroutineScope.launch {
            pagerState.animateScrollToPage(2)
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        // Onboarding completed
        preferencesManager.setOnboardingCompleted(true)
        onComplete()
    }

    // Background image animation state
    val backgroundImageAlpha = remember { Animatable(0f) }

    // Start with background image animation
    LaunchedEffect(Unit) {
        backgroundImageAlpha.animateTo(
            targetValue = 1f,
            animationSpec = tween(800, easing = EaseInOutCubic)
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Background image with crossfade animation on page change
        AnimatedContent(
            targetState = pagerState.currentPage,
            transitionSpec = {
                fadeIn(animationSpec = tween(1000, easing = EaseInOutCubic)) togetherWith
                fadeOut(animationSpec = tween(1000, easing = EaseInOutCubic))
            }
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
                        .graphicsLayer {
                            alpha = backgroundImageAlpha.value
                        }
                )
            }
        }

        // Content pager with no swiping
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            pageSpacing = 0.dp,
            userScrollEnabled = false
        ) { page ->
            // No content here - rendered separately with animations
        }

        // Animated page content - separate from pager to control animations
        AnimatedContent(
            targetState = pagerState.currentPage,
            transitionSpec = {
                fadeIn(animationSpec = tween(800, easing = EaseInOutCubic)) togetherWith
                fadeOut(animationSpec = tween(500, easing = EaseInOutCubic))
            }
        ) { page ->
            when (page) {
                0 -> OnboardingPage(
                    title = "Welcome to OnePlus 7 Wallpapers",
                    description = "Explore a handpicked collection of stunning, high-quality wallpapers designed to give your phone a whole new vibe.",
                    buttonText = "Get Started",
                    pageNumber = 1,
                    totalPages = 3,
                    onButtonClick = {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(1)
                        }
                    },
                    isVisible = true,
                    backgroundAlpha = backgroundImageAlpha.value
                )
                1 -> OnboardingPage(
                    title = "Storage Access",
                    description = buildAnnotatedString {
                        append("Give your phone a new look with beautiful ")
                        withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                            append("Depth Effect Wallpapers")
                        }
                        append(".\nWe'll need storage access to save them for you.")
                    },
                    buttonText = "Grant Permission",
                    pageNumber = 2,
                    totalPages = 3,
                    onButtonClick = {
                        val permissionToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            Manifest.permission.READ_MEDIA_IMAGES
                        } else {
                            Manifest.permission.WRITE_EXTERNAL_STORAGE
                        }
                        storagePermissionLauncher.launch(permissionToRequest)
                    },
                    isVisible = true,
                    backgroundAlpha = backgroundImageAlpha.value
                )
                2 -> OnboardingPage(
                    title = "Stay Updated",
                    description = "Stay inspired with daily wallpaper drops and special collections.\nEnable notifications so you never miss a new release!",
                    buttonText = "Enable Notifications",
                    pageNumber = 3,
                    totalPages = 3,
                    onButtonClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            // For older Android versions, just complete onboarding
                            preferencesManager.setOnboardingCompleted(true)
                            onComplete()
                        }
                    },
                    isVisible = true,
                    backgroundAlpha = backgroundImageAlpha.value
                )
            }
        }

        // Skip button at the top right
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
    onPrivacyClick: (() -> Unit)? = null
) {
    // Calculate progress for indicator
    val progress = pageNumber.toFloat() / totalPages.toFloat()

    // Container animation - appears after background is loaded
    val containerAlpha = remember { Animatable(0f) }

    // Content animations - appear after container is visible
    val titleAlpha = remember { Animatable(0f) }
    val descriptionAlpha = remember { Animatable(0f) }
    val buttonAlpha = remember { Animatable(0f) }
    val indicatorAlpha = remember { Animatable(0f) }

    // Animation stages with sequential timing
    LaunchedEffect(isVisible, backgroundAlpha) {
        if (isVisible && backgroundAlpha > 0.9f) {
            // Reset all animations
            containerAlpha.snapTo(0f)
            titleAlpha.snapTo(0f)
            descriptionAlpha.snapTo(0f)
            buttonAlpha.snapTo(0f)
            indicatorAlpha.snapTo(0f)

            // 1. First animate container with a slight delay after background
            delay(300)
            containerAlpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(700, easing = EaseOutCubic)
            )

            // 2. Then animate title
            delay(200)
            titleAlpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(700, easing = EaseOutCubic)
            )

            // 3. Then animate description
            delay(150)
            descriptionAlpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(700, easing = EaseOutCubic)
            )

            // 4. Then animate button
            delay(100)
            buttonAlpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(500, easing = EaseOutCubic)
            )

            // 5. Finally animate indicators
            delay(50)
            indicatorAlpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(400, easing = EaseOutCubic)
            )
        }
    }

    // Button pulsing animation
    val buttonScale = remember { Animatable(1f) }
    LaunchedEffect(isVisible, buttonAlpha.value) {
        if (isVisible && buttonAlpha.value > 0.9f) {
            // Start pulsing after initial fade in
            delay(500)
            while (true) {
                buttonScale.animateTo(
                    targetValue = 1.05f,
                    animationSpec = tween(800, easing = EaseInOutQuad)
                )
                buttonScale.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(800, easing = EaseInOutQuad)
                )
                delay(300)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Card with fixed dark background so white text is always readable
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 32.dp)
                .navigationBarsPadding()
                .graphicsLayer {
                    alpha = containerAlpha.value
                    translationY = (1f - containerAlpha.value) * 50f
                }
                .background(
                    color = Color(0xCC0D0D12),
                    shape = RoundedCornerShape(24.dp)
                )
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFFFBB0D7).copy(alpha = 0.5f),
                            Color(0xFFFBB0D7).copy(alpha = 0.15f)
                        )
                    ),
                    shape = RoundedCornerShape(24.dp)
                )
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Title with fade in
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
                                translationY = (1f - titleAlpha.value) * 20f
                            }
                    )

                    // Description with fade in
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
                                        translationY = (1f - descriptionAlpha.value) * 15f
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
                                        translationY = (1f - descriptionAlpha.value) * 15f
                                    }
                            )
                        }
                    }

                    // Button with fade in and pulse animation
                    Button(
                        onClick = onButtonClick,
                        modifier = Modifier
                            .height(56.dp)
                            .fillMaxWidth()
                            .graphicsLayer {
                                alpha = buttonAlpha.value
                                scaleX = buttonScale.value
                                scaleY = buttonScale.value
                                translationY = (1f - buttonAlpha.value) * 10f
                            },
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        elevation = ButtonDefaults.buttonElevation(
                            defaultElevation = 4.dp,
                            pressedElevation = 8.dp
                        )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = buttonText,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    // Page indicator with fade in
                    Column(
                        modifier = Modifier
                            .padding(top = 24.dp)
                            .graphicsLayer { alpha = indicatorAlpha.value },
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Page number text
                        Text(
                            text = "$pageNumber/$totalPages",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        // Dots indicator
                        Row(
                            horizontalArrangement = Arrangement.Center
                        ) {
                            repeat(3) { index ->
                                val isSelected = index == pageNumber - 1
                                Box(
                                    modifier = Modifier
                                        .padding(horizontal = 4.dp)
                                        .size(if (isSelected) 10.dp else 8.dp)
                                        .background(
                                            color = if (isSelected)
                                                MaterialTheme.colorScheme.primary
                                            else
                                                Color.White.copy(alpha = 0.4f),
                                            shape = CircleShape
                                        )
                                )
                            }
                        }

                        // Privacy policy footnote on welcome page only
                        if (onPrivacyClick != null) {
                            Spacer(modifier = Modifier.height(12.dp))
                            val annotated = buildAnnotatedString {
                                append("By continuing you agree to our ")
                                withStyle(style = SpanStyle(
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )) {
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
}
