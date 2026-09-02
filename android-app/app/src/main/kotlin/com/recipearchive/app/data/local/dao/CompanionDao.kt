package com.recipearchive.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.recipearchive.app.data.local.entity.CookingSessionEntity
import com.recipearchive.app.data.local.entity.MealPlanEntryEntity
import com.recipearchive.app.data.local.entity.PantryItemEntity
import com.recipearchive.app.data.local.entity.ShoppingItemEntity
import com.recipearchive.app.data.local.entity.ShoppingItemSourceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CookingSessionDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(session: CookingSessionEntity)

    @Query("SELECT * FROM cooking_sessions WHERE id = :sessionId")
    fun observeById(sessionId: String): Flow<CookingSessionEntity?>

    @Query("SELECT * FROM cooking_sessions WHERE id = :sessionId")
    suspend fun getById(sessionId: String): CookingSessionEntity?

    @Query("SELECT * FROM cooking_sessions WHERE recipeId = :recipeId AND status = 'confirmed' ORDER BY finishedAt DESC")
    fun observeConfirmedForRecipe(recipeId: String): Flow<List<CookingSessionEntity>>

    @Query("SELECT * FROM cooking_sessions WHERE status = 'confirmed' ORDER BY finishedAt DESC")
    fun observeAllConfirmed(): Flow<List<CookingSessionEntity>>

    @Query("SELECT * FROM cooking_sessions WHERE recipeId = :recipeId AND status = 'active' ORDER BY startedAt DESC LIMIT 1")
    fun observeActiveForRecipe(recipeId: String): Flow<CookingSessionEntity?>

    @Query("SELECT * FROM cooking_sessions WHERE recipeId = :recipeId AND status = 'active' ORDER BY startedAt DESC LIMIT 1")
    suspend fun getActiveForRecipe(recipeId: String): CookingSessionEntity?

    @Query("UPDATE cooking_sessions SET finishedAt = :finishedAt, durationMillis = :durationMillis, notes = :notes, rating = :rating, status = 'confirmed', updatedAt = :finishedAt WHERE id = :sessionId")
    suspend fun confirm(sessionId: String, finishedAt: Long, durationMillis: Long, notes: String, rating: Int?)

    @Query("UPDATE cooking_sessions SET status = 'discarded', finishedAt = :finishedAt, updatedAt = :finishedAt WHERE id = :sessionId")
    suspend fun discard(sessionId: String, finishedAt: Long)
}

@Dao
interface PantryDao {
    @Query("SELECT * FROM pantry_items ORDER BY displayName COLLATE NOCASE")
    fun observeAll(): Flow<List<PantryItemEntity>>

    @Query("SELECT * FROM pantry_items WHERE ingredientKey = :ingredientKey")
    suspend fun get(ingredientKey: String): PantryItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: PantryItemEntity)

    @Query("DELETE FROM pantry_items WHERE ingredientKey = :ingredientKey")
    suspend fun delete(ingredientKey: String)
}

@Dao
interface ShoppingDao {
    @Query("SELECT * FROM shopping_items ORDER BY isChecked, displayName COLLATE NOCASE")
    fun observeItems(): Flow<List<ShoppingItemEntity>>

    @Query("SELECT * FROM shopping_item_sources")
    fun observeSources(): Flow<List<ShoppingItemSourceEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertItem(item: ShoppingItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSource(source: ShoppingItemSourceEntity)

    @Query("UPDATE shopping_items SET isChecked = :checked, updatedAt = :updatedAt WHERE ingredientKey = :ingredientKey")
    suspend fun setChecked(ingredientKey: String, checked: Boolean, updatedAt: Long)

    @Query("DELETE FROM shopping_items WHERE ingredientKey = :ingredientKey")
    suspend fun delete(ingredientKey: String)

    @Query("DELETE FROM shopping_items WHERE isChecked = 1")
    suspend fun deleteChecked()
}

@Dao
interface MealPlanDao {
    @Query("SELECT * FROM meal_plan_entries ORDER BY plannedDate, mealSlot")
    fun observeAll(): Flow<List<MealPlanEntryEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entry: MealPlanEntryEntity)

    @Query("DELETE FROM meal_plan_entries WHERE id = :entryId")
    suspend fun delete(entryId: String)
}
