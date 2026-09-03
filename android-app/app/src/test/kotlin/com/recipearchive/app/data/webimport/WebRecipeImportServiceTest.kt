package com.recipearchive.app.data.webimport

import com.recipearchive.app.data.local.RecipeDatabase
import com.recipearchive.app.testutil.TestDatabaseFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WebRecipeImportServiceTest {

    private lateinit var database: RecipeDatabase
    private lateinit var server: MockWebServer
    private lateinit var service: WebRecipeImportService

    private val recipeHtml = """
        <html><head>
        <script type="application/ld+json">
        {
          "@type": "Recipe",
          "name": "Weeknight Tacos",
          "recipeIngredient": ["8 tortillas", "1 lb ground beef", "1 packet taco seasoning"],
          "recipeInstructions": ["Brown the beef.", "Stir in seasoning.", "Fill tortillas and serve."]
        }
        </script>
        </head><body></body></html>
    """.trimIndent()

    @Before
    fun setUp() {
        database = TestDatabaseFactory.create()
        server = MockWebServer()
        server.start()
        service = WebRecipeImportService(database, httpClient = OkHttpClient())
    }

    @After
    fun tearDown() {
        server.shutdown()
        database.close()
    }

    @Test
    fun `imports a recipe from a page's JSON-LD`() = runTest {
        server.enqueue(MockResponse().setBody(recipeHtml).setResponseCode(200))
        val url = server.url("/tacos").toString()

        val outcome = service.importFromUrl(url)

        assertTrue(outcome is WebImportOutcome.Success)
        outcome as WebImportOutcome.Success
        assertEquals("Weeknight Tacos", outcome.title)
        assertTrue(outcome.wasNew)

        val saved = database.recipeDao().getById(outcome.recipeId)
        assertNotNull(saved)
        assertEquals("Weeknight Tacos", saved!!.title)
        assertEquals(url, saved.sourceUrl)

        val ingredients = database.ingredientDao().getForRecipe(outcome.recipeId)
        assertEquals(3, ingredients.size)
        val appState = database.recipeAppStateDao().getForRecipe(outcome.recipeId)
        assertNotNull(appState)
    }

    @Test
    fun `reimporting the same URL updates the existing recipe instead of duplicating it`() = runTest {
        server.enqueue(MockResponse().setBody(recipeHtml).setResponseCode(200))
        server.enqueue(MockResponse().setBody(recipeHtml).setResponseCode(200))
        val url = server.url("/tacos").toString()

        val first = service.importFromUrl(url) as WebImportOutcome.Success
        val second = service.importFromUrl(url) as WebImportOutcome.Success

        assertEquals(first.recipeId, second.recipeId)
        assertTrue(second.wasNew.not())
        assertEquals(1, database.recipeDao().count())
    }

    @Test
    fun `returns NotFound when the page has no recipe data`() = runTest {
        server.enqueue(MockResponse().setBody("<html><body>no recipe here</body></html>").setResponseCode(200))
        val url = server.url("/nope").toString()

        val outcome = service.importFromUrl(url)

        assertTrue(outcome is WebImportOutcome.NotFound)
    }

    @Test
    fun `returns NetworkError on a failing HTTP response`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))
        val url = server.url("/broken").toString()

        val outcome = service.importFromUrl(url)

        assertTrue(outcome is WebImportOutcome.NetworkError)
    }

    @Test
    fun `returns ParseError for a blank url`() = runTest {
        val outcome = service.importFromUrl("   ")

        assertTrue(outcome is WebImportOutcome.ParseError)
    }

    @Test
    fun `successful url imports show up as a saved link`() = runTest {
        server.enqueue(MockResponse().setBody(recipeHtml).setResponseCode(200))
        val url = server.url("/tacos").toString()

        service.importFromUrl(url)

        val savedLinks = service.observeSavedLinks().first()
        assertEquals(1, savedLinks.size)
        assertEquals(url, savedLinks.first().url)
        assertEquals("Weeknight Tacos", savedLinks.first().title)
    }

    @Test
    fun `every import attempt is recorded in history, including failures`() = runTest {
        server.enqueue(MockResponse().setBody(recipeHtml).setResponseCode(200))
        server.enqueue(MockResponse().setResponseCode(500))
        service.importFromUrl(server.url("/tacos").toString())
        service.importFromUrl(server.url("/broken").toString())

        val history = service.observeHistory().first()
        assertEquals(2, history.size)
        assertTrue(history.any { it.status == com.recipearchive.app.data.local.entity.WebImportOutcomeStatus.SUCCESS })
        assertTrue(history.any { it.status == com.recipearchive.app.data.local.entity.WebImportOutcomeStatus.NETWORK_ERROR })
    }

    @Test
    fun `imports pasted text without fetching, and each save is a new recipe`() = runTest {
        val text = """
            Weeknight Chili
            Ingredients:
            1 lb ground beef
            1 can kidney beans
            Instructions:
            Brown the beef.
            Simmer for 20 minutes.
        """.trimIndent()

        val first = service.importPastedText(text) as WebImportOutcome.Success
        val second = service.importPastedText(text) as WebImportOutcome.Success

        assertEquals("Weeknight Chili", first.title)
        assertTrue(first.wasNew)
        assertTrue(second.wasNew)
        assertTrue(first.recipeId != second.recipeId)
        assertEquals(2, database.recipeDao().count())

        val ingredients = database.ingredientDao().getForRecipe(first.recipeId)
        assertEquals(2, ingredients.size)

        // Pasted-text imports have no URL, so they never show up as a "Saved Link".
        assertTrue(service.observeSavedLinks().first().isEmpty())
    }

    @Test
    fun `blank pasted text returns NotFound`() = runTest {
        val outcome = service.importPastedText("   \n  ")

        assertTrue(outcome is WebImportOutcome.NotFound)
    }
}
