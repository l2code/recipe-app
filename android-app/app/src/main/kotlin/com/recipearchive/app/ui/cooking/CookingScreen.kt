package com.recipearchive.app.ui.cooking

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.recipearchive.app.data.companion.IngredientAvailabilityUi
import com.recipearchive.app.data.companion.PantryStatus
import com.recipearchive.app.data.companion.RecipeCompanionUi
import com.recipearchive.app.data.local.entity.IngredientEntity
import com.recipearchive.app.data.local.entity.InstructionEntity
import com.recipearchive.app.data.local.entity.CookingSessionEntity
import com.recipearchive.app.data.repository.RecipeDetailUi
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CookingScreen(
    viewModel: CookingViewModel,
    widthSizeClass: WindowWidthSizeClass,
    onBack: () -> Unit,
    onSessionComplete: () -> Unit,
) {
    val recipe by viewModel.recipe.collectAsState()
    val session by viewModel.session.collectAsState()
    val companion by viewModel.companion.collectAsState()
    var showFinishDialog by remember { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }
    var completedSteps by remember(recipe?.recipe?.id) { mutableStateOf<Set<Long>>(emptySet()) }
    val now by produceState(System.currentTimeMillis(), session?.id) {
        while (true) {
            value = System.currentTimeMillis()
            delay(1000)
        }
    }
    val view = LocalView.current
    DisposableEffect(view) {
        val previous = view.keepScreenOn
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = previous }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    val isPaused = session?.pausedAt != null
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(9.dp).background(
                                if (isPaused) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                                CircleShape,
                            ),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (isPaused) "PAUSED" else "COOKING",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (isPaused) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(12.dp))
                        Text("· ${session?.let { formatElapsed(cookingElapsed(now, it)) } ?: "00:00"}", style = MaterialTheme.typography.titleMedium)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to recipe")
                    }
                },
                actions = {
                    OutlinedButton(onClick = viewModel::toggleTimer) {
                        Text(if (session?.pausedAt == null) "Pause" else "Resume")
                    }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = { showFinishDialog = true }) { Text("Finish") }
                    TextButton(onClick = { showDiscardDialog = true }) { Text("Discard") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        if (recipe == null || session == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            val currentRecipe = recipe!!
            val toggleStep: (Long) -> Unit = { id ->
                completedSteps = if (id in completedSteps) completedSteps - id else completedSteps + id
            }
            if (widthSizeClass == WindowWidthSizeClass.Expanded) {
                CookingTabletContent(currentRecipe, companion, completedSteps, toggleStep, padding)
            } else {
                CookingCompactContent(currentRecipe, completedSteps, toggleStep, padding)
            }
        }
    }

    if (showFinishDialog) {
        FinishCookingDialog(
            onDismiss = { showFinishDialog = false },
            onConfirm = { notes, rating -> viewModel.finish(notes, rating, onSessionComplete) },
        )
    }
    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Discard this cooking session?") },
            text = { Text("It will not count toward the number of times this recipe was made.") },
            confirmButton = {
                TextButton(onClick = { viewModel.discard(onSessionComplete) }) { Text("Discard") }
            },
            dismissButton = { TextButton(onClick = { showDiscardDialog = false }) { Text("Keep cooking") } },
        )
    }
}

@Composable
private fun CookingTabletContent(
    recipe: RecipeDetailUi,
    companion: RecipeCompanionUi,
    completedSteps: Set<Long>,
    onToggleStep: (Long) -> Unit,
    padding: PaddingValues,
) {
    Row(
        modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CookingIngredientsPane(recipe, companion, Modifier.weight(0.35f).fillMaxHeight())
        VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        CookingStepsPane(recipe.instructions, completedSteps, onToggleStep, Modifier.weight(0.65f).fillMaxHeight())
    }
}

@Composable
private fun CookingIngredientsPane(recipe: RecipeDetailUi, companion: RecipeCompanionUi, modifier: Modifier = Modifier) {
    val availabilityByIngredient = companion.ingredients.associateBy { it.ingredient.id }
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(recipe.recipe.title, style = MaterialTheme.typography.titleLarge)
            Text(
                "${companion.availableCount} have · ${companion.lowCount + companion.neededCount} need · ${companion.unknownCount} unsure",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 5.dp, bottom = 14.dp),
            )
            Text("Ingredients", style = MaterialTheme.typography.titleMedium)
            LazyColumn(
                modifier = Modifier.weight(1f).padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                recipe.ingredients.forEach { ingredient ->
                    item(key = ingredient.id) {
                        CookingIngredientRow(ingredient, availabilityByIngredient[ingredient.id])
                    }
                }
            }
        }
    }
}

@Composable
private fun CookingIngredientRow(ingredient: IngredientEntity, availability: IngredientAvailabilityUi?) {
    Row(verticalAlignment = Alignment.Top) {
        CookingStatusMark(availability)
        Spacer(Modifier.width(10.dp))
        Text(
            listOf(ingredient.quantity, ingredient.unit, ingredient.item)
                .filter(String::isNotBlank).joinToString(" ").ifBlank { ingredient.rawText },
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun CookingStatusMark(availability: IngredientAvailabilityUi?) {
    val isAvailable = availability?.status == PantryStatus.HAVE ||
        (availability?.isStaple == true && availability.status == PantryStatus.UNKNOWN)
    val color = when {
        isAvailable -> MaterialTheme.colorScheme.primary
        availability?.status == PantryStatus.DONT_HAVE -> MaterialTheme.colorScheme.error
        availability?.status == PantryStatus.LOW -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.outline
    }
    Surface(
        modifier = Modifier.padding(top = 2.dp).size(19.dp),
        shape = CircleShape,
        color = if (isAvailable) color else MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.5.dp, color),
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (isAvailable) Text("✓", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary)
        }
    }
}

@Composable
private fun CookingStepsPane(
    instructions: List<InstructionEntity>,
    completedSteps: Set<Long>,
    onToggleStep: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val activeStepId = instructions.firstOrNull { it.id !in completedSteps }?.id
    Column(modifier = modifier) {
        Text("Instructions", style = MaterialTheme.typography.titleLarge)
        Text(
            "${completedSteps.size.coerceAtMost(instructions.size)} of ${instructions.size} steps complete",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 10.dp),
        )
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            instructions.forEach { instruction ->
                item(key = instruction.id) {
                    CookingStepCard(
                        instruction = instruction,
                        isActive = instruction.id == activeStepId,
                        isComplete = instruction.id in completedSteps,
                        onToggle = { onToggleStep(instruction.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun CookingStepCard(
    instruction: InstructionEntity,
    isActive: Boolean,
    isComplete: Boolean,
    onToggle: () -> Unit,
) {
    Surface(
        onClick = onToggle,
        modifier = Modifier.fillMaxWidth(),
        color = when {
            isActive -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
            isComplete -> MaterialTheme.colorScheme.surfaceVariant
            else -> MaterialTheme.colorScheme.surface
        },
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier.size(34.dp).background(
                    if (isComplete || isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
                    CircleShape,
                ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (isComplete) "✓" else instruction.displayOrder.toString(),
                    fontWeight = FontWeight.Bold,
                    color = if (isComplete || isActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(instruction.text, style = MaterialTheme.typography.bodyLarge)
                if (isActive) {
                    Text(
                        "Tap when this step is done",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun CookingCompactContent(
    recipe: RecipeDetailUi,
    completedSteps: Set<Long>,
    onToggleStep: (Long) -> Unit,
    padding: PaddingValues,
) {
    val activeStepId = recipe.instructions.firstOrNull { it.id !in completedSteps }?.id
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding).widthIn(max = 900.dp),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { Text(recipe.recipe.title, style = MaterialTheme.typography.headlineMedium) }
        recipe.instructions.forEach { instruction ->
            item(key = instruction.id) {
                CookingStepCard(
                    instruction,
                    instruction.id == activeStepId,
                    instruction.id in completedSteps,
                ) { onToggleStep(instruction.id) }
            }
        }
    }
}

@Composable
private fun FinishCookingDialog(onDismiss: () -> Unit, onConfirm: (String, Int?) -> Unit) {
    var notes by remember { mutableStateOf("") }
    var rating by remember { mutableIntStateOf(0) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("Save cooking session") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("How did it go?", style = MaterialTheme.typography.titleMedium)
                Row {
                    (1..5).forEach { value ->
                        IconButton(onClick = { rating = if (rating == value) 0 else value }) {
                            Icon(
                                if (rating >= value) Icons.Filled.Star else Icons.Filled.StarBorder,
                                contentDescription = "$value stars",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Session notes") },
                    placeholder = { Text("What would you change next time?") },
                    minLines = 3,
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(notes, rating.takeIf { it > 0 }) }) { Text("Save session") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Keep cooking") } },
    )
}

private fun formatElapsed(milliseconds: Long): String {
    val totalSeconds = milliseconds.coerceAtLeast(0) / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%02d:%02d".format(minutes, seconds)
}

private fun cookingElapsed(now: Long, session: CookingSessionEntity): Long {
    val timerEnd = session.pausedAt ?: now
    return (timerEnd - session.startedAt - session.totalPausedMillis).coerceAtLeast(0)
}
