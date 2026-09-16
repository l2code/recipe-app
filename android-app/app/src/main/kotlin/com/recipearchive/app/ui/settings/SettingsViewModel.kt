package com.recipearchive.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.recipearchive.app.data.settings.SettingsStore
import com.recipearchive.app.data.settings.ThemeMode
import com.recipearchive.app.data.webimport.CredentialStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class NytAccountUiState(
    val email: String = "",
    val password: String = "",
    val isSaved: Boolean = false,
    val statusMessage: String? = null,
)

class SettingsViewModel(
    private val settingsStore: SettingsStore,
    private val credentialStore: CredentialStore,
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

    class Factory(
        private val settingsStore: SettingsStore,
        private val credentialStore: CredentialStore,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(SettingsViewModel::class.java))
            return SettingsViewModel(settingsStore, credentialStore) as T
        }
    }
}
