package com.recipearchive.app.ui.companion

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.recipearchive.app.data.companion.CookingCompanionRepository
import com.recipearchive.app.data.companion.CookingHistoryItemUi
import com.recipearchive.app.data.companion.MealPlanItemUi
import com.recipearchive.app.data.companion.PantryCatalogItemUi
import com.recipearchive.app.data.companion.PantryStatus
import com.recipearchive.app.data.companion.ShoppingListItemUi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CompanionViewModel(private val repository: CookingCompanionRepository) : ViewModel() {
    val shopping: StateFlow<List<ShoppingListItemUi>> = repository.observeShoppingList()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val pantry: StateFlow<List<PantryCatalogItemUi>> = repository.observePantry()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val mealPlan: StateFlow<List<MealPlanItemUi>> = repository.observeMealPlan()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val cookingHistory: StateFlow<List<CookingHistoryItemUi>> = repository.observeCookingHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addPantryItem(name: String) = viewModelScope.launch { repository.addPantryItem(name) }

    fun setPantryStatus(item: PantryCatalogItemUi, status: PantryStatus) = viewModelScope.launch {
        repository.setPantryStatus(item.item.ingredientKey, item.item.displayName, status, item.item.isStaple)
    }

    fun toggleStaple(item: PantryCatalogItemUi) = viewModelScope.launch {
        repository.setPantryStatus(
            item.item.ingredientKey,
            item.item.displayName,
            PantryStatus.from(item.item.status),
            !item.item.isStaple,
        )
    }

    fun setShoppingChecked(item: ShoppingListItemUi, checked: Boolean) = viewModelScope.launch {
        repository.setShoppingChecked(item.item.ingredientKey, item.item.displayName, checked)
    }

    fun deleteShoppingItem(key: String) = viewModelScope.launch { repository.deleteShoppingItem(key) }
    fun clearChecked() = viewModelScope.launch { repository.clearCheckedShoppingItems() }
    fun deleteMealPlanEntry(id: String) = viewModelScope.launch { repository.deleteMealPlanEntry(id) }
    fun addMissingForPlan(item: MealPlanItemUi) = viewModelScope.launch {
        repository.addUnavailableToShopping(item.entry.recipeId)
    }

    class Factory(private val repository: CookingCompanionRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(CompanionViewModel::class.java))
            return CompanionViewModel(repository) as T
        }
    }
}
