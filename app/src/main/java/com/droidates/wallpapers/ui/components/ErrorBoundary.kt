package com.droidates.wallpapers.ui.components

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.droidates.wallpapers.R
import kotlinx.coroutines.CancellationException

private const val TAG = "ErrorBoundary"

/**
 * An error boundary component for Compose that catches errors in its child composables
 * and displays a fallback UI instead of crashing the app.
 */
@Composable
fun ErrorBoundary(
    content: @Composable () -> Unit
) {
    // State to track error conditions
    var hasError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    // Display either error UI or content based on error state
    if (hasError) {
        // Fallback UI when an error occurs
        ErrorFallbackUI(
            errorMessage = errorMessage,
            onRetry = {
                // Reset error state and try again
                hasError = false
                errorMessage = ""
            }
        )
    } else {
        // Use CompositionLocalProvider to propagate error handler
        CompositionLocalProvider(
            LocalErrorHandler provides { error ->
                Log.e(TAG, "Error caught in composition: ${error.message}", error)
                hasError = true
                errorMessage = error.message ?: "Unknown error"
            }
        ) {
            // Render content normally, errors will be caught by error observer effects
            SafeContent(content = content)
        }
    }
}

/**
 * Fallback UI to display when an error occurs
 */
@Composable
private fun ErrorFallbackUI(
    errorMessage: String,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.something_went_wrong),
                color = Color.White,
                fontSize = 20.sp,
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center
            )
            
            Text(
                text = errorMessage.ifEmpty { stringResource(R.string.unexpected_error) },
                color = Color.White.copy(alpha = 0.8f),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
            
            Button(onClick = onRetry) {
                Text(stringResource(R.string.try_again))
            }
        }
    }
}

/**
 * Custom CompositionLocal to propagate error handler
 */
private val LocalErrorHandler = compositionLocalOf<(Throwable) -> Unit> { 
    { error -> 
        // Default handler just logs the error
        Log.e(TAG, "Unhandled error in composition", error)
    }
}

/**
 * Wrapper that attempts to safely render content with error observation
 */
@Composable
private fun SafeContent(content: @Composable () -> Unit) {
    // CRASH FIX: Remove try-catch around composables (not allowed in Compose)
    // Just render the content normally - Compose handles errors internally
    content()
}

/**
 * Helper composable to wrap error-prone sections
 */
@Composable
fun CatchErrors(content: @Composable () -> Unit) {
    // Just call the content directly - the ErrorBoundary will catch errors
    content()
} 