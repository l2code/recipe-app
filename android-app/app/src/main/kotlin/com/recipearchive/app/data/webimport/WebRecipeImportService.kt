package com.recipearchive.app.data.webimport

import androidx.room.withTransaction
import com.recipearchive.app.data.local.RecipeDatabase
import com.recipearchive.app.data.local.entity.IngredientEntity
import com.recipearchive.app.data.local.entity.InstructionEntity
import com.recipearchive.app.data.local.entity.RecipeAppStateEntity
import com.recipearchive.app.data.local.entity.RecipeEntity
import com.recipearchive.app.data.local.entity.RecipeSearchDocumentEntity
import com.recipearchive.app.data.local.entity.WebImportHistoryEntity
import com.recipearchive.app.data.local.entity.WebImportOutcomeStatus
import com.recipearchive.app.data.organization.RecipeCategories
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Fetches an arbitrary recipe URL, parses its `schema.org/Recipe` JSON-LD via
 * [RecipeJsonLdParser] (or splits pasted text heuristically via
 * [PastedTextParser]), and upserts the result into the same Room tables the
 * offline archive [com.recipearchive.app.data.import.ImportService] writes to.
 *
 * Recipes saved this way get a random UUID id (never an `R####` archive id).
 * URL imports are deduplicated by [RecipeEntity.sourceUrl]: reimporting the
 * same URL updates the existing row in place instead of creating a second
 * copy. Pasted-text imports have no URL to dedupe on, so each save creates a
 * new recipe. Every attempt -- successful or not -- is recorded to
 * [WebImportHistoryEntity], which backs both the "Saved Link" quick-reimport
 * shortcut and the full Import History screen.
 */
class WebRecipeImportService(
    private val database: RecipeDatabase,
    private val httpClient: OkHttpClient = OkHttpClient(),
    private val parser: RecipeJsonLdParser = RecipeJsonLdParser,
    private val clock: () -> Long = System::currentTimeMillis,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun importFromUrl(url: String, sourcePublisherOverride: String? = null): WebImportOutcome =
        withContext(ioDispatcher) {
            when (val result = fetchAndParse(url, sourcePublisherOverride)) {
                is FetchAndParseOutcome.Success ->
                    saveParsedRecipe(result.parsed, result.url, result.domain, result.publisher)
                is FetchAndParseOutcome.NotFound ->
                    recordHistory(WebImportOutcome.NotFound, normalizeUrl(url) ?: url.trim(), "", "")
                is FetchAndParseOutcome.NetworkError ->
                    recordHistory(WebImportOutcome.NetworkError(result.message), normalizeUrl(url) ?: url.trim(), "", "")
                is FetchAndParseOutcome.ParseError ->
                    recordHistory(WebImportOutcome.ParseError(result.message), normalizeUrl(url) ?: url.trim(), "", "")
            }
        }

    /** Fetches and parses a URL without saving -- backs the "Review Before Import" preview. */
    suspend fun fetchAndParse(url: String, sourcePublisherOverride: String? = null): FetchAndParseOutcome =
        withContext(ioDispatcher) {
            val normalizedUrl = normalizeUrl(url)
                ?: return@withContext FetchAndParseOutcome.ParseError("Not a valid URL")
            val domain = normalizedUrl.toHttpUrlOrNull()?.host?.removePrefix("www.").orEmpty()
            val publisher = sourcePublisherOverride?.takeIf { it.isNotBlank() } ?: domain

            val html = try {
                fetch(normalizedUrl)
            } catch (e: IOException) {
                return@withContext FetchAndParseOutcome.NetworkError(e.message ?: "Network error")
            }

            val parsed = try {
                parser.parse(html)
            } catch (e: Exception) {
                return@withContext FetchAndParseOutcome.ParseError(e.message ?: "Failed to parse recipe")
            }

            if (parsed == null || parsed.isEmpty) return@withContext FetchAndParseOutcome.NotFound

            FetchAndParseOutcome.Success(parsed, normalizedUrl, domain, publisher)
        }

    suspend fun importPastedText(text: String): WebImportOutcome = withContext(ioDispatcher) {
        val parsed = PastedTextParser.parse(text)
        if (parsed.isEmpty) {
            return@withContext recordHistory(WebImportOutcome.NotFound, "", PASTED_TEXT_LABEL, "")
        }
        saveParsedRecipe(parsed, url = "", domain = PASTED_TEXT_LABEL, publisher = PASTED_TEXT_LABEL)
    }

    /**
     * Persists an already-parsed recipe and records the save to history. Shared by both import
     * paths and by the review-before-import flow's final "Import This Recipe" commit.
     */
    suspend fun saveParsedRecipe(parsed: ParsedRecipe, url: String, domain: String, publisher: String): WebImportOutcome {
        val now = clock()
        val (recipeId, wasNew) = database.withTransaction {
            persistRecipe(parsed, url, domain, publisher, now)
        }
        val outcome = WebImportOutcome.Success(recipeId, parsed.title.ifBlank { "Untitled recipe" }, wasNew)
        return recordHistory(outcome, url, domain, outcome.title)
    }

    fun observeSavedLinks(limit: Int = 10): Flow<List<SavedLinkUi>> =
        database.webImportHistoryDao().observeRecentSuccessful(limit).map { entries ->
            entries.distinctBy { it.url }.map { SavedLinkUi(it.url, it.title, it.domain, it.importedAt) }
        }

    fun observeHistory(limit: Int = 50): Flow<List<ImportHistoryEntryUi>> =
        database.webImportHistoryDao().observeRecent(limit).map { entries ->
            entries.map {
                ImportHistoryEntryUi(it.id, it.url, it.title, it.domain, it.status, it.errorMessage, it.recipeId, it.importedAt)
            }
        }

    private fun fetch(url: String): String {
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            return response.body?.string() ?: throw IOException("Empty response body")
        }
    }

    private suspend fun persistRecipe(
        parsed: ParsedRecipe,
        url: String,
        domain: String,
        publisher: String,
        now: Long,
    ): Pair<String, Boolean> {
        val recipeDao = database.recipeDao()
        val existing = if (url.isNotBlank()) recipeDao.getBySourceUrl(url) else null
        val id = existing?.id ?: UUID.randomUUID().toString()
        val createdAt = existing?.createdAt ?: now
        val title = parsed.title.takeIf { it.isNotBlank() } ?: "Untitled recipe"
        val rawText = buildRawText(title, parsed)

        val entity = RecipeEntity(
            id = id,
            title = title,
            rawText = rawText,
            wordCount = rawText.split(Regex("\\s+")).count { it.isNotBlank() },
            arrangementStatus = "web_import",
            duplicateStatus = "",
            sourcePublisher = publisher,
            sourceDomain = domain,
            sourceUrl = url,
            sourceStatus = "confirmed",
            importSchemaVersion = 0,
            importGeneratedAt = "",
            createdAt = createdAt,
            lastImportedAt = now,
        )
        if (existing == null) recipeDao.insert(entity) else recipeDao.update(entity)

        val ingredientDao = database.ingredientDao()
        ingredientDao.deleteForRecipe(id)
        if (parsed.ingredients.isNotEmpty()) {
            ingredientDao.insertAll(
                parsed.ingredients.mapIndexed { index, text ->
                    IngredientEntity(
                        recipeId = id,
                        displayOrder = index,
                        rawText = text,
                        quantity = "",
                        unit = "",
                        item = text,
                        parseStatus = "needs_review",
                    )
                },
            )
        }

        val instructionDao = database.instructionDao()
        instructionDao.deleteForRecipe(id)
        if (parsed.instructions.isNotEmpty()) {
            instructionDao.insertAll(
                parsed.instructions.mapIndexed { index, text ->
                    InstructionEntity(
                        recipeId = id,
                        displayOrder = index + 1,
                        text = text,
                        parseStatus = "needs_review",
                    )
                },
            )
        }

        val searchDao = database.recipeSearchDao()
        searchDao.deleteDocument(id)
        searchDao.insertDocument(
            RecipeSearchDocumentEntity(
                recipeId = id,
                title = title,
                rawText = rawText,
                ingredientsText = parsed.ingredients.joinToString(" "),
                instructionsText = parsed.instructions.joinToString(" "),
                handwritingText = "",
                sourceText = listOf(publisher, domain, url).joinToString(" "),
            ),
        )

        val inferredCategory = RecipeCategories.infer(
            title = title,
            ingredientText = parsed.ingredients.joinToString(" "),
        )
        database.recipeAppStateDao().insertDefaultIfMissing(
            RecipeAppStateEntity(recipeId = id, category = inferredCategory, updatedAt = now),
        )
        database.recipeAppStateDao().setInferredCategory(id, inferredCategory)

        return id to (existing == null)
    }

    private suspend fun recordHistory(outcome: WebImportOutcome, url: String, domain: String, fallbackTitle: String): WebImportOutcome {
        val status = when (outcome) {
            is WebImportOutcome.Success -> WebImportOutcomeStatus.SUCCESS
            is WebImportOutcome.NotFound -> WebImportOutcomeStatus.NOT_FOUND
            is WebImportOutcome.NetworkError -> WebImportOutcomeStatus.NETWORK_ERROR
            is WebImportOutcome.ParseError -> WebImportOutcomeStatus.PARSE_ERROR
        }
        val title = (outcome as? WebImportOutcome.Success)?.title ?: fallbackTitle
        val errorMessage = when (outcome) {
            is WebImportOutcome.NetworkError -> outcome.message
            is WebImportOutcome.ParseError -> outcome.message
            else -> null
        }
        database.webImportHistoryDao().insert(
            WebImportHistoryEntity(
                url = url,
                title = title,
                domain = domain,
                status = status,
                errorMessage = errorMessage,
                recipeId = (outcome as? WebImportOutcome.Success)?.recipeId,
                importedAt = clock(),
            ),
        )
        return outcome
    }

    companion object {
        private const val USER_AGENT = "Mozilla/5.0 (compatible; RecipeArchiveApp/1.0)"
        private const val PASTED_TEXT_LABEL = "Pasted text"

        internal fun normalizeUrl(input: String): String? {
            val trimmed = input.trim()
            if (trimmed.isBlank()) return null
            val withScheme = if (trimmed.contains("://")) trimmed else "https://$trimmed"
            return withScheme.toHttpUrlOrNull()?.toString()
        }

        private fun buildRawText(title: String, parsed: ParsedRecipe): String = buildString {
            appendLine(title)
            if (parsed.ingredients.isNotEmpty()) {
                appendLine()
                parsed.ingredients.forEach { appendLine(it) }
            }
            if (parsed.instructions.isNotEmpty()) {
                appendLine()
                parsed.instructions.forEach { appendLine(it) }
            }
        }.trim()
    }
}
