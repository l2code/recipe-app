package com.recipearchive.app.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.StickyNote2
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.recipearchive.app.data.repository.RecipeDetailUi
import com.recipearchive.app.data.organization.RecipeCategories

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    viewModel: DetailViewModel,
    widthSizeClass: WindowWidthSizeClass,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val detail by viewModel.uiState.collectAsState()
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Recipe", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to library")
                    }
                },
                actions = {
                    detail?.let { current ->
                        val favorite = current.appState?.isFavorite ?: false
                        IconButton(onClick = { viewModel.toggleFavorite(favorite) }) {
                            Icon(
                                if (favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                contentDescription = if (favorite) "Remove from favorites" else "Add to favorites",
                                tint = if (favorite) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        detail?.let { current ->
            DetailContent(
                detail = current,
                onNotesChanged = viewModel::updateNotes,
                onRatingChanged = viewModel::updateRating,
                onCategoryChanged = viewModel::updateCategory,
                onCollectionChanged = viewModel::updateCollection,
                onCollectionCreated = viewModel::createCollection,
                useTwoColumns = widthSizeClass == WindowWidthSizeClass.Expanded,
                modifier = Modifier.padding(padding),
            )
        } ?: Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    }
}

@Composable
fun DetailContent(
    detail: RecipeDetailUi,
    onNotesChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
    onRatingChanged: (Int?) -> Unit = {},
    onCategoryChanged: (String?) -> Unit = {},
    onCollectionChanged: (String, Boolean) -> Unit = { _, _ -> },
    onCollectionCreated: (String) -> Unit = {},
    useTwoColumns: Boolean = false,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = 940.dp),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            RecipeHero(
                detail = detail,
                onRatingChanged = onRatingChanged,
                onCategoryChanged = onCategoryChanged,
                onCollectionChanged = onCollectionChanged,
                onCollectionCreated = onCollectionCreated,
            )
        }
        if (detail.reviewFlags.isNotEmpty()) item { ReviewFlagsSection(detail.reviewFlags.map { it.flagValue }) }

        if (useTwoColumns) {
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                    Box(modifier = Modifier.weight(0.42f)) { IngredientsCard(detail) }
                    Box(modifier = Modifier.weight(0.58f)) { InstructionsCard(detail) }
                }
            }
        } else {
            item { IngredientsCard(detail) }
            item { InstructionsCard(detail) }
        }

        if (detail.handwrittenNotes.isNotEmpty()) {
            item { HandwrittenNotesCard(detail) }
        }
        if (detail.pages.isNotEmpty()) item { ScanPagesCard(detail) }
        item { NotesSection(detail.appState?.personalNotes.orEmpty(), onNotesChanged) }
        item { SourceDetailsSection(detail) }
        item { RawOcrSection(detail.recipe.rawText) }
    }
}

@Composable
private fun RecipeHero(
    detail: RecipeDetailUi,
    onRatingChanged: (Int?) -> Unit,
    onCategoryChanged: (String?) -> Unit,
    onCollectionChanged: (String, Boolean) -> Unit,
    onCollectionCreated: (String) -> Unit,
) {
    var showOrganizer by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Text(detail.recipe.title, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Spacer(Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    detail.recipe.sourcePublisher.ifBlank { "Source unknown" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f),
                    modifier = Modifier.weight(1f),
                )
                RatingRow(detail.appState?.personalRating, onRatingChanged)
            }
            TextButton(
                onClick = { showOrganizer = true },
                modifier = Modifier.padding(top = 2.dp),
            ) {
                val category = detail.appState?.category ?: "Choose category"
                val collectionCount = detail.collectionIds.size
                Text("$category · $collectionCount ${if (collectionCount == 1) "collection" else "collections"} · Organize")
            }
        }
    }
    if (showOrganizer) {
        OrganizerDialog(
            detail = detail,
            onDismiss = { showOrganizer = false },
            onCategoryChanged = onCategoryChanged,
            onCollectionChanged = onCollectionChanged,
            onCollectionCreated = onCollectionCreated,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OrganizerDialog(
    detail: RecipeDetailUi,
    onDismiss: () -> Unit,
    onCategoryChanged: (String?) -> Unit,
    onCollectionChanged: (String, Boolean) -> Unit,
    onCollectionCreated: (String) -> Unit,
) {
    var newCollectionName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Organize recipe") },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Text("Category", style = MaterialTheme.typography.titleSmall)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        RecipeCategories.all.forEach { category ->
                            FilterChip(
                                selected = detail.appState?.category == category,
                                onClick = { onCategoryChanged(category) },
                                label = { Text(category) },
                            )
                        }
                    }
                }
                item {
                    Text("Collections", style = MaterialTheme.typography.titleSmall)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        detail.collections.forEach { collection ->
                            val selected = collection.id in detail.collectionIds
                            FilterChip(
                                selected = selected,
                                onClick = { onCollectionChanged(collection.id, !selected) },
                                label = { Text(collection.name) },
                            )
                        }
                    }
                }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = newCollectionName,
                            onValueChange = { newCollectionName = it },
                            modifier = Modifier.weight(1f),
                            label = { Text("New collection") },
                            singleLine = true,
                        )
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = {
                                onCollectionCreated(newCollectionName)
                                newCollectionName = ""
                            },
                            enabled = newCollectionName.isNotBlank(),
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("Add")
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

@Composable
private fun RatingRow(rating: Int?, onRatingChanged: (Int?) -> Unit) {
    Row(modifier = Modifier.semantics { contentDescription = "Recipe rating" }) {
        (1..5).forEach { value ->
            IconButton(
                onClick = { onRatingChanged(if (rating == value) null else value) },
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    if ((rating ?: 0) >= value) Icons.Filled.Star else Icons.Filled.StarBorder,
                    contentDescription = "$value stars",
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

@Composable
private fun IngredientsCard(detail: RecipeDetailUi) {
    ContentCard(title = "Ingredients", icon = Icons.Filled.RestaurantMenu) {
        if (detail.ingredients.isEmpty()) {
            Text("No ingredients were extracted for this recipe.", style = MaterialTheme.typography.bodyMedium)
        } else {
            val reviewCount = detail.ingredients.count { it.parseStatus == "needs_review" }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (reviewCount > 0) {
                    Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.tertiaryContainer) {
                        Text(
                            "$reviewCount ${if (reviewCount == 1) "line may" else "lines may"} need cleanup",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                }
                detail.ingredients.forEach { ingredient ->
                    IngredientRow(
                        listOf(ingredient.quantity, ingredient.unit, ingredient.item)
                            .filter { it.isNotBlank() }.joinToString(" ").ifBlank { ingredient.rawText },
                    )
                }
            }
        }
    }
}

@Composable
private fun InstructionsCard(detail: RecipeDetailUi) {
    ContentCard(title = "Instructions", icon = Icons.AutoMirrored.Filled.MenuBook) {
        if (detail.instructions.isEmpty()) {
            Text("No instructions were extracted for this recipe.", style = MaterialTheme.typography.bodyMedium)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                detail.instructions.forEach { instruction ->
                    InstructionRow(instruction.displayOrder, instruction.text)
                }
            }
        }
    }
}

@Composable
private fun ContentCard(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, content: @Composable () -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
                    Icon(icon, contentDescription = null, modifier = Modifier.padding(7.dp).size(18.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                }
                Spacer(Modifier.width(10.dp))
                Text(title, style = MaterialTheme.typography.titleLarge)
            }
            Spacer(Modifier.height(14.dp))
            content()
        }
    }
}

@Composable
private fun IngredientRow(text: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Box(Modifier.padding(top = 9.dp).size(7.dp).background(MaterialTheme.colorScheme.secondary, CircleShape))
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun InstructionRow(order: Int, text: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier.size(32.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(order.toString(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(14.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun ReviewFlagsSection(flags: List<String>) {
    Surface(
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Unresolved review items" },
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.tertiaryContainer,
    ) {
        Row(modifier = Modifier.padding(18.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
            Spacer(Modifier.width(12.dp))
            Column {
                Text("A quick check would help", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
                flags.forEach { Text("• ${it.replace('_', ' ')}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer) }
            }
        }
    }
}

@Composable
private fun HandwrittenNotesCard(detail: RecipeDetailUi) {
    ContentCard(title = "Notes from the original", icon = Icons.AutoMirrored.Filled.StickyNote2) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            detail.handwrittenNotes.forEach { note ->
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            note.transcription.ifBlank { "Transcription pending" },
                            style = MaterialTheme.typography.bodyLarge,
                            fontStyle = if (note.transcription.isBlank()) FontStyle.Italic else FontStyle.Normal,
                        )
                        if (note.ocrDraft.isNotBlank() && note.ocrDraft != note.transcription) {
                            Text("OCR draft: ${note.ocrDraft}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScanPagesCard(detail: RecipeDetailUi) {
    ContentCard(title = "Original pages", icon = Icons.Filled.Description) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            detail.pages.forEach { page ->
                val scan = page.scanFilename ?: page.pageRef
                val pageNum = page.pageNumber?.let { "Page $it" }.orEmpty()
                Text(if (pageNum.isBlank()) scan else "$pageNum · $scan", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun NotesSection(initialNotes: String, onNotesChanged: (String) -> Unit) {
    var text by remember(initialNotes) { mutableStateOf(initialNotes) }
    ContentCard(title = "Your notes", icon = Icons.Filled.EditNote) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it; onNotesChanged(it) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("What would you change next time?") },
            minLines = 3,
            shape = MaterialTheme.shapes.medium,
        )
    }
}

@Composable
private fun SourceDetailsSection(detail: RecipeDetailUi) {
    var expanded by remember { mutableStateOf(false) }
    val publisher = detail.recipe.sourcePublisher
    val url = detail.recipe.sourceUrl
    Column(modifier = Modifier.fillMaxWidth()) {
        TextButton(onClick = { expanded = !expanded }) {
            Text(if (expanded) "Hide source details" else "Source details")
        }
        if (expanded) {
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(publisher.ifBlank { "Source not yet identified" }, style = MaterialTheme.typography.titleMedium)
                    if (url.isNotBlank()) Text(url, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    detail.sourceEvidence
                        .map { it.evidenceText.trim() }
                        .filter { it.isNotBlank() && !it.equals(url, ignoreCase = true) && !it.equals(publisher, ignoreCase = true) }
                        .distinct()
                        .forEach { evidence ->
                            Text(evidence, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                }
            }
        }
    }
}

@Composable
private fun RawOcrSection(rawText: String) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth()) {
        TextButton(onClick = { expanded = !expanded }) {
            Text(if (expanded) "Hide original OCR" else "Show original OCR")
        }
        if (expanded) {
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant) {
                Text(rawText.ifBlank { "No OCR text available." }, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(18.dp))
            }
        }
    }
}
