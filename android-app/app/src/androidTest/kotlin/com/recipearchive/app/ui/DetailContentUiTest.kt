package com.recipearchive.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.recipearchive.app.data.local.entity.IngredientEntity
import com.recipearchive.app.data.local.entity.InstructionEntity
import com.recipearchive.app.data.local.entity.RecipeEntity
import com.recipearchive.app.data.repository.RecipeDetailUi
import com.recipearchive.app.ui.detail.DetailContent
import org.junit.Rule
import org.junit.Test

class DetailContentUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun baseRecipe() = RecipeEntity(
        id = "R0001",
        title = "Chicken Soup",
        rawText = "raw ocr text",
        wordCount = 5,
        arrangementStatus = "single_group",
        duplicateStatus = "canonical",
        sourcePublisher = "",
        sourceDomain = "",
        sourceUrl = "",
        sourceStatus = "unknown",
        importSchemaVersion = 1,
        importGeneratedAt = "2026-01-01T00:00:00Z",
        createdAt = 0L,
        lastImportedAt = 0L,
    )

    @Test
    fun missingIngredientsAndInstructionsRenderSafely() {
        val detail = RecipeDetailUi(
            recipe = baseRecipe(),
            ingredients = emptyList(),
            instructions = emptyList(),
            pages = emptyList(),
            handwrittenNotes = emptyList(),
            sourceEvidence = emptyList(),
            reviewFlags = emptyList(),
            appState = null,
        )

        composeRule.setContent {
            DetailContent(detail = detail, onNotesChanged = {})
        }

        composeRule.onNodeWithText("No ingredients were extracted for this recipe.").assertIsDisplayed()
        composeRule.onNodeWithText("No instructions were extracted for this recipe.").assertIsDisplayed()
    }

    @Test
    fun ingredientsAndInstructionsRenderInOrder() {
        val detail = RecipeDetailUi(
            recipe = baseRecipe(),
            ingredients = listOf(
                IngredientEntity(id = 1, recipeId = "R0001", displayOrder = 0, rawText = "1 cup chicken", quantity = "1", unit = "cup", item = "chicken", parseStatus = "candidate"),
            ),
            instructions = listOf(
                InstructionEntity(id = 1, recipeId = "R0001", displayOrder = 1, text = "Boil water", parseStatus = "candidate"),
            ),
            pages = emptyList(),
            handwrittenNotes = emptyList(),
            sourceEvidence = emptyList(),
            reviewFlags = emptyList(),
            appState = null,
        )

        composeRule.setContent {
            DetailContent(detail = detail, onNotesChanged = {})
        }

        composeRule.onNodeWithText("1 cup chicken").assertIsDisplayed()
        composeRule.onNodeWithText("Boil water").assertIsDisplayed()
    }
}
