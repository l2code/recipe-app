package com.recipearchive.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.recipearchive.app.data.local.entity.HandwrittenNoteEntity
import com.recipearchive.app.data.local.entity.IngredientEntity
import com.recipearchive.app.data.local.entity.InstructionEntity
import com.recipearchive.app.data.local.entity.RecipePageEntity
import com.recipearchive.app.data.local.entity.RecipeReviewFlagEntity
import com.recipearchive.app.data.local.entity.SourceEvidenceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface IngredientDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(ingredients: List<IngredientEntity>)

    @Query("DELETE FROM ingredients WHERE recipeId = :recipeId")
    suspend fun deleteForRecipe(recipeId: String)

    @Query("SELECT * FROM ingredients WHERE recipeId = :recipeId ORDER BY displayOrder ASC")
    fun observeForRecipe(recipeId: String): Flow<List<IngredientEntity>>

    @Query("SELECT * FROM ingredients WHERE recipeId = :recipeId ORDER BY displayOrder ASC")
    suspend fun getForRecipe(recipeId: String): List<IngredientEntity>

    @Query("SELECT * FROM ingredients ORDER BY recipeId, displayOrder")
    fun observeAll(): Flow<List<IngredientEntity>>
}

@Dao
interface InstructionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(instructions: List<InstructionEntity>)

    @Query("DELETE FROM instructions WHERE recipeId = :recipeId")
    suspend fun deleteForRecipe(recipeId: String)

    @Query("SELECT * FROM instructions WHERE recipeId = :recipeId ORDER BY displayOrder ASC")
    fun observeForRecipe(recipeId: String): Flow<List<InstructionEntity>>
}

@Dao
interface RecipePageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(pages: List<RecipePageEntity>)

    @Query("DELETE FROM recipe_pages WHERE recipeId = :recipeId")
    suspend fun deleteForRecipe(recipeId: String)

    @Query("SELECT * FROM recipe_pages WHERE recipeId = :recipeId ORDER BY displayOrder ASC")
    fun observeForRecipe(recipeId: String): Flow<List<RecipePageEntity>>
}

@Dao
interface HandwrittenNoteDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(notes: List<HandwrittenNoteEntity>)

    @Query("DELETE FROM handwritten_notes WHERE recipeId = :recipeId")
    suspend fun deleteForRecipe(recipeId: String)

    @Query("SELECT * FROM handwritten_notes WHERE recipeId = :recipeId ORDER BY page ASC")
    fun observeForRecipe(recipeId: String): Flow<List<HandwrittenNoteEntity>>
}

@Dao
interface SourceEvidenceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(evidence: List<SourceEvidenceEntity>)

    @Query("DELETE FROM source_evidence WHERE recipeId = :recipeId")
    suspend fun deleteForRecipe(recipeId: String)

    @Query("SELECT * FROM source_evidence WHERE recipeId = :recipeId ORDER BY displayOrder ASC")
    fun observeForRecipe(recipeId: String): Flow<List<SourceEvidenceEntity>>
}

@Dao
interface RecipeReviewFlagDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(flags: List<RecipeReviewFlagEntity>)

    @Query("DELETE FROM recipe_review_flags WHERE recipeId = :recipeId")
    suspend fun deleteForRecipe(recipeId: String)

    @Query("SELECT * FROM recipe_review_flags WHERE recipeId = :recipeId")
    fun observeForRecipe(recipeId: String): Flow<List<RecipeReviewFlagEntity>>

    @Query("SELECT DISTINCT recipeId FROM recipe_review_flags")
    fun observeRecipeIdsWithFlags(): Flow<List<String>>
}
