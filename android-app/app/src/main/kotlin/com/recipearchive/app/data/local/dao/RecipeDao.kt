package com.recipearchive.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.recipearchive.app.data.local.entity.RecipeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecipeDao {
    // Deliberately NOT an @Insert(onConflict = REPLACE): SQLite implements REPLACE as a physical
    // delete-then-insert of the conflicting row, which would cascade-delete every foreign-key
    // child row -- including the app-owned recipe_app_state table -- on every reimport. An
    // explicit insert/update pair updates the existing row in place instead, leaving children
    // (and app-owned state) undisturbed.
    @Insert
    suspend fun insert(recipe: RecipeEntity)

    @Update
    suspend fun update(recipe: RecipeEntity)

    @Query("SELECT * FROM recipes WHERE id = :id")
    suspend fun getById(id: String): RecipeEntity?

    @Query("SELECT id FROM recipes")
    suspend fun getAllIds(): List<String>

    @Query("DELETE FROM recipes WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM recipes WHERE id = :id")
    fun observeById(id: String): Flow<RecipeEntity?>

    @Query("SELECT * FROM recipes ORDER BY title COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<RecipeEntity>>

    @Query("SELECT COUNT(*) FROM recipes")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM recipes")
    fun observeCount(): Flow<Int>
}
