package com.recipearchive.app.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.recipearchive.app.data.repository.RecipeDetailUi

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    viewModel: DetailViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val detail by viewModel.uiState.collectAsState()
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(detail?.recipe?.title ?: "Recipe") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to library")
                    }
                },
                actions = {
                    val current = detail
                    if (current != null) {
                        val isFavorite = current.appState?.isFavorite ?: false
                        IconButton(onClick = { viewModel.toggleFavorite(isFavorite) }) {
                            Icon(
                                imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        val current = detail
        if (current == null) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else {
            DetailContent(
                detail = current,
                onNotesChanged = viewModel::updateNotes,
                modifier = Modifier.padding(padding),
            )
        }
    }
}

@Composable
fun DetailContent(
    detail: RecipeDetailUi,
    onNotesChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValuesAll16,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { SourceSection(detail) }

        if (detail.reviewFlags.isNotEmpty()) {
            item { ReviewFlagsSection(detail.reviewFlags.map { it.flagValue }) }
        }

        item { SectionHeader("Ingredients") }
        if (detail.ingredients.isEmpty()) {
            item { Text("No ingredients were extracted for this recipe.", style = MaterialTheme.typography.bodyMedium) }
        } else {
            items(detail.ingredients, key = { "ingredient-${it.id}" }) { ingredient ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = listOf(ingredient.quantity, ingredient.unit, ingredient.item)
                            .filter { it.isNotBlank() }
                            .joinToString(" ")
                            .ifBlank { ingredient.rawText },
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    if (ingredient.parseStatus == "needs_review") {
                        Text(
                            "needs review",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                }
            }
        }

        item { SectionHeader("Instructions") }
        if (detail.instructions.isEmpty()) {
            item { Text("No instructions were extracted for this recipe.", style = MaterialTheme.typography.bodyMedium) }
        } else {
            items(detail.instructions, key = { "instruction-${it.id}" }) { instruction ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "${instruction.displayOrder}.",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    Text(instruction.text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                }
            }
        }

        if (detail.handwrittenNotes.isNotEmpty()) {
            item { SectionHeader("Handwritten notes") }
            items(detail.handwrittenNotes, key = { it.pageId }) { note ->
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    if (note.transcription.isNotBlank()) {
                        Text(note.transcription, style = MaterialTheme.typography.bodyLarge)
                    } else {
                        Text("(transcription pending, status: ${note.status})", style = MaterialTheme.typography.bodyMedium)
                    }
                    if (note.ocrDraft.isNotBlank() && note.ocrDraft != note.transcription) {
                        Text(
                            "OCR draft: ${note.ocrDraft}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        if (detail.pages.isNotEmpty()) {
            item { SectionHeader("Scan pages") }
            item {
                Text(
                    detail.pages.joinToString("\n") { page ->
                        val scan = page.scanFilename ?: page.pageRef
                        val pageNum = page.pageNumber?.let { " (page $it)" }.orEmpty()
                        "$scan$pageNum"
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        item { NotesSection(detail.appState?.personalNotes.orEmpty(), onNotesChanged) }

        item { RawOcrSection(detail.recipe.rawText) }
    }
}

private val PaddingValuesAll16 = androidx.compose.foundation.layout.PaddingValues(16.dp)

@Composable
private fun SourceSection(detail: RecipeDetailUi) {
    Column {
        val publisher = detail.recipe.sourcePublisher
        val url = detail.recipe.sourceUrl
        if (publisher.isBlank() && url.isBlank()) {
            Text("Source: unknown", style = MaterialTheme.typography.bodyMedium)
        } else {
            if (publisher.isNotBlank()) Text(publisher, style = MaterialTheme.typography.bodyMedium)
            if (url.isNotBlank()) {
                Text(url, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
        }
        if (detail.sourceEvidence.isNotEmpty()) {
            detail.sourceEvidence.forEach { evidence ->
                Text(
                    "• ${evidence.evidenceText}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ReviewFlagsSection(flags: List<String>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Unresolved review items" },
    ) {
        Text("Needs review", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.tertiary)
        flags.forEach { flag ->
            Text("• ${flag.replace('_', ' ')}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium)
    HorizontalDivider()
}

@Composable
private fun NotesSection(initialNotes: String, onNotesChanged: (String) -> Unit) {
    var text by remember(initialNotes) { mutableStateOf(initialNotes) }
    Column {
        SectionHeader("Your notes")
        OutlinedTextField(
            value = text,
            onValueChange = {
                text = it
                onNotesChanged(it)
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Add a personal note...") },
        )
    }
}

@Composable
private fun RawOcrSection(rawText: String) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        TextButton(onClick = { expanded = !expanded }) {
            Text(if (expanded) "Hide original OCR" else "Show original OCR")
        }
        if (expanded) {
            Text(
                rawText.ifBlank { "No OCR text available." },
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
