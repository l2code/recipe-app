package com.recipearchive.app.ui.webimport

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.recipearchive.app.data.local.entity.WebImportOutcomeStatus
import com.recipearchive.app.data.webimport.ImportHistoryEntryUi
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportHistoryScreen(
    viewModel: ImportViewModel,
    onBack: () -> Unit,
    onRecipeClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val history by viewModel.history.collectAsState()

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Import History", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        if (history.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                    Icon(Icons.Filled.History, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                    Text(
                        "No imports yet",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Text(
                        "Recipes you import from a URL or pasted text will show up here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(history, key = { it.id }) { entry -> HistoryRow(entry, onRecipeClick) }
            }
        }
    }
}

@Composable
private fun HistoryRow(entry: ImportHistoryEntryUi, onRecipeClick: (String) -> Unit) {
    val recipeId = entry.recipeId
    val clickable = entry.status == WebImportOutcomeStatus.SUCCESS && recipeId != null
    Surface(
        onClick = if (clickable) ({ onRecipeClick(recipeId!!) }) else ({}),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                Text(entry.title.ifBlank { "Untitled recipe" }, style = MaterialTheme.typography.titleMedium)
                Text(
                    entry.domain.ifBlank { entry.url },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    formatTimestamp(entry.importedAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (entry.errorMessage != null) {
                    Text(
                        entry.errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            StatusBadge(entry.status)
        }
    }
}

@Composable
private fun StatusBadge(status: WebImportOutcomeStatus) {
    val (label, color) = when (status) {
        WebImportOutcomeStatus.SUCCESS -> "Imported" to MaterialTheme.colorScheme.secondaryContainer
        WebImportOutcomeStatus.NOT_FOUND -> "No recipe found" to MaterialTheme.colorScheme.surfaceVariant
        WebImportOutcomeStatus.NETWORK_ERROR -> "Network error" to MaterialTheme.colorScheme.errorContainer
        WebImportOutcomeStatus.PARSE_ERROR -> "Parse error" to MaterialTheme.colorScheme.errorContainer
    }
    Surface(shape = MaterialTheme.shapes.small, color = color) {
        Text(label, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelLarge)
    }
}

private fun formatTimestamp(timestamp: Long): String = Instant.ofEpochMilli(timestamp)
    .atZone(ZoneId.systemDefault())
    .format(DateTimeFormatter.ofPattern("EEE, MMM d, yyyy · h:mm a"))
