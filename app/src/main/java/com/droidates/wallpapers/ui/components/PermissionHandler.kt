package com.droidates.wallpapers.ui.components

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale
import androidx.core.content.ContextCompat

enum class PermissionType {
    STORAGE,
    NOTIFICATIONS
}

@Composable
fun PermissionHandler(
    permissionType: PermissionType,
    onPermissionGranted: () -> Unit,
    onPermissionDenied: () -> Unit = {},
    rationaleTitle: String = "Permission Required",
    rationaleMessage: String = "This permission is needed to provide core functionality of the app.",
    settingsTitle: String = "Permission Required",
    settingsMessage: String = "Please grant the required permission in Settings to use this feature."
) {
    val context = LocalContext.current
    var showRationaleDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    val permissionToRequest = remember(permissionType) {
        when (permissionType) {
            PermissionType.STORAGE -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    Manifest.permission.READ_MEDIA_IMAGES
                } else {
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                }
            }
            PermissionType.NOTIFICATIONS -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    Manifest.permission.POST_NOTIFICATIONS
                } else {
                    ""
                }
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            onPermissionGranted()
        } else {
            // Only show settings dialog if permission is permanently denied
            if (!shouldShowRequestPermissionRationale(context as Activity, permissionToRequest)) {
                showSettingsDialog = true
            }
            onPermissionDenied()
        }
    }

    if (showRationaleDialog) {
        AlertDialog(
            onDismissRequest = { showRationaleDialog = false },
            title = { Text(text = rationaleTitle, style = MaterialTheme.typography.titleLarge) },
            text = { Text(text = rationaleMessage, style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                Button(onClick = {
                    showRationaleDialog = false
                    permissionLauncher.launch(permissionToRequest)
                }) {
                    Text("Grant Permission")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRationaleDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            title = { Text(text = settingsTitle, style = MaterialTheme.typography.titleLarge) },
            text = { Text(text = settingsMessage, style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                Button(onClick = {
                    showSettingsDialog = false
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                    context.startActivity(intent)
                }) {
                    Text("Open Settings")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSettingsDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Initial permission check
    when {
        // Permission already granted
        ContextCompat.checkSelfPermission(
            context,
            permissionToRequest
        ) == PackageManager.PERMISSION_GRANTED -> {
            onPermissionGranted()
        }
        // Should show rationale
        shouldShowRequestPermissionRationale(context as Activity, permissionToRequest) -> {
            showRationaleDialog = true
        }
        // First time asking for permission
        else -> {
            permissionLauncher.launch(permissionToRequest)
        }
    }
} 