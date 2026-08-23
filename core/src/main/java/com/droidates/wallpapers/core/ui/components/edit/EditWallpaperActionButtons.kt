package com.droidates.wallpapers.core.ui.components.edit

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun EditWallpaperActionButtons(
    onResetClick: () -> Unit,
    onApplyClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween
    ) {
        Button(
            onClick = onResetClick,
            modifier = Modifier.weight(1f),
            // RESET BUTTON VISIBILITY FIX: Use filled button with high contrast
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.onSurface,
                contentColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = null
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Reset")
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Button(
            onClick = onApplyClick,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = Icons.Default.Done,
                contentDescription = null
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Apply")
        }
    }
} 