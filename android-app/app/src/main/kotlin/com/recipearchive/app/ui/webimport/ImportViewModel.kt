package com.recipearchive.app.ui.webimport

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.recipearchive.app.data.webimport.CredentialStore
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
    val manageAccountsDialogOpen: Boolean = false,
)

data class NytAccountUiState(
    val email: String = "",
    val password: String = "",
    val isSaved: Boolean = false,
    val statusMessage: String? = null,
)

sealed class ImportEvent {
    data class Imported(val recipeId: String) : ImportEvent()
}

class ImportViewModel(
    private val webRecipeImportService: WebRecipeImportService,
    private val credentialStore: CredentialStore,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ImportUiState())
    val uiState: StateFlow<ImportUiState> = _uiState.asStateFlow()

    private val _nytAccountState = MutableStateFlow(
        NytAccountUiState(
            email = credentialStore.getEmail().orEmpty(),
            password = credentialStore.getPassword().orEmpty(),
            isSaved = credentialStore.hasCredentials(),
        ),
    )
    val nytAccountState: StateFlow<NytAccountUiState> = _nytAccountState.asStateFlow()

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

    fun onNytEmailChanged(email: String) {
        _nytAccountState.update { it.copy(email = email, statusMessage = null) }
    }

    fun onNytPasswordChanged(password: String) {
        _nytAccountState.update { it.copy(password = password, statusMessage = null) }
    }

    fun saveNytCredentials() {
        val state = _nytAccountState.value
        if (state.email.isBlank() || state.password.isBlank()) {
            _nytAccountState.update { it.copy(statusMessage = "Enter both an email and a password to save.") }
            return
        }
        credentialStore.saveCredentials(state.email.trim(), state.password)
        _nytAccountState.update { it.copy(isSaved = true, statusMessage = "Credentials saved to this device.") }
    }

    /**
     * Only checks that the fields look like usable credentials. This never signs in to NYT --
     * recipe pages are always fetched from the public page, never behind a login (see plan).
     */
    fun testNytLogin() {
        val state = _nytAccountState.value
        val message = if (state.email.contains("@") && state.password.isNotBlank()) {
            "Looks good. We don't sign in to NYT Cooking -- recipes are fetched from the public page."
        } else {
            "Enter a valid email and password first."
        }
        _nytAccountState.update { it.copy(statusMessage = message) }
    }

    fun openManageAccounts() {
        _uiState.update { it.copy(manageAccountsDialogOpen = true) }
    }

    fun dismissManageAccounts() {
        _uiState.update { it.copy(manageAccountsDialogOpen = false) }
    }

    fun removeNytCredentials() {
        credentialStore.clearCredentials()
        _nytAccountState.update { NytAccountUiState() }
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
        private val credentialStore: CredentialStore,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(ImportViewModel::class.java))
            return ImportViewModel(webRecipeImportService, credentialStore) as T
        }
    }
}
