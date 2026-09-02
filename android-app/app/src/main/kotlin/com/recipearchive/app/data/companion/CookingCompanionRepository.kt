package com.recipearchive.app.data.companion

import com.recipearchive.app.data.local.RecipeDatabase
import com.recipearchive.app.data.local.entity.CookingSessionEntity
import com.recipearchive.app.data.local.entity.MealPlanEntryEntity
import com.recipearchive.app.data.local.entity.PantryItemEntity
import com.recipearchive.app.data.local.entity.ShoppingItemEntity
import com.recipearchive.app.data.local.entity.ShoppingItemSourceEntity
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

class CookingCompanionRepository(private val database: RecipeDatabase) {

    fun observeCookingHistory(): Flow<List<CookingHistoryItemUi>> = combine(
        database.cookingSessionDao().observeAllConfirmed(),
        database.recipeDao().observeAll(),
    ) { sessions, recipes ->
        val titlesById = recipes.associate { it.id to it.title }
        sessions.map { session ->
            CookingHistoryItemUi(session, titlesById[session.recipeId] ?: "Recipe")
        }
    }
    fun observeRecipe(recipeId: String): Flow<RecipeCompanionUi> = combine(
        database.cookingSessionDao().observeConfirmedForRecipe(recipeId),
        database.cookingSessionDao().observeActiveForRecipe(recipeId),
        database.cookingSessionDao().observePossibleForRecipe(recipeId),
        database.ingredientDao().observeForRecipe(recipeId),
        database.pantryDao().observeAll(),
    ) { sessions, active, possible, ingredients, pantry ->
        val pantryByKey = pantry.associateBy { it.ingredientKey }
        RecipeCompanionUi(
            sessions = sessions,
            activeSession = active,
            possibleSession = possible,
            ingredients = ingredients.map { ingredient ->
                val displayName = ingredient.item.ifBlank { ingredient.rawText }
                val key = IngredientNormalizer.key(displayName)
                val pantryItem = pantryByKey[key]
                IngredientAvailabilityUi(
                    ingredient = ingredient,
                    ingredientKey = key,
                    displayName = IngredientNormalizer.displayName(displayName),
                    status = PantryStatus.from(pantryItem?.status),
                    isStaple = pantryItem?.isStaple ?: false,
                )
            },
        )
    }

    fun observeSession(sessionId: String) = database.cookingSessionDao().observeById(sessionId)

    fun observeShoppingList(): Flow<List<ShoppingListItemUi>> = combine(
        database.shoppingDao().observeItems(),
        database.shoppingDao().observeSources(),
        database.recipeDao().observeAll(),
    ) { items, sources, recipes ->
        val recipeTitles = recipes.associate { it.id to it.title }
        items.map { item ->
            val itemSources = sources.filter { it.ingredientKey == item.ingredientKey }
            ShoppingListItemUi(
                item = item,
                sources = itemSources,
                sourceRecipeTitles = itemSources.mapNotNull { recipeTitles[it.recipeId] }.distinct(),
            )
        }
    }

    fun observePantry(): Flow<List<PantryCatalogItemUi>> = combine(
        database.pantryDao().observeAll(),
        database.ingredientDao().observeAll(),
    ) { pantry, ingredients ->
        val usage = ingredients.groupingBy {
            IngredientNormalizer.key(it.item.ifBlank { it.rawText })
        }.eachCount()
        pantry.map { PantryCatalogItemUi(it, usage[it.ingredientKey] ?: 0) }
    }

    fun observeMealPlan(): Flow<List<MealPlanItemUi>> = combine(
        database.mealPlanDao().observeAll(),
        database.recipeDao().observeAll(),
        database.ingredientDao().observeAll(),
        database.pantryDao().observeAll(),
    ) { entries, recipes, ingredients, pantry ->
        val recipesById = recipes.associateBy { it.id }
        val ingredientsByRecipe = ingredients.groupBy { it.recipeId }
        val pantryByKey = pantry.associateBy { it.ingredientKey }
        entries.mapNotNull { entry ->
            val recipe = recipesById[entry.recipeId] ?: return@mapNotNull null
            val recipeIngredients = ingredientsByRecipe[entry.recipeId].orEmpty()
            val available = recipeIngredients.count { ingredient ->
                val key = IngredientNormalizer.key(ingredient.item.ifBlank { ingredient.rawText })
                val item = pantryByKey[key]
                item?.status == PantryStatus.HAVE.storedValue ||
                    (item?.isStaple == true && item.status == PantryStatus.UNKNOWN.storedValue)
            }
            MealPlanItemUi(entry, recipe.title, available, recipeIngredients.size)
        }
    }

    suspend fun startCooking(recipeId: String): String {
        database.cookingSessionDao().getActiveForRecipe(recipeId)?.let { return it.id }
        val now = System.currentTimeMillis()
        database.cookingSessionDao().discardPossibleForRecipe(recipeId, now)
        val id = UUID.randomUUID().toString()
        database.cookingSessionDao().insert(
            CookingSessionEntity(
                id = id,
                recipeId = recipeId,
                startedAt = now,
                createdAt = now,
                updatedAt = now,
            ),
        )
        return id
    }

    suspend fun confirmSession(sessionId: String, notes: String, rating: Int?) {
        val session = database.cookingSessionDao().getById(sessionId) ?: return
        val finishedAt = System.currentTimeMillis()
        val timerStoppedAt = session.pausedAt ?: finishedAt
        database.cookingSessionDao().confirm(
            sessionId = sessionId,
            finishedAt = finishedAt,
            durationMillis = (timerStoppedAt - session.startedAt - session.totalPausedMillis).coerceAtLeast(0),
            notes = notes.trim(),
            rating = rating,
        )
    }

    suspend fun recordPossibleSession(recipeId: String, startedAt: Long, finishedAt: Long) {
        val duration = (finishedAt - startedAt).coerceAtLeast(0)
        if (duration < 8 * 60_000L) return
        if (database.cookingSessionDao().getActiveForRecipe(recipeId) != null) return
        if (database.cookingSessionDao().getPossibleForRecipe(recipeId) != null) return
        database.cookingSessionDao().insert(
            CookingSessionEntity(
                id = UUID.randomUUID().toString(),
                recipeId = recipeId,
                startedAt = startedAt,
                finishedAt = finishedAt,
                durationMillis = duration,
                origin = "inferred",
                status = "possible",
                createdAt = finishedAt,
                updatedAt = finishedAt,
            ),
        )
    }

    suspend fun confirmPossibleSession(sessionId: String, notes: String, rating: Int?) {
        database.cookingSessionDao().confirmPossible(sessionId, notes.trim(), rating, System.currentTimeMillis())
    }

    suspend fun pauseSession(sessionId: String) {
        database.cookingSessionDao().pause(sessionId, System.currentTimeMillis())
    }

    suspend fun resumeSession(sessionId: String) {
        database.cookingSessionDao().resume(sessionId, System.currentTimeMillis())
    }

    suspend fun discardSession(sessionId: String) {
        database.cookingSessionDao().discard(sessionId, System.currentTimeMillis())
    }

    suspend fun setPantryStatus(key: String, displayName: String, status: PantryStatus, isStaple: Boolean = false) {
        if (status == PantryStatus.UNKNOWN && !isStaple) {
            database.pantryDao().delete(key)
        } else {
            database.pantryDao().upsert(
                PantryItemEntity(key, displayName, status.storedValue, isStaple, System.currentTimeMillis()),
            )
        }
    }

    suspend fun addPantryItem(name: String, status: PantryStatus = PantryStatus.HAVE) {
        val cleaned = name.trim()
        if (cleaned.isBlank()) return
        setPantryStatus(IngredientNormalizer.key(cleaned), IngredientNormalizer.displayName(cleaned), status)
    }

    suspend fun addUnavailableToShopping(recipeId: String) {
        val ingredients = database.ingredientDao().getForRecipe(recipeId)
        val pantrySnapshot = database.pantryDao().observeAll().first()
        val pantryByKey = pantrySnapshot.associateBy { it.ingredientKey }
        val now = System.currentTimeMillis()
        ingredients.forEach { ingredient ->
            val rawName = ingredient.item.ifBlank { ingredient.rawText }
            val key = IngredientNormalizer.key(rawName)
            val pantryItem = pantryByKey[key]
            val available = pantryItem?.status == PantryStatus.HAVE.storedValue ||
                (pantryItem?.isStaple == true && pantryItem.status == PantryStatus.UNKNOWN.storedValue)
            if (!available) {
                database.shoppingDao().insertItem(
                    ShoppingItemEntity(key, IngredientNormalizer.displayName(rawName), createdAt = now, updatedAt = now),
                )
                database.shoppingDao().insertSource(
                    ShoppingItemSourceEntity(key, recipeId, ingredient.quantity, ingredient.unit, ingredient.rawText),
                )
            }
        }
    }

    suspend fun setShoppingChecked(key: String, displayName: String, checked: Boolean) {
        val now = System.currentTimeMillis()
        database.shoppingDao().setChecked(key, checked, now)
        if (checked) {
            val existing = database.pantryDao().get(key)
            setPantryStatus(key, displayName, PantryStatus.HAVE, existing?.isStaple ?: false)
        }
    }

    suspend fun deleteShoppingItem(key: String) = database.shoppingDao().delete(key)
    suspend fun clearCheckedShoppingItems() = database.shoppingDao().deleteChecked()

    suspend fun addMealPlan(recipeId: String, date: LocalDate, mealSlot: String = "Dinner") {
        database.mealPlanDao().insert(
            MealPlanEntryEntity(
                id = UUID.randomUUID().toString(),
                recipeId = recipeId,
                plannedDate = date.toString(),
                mealSlot = mealSlot,
                createdAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun deleteMealPlanEntry(entryId: String) = database.mealPlanDao().delete(entryId)
}
