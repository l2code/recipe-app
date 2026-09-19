package com.recipearchive.app.ui.webimport

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
 * Full-screen NYT Cooking search. Every row gets a one-tap import icon; recipes already
 * in the library show a disabled checkmark instead so the same recipe can't be imported
 * twice. Rating filter and sort order are both applied client-side, since NYT's own
 * search page doesn't support either.
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::onNytSearchQueryChanged,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Search recipes…") },
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
                Spacer(Modifier.width(4.dp))
                DropdownSelector(
                    selectedLabel = RATING_OPTIONS.first { it.first == state.minRating }.second,
                    options = RATING_OPTIONS,
                    onSelected = viewModel::setNytMinRating,
                )
                DropdownSelector(
                    selectedLabel = SORT_OPTIONS.first { it.first == state.sortOrder }.second,
                    options = SORT_OPTIONS,
                    onSelected = viewModel::setNytSortOrder,
                )
            }
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
            if (!state.hasSearched) {
                InfoText("Search NYT Cooking above to find a recipe to import.")
            } else {
                Text("Search results", style = MaterialTheme.typography.titleMedium)
                val visible = sortResults(
                    state.results.filter { (it.rating ?: 0) >= state.minRating },
                    state.sortOrder,
                )
                when {
                    state.isSearching -> LoadingBox()
                    state.searchError != null -> InfoText(state.searchError!!, isError = true)
                    visible.isEmpty() -> InfoText(
                        if (state.minRating > 0) {
                            "No ${state.minRating}+ star recipes found for \"${state.query}\"."
                        } else {
                            "No recipes found for \"${state.query}\". Try a different search."
                        },
                    )
                    else -> NytResultList(
                        results = visible,
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

private val RATING_OPTIONS = listOf(
    0 to "All ratings",
    5 to "5★",
    4 to "4★+",
    3 to "3★+",
    2 to "2★+",
    1 to "1★+",
)

private val SORT_OPTIONS = listOf(
    NytSortOrder.NONE to "Sort",
    NytSortOrder.TITLE_ASC to "A–Z",
    NytSortOrder.TITLE_DESC to "Z–A",
    NytSortOrder.REVIEWS_DESC to "Most reviews",
    NytSortOrder.REVIEWS_ASC to "Fewest reviews",
)

private fun sortResults(results: List<NytSearchResult>, order: NytSortOrder): List<NytSearchResult> = when (order) {
    NytSortOrder.NONE -> results
    NytSortOrder.TITLE_ASC -> results.sortedBy { it.title.lowercase() }
    NytSortOrder.TITLE_DESC -> results.sortedByDescending { it.title.lowercase() }
    NytSortOrder.REVIEWS_ASC -> results.sortedBy { it.reviewCount ?: 0 }
    NytSortOrder.REVIEWS_DESC -> results.sortedByDescending { it.reviewCount ?: 0 }
}

@Composable
private fun <T> DropdownSelector(selectedLabel: String, options: List<Pair<T, String>>, onSelected: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }) {
            Text(selectedLabel)
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (value, label) ->
                DropdownMenuItem(text = { Text(label) }, onClick = { onSelected(value); expanded = false })
            }
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
                modifier = Modifier.weight(1f),
            )
            if (result.rating != null) {
                Spacer(Modifier.width(8.dp))
                RatingStars(result.rating, result.reviewCount)
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
                maxLines = 1,
            )
        }
    }
}
