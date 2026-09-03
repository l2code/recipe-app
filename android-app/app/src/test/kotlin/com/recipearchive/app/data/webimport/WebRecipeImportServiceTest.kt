package com.recipearchive.app.data.webimport

import com.recipearchive.app.data.local.RecipeDatabase
import com.recipearchive.app.testutil.TestDatabaseFactory
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
}
