package com.recipearchive.app.data.repository

import com.recipearchive.app.data.import.ImportService
import com.recipearchive.app.data.local.RecipeDatabase
import com.recipearchive.app.testutil.TestBundleFixtures
import com.recipearchive.app.testutil.TestDatabaseFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RecipeDeletionTest {
    private lateinit var database: RecipeDatabase
    private lateinit var repository: RecipeRepository

    @Before
    fun setUp() {
        database = TestDatabaseFactory.create()
        repository = RecipeRepository(database, ImportService(database))
        val bundle = TestBundleFixtures.envelope(
            recipesJson = "${TestBundleFixtures.chickenSoup},${TestBundleFixtures.applePie}",
        )
        runTest { ImportService(database).import(bundle) }
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun `deleting a recipe removes it and its child rows`() = runTest {
        repository.deleteRecipe("R0001")

        assertNull(database.recipeDao().getById("R0001"))
        assertTrue(database.ingredientDao().getForRecipe("R0001").isEmpty())
        assertEquals(1, database.recipeDao().count())
    }

    @Test
    fun `deleting a recipe removes it from search results`() = runTest {
        val beforeDelete = repository.observeLibrary("chicken").first()
        assertTrue(beforeDelete.any { it.id == "R0001" })

        repository.deleteRecipe("R0001")

        val afterDelete = repository.observeLibrary("chicken").first()
        assertTrue(afterDelete.none { it.id == "R0001" })
    }

    @Test
    fun `deleting a recipe removes it from the library list`() = runTest {
        repository.deleteRecipe("R0001")

        val library = repository.observeLibrary("").first()
        assertTrue(library.none { it.id == "R0001" })
        assertEquals(1, library.size)
    }
}
