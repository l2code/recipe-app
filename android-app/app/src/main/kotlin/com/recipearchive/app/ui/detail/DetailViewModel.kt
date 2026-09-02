package com.recipearchive.app.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.recipearchive.app.data.repository.RecipeDetailUi
import com.recipearchive.app.data.repository.RecipeRepository
import com.recipearchive.app.data.companion.CookingCompanionRepository
import com.recipearchive.app.data.companion.PantryStatus
import com.recipearchive.app.data.companion.RecipeCompanionUi
import java.time.LocalDate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DetailViewModel(
    private val repository: RecipeRepository,
    private val companionRepository: CookingCompanionRepository,
    private val recipeId: String,
) : ViewModel() {

    val uiState: StateFlow<RecipeDetailUi?> = repository.observeRecipeDetail(recipeId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val companionState: StateFlow<RecipeCompanionUi> = companionRepository.observeRecipe(recipeId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RecipeCompanionUi())

    fun toggleFavorite(currentlyFavorite: Boolean) {
        viewModelScope.launch { repository.setFavorite(recipeId, !currentlyFavorite) }
    }

    fun updateNotes(notes: String) {
        viewModelScope.launch { repository.setPersonalNotes(recipeId, notes) }
    }

    fun updateRating(rating: Int?) {
        viewModelScope.launch { repository.setPersonalRating(recipeId, rating) }
    }

    fun reviewImportedNotes(status: String) {
        viewModelScope.launch { repository.setImportedNotesReviewStatus(recipeId, status) }
    }

    fun updateCategory(category: String?) {
        viewModelScope.launch { repository.setCategory(recipeId, category) }
    }

    fun updateCollection(collectionId: String, selected: Boolean) {
        viewModelScope.launch { repository.setRecipeCollection(recipeId, collectionId, selected) }
    }

    fun createCollection(name: String) {
        viewModelScope.launch {
            repository.createCollection(name)?.let { collectionId ->
                repository.setRecipeCollection(recipeId, collectionId, true)
            }
        }
    }

    fun startCooking(onStarted: (String) -> Unit) {
        viewModelScope.launch { onStarted(companionRepository.startCooking(recipeId)) }
    }

    fun recordPossibleSession(startedAt: Long, finishedAt: Long) {
        viewModelScope.launch { companionRepository.recordPossibleSession(recipeId, startedAt, finishedAt) }
    }

    fun confirmPossibleSession(sessionId: String, notes: String, rating: Int?) {
        viewModelScope.launch { companionRepository.confirmPossibleSession(sessionId, notes, rating) }
    }

    fun dismissPossibleSession(sessionId: String) {
        viewModelScope.launch { companionRepository.discardSession(sessionId) }
    }

    fun setPantryStatus(key: String, displayName: String, status: PantryStatus, isStaple: Boolean = false) {
        viewModelScope.launch { companionRepository.setPantryStatus(key, displayName, status, isStaple) }
    }

    fun addUnavailableToShopping() {
        viewModelScope.launch { companionRepository.addUnavailableToShopping(recipeId) }
    }

    fun addToMealPlan(date: LocalDate) {
        viewModelScope.launch { companionRepository.addMealPlan(recipeId, date) }
    }

    class Factory(
        private val repository: RecipeRepository,
        private val companionRepository: CookingCompanionRepository,
        private val recipeId: String,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(DetailViewModel::class.java))
            return DetailViewModel(repository, companionRepository, recipeId) as T
        }
    }
}
