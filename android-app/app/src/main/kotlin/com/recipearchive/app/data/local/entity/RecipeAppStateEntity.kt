package com.recipearchive.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/**
 * App-owned data. Never written by the importer beyond creating a default row
 * for newly-seen recipes; reimporting a bundle must never modify existing rows.
 */
@Entity(
    tableName = "recipe_app_state",
    foreignKeys = [
        ForeignKey(
            entity = RecipeEntity::class,
            parentColumns = ["id"],
            childColumns = ["recipeId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class RecipeAppStateEntity(
    @PrimaryKey val recipeId: String,
    val isFavorite: Boolean = false,
    val personalRating: Int? = null,
    val personalNotes: String = "",
    val reviewCompleted: Boolean = false,
    val updatedAt: Long = 0,
)
