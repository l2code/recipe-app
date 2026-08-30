package com.recipearchive.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Denormalized search document, one row per recipe. Room maintains
 * [RecipeSearchFts] automatically via triggers whenever this table changes,
 * so the importer only ever writes here (delete-then-insert per recipe).
 */
@Entity(tableName = "recipe_search_documents", indices = [Index("recipeId")])
data class RecipeSearchDocumentEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "rowid") val rowId: Long = 0,
    val recipeId: String,
    val title: String,
    val rawText: String,
    val ingredientsText: String,
    val instructionsText: String,
    val handwritingText: String,
    val sourceText: String,
)

@Fts4(contentEntity = RecipeSearchDocumentEntity::class)
@Entity(tableName = "recipe_search_fts")
data class RecipeSearchFts(
    val recipeId: String,
    val title: String,
    val rawText: String,
    val ingredientsText: String,
    val instructionsText: String,
    val handwritingText: String,
    val sourceText: String,
)
