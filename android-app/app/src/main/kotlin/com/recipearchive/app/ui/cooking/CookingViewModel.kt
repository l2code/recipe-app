package com.recipearchive.app.ui.cooking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.recipearchive.app.data.companion.CookingCompanionRepository
import com.recipearchive.app.data.local.entity.CookingSessionEntity
import com.recipearchive.app.data.repository.RecipeDetailUi
import com.recipearchive.app.data.repository.RecipeRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CookingViewModel(
    recipeRepository: RecipeRepository,
    private val companionRepository: CookingCompanionRepository,
    recipeId: String,
    private val sessionId: String,
) : ViewModel() {
    val recipe: StateFlow<RecipeDetailUi?> = recipeRepository.observeRecipeDetail(recipeId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val session: StateFlow<CookingSessionEntity?> = companionRepository.observeSession(sessionId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun finish(notes: String, rating: Int?, onFinished: () -> Unit) {
        viewModelScope.launch {
            companionRepository.confirmSession(sessionId, notes, rating)
            onFinished()
        }
    }

    fun discard(onDiscarded: () -> Unit) {
        viewModelScope.launch {
            companionRepository.discardSession(sessionId)
            onDiscarded()
        }
    }

    class Factory(
        private val recipeRepository: RecipeRepository,
        private val companionRepository: CookingCompanionRepository,
        private val recipeId: String,
        private val sessionId: String,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(CookingViewModel::class.java))
            return CookingViewModel(recipeRepository, companionRepository, recipeId, sessionId) as T
        }
    }
}
