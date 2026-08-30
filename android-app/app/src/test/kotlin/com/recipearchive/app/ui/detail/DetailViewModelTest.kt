package com.recipearchive.app.ui.detail

import com.recipearchive.app.data.import.ImportService
import com.recipearchive.app.data.local.RecipeDatabase
import com.recipearchive.app.data.repository.RecipeRepository
import com.recipearchive.app.testutil.MainDispatcherRule
import com.recipearchive.app.testutil.TestBundleFixtures
import com.recipearchive.app.testutil.TestDatabaseFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
class DetailViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(testDispatcher)

    private lateinit var database: RecipeDatabase
    private lateinit var repository: RecipeRepository

    @Before
    fun setUp() {
        database = TestDatabaseFactory.create()
        val importService = ImportService(database)
        repository = RecipeRepository(database, importService)
        val bundle = TestBundleFixtures.envelope(recipesJson = TestBundleFixtures.chickenSoup)
        runTest(testDispatcher) { importService.import(bundle) }
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `detail state is null until the recipe loads`() = runTest(testDispatcher) {
        val viewModel = DetailViewModel(repository, "R0001")
        assertNull(viewModel.uiState.value)
    }

    @Test
    fun `detail loads recipe with ingredients and instructions`() = runTest(testDispatcher) {
        val viewModel = DetailViewModel(repository, "R0001")
        val job = launch { viewModel.uiState.collect { } }
        advanceUntilIdle()

        val detail = viewModel.uiState.value
        assertEquals("Chicken Soup", detail?.recipe?.title)
        assertEquals(2, detail?.ingredients?.size)
        assertEquals(2, detail?.instructions?.size)
        assertTrue(detail?.handwrittenNotes?.isNotEmpty() == true)
        job.cancel()
    }

    @Test
    fun `toggling favorite updates app state`() = runTest(testDispatcher) {
        val viewModel = DetailViewModel(repository, "R0001")
        val job = launch { viewModel.uiState.collect { } }
        advanceUntilIdle()

        viewModel.toggleFavorite(currentlyFavorite = false)
        advanceUntilIdle()

        assertEquals(true, viewModel.uiState.value?.appState?.isFavorite)
        job.cancel()
    }
}
