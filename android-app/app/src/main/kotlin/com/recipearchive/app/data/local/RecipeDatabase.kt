package com.recipearchive.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
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
import com.recipearchive.app.data.local.entity.ImportRunEntity
import com.recipearchive.app.data.local.entity.IngredientEntity
import com.recipearchive.app.data.local.entity.InstructionEntity
import com.recipearchive.app.data.local.entity.RecipeAppStateEntity
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
    ],
    version = 1,
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

    companion object {
        private const val DATABASE_NAME = "recipe-archive.db"

        @Volatile private var instance: RecipeDatabase? = null

        fun getInstance(context: Context): RecipeDatabase =
            instance ?: synchronized(this) {
                instance ?: build(context).also { instance = it }
            }

        private fun build(context: Context): RecipeDatabase =
            Room.databaseBuilder(context.applicationContext, RecipeDatabase::class.java, DATABASE_NAME)
                .build()
    }
}
