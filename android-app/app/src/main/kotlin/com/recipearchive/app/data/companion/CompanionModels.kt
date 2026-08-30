package com.recipearchive.app.data.companion

import com.recipearchive.app.data.local.entity.CookingSessionEntity
import com.recipearchive.app.data.local.entity.IngredientEntity
import com.recipearchive.app.data.local.entity.MealPlanEntryEntity
import com.recipearchive.app.data.local.entity.PantryItemEntity
import com.recipearchive.app.data.local.entity.ShoppingItemEntity
import com.recipearchive.app.data.local.entity.ShoppingItemSourceEntity

enum class PantryStatus(val storedValue: String, val label: String) {
    UNKNOWN("unknown", "Unknown"),
    HAVE("have", "Have"),
    LOW("low", "Low"),
    DONT_HAVE("dont_have", "Don't have");

    companion object {
        fun from(value: String?): PantryStatus = entries.firstOrNull { it.storedValue == value } ?: UNKNOWN
    }
}

data class IngredientAvailabilityUi(
    val ingredient: IngredientEntity,
    val ingredientKey: String,
    val displayName: String,
    val status: PantryStatus,
    val isStaple: Boolean,
)

data class RecipeCompanionUi(
    val sessions: List<CookingSessionEntity> = emptyList(),
    val activeSession: CookingSessionEntity? = null,
    val ingredients: List<IngredientAvailabilityUi> = emptyList(),
) {
    val madeCount: Int get() = sessions.size
    val availableCount: Int get() = ingredients.count { it.status == PantryStatus.HAVE || (it.isStaple && it.status == PantryStatus.UNKNOWN) }
    val lowCount: Int get() = ingredients.count { it.status == PantryStatus.LOW }
    val neededCount: Int get() = ingredients.count { it.status == PantryStatus.DONT_HAVE }
    val unknownCount: Int get() = ingredients.count { it.status == PantryStatus.UNKNOWN && !it.isStaple }
}

data class ShoppingListItemUi(
    val item: ShoppingItemEntity,
    val sources: List<ShoppingItemSourceEntity>,
    val sourceRecipeTitles: List<String>,
)

data class PantryCatalogItemUi(
    val item: PantryItemEntity,
    val recipeUseCount: Int,
)

data class MealPlanItemUi(
    val entry: MealPlanEntryEntity,
    val recipeTitle: String,
    val availableCount: Int,
    val totalCount: Int,
) {
    val missingCount: Int get() = totalCount - availableCount
}
