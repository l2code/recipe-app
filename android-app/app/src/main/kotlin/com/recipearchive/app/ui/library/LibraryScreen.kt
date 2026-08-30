package com.recipearchive.app.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.recipearchive.app.data.repository.RecipeSummary

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
        topBar = {
            TopAppBar(title = { Text("Recipe Archive") })
        },
    ) { padding ->
        LibraryContent(
            state = state,
            widthSizeClass = widthSizeClass,
            onQueryChange = viewModel::updateQuery,
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
    onRecipeClick: (String) -> Unit,
    onToggleFavorite: (String, Boolean) -> Unit,
    onRetryImport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        SearchField(
            query = state.query,
            onQueryChange = onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )

        when {
            state.isImporting && !state.hasImportedOnce -> LoadingState(modifier = Modifier.fillMaxSize())
            state.importError != null && state.recipes.isEmpty() -> ImportErrorState(
                message = state.importError,
                onRetry = onRetryImport,
                modifier = Modifier.fillMaxSize(),
            )
            state.recipes.isEmpty() && state.query.isBlank() -> EmptyLibraryState(modifier = Modifier.fillMaxSize())
            state.recipes.isEmpty() -> NoResultsState(query = state.query, modifier = Modifier.fillMaxSize())
            else -> RecipeResults(
                recipes = state.recipes,
                widthSizeClass = widthSizeClass,
                onRecipeClick = onRecipeClick,
                onToggleFavorite = onToggleFavorite,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit, modifier: Modifier = Modifier) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.semantics { contentDescription = "Search recipes" },
        placeholder = { Text("Search titles, ingredients, instructions...") },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        singleLine = true,
    )
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
            columns = GridCells.Adaptive(minSize = 280.dp),
            modifier = modifier,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            gridItems(recipes, key = { it.id }) { recipe ->
                RecipeCard(recipe, onRecipeClick, onToggleFavorite)
            }
        }
    } else {
        LazyColumn(
            modifier = modifier,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(recipes, key = { it.id }) { recipe ->
                RecipeCard(recipe, onRecipeClick, onToggleFavorite)
            }
        }
    }
}

@Composable
private fun RecipeCard(
    recipe: RecipeSummary,
    onRecipeClick: (String) -> Unit,
    onToggleFavorite: (String, Boolean) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Recipe: ${recipe.title}" },
        tonalElevation = 1.dp,
        shape = MaterialTheme.shapes.medium,
        onClick = { onRecipeClick(recipe.id) },
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            androidx.compose.foundation.layout.Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = recipe.title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                if (recipe.hasReviewFlags) {
                    Icon(
                        Icons.Filled.Warning,
                        contentDescription = "Needs review",
                        tint = MaterialTheme.colorScheme.tertiary,
                    )
                }
                IconButton(onClick = { onToggleFavorite(recipe.id, recipe.isFavorite) }) {
                    Icon(
                        imageVector = if (recipe.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = if (recipe.isFavorite) "Remove from favorites" else "Add to favorites",
                    )
                }
            }
            if (recipe.sourcePublisher.isNotBlank()) {
                Text(
                    text = recipe.sourcePublisher,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun LoadingState(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Text("Importing recipes…", modifier = Modifier.padding(top = 16.dp))
        }
    }
}

@Composable
private fun ImportErrorState(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
            Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            Text(
                "Import failed",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(message, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
            androidx.compose.material3.TextButton(onClick = onRetry, modifier = Modifier.padding(top = 12.dp)) {
                Text("Retry import")
            }
        }
    }
}

@Composable
private fun EmptyLibraryState(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(
            "No recipes yet. Once a bundle is imported, your recipes will appear here.",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(24.dp),
        )
    }
}

@Composable
private fun NoResultsState(query: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(
            "No recipes match \"$query\".",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(24.dp),
        )
    }
}
