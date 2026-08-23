package com.droidates.wallpapers.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay

/**
 * Custom toast component that appears at the bottom of the screen with a close button
 * and automatically dismisses after a delay
 */
@Composable
fun CustomToast(
    message: String,
    icon: ImageVector? = null,
    duration: Long = 3000,
    onDismiss: () -> Unit
) {
    val visible = remember { mutableStateOf(true) }
    val density = LocalDensity.current
    
    LaunchedEffect(message) {
        // Auto-dismiss after the duration
        delay(duration)
        visible.value = false
        delay(300) // Animation duration
        onDismiss()
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(10f), // Ensure toast appears above other content
        contentAlignment = Alignment.BottomCenter
    ) {
        AnimatedVisibility(
            visible = visible.value,
            enter = fadeIn(tween(200)) + slideInVertically(
                initialOffsetY = { with(density) { 50.dp.roundToPx() } },
                animationSpec = tween(200)
            ),
            exit = fadeOut(tween(200)) + slideOutVertically(
                targetOffsetY = { with(density) { 50.dp.roundToPx() } },
                animationSpec = tween(200)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 60.dp) // Increased padding from bottom for better visibility
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(8.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Icon if provided
                    if (icon != null) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                    }
                    
                    // Message text
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    
                    Spacer(modifier = Modifier.width(16.dp))
                    
                    // Close button - always visible with improved clickable area
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .clickable {
                                visible.value = false
                                onDismiss()
                            }
                            .padding(4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dismiss",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Toast manager to handle showing and dismissing toasts
 */
class ToastManager {
    var currentToastMessage by mutableStateOf<String?>(null)
    var currentToastIcon by mutableStateOf<ImageVector?>(null)
    var currentToastDuration by mutableStateOf<Long>(3000)
    
    fun showToast(
        message: String,
        icon: ImageVector? = null,
        duration: Long = 3000
    ) {
        currentToastMessage = message
        currentToastIcon = icon
        currentToastDuration = duration
    }
    
    fun dismissCurrentToast() {
        currentToastMessage = null
        currentToastIcon = null
    }
}

/**
 * Toast host component that manages displaying toasts
 */
@Composable
fun ToastHost(toastManager: ToastManager) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter
    ) {
        toastManager.currentToastMessage?.let { message ->
            CustomToast(
                message = message,
                icon = toastManager.currentToastIcon,
                duration = toastManager.currentToastDuration,
                onDismiss = { toastManager.dismissCurrentToast() }
            )
        }
    }
} 