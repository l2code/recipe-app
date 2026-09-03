package com.recipearchive.app.data.webimport

import androidx.room.withTransaction
import com.recipearchive.app.data.local.RecipeDatabase
import com.recipearchive.app.data.local.entity.IngredientEntity
import com.recipearchive.app.data.local.entity.InstructionEntity
import com.recipearchive.app.data.local.entity.RecipeAppStateEntity
import com.recipearchive.app.data.local.entity.RecipeEntity
import com.recipearchive.app.data.local.entity.RecipeSearchDocumentEntity
import com.recipearchive.app.data.organization.RecipeCategories
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Fetches an arbitrary recipe URL, parses its `schema.org/Recipe` JSON-LD via
 * [RecipeJsonLdParser], and upserts the result into the same Room tables the
 * offline archive [com.recipearchive.app.data.import.ImportService] writes to.
 *
 * Recipes saved this way get a random UUID id (never an `R####` archive id),
 * and are deduplicated by [RecipeEntity.sourceUrl]: reimporting the same URL
 * updates the existing row in place instead of creating a second copy.
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
            val normalizedUrl = normalizeUrl(url)
            if (normalizedUrl == null) {
                return@withContext WebImportOutcome.ParseError("Not a valid URL")
            }

            val html = try {
                fetch(normalizedUrl)
            } catch (e: IOException) {
                return@withContext WebImportOutcome.NetworkError(e.message ?: "Network error")
            }

            val parsed = try {
                parser.parse(html)
            } catch (e: Exception) {
                return@withContext WebImportOutcome.ParseError(e.message ?: "Failed to parse recipe")
            }

            if (parsed == null || parsed.isEmpty) {
                return@withContext WebImportOutcome.NotFound
            }

            val domain = normalizedUrl.toHttpUrlOrNull()?.host?.removePrefix("www.").orEmpty()
            val publisher = sourcePublisherOverride?.takeIf { it.isNotBlank() } ?: domain
            val now = clock()

            val (recipeId, wasNew) = database.withTransaction {
                writeRecipe(parsed, normalizedUrl, domain, publisher, now)
            }
            WebImportOutcome.Success(recipeId, parsed.title, wasNew)
        }

    private fun fetch(url: String): String {
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            return response.body?.string() ?: throw IOException("Empty response body")
        }
    }

    private suspend fun writeRecipe(
        parsed: ParsedRecipe,
        url: String,
        domain: String,
        publisher: String,
        now: Long,
    ): Pair<String, Boolean> {
        val recipeDao = database.recipeDao()
        val existing = recipeDao.getBySourceUrl(url)
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

    companion object {
        private const val USER_AGENT = "Mozilla/5.0 (compatible; RecipeArchiveApp/1.0)"

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
