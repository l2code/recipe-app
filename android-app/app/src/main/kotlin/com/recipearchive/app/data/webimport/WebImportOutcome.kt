package com.recipearchive.app.data.webimport

sealed class WebImportOutcome {
    data class Success(val recipeId: String, val title: String, val wasNew: Boolean) : WebImportOutcome()

    /** The page loaded but no `schema.org/Recipe` structured data could be found on it. */
    object NotFound : WebImportOutcome()

    data class NetworkError(val message: String) : WebImportOutcome()

    data class ParseError(val message: String) : WebImportOutcome()
}
