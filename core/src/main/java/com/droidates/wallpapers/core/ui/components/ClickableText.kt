package com.droidates.wallpapers.core.ui.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle

/**
 * A reusable clickable text component that supports text with clickable parts
 */
@Composable
fun ClickableText(
    text: String,
    modifier: Modifier = Modifier,
    styleNormal: TextStyle = LocalTextStyle.current,
    colorNormal: Color = LocalContentColor.current,
    textAlign: TextAlign = TextAlign.Start,
    onClick: () -> Unit
) {
    val annotatedText = buildAnnotatedString {
        withStyle(
            style = SpanStyle(
                color = colorNormal,
                textDecoration = TextDecoration.Underline
            )
        ) {
            append(text)
        }
    }
    
    BasicText(
        text = annotatedText,
        modifier = modifier.pointerInput(Unit) {
            detectTapGestures { onClick() }
        },
        style = styleNormal.copy(textAlign = textAlign),
        overflow = TextOverflow.Clip,
        maxLines = Int.MAX_VALUE
    )
}

/**
 * A clickable text component with clickable spans
 *
 * @param fullText The complete text string
 * @param clickableParts Map of clickable text parts to their click handlers
 * @param modifier Modifier for the text
 * @param style Base TextStyle for the text
 * @param normalColor Color for non-clickable text
 * @param clickableColor Color for clickable text
 * @param textAlign Optional text alignment
 */
@Composable
fun ClickableText(
    fullText: String,
    clickableParts: Map<String, () -> Unit>,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    normalColor: Color = LocalContentColor.current,
    clickableColor: Color = MaterialTheme.colorScheme.primary,
    textAlign: TextAlign = TextAlign.Start
) {
    // Create an AnnotatedString with clickable parts tagged
    val annotatedString = buildAnnotatedString {
        var currentPosition = 0
        
        // Process the full text to find and tag clickable parts
        for ((key, _) in clickableParts) {
            val startIndex = fullText.indexOf(key, currentPosition)
            if (startIndex >= 0) {
                // Add non-clickable text before the clickable part
                if (startIndex > currentPosition) {
                    val nonClickablePart = fullText.substring(currentPosition, startIndex)
                    withStyle(SpanStyle(color = normalColor)) {
                        append(nonClickablePart)
                    }
                }
                
                // Add the clickable part with a tag
                val endIndex = startIndex + key.length
                withStyle(
                    SpanStyle(
                        color = clickableColor,
                        textDecoration = TextDecoration.Underline
                    )
                ) {
                    // Tag the clickable part with its text as the tag
                    pushStringAnnotation(tag = key, annotation = key)
                    append(key)
                    pop()
                }
                
                currentPosition = endIndex
            }
        }
        
        // Add any remaining non-clickable text
        if (currentPosition < fullText.length) {
            val remaining = fullText.substring(currentPosition)
            withStyle(SpanStyle(color = normalColor)) {
                append(remaining)
            }
        }
    }
    
    // Use BasicText with pointerInput for handling clicks
    BasicText(
        text = annotatedString,
        modifier = modifier.pointerInput(Unit) {
            detectTapGestures { offset ->
                // Find which tagged part was clicked
                clickableParts.keys.forEach { key ->
                    annotatedString.getStringAnnotations(tag = key, start = offset.x.toInt(), end = offset.x.toInt())
                        .firstOrNull()?.let { clickableParts[key]?.invoke() }
                }
            }
        },
        style = style.copy(textAlign = textAlign),
        overflow = TextOverflow.Clip,
        maxLines = Int.MAX_VALUE
    )
} 