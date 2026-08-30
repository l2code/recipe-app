package com.recipearchive.app.ui.nav

import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.recipearchive.app.AppContainer
import com.recipearchive.app.ui.detail.DetailScreen
import com.recipearchive.app.ui.detail.DetailViewModel
import com.recipearchive.app.ui.library.LibraryScreen
import com.recipearchive.app.ui.library.LibraryViewModel

private const val ROUTE_LIBRARY = "library"
private const val ROUTE_DETAIL = "detail/{recipeId}"
private const val ARG_RECIPE_ID = "recipeId"

@Composable
fun RecipeNavHost(container: AppContainer, widthSizeClass: WindowWidthSizeClass) {
    val navController = rememberNavController()
    val appContext = LocalContext.current.applicationContext

    val libraryViewModel: LibraryViewModel = viewModel(
        factory = LibraryViewModel.Factory(container.recipeRepository, appContext),
    )

    NavHost(navController = navController, startDestination = ROUTE_LIBRARY) {
        composable(ROUTE_LIBRARY) {
            LibraryScreen(
                viewModel = libraryViewModel,
                widthSizeClass = widthSizeClass,
                onRecipeClick = { recipeId -> navController.navigate("detail/$recipeId") },
            )
        }
        composable(
            route = ROUTE_DETAIL,
            arguments = listOf(navArgument(ARG_RECIPE_ID) { type = NavType.StringType }),
        ) { backStackEntry ->
            val recipeId = backStackEntry.arguments?.getString(ARG_RECIPE_ID)
            if (recipeId != null) {
                val detailViewModel: DetailViewModel = viewModel(
                    key = recipeId,
                    factory = DetailViewModel.Factory(container.recipeRepository, recipeId),
                )
                DetailScreen(viewModel = detailViewModel, onBack = { navController.popBackStack() })
            }
        }
    }
}
