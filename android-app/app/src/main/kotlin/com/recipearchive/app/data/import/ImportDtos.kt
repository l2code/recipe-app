package com.recipearchive.app.data.import

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Loosely-typed envelope: only the fields needed to validate the schema version
 * and enumerate recipes are strict. Each recipe element is decoded individually
 * so one malformed recipe cannot fail the whole bundle.
 */
@Serializable
data class ImportBundleEnvelopeDto(
    val schemaVersion: Int,
    val generatedAt: String? = null,
    val recipes: List<JsonElement> = emptyList(),
)

@Serializable
data class RecipeDto(
    val id: String,
    val title: String? = null,
    val rawText: String? = null,
    val wordCount: Int? = null,
    val ingredients: List<IngredientDto> = emptyList(),
    val instructions: List<InstructionDto> = emptyList(),
    val pageRefs: List<String> = emptyList(),
    val arrangementStatus: String? = null,
    val duplicateStatus: String? = null,
    val reviewFlags: List<String> = emptyList(),
    val handwritingPageRefs: List<String> = emptyList(),
    val source: SourceDto? = null,
    val handwrittenNotes: List<HandwrittenNoteDto> = emptyList(),
)

@Serializable
data class IngredientDto(
    val rawText: String? = null,
    val quantity: String? = null,
    val unit: String? = null,
    val item: String? = null,
    val parseStatus: String? = null,
)

@Serializable
data class InstructionDto(
    val order: Int? = null,
    val text: String? = null,
    val parseStatus: String? = null,
)

@Serializable
data class SourceDto(
    val publisher: String? = null,
    val domain: String? = null,
    val url: String? = null,
    val status: String? = null,
    val evidence: List<String> = emptyList(),
)

@Serializable
data class HandwrittenNoteDto(
    val pageId: String? = null,
    val scan: String? = null,
    val page: Int? = null,
    val imagePath: String? = null,
    val ocrDraft: String? = null,
    val transcription: String? = null,
    val status: String? = null,
    val reasons: List<String> = emptyList(),
)
