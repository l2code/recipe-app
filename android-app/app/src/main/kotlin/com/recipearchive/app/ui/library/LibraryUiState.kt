package com.recipearchive.app.ui.library

import com.recipearchive.app.data.repository.RecipeSummary
import com.recipearchive.app.data.local.entity.CollectionEntity
import com.recipearchive.app.data.organization.RecipeCategories

data class LibraryUiState(
    val query: String = "",
    val recipes: List<RecipeSummary> = emptyList(),
    val categories: List<String> = RecipeCategories.all,
    val selectedCategory: String? = null,
    val collections: List<CollectionEntity> = emptyList(),
    val selectedCollectionId: String? = null,
    val isImporting: Boolean = false,
    val hasImportedOnce: Boolean = false,
    val importError: String? = null,
)
