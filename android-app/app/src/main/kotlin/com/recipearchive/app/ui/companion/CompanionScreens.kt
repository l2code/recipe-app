package com.recipearchive.app.ui.companion

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.recipearchive.app.data.companion.MealPlanItemUi
import com.recipearchive.app.data.companion.PantryCatalogItemUi
import com.recipearchive.app.data.companion.PantryStatus
import com.recipearchive.app.data.companion.ShoppingListItemUi
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingScreen(viewModel: CompanionViewModel, modifier: Modifier = Modifier) {
    val items by viewModel.shopping.collectAsState()
    CompanionScaffold(
        title = "Shopping List",
        modifier = modifier,
        action = {
            if (items.any { it.item.isChecked }) {
                TextButton(onClick = viewModel::clearChecked) { Text("Clear checked") }
            }
        },
    ) { padding ->
        if (items.isEmpty()) {
            EmptyCompanionState(Icons.Filled.ShoppingCart, "Your shopping list is empty", "Add unavailable ingredients from a recipe or meal plan.", padding)
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(items, key = { it.item.ingredientKey }) { item -> ShoppingRow(item, viewModel) }
            }
        }
    }
}

@Composable
private fun ShoppingRow(item: ShoppingListItemUi, viewModel: CompanionViewModel) {
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = item.item.isChecked,
                onCheckedChange = { viewModel.setShoppingChecked(item, it) },
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(item.item.displayName, style = MaterialTheme.typography.titleMedium)
                val quantities = item.sources.map { listOf(it.quantity, it.unit).filter(String::isNotBlank).joinToString(" ") }.filter(String::isNotBlank)
                if (quantities.isNotEmpty()) Text(quantities.joinToString(" + "), style = MaterialTheme.typography.bodyMedium)
                if (item.sourceRecipeTitles.isNotEmpty()) {
                    Text("For ${item.sourceRecipeTitles.joinToString()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            IconButton(onClick = { viewModel.deleteShoppingItem(item.item.ingredientKey) }) {
                Icon(Icons.Filled.DeleteOutline, contentDescription = "Remove ${item.item.displayName}")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantryScreen(viewModel: CompanionViewModel, modifier: Modifier = Modifier) {
    val items by viewModel.pantry.collectAsState()
    var newItem by remember { mutableStateOf("") }
    CompanionScaffold(title = "Pantry", modifier = modifier) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newItem,
                    onValueChange = { newItem = it },
                    modifier = Modifier.weight(1f),
                    label = { Text("Add pantry item") },
                    singleLine = true,
                )
                Spacer(Modifier.width(8.dp))
                Button(
                    enabled = newItem.isNotBlank(),
                    onClick = { viewModel.addPantryItem(newItem); newItem = "" },
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text("Add")
                }
            }
            if (items.isEmpty()) {
                EmptyCompanionState(Icons.Filled.Storefront, "No pantry items yet", "Mark ingredients as Have, Low, or Don't have from any recipe.", PaddingValues())
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(top = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(items, key = { it.item.ingredientKey }) { item -> PantryRow(item, viewModel) }
                }
            }
        }
    }
}

@Composable
private fun PantryRow(item: PantryCatalogItemUi, viewModel: CompanionViewModel) {
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(item.item.displayName, style = MaterialTheme.typography.titleMedium)
                    Text("Used in ${item.recipeUseCount} ${if (item.recipeUseCount == 1) "recipe" else "recipes"}", style = MaterialTheme.typography.bodySmall)
                }
                FilterChip(
                    selected = item.item.isStaple,
                    onClick = { viewModel.toggleStaple(item) },
                    label = { Text("Staple") },
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(PantryStatus.HAVE, PantryStatus.LOW, PantryStatus.DONT_HAVE).forEach { status ->
                    FilterChip(
                        selected = item.item.status == status.storedValue,
                        onClick = { viewModel.setPantryStatus(item, status) },
                        label = { Text(status.label) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealPlanScreen(viewModel: CompanionViewModel, onRecipeClick: (String) -> Unit, modifier: Modifier = Modifier) {
    val entries by viewModel.mealPlan.collectAsState()
    CompanionScaffold(title = "Meal Plan", modifier = modifier) { padding ->
        if (entries.isEmpty()) {
            EmptyCompanionState(Icons.Filled.Restaurant, "Nothing planned yet", "Open a recipe and add it to today or tomorrow.", padding)
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(entries, key = { it.entry.id }) { item -> MealPlanRow(item, viewModel, onRecipeClick) }
            }
        }
    }
}

@Composable
private fun MealPlanRow(item: MealPlanItemUi, viewModel: CompanionViewModel, onRecipeClick: (String) -> Unit) {
    val date = runCatching { LocalDate.parse(item.entry.plannedDate) }.getOrNull()
    val dateText = date?.format(DateTimeFormatter.ofPattern("EEE, MMM d")) ?: item.entry.plannedDate
    Surface(
        onClick = { onRecipeClick(item.entry.recipeId) },
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(dateText, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Text(item.recipeTitle, style = MaterialTheme.typography.titleLarge)
                    Text(item.entry.mealSlot, style = MaterialTheme.typography.bodyMedium)
                }
                IconButton(onClick = { viewModel.deleteMealPlanEntry(item.entry.id) }) {
                    Icon(Icons.Filled.DeleteOutline, contentDescription = "Remove planned meal")
                }
            }
            val ready = item.missingCount == 0 && item.totalCount > 0
            Surface(shape = CircleShape, color = if (ready) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.tertiaryContainer) {
                Text(
                    if (ready) "Ready · ${item.totalCount} ingredients available"
                    else "${item.availableCount} of ${item.totalCount} available · ${item.missingCount} to check",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            if (!ready) {
                TextButton(onClick = { viewModel.addMissingForPlan(item) }) { Text("Add unavailable to shopping list") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CompanionScaffold(
    title: String,
    modifier: Modifier = Modifier,
    action: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(title, style = MaterialTheme.typography.titleLarge) },
                actions = { action() },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        content = content,
    )
}

@Composable
private fun EmptyCompanionState(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, message: String, padding: PaddingValues) {
    Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
            Icon(icon, contentDescription = null, modifier = Modifier.padding(8.dp), tint = MaterialTheme.colorScheme.secondary)
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
