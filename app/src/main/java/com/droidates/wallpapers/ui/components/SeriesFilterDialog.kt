package com.droidates.wallpapers.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.droidates.wallpapers.R

@Composable
fun SeriesFilterDialog(
    availableSeries: List<String>,
    currentSeries: String?,
    onDismiss: () -> Unit,
    onSeriesSelected: (String?) -> Unit
) {
    var selectedSeries by remember { 
        mutableStateOf(currentSeries ?: "All Series") 
    }
    var searchQuery by remember { mutableStateOf("") }
    
    // Filter series based on search query
    val filteredSeries = remember(availableSeries, searchQuery) {
        if (searchQuery.isEmpty()) {
            availableSeries
        } else {
            availableSeries.filter { series ->
                series.contains(searchQuery, ignoreCase = true)
            }
        }
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Text(
                text = "Filter iPhone Series",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            ) 
        },
        text = {
            Column {
                Text(
                    text = "Select a iPhone series to filter wallpapers:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                // Search field
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search series...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    shape = RoundedCornerShape(24.dp),
                    singleLine = true
                )
                
                // Use LazyColumn for better performance with many series
                LazyColumn(
                    modifier = Modifier
                        .selectableGroup()
                        .heightIn(max = 400.dp), // Limit height to prevent dialog from being too tall
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(filteredSeries) { series ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .selectable(
                                    selected = (series == selectedSeries),
                                    onClick = { selectedSeries = series },
                                    role = Role.RadioButton
                                )
                                .padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (series == selectedSeries),
                                onClick = null // null because we're handling the click on the row
                            )
                            Text(
                                text = series,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(start = 12.dp),
                                fontWeight = if (series == "All Series") FontWeight.Medium else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val seriesToFilter = if (selectedSeries == "All Series") null else selectedSeries
                    onSeriesSelected(seriesToFilter)
                    onDismiss()
                }
            ) {
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}