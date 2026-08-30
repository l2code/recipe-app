package com.recipearchive.app.data.import

import com.recipearchive.app.data.local.RecipeDatabase
import com.recipearchive.app.data.local.entity.ImportStatus
import com.recipearchive.app.testutil.TestBundleFixtures
import com.recipearchive.app.testutil.TestDatabaseFactory
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
class ImportServiceTest {

    private lateinit var database: RecipeDatabase
    private lateinit var importService: ImportService

    @Before
    fun setUp() {
        database = TestDatabaseFactory.create()
        importService = ImportService(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `valid bundle imports all recipes`() = runTest {
        val bundle = TestBundleFixtures.envelope(
            recipesJson = "${TestBundleFixtures.chickenSoup},${TestBundleFixtures.applePie}",
        )

        val outcome = importService.import(bundle)

        assertTrue(outcome is ImportOutcome.Completed)
        outcome as ImportOutcome.Completed
        assertEquals(ImportStatus.SUCCESS, outcome.status)
        assertEquals(2, outcome.importedRecipeCount)
        assertEquals(2, outcome.insertedCount)
        assertEquals(0, outcome.updatedCount)
        assertEquals(2, database.recipeDao().count())
    }

    @Test
    fun `repeat import is idempotent`() = runTest {
        val bundle = TestBundleFixtures.envelope(
            recipesJson = "${TestBundleFixtures.chickenSoup},${TestBundleFixtures.applePie}",
        )

        importService.import(bundle)
        importService.import(bundle)

        assertEquals(2, database.recipeDao().count())
        assertEquals(2, database.ingredientDao().observeForRecipe("R0001").first().size)
    }

    @Test
    fun `updated recipe is upserted and preserves created timestamp`() = runTest {
        val firstBundle = TestBundleFixtures.envelope(recipesJson = TestBundleFixtures.chickenSoup)
        importService.import(firstBundle)
        val originalCreatedAt = database.recipeDao().getById("R0001")!!.createdAt

        val renamed = TestBundleFixtures.chickenSoup.replace("Chicken Soup", "Chicken Soup (Updated)")
        val secondBundle = TestBundleFixtures.envelope(recipesJson = renamed)
        val outcome = importService.import(secondBundle) as ImportOutcome.Completed

        assertEquals(1, outcome.insertedCount + outcome.updatedCount)
        assertEquals(0, outcome.insertedCount)
        assertEquals(1, outcome.updatedCount)
        val updated = database.recipeDao().getById("R0001")!!
        assertEquals("Chicken Soup (Updated)", updated.title)
        assertEquals(originalCreatedAt, updated.createdAt)
        assertTrue(updated.lastImportedAt >= originalCreatedAt)
    }

    @Test
    fun `app-owned favorite state survives reimport`() = runTest {
        val bundle = TestBundleFixtures.envelope(recipesJson = TestBundleFixtures.chickenSoup)
        importService.import(bundle)
        database.recipeAppStateDao().setFavorite("R0001", true, 123L)

        importService.import(bundle)

        val state = database.recipeAppStateDao().getForRecipe("R0001")
        assertNotNull(state)
        assertTrue(state!!.isFavorite)
    }

    @Test
    fun `child rows do not duplicate across reimports`() = runTest {
        val bundle = TestBundleFixtures.envelope(recipesJson = TestBundleFixtures.chickenSoup)
        importService.import(bundle)
        importService.import(bundle)
        importService.import(bundle)

        val ingredients = database.ingredientDao().observeForRecipe("R0001").first()
        val instructions = database.instructionDao().observeForRecipe("R0001").first()
        val pages = database.recipePageDao().observeForRecipe("R0001").first()
        assertEquals(2, ingredients.size)
        assertEquals(2, instructions.size)
        assertEquals(2, pages.size)
    }

    @Test
    fun `unsupported schema version is rejected without touching the database`() = runTest {
        val goodBundle = TestBundleFixtures.envelope(recipesJson = TestBundleFixtures.chickenSoup)
        importService.import(goodBundle)
        assertEquals(1, database.recipeDao().count())

        val futureBundle = TestBundleFixtures.envelope(schemaVersion = 99, recipesJson = TestBundleFixtures.applePie)
        val outcome = importService.import(futureBundle)

        assertTrue(outcome is ImportOutcome.Failed)
        assertEquals(1, database.recipeDao().count())
        assertNull(database.recipeDao().getById("R0002"))
    }

    @Test
    fun `malformed recipe is skipped and reported without failing the whole import`() = runTest {
        val bundle = TestBundleFixtures.envelope(
            recipesJson = "${TestBundleFixtures.malformedMissingId},${TestBundleFixtures.chickenSoup}",
        )

        val outcome = importService.import(bundle) as ImportOutcome.Completed

        assertEquals(ImportStatus.PARTIAL, outcome.status)
        assertEquals(1, outcome.importedRecipeCount)
        assertEquals(1, outcome.skipped.size)
        assertEquals(1, database.recipeDao().count())
        assertNotNull(database.recipeDao().getById("R0001"))
    }

    @Test
    fun `malformed bundle JSON leaves previous data intact`() = runTest {
        val goodBundle = TestBundleFixtures.envelope(recipesJson = TestBundleFixtures.chickenSoup)
        importService.import(goodBundle)

        val outcome = importService.import("{ this is not valid json ")

        assertTrue(outcome is ImportOutcome.Failed)
        assertEquals(1, database.recipeDao().count())
        assertEquals("Chicken Soup", database.recipeDao().getById("R0001")!!.title)
    }
}
