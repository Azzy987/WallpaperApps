package com.droidates.wallpapers.core.ui.components.detail

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** What an action button is currently showing. */
enum class ActionPhase { Idle, Working, Done }

/**
 * The icon area of an action button, morphing between its resting icon, a spinner
 * while work is in flight, and a checkmark once the work succeeded.
 *
 * The checkmark is the point: without it the spinner simply vanishes and the user
 * is left guessing whether the download actually happened. It shows for
 * [doneDurationMillis] and then falls back to the resting icon on its own.
 *
 * Cost: one [AnimatedContent] that only animates on phase changes — three per
 * download, not per frame. The transition is scale+alpha, both draw-time
 * properties, so no relayout.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ActionButtonContent(
    icon: ImageVector,
    label: String,
    phase: ActionPhase,
    tint: Color,
    modifier: Modifier = Modifier,
    iconSize: Dp = 22.dp,
    doneDurationMillis: Long = 1100L
) {
    // Hold "Done" briefly, then drop back to the resting icon.
    var showDone by remember { mutableStateOf(false) }
    LaunchedEffect(phase) {
        if (phase == ActionPhase.Done) {
            showDone = true
            delay(doneDurationMillis)
            showDone = false
        } else {
            showDone = false
        }
    }

    val rendered = when {
        phase == ActionPhase.Working -> ActionPhase.Working
        showDone -> ActionPhase.Done
        else -> ActionPhase.Idle
    }

    AnimatedContent(
        targetState = rendered,
        transitionSpec = {
            (scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn(tween(180)))
                .togetherWith(scaleOut(tween(140)) + fadeOut(tween(140)))
        },
        modifier = modifier,
        label = "action_phase"
    ) { current ->
        when (current) {
            ActionPhase.Working -> LoadingIndicator(
                modifier = Modifier.size(iconSize),
                color = Color.White
            )
            ActionPhase.Done -> Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = "$label complete",
                tint = Color.White,
                modifier = Modifier.size(iconSize)
            )
            ActionPhase.Idle -> Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(iconSize)
            )
        }
    }
}
