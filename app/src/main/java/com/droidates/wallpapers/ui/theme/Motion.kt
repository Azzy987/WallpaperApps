package com.droidates.wallpapers.ui.theme

import androidx.compose.animation.core.*
import androidx.compose.animation.core.Spring.DampingRatioHighBouncy
import androidx.compose.animation.core.Spring.DampingRatioLowBouncy
import androidx.compose.animation.core.Spring.DampingRatioMediumBouncy
import androidx.compose.animation.core.Spring.DampingRatioNoBouncy
import androidx.compose.animation.core.Spring.StiffnessHigh
import androidx.compose.animation.core.Spring.StiffnessLow
import androidx.compose.animation.core.Spring.StiffnessMedium
import androidx.compose.animation.core.Spring.StiffnessMediumLow
import androidx.compose.animation.core.Spring.StiffnessVeryLow
import androidx.compose.ui.unit.dp

/**
 * Material 3 Expressive Motion System
 * 
 * This provides enhanced spring physics and natural motion patterns
 * following Material Design 3 expressive guidelines for fluid interactions.
 */
object Material3Motion {
    
    // DURATION CONSTANTS - Material 3 Expressive
    const val DURATION_SHORT = 200
    const val DURATION_MEDIUM = 300
    const val DURATION_LONG = 400
    const val DURATION_EXTRA_LONG = 500
    
    // ENHANCED SPRING CONFIGURATIONS
    
    /**
     * Bouncy spring with high energy - for interactive elements like buttons
     */
    val bouncySpring = spring<Float>(
        dampingRatio = DampingRatioMediumBouncy,
        stiffness = StiffnessHigh
    )
    
    /**
     * Gentle bounce - for cards and surfaces
     */
    val gentleBounce = spring<Float>(
        dampingRatio = DampingRatioLowBouncy,
        stiffness = StiffnessMedium
    )
    
    /**
     * Smooth spring - for smooth transitions without bounce
     */
    val smoothSpring = spring<Float>(
        dampingRatio = DampingRatioNoBouncy,
        stiffness = StiffnessMedium
    )
    
    /**
     * Emphasized spring - for important UI state changes
     */
    val emphasizedSpring = spring<Float>(
        dampingRatio = DampingRatioHighBouncy,
        stiffness = StiffnessHigh
    )
    
    /**
     * Subtle spring - for background elements and subtle feedback
     */
    val subtleSpring = spring<Float>(
        dampingRatio = DampingRatioNoBouncy,
        stiffness = StiffnessMediumLow
    )
    
    // EXPRESSIVE EASING CURVES
    
    /**
     * Emphasized easing - for primary interactions
     */
    val emphasizedEasing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
    
    /**
     * Emphasized accelerate - for exit transitions  
     */
    val emphasizedAccelerate = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f)
    
    /**
     * Emphasized decelerate - for enter transitions
     */
    val emphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)
    
    /**
     * Legacy emphasized - for compatibility
     */
    val legacyEmphasized = CubicBezierEasing(0.4f, 0.0f, 0.2f, 1.0f)
    
    // PREDEFINED ANIMATION SPECS
    
    /**
     * Button press animation - bouncy and responsive
     */
    fun buttonPressSpec() = spring<Float>(
        dampingRatio = DampingRatioMediumBouncy,
        stiffness = StiffnessHigh
    )
    
    /**
     * Card appearance animation - gentle and smooth
     */
    fun cardEnterSpec() = spring<Float>(
        dampingRatio = DampingRatioLowBouncy,
        stiffness = StiffnessMedium
    )
    
    /**
     * Loading indicator animation - continuous and smooth
     */
    fun loadingSpec() = infiniteRepeatable<Float>(
        animation = tween(
            durationMillis = DURATION_MEDIUM,
            easing = emphasizedEasing
        ),
        repeatMode = RepeatMode.Reverse
    )
    
    /**
     * Fade transition - Material 3 standard
     */
    fun fadeSpec(durationMillis: Int = DURATION_SHORT) = tween<Float>(
        durationMillis = durationMillis,
        easing = emphasizedDecelerate
    )
    
    /**
     * Scale animation with bounce - for interactive feedback
     */
    fun scaleBouncySpec() = spring<Float>(
        dampingRatio = 0.6f,
        stiffness = StiffnessHigh
    )
    
    /**
     * Shared element transition - smooth and natural
     */
    fun sharedElementSpec() = spring<Float>(
        dampingRatio = DampingRatioNoBouncy,
        stiffness = StiffnessMedium
    )
    
    // GESTURE RESPONSE ANIMATIONS
    
    /**
     * Swipe gesture response - immediate and bouncy
     */
    fun swipeResponseSpec() = spring<Float>(
        dampingRatio = DampingRatioMediumBouncy,
        stiffness = StiffnessHigh
    )
    
    /**
     * Long press feedback - subtle bounce
     */
    fun longPressSpec() = spring<Float>(
        dampingRatio = 0.8f,
        stiffness = StiffnessHigh
    )
    
    // MOTION TOKENS
    
    /**
     * Standard elevation for interactive elements
     */
    val interactiveElevation = 8.dp
    
    /**
     * Hover elevation for cards
     */
    val hoverElevation = 12.dp
    
    /**
     * Pressed elevation 
     */
    val pressedElevation = 4.dp
}