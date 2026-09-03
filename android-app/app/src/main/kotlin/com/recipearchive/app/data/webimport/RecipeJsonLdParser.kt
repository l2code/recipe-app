package com.recipearchive.app.data.webimport

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import org.jsoup.Jsoup

/**
 * Extracts recipe fields from a page's `schema.org/Recipe` JSON-LD blocks.
 *
 * Almost every modern recipe site (NYT Cooking included) embeds this structured
 * data server-side for Google's recipe rich results, regardless of any
 * subscription/paywall gate on the rendered article -- so this is the one
 * approach that works generically across arbitrary sites without a
 * site-specific scraper per domain.
 */
object RecipeJsonLdParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(html: String): ParsedRecipe? {
        val document = Jsoup.parse(html)
        val scripts = document.select("script[type=application/ld+json]")
        for (script in scripts) {
            val raw = script.data().ifBlank { script.html() }
            if (raw.isBlank()) continue
            val element = try {
                json.parseToJsonElement(raw)
            } catch (e: Exception) {
                continue
            }
            val recipeObject = findRecipeObject(element) ?: continue
            val parsed = extractRecipe(recipeObject)
            if (!parsed.isEmpty) return parsed
        }
        return null
    }

    private fun findRecipeObject(element: JsonElement): JsonObject? = when (element) {
        is JsonObject -> {
            if (isRecipeType(element["@type"])) {
                element
            } else {
                val graph = element["@graph"]
                if (graph is JsonArray) graph.firstNotNullOfOrNull { findRecipeObject(it) } else null
            }
        }
        is JsonArray -> element.firstNotNullOfOrNull { findRecipeObject(it) }
        else -> null
    }

    private fun isRecipeType(typeElement: JsonElement?): Boolean = when (typeElement) {
        is JsonPrimitive -> typeElement.contentOrNull == "Recipe"
        is JsonArray -> typeElement.any { (it as? JsonPrimitive)?.contentOrNull == "Recipe" }
        else -> false
    }

    private fun extractRecipe(obj: JsonObject): ParsedRecipe {
        val title = (obj["name"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
        val ingredients = extractStringList(obj["recipeIngredient"] ?: obj["ingredients"])
        val instructions = extractInstructions(obj["recipeInstructions"])
        val image = extractImage(obj["image"])
        val yieldText = extractYield(obj["recipeYield"])
        return ParsedRecipe(title, ingredients, instructions, image, yieldText)
    }

    private fun extractStringList(element: JsonElement?): List<String> = when (element) {
        null -> emptyList()
        is JsonArray -> element.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { s -> s.isNotBlank() } }
        is JsonPrimitive -> element.contentOrNull
            ?.split(Regex("\\r?\\n+"))
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?: emptyList()
        else -> emptyList()
    }

    private fun extractInstructions(element: JsonElement?): List<String> = when (element) {
        null -> emptyList()
        is JsonArray -> element.flatMap { extractInstructionItem(it) }
        is JsonPrimitive -> element.contentOrNull
            ?.split(Regex("\\r?\\n+"))
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?: emptyList()
        else -> emptyList()
    }

    // Handles HowToStep (has "text"/"name"), HowToSection (nests steps under
    // "itemListElement"), and plain strings.
    private fun extractInstructionItem(item: JsonElement): List<String> = when (item) {
        is JsonPrimitive -> listOfNotNull(item.contentOrNull?.trim()?.takeIf { it.isNotBlank() })
        is JsonObject -> {
            val nested = item["itemListElement"]
            if (nested != null) {
                extractInstructions(nested)
            } else {
                val text = (item["text"] as? JsonPrimitive)?.contentOrNull
                    ?: (item["name"] as? JsonPrimitive)?.contentOrNull
                listOfNotNull(text?.trim()?.takeIf { it.isNotBlank() })
            }
        }
        else -> emptyList()
    }

    private fun extractImage(element: JsonElement?): String? = when (element) {
        null -> null
        is JsonPrimitive -> element.contentOrNull
        is JsonArray -> element.firstOrNull()?.let { extractImage(it) }
        is JsonObject -> (element["url"] as? JsonPrimitive)?.contentOrNull
        else -> null
    }

    private fun extractYield(element: JsonElement?): String? = when (element) {
        null -> null
        is JsonPrimitive -> element.contentOrNull
        is JsonArray -> element.firstOrNull()?.let { (it as? JsonPrimitive)?.contentOrNull }
        else -> null
    }
}
