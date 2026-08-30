package com.recipearchive.app.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Scaffold for schema migrations: the database is version 1 today, so there is
 * nothing to migrate yet, but this wires up [MigrationTestHelper] against the
 * exported schema JSON (see `room.schemaLocation` in app/build.gradle.kts and
 * app/schemas/). When a version 2 is introduced, add a real `Migration(1, 2)`
 * object to [RecipeDatabase] and a test here that creates v1, inserts rows,
 * runs the migration, and asserts the data + new columns look right.
 */
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
    fun `version 1 schema creates successfully from the exported schema file`() {
        helper.createDatabase(TEST_DB_NAME, 1).close()
    }

    companion object {
        private const val TEST_DB_NAME = "migration-test"
    }
}
