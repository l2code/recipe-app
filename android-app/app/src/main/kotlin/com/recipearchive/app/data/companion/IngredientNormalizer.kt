package com.recipearchive.app.data.companion

object IngredientNormalizer {
    private val descriptors = setOf(
        "fresh", "large", "medium", "small", "diced", "chopped", "sliced", "minced",
        "crushed", "ground", "optional", "divided", "packed", "finely", "thinly",
    )

    fun key(value: String): String {
        val cleaned = value.lowercase()
            .replace(Regex("\\([^)]*\\)"), " ")
            .replace(Regex("[^a-z0-9 ]"), " ")
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() && it !in descriptors }
            .joinToString(" ")
            .trim()
        return singularize(cleaned).ifBlank { "unknown ingredient" }
    }

    fun displayName(value: String): String = value.trim()
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        .ifBlank { "Unknown ingredient" }

    private fun singularize(value: String): String = when {
        value.endsWith("ies") && value.length > 3 -> value.dropLast(3) + "y"
        value.endsWith("oes") && value.length > 3 -> value.dropLast(2)
        value.endsWith("ses") || value.endsWith("ss") -> value
        value.endsWith("s") && value.length > 3 -> value.dropLast(1)
        else -> value
    }
}
