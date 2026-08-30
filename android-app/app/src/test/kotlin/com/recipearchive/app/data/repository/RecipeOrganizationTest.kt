package com.recipearchive.app.data.repository

import com.recipearchive.app.data.import.ImportService
import com.recipearchive.app.data.local.RecipeDatabase
import com.recipearchive.app.data.organization.RecipeCategories
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
class RecipeOrganizationTest {
    private lateinit var database: RecipeDatabase
    private lateinit var importService: ImportService
    private lateinit var repository: RecipeRepository
    private lateinit var bundle: String

    @Before
    fun setUp() {
        database = TestDatabaseFactory.create()
        importService = ImportService(database)
        repository = RecipeRepository(database, importService)
        bundle = TestBundleFixtures.envelope(
            recipesJson = "${TestBundleFixtures.chickenSoup},${TestBundleFixtures.applePie}",
        )
        runTest { importService.import(bundle) }
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun `library summary includes personal rating and inferred category`() = runTest {
        repository.setPersonalRating("R0001", 4)
        val recipe = repository.observeLibrary("").first().first { it.id == "R0001" }
        assertEquals(4, recipe.personalRating)
        assertEquals(RecipeCategories.SOUP_SALAD, recipe.category)
    }

    @Test
    fun `category inference uses whole words rather than matching pie in pieces`() {
        assertEquals(
            RecipeCategories.ENTREE,
            RecipeCategories.infer("Miso Salmon", "four salmon fillets, 5-ounce pieces"),
        )
    }

    @Test
    fun `collection assignment filters the library`() = runTest {
        val collectionId = repository.createCollection("Sunday Supper")!!
        repository.setRecipeCollection("R0001", collectionId, true)
        val results = repository.observeLibrary("", collectionId = collectionId).first()
        assertEquals(listOf("R0001"), results.map { it.id })
    }

    @Test
    fun `search category and collection filters combine`() = runTest {
        val collectionId = repository.createCollection("Favorites for Guests")!!
        repository.setRecipeCollection("R0001", collectionId, true)
        repository.setRecipeCollection("R0002", collectionId, true)
        repository.setCategory("R0001", RecipeCategories.ENTREE)
        val results = repository.observeLibrary(
            query = "chicken",
            category = RecipeCategories.ENTREE,
            collectionId = collectionId,
        ).first()
        assertEquals(listOf("R0001"), results.map { it.id })
    }

    @Test
    fun `reimport preserves rating category and collection membership`() = runTest {
        val collectionId = repository.createCollection("Christmas Eve")!!
        repository.setPersonalRating("R0001", 5)
        repository.setCategory("R0001", RecipeCategories.APPETIZER)
        repository.setRecipeCollection("R0001", collectionId, true)
        importService.import(bundle)
        val recipe = repository.observeLibrary("", collectionId = collectionId).first().single()
        assertEquals(5, recipe.personalRating)
        assertEquals(RecipeCategories.APPETIZER, recipe.category)
        assertTrue(collectionId in recipe.collectionIds)
    }
}
