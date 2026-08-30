package com.recipearchive.app.ui.library

import androidx.test.core.app.ApplicationProvider
import com.recipearchive.app.data.import.ImportService
import com.recipearchive.app.data.local.RecipeDatabase
import com.recipearchive.app.data.repository.RecipeRepository
import com.recipearchive.app.testutil.MainDispatcherRule
import com.recipearchive.app.testutil.TestDatabaseFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class LibraryViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(testDispatcher)

    private lateinit var database: RecipeDatabase
    private lateinit var repository: RecipeRepository

    @Before
    fun setUp() {
        database = TestDatabaseFactory.create()
        repository = RecipeRepository(database, ImportService(database))
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `initial state is importing before the bundle loads`() = runTest(testDispatcher) {
        val viewModel = LibraryViewModel(
            repository,
            ApplicationProvider.getApplicationContext(),
            importAssetName = "test-recipe-bundle.json",
        )
        assertTrue(viewModel.uiState.value.isImporting)
    }

    @Test
    fun `successful import populates the recipe list`() = runTest(testDispatcher) {
        val viewModel = LibraryViewModel(
            repository,
            ApplicationProvider.getApplicationContext(),
            importAssetName = "test-recipe-bundle.json",
        )
        val states = mutableListOf<LibraryUiState>()
        val job = launch { viewModel.uiState.collect { states.add(it) } }
        advanceUntilIdle()

        val finalState = states.last()
        assertEquals(false, finalState.isImporting)
        assertTrue(finalState.hasImportedOnce)
        assertNull(finalState.importError)
        assertEquals(2, finalState.recipes.size)
        job.cancel()
    }

    @Test
    fun `import failure surfaces an error without crashing`() = runTest(testDispatcher) {
        val viewModel = LibraryViewModel(
            repository,
            ApplicationProvider.getApplicationContext(),
            importAssetName = "test-bad-schema-bundle.json",
        )
        val states = mutableListOf<LibraryUiState>()
        val job = launch { viewModel.uiState.collect { states.add(it) } }
        advanceUntilIdle()

        val finalState = states.last()
        assertEquals(false, finalState.isImporting)
        assertTrue(finalState.hasImportedOnce)
        assertTrue(finalState.importError!!.contains("schema version"))
        assertTrue(finalState.recipes.isEmpty())
        job.cancel()
    }

    @Test
    fun `search query change filters the results after debounce`() = runTest(testDispatcher) {
        val viewModel = LibraryViewModel(
            repository,
            ApplicationProvider.getApplicationContext(),
            importAssetName = "test-recipe-bundle.json",
        )
        val states = mutableListOf<LibraryUiState>()
        val job = launch { viewModel.uiState.collect { states.add(it) } }
        advanceUntilIdle()

        viewModel.updateQuery("chicken")
        advanceTimeBy(300)
        advanceUntilIdle()

        val finalState = states.last()
        assertEquals("chicken", finalState.query)
        assertEquals(listOf("Chicken Soup"), finalState.recipes.map { it.title })
        job.cancel()
    }

    @Test
    fun `search query with no matches yields an empty result list`() = runTest(testDispatcher) {
        val viewModel = LibraryViewModel(
            repository,
            ApplicationProvider.getApplicationContext(),
            importAssetName = "test-recipe-bundle.json",
        )
        val states = mutableListOf<LibraryUiState>()
        val job = launch { viewModel.uiState.collect { states.add(it) } }
        advanceUntilIdle()

        viewModel.updateQuery("nonexistentxyz")
        advanceTimeBy(300)
        advanceUntilIdle()

        assertTrue(states.last().recipes.isEmpty())
        job.cancel()
    }
}
