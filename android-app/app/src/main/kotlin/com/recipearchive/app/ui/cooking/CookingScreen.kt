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
    var stepNotes by remember(session?.id) { mutableStateOf<Map<Long, String>>(emptyMap()) }
    var stepTimers by remember(session?.id) { mutableStateOf<Map<Long, StepTimerState>>(emptyMap()) }
    var editingStep by remember { mutableStateOf<InstructionEntity?>(null) }
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
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isPaused) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(16.dp))
                        Text(
                            session?.let { formatElapsed(cookingElapsed(now, it)) } ?: "00:00",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
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
                    Button(onClick = { showFinishDialog = true }) { Text("Finish") }
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
            val toggleStepTimer: (Long) -> Unit = { id ->
                val timer = stepTimers[id] ?: StepTimerState()
                stepTimers = stepTimers + (id to if (timer.startedAt == null) {
                    timer.copy(startedAt = now)
                } else {
                    timer.copy(
                        startedAt = null,
                        accumulatedMillis = timer.accumulatedMillis + (now - timer.startedAt).coerceAtLeast(0),
                    )
                })
            }
            if (widthSizeClass == WindowWidthSizeClass.Expanded) {
                CookingTabletContent(
                    currentRecipe,
                    companion,
                    completedSteps,
                    toggleStep,
                    now,
                    stepTimers,
                    toggleStepTimer,
                    stepNotes,
                    { editingStep = it },
                    padding,
                )
            } else {
                CookingCompactContent(
                    currentRecipe,
                    companion,
                    completedSteps,
                    toggleStep,
                    now,
                    stepTimers,
                    toggleStepTimer,
                    stepNotes,
                    { editingStep = it },
                    padding,
                )
            }
        }
    }

    if (showFinishDialog) {
        val stepNoteSummary = recipe?.instructions.orEmpty().mapNotNull { instruction ->
            stepNotes[instruction.id]?.takeIf(String::isNotBlank)?.let { "Step ${instruction.displayOrder}: $it" }
        }
        val combinedNotes = stepNoteSummary.joinToString("\n")
        FinishCookingDialog(
            initialNotes = combinedNotes,
            onDismiss = { showFinishDialog = false },
            onRequestDiscard = {
                showFinishDialog = false
                showDiscardDialog = true
            },
            onConfirm = { notes, rating -> viewModel.finish(notes, rating, onSessionComplete) },
        )
    }
    editingStep?.let { step ->
        SessionNoteDialog(
            title = "Note for step ${step.displayOrder}",
            initialNote = stepNotes[step.id].orEmpty(),
            onDismiss = { editingStep = null },
            onSave = { note ->
                stepNotes = if (note.isBlank()) stepNotes - step.id else stepNotes + (step.id to note.trim())
                editingStep = null
            },
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
    now: Long,
    stepTimers: Map<Long, StepTimerState>,
    onToggleStepTimer: (Long) -> Unit,
    stepNotes: Map<Long, String>,
    onEditStepNote: (InstructionEntity) -> Unit,
    padding: PaddingValues,
) {
    Row(
        modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CookingIngredientsPane(recipe, companion, Modifier.weight(0.35f).fillMaxHeight())
        VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        CookingStepsPane(
            recipe.instructions,
            completedSteps,
            onToggleStep,
            now,
            stepTimers,
            onToggleStepTimer,
            stepNotes,
            onEditStepNote,
            Modifier.weight(0.65f).fillMaxHeight(),
        )
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
        Text(
            listOf(ingredient.quantity, ingredient.unit, ingredient.item)
                .filter(String::isNotBlank).joinToString(" ").ifBlank { ingredient.rawText },
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        when {
            availability?.status == PantryStatus.HAVE ||
                (availability?.isStaple == true && availability.status == PantryStatus.UNKNOWN) -> {
                Text("Have", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            availability?.status == PantryStatus.LOW || availability?.status == PantryStatus.DONT_HAVE -> {
                Text("Need", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun CookingStepsPane(
    instructions: List<InstructionEntity>,
    completedSteps: Set<Long>,
    onToggleStep: (Long) -> Unit,
    now: Long,
    stepTimers: Map<Long, StepTimerState>,
    onToggleStepTimer: (Long) -> Unit,
    stepNotes: Map<Long, String>,
    onEditStepNote: (InstructionEntity) -> Unit,
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
                        timer = stepTimers[instruction.id],
                        now = now,
                        onToggleTimer = { onToggleStepTimer(instruction.id) },
                        note = stepNotes[instruction.id].orEmpty(),
                        onEditNote = { onEditStepNote(instruction) },
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
    timer: StepTimerState? = null,
    now: Long = 0,
    onToggleTimer: () -> Unit = {},
    note: String = "",
    onEditNote: () -> Unit = {},
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
                Row(modifier = Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onToggleTimer) {
                        Text(
                            if (timer?.startedAt != null) "Pause timer ${formatElapsed(stepTimerElapsed(now, timer))}"
                            else if ((timer?.accumulatedMillis ?: 0) > 0) "Resume timer ${formatElapsed(timer?.accumulatedMillis ?: 0)}"
                            else "Start timer",
                        )
                    }
                    TextButton(onClick = onEditNote) { Text(if (note.isBlank()) "Add note" else "Edit note") }
                }
                if (note.isNotBlank()) {
                    Text(
                        note,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun CookingCompactContent(
    recipe: RecipeDetailUi,
    companion: RecipeCompanionUi,
    completedSteps: Set<Long>,
    onToggleStep: (Long) -> Unit,
    now: Long,
    stepTimers: Map<Long, StepTimerState>,
    onToggleStepTimer: (Long) -> Unit,
    stepNotes: Map<Long, String>,
    onEditStepNote: (InstructionEntity) -> Unit,
    padding: PaddingValues,
) {
    val activeStepId = recipe.instructions.firstOrNull { it.id !in completedSteps }?.id
    val availabilityByIngredient = companion.ingredients.associateBy { it.ingredient.id }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding).widthIn(max = 900.dp),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { Text(recipe.recipe.title, style = MaterialTheme.typography.headlineMedium) }
        item { Text("Ingredients", style = MaterialTheme.typography.titleLarge) }
        recipe.ingredients.forEach { ingredient ->
            item(key = "ingredient:${ingredient.id}") {
                CookingIngredientRow(ingredient, availabilityByIngredient[ingredient.id])
            }
        }
        item { Text("Instructions", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp)) }
        recipe.instructions.forEach { instruction ->
            item(key = instruction.id) {
                CookingStepCard(
                    instruction,
                    instruction.id == activeStepId,
                    instruction.id in completedSteps,
                    onToggle = { onToggleStep(instruction.id) },
                    timer = stepTimers[instruction.id],
                    now = now,
                    onToggleTimer = { onToggleStepTimer(instruction.id) },
                    note = stepNotes[instruction.id].orEmpty(),
                    onEditNote = { onEditStepNote(instruction) },
                )
            }
        }
    }
}

private data class StepTimerState(
    val startedAt: Long? = null,
    val accumulatedMillis: Long = 0,
)

@Composable
private fun SessionNoteDialog(
    title: String,
    initialNote: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var note by remember(initialNote) { mutableStateOf(initialNote) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("What happened while cooking?") },
                minLines = 3,
            )
        },
        confirmButton = { Button(onClick = { onSave(note) }) { Text("Save note") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun FinishCookingDialog(
    initialNotes: String,
    onDismiss: () -> Unit,
    onRequestDiscard: () -> Unit,
    onConfirm: (String, Int?) -> Unit,
) {
    var notes by remember(initialNotes) { mutableStateOf(initialNotes) }
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
        dismissButton = {
            Row {
                TextButton(onClick = onRequestDiscard) {
                    Text("Discard session", color = MaterialTheme.colorScheme.error)
                }
                TextButton(onClick = onDismiss) { Text("Keep cooking") }
            }
        },
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

private fun stepTimerElapsed(now: Long, timer: StepTimerState): Long =
    timer.accumulatedMillis + (timer.startedAt?.let { (now - it).coerceAtLeast(0) } ?: 0)
