package com.droidates.wallpapers.core.ui.components.detail

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import com.droidates.wallpapers.core.utils.StatFormatter

/**
 * A counter that rolls each digit independently, odometer style: the digit that
 * changed slides up and out while its replacement rises from below. Digits that
 * did not change never animate.
 *
 * Rolling per digit rather than per number is what makes an increment read as
 * "1240 -> 1241" instead of the whole value being swapped out.
 *
 * The value is formatted through [StatFormatter] first, so this matches the K/M
 * form used everywhere else. Non-digit characters (".", "K", "M") are rendered as
 * static text and never animate.
 *
 * Cost: one [AnimatedContent] per character, and only while a digit is mid-roll.
 * A four-digit counter animating one digit runs a single 300ms offset+alpha
 * animation, which stays on the compositor and does not trigger relayout.
 */
@Composable
fun RollingCounterText(
    count: Int,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    durationMillis: Int = 320
) {
    val formatted = remember(count) { StatFormatter.formatStatValue(count) }

    Row(modifier = modifier) {
        formatted.forEachIndexed { index, char ->
            // Keying by position keeps each slot's AnimatedContent stable across
            // recompositions, so only the slot whose digit actually changed rolls.
            key(index) {
                if (char.isDigit()) {
                    AnimatedContent(
                        targetState = char,
                        transitionSpec = {
                            (slideInVertically(tween(durationMillis)) { height -> height } +
                                fadeIn(tween(durationMillis)))
                                .togetherWith(
                                    slideOutVertically(tween(durationMillis)) { height -> -height } +
                                        fadeOut(tween(durationMillis))
                                )
                        },
                        label = "digit_$index"
                    ) { digit ->
                        Text(text = digit.toString(), style = style, color = color)
                    }
                } else {
                    // "." / "K" / "M" — static, so a rolling digit beside them stays readable.
                    Text(text = char.toString(), style = style, color = color)
                }
            }
        }
    }
}
