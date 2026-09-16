package com.recipearchive.app.ui.webimport

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Read-only "view before importing" screen for a NYT search/featured result -- fetches
 * and shows the full recipe formatted like the saved-recipe DetailScreen (same
 * ingredients-left/steps-right layout on wide screens, same card language), with an
 * Import button so a recipe can be looked over without committing to save it first.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NytRecipePreviewScreen(
    viewModel: ImportViewModel,
    widthSizeClass: WindowWidthSizeClass,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val preview by viewModel.nytPreviewState.collectAsState()
    val useTwoColumns = widthSizeClass == WindowWidthSizeClass.Expanded

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        preview?.title ?: "Recipe",
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        val current = preview
        when {
            current == null || current.isLoading -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            current.error != null -> Box(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    current.error,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            useTwoColumns -> Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .wrapContentWidth(Alignment.CenterHorizontally)
                    .widthIn(max = 1160.dp)
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                PreviewHero(current, onImport = viewModel::importNytPreview)
                Row(modifier = Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    LazyColumn(
                        modifier = Modifier.weight(0.35f).fillMaxHeight(),
                        contentPadding = PaddingValues(bottom = 20.dp),
                    ) {
                        item { PreviewIngredientsCard(current) }
                    }
                    LazyColumn(
                        modifier = Modifier.weight(0.65f).fillMaxHeight(),
                        contentPadding = PaddingValues(bottom = 20.dp),
                    ) {
                        item { PreviewInstructionsCard(current) }
                    }
                }
            }
            else -> LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize().wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = 940.dp),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                item { PreviewHero(current, onImport = viewModel::importNytPreview) }
                item { PreviewIngredientsCard(current) }
                item { PreviewInstructionsCard(current) }
            }
        }
    }
}

@Composable
private fun PreviewHero(preview: NytPreviewUiState, onImport: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(preview.title, style = MaterialTheme.typography.titleLarge)
                Text(
                    preview.byline?.let { "NYT Cooking · $it" } ?: "NYT Cooking",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(12.dp))
            when {
                preview.imported -> Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("In your library", color = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                }
                preview.isImporting -> Button(onClick = {}, enabled = false) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(10.dp))
                    Text("Importing…")
                }
                else -> Button(onClick = onImport) {
                    Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Import")
                }
            }
        }
    }
}

@Composable
private fun PreviewIngredientsCard(preview: NytPreviewUiState) {
    PreviewContentCard(title = "Ingredients", icon = Icons.Filled.RestaurantMenu) {
        if (preview.ingredients.isEmpty()) {
            Text("No ingredients were found for this recipe.", style = MaterialTheme.typography.bodyMedium)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                preview.ingredients.forEach { ingredient ->
                    Text(ingredient, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

@Composable
private fun PreviewInstructionsCard(preview: NytPreviewUiState) {
    PreviewContentCard(title = "Instructions", icon = Icons.AutoMirrored.Filled.MenuBook) {
        if (preview.instructions.isEmpty()) {
            Text("No instructions were found for this recipe.", style = MaterialTheme.typography.bodyMedium)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                preview.instructions.forEachIndexed { index, step ->
                    PreviewInstructionRow(index + 1, step)
                }
            }
        }
    }
}

@Composable
private fun PreviewContentCard(title: String, icon: ImageVector, content: @Composable () -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Text(title, style = MaterialTheme.typography.titleLarge)
            }
            Spacer(Modifier.size(14.dp))
            content()
        }
    }
}

@Composable
private fun PreviewInstructionRow(order: Int, text: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier.size(32.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                order.toString(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.width(14.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
    }
}
