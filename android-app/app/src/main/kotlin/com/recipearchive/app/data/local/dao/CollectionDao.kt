package com.recipearchive.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.recipearchive.app.data.local.entity.CollectionEntity
import com.recipearchive.app.data.local.entity.RecipeCollectionCrossRef
import kotlinx.coroutines.flow.Flow

@Dao
interface CollectionDao {
    @Query("SELECT * FROM collections ORDER BY sortOrder, name COLLATE NOCASE")
    fun observeAll(): Flow<List<CollectionEntity>>

    @Query("SELECT * FROM collections ORDER BY sortOrder, name COLLATE NOCASE")
    suspend fun getAll(): List<CollectionEntity>

    @Query("SELECT * FROM collections WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findByName(name: String): CollectionEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(collection: CollectionEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(collections: List<CollectionEntity>)

    @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM collections")
    suspend fun nextSortOrder(): Int

    @Query("SELECT * FROM recipe_collection_cross_ref")
    fun observeAllAssignments(): Flow<List<RecipeCollectionCrossRef>>

    @Query("SELECT collectionId FROM recipe_collection_cross_ref WHERE recipeId = :recipeId")
    fun observeCollectionIdsForRecipe(recipeId: String): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addRecipeToCollection(crossRef: RecipeCollectionCrossRef)

    @Query("DELETE FROM recipe_collection_cross_ref WHERE recipeId = :recipeId AND collectionId = :collectionId")
    suspend fun removeRecipeFromCollection(recipeId: String, collectionId: String)
}
