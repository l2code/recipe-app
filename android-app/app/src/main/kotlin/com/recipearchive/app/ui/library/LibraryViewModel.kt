package com.recipearchive.app.ui.library

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.recipearchive.app.data.import.ImportOutcome
import com.recipearchive.app.data.repository.RecipeRepository
import com.recipearchive.app.data.repository.RecipeSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private data class ImportPhase(
    val isImporting: Boolean = true,
    val hasCompletedOnce: Boolean = false,
    val error: String? = null,
)

private data class LibraryFilters(val category: String?, val collectionId: String?)

private data class LibraryData(
    val recipes: List<RecipeSummary>,
    val collections: List<com.recipearchive.app.data.local.entity.CollectionEntity>,
    val filters: LibraryFilters,
    val sort: LibrarySort,
)

internal fun sortLibraryRecipes(recipes: List<RecipeSummary>, sort: LibrarySort): List<RecipeSummary> =
    when (sort) {
        LibrarySort.ALPHABETICAL -> recipes.sortedBy { it.title.lowercase() }
        LibrarySort.RATING -> recipes.sortedWith(
            compareByDescending<RecipeSummary> { it.personalRating ?: -1 }
                .thenBy { it.title.lowercase() },
        )
    }

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class, kotlinx.coroutines.FlowPreview::class)
class LibraryViewModel(
    private val repository: RecipeRepository,
    private val applicationContext: Context,
    private val importAssetName: String = com.recipearchive.app.data.import.ImportService.DEFAULT_ASSET_NAME,
) : ViewModel() {

    private val queryState = MutableStateFlow("")
    private val importPhase = MutableStateFlow(ImportPhase())
    private val selectedCategory = MutableStateFlow<String?>(null)
    private val selectedCollectionId = MutableStateFlow<String?>(null)
    private val selectedSort = MutableStateFlow(LibrarySort.ALPHABETICAL)

    private val filters = combine(selectedCategory, selectedCollectionId, ::LibraryFilters)

    private val debouncedQuery = queryState
        .debounce(250)
        .distinctUntilChanged()

    private val unsortedRecipesFlow = combine(
        combine(debouncedQuery, filters) { query, filters -> query to filters },
        importPhase,
    ) { queryAndFilters, phase -> queryAndFilters to phase.isImporting }
        .distinctUntilChanged()
        .flatMapLatest { (queryAndFilters, isImporting) ->
            val (query, filters) = queryAndFilters
            if (isImporting) flowOf(emptyList())
            else repository.observeLibrary(query, filters.category, filters.collectionId)
        }

    private val recipesFlow = combine(unsortedRecipesFlow, selectedSort) { recipes, sort ->
        sortLibraryRecipes(recipes, sort)
    }

    private val collectionsFlow = importPhase.flatMapLatest { phase ->
        if (phase.isImporting) flowOf(emptyList()) else repository.observeCollections()
    }

    private val libraryData = combine(
        recipesFlow,
        collectionsFlow,
        filters,
        selectedSort,
    ) { recipes, collections, filters, sort -> LibraryData(recipes, collections, filters, sort) }

    val uiState: StateFlow<LibraryUiState> = combine(
        queryState,
        libraryData,
        importPhase,
    ) { query, data, phase ->
        LibraryUiState(
            query = query,
            recipes = data.recipes,
            selectedCategory = data.filters.category,
            collections = data.collections,
            selectedCollectionId = data.filters.collectionId,
            selectedSort = data.sort,
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

    fun selectCategory(category: String?) {
        selectedCategory.value = category
    }

    fun selectCollection(collectionId: String?) {
        selectedCollectionId.value = collectionId
    }

    fun selectSort(sort: LibrarySort) {
        selectedSort.value = sort
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
