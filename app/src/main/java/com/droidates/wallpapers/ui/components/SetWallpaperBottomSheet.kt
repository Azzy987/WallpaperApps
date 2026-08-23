package com.droidates.wallpapers.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.droidates.wallpapers.R

enum class WallpaperSetOption {
    HOME_SCREEN, LOCK_SCREEN, BOTH_SCREENS, EXTERNAL
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SetWallpaperBottomSheet(
    onDismiss: () -> Unit,
    onOptionSelected: (WallpaperSetOption) -> Unit,
    isSettingWallpaper: Boolean = false,
    progress: Float = 0f,
    showExternalOption: Boolean = true
) {
    val modalBottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val view = LocalView.current
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color.Black.copy(alpha = 0.9f),
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White) },
        modifier = Modifier
            .fillMaxWidth(),
           // .navigationBarsPadding(),
        sheetState = modalBottomSheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(horizontal = 24.dp)
                .padding(top = 8.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.set_wallpaper_as),
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Show progress information if currently setting wallpaper
            if (isSettingWallpaper) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Linear progress
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = stringResource(R.string.setting_wallpaper_progress, (progress * 100).toInt()),
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White
                    )
                }
            } else {
                // Show all options or exclude EXTERNAL based on showExternalOption
                val options = if (showExternalOption) {
                    listOf(
                        WallpaperSetOption.HOME_SCREEN to stringResource(R.string.home_screen),
                        WallpaperSetOption.LOCK_SCREEN to stringResource(R.string.lock_screen),
                        WallpaperSetOption.BOTH_SCREENS to stringResource(R.string.both_screens),
                        WallpaperSetOption.EXTERNAL to stringResource(R.string.external_system)
                    )
                } else {
                    listOf(
                        WallpaperSetOption.HOME_SCREEN to stringResource(R.string.home_screen),
                        WallpaperSetOption.LOCK_SCREEN to stringResource(R.string.lock_screen),
                        WallpaperSetOption.BOTH_SCREENS to stringResource(R.string.both_screens)
                    )
                }

                options.forEach { (option, title) ->
                    OutlinedButton(
                        onClick = { 
                            // Perform haptic feedback on option selection
                            view.performHapticFeedback(
                                HapticFeedbackConstants.KEYBOARD_TAP,
                                HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
                            )
                            onOptionSelected(option)
                            onDismiss()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = Color.Transparent,
                            contentColor = Color.White
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleMedium
                            )
                            
                            if (isSettingWallpaper && 
                                (option != WallpaperSetOption.EXTERNAL || 
                                (option == WallpaperSetOption.EXTERNAL && progress < 1.0f))) {
                                LoadingIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = if (showExternalOption) {
                        stringResource(R.string.wallpaper_option_help_with_external)
                    } else {
                        stringResource(R.string.wallpaper_option_help)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        }
    }
} 