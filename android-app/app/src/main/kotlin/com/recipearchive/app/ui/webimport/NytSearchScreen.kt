package com.recipearchive.app.ui.webimport

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.recipearchive.app.data.webimport.NytSearchResult

/**
 * Full-screen NYT Cooking search: a search field over a "Today on NYT Cooking" list
 * pulled from their homepage, replaced by search results once a query runs. Every row
 * gets a one-tap import icon; recipes already in the library show a disabled checkmark
 * instead so the same recipe can't be imported twice.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NytSearchScreen(
    viewModel: ImportViewModel,
    onBack: () -> Unit,
    onOpenPreview: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.nytSearchState.collectAsState()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        viewModel.loadNytFeaturedIfNeeded()
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("NYT Cooking", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(onTap = {
                        keyboardController?.hide()
                        focusManager.clearFocus()
                    })
                }
                .padding(padding)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::onNytSearchQueryChanged,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Search recipes, e.g. chicken tagine") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        keyboardController?.hide()
                        viewModel.runNytSearch()
                    }),
                    trailingIcon = if (state.query.isNotBlank()) {
                        {
                            IconButton(onClick = viewModel::clearNytSearch) {
                                Icon(Icons.Filled.Close, contentDescription = "Clear search")
                            }
                        }
                    } else {
                        null
                    },
                )
                IconButton(onClick = viewModel::runNytSearch, enabled = state.query.isNotBlank() && !state.isSearching) {
                    Icon(Icons.Filled.Search, contentDescription = "Search")
                }
            }
            RatingFilterRow(selected = state.minRating, onSelected = viewModel::setNytMinRating)
            if (state.message != null) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    onClick = viewModel::dismissNytSearchMessage,
                ) {
                    Text(
                        state.message!!,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
            if (state.hasSearched) {
                Text("Search results", style = MaterialTheme.typography.titleMedium)
                val filtered = state.results.filter { (it.rating ?: 0) >= state.minRating }
                when {
                    state.isSearching -> LoadingBox()
                    state.searchError != null -> InfoText(state.searchError!!, isError = true)
                    filtered.isEmpty() -> InfoText(
                        if (state.minRating > 0) {
                            "No ${state.minRating}+ star recipes found for \"${state.query}\"."
                        } else {
                            "No recipes found for \"${state.query}\". Try a different search."
                        },
                    )
                    else -> NytResultList(
                        results = filtered,
                        existingUrls = state.existingUrls,
                        importingUrls = state.importingUrls,
                        onImport = viewModel::importNytResult,
                        onOpenPreview = { result -> viewModel.openNytPreview(result); onOpenPreview() },
                    )
                }
            } else {
                Text("Today on NYT Cooking", style = MaterialTheme.typography.titleMedium)
                val filtered = state.featured.filter { (it.rating ?: 0) >= state.minRating }
                when {
                    state.isFeaturedLoading -> LoadingBox()
                    state.featuredError != null -> InfoText(state.featuredError!!, isError = true)
                    filtered.isEmpty() -> InfoText(
                        if (state.minRating > 0) "No ${state.minRating}+ star recipes right now." else "Nothing to show right now.",
                    )
                    else -> NytResultList(
                        results = filtered,
                        existingUrls = state.existingUrls,
                        importingUrls = state.importingUrls,
                        onImport = viewModel::importNytResult,
                        onOpenPreview = { result -> viewModel.openNytPreview(result); onOpenPreview() },
                    )
                }
            }
        }
    }
}

@Composable
private fun RatingFilterRow(selected: Int, onSelected: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(0 to "All", 3 to "3★+", 4 to "4★+", 5 to "5★").forEach { (rating, label) ->
            FilterChip(selected = selected == rating, onClick = { onSelected(rating) }, label = { Text(label) })
        }
    }
}

@Composable
private fun LoadingBox() {
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun InfoText(text: String, isError: Boolean = false) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 12.dp),
    )
}

@Composable
private fun NytResultList(
    results: List<NytSearchResult>,
    existingUrls: Set<String>,
    importingUrls: Set<String>,
    onImport: (NytSearchResult) -> Unit,
    onOpenPreview: (NytSearchResult) -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(results, key = { it.url }) { result ->
            NytResultRow(
                result = result,
                alreadyImported = result.url in existingUrls,
                isImporting = result.url in importingUrls,
                onImport = { onImport(result) },
                onOpenPreview = { onOpenPreview(result) },
            )
        }
    }
}

@Composable
private fun NytResultRow(
    result: NytSearchResult,
    alreadyImported: Boolean,
    isImporting: Boolean,
    onImport: () -> Unit,
    onOpenPreview: () -> Unit,
) {
    Surface(onClick = onOpenPreview, shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(40.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        result.title.firstOrNull()?.uppercase() ?: "?",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    buildAnnotatedString {
                        append(result.title)
                        if (result.byline != null) {
                            append("  ")
                            withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.onSurfaceVariant)) {
                                append("(${result.byline})")
                            }
                        }
                    },
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (result.rating != null) {
                    Spacer(Modifier.height(2.dp))
                    RatingStars(result.rating, result.reviewCount)
                }
            }
            Spacer(Modifier.width(8.dp))
            when {
                isImporting -> CircularProgressIndicator(modifier = Modifier.size(24.dp))
                alreadyImported -> IconButton(onClick = {}, enabled = false) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = "Already in your library",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                else -> IconButton(onClick = onImport) {
                    Icon(Icons.Filled.Download, contentDescription = "Import ${result.title}", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
private fun RatingStars(rating: Int, reviewCount: Int?) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.semantics {
            contentDescription = "$rating out of 5 stars" + (reviewCount?.let { ", $it ratings" } ?: "")
        },
    ) {
        repeat(5) { index ->
            Icon(
                if (index < rating) Icons.Filled.Star else Icons.Filled.StarBorder,
                contentDescription = null,
                modifier = Modifier.size(13.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        if (reviewCount != null) {
            Spacer(Modifier.width(4.dp))
            Text(
                "(${"%,d".format(reviewCount)})",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
