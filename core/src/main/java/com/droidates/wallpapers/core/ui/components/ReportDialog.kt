package com.droidates.wallpapers.core.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ReportProblem
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * A composable dialog that allows users to report issues with a wallpaper.
 * Used in the DetailScreen to handle user reports.
 *
 * @param onDismiss Callback to invoke when the dialog should be dismissed
 * @param onSubmit Callback to invoke when a report is submitted, with the selected reason
 */
@Composable
fun ReportDialog(
    onDismiss: () -> Unit,
    onSubmit: (ReportReason) -> Unit
) {
    var selectedReason by remember { mutableStateOf<ReportReason?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.ReportProblem,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 12.dp)
                )
                Text(
                    text = "Report Wallpaper",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .selectableGroup()
            ) {
                Text(
                    text = "Please select a reason for reporting this wallpaper:",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                ReportReason.values().forEach { reason ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = reason == selectedReason,
                                onClick = { selectedReason = reason },
                                role = Role.RadioButton
                            )
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = reason == selectedReason,
                            onClick = null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = reason.displayName,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    selectedReason?.let { onSubmit(it) }
                    onDismiss()
                },
                enabled = selectedReason != null,
                shape = MaterialTheme.shapes.small
            ) {
                Text("Submit")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shape = MaterialTheme.shapes.small
            ) {
                Text("Cancel")
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.large,
        tonalElevation = 8.dp
    )
}

/**
 * Enum class representing the possible reasons for reporting a wallpaper.
 */
enum class ReportReason(val displayName: String) {
    COPYRIGHT_VIOLATION("Copyright Violation"),
    INAPPROPRIATE_CONTENT("Inappropriate Content"),
    QUALITY_ISSUE("Poor Quality or Resolution"),
    WRONG_CATEGORY("Wrong Category"),
    OTHER("Other Issue")
} 