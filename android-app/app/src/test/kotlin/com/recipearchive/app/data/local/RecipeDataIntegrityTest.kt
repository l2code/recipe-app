package com.recipearchive.app.data.local

import com.recipearchive.app.data.import.ImportService
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
class RecipeDataIntegrityTest {

    private lateinit var database: RecipeDatabase

    @Before
    fun setUp() {
        database = TestDatabaseFactory.create()
        val importService = ImportService(database)
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
    fun `ingredient order follows source array order`() = runTest {
        val ingredients = database.ingredientDao().observeForRecipe("R0001").first()
        assertEquals(listOf("chicken", "carrots"), ingredients.sortedBy { it.displayOrder }.map { it.item })
    }

    @Test
    fun `instruction order follows the explicit order field, not array position`() = runTest {
        val instructions = database.instructionDao().observeForRecipe("R0001").first()
        val ordered = instructions.sortedBy { it.displayOrder }
        assertEquals(listOf("Boil water", "Simmer for an hour"), ordered.map { it.text })
        assertEquals(listOf(1, 2), ordered.map { it.displayOrder })
    }

    @Test
    fun `page references preserve given order and are parsed`() = runTest {
        val pages = database.recipePageDao().observeForRecipe("R0001").first().sortedBy { it.displayOrder }
        assertEquals(listOf("Batch_01__scan.pdf#p002", "Batch_01__scan.pdf#p001"), pages.map { it.pageRef })
        assertEquals(listOf(2, 1), pages.map { it.pageNumber })
        assertTrue(pages.all { it.scanFilename == "Batch_01__scan.pdf" })
    }

    @Test
    fun `handwritten notes link to the correct recipe only`() = runTest {
        val chickenNotes = database.handwrittenNoteDao().observeForRecipe("R0001").first()
        val pieNotes = database.handwrittenNoteDao().observeForRecipe("R0002").first()
        assertEquals(1, chickenNotes.size)
        assertEquals("R0001", chickenNotes.single().recipeId)
        assertEquals("Add lots of love", chickenNotes.single().transcription)
        assertTrue(pieNotes.isEmpty())
    }

    @Test
    fun `recipe relations expose review flags normalized from combined entries`() = runTest {
        val flags = database.recipeReviewFlagDao().observeForRecipe("R0001").first().map { it.flagValue }
        assertEquals(setOf("handwriting_review", "duplicate_review"), flags.toSet())
    }
}
