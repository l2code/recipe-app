package com.recipearchive.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.recipearchive.app.data.local.dao.CollectionDao
import com.recipearchive.app.data.local.dao.HandwrittenNoteDao
import com.recipearchive.app.data.local.dao.ImportRunDao
import com.recipearchive.app.data.local.dao.IngredientDao
import com.recipearchive.app.data.local.dao.InstructionDao
import com.recipearchive.app.data.local.dao.RecipeAppStateDao
import com.recipearchive.app.data.local.dao.RecipeDao
import com.recipearchive.app.data.local.dao.RecipePageDao
import com.recipearchive.app.data.local.dao.RecipeReviewFlagDao
import com.recipearchive.app.data.local.dao.RecipeSearchDao
import com.recipearchive.app.data.local.dao.SourceEvidenceDao
import com.recipearchive.app.data.local.entity.HandwrittenNoteEntity
import com.recipearchive.app.data.local.entity.CollectionEntity
import com.recipearchive.app.data.local.entity.ImportRunEntity
import com.recipearchive.app.data.local.entity.IngredientEntity
import com.recipearchive.app.data.local.entity.InstructionEntity
import com.recipearchive.app.data.local.entity.RecipeAppStateEntity
import com.recipearchive.app.data.local.entity.RecipeCollectionCrossRef
import com.recipearchive.app.data.local.entity.RecipeEntity
import com.recipearchive.app.data.local.entity.RecipePageEntity
import com.recipearchive.app.data.local.entity.RecipeReviewFlagEntity
import com.recipearchive.app.data.local.entity.RecipeSearchDocumentEntity
import com.recipearchive.app.data.local.entity.RecipeSearchFts
import com.recipearchive.app.data.local.entity.SourceEvidenceEntity

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
    ],
    version = 3,
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

        @Volatile private var instance: RecipeDatabase? = null

        fun getInstance(context: Context): RecipeDatabase =
            instance ?: synchronized(this) {
                instance ?: build(context).also { instance = it }
            }

        private fun build(context: Context): RecipeDatabase =
            Room.databaseBuilder(context.applicationContext, RecipeDatabase::class.java, DATABASE_NAME)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
    }
}
