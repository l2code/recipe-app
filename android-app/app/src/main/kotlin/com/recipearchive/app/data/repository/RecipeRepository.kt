package com.recipearchive.app.data.repository

import android.content.Context
import com.recipearchive.app.data.import.ImportOutcome
import com.recipearchive.app.data.import.ImportService
import com.recipearchive.app.data.local.RecipeDatabase
import com.recipearchive.app.data.local.entity.HandwrittenNoteEntity
import com.recipearchive.app.data.local.entity.IngredientEntity
import com.recipearchive.app.data.local.entity.InstructionEntity
import com.recipearchive.app.data.local.entity.RecipeEntity
import com.recipearchive.app.data.local.entity.RecipePageEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow

class RecipeRepository(
    private val database: RecipeDatabase,
    private val importService: ImportService,
) {
    suspend fun importBundle(
        context: Context,
        assetName: String = ImportService.DEFAULT_ASSET_NAME,
    ): ImportOutcome = importService.importFromAssets(context, assetName)

    fun observeLibrary(query: String): Flow<List<RecipeSummary>> {
        val trimmed = query.trim()
        val matchIdsFlow: Flow<List<String>?> = if (trimmed.isEmpty()) {
            flow { emit(null) }
        } else {
            flow { emit(searchRecipeIds(trimmed)) }
        }
        return combine(
            database.recipeDao().observeAll(),
            database.recipeReviewFlagDao().observeRecipeIdsWithFlags(),
            database.recipeAppStateDao().observeFavoriteRecipeIds(),
            matchIdsFlow,
        ) { recipes, flaggedIds, favoriteIds, matchIds ->
            val flaggedSet = flaggedIds.toSet()
            val favoriteSet = favoriteIds.toSet()
            val byId = recipes.associateBy { it.id }
            val ordered = if (matchIds == null) recipes else matchIds.mapNotNull { byId[it] }
            ordered.map { recipe ->
                RecipeSummary(
                    id = recipe.id,
                    title = recipe.title,
                    sourcePublisher = recipe.sourcePublisher,
                    hasReviewFlags = flaggedSet.contains(recipe.id),
                    isFavorite = favoriteSet.contains(recipe.id),
                )
            }
        }
    }

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

        return combine(
            corePart,
            database.sourceEvidenceDao().observeForRecipe(recipeId),
            database.recipeReviewFlagDao().observeForRecipe(recipeId),
            database.recipeAppStateDao().observeForRecipe(recipeId),
        ) { core, evidence, flags, appState ->
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
}
