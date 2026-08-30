package com.recipearchive.app.ui.library

import com.recipearchive.app.data.repository.RecipeSummary

data class LibraryUiState(
    val query: String = "",
    val recipes: List<RecipeSummary> = emptyList(),
    val isImporting: Boolean = false,
    val hasImportedOnce: Boolean = false,
    val importError: String? = null,
)
