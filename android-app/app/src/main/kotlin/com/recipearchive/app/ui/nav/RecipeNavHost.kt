package com.recipearchive.app.ui.nav

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Kitchen
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.recipearchive.app.AppContainer
import com.recipearchive.app.ui.companion.CompanionViewModel
import com.recipearchive.app.ui.companion.MealPlanScreen
import com.recipearchive.app.ui.companion.HistoryScreen
import com.recipearchive.app.ui.companion.PantryScreen
import com.recipearchive.app.ui.companion.ShoppingScreen
import com.recipearchive.app.ui.cooking.CookingScreen
import com.recipearchive.app.ui.cooking.CookingViewModel
import com.recipearchive.app.ui.detail.DetailScreen
import com.recipearchive.app.ui.detail.DetailViewModel
import com.recipearchive.app.ui.library.LibraryScreen
import com.recipearchive.app.ui.library.LibraryViewModel
import com.recipearchive.app.ui.webimport.ImportHistoryScreen
import com.recipearchive.app.ui.webimport.ImportScreen
import com.recipearchive.app.ui.webimport.ImportViewModel

private const val ROUTE_LIBRARY = "library"
private const val ROUTE_PLAN = "plan"
private const val ROUTE_SHOPPING = "shopping"
private const val ROUTE_PANTRY = "pantry"
private const val ROUTE_HISTORY = "history"
private const val ROUTE_IMPORT = "import"
private const val ROUTE_IMPORT_HISTORY = "import_history"
private const val ROUTE_DETAIL = "detail/{recipeId}"
private const val ROUTE_COOKING = "cooking/{recipeId}/{sessionId}"
private const val ARG_RECIPE_ID = "recipeId"
private const val ARG_SESSION_ID = "sessionId"

private data class MainDestination(val route: String, val label: String, val icon: ImageVector)

private val mainDestinations = listOf(
    MainDestination(ROUTE_LIBRARY, "Recipes", Icons.AutoMirrored.Filled.MenuBook),
    MainDestination(ROUTE_PLAN, "Plan", Icons.Filled.CalendarMonth),
    MainDestination(ROUTE_SHOPPING, "Shopping", Icons.Filled.ShoppingCart),
    MainDestination(ROUTE_PANTRY, "Pantry", Icons.Filled.Kitchen),
    MainDestination(ROUTE_HISTORY, "History", Icons.Filled.History),
    MainDestination(ROUTE_IMPORT, "Import", Icons.Filled.Download),
)

@Composable
fun RecipeNavHost(container: AppContainer, widthSizeClass: WindowWidthSizeClass) {
    val navController = rememberNavController()
    val appContext = LocalContext.current.applicationContext
    val expanded = widthSizeClass == WindowWidthSizeClass.Expanded

    val libraryViewModel: LibraryViewModel = viewModel(
        factory = LibraryViewModel.Factory(container.recipeRepository, appContext),
    )
    val companionViewModel: CompanionViewModel = viewModel(
        factory = CompanionViewModel.Factory(container.cookingCompanionRepository),
    )
    val importViewModel: ImportViewModel = viewModel(
        factory = ImportViewModel.Factory(container.webRecipeImportService, container.credentialStore),
    )

    fun navigateMain(route: String) {
        navController.navigate(route) {
            popUpTo(ROUTE_LIBRARY) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    NavHost(
        navController = navController,
        startDestination = ROUTE_LIBRARY,
        // The default cross-fade left the outgoing screen's text visible, fading through
        // underneath the incoming one -- distracting on a text-heavy app like this. An instant
        // swap reads cleaner than a fixed-up fade here.
        enterTransition = { EnterTransition.None },
        exitTransition = { ExitTransition.None },
        popEnterTransition = { EnterTransition.None },
        popExitTransition = { ExitTransition.None },
    ) {
        composable(ROUTE_LIBRARY) {
            MainScaffold(ROUTE_LIBRARY, expanded, ::navigateMain) {
                LibraryScreen(
                    viewModel = libraryViewModel,
                    widthSizeClass = widthSizeClass,
                    onRecipeClick = { recipeId -> navController.navigate("detail/$recipeId") },
                )
            }
        }
        composable(ROUTE_PLAN) {
            MainScaffold(ROUTE_PLAN, expanded, ::navigateMain) {
                MealPlanScreen(
                    viewModel = companionViewModel,
                    onRecipeClick = { recipeId -> navController.navigate("detail/$recipeId") },
                )
            }
        }
        composable(ROUTE_SHOPPING) {
            MainScaffold(ROUTE_SHOPPING, expanded, ::navigateMain) { ShoppingScreen(companionViewModel) }
        }
        composable(ROUTE_PANTRY) {
            MainScaffold(ROUTE_PANTRY, expanded, ::navigateMain) { PantryScreen(companionViewModel) }
        }
        composable(ROUTE_HISTORY) {
            MainScaffold(ROUTE_HISTORY, expanded, ::navigateMain) {
                HistoryScreen(
                    viewModel = companionViewModel,
                    onRecipeClick = { recipeId -> navController.navigate("detail/$recipeId") },
                )
            }
        }
        composable(ROUTE_IMPORT) {
            MainScaffold(ROUTE_IMPORT, expanded, ::navigateMain) {
                ImportScreen(
                    viewModel = importViewModel,
                    onImported = { recipeId -> navController.navigate("detail/$recipeId") },
                    onOpenHistory = { navController.navigate(ROUTE_IMPORT_HISTORY) },
                )
            }
        }
        composable(ROUTE_IMPORT_HISTORY) {
            ImportHistoryScreen(
                viewModel = importViewModel,
                onBack = { navController.popBackStack() },
                onRecipeClick = { recipeId -> navController.navigate("detail/$recipeId") },
            )
        }
        composable(
            route = ROUTE_DETAIL,
            arguments = listOf(navArgument(ARG_RECIPE_ID) { type = NavType.StringType }),
        ) { detailEntry ->
            val recipeId = detailEntry.arguments?.getString(ARG_RECIPE_ID)
            if (recipeId != null) {
                val detailViewModel: DetailViewModel = viewModel(
                    key = recipeId,
                    factory = DetailViewModel.Factory(
                        container.recipeRepository,
                        container.cookingCompanionRepository,
                        recipeId,
                    ),
                )
                DetailScreen(
                    viewModel = detailViewModel,
                    widthSizeClass = widthSizeClass,
                    onBack = { navController.popBackStack() },
                    onCookingStarted = { sessionId ->
                        navController.navigate("cooking/$recipeId/$sessionId")
                    },
                )
            }
        }
        composable(
            route = ROUTE_COOKING,
            arguments = listOf(
                navArgument(ARG_RECIPE_ID) { type = NavType.StringType },
                navArgument(ARG_SESSION_ID) { type = NavType.StringType },
            ),
        ) { cookingEntry ->
            val recipeId = cookingEntry.arguments?.getString(ARG_RECIPE_ID)
            val sessionId = cookingEntry.arguments?.getString(ARG_SESSION_ID)
            if (recipeId != null && sessionId != null) {
                val cookingViewModel: CookingViewModel = viewModel(
                    key = sessionId,
                    factory = CookingViewModel.Factory(
                        container.recipeRepository,
                        container.cookingCompanionRepository,
                        recipeId,
                        sessionId,
                    ),
                )
                CookingScreen(
                    viewModel = cookingViewModel,
                    widthSizeClass = widthSizeClass,
                    onBack = { navController.popBackStack() },
                    onSessionComplete = { navController.popBackStack() },
                )
            }
        }
    }
}

/**
 * Wraps a main destination's content with the bottom nav bar (compact) or nav rail (expanded).
 * This lives inside each main route's own `composable { }` block rather than at the NavHost
 * level so the rail/bottom-bar can never be out of sync with the screen it wraps: both are part
 * of the exact same composition as the destination that's actually on screen. Hoisting this to
 * a shared wrapper keyed off a separately-observed "current route" caused a one-frame race on
 * back navigation, where the rail would appear (from the new, already-updated back stack state)
 * a frame before the destination itself actually swapped, squeezing the outgoing screen sideways.
 */
@Composable
private fun MainScaffold(
    currentRoute: String,
    expanded: Boolean,
    onNavigate: (String) -> Unit,
    content: @Composable () -> Unit,
) {
    Scaffold(
        bottomBar = {
            if (!expanded) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    mainDestinations.forEach { destination ->
                        NavigationBarItem(
                            selected = currentRoute == destination.route,
                            onClick = { onNavigate(destination.route) },
                            icon = { Icon(destination.icon, contentDescription = null) },
                            label = { Text(destination.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        Row(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (expanded) {
                NavigationRail(containerColor = MaterialTheme.colorScheme.surface) {
                    mainDestinations.forEach { destination ->
                        NavigationRailItem(
                            selected = currentRoute == destination.route,
                            onClick = { onNavigate(destination.route) },
                            icon = { Icon(destination.icon, contentDescription = null) },
                            label = { Text(destination.label) },
                        )
                    }
                }
            }
            Box(modifier = Modifier.weight(1f)) { content() }
        }
    }
}
