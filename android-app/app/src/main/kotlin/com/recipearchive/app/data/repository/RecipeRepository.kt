package com.recipearchive.app.data.repository

import android.content.Context
import com.recipearchive.app.data.import.ImportOutcome
import com.recipearchive.app.data.import.ImportService
import com.recipearchive.app.data.local.RecipeDatabase
import com.recipearchive.app.data.local.entity.HandwrittenNoteEntity
import com.recipearchive.app.data.local.entity.CollectionEntity
import com.recipearchive.app.data.local.entity.IngredientEntity
import com.recipearchive.app.data.local.entity.InstructionEntity
import com.recipearchive.app.data.local.entity.RecipeEntity
import com.recipearchive.app.data.local.entity.RecipePageEntity
import com.recipearchive.app.data.local.entity.RecipeCollectionCrossRef
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import java.util.UUID

class RecipeRepository(
    private val database: RecipeDatabase,
    private val importService: ImportService,
) {
    suspend fun importBundle(
        context: Context,
        assetName: String = ImportService.DEFAULT_ASSET_NAME,
    ): ImportOutcome = importService.importFromAssets(context, assetName)

    fun observeLibrary(
        query: String,
        category: String? = null,
        collectionId: String? = null,
    ): Flow<List<RecipeSummary>> {
        val trimmed = query.trim()
        val matchIdsFlow: Flow<List<String>?> = if (trimmed.isEmpty()) {
            flow { emit(null) }
        } else {
            flow { emit(searchRecipeIds(trimmed)) }
        }
        val organizationFlow = combine(
            database.recipeAppStateDao().observeAll(),
            database.collectionDao().observeAllAssignments(),
        ) { states, assignments -> states to assignments }
        return combine(
            database.recipeDao().observeAll(),
            database.recipeReviewFlagDao().observeRecipeIdsWithFlags(),
            organizationFlow,
            matchIdsFlow,
            database.cookingSessionDao().observeAllConfirmed(),
        ) { recipes, flaggedIds, organization, matchIds, sessions ->
            val (states, assignments) = organization
            val flaggedSet = flaggedIds.toSet()
            val stateByRecipe = states.associateBy { it.recipeId }
            val sessionsByRecipe = sessions.groupBy { it.recipeId }
            val collectionsByRecipe = assignments.groupBy { it.recipeId }
                .mapValues { (_, refs) -> refs.map { it.collectionId }.toSet() }
            val byId = recipes.associateBy { it.id }
            val ordered = if (matchIds == null) recipes else matchIds.mapNotNull { byId[it] }
            ordered.mapNotNull { recipe ->
                val appState = stateByRecipe[recipe.id]
                val collectionIds = collectionsByRecipe[recipe.id].orEmpty()
                if (category != null && appState?.category != category) return@mapNotNull null
                if (collectionId != null && collectionId !in collectionIds) return@mapNotNull null
                val recipeSessions = sessionsByRecipe[recipe.id].orEmpty()
                val durations = recipeSessions.mapNotNull { it.durationMillis }
                RecipeSummary(
                    id = recipe.id,
                    title = recipe.title,
                    sourcePublisher = recipe.sourcePublisher,
                    hasReviewFlags = flaggedSet.contains(recipe.id),
                    isFavorite = appState?.isFavorite ?: false,
                    personalRating = appState?.personalRating,
                    category = appState?.category,
                    collectionIds = collectionIds,
                    madeCount = recipeSessions.size,
                    lastMadeAt = recipeSessions.firstOrNull()?.finishedAt,
                    averageDurationMillis = durations.takeIf { it.isNotEmpty() }?.average()?.toLong(),
                )
            }
        }
    }

    fun observeCollections(): Flow<List<CollectionEntity>> = database.collectionDao().observeAll()

    suspend fun searchRecipeIds(query: String): List<String> {
        val sanitized = SearchQuerySanitizer.sanitize(query) ?: return emptyList()
        val titleMatches = database.recipeSearchDao().searchTitleMatches(sanitized)
        val allMatches = database.recipeSearchDao().searchAllMatches(sanitized)
        val ordered = LinkedHashSet<String>()
        ordered.addAll(titleMatches)
        ordered.addAll(allMatches)
        return ordered.toList()
    }

    fun observeRecipeDetail(recipeId: String): Flow<RecipeDetailUi?> {
        data class CorePart(
            val recipe: RecipeEntity?,
            val ingredients: List<IngredientEntity>,
            val instructions: List<InstructionEntity>,
            val pages: List<RecipePageEntity>,
            val notes: List<HandwrittenNoteEntity>,
        )

        val corePart = combine(
            database.recipeDao().observeById(recipeId),
            database.ingredientDao().observeForRecipe(recipeId),
            database.instructionDao().observeForRecipe(recipeId),
            database.recipePageDao().observeForRecipe(recipeId),
            database.handwrittenNoteDao().observeForRecipe(recipeId),
        ) { recipe, ingredients, instructions, pages, notes ->
            CorePart(recipe, ingredients, instructions, pages, notes)
        }

        data class OrganizationPart(
            val collections: List<CollectionEntity>,
            val selectedIds: Set<String>,
        )
        val organizationPart = combine(
            database.collectionDao().observeAll(),
            database.collectionDao().observeCollectionIdsForRecipe(recipeId),
        ) { collections, selectedIds -> OrganizationPart(collections, selectedIds.toSet()) }

        return combine(
            corePart,
            database.sourceEvidenceDao().observeForRecipe(recipeId),
            database.recipeReviewFlagDao().observeForRecipe(recipeId),
            database.recipeAppStateDao().observeForRecipe(recipeId),
            organizationPart,
        ) { core, evidence, flags, appState, organization ->
            val recipe = core.recipe ?: return@combine null
            RecipeDetailUi(
                recipe = recipe,
                ingredients = core.ingredients,
                instructions = core.instructions,
                pages = core.pages,
                handwrittenNotes = core.notes,
                sourceEvidence = evidence,
                reviewFlags = flags,
                appState = appState,
                collections = organization.collections,
                collectionIds = organization.selectedIds,
            )
        }
    }

    suspend fun setFavorite(recipeId: String, isFavorite: Boolean) {
        database.recipeAppStateDao().setFavorite(recipeId, isFavorite, System.currentTimeMillis())
    }

    suspend fun setPersonalNotes(recipeId: String, notes: String) {
        database.recipeAppStateDao().setNotes(recipeId, notes, System.currentTimeMillis())
    }

    suspend fun setPersonalRating(recipeId: String, rating: Int?) {
        database.recipeAppStateDao().setRating(recipeId, rating, System.currentTimeMillis())
    }

    suspend fun setImportedNotesReviewStatus(recipeId: String, status: String) {
        database.recipeAppStateDao().setImportedNotesReviewStatus(recipeId, status, System.currentTimeMillis())
    }

    suspend fun setCategory(recipeId: String, category: String?) {
        database.recipeAppStateDao().setCategory(recipeId, category, System.currentTimeMillis())
    }

    suspend fun setRecipeCollection(recipeId: String, collectionId: String, selected: Boolean) {
        if (selected) {
            database.collectionDao().addRecipeToCollection(RecipeCollectionCrossRef(recipeId, collectionId))
        } else {
            database.collectionDao().removeRecipeFromCollection(recipeId, collectionId)
        }
    }

    suspend fun createCollection(name: String): String? {
        val cleaned = name.trim().replace(Regex("\\s+"), " ")
        if (cleaned.isBlank()) return null
        database.collectionDao().findByName(cleaned)?.let { return it.id }
        val id = UUID.randomUUID().toString()
        database.collectionDao().insert(
            CollectionEntity(id, cleaned, database.collectionDao().nextSortOrder(), System.currentTimeMillis()),
        )
        return database.collectionDao().findByName(cleaned)?.id ?: id
    }
}
