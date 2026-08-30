package com.recipearchive.app.ui

import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.recipearchive.app.data.repository.RecipeSummary
import com.recipearchive.app.ui.library.LibraryContent
import com.recipearchive.app.ui.library.LibraryUiState
import org.junit.Rule
import org.junit.Test

class LibraryContentUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val sampleRecipes = listOf(
        RecipeSummary("R0001", "Chicken Soup", "Grandma's Notebook", hasReviewFlags = true, isFavorite = false),
        RecipeSummary("R0002", "Grandma's Apple Pie", "Food Network", hasReviewFlags = false, isFavorite = true),
    )

    @Test
    fun libraryRendersImportedRecipes() {
        composeRule.setContent {
            LibraryContent(
                state = LibraryUiState(recipes = sampleRecipes, hasImportedOnce = true),
                widthSizeClass = WindowWidthSizeClass.Compact,
                onQueryChange = {},
                onRecipeClick = {},
                onToggleFavorite = { _, _ -> },
                onRetryImport = {},
            )
        }

        composeRule.onNodeWithText("Chicken Soup").assertIsDisplayed()
        composeRule.onNodeWithText("Grandma's Apple Pie").assertIsDisplayed()
    }

    @Test
    fun typingInSearchFieldNotifiesCallback() {
        var lastQuery = ""
        composeRule.setContent {
            LibraryContent(
                state = LibraryUiState(recipes = sampleRecipes, hasImportedOnce = true),
                widthSizeClass = WindowWidthSizeClass.Compact,
                onQueryChange = { lastQuery = it },
                onRecipeClick = {},
                onToggleFavorite = { _, _ -> },
                onRetryImport = {},
            )
        }

        composeRule.onNodeWithContentDescription("Search recipes").performTextInput("soup")

        assert(lastQuery == "soup") { "Expected query callback to receive 'soup', got '$lastQuery'" }
    }

    @Test
    fun selectingARecipeInvokesOnRecipeClickWithItsId() {
        var clickedId: String? = null
        composeRule.setContent {
            LibraryContent(
                state = LibraryUiState(recipes = sampleRecipes, hasImportedOnce = true),
                widthSizeClass = WindowWidthSizeClass.Compact,
                onQueryChange = {},
                onRecipeClick = { clickedId = it },
                onToggleFavorite = { _, _ -> },
                onRetryImport = {},
            )
        }

        composeRule.onNodeWithText("Chicken Soup").performClick()

        assert(clickedId == "R0001") { "Expected click on Chicken Soup to report id R0001, got $clickedId" }
    }

    @Test
    fun emptyLibraryStateRendersSafely() {
        composeRule.setContent {
            LibraryContent(
                state = LibraryUiState(recipes = emptyList(), hasImportedOnce = true),
                widthSizeClass = WindowWidthSizeClass.Compact,
                onQueryChange = {},
                onRecipeClick = {},
                onToggleFavorite = { _, _ -> },
                onRetryImport = {},
            )
        }

        composeRule.onNodeWithText("No recipes yet. Once a bundle is imported, your recipes will appear here.")
            .assertIsDisplayed()
    }

    @Test
    fun noResultsStateRendersWhenSearchFindsNothing() {
        composeRule.setContent {
            LibraryContent(
                state = LibraryUiState(query = "xyz", recipes = emptyList(), hasImportedOnce = true),
                widthSizeClass = WindowWidthSizeClass.Compact,
                onQueryChange = {},
                onRecipeClick = {},
                onToggleFavorite = { _, _ -> },
                onRetryImport = {},
            )
        }

        composeRule.onNodeWithText("No recipes match \"xyz\".").assertIsDisplayed()
    }

    @Test
    fun importErrorStateOffersRetry() {
        var retried = false
        composeRule.setContent {
            LibraryContent(
                state = LibraryUiState(
                    recipes = emptyList(),
                    hasImportedOnce = true,
                    importError = "Unsupported schema version 99",
                ),
                widthSizeClass = WindowWidthSizeClass.Compact,
                onQueryChange = {},
                onRecipeClick = {},
                onToggleFavorite = { _, _ -> },
                onRetryImport = { retried = true },
            )
        }

        composeRule.onNodeWithText("Retry import").performClick()
        assert(retried)
    }
}
