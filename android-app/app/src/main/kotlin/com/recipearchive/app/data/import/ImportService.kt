package com.recipearchive.app.data.import

import android.content.Context
import androidx.room.withTransaction
import com.recipearchive.app.data.local.RecipeDatabase
import com.recipearchive.app.data.local.entity.HandwrittenNoteEntity
import com.recipearchive.app.data.local.entity.ImportRunEntity
import com.recipearchive.app.data.local.entity.ImportStatus
import com.recipearchive.app.data.local.entity.IngredientEntity
import com.recipearchive.app.data.local.entity.InstructionEntity
import com.recipearchive.app.data.local.entity.RecipeAppStateEntity
import com.recipearchive.app.data.local.entity.RecipeEntity
import com.recipearchive.app.data.local.entity.RecipePageEntity
import com.recipearchive.app.data.local.entity.RecipeReviewFlagEntity
import com.recipearchive.app.data.local.entity.RecipeSearchDocumentEntity
import com.recipearchive.app.data.local.entity.SourceEvidenceEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement

/**
 * Deterministic, idempotent importer for the Phase 2 recipe bundle.
 *
 * Import-owned tables (recipe + all child tables) are fully replaced per
 * recipe inside one transaction; [RecipeAppStateEntity] rows are only ever
 * created with defaults for newly-seen recipes, never overwritten, so
 * favorites/ratings/notes survive a reimport untouched.
 */
class ImportService(
    private val database: RecipeDatabase,
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun importFromAssets(context: Context, assetName: String = DEFAULT_ASSET_NAME): ImportOutcome {
        val text = withContext(Dispatchers.IO) {
            context.assets.open(assetName).bufferedReader().use { it.readText() }
        }
        return import(text)
    }

    suspend fun import(jsonText: String): ImportOutcome = withContext(Dispatchers.IO) {
        val startedAt = clock()

        val envelope = try {
            json.decodeFromString(ImportBundleEnvelopeDto.serializer(), jsonText)
        } catch (e: Exception) {
            val reason = "Malformed bundle JSON: ${e.message}"
            val runId = recordFailedRun(startedAt, 0, "", reason)
            return@withContext ImportOutcome.Failed(runId, reason)
        }

        if (envelope.schemaVersion != SUPPORTED_SCHEMA_VERSION) {
            val reason = "Unsupported schema version ${envelope.schemaVersion}; " +
                "this app supports version $SUPPORTED_SCHEMA_VERSION"
            val runId = recordFailedRun(startedAt, envelope.schemaVersion, envelope.generatedAt.orEmpty(), reason)
            return@withContext ImportOutcome.Failed(runId, reason)
        }

        val validRecipes = mutableListOf<RecipeDto>()
        val skipped = mutableListOf<SkippedRecipe>()
        val seenIds = mutableSetOf<String>()
        envelope.recipes.forEachIndexed { index, element ->
            try {
                val dto = json.decodeFromJsonElement(RecipeDto.serializer(), element)
                when {
                    dto.id.isBlank() -> skipped += SkippedRecipe(index, null, "Missing or blank recipe id")
                    !seenIds.add(dto.id) -> skipped += SkippedRecipe(index, dto.id, "Duplicate recipe id within bundle")
                    else -> validRecipes += dto
                }
            } catch (e: Exception) {
                skipped += SkippedRecipe(index, null, "Failed to parse recipe: ${e.message}")
            }
        }

        var insertedCount = 0
        var updatedCount = 0
        val now = clock()

        try {
            database.withTransaction {
                for (dto in validRecipes) {
                    val isNew = writeRecipe(dto, envelope, now)
                    if (isNew) insertedCount++ else updatedCount++
                }
            }
        } catch (e: Exception) {
            val reason = "Import transaction failed: ${e.message}"
            val runId = recordFailedRun(startedAt, envelope.schemaVersion, envelope.generatedAt.orEmpty(), reason)
            return@withContext ImportOutcome.Failed(runId, reason)
        }

        val status = if (skipped.isEmpty()) ImportStatus.SUCCESS else ImportStatus.PARTIAL
        val run = ImportRunEntity(
            bundleSchemaVersion = envelope.schemaVersion,
            bundleGeneratedAt = envelope.generatedAt.orEmpty(),
            importStartedAt = startedAt,
            importCompletedAt = clock(),
            importedRecipeCount = validRecipes.size,
            insertedCount = insertedCount,
            updatedCount = updatedCount,
            deletedCount = 0,
            errorCount = skipped.size,
            status = status,
            errorSummary = skipped.takeIf { it.isNotEmpty() }?.let(::summarize),
        )
        val runId = database.importRunDao().insert(run)
        ImportOutcome.Completed(runId, status, validRecipes.size, insertedCount, updatedCount, skipped)
    }

    /** Returns true if this recipe was newly inserted, false if it already existed. */
    private suspend fun writeRecipe(dto: RecipeDto, envelope: ImportBundleEnvelopeDto, now: Long): Boolean {
        val recipeDao = database.recipeDao()
        val existing = recipeDao.getById(dto.id)
        val createdAt = existing?.createdAt ?: now

        val entity = RecipeEntity(
            id = dto.id,
            title = dto.title?.takeIf { it.isNotBlank() } ?: "Untitled recipe",
            rawText = dto.rawText.orEmpty(),
            wordCount = dto.wordCount ?: 0,
            arrangementStatus = dto.arrangementStatus.orEmpty(),
            duplicateStatus = dto.duplicateStatus.orEmpty(),
            sourcePublisher = dto.source?.publisher.orEmpty(),
            sourceDomain = dto.source?.domain.orEmpty(),
            sourceUrl = dto.source?.url.orEmpty(),
            sourceStatus = dto.source?.status.orEmpty(),
            importSchemaVersion = envelope.schemaVersion,
            importGeneratedAt = envelope.generatedAt.orEmpty(),
            createdAt = createdAt,
            lastImportedAt = now,
        )
        if (existing == null) recipeDao.insert(entity) else recipeDao.update(entity)

        val ingredientDao = database.ingredientDao()
        ingredientDao.deleteForRecipe(dto.id)
        if (dto.ingredients.isNotEmpty()) {
            ingredientDao.insertAll(
                dto.ingredients.mapIndexed { index, ing ->
                    IngredientEntity(
                        recipeId = dto.id,
                        displayOrder = index,
                        rawText = ing.rawText.orEmpty(),
                        quantity = ing.quantity.orEmpty(),
                        unit = ing.unit.orEmpty(),
                        item = ing.item.orEmpty(),
                        parseStatus = ing.parseStatus ?: "needs_review",
                    )
                },
            )
        }

        val instructionDao = database.instructionDao()
        instructionDao.deleteForRecipe(dto.id)
        if (dto.instructions.isNotEmpty()) {
            instructionDao.insertAll(
                dto.instructions.mapIndexed { index, step ->
                    InstructionEntity(
                        recipeId = dto.id,
                        displayOrder = step.order ?: (index + 1),
                        text = step.text.orEmpty(),
                        parseStatus = step.parseStatus ?: "needs_review",
                    )
                },
            )
        }

        val pageDao = database.recipePageDao()
        pageDao.deleteForRecipe(dto.id)
        if (dto.pageRefs.isNotEmpty()) {
            pageDao.insertAll(
                dto.pageRefs.mapIndexed { index, ref ->
                    val (scan, page) = parsePageRef(ref)
                    RecipePageEntity(
                        recipeId = dto.id,
                        displayOrder = index,
                        pageRef = ref,
                        scanFilename = scan,
                        pageNumber = page,
                    )
                },
            )
        }

        val noteDao = database.handwrittenNoteDao()
        noteDao.deleteForRecipe(dto.id)
        val validNotes = dto.handwrittenNotes.filter { !it.pageId.isNullOrBlank() }
        if (validNotes.isNotEmpty()) {
            noteDao.insertAll(
                validNotes.map { note ->
                    HandwrittenNoteEntity(
                        pageId = note.pageId!!,
                        recipeId = dto.id,
                        scan = note.scan.orEmpty(),
                        page = note.page ?: 0,
                        imagePath = note.imagePath.orEmpty(),
                        ocrDraft = note.ocrDraft.orEmpty(),
                        transcription = note.transcription.orEmpty(),
                        status = note.status.orEmpty(),
                        reasons = note.reasons.joinToString(";"),
                    )
                },
            )
        }

        val evidenceDao = database.sourceEvidenceDao()
        evidenceDao.deleteForRecipe(dto.id)
        val evidence = dto.source?.evidence.orEmpty().filter { it.isNotBlank() }
        if (evidence.isNotEmpty()) {
            evidenceDao.insertAll(
                evidence.mapIndexed { index, text ->
                    SourceEvidenceEntity(recipeId = dto.id, displayOrder = index, evidenceText = text)
                },
            )
        }

        val flagDao = database.recipeReviewFlagDao()
        flagDao.deleteForRecipe(dto.id)
        val flags = normalizeFlags(dto.reviewFlags)
        if (flags.isNotEmpty()) {
            flagDao.insertAll(flags.map { RecipeReviewFlagEntity(recipeId = dto.id, flagValue = it) })
        }

        val searchDao = database.recipeSearchDao()
        searchDao.deleteDocument(dto.id)
        searchDao.insertDocument(
            RecipeSearchDocumentEntity(
                recipeId = dto.id,
                title = dto.title.orEmpty(),
                rawText = dto.rawText.orEmpty(),
                ingredientsText = dto.ingredients.joinToString(" ") {
                    listOfNotNull(it.rawText, it.item).joinToString(" ")
                },
                instructionsText = dto.instructions.joinToString(" ") { it.text.orEmpty() },
                handwritingText = validNotes.joinToString(" ") {
                    listOfNotNull(it.transcription, it.ocrDraft).joinToString(" ")
                },
                sourceText = listOfNotNull(
                    dto.source?.publisher,
                    dto.source?.domain,
                    dto.source?.url,
                ).joinToString(" "),
            ),
        )

        database.recipeAppStateDao().insertDefaultIfMissing(
            RecipeAppStateEntity(recipeId = dto.id, updatedAt = now),
        )

        return existing == null
    }

    private suspend fun recordFailedRun(startedAt: Long, schemaVersion: Int, generatedAt: String, reason: String): Long {
        val run = ImportRunEntity(
            bundleSchemaVersion = schemaVersion,
            bundleGeneratedAt = generatedAt,
            importStartedAt = startedAt,
            importCompletedAt = clock(),
            importedRecipeCount = 0,
            insertedCount = 0,
            updatedCount = 0,
            deletedCount = 0,
            errorCount = 1,
            status = ImportStatus.FAILED,
            errorSummary = reason.take(MAX_ERROR_SUMMARY_LENGTH),
        )
        return database.importRunDao().insert(run)
    }

    private fun summarize(skipped: List<SkippedRecipe>): String =
        skipped.joinToString("; ") { s ->
            val idPart = s.id?.let { " ($it)" }.orEmpty()
            "#${s.index}$idPart: ${s.reason}"
        }.take(MAX_ERROR_SUMMARY_LENGTH)

    companion object {
        const val SUPPORTED_SCHEMA_VERSION = 1
        const val DEFAULT_ASSET_NAME = "recipe-app-import.json"
        private const val MAX_ERROR_SUMMARY_LENGTH = 4000
        private val pageRefRegex = Regex("^(.*)#p(\\d+)$")

        internal fun parsePageRef(ref: String): Pair<String?, Int?> {
            val match = pageRefRegex.matchEntire(ref) ?: return null to null
            return match.groupValues[1] to match.groupValues[2].toIntOrNull()
        }

        internal fun normalizeFlags(flags: List<String>): List<String> =
            flags.flatMap { it.split(';', ',') }
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
    }
}
