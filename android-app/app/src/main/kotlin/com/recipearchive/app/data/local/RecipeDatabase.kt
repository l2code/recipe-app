package com.recipearchive.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.recipearchive.app.data.local.dao.CollectionDao
import com.recipearchive.app.data.local.dao.CookingSessionDao
import com.recipearchive.app.data.local.dao.HandwrittenNoteDao
import com.recipearchive.app.data.local.dao.ImportRunDao
import com.recipearchive.app.data.local.dao.IngredientDao
import com.recipearchive.app.data.local.dao.InstructionDao
import com.recipearchive.app.data.local.dao.MealPlanDao
import com.recipearchive.app.data.local.dao.PantryDao
import com.recipearchive.app.data.local.dao.RecipeAppStateDao
import com.recipearchive.app.data.local.dao.RecipeDao
import com.recipearchive.app.data.local.dao.RecipePageDao
import com.recipearchive.app.data.local.dao.RecipeReviewFlagDao
import com.recipearchive.app.data.local.dao.RecipeSearchDao
import com.recipearchive.app.data.local.dao.ShoppingDao
import com.recipearchive.app.data.local.dao.SourceEvidenceDao
import com.recipearchive.app.data.local.dao.WebImportHistoryDao
import com.recipearchive.app.data.local.entity.HandwrittenNoteEntity
import com.recipearchive.app.data.local.entity.CollectionEntity
import com.recipearchive.app.data.local.entity.CookingSessionEntity
import com.recipearchive.app.data.local.entity.ImportRunEntity
import com.recipearchive.app.data.local.entity.IngredientEntity
import com.recipearchive.app.data.local.entity.InstructionEntity
import com.recipearchive.app.data.local.entity.MealPlanEntryEntity
import com.recipearchive.app.data.local.entity.PantryItemEntity
import com.recipearchive.app.data.local.entity.RecipeAppStateEntity
import com.recipearchive.app.data.local.entity.RecipeCollectionCrossRef
import com.recipearchive.app.data.local.entity.RecipeEntity
import com.recipearchive.app.data.local.entity.RecipePageEntity
import com.recipearchive.app.data.local.entity.RecipeReviewFlagEntity
import com.recipearchive.app.data.local.entity.RecipeSearchDocumentEntity
import com.recipearchive.app.data.local.entity.RecipeSearchFts
import com.recipearchive.app.data.local.entity.SourceEvidenceEntity
import com.recipearchive.app.data.local.entity.ShoppingItemEntity
import com.recipearchive.app.data.local.entity.ShoppingItemSourceEntity
import com.recipearchive.app.data.local.entity.WebImportHistoryEntity

@Database(
    entities = [
        RecipeEntity::class,
        IngredientEntity::class,
        InstructionEntity::class,
        RecipePageEntity::class,
        HandwrittenNoteEntity::class,
        SourceEvidenceEntity::class,
        RecipeReviewFlagEntity::class,
        ImportRunEntity::class,
        RecipeAppStateEntity::class,
        RecipeSearchDocumentEntity::class,
        RecipeSearchFts::class,
        CollectionEntity::class,
        RecipeCollectionCrossRef::class,
        CookingSessionEntity::class,
        PantryItemEntity::class,
        ShoppingItemEntity::class,
        ShoppingItemSourceEntity::class,
        MealPlanEntryEntity::class,
        WebImportHistoryEntity::class,
    ],
    version = 7,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class RecipeDatabase : RoomDatabase() {
    abstract fun recipeDao(): RecipeDao
    abstract fun ingredientDao(): IngredientDao
    abstract fun instructionDao(): InstructionDao
    abstract fun recipePageDao(): RecipePageDao
    abstract fun handwrittenNoteDao(): HandwrittenNoteDao
    abstract fun sourceEvidenceDao(): SourceEvidenceDao
    abstract fun recipeReviewFlagDao(): RecipeReviewFlagDao
    abstract fun importRunDao(): ImportRunDao
    abstract fun recipeAppStateDao(): RecipeAppStateDao
    abstract fun recipeSearchDao(): RecipeSearchDao
    abstract fun collectionDao(): CollectionDao
    abstract fun cookingSessionDao(): CookingSessionDao
    abstract fun pantryDao(): PantryDao
    abstract fun shoppingDao(): ShoppingDao
    abstract fun mealPlanDao(): MealPlanDao
    abstract fun webImportHistoryDao(): WebImportHistoryDao

    companion object {
        private const val DATABASE_NAME = "recipe-archive.db"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE recipe_app_state ADD COLUMN category TEXT")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS collections (
                        id TEXT NOT NULL,
                        name TEXT NOT NULL,
                        sortOrder INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL,
                        PRIMARY KEY(id)
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_collections_name ON collections(name)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS recipe_collection_cross_ref (
                        recipeId TEXT NOT NULL,
                        collectionId TEXT NOT NULL,
                        PRIMARY KEY(recipeId, collectionId),
                        FOREIGN KEY(recipeId) REFERENCES recipes(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(collectionId) REFERENCES collections(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_recipe_collection_cross_ref_collectionId " +
                        "ON recipe_collection_cross_ref(collectionId)",
                )
                db.execSQL(
                    "INSERT OR IGNORE INTO collections(id, name, sortOrder, createdAt) " +
                        "VALUES('easter-sunday', 'Easter Sunday', 0, 0)",
                )
                db.execSQL(
                    "INSERT OR IGNORE INTO collections(id, name, sortOrder, createdAt) " +
                        "VALUES('christmas-dinner', 'Christmas Dinner', 1, 0)",
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE recipe_app_state ADD COLUMN categoryIsUserSet INTEGER NOT NULL DEFAULT 0",
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS cooking_sessions (
                        id TEXT NOT NULL,
                        recipeId TEXT NOT NULL,
                        startedAt INTEGER NOT NULL,
                        finishedAt INTEGER,
                        durationMillis INTEGER,
                        notes TEXT NOT NULL,
                        origin TEXT NOT NULL,
                        status TEXT NOT NULL,
                        rating INTEGER,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        PRIMARY KEY(id),
                        FOREIGN KEY(recipeId) REFERENCES recipes(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_cooking_sessions_recipeId ON cooking_sessions(recipeId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_cooking_sessions_status ON cooking_sessions(status)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS pantry_items (
                        ingredientKey TEXT NOT NULL,
                        displayName TEXT NOT NULL,
                        status TEXT NOT NULL,
                        isStaple INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        PRIMARY KEY(ingredientKey)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS shopping_items (
                        ingredientKey TEXT NOT NULL,
                        displayName TEXT NOT NULL,
                        isChecked INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        PRIMARY KEY(ingredientKey)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS shopping_item_sources (
                        ingredientKey TEXT NOT NULL,
                        recipeId TEXT NOT NULL,
                        quantity TEXT NOT NULL,
                        unit TEXT NOT NULL,
                        rawText TEXT NOT NULL,
                        PRIMARY KEY(ingredientKey, recipeId),
                        FOREIGN KEY(ingredientKey) REFERENCES shopping_items(ingredientKey) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(recipeId) REFERENCES recipes(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_shopping_item_sources_recipeId ON shopping_item_sources(recipeId)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS meal_plan_entries (
                        id TEXT NOT NULL,
                        recipeId TEXT NOT NULL,
                        plannedDate TEXT NOT NULL,
                        mealSlot TEXT NOT NULL,
                        servings INTEGER,
                        createdAt INTEGER NOT NULL,
                        PRIMARY KEY(id),
                        FOREIGN KEY(recipeId) REFERENCES recipes(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_meal_plan_entries_recipeId ON meal_plan_entries(recipeId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_meal_plan_entries_plannedDate ON meal_plan_entries(plannedDate)")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE cooking_sessions ADD COLUMN pausedAt INTEGER")
                db.execSQL("ALTER TABLE cooking_sessions ADD COLUMN totalPausedMillis INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE recipe_app_state ADD COLUMN importedNotesReviewStatus TEXT NOT NULL DEFAULT 'pending'",
                )
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS web_import_history (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        url TEXT NOT NULL,
                        title TEXT NOT NULL,
                        domain TEXT NOT NULL,
                        status TEXT NOT NULL,
                        errorMessage TEXT,
                        recipeId TEXT,
                        importedAt INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
            }
        }

        @Volatile private var instance: RecipeDatabase? = null

        fun getInstance(context: Context): RecipeDatabase =
            instance ?: synchronized(this) {
                instance ?: build(context).also { instance = it }
            }

        private fun build(context: Context): RecipeDatabase =
            Room.databaseBuilder(context.applicationContext, RecipeDatabase::class.java, DATABASE_NAME)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                .build()
    }
}
