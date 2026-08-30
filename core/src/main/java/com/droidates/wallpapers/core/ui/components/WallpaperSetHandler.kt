package com.droidates.wallpapers.core.ui.components

import android.app.Activity
import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.droidates.wallpapers.core.model.Wallpaper
import com.droidates.wallpapers.core.utils.AdManager
import com.droidates.wallpapers.core.viewmodel.DetailViewModel
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
    onComplete: () -> Unit = {},
    /**
     * Invoked instead of the wallpaper work once the session's free actions are spent.
     * Receives the deferred action so the caller's gate dialog can resume it after a
     * rewarded ad. Defaults to running the action, so a caller that has not adopted the
     * gate keeps working exactly as before rather than silently dropping the tap.
     */
    onLimitReached: (proceed: () -> Unit) -> Unit = { it() }
): (WallpaperSetOption) -> Unit {
    var isSettingWallpaper by remember { mutableStateOf(false) }

    // The wallpaper work itself. Split out so the interstitial can run BEFORE it:
    // these paths hand off to the system wallpaper picker, and an ad fired on the way
    // back used to land on a half-restored activity and skip its dismissal callback.
    val runSet: (WallpaperSetOption) -> Unit = { option: WallpaperSetOption ->
        wallpaper?.let { wall ->
            adManager.recordWallpaperAction()

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
                        // Ad already shown before this work started (see runSet).
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
                            val app = context.applicationContext as? com.droidates.wallpapers.core.WallpaperApplication
                            app?.analytics?.logEvent("wallpaper_set") {
                                param("wallpaper_id", wall.id)
                                param("option", option.name)
                                param("is_exclusive", if (isExclusive) 1L else 0L)
                            }
                        } catch (e: Exception) {
                            Log.e("WallpaperSetHandler", "Error logging analytics: ${e.message}")
                        }

                        // Ad already shown before this work started (see runSet).

                        onComplete()
                    }
                )
            }
        }
    }

    // Public entry point: exclusive-unlock check, then session gate, then the
    // interstitial, and only then the wallpaper work.
    return { option: WallpaperSetOption ->
        if (wallpaper != null) {
            if (isExclusive && !hasWatchedAd && !isPremiumUser) {
                onShowUnlockDialog()
            } else if (!adManager.hasFreeActionsLeft()) {
                // Out of free actions this session: hand off to the gate dialog owner.
                onLimitReached { runSet(option) }
            } else {
                val act = context as? Activity
                if (act != null && !isPremiumUser) {
                    adManager.showInterstitialThen(act) { runSet(option) }
                } else {
                    runSet(option)
                }
            }
        }
    }
}
