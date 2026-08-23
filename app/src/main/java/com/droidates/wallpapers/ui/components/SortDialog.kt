package com.droidates.wallpapers.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.droidates.wallpapers.R
import com.droidates.wallpapers.utils.SortOption

@Composable
fun SortDialog(
    currentSortOption: SortOption,
    onDismiss: () -> Unit,
    onSortOptionSelected: (SortOption) -> Unit,
    showLaunchYearOption: Boolean = true
) {
    var selectedOption by remember { mutableStateOf(currentSortOption) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sort_wallpapers)) },
        shape = RoundedCornerShape(24.dp), // Match app card corners
        text = {
            Column(Modifier.selectableGroup()) {
                SortOption.values().forEach { option ->
                    // Skip LAUNCH_YEAR option if showLaunchYearOption is false
                    if (option != SortOption.LAUNCH_YEAR || showLaunchYearOption) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .selectable(
                                    selected = (option == selectedOption),
                                    onClick = { selectedOption = option },
                                    role = Role.RadioButton
                                )
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (option == selectedOption),
                                onClick = null // null because we're handling the click on the row
                            )
                            Text(
                                text = stringResource(option.displayNameResId),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(start = 16.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            FilledTonalButton(
                onClick = {
                    onSortOptionSelected(selectedOption)
                    onDismiss()
                },
                shape = RoundedCornerShape(24.dp), // Match app card corners
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(
                    text = stringResource(R.string.ok),
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(24.dp), // Match app card corners
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
} 