package com.recipearchive.app.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RecipeDatabaseMigrationTest {

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        RecipeDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun `migration from version 1 preserves app state and seeds collections`() {
        helper.createDatabase(TEST_DB_NAME, 1).apply {
            execSQL(
                """
                INSERT INTO recipes VALUES(
                    'R1', 'Test recipe', '', 0, '', '', '', '', '', '', 1, '', 1, 1
                )
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO recipe_app_state VALUES(
                    'R1', 1, 4, 'Keep this note', 1, 123
                )
                """.trimIndent(),
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            TEST_DB_NAME,
            7,
            true,
            RecipeDatabase.MIGRATION_1_2,
            RecipeDatabase.MIGRATION_2_3,
            RecipeDatabase.MIGRATION_3_4,
            RecipeDatabase.MIGRATION_4_5,
            RecipeDatabase.MIGRATION_5_6,
            RecipeDatabase.MIGRATION_6_7,
        )

        migrated.query(
            "SELECT isFavorite, personalRating, personalNotes, reviewCompleted, category, categoryIsUserSet FROM recipe_app_state WHERE recipeId = 'R1'",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1, cursor.getInt(0))
            assertEquals(4, cursor.getInt(1))
            assertEquals("Keep this note", cursor.getString(2))
            assertEquals(1, cursor.getInt(3))
            assertTrue(cursor.isNull(4))
            assertEquals(0, cursor.getInt(5))
        }
        migrated.query("SELECT name FROM collections ORDER BY sortOrder").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Easter Sunday", cursor.getString(0))
            assertTrue(cursor.moveToNext())
            assertEquals("Christmas Dinner", cursor.getString(0))
        }
        migrated.query("SELECT COUNT(*) FROM cooking_sessions").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
        migrated.query("PRAGMA table_info(cooking_sessions)").use { cursor ->
            val names = buildSet {
                while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
            }
            assertTrue("pausedAt" in names)
            assertTrue("totalPausedMillis" in names)
        }
        migrated.query("SELECT COUNT(*) FROM pantry_items").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
        migrated.query("SELECT importedNotesReviewStatus FROM recipe_app_state WHERE recipeId = 'R1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("pending", cursor.getString(0))
        }
        migrated.query("SELECT COUNT(*) FROM web_import_history").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
        migrated.query("PRAGMA table_info(web_import_history)").use { cursor ->
            val names = buildSet {
                while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
            }
            assertTrue("url" in names)
            assertTrue("status" in names)
            assertTrue("recipeId" in names)
        }
        migrated.close()
    }

    companion object {
        private const val TEST_DB_NAME = "migration-test"
    }
}
