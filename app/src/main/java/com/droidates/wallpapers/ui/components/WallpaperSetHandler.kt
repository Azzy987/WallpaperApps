package com.droidates.wallpapers.ui.components

import android.app.Activity
import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.droidates.wallpapers.model.Wallpaper
import com.droidates.wallpapers.utils.AdManager
import com.droidates.wallpapers.viewmodel.DetailViewModel
import java.io.File
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.ktx.logEvent

@Composable
fun WallpaperSetHandler(
    wallpaper: Wallpaper?,
    isExclusive: Boolean,
    hasWatchedAd: Boolean,
    isPremiumUser: Boolean,
    viewModel: DetailViewModel,
    adManager: AdManager,
    context: Context,
    onShowUnlockDialog: () -> Unit,
    onComplete: () -> Unit = {}
): (WallpaperSetOption) -> Unit {
    var isSettingWallpaper by remember { mutableStateOf(false) }

    return { option: WallpaperSetOption ->
        wallpaper?.let { wall ->
            // First check if this is exclusive content that requires watching an ad
            // Skip for premium users
            if (isExclusive && !hasWatchedAd && !isPremiumUser) {
                onShowUnlockDialog()
                return@let
            }

            if (option == WallpaperSetOption.EXTERNAL) {
                // Show toast before starting the process
                Toast.makeText(context, "Preparing wallpaper...", Toast.LENGTH_SHORT).show()

                // Set isSettingWallpaper to true to show progress indicator
                isSettingWallpaper = true

                // Use specialized download function to avoid UI conflicts
                viewModel.downloadWallpaperForExternal(
                    context = context,
                    wallpaper = wall,
                    onComplete = { filePath ->
                        // Reset isSettingWallpaper when complete
                        isSettingWallpaper = false

                        if (filePath.isNotEmpty()) {
                            val file = File(filePath)
                            if (file.exists()) {
                                // Show toast after preparation is complete
                                Toast.makeText(context, "Opening system wallpaper picker...", Toast.LENGTH_SHORT).show()

                                // Register an activity lifecycle callback to show ad when user returns
                                (context as? Activity)?.let { act ->
                                    // Only show ad for non-premium users
                                    if (!isPremiumUser) {
                                        // WALLPAPER AD CRASH FIX: Add delay and lifecycle checks before showing ad
                                        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                                            // Wait for activity to stabilize after returning from external picker
                                            kotlinx.coroutines.delay(1000)
                                            
                                            // Double-check activity state before showing ad
                                            if (!act.isDestroyed && !act.isFinishing && !act.isChangingConfigurations) {
                                                try {
                                                    adManager.loadInterstitialAd()
                                                    adManager.showInterstitialAd(act)
                                                } catch (e: Exception) {
                                                    Log.e("WallpaperSetHandler", "Error showing ad after wallpaper set: ${e.message}")
                                                }
                                            } else {
                                                Log.w("WallpaperSetHandler", "Activity not ready for ad display after wallpaper set")
                                            }
                                        }
                                    }
                                }

                                // Open system wallpaper picker with file
                                try {
                                    val fileUri = androidx.core.content.FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.provider",
                                        file
                                    )

                                    // Create intent with proper flags
                                    val intent = android.content.Intent(android.content.Intent.ACTION_ATTACH_DATA).apply {
                                        setDataAndType(fileUri, "image/*")
                                        putExtra("mimeType", "image/*")
                                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }

                                    // Check if Google Photos is available
                                    val googlePhotosPackage = "com.google.android.apps.photos"
                                    intent.setPackage(googlePhotosPackage)

                                    try {
                                        context.startActivity(intent)
                                        android.util.Log.d("WallpaperSetHandler", "Opened wallpaper picker in Google Photos")
                                    } catch (e: Exception) {
                                        android.util.Log.d("WallpaperSetHandler", "Google Photos not available, showing chooser: ${e.message}")
                                        intent.setPackage(null) // Reset package

                                        // Create a chooser as fallback
                                        val chooserIntent = android.content.Intent.createChooser(intent, "Set as wallpaper using")
                                        chooserIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)

                                        try {
                                            context.startActivity(chooserIntent)
                                            android.util.Log.d("WallpaperSetHandler", "Opened wallpaper picker using chooser dialog")
                                        } catch (e: Exception) {
                                            android.util.Log.e("WallpaperSetHandler", "Error opening chooser: ${e.message}")
                                            Toast.makeText(
                                                context,
                                                "Unable to find an app to set wallpaper. Please try using the in-app options.",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                } catch (e: Exception) {
                                    android.util.Log.e("WallpaperSetHandler", "Error opening wallpaper picker: ${e.message}", e)
                                    Toast.makeText(
                                        context,
                                        "Error setting wallpaper: ${e.message ?: "Unknown error"}",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }

                                onComplete()
                            } else {
                                Toast.makeText(
                                    context,
                                    "Could not find downloaded wallpaper",
                                    Toast.LENGTH_SHORT
                                ).show()
                                onComplete()
                            }
                        } else {
                            Toast.makeText(
                                context,
                                "Failed to download wallpaper",
                                Toast.LENGTH_SHORT
                            ).show()
                            onComplete()
                        }
                    }
                )
            } else {
                // Show toast before starting the process
                Toast.makeText(context, "Setting wallpaper...", Toast.LENGTH_SHORT).show()

                // Set isSettingWallpaper to true to show progress indicator
                isSettingWallpaper = true

                // Set wallpaper based on option
                viewModel.setWallpaper(
                    context = context,
                    wallpaper = wall,
                    option = option,
                    onComplete = {
                        // Reset isSettingWallpaper when complete
                        isSettingWallpaper = false

                        // Firebase Analytics: Track wallpaper set
                        try {
                            val app = context.applicationContext as? com.droidates.wallpapers.WallpaperApplication
                            app?.analytics?.logEvent("wallpaper_set") {
                                param("wallpaper_id", wall.id)
                                param("option", option.name)
                                param("is_exclusive", if (isExclusive) 1L else 0L)
                            }
                        } catch (e: Exception) {
                            Log.e("WallpaperSetHandler", "Error logging analytics: ${e.message}")
                        }

                        // Show interstitial ad after setting wallpaper if user is not premium
                        (context as? Activity)?.let { act ->
                            if (!isPremiumUser) {
                                // WALLPAPER AD CRASH FIX: Add delay and lifecycle checks before showing ad
                                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                                    // Wait for wallpaper setting process to complete fully
                                    kotlinx.coroutines.delay(800)

                                    // Double-check activity state before showing ad
                                    if (!act.isDestroyed && !act.isFinishing && !act.isChangingConfigurations) {
                                        try {
                                            adManager.loadInterstitialAd()
                                            adManager.showInterstitialAd(act)
                                        } catch (e: Exception) {
                                            Log.e("WallpaperSetHandler", "Error showing ad after wallpaper set: ${e.message}")
                                        }
                                    } else {
                                        Log.w("WallpaperSetHandler", "Activity not ready for ad display after wallpaper set")
                                    }
                                }
                            }
                        }

                        onComplete()
                    }
                )
            }
        }
    }
} 