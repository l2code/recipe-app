package com.recipearchive.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.recipearchive.app.data.settings.SettingsStore
import com.recipearchive.app.data.settings.ThemeMode
import com.recipearchive.app.data.webimport.CredentialStore
import com.recipearchive.app.data.webimport.WebRecipeImportService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NytAccountUiState(
    val email: String = "",
    val password: String = "",
    val isSaved: Boolean = false,
    val statusMessage: String? = null,
)

data class NytRatingSyncUiState(
    val isSyncing: Boolean = false,
    val completed: Int = 0,
    val total: Int = 0,
    val resultMessage: String? = null,
)

class SettingsViewModel(
    private val settingsStore: SettingsStore,
    private val credentialStore: CredentialStore,
    private val webRecipeImportService: WebRecipeImportService,
) : ViewModel() {
    val themeMode: StateFlow<ThemeMode> = settingsStore.themeMode
    val showNavLabels: StateFlow<Boolean> = settingsStore.showNavLabels

    private val _nytAccountState = MutableStateFlow(
        NytAccountUiState(
            email = credentialStore.getEmail().orEmpty(),
            password = credentialStore.getPassword().orEmpty(),
            isSaved = credentialStore.hasCredentials(),
        ),
    )
    val nytAccountState: StateFlow<NytAccountUiState> = _nytAccountState.asStateFlow()

    private val _nytRatingSyncState = MutableStateFlow(NytRatingSyncUiState())
    val nytRatingSyncState: StateFlow<NytRatingSyncUiState> = _nytRatingSyncState.asStateFlow()

    fun setThemeMode(mode: ThemeMode) = settingsStore.setThemeMode(mode)
    fun setShowNavLabels(show: Boolean) = settingsStore.setShowNavLabels(show)

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

    fun removeNytCredentials() {
        credentialStore.clearCredentials()
        _nytAccountState.value = NytAccountUiState()
    }

    /**
     * Refreshes the public star rating/review count for every saved NYT Cooking recipe, one at
     * a time. Deliberately separate from personalRating (the user's own rating) -- this only
     * ever writes nytRating/nytReviewCount, never touches personalRating, recipe content, or
     * library sort order (which stays keyed on personalRating; see LibraryViewModel).
     */
    fun syncNytRatings() {
        if (_nytRatingSyncState.value.isSyncing) return
        viewModelScope.launch {
            val ids = webRecipeImportService.getNytCookingRecipeIds()
            if (ids.isEmpty()) {
                _nytRatingSyncState.update {
                    it.copy(resultMessage = "No NYT Cooking recipes in your library yet.")
                }
                return@launch
            }
            _nytRatingSyncState.update { it.copy(isSyncing = true, completed = 0, total = ids.size, resultMessage = null) }
            var updated = 0
            ids.forEachIndexed { index, id ->
                if (webRecipeImportService.syncNytRating(id)) updated++
                _nytRatingSyncState.update { it.copy(completed = index + 1) }
            }
            val failed = ids.size - updated
            _nytRatingSyncState.update {
                it.copy(
                    isSyncing = false,
                    resultMessage = "Updated $updated of ${ids.size} recipes" +
                        if (failed > 0) " ($failed couldn't be reached or had no rating)." else ".",
                )
            }
        }
    }

    fun dismissNytRatingSyncMessage() {
        _nytRatingSyncState.update { it.copy(resultMessage = null) }
    }

    class Factory(
        private val settingsStore: SettingsStore,
        private val credentialStore: CredentialStore,
        private val webRecipeImportService: WebRecipeImportService,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(SettingsViewModel::class.java))
            return SettingsViewModel(settingsStore, credentialStore, webRecipeImportService) as T
        }
    }
}
