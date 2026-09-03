package com.recipearchive.app.ui.webimport

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
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

enum class QuickSource { NYT_COOKING, ANY_WEBSITE, PASTE_TEXT, SAVED_LINK }

data class ImportUiState(
    val url: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String? = null,
    val pasteTextDialogOpen: Boolean = false,
    val pastedText: String = "",
    val savedLinksExpanded: Boolean = false,
)

sealed class ImportEvent {
    data class Imported(val recipeId: String) : ImportEvent()
}

class ImportViewModel(private val webRecipeImportService: WebRecipeImportService) : ViewModel() {
    private val _uiState = MutableStateFlow(ImportUiState())
    val uiState: StateFlow<ImportUiState> = _uiState.asStateFlow()

    val savedLinks: StateFlow<List<SavedLinkUi>> = webRecipeImportService.observeSavedLinks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _events = Channel<ImportEvent>(Channel.BUFFERED)
    val events: Flow<ImportEvent> = _events.receiveAsFlow()

    fun onUrlChanged(url: String) {
        _uiState.update { it.copy(url = url, errorMessage = null) }
    }

    fun onQuickSourceSelected(source: QuickSource) {
        when (source) {
            QuickSource.NYT_COOKING -> _uiState.update { it.copy(infoMessage = null, savedLinksExpanded = false) }
            QuickSource.ANY_WEBSITE -> _uiState.update { it.copy(infoMessage = null, savedLinksExpanded = false) }
            QuickSource.PASTE_TEXT -> _uiState.update { it.copy(pasteTextDialogOpen = true, infoMessage = null) }
            QuickSource.SAVED_LINK -> _uiState.update {
                it.copy(savedLinksExpanded = !it.savedLinksExpanded, infoMessage = null)
            }
        }
    }

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

    class Factory(private val webRecipeImportService: WebRecipeImportService) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(ImportViewModel::class.java))
            return ImportViewModel(webRecipeImportService) as T
        }
    }
}
