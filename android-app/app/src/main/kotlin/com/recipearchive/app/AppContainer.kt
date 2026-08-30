package com.recipearchive.app

import android.content.Context
import com.recipearchive.app.data.import.ImportService
import com.recipearchive.app.data.local.RecipeDatabase
import com.recipearchive.app.data.repository.RecipeRepository

/** Hand-rolled dependency container: one Room database, one repository, shared app-wide. */
class AppContainer(context: Context) {
    private val database: RecipeDatabase = RecipeDatabase.getInstance(context)
    private val importService: ImportService = ImportService(database)
    val recipeRepository: RecipeRepository = RecipeRepository(database, importService)
}
