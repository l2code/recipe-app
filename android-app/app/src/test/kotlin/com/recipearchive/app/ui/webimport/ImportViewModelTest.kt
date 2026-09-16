package com.recipearchive.app.ui.webimport

import com.recipearchive.app.data.local.RecipeDatabase
import com.recipearchive.app.data.webimport.WebRecipeImportService
import com.recipearchive.app.testutil.MainDispatcherRule
import com.recipearchive.app.testutil.TestDatabaseFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ImportViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(testDispatcher)

    private lateinit var database: RecipeDatabase
    private lateinit var server: MockWebServer
    private lateinit var service: WebRecipeImportService

    private val recipeHtml = """
        <html><head>
        <script type="application/ld+json">
        {
          "@type": "Recipe",
          "name": "Weeknight Tacos",
          "recipeIngredient": ["8 tortillas", "1 lb ground beef"],
          "recipeInstructions": ["Brown the beef.", "Fill tortillas and serve."]
        }
        </script>
        </head><body></body></html>
    """.trimIndent()

    @Before
    fun setUp() {
        database = TestDatabaseFactory.create()
        server = MockWebServer()
        server.start()
        service = WebRecipeImportService(database, httpClient = OkHttpClient(), ioDispatcher = testDispatcher)
    }

    @After
    fun tearDown() {
        server.shutdown()
        database.close()
    }

    @Test
    fun `importing a valid url clears the field and emits an Imported event`() = runTest(testDispatcher) {
        server.enqueue(MockResponse().setBody(recipeHtml).setResponseCode(200))
        val url = server.url("/tacos").toString()
        val viewModel = ImportViewModel(service)

        val events = mutableListOf<ImportEvent>()
        val job = launch { viewModel.events.collect { events.add(it) } }

        viewModel.onUrlChanged(url)
        viewModel.importRecipe()
        advanceUntilIdle()

        assertEquals(1, events.size)
        assertTrue(events.first() is ImportEvent.Imported)
        assertEquals("", viewModel.uiState.value.url)
        assertNull(viewModel.uiState.value.errorMessage)
        job.cancel()
    }

    @Test
    fun `importing a blank url surfaces an error instead of calling the service`() = runTest(testDispatcher) {
        val viewModel = ImportViewModel(service)

        viewModel.importRecipe()
        advanceUntilIdle()

        assertEquals("Paste a recipe URL first", viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `review before import populates an editable preview without saving`() = runTest(testDispatcher) {
        server.enqueue(MockResponse().setBody(recipeHtml).setResponseCode(200))
        val url = server.url("/tacos").toString()
        val viewModel = ImportViewModel(service)

        viewModel.onUrlChanged(url)
        viewModel.reviewBeforeImport()
        advanceUntilIdle()

        val preview = viewModel.previewState.value
        assertNotNull(preview)
        assertEquals("Weeknight Tacos", preview!!.title)
        assertEquals(0, database.recipeDao().count())
    }

    @Test
    fun `confirming the preview saves the edited recipe and clears the preview`() = runTest(testDispatcher) {
        server.enqueue(MockResponse().setBody(recipeHtml).setResponseCode(200))
        val url = server.url("/tacos").toString()
        val viewModel = ImportViewModel(service)
        viewModel.onUrlChanged(url)
        viewModel.reviewBeforeImport()
        advanceUntilIdle()

        viewModel.onPreviewTitleChanged("Weeknight Tacos (edited)")
        val events = mutableListOf<ImportEvent>()
        val job = launch { viewModel.events.collect { events.add(it) } }
        viewModel.confirmPreviewImport()
        advanceUntilIdle()

        assertNull(viewModel.previewState.value)
        assertEquals(1, database.recipeDao().count())
        assertEquals(1, events.size)
        job.cancel()
    }

    @Test
    fun `pasting text imports without a network call`() = runTest(testDispatcher) {
        val viewModel = ImportViewModel(service)
        val events = mutableListOf<ImportEvent>()
        val job = launch { viewModel.events.collect { events.add(it) } }

        viewModel.onQuickSourceSelected(QuickSource.PASTE_TEXT)
        assertTrue(viewModel.uiState.value.pasteTextDialogOpen)

        viewModel.onPastedTextChanged("Skillet Corn\nIngredients:\n1 cup corn\nInstructions:\nHeat it up.")
        viewModel.importPastedText()
        advanceUntilIdle()

        assertEquals(1, events.size)
        assertEquals(1, database.recipeDao().count())
        job.cancel()
    }
}
