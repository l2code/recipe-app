package com.recipearchive.app.ui.webimport

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.recipearchive.app.data.webimport.FetchAndParseOutcome
import com.recipearchive.app.data.webimport.ImportHistoryEntryUi
import com.recipearchive.app.data.webimport.NytSearchOutcome
import com.recipearchive.app.data.webimport.NytSearchResult
import com.recipearchive.app.data.webimport.NytSearchService
import com.recipearchive.app.data.webimport.ParsedRecipe
import com.recipearchive.app.data.webimport.SavedLinkUi
import com.recipearchive.app.data.webimport.WebImportOutcome
import com.recipearchive.app.data.webimport.WebRecipeImportService
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class QuickSource { PASTE_TEXT, SAVED_LINK }

enum class NytSortOrder { NONE, TITLE_ASC, TITLE_DESC, REVIEWS_ASC, REVIEWS_DESC }

data class ImportUiState(
    val url: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String? = null,
    val pasteTextDialogOpen: Boolean = false,
    val pastedText: String = "",
    val savedLinksExpanded: Boolean = false,
)

/** Backs the full-screen "Search NYT Cooking" destination. */
data class NytSearchUiState(
    val query: String = "",
    val results: List<NytSearchResult> = emptyList(),
    val isSearching: Boolean = false,
    val searchError: String? = null,
    val hasSearched: Boolean = false,
    // URLs already in the library -- these recipes show a disabled/checked import icon.
    val existingUrls: Set<String> = emptySet(),
    // URLs with an import currently in flight -- these show a spinner.
    val importingUrls: Set<String> = emptySet(),
    val message: String? = null,
    // Minimum star rating to show, applied client-side; 0 = no filter.
    val minRating: Int = 0,
    val sortOrder: NytSortOrder = NytSortOrder.NONE,
)

/** Backs the read-only "view before importing" screen opened from a search/featured row. */
data class NytPreviewUiState(
    val url: String,
    val title: String,
    val byline: String?,
    val ingredients: List<String> = emptyList(),
    val instructions: List<String> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val isImporting: Boolean = false,
    val imported: Boolean = false,
)

data class PreviewUiState(
    val url: String,
    val domain: String,
    val publisher: String,
    val title: String,
    val ingredientsText: String,
    val instructionsText: String,
    val isSaving: Boolean = false,
)

sealed class ImportEvent {
    data class Imported(val recipeId: String) : ImportEvent()
}

class ImportViewModel(
    private val webRecipeImportService: WebRecipeImportService,
    private val nytSearchService: NytSearchService = NytSearchService(),
) : ViewModel() {
    private val _uiState = MutableStateFlow(ImportUiState())
    val uiState: StateFlow<ImportUiState> = _uiState.asStateFlow()

    private val _previewState = MutableStateFlow<PreviewUiState?>(null)
    val previewState: StateFlow<PreviewUiState?> = _previewState.asStateFlow()

    private val _nytSearchState = MutableStateFlow(NytSearchUiState())
    val nytSearchState: StateFlow<NytSearchUiState> = _nytSearchState.asStateFlow()

    private val _nytPreviewState = MutableStateFlow<NytPreviewUiState?>(null)
    val nytPreviewState: StateFlow<NytPreviewUiState?> = _nytPreviewState.asStateFlow()

    val savedLinks: StateFlow<List<SavedLinkUi>> = webRecipeImportService.observeSavedLinks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val history: StateFlow<List<ImportHistoryEntryUi>> = webRecipeImportService.observeHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _events = Channel<ImportEvent>(Channel.BUFFERED)
    val events: Flow<ImportEvent> = _events.receiveAsFlow()

    fun onUrlChanged(url: String) {
        _uiState.update { it.copy(url = url, errorMessage = null) }
    }

    fun onQuickSourceSelected(source: QuickSource) {
        when (source) {
            QuickSource.PASTE_TEXT -> _uiState.update { it.copy(pasteTextDialogOpen = true, infoMessage = null) }
            QuickSource.SAVED_LINK -> _uiState.update {
                it.copy(savedLinksExpanded = !it.savedLinksExpanded, infoMessage = null)
            }
        }
    }

    // --- NYT Cooking search screen -----------------------------------------------------

    fun onNytSearchQueryChanged(query: String) {
        _nytSearchState.update { it.copy(query = query) }
    }

    fun runNytSearch() {
        val query = _nytSearchState.value.query.trim()
        if (query.isBlank()) return
        _nytSearchState.update { it.copy(isSearching = true, searchError = null) }
        viewModelScope.launch {
            when (val outcome = nytSearchService.search(query)) {
                is NytSearchOutcome.Success -> {
                    val existing = webRecipeImportService.findExistingUrls(outcome.results.map { it.url })
                    _nytSearchState.update {
                        it.copy(
                            isSearching = false,
                            results = outcome.results,
                            hasSearched = true,
                            existingUrls = it.existingUrls + existing,
                        )
                    }
                }
                is NytSearchOutcome.NetworkError -> _nytSearchState.update {
                    it.copy(isSearching = false, hasSearched = true, searchError = "Couldn't reach NYT Cooking: ${outcome.message}")
                }
            }
        }
    }

    /** One-tap import for a search/featured result. Stays on the search screen so several
     *  recipes can be imported in a row; the row switches to a disabled/checked icon after. */
    fun importNytResult(result: NytSearchResult) {
        val state = _nytSearchState.value
        if (result.url in state.existingUrls || result.url in state.importingUrls) return
        _nytSearchState.update { it.copy(importingUrls = it.importingUrls + result.url, message = null) }
        viewModelScope.launch {
            val outcome = webRecipeImportService.importFromUrl(result.url, sourcePublisherOverride = "NYT Cooking")
            _nytSearchState.update { current ->
                val importing = current.importingUrls - result.url
                when (outcome) {
                    is WebImportOutcome.Success -> current.copy(
                        importingUrls = importing,
                        existingUrls = current.existingUrls + result.url,
                        message = "Imported \"${outcome.title}\"",
                    )
                    is WebImportOutcome.NotFound -> current.copy(importingUrls = importing, message = "Couldn't find a recipe there.")
                    is WebImportOutcome.NetworkError -> current.copy(importingUrls = importing, message = "Couldn't reach that page: ${outcome.message}")
                    is WebImportOutcome.ParseError -> current.copy(importingUrls = importing, message = outcome.message)
                }
            }
        }
    }

    fun dismissNytSearchMessage() {
        _nytSearchState.update { it.copy(message = null) }
    }

    /** Clears the current search back to the empty pre-search state. */
    fun clearNytSearch() {
        _nytSearchState.update { it.copy(query = "", results = emptyList(), hasSearched = false, searchError = null) }
    }

    /** Minimum star rating to show, applied client-side; pass 0 to clear the filter. */
    fun setNytMinRating(rating: Int) {
        _nytSearchState.update { it.copy(minRating = rating) }
    }

    fun setNytSortOrder(order: NytSortOrder) {
        _nytSearchState.update { it.copy(sortOrder = order) }
    }

    /** Opens the read-only preview for a search/featured row and fetches its full recipe. */
    fun openNytPreview(result: NytSearchResult) {
        val alreadyImported = result.url in _nytSearchState.value.existingUrls
        _nytPreviewState.value = NytPreviewUiState(
            url = result.url,
            title = result.title,
            byline = result.byline,
            isLoading = true,
            imported = alreadyImported,
        )
        viewModelScope.launch {
            when (val outcome = webRecipeImportService.fetchAndParse(result.url, sourcePublisherOverride = "NYT Cooking")) {
                is FetchAndParseOutcome.Success -> _nytPreviewState.update {
                    it?.copy(
                        isLoading = false,
                        title = outcome.parsed.title.ifBlank { it.title },
                        ingredients = outcome.parsed.ingredients,
                        instructions = outcome.parsed.instructions,
                    )
                }
                is FetchAndParseOutcome.NotFound -> _nytPreviewState.update {
                    it?.copy(isLoading = false, error = "Couldn't find a recipe there.")
                }
                is FetchAndParseOutcome.NetworkError -> _nytPreviewState.update {
                    it?.copy(isLoading = false, error = "Couldn't reach that page: ${outcome.message}")
                }
                is FetchAndParseOutcome.ParseError -> _nytPreviewState.update {
                    it?.copy(isLoading = false, error = outcome.message)
                }
            }
        }
    }

    fun dismissNytPreview() {
        _nytPreviewState.value = null
    }

    fun importNytPreview() {
        val preview = _nytPreviewState.value ?: return
        if (preview.imported || preview.isImporting) return
        _nytPreviewState.update { it?.copy(isImporting = true, error = null) }
        viewModelScope.launch {
            val outcome = webRecipeImportService.importFromUrl(preview.url, sourcePublisherOverride = "NYT Cooking")
            when (outcome) {
                is WebImportOutcome.Success -> {
                    _nytPreviewState.update { it?.copy(isImporting = false, imported = true) }
                    _nytSearchState.update { it.copy(existingUrls = it.existingUrls + preview.url) }
                }
                is WebImportOutcome.NotFound -> _nytPreviewState.update {
                    it?.copy(isImporting = false, error = "Couldn't find a recipe there.")
                }
                is WebImportOutcome.NetworkError -> _nytPreviewState.update {
                    it?.copy(isImporting = false, error = "Couldn't reach that page: ${outcome.message}")
                }
                is WebImportOutcome.ParseError -> _nytPreviewState.update {
                    it?.copy(isImporting = false, error = outcome.message)
                }
            }
        }
    }

    // -------------------------------------------------------------------------------------

    fun dismissInfoMessage() {
        _uiState.update { it.copy(infoMessage = null) }
    }

    fun onPastedTextChanged(text: String) {
        _uiState.update { it.copy(pastedText = text) }
    }

    fun dismissPasteTextDialog() {
        _uiState.update { it.copy(pasteTextDialogOpen = false, pastedText = "") }
    }

    fun importPastedText() {
        val text = _uiState.value.pastedText
        if (text.isBlank()) return
        _uiState.update { it.copy(isLoading = true, pasteTextDialogOpen = false) }
        viewModelScope.launch {
            handleOutcome(webRecipeImportService.importPastedText(text))
        }
    }

    fun importSavedLink(link: SavedLinkUi) {
        _uiState.update { it.copy(url = link.url, isLoading = true, errorMessage = null, savedLinksExpanded = false) }
        viewModelScope.launch {
            handleOutcome(webRecipeImportService.importFromUrl(link.url))
        }
    }

    fun importRecipe() {
        val url = _uiState.value.url.trim()
        if (url.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Paste a recipe URL first") }
            return
        }
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            handleOutcome(webRecipeImportService.importFromUrl(url))
        }
    }

    /** Fetches + parses the URL but doesn't save -- populates the editable preview instead. */
    fun reviewBeforeImport() {
        val url = _uiState.value.url.trim()
        if (url.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Paste a recipe URL first") }
            return
        }
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = webRecipeImportService.fetchAndParse(url)) {
                is FetchAndParseOutcome.Success -> {
                    _uiState.update { it.copy(isLoading = false, url = "") }
                    _previewState.value = PreviewUiState(
                        url = result.url,
                        domain = result.domain,
                        publisher = result.publisher,
                        title = result.parsed.title,
                        ingredientsText = result.parsed.ingredients.joinToString("\n"),
                        instructionsText = result.parsed.instructions.joinToString("\n"),
                    )
                }
                is FetchAndParseOutcome.NotFound -> _uiState.update {
                    it.copy(isLoading = false, errorMessage = "Couldn't find a recipe there.")
                }
                is FetchAndParseOutcome.NetworkError -> _uiState.update {
                    it.copy(isLoading = false, errorMessage = "Couldn't reach that page: ${result.message}")
                }
                is FetchAndParseOutcome.ParseError -> _uiState.update {
                    it.copy(isLoading = false, errorMessage = result.message)
                }
            }
        }
    }

    fun onPreviewTitleChanged(title: String) {
        _previewState.update { it?.copy(title = title) }
    }

    fun onPreviewIngredientsChanged(text: String) {
        _previewState.update { it?.copy(ingredientsText = text) }
    }

    fun onPreviewInstructionsChanged(text: String) {
        _previewState.update { it?.copy(instructionsText = text) }
    }

    fun discardPreview() {
        _previewState.value = null
    }

    fun confirmPreviewImport() {
        val preview = _previewState.value ?: return
        _previewState.update { it?.copy(isSaving = true) }
        viewModelScope.launch {
            val parsed = ParsedRecipe(
                title = preview.title,
                ingredients = preview.ingredientsText.lines().map { it.trim() }.filter { it.isNotBlank() },
                instructions = preview.instructionsText.lines().map { it.trim() }.filter { it.isNotBlank() },
                imageUrl = null,
                recipeYield = null,
            )
            val outcome = webRecipeImportService.saveParsedRecipe(parsed, preview.url, preview.domain, preview.publisher)
            _previewState.value = null
            handleOutcome(outcome)
        }
    }

    private suspend fun handleOutcome(outcome: WebImportOutcome) {
        when (outcome) {
            is WebImportOutcome.Success -> {
                _uiState.update { ImportUiState() }
                _events.send(ImportEvent.Imported(outcome.recipeId))
            }
            is WebImportOutcome.NotFound -> _uiState.update {
                it.copy(isLoading = false, errorMessage = "Couldn't find a recipe there.")
            }
            is WebImportOutcome.NetworkError -> _uiState.update {
                it.copy(isLoading = false, errorMessage = "Couldn't reach that page: ${outcome.message}")
            }
            is WebImportOutcome.ParseError -> _uiState.update {
                it.copy(isLoading = false, errorMessage = outcome.message)
            }
        }
    }

    class Factory(
        private val webRecipeImportService: WebRecipeImportService,
        private val nytSearchService: NytSearchService,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(ImportViewModel::class.java))
            return ImportViewModel(webRecipeImportService, nytSearchService) as T
        }
    }
}
