package com.recipearchive.app.ui.library

import com.recipearchive.app.data.repository.RecipeSummary
import org.junit.Assert.assertEquals
import org.junit.Test

class LibrarySortingTest {
    private val recipes = listOf(
        summary("R1", "Zucchini Bread", 4),
        summary("R2", "Apple Pie", null),
        summary("R3", "Chicken Soup", 5),
        summary("R4", "Banana Bread", 4),
    )

    @Test
    fun `alphabetical sort orders titles ignoring their input order`() {
        val sorted = sortLibraryRecipes(recipes, LibrarySort.ALPHABETICAL)
        assertEquals(listOf("Apple Pie", "Banana Bread", "Chicken Soup", "Zucchini Bread"), sorted.map { it.title })
    }

    @Test
    fun `rating sort orders high to low with alphabetical ties and unrated last`() {
        val sorted = sortLibraryRecipes(recipes, LibrarySort.RATING)
        assertEquals(listOf("Chicken Soup", "Banana Bread", "Zucchini Bread", "Apple Pie"), sorted.map { it.title })
    }

    private fun summary(id: String, title: String, rating: Int?) = RecipeSummary(
        id = id,
        title = title,
        sourcePublisher = "",
        hasReviewFlags = false,
        isFavorite = false,
        personalRating = rating,
    )
}
