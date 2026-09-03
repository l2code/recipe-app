package com.recipearchive.app.data.webimport

/** Recipe fields extracted from a page's schema.org/Recipe JSON-LD block. */
data class ParsedRecipe(
    val title: String,
    val ingredients: List<String>,
    val instructions: List<String>,
    val imageUrl: String?,
    val recipeYield: String?,
) {
    val isEmpty: Boolean
        get() = title.isBlank() && ingredients.isEmpty() && instructions.isEmpty()
}
