package com.recipearchive.app.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.recipearchive.app.data.repository.RecipeSummary
import com.recipearchive.app.data.local.entity.CollectionEntity
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    widthSizeClass: WindowWidthSizeClass,
    onRecipeClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsState()
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("My Recipe Box", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "Family favorites, notes and discoveries",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        LibraryContent(
            state = state,
            widthSizeClass = widthSizeClass,
            onQueryChange = viewModel::updateQuery,
            onCategorySelected = viewModel::selectCategory,
            onCollectionSelected = viewModel::selectCollection,
            onSortSelected = viewModel::selectSort,
            onRecipeClick = onRecipeClick,
            onToggleFavorite = viewModel::toggleFavorite,
            onRetryImport = viewModel::retryImport,
            modifier = Modifier.padding(padding),
        )
    }
}

@Composable
fun LibraryContent(
    state: LibraryUiState,
    widthSizeClass: WindowWidthSizeClass,
    onQueryChange: (String) -> Unit,
    onCategorySelected: (String?) -> Unit = {},
    onCollectionSelected: (String?) -> Unit = {},
    onSortSelected: (LibrarySort) -> Unit = {},
    onRecipeClick: (String) -> Unit,
    onToggleFavorite: (String, Boolean) -> Unit,
    onRetryImport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
            SearchControls(
                query = state.query,
                onQueryChange = onQueryChange,
                collections = state.collections,
                selectedCollectionId = state.selectedCollectionId,
                onCollectionSelected = onCollectionSelected,
                selectedSort = state.selectedSort,
                onSortSelected = onSortSelected,
            )
            Spacer(Modifier.height(10.dp))
            FilterRow(
                label = "Categories",
                options = state.categories.map { it to it },
                selected = state.selectedCategory,
                onSelected = onCategorySelected,
            )
            if (state.recipes.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                ResultSummary(
                    count = state.recipes.size,
                    isFiltered = state.query.isNotBlank() || state.selectedCategory != null || state.selectedCollectionId != null,
                )
            }
        }
        when {
            state.isImporting && !state.hasImportedOnce -> LoadingState(modifier = Modifier.fillMaxSize())
            state.importError != null && state.recipes.isEmpty() -> ImportErrorState(state.importError, onRetryImport, Modifier.fillMaxSize())
            state.recipes.isEmpty() && state.query.isBlank() && state.selectedCategory == null && state.selectedCollectionId == null -> EmptyLibraryState(modifier = Modifier.fillMaxSize())
            state.recipes.isEmpty() -> NoResultsState(state.query, Modifier.fillMaxSize())
            else -> RecipeResults(state.recipes, widthSizeClass, onRecipeClick, onToggleFavorite, Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun SearchControls(
    query: String,
    onQueryChange: (String) -> Unit,
    collections: List<CollectionEntity>,
    selectedCollectionId: String?,
    onCollectionSelected: (String?) -> Unit,
    selectedSort: LibrarySort,
    onSortSelected: (LibrarySort) -> Unit,
) {
    var showCollectionMenu by remember { mutableStateOf(false) }
    val selectedCollection = collections.firstOrNull { it.id == selectedCollectionId }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SearchField(query = query, onQueryChange = onQueryChange, modifier = Modifier.weight(1f))
        Box {
            Surface(
                modifier = Modifier.size(56.dp).semantics {
                    contentDescription = selectedCollection?.let { "Filtering by collection ${it.name}" }
                        ?: "Filter by collection"
                },
                shape = MaterialTheme.shapes.medium,
                color = if (selectedCollection == null) MaterialTheme.colorScheme.surface
                else MaterialTheme.colorScheme.secondaryContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                onClick = { showCollectionMenu = true },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.Tune,
                        contentDescription = null,
                        tint = if (selectedCollection == null) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
            DropdownMenu(
                expanded = showCollectionMenu,
                onDismissRequest = { showCollectionMenu = false },
            ) {
                DropdownMenuItem(
                    text = { Text("All collections") },
                    onClick = {
                        onCollectionSelected(null)
                        showCollectionMenu = false
                    },
                    leadingIcon = if (selectedCollection == null) {
                        { Icon(Icons.Filled.Check, contentDescription = null) }
                    } else null,
                )
                collections.forEach { collection ->
                    val selected = collection.id == selectedCollectionId
                    DropdownMenuItem(
                        text = { Text(collection.name) },
                        onClick = {
                            onCollectionSelected(collection.id)
                            showCollectionMenu = false
                        },
                        leadingIcon = if (selected) {
                            { Icon(Icons.Filled.Check, contentDescription = null) }
                        } else null,
                    )
                }
            }
        }
        SortSelector(selectedSort = selectedSort, onSortSelected = onSortSelected)
    }
}

@Composable
private fun SortSelector(
    selectedSort: LibrarySort,
    onSortSelected: (LibrarySort) -> Unit,
) {
    var showSortMenu by remember { mutableStateOf(false) }
    Box {
        Surface(
            modifier = Modifier.size(56.dp).semantics {
                contentDescription = "Sort recipes: ${selectedSort.label}"
            },
            shape = MaterialTheme.shapes.medium,
            color = if (selectedSort == LibrarySort.ALPHABETICAL) MaterialTheme.colorScheme.surface
            else MaterialTheme.colorScheme.secondaryContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            onClick = { showSortMenu = true },
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.AutoMirrored.Filled.Sort,
                    contentDescription = null,
                    tint = if (selectedSort == LibrarySort.ALPHABETICAL) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
        DropdownMenu(
            expanded = showSortMenu,
            onDismissRequest = { showSortMenu = false },
        ) {
            LibrarySort.entries.forEach { sort ->
                DropdownMenuItem(
                    text = { Text(sort.label) },
                    onClick = {
                        onSortSelected(sort)
                        showSortMenu = false
                    },
                    leadingIcon = if (sort == selectedSort) {
                        { Icon(Icons.Filled.Check, contentDescription = null) }
                    } else null,
                )
            }
        }
    }
}

@Composable
private fun FilterRow(
    label: String,
    options: List<Pair<String, String>>,
    selected: String?,
    onSelected: (String?) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 2.dp),
        )
        FilterChip(
            selected = selected == null,
            onClick = { onSelected(null) },
            label = { Text("All") },
        )
        options.forEach { (id, name) ->
            FilterChip(
                selected = selected == id,
                onClick = { onSelected(if (selected == id) null else id) },
                label = { Text(name) },
            )
        }
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.semantics { contentDescription = "Search recipes" },
        placeholder = { Text("Search recipes, ingredients or notes") },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotBlank()) IconButton(onClick = { onQueryChange("") }) {
                Icon(Icons.Filled.Close, contentDescription = "Clear search")
            }
        },
        shape = MaterialTheme.shapes.medium,
        singleLine = true,
    )
}

@Composable
private fun ResultSummary(count: Int, isFiltered: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
            Icon(
                Icons.AutoMirrored.Filled.MenuBook,
                contentDescription = null,
                modifier = Modifier.padding(7.dp).size(18.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            if (isFiltered) "$count matching ${if (count == 1) "recipe" else "recipes"}"
            else "$count ${if (count == 1) "recipe" else "recipes"} in your collection",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RecipeResults(
    recipes: List<RecipeSummary>,
    widthSizeClass: WindowWidthSizeClass,
    onRecipeClick: (String) -> Unit,
    onToggleFavorite: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (widthSizeClass == WindowWidthSizeClass.Expanded) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 300.dp),
            modifier = modifier,
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            gridItems(recipes, key = { it.id }) { RecipeCard(it, onRecipeClick, onToggleFavorite) }
        }
    } else {
        LazyColumn(
            modifier = modifier,
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(recipes, key = { it.id }) { RecipeCard(it, onRecipeClick, onToggleFavorite) }
        }
    }
}

@Composable
private fun RecipeCard(recipe: RecipeSummary, onRecipeClick: (String) -> Unit, onToggleFavorite: (String, Boolean) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Recipe: ${recipe.title}" },
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = MaterialTheme.shapes.medium,
        onClick = { onRecipeClick(recipe.id) },
    ) {
        Row(modifier = Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(46.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    recipe.title.firstOrNull()?.uppercase() ?: "R",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(recipe.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Text(
                    recipe.sourcePublisher.ifBlank { "Family recipe archive" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (recipe.category != null || recipe.personalRating != null) {
                    Spacer(Modifier.height(7.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        recipe.category?.let { category ->
                            Text(
                                category,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (recipe.personalRating == null) Icons.Filled.StarBorder else Icons.Filled.Star,
                                contentDescription = if (recipe.personalRating == null) "Not rated" else "Rated ${recipe.personalRating} out of 5",
                                tint = if (recipe.personalRating == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(3.dp))
                            Text(
                                recipe.personalRating?.toString() ?: "–",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                if (recipe.madeCount > 0) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        buildList {
                            add("Made ${recipe.madeCount}×")
                            recipe.averageDurationMillis?.let { add(formatCompactDuration(it)) }
                            recipe.lastMadeAt?.let { add(formatLastMade(it)) }
                        }.joinToString(" · "),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (recipe.hasReviewFlags) {
                    Spacer(Modifier.height(8.dp))
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.tertiaryContainer) {
                        Row(modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Warning, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onTertiaryContainer)
                            Spacer(Modifier.width(5.dp))
                            Text("Check details", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
                        }
                    }
                }
            }
            IconButton(onClick = { onToggleFavorite(recipe.id, recipe.isFavorite) }) {
                Icon(
                    if (recipe.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = if (recipe.isFavorite) "Remove from favorites" else "Add to favorites",
                    tint = if (recipe.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun formatCompactDuration(milliseconds: Long): String {
    val minutes = (milliseconds / 60_000).coerceAtLeast(1)
    return if (minutes >= 60) "${minutes / 60} hr ${minutes % 60} min" else "$minutes min"
}

private fun formatLastMade(timestamp: Long): String = Instant.ofEpochMilli(timestamp)
    .atZone(ZoneId.systemDefault())
    .toLocalDate()
    .format(DateTimeFormatter.ofPattern("MMM d"))

@Composable
private fun LoadingState(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Text("Preparing your recipe box…", modifier = Modifier.padding(top = 16.dp))
        }
    }
}

@Composable
private fun ImportErrorState(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.errorContainer) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(28.dp)) {
                Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                Text("We couldn't load your recipes", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 10.dp))
                Text(message, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp))
                TextButton(onClick = onRetry, modifier = Modifier.padding(top = 10.dp)) { Text("Retry import") }
            }
        }
    }
}

@Composable
private fun EmptyLibraryState(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
            Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, modifier = Modifier.size(44.dp), tint = MaterialTheme.colorScheme.primary)
            Text("Your recipe box is empty", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 12.dp))
            Text("No recipes yet. Once a bundle is imported, your recipes will appear here.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
private fun NoResultsState(query: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
            Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(38.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                if (query.isBlank()) "No recipes match these filters." else "No recipes match \"$query\".",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 12.dp),
            )
            Text("Try another search or select All to clear a filter.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
