package com.droidates.wallpapers.core.notifications

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.droidates.wallpapers.core.config.AppConfig

/**
 * Asks for POST_NOTIFICATIONS at a moment the user is likely to say yes.
 *
 * Timing matters more than wording here. Android 13+ treats repeated dismissals as
 * a permanent denial — after two, the system stops showing the dialog at all and the
 * user has to enable notifications from Settings by hand. So the app gets
 * essentially **one** good attempt.
 *
 * We spend it right after a success (first wallpaper downloaded or applied), when
 * the app has just proved its worth — not during onboarding, where the user has not
 * seen a single wallpaper yet and "allow notifications" reads as spam.
 *
 * Below Android 13 the permission does not exist and notifications work by default.
 */
object NotificationPermissionPrompt {

    private const val KEY_ASKED = "notif_permission_asked"
    private const val KEY_SUCCESS_COUNT = "notif_success_count"

    /** Ask only after this many satisfying actions (download / apply). */
    private const val ASK_AFTER_SUCCESSES = 1

    fun isGranted(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }

    private fun prefs(context: Context) =
        context.getSharedPreferences(AppConfig.PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Call after a wallpaper is successfully downloaded or applied. Cheap and safe to
     * call every time — it only counts up.
     */
    fun recordSuccess(context: Context) {
        val p = prefs(context)
        p.edit().putInt(KEY_SUCCESS_COUNT, p.getInt(KEY_SUCCESS_COUNT, 0) + 1).apply()
    }

    /** True when we should show the system dialog now. */
    fun shouldAsk(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        if (isGranted(context)) return false
        val p = prefs(context)
        if (p.getBoolean(KEY_ASKED, false)) return false // never burn the second attempt
        return p.getInt(KEY_SUCCESS_COUNT, 0) >= ASK_AFTER_SUCCESSES
    }

    fun markAsked(context: Context) {
        prefs(context).edit().putBoolean(KEY_ASKED, true).apply()
    }
}

/**
 * Drop into a screen where wallpapers get downloaded or applied. It stays dormant
 * until [NotificationPermissionPrompt.shouldAsk] is satisfied, then shows the system
 * dialog exactly once.
 */
@Composable
fun NotificationPermissionEffect() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* granted or not, we do not ask again */ }

    LaunchedEffect(Unit) {
        if (NotificationPermissionPrompt.shouldAsk(context)) {
            NotificationPermissionPrompt.markAsked(context)
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
