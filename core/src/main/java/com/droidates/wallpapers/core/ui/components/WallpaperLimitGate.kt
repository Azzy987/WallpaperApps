package com.droidates.wallpapers.core.ui.components

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.droidates.wallpapers.core.utils.AdManager

/**
 * Offered once the user has spent their free wallpaper actions for this session.
 *
 * The rewarded ad both clears the session gate and grants an ad-free window, so the
 * trade the user is offered is a real one: watching a video buys quiet browsing, not
 * permission for exactly one more download.
 *
 * Deliberately NOT a hard paywall. "Maybe later" always closes the dialog and leaves
 * the app usable — a wallpaper app that appears to require payment for its core
 * function invites refunds and one-star reviews, and Play scrutinises exactly that.
 * The gate only ever asks; the rewarded path is always free.
 *
 * @param onProceed run when the user has earned their way past the gate. Called after
 *   a completed reward, never on plain dismissal.
 */
@Composable
fun WallpaperLimitGateDialog(
    adManager: AdManager,
    activity: Activity?,
    freeActionsUsed: Int,
    onGoPremium: () -> Unit,
    onDismiss: () -> Unit,
    onProceed: () -> Unit,
    windowMinutes: Int = (AdManager.AD_FREE_WINDOW_MS / 60_000).toInt()
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Rounded.Bolt,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
        },
        title = { Text("Enjoying the wallpapers?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "You've set $freeActionsUsed wallpapers already. Watch one short " +
                        "video to keep going ad-free for the next $windowMinutes minutes."
                )
                Text(
                    "Go Premium to remove ads for good.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val act = activity
                if (act == null) {
                    // No activity to show an ad on: let the action through rather than
                    // trapping the user behind a dialog that cannot resolve itself.
                    adManager.resetSessionActions()
                    onProceed()
                    return@TextButton
                }
                var rewarded = false
                adManager.showRewardAd(
                    activity = act,
                    onRewarded = {
                        rewarded = true
                        adManager.grantAdFreeWindow()
                        adManager.resetSessionActions()
                    },
                    // Resolve on dismiss, not on reward: a user who abandons the video
                    // must still get their dialog closed. Only a completed reward
                    // proceeds with the action.
                    onAdDismissed = {
                        onDismiss()
                        if (rewarded) onProceed()
                    }
                )
            }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Bolt,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Watch video")
                }
            }
        },
        dismissButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = {
                    onDismiss()
                    onGoPremium()
                }) { Text("Go Premium") }
                TextButton(onClick = onDismiss) { Text("Maybe later") }
            }
        }
    )
}
