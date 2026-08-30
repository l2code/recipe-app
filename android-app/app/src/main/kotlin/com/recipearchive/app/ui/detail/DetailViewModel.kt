package com.recipearchive.app.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.recipearchive.app.data.repository.RecipeDetailUi
import com.recipearchive.app.data.repository.RecipeRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DetailViewModel(
    private val repository: RecipeRepository,
    private val recipeId: String,
) : ViewModel() {

    val uiState: StateFlow<RecipeDetailUi?> = repository.observeRecipeDetail(recipeId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun toggleFavorite(currentlyFavorite: Boolean) {
        viewModelScope.launch { repository.setFavorite(recipeId, !currentlyFavorite) }
    }

    fun updateNotes(notes: String) {
        viewModelScope.launch { repository.setPersonalNotes(recipeId, notes) }
    }

    class Factory(
        private val repository: RecipeRepository,
        private val recipeId: String,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(DetailViewModel::class.java))
            return DetailViewModel(repository, recipeId) as T
        }
    }
}
