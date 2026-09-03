package com.recipearchive.app.data.webimport

/**
 * Lightweight heuristic splitter for recipe text pasted directly into the app
 * (no HTML, so [RecipeJsonLdParser] doesn't apply). Looks for "Ingredients" /
 * "Instructions"-style section headers first; if none are present, falls back
 * to treating lines that start with a quantity as ingredients and everything
 * else as instructions.
 */
object PastedTextParser {
    private val ingredientHeaderRegex = Regex("^ingredients?\\s*:?\\s*$", RegexOption.IGNORE_CASE)
    private val instructionHeaderRegex =
        Regex("^(instructions?|directions?|method|steps?|preparation)\\s*:?\\s*$", RegexOption.IGNORE_CASE)
    private val leadingQuantityRegex = Regex("^[\\s\\-*•]*[\\d¼½¾⅓⅔⅛⅜⅝⅞]")

    fun parse(text: String): ParsedRecipe {
        val lines = text.lines().map { it.trim() }
        if (lines.all { it.isBlank() }) return ParsedRecipe("", emptyList(), emptyList(), null, null)

        val ingredientsHeaderIdx = lines.indexOfFirst { ingredientHeaderRegex.matches(it) }
        val instructionsHeaderIdx = lines.indexOfFirst { instructionHeaderRegex.matches(it) }

        return if (ingredientsHeaderIdx >= 0 || instructionsHeaderIdx >= 0) {
            parseWithHeaders(lines, ingredientsHeaderIdx, instructionsHeaderIdx)
        } else {
            parseWithoutHeaders(lines)
        }
    }

    private fun parseWithHeaders(lines: List<String>, ingredientsHeaderIdx: Int, instructionsHeaderIdx: Int): ParsedRecipe {
        val firstHeaderIdx = listOf(ingredientsHeaderIdx, instructionsHeaderIdx).filter { it >= 0 }.min()
        val title = lines.take(firstHeaderIdx).firstOrNull { it.isNotBlank() }.orEmpty()

        val ingredients = if (ingredientsHeaderIdx >= 0) {
            val end = if (instructionsHeaderIdx > ingredientsHeaderIdx) instructionsHeaderIdx else lines.size
            lines.subList(ingredientsHeaderIdx + 1, end).filter { it.isNotBlank() }
        } else {
            emptyList()
        }

        val instructions = if (instructionsHeaderIdx >= 0) {
            val end = if (ingredientsHeaderIdx > instructionsHeaderIdx) ingredientsHeaderIdx else lines.size
            lines.subList(instructionsHeaderIdx + 1, end).filter { it.isNotBlank() }
        } else {
            emptyList()
        }

        return ParsedRecipe(title, ingredients, instructions, null, null)
    }

    private fun parseWithoutHeaders(lines: List<String>): ParsedRecipe {
        val titleIdx = lines.indexOfFirst { it.isNotBlank() }
        val title = lines[titleIdx]
        val rest = lines.drop(titleIdx + 1).filter { it.isNotBlank() }
        val ingredients = rest.filter { leadingQuantityRegex.containsMatchIn(it) }
        val instructions = rest.filterNot { leadingQuantityRegex.containsMatchIn(it) }
        return ParsedRecipe(title, ingredients, instructions, null, null)
    }
}
