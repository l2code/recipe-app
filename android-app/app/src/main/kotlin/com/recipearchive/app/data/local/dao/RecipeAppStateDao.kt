package com.recipearchive.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.recipearchive.app.data.local.entity.RecipeAppStateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecipeAppStateDao {
    /** Only creates a default row; never overwrites existing app-owned state. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertDefaultIfMissing(state: RecipeAppStateEntity)

    @Update
    suspend fun update(state: RecipeAppStateEntity)

    @Query("SELECT * FROM recipe_app_state WHERE recipeId = :recipeId")
    suspend fun getForRecipe(recipeId: String): RecipeAppStateEntity?

    @Query("SELECT * FROM recipe_app_state WHERE recipeId = :recipeId")
    fun observeForRecipe(recipeId: String): Flow<RecipeAppStateEntity?>

    @Query("SELECT recipeId FROM recipe_app_state WHERE isFavorite = 1")
    fun observeFavoriteRecipeIds(): Flow<List<String>>

    @Query("UPDATE recipe_app_state SET isFavorite = :isFavorite, updatedAt = :updatedAt WHERE recipeId = :recipeId")
    suspend fun setFavorite(recipeId: String, isFavorite: Boolean, updatedAt: Long)

    @Query("UPDATE recipe_app_state SET personalNotes = :notes, updatedAt = :updatedAt WHERE recipeId = :recipeId")
    suspend fun setNotes(recipeId: String, notes: String, updatedAt: Long)

    @Query("UPDATE recipe_app_state SET personalRating = :rating, updatedAt = :updatedAt WHERE recipeId = :recipeId")
    suspend fun setRating(recipeId: String, rating: Int?, updatedAt: Long)

    @Query("UPDATE recipe_app_state SET reviewCompleted = :completed, updatedAt = :updatedAt WHERE recipeId = :recipeId")
    suspend fun setReviewCompleted(recipeId: String, completed: Boolean, updatedAt: Long)
}
