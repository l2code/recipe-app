package com.recipearchive.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "cooking_sessions",
    foreignKeys = [
        ForeignKey(
            entity = RecipeEntity::class,
            parentColumns = ["id"],
            childColumns = ["recipeId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("recipeId"), Index("status")],
)
data class CookingSessionEntity(
    @PrimaryKey val id: String,
    val recipeId: String,
    val startedAt: Long,
    val finishedAt: Long? = null,
    val durationMillis: Long? = null,
    val notes: String = "",
    val origin: String = "manual",
    val status: String = "active",
    val rating: Int? = null,
    val pausedAt: Long? = null,
    val totalPausedMillis: Long = 0,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(tableName = "pantry_items")
data class PantryItemEntity(
    @PrimaryKey val ingredientKey: String,
    val displayName: String,
    val status: String,
    val isStaple: Boolean = false,
    val updatedAt: Long,
)

@Entity(tableName = "shopping_items")
data class ShoppingItemEntity(
    @PrimaryKey val ingredientKey: String,
    val displayName: String,
    val isChecked: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "shopping_item_sources",
    primaryKeys = ["ingredientKey", "recipeId"],
    foreignKeys = [
        ForeignKey(
            entity = ShoppingItemEntity::class,
            parentColumns = ["ingredientKey"],
            childColumns = ["ingredientKey"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = RecipeEntity::class,
            parentColumns = ["id"],
            childColumns = ["recipeId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("recipeId")],
)
data class ShoppingItemSourceEntity(
    val ingredientKey: String,
    val recipeId: String,
    val quantity: String,
    val unit: String,
    val rawText: String,
)

@Entity(
    tableName = "meal_plan_entries",
    foreignKeys = [
        ForeignKey(
            entity = RecipeEntity::class,
            parentColumns = ["id"],
            childColumns = ["recipeId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("recipeId"), Index("plannedDate")],
)
data class MealPlanEntryEntity(
    @PrimaryKey val id: String,
    val recipeId: String,
    val plannedDate: String,
    val mealSlot: String = "Dinner",
    val servings: Int? = null,
    val createdAt: Long,
)
