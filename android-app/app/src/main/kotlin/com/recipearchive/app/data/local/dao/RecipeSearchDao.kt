package com.recipearchive.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.recipearchive.app.data.local.entity.RecipeSearchDocumentEntity

@Dao
interface RecipeSearchDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(document: RecipeSearchDocumentEntity)

    @Query("DELETE FROM recipe_search_documents WHERE recipeId = :recipeId")
    suspend fun deleteDocument(recipeId: String)

    /** Recipe IDs whose title matches, in FTS rowid order (used to rank title hits first). */
    @Query(
        """
        SELECT recipeId FROM recipe_search_fts
        WHERE recipe_search_fts MATCH 'title:' || :ftsQuery
        """,
    )
    suspend fun searchTitleMatches(ftsQuery: String): List<String>

    /** All matching recipe IDs across every indexed field. */
    @Query(
        """
        SELECT recipeId FROM recipe_search_fts
        WHERE recipe_search_fts MATCH :ftsQuery
        """,
    )
    suspend fun searchAllMatches(ftsQuery: String): List<String>
}
