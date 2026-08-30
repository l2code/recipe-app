package com.recipearchive.app.data.import

import androidx.test.core.app.ApplicationProvider
import com.recipearchive.app.data.local.RecipeDatabase
import com.recipearchive.app.testutil.TestDatabaseFactory
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Integration check against the real Phase 2 archive output (copied into assets by the
 * `copyImportBundle` Gradle task from /media/nas/RecipeScans/.processed/archive), not a
 * hand-written fixture. Confirms the acceptance criterion that Room holds exactly 418
 * canonical recipes after import, and stays at 418 after a repeat import.
 */
@RunWith(RobolectricTestRunner::class)
class FullBundleImportIntegrationTest {

    private lateinit var database: RecipeDatabase

    @Before
    fun setUp() {
        database = TestDatabaseFactory.create()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `importing the real bundle yields exactly 418 canonical recipes`() = runTest {
        val importService = ImportService(database)
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()

        val first = importService.importFromAssets(context, ImportService.DEFAULT_ASSET_NAME)
        assertTrue(first is ImportOutcome.Completed)
        assertEquals(418, database.recipeDao().count())

        val second = importService.importFromAssets(context, ImportService.DEFAULT_ASSET_NAME)
        assertTrue(second is ImportOutcome.Completed)
        assertEquals(418, database.recipeDao().count())
        assertEquals(0, (second as ImportOutcome.Completed).insertedCount)
        assertEquals(418, second.updatedCount)
    }
}
