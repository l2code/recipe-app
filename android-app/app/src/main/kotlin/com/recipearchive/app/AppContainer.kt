package com.recipearchive.app

import android.content.Context
import com.recipearchive.app.data.import.ImportService
import com.recipearchive.app.data.companion.CookingCompanionRepository
import com.recipearchive.app.data.local.RecipeDatabase
import com.recipearchive.app.data.repository.RecipeRepository
import com.recipearchive.app.data.webimport.CredentialStore
import com.recipearchive.app.data.webimport.WebRecipeImportService

/** Hand-rolled dependency container: one Room database, one repository, shared app-wide. */
class AppContainer(context: Context) {
    private val database: RecipeDatabase = RecipeDatabase.getInstance(context)
    private val importService: ImportService = ImportService(database)
    val recipeRepository: RecipeRepository = RecipeRepository(database, importService)
    val cookingCompanionRepository: CookingCompanionRepository = CookingCompanionRepository(database)
    val webRecipeImportService: WebRecipeImportService = WebRecipeImportService(database)

    // Lazy: touches the Android Keystore, which isn't available until the Import screen is
    // actually opened (and isn't available at all under Robolectric's JVM test environment,
    // where every test otherwise instantiates the real Application/AppContainer on startup).
    val credentialStore: CredentialStore by lazy { CredentialStore.create(context) }
}
