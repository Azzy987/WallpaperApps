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
 * Offers the user an ad-free browsing window in exchange for watching one rewarded ad.
 *
 * Why this exists: interstitials earn ~$2 eCPM and cost goodwill, rewarded earns ~$6 and
 * is opt-in. A user who is visibly tired of interstitials is the *worst* candidate for
 * another interstitial and the *best* candidate for a rewarded ad — this converts that
 * moment instead of losing them.
 *
 * Shown only when a rewarded ad is actually available, so the offer never fails after
 * the user accepts.
 */
@Composable
fun AdFreeOfferDialog(
    adManager: AdManager,
    activity: Activity?,
    onDismiss: () -> Unit,
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
        title = { Text("Browse ad-free for $windowMinutes minutes") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Watch one short video and we'll hide full-screen ads for the " +
                        "next $windowMinutes minutes."
                )
                Text(
                    "Want them gone for good? Go Premium.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val act = activity
                if (act == null) {
                    onDismiss()
                    return@TextButton
                }
                adManager.showRewardAd(
                    activity = act,
                    onRewarded = { adManager.grantAdFreeWindow() },
                    // Dismiss on close rather than on reward: if the user abandons the
                    // ad the dialog must still go away, or they are stuck behind it.
                    onAdDismissed = { onDismiss() }
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
            TextButton(onClick = onDismiss) { Text("No thanks") }
        }
    )
}
