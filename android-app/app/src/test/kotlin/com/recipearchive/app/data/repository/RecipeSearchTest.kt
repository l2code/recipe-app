package com.recipearchive.app.data.repository

import com.recipearchive.app.data.import.ImportService
import com.recipearchive.app.data.local.RecipeDatabase
import com.recipearchive.app.testutil.TestBundleFixtures
import com.recipearchive.app.testutil.TestDatabaseFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RecipeSearchTest {

    private lateinit var database: RecipeDatabase
    private lateinit var repository: RecipeRepository

    @Before
    fun setUp() {
        database = TestDatabaseFactory.create()
        val importService = ImportService(database)
        repository = RecipeRepository(database, importService)
        val bundle = TestBundleFixtures.envelope(
            recipesJson = "${TestBundleFixtures.chickenSoup},${TestBundleFixtures.applePie}",
        )
        runTest { importService.import(bundle) }
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `search matches by title`() = runTest {
        assertEquals(listOf("R0001"), repository.searchRecipeIds("Chicken"))
    }

    @Test
    fun `search matches by ingredient text`() = runTest {
        assertEquals(listOf("R0001"), repository.searchRecipeIds("carrots"))
    }

    @Test
    fun `search matches by instruction text`() = runTest {
        assertEquals(listOf("R0001"), repository.searchRecipeIds("simmer"))
    }

    @Test
    fun `search matches by handwritten transcription`() = runTest {
        assertEquals(listOf("R0001"), repository.searchRecipeIds("love"))
    }

    @Test
    fun `search matches by publisher and source fields`() = runTest {
        assertEquals(listOf("R0002"), repository.searchRecipeIds("Food Network"))
    }

    @Test
    fun `search handles apostrophes without crashing and matches both recipes`() = runTest {
        val results = repository.searchRecipeIds("grandma's")
        assertEquals(setOf("R0001", "R0002"), results.toSet())
    }

    @Test
    fun `search handles stray punctuation without crashing`() = runTest {
        val results = repository.searchRecipeIds("chicken!! (soup)")
        assertTrue(results.contains("R0001"))
    }

    @Test
    fun `title matches are ranked before other field matches`() = runTest {
        // "chicken" appears in R0001's title AND its rawText/ingredients; only one recipe here,
        // so assert title-query path also finds it via the title-restricted match.
        val titleOnly = database.recipeSearchDao().searchTitleMatches(
            SearchQuerySanitizer.sanitize("chicken")!!,
        )
        assertEquals(listOf("R0001"), titleOnly)
    }

    @Test
    fun `empty query returns the full library via observeLibrary`() = runTest {
        val results = repository.observeLibrary("").first()
        assertEquals(setOf("R0001", "R0002"), results.map { it.id }.toSet())
    }

    @Test
    fun `no-results query returns an empty list`() = runTest {
        val results = repository.searchRecipeIds("nonexistentingredientxyz")
        assertTrue(results.isEmpty())
    }

    @Test
    fun `punctuation-only query yields no results rather than the full library`() = runTest {
        val results = repository.observeLibrary("!!!").first()
        assertTrue(results.isEmpty())
    }
}
