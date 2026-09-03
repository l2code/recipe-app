package com.recipearchive.app.ui.webimport

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.recipearchive.app.data.webimport.WebImportOutcome
import com.recipearchive.app.data.webimport.WebRecipeImportService
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class QuickSource { NYT_COOKING, ANY_WEBSITE, PASTE_TEXT, SAVED_LINK }

data class ImportUiState(
    val url: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String? = null,
)

sealed class ImportEvent {
    data class Imported(val recipeId: String) : ImportEvent()
}

class ImportViewModel(private val webRecipeImportService: WebRecipeImportService) : ViewModel() {
    private val _uiState = MutableStateFlow(ImportUiState())
    val uiState: StateFlow<ImportUiState> = _uiState.asStateFlow()

    private val _events = Channel<ImportEvent>(Channel.BUFFERED)
    val events: Flow<ImportEvent> = _events.receiveAsFlow()

    fun onUrlChanged(url: String) {
        _uiState.update { it.copy(url = url, errorMessage = null) }
    }

    /** NYT Cooking / Any Website just focus the URL field; the other two are Stage 2 work. */
    fun onQuickSourceSelected(source: QuickSource) {
        when (source) {
            QuickSource.NYT_COOKING, QuickSource.ANY_WEBSITE ->
                _uiState.update { it.copy(infoMessage = null) }
            QuickSource.PASTE_TEXT, QuickSource.SAVED_LINK ->
                _uiState.update { it.copy(infoMessage = "Coming soon") }
        }
    }

    fun dismissInfoMessage() {
        _uiState.update { it.copy(infoMessage = null) }
    }

    fun importRecipe() {
        val url = _uiState.value.url.trim()
        if (url.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Paste a recipe URL first") }
            return
        }
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            when (val outcome = webRecipeImportService.importFromUrl(url)) {
                is WebImportOutcome.Success -> {
                    _uiState.update { ImportUiState() }
                    _events.send(ImportEvent.Imported(outcome.recipeId))
                }
                is WebImportOutcome.NotFound -> _uiState.update {
                    it.copy(isLoading = false, errorMessage = "Couldn't find a recipe on that page.")
                }
                is WebImportOutcome.NetworkError -> _uiState.update {
                    it.copy(isLoading = false, errorMessage = "Couldn't reach that page: ${outcome.message}")
                }
                is WebImportOutcome.ParseError -> _uiState.update {
                    it.copy(isLoading = false, errorMessage = outcome.message)
                }
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
