package com.droidates.wallpapers.core.ui.components.detail

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember

/**
 * Drives the "pop" a heart makes when it is tapped.
 *
 * Call [FavoriteBounceState.pop] on every tap; read [FavoriteBounceState.scale]
 * into a `Modifier.scale`. The pop overshoots to [peak] and springs back to 1f.
 *
 * Why an [Animatable] and not a timed `delay()` sequence: a spring settles on its
 * own, so the button never sits in a "mid-animation" state that has to be cleared
 * by a timer. Tapping again mid-pop retargets the same spring from wherever it is,
 * which is what makes rapid double-taps feel responsive instead of dropped.
 *
 * Cost: one animated float feeding `Modifier.scale`, which is a draw-time property.
 * It does not remeasure or relayout the button.
 */
class FavoriteBounceState internal constructor(
    private val peak: Float
) {
    internal val animatable = Animatable(1f)
    internal var popCount = mutableIntStateOf(0)

    val scale: Float get() = animatable.value

    /** Trigger a pop. Safe to call repeatedly; each call restarts the overshoot. */
    fun pop() {
        popCount.intValue++
    }

    internal suspend fun runPop() {
        animatable.animateTo(
            targetValue = peak,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessHigh
            )
        )
        animatable.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium
            )
        )
    }
}

/**
 * Remembers a [FavoriteBounceState] and runs its animation.
 *
 * @param peak how far the icon overshoots on tap; 1.3f reads as a clear pop
 *             without the icon colliding with its neighbours.
 */
@Composable
fun rememberFavoriteBounce(peak: Float = 1.3f): FavoriteBounceState {
    val state = remember { FavoriteBounceState(peak) }
    val count = state.popCount.intValue
    LaunchedEffect(count) {
        // Skip the initial composition so the icon does not pop on screen entry.
        if (count > 0) state.runPop()
    }
    return state
}
