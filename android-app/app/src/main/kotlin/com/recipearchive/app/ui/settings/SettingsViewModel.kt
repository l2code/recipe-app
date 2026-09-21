package com.recipearchive.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.recipearchive.app.data.settings.SettingsStore
import com.recipearchive.app.data.settings.ThemeMode
import com.recipearchive.app.data.sync.PullPendingOutcome
import com.recipearchive.app.data.sync.PushMirrorOutcome
import com.recipearchive.app.data.sync.ServerCredentialStore
import com.recipearchive.app.data.sync.ServerSyncService
import com.recipearchive.app.data.webimport.CredentialStore
import com.recipearchive.app.data.webimport.ParsedRecipe
import com.recipearchive.app.data.webimport.WebImportOutcome
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

data class ServerSyncCredentialsUiState(
    val serverUrl: String = "",
    val username: String = "",
    val password: String = "",
    val isSaved: Boolean = false,
    val statusMessage: String? = null,
)

data class ServerSyncUiState(
    val isSyncing: Boolean = false,
    val statusText: String? = null,
    val resultMessage: String? = null,
)

class SettingsViewModel(
    private val settingsStore: SettingsStore,
    private val credentialStore: CredentialStore,
    private val webRecipeImportService: WebRecipeImportService,
    private val serverCredentialStore: ServerCredentialStore,
    private val serverSyncService: ServerSyncService,
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

    private val _serverCredentialsState = MutableStateFlow(
        ServerSyncCredentialsUiState(
            serverUrl = serverCredentialStore.getServerUrl().orEmpty(),
            username = serverCredentialStore.getUsername().orEmpty(),
            password = serverCredentialStore.getPassword().orEmpty(),
            isSaved = serverCredentialStore.hasCredentials(),
        ),
    )
    val serverCredentialsState: StateFlow<ServerSyncCredentialsUiState> = _serverCredentialsState.asStateFlow()

    private val _serverSyncState = MutableStateFlow(ServerSyncUiState())
    val serverSyncState: StateFlow<ServerSyncUiState> = _serverSyncState.asStateFlow()

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

    fun onServerUrlChanged(url: String) {
        _serverCredentialsState.update { it.copy(serverUrl = url, statusMessage = null) }
    }

    fun onServerUsernameChanged(username: String) {
        _serverCredentialsState.update { it.copy(username = username, statusMessage = null) }
    }

    fun onServerPasswordChanged(password: String) {
        _serverCredentialsState.update { it.copy(password = password, statusMessage = null) }
    }

    fun saveServerCredentials() {
        val state = _serverCredentialsState.value
        if (state.serverUrl.isBlank() || state.username.isBlank() || state.password.isBlank()) {
            _serverCredentialsState.update {
                it.copy(statusMessage = "Enter a server URL, username, and password to save.")
            }
            return
        }
        val serverUrl = state.serverUrl.trim().trimEnd('/')
        serverCredentialStore.saveCredentials(serverUrl, state.username.trim(), state.password)
        _serverCredentialsState.update {
            it.copy(serverUrl = serverUrl, isSaved = true, statusMessage = "Server connection saved.")
        }
    }

    fun removeServerCredentials() {
        serverCredentialStore.clearCredentials()
        _serverCredentialsState.value = ServerSyncCredentialsUiState()
    }

    /**
     * Pushes the full local library to the home server's mirror, then pulls down any recipes
     * added from the web-add page and imports them via the same [WebRecipeImportService] path
     * URL imports already use, then acks the ones that imported cleanly. The server can never
     * reach the tablet on its own (not reliably on, no public address), so every step here is
     * initiated by the app.
     */
    fun syncWithServer() {
        if (_serverSyncState.value.isSyncing) return
        if (!serverCredentialStore.hasCredentials()) {
            _serverSyncState.update { it.copy(resultMessage = "Connect to a home server below first.") }
            return
        }
        viewModelScope.launch {
            _serverSyncState.update {
                it.copy(isSyncing = true, statusText = "Pushing your library to the server…", resultMessage = null)
            }

            val pushMessage = when (val outcome = serverSyncService.pushMirror()) {
                is PushMirrorOutcome.Success -> "Pushed ${outcome.count} recipes."
                is PushMirrorOutcome.NotConfigured -> {
                    finishServerSync(outcome.message)
                    return@launch
                }
                is PushMirrorOutcome.Failure -> {
                    finishServerSync("Sync failed: ${outcome.message}")
                    return@launch
                }
            }

            _serverSyncState.update { it.copy(statusText = "Checking for recipes added from the web…") }
            val pending = when (val outcome = serverSyncService.pullPending()) {
                is PullPendingOutcome.Success -> outcome.recipes
                is PullPendingOutcome.NotConfigured -> {
                    finishServerSync("$pushMessage ${outcome.message}")
                    return@launch
                }
                is PullPendingOutcome.Failure -> {
                    finishServerSync("$pushMessage Couldn't check for new recipes: ${outcome.message}")
                    return@launch
                }
            }

            if (pending.isEmpty()) {
                finishServerSync(pushMessage)
                return@launch
            }

            _serverSyncState.update { it.copy(statusText = "Importing ${pending.size} recipe(s) from the web…") }
            val importedIds = pending.filter { item ->
                val parsed = ParsedRecipe(
                    title = item.title,
                    ingredients = item.ingredients,
                    instructions = item.instructions,
                    imageUrl = item.imageUrl,
                    recipeYield = null,
                )
                val outcome = webRecipeImportService.saveParsedRecipe(parsed, item.sourceUrl, item.sourceDomain, item.sourcePublisher)
                outcome is WebImportOutcome.Success
            }.map { it.id }

            if (importedIds.isNotEmpty()) {
                serverSyncService.ackImported(importedIds)
            }

            val importMessage = if (importedIds.size == pending.size) {
                "Imported ${importedIds.size} new recipe${if (importedIds.size == 1) "" else "s"}."
            } else {
                "Imported ${importedIds.size} of ${pending.size} new recipes."
            }
            finishServerSync("$pushMessage $importMessage")
        }
    }

    private fun finishServerSync(resultMessage: String) {
        _serverSyncState.update { it.copy(isSyncing = false, statusText = null, resultMessage = resultMessage) }
    }

    fun dismissServerSyncMessage() {
        _serverSyncState.update { it.copy(resultMessage = null) }
    }

    class Factory(
        private val settingsStore: SettingsStore,
        private val credentialStore: CredentialStore,
        private val webRecipeImportService: WebRecipeImportService,
        private val serverCredentialStore: ServerCredentialStore,
        private val serverSyncService: ServerSyncService,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(SettingsViewModel::class.java))
            return SettingsViewModel(
                settingsStore,
                credentialStore,
                webRecipeImportService,
                serverCredentialStore,
                serverSyncService,
            ) as T
        }
    }
}
