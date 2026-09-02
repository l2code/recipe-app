package com.recipearchive.app.data.companion

import com.recipearchive.app.data.import.ImportService
import com.recipearchive.app.data.local.RecipeDatabase
import com.recipearchive.app.testutil.TestBundleFixtures
import com.recipearchive.app.testutil.TestDatabaseFactory
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CookingCompanionRepositoryTest {
    private lateinit var database: RecipeDatabase
    private lateinit var repository: CookingCompanionRepository

    @Before
    fun setUp() {
        database = TestDatabaseFactory.create()
        repository = CookingCompanionRepository(database)
        runTest {
            ImportService(database).import(TestBundleFixtures.envelope(recipesJson = TestBundleFixtures.chickenSoup))
        }
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun `only confirmed cooking sessions count as made`() = runTest {
        val sessionId = repository.startCooking("R0001")
        assertEquals(0, repository.observeRecipe("R0001").first().madeCount)

        repository.confirmSession(sessionId, "Needed more salt", 4)

        val state = repository.observeRecipe("R0001").first()
        assertEquals(1, state.madeCount)
        assertEquals("Needed more salt", state.sessions.single().notes)
        assertEquals(4, state.sessions.single().rating)
        assertNotNull(state.sessions.single().finishedAt)
    }

    @Test
    fun `discarded session never increments made count`() = runTest {
        val sessionId = repository.startCooking("R0001")
        repository.discardSession(sessionId)
        assertEquals(0, repository.observeRecipe("R0001").first().madeCount)
    }

    @Test
    fun `cooking timer pause state persists and resumes`() = runTest {
        val sessionId = repository.startCooking("R0001")

        repository.pauseSession(sessionId)
        assertNotNull(database.cookingSessionDao().getById(sessionId)?.pausedAt)

        repository.resumeSession(sessionId)
        val resumed = database.cookingSessionDao().getById(sessionId)
        assertNull(resumed?.pausedAt)
        assertTrue((resumed?.totalPausedMillis ?: -1) >= 0)
    }

    @Test
    fun `global cooking history includes confirmed sessions only`() = runTest {
        val confirmed = repository.startCooking("R0001")
        repository.confirmSession(confirmed, "Good soup", 5)
        repository.startCooking("R0001")

        val history = repository.observeCookingHistory().first()
        assertEquals(1, history.size)
        assertEquals("Chicken Soup", history.single().recipeTitle)
    }

    @Test
    fun `pantry availability controls generated shopping items`() = runTest {
        val initial = repository.observeRecipe("R0001").first()
        val availableIngredient = initial.ingredients.first()
        repository.setPantryStatus(
            availableIngredient.ingredientKey,
            availableIngredient.displayName,
            PantryStatus.HAVE,
        )

        repository.addUnavailableToShopping("R0001")

        val shopping = repository.observeShoppingList().first()
        assertEquals(initial.ingredients.size - 1, shopping.size)
        assertTrue(shopping.none { it.item.ingredientKey == availableIngredient.ingredientKey })
    }

    @Test
    fun `checking a shopping item marks it available in pantry`() = runTest {
        repository.addUnavailableToShopping("R0001")
        val item = repository.observeShoppingList().first().first()
        repository.setShoppingChecked(item.item.ingredientKey, item.item.displayName, true)

        val recipe = repository.observeRecipe("R0001").first()
        assertTrue(recipe.ingredients.first { it.ingredientKey == item.item.ingredientKey }.status == PantryStatus.HAVE)
    }

    @Test
    fun `meal plan readiness reflects pantry state`() = runTest {
        repository.addMealPlan("R0001", LocalDate.of(2026, 8, 31))
        val before = repository.observeMealPlan().first().single()
        assertEquals(0, before.availableCount)

        repository.observeRecipe("R0001").first().ingredients.forEach { ingredient ->
            repository.setPantryStatus(ingredient.ingredientKey, ingredient.displayName, PantryStatus.HAVE)
        }

        val after = repository.observeMealPlan().first().single()
        assertEquals(after.totalCount, after.availableCount)
    }
}
