package com.recipearchive.app.ui.library

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.recipearchive.app.data.import.ImportOutcome
import com.recipearchive.app.data.repository.RecipeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private data class ImportPhase(
    val isImporting: Boolean = true,
    val hasCompletedOnce: Boolean = false,
    val error: String? = null,
)

class LibraryViewModel(
    private val repository: RecipeRepository,
    private val applicationContext: Context,
    private val importAssetName: String = com.recipearchive.app.data.import.ImportService.DEFAULT_ASSET_NAME,
) : ViewModel() {

    private val queryState = MutableStateFlow("")
    private val importPhase = MutableStateFlow(ImportPhase())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class, kotlinx.coroutines.FlowPreview::class)
    private val recipesFlow = queryState
        .debounce(250)
        .distinctUntilChanged()
        .flatMapLatest { query -> repository.observeLibrary(query) }

    val uiState: StateFlow<LibraryUiState> = combine(
        queryState,
        recipesFlow,
        importPhase,
    ) { query, recipes, phase ->
        LibraryUiState(
            query = query,
            recipes = recipes,
            isImporting = phase.isImporting,
            hasImportedOnce = phase.hasCompletedOnce,
            importError = phase.error,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LibraryUiState(isImporting = true))

    init {
        runImport()
    }

    fun updateQuery(newQuery: String) {
        queryState.value = newQuery
    }

    fun retryImport() = runImport()

    fun toggleFavorite(recipeId: String, currentlyFavorite: Boolean) {
        viewModelScope.launch { repository.setFavorite(recipeId, !currentlyFavorite) }
    }

    private fun runImport() {
        viewModelScope.launch {
            importPhase.value = ImportPhase(isImporting = true, hasCompletedOnce = importPhase.value.hasCompletedOnce)
            importPhase.value = when (val outcome = repository.importBundle(applicationContext, importAssetName)) {
                is ImportOutcome.Completed -> ImportPhase(isImporting = false, hasCompletedOnce = true, error = null)
                is ImportOutcome.Failed -> ImportPhase(isImporting = false, hasCompletedOnce = true, error = outcome.reason)
            }
        }
    }

    class Factory(
        private val repository: RecipeRepository,
        private val applicationContext: Context,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(LibraryViewModel::class.java))
            return LibraryViewModel(repository, applicationContext) as T
        }
    }
}
