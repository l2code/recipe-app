package com.recipearchive.app.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.StickyNote2
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.recipearchive.app.data.repository.RecipeDetailUi
import com.recipearchive.app.data.organization.RecipeCategories
import com.recipearchive.app.data.companion.IngredientAvailabilityUi
import com.recipearchive.app.data.companion.PantryStatus
import com.recipearchive.app.data.companion.RecipeCompanionUi
import com.recipearchive.app.data.local.entity.CookingSessionEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    viewModel: DetailViewModel,
    widthSizeClass: WindowWidthSizeClass,
    onBack: () -> Unit,
    onCookingStarted: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val detail by viewModel.uiState.collectAsState()
    val companion by viewModel.companionState.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestCompanion by rememberUpdatedState(companion)
    var showOriginalRecipe by remember { mutableStateOf(false) }
    DisposableEffect(lifecycleOwner, detail?.recipe?.id) {
        if (detail == null) return@DisposableEffect onDispose {}
        var activeSince = if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            System.currentTimeMillis()
        } else {
            null
        }
        var activeDuration = 0L
        var candidateRecorded = false
        fun stopTracking(now: Long) {
            activeSince?.let { activeDuration += (now - it).coerceAtLeast(0) }
            activeSince = null
            if (!candidateRecorded && activeDuration >= 8 * 60_000L &&
                latestCompanion.activeSession == null && latestCompanion.possibleSession == null
            ) {
                candidateRecorded = true
                viewModel.recordPossibleSession(now - activeDuration, now)
            }
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> activeSince = System.currentTimeMillis()
                Lifecycle.Event.ON_STOP -> stopTracking(System.currentTimeMillis())
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            stopTracking(System.currentTimeMillis())
        }
    }
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        detail?.recipe?.title ?: "Recipe",
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to library")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        detail?.let { current ->
            DetailContent(
                detail = current,
                onNotesChanged = viewModel::updateNotes,
                onRatingChanged = viewModel::updateRating,
                onCategoryChanged = viewModel::updateCategory,
                onCollectionChanged = viewModel::updateCollection,
                onCollectionCreated = viewModel::createCollection,
                companion = companion,
                onStartCooking = { viewModel.startCooking(onCookingStarted) },
                onPantryStatusChanged = viewModel::setPantryStatus,
                onAddUnavailableToShopping = viewModel::addUnavailableToShopping,
                onAddToMealPlan = viewModel::addToMealPlan,
                onShowOriginalRecipe = { showOriginalRecipe = true },
                onFavoriteChanged = viewModel::toggleFavorite,
                useTwoColumns = widthSizeClass == WindowWidthSizeClass.Expanded,
                modifier = Modifier.padding(padding),
            )
        } ?: Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    }
    if (showOriginalRecipe) {
        detail?.let { current ->
            OriginalRecipeSideSheet(
                detail = current,
                onDismiss = { showOriginalRecipe = false },
                onPromoteNote = { importedNote ->
                    val existing = current.appState?.personalNotes.orEmpty().trim()
                    val promoted = importedNote.trim()
                    if (promoted.isNotBlank() && !existing.contains(promoted)) {
                        viewModel.updateNotes(listOf(existing, promoted).filter(String::isNotBlank).joinToString("\n\n"))
                        viewModel.reviewImportedNotes("promoted")
                    }
                },
                onReviewStatusChanged = viewModel::reviewImportedNotes,
            )
        }
    }
    companion.possibleSession?.let { possible ->
        PossibleCookingSessionDialog(
            recipeTitle = detail?.recipe?.title ?: "this recipe",
            session = possible,
            onConfirm = { notes, rating -> viewModel.confirmPossibleSession(possible.id, notes, rating) },
            onDismiss = { viewModel.dismissPossibleSession(possible.id) },
        )
    }
}

@Composable
fun DetailContent(
    detail: RecipeDetailUi,
    onNotesChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
    onRatingChanged: (Int?) -> Unit = {},
    onCategoryChanged: (String?) -> Unit = {},
    onCollectionChanged: (String, Boolean) -> Unit = { _, _ -> },
    onCollectionCreated: (String) -> Unit = {},
    companion: RecipeCompanionUi = RecipeCompanionUi(),
    onStartCooking: () -> Unit = {},
    onPantryStatusChanged: (String, String, PantryStatus, Boolean) -> Unit = { _, _, _, _ -> },
    onAddUnavailableToShopping: () -> Unit = {},
    onAddToMealPlan: (LocalDate) -> Unit = {},
    onShowOriginalRecipe: () -> Unit = {},
    onFavoriteChanged: (Boolean) -> Unit = {},
    useTwoColumns: Boolean = false,
) {
    val header: @Composable () -> Unit = {
        RecipeHero(
            detail = detail,
            onRatingChanged = onRatingChanged,
            onCategoryChanged = onCategoryChanged,
            onCollectionChanged = onCollectionChanged,
            onCollectionCreated = onCollectionCreated,
            companion = companion,
            onStartCooking = onStartCooking,
            onAddToMealPlan = onAddToMealPlan,
            onAddUnavailableToShopping = onAddUnavailableToShopping,
            onShowOriginalRecipe = onShowOriginalRecipe,
            onFavoriteChanged = onFavoriteChanged,
            expanded = useTwoColumns,
        )
    }
    if (useTwoColumns) {
        Column(
            modifier = modifier.fillMaxSize().wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = 1160.dp)
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            header()
            if (detail.reviewFlags.isNotEmpty()) ReviewFlagsSection(detail.reviewFlags.map { it.flagValue })
            Row(modifier = Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                LazyColumn(
                    modifier = Modifier.weight(0.35f).fillMaxHeight(),
                    contentPadding = PaddingValues(bottom = 20.dp),
                ) {
                    item { IngredientsCard(detail, companion, onPantryStatusChanged, onAddUnavailableToShopping) }
                }
                LazyColumn(
                    modifier = Modifier.weight(0.65f).fillMaxHeight(),
                    contentPadding = PaddingValues(bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    item { InstructionsCard(detail) }
                    item { NotesSection(detail.appState?.personalNotes.orEmpty(), onNotesChanged) }
                    if (companion.sessions.isNotEmpty()) item { CookingHistoryCard(companion.sessions) }
                }
            }
        }
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize().wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = 940.dp),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item { header() }
            if (detail.reviewFlags.isNotEmpty()) item { ReviewFlagsSection(detail.reviewFlags.map { it.flagValue }) }
            item { IngredientsCard(detail, companion, onPantryStatusChanged, onAddUnavailableToShopping) }
            item { InstructionsCard(detail) }
            item { NotesSection(detail.appState?.personalNotes.orEmpty(), onNotesChanged) }
            if (companion.sessions.isNotEmpty()) item { CookingHistoryCard(companion.sessions) }
        }
    }
}

@Composable
private fun RecipeHero(
    detail: RecipeDetailUi,
    onRatingChanged: (Int?) -> Unit,
    onCategoryChanged: (String?) -> Unit,
    onCollectionChanged: (String, Boolean) -> Unit,
    onCollectionCreated: (String) -> Unit,
    companion: RecipeCompanionUi,
    onStartCooking: () -> Unit,
    onAddToMealPlan: (LocalDate) -> Unit,
    onAddUnavailableToShopping: () -> Unit,
    onShowOriginalRecipe: () -> Unit,
    onFavoriteChanged: (Boolean) -> Unit,
    expanded: Boolean,
) {
    var showOrganizer by remember { mutableStateOf(false) }
    var showPlanDialog by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    val favorite = detail.appState?.isFavorite ?: false
    val metadata: @Composable () -> Unit = {
        RecipeMetadata(
            detail = detail,
            companion = companion,
            onRatingChanged = onRatingChanged,
            onCategoryClick = { showOrganizer = true },
        )
    }
    val actions: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { showPlanDialog = true }) {
                Icon(Icons.Filled.CalendarMonth, contentDescription = "Add to meal plan", modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(5.dp))
                Text("Plan")
            }
            Spacer(Modifier.width(8.dp))
            Button(onClick = onStartCooking) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(if (companion.activeSession == null) "Start cooking" else "Resume")
            }
            Box {
                IconButton(onClick = { showMoreMenu = true }, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "More recipe actions")
                }
                DropdownMenu(expanded = showMoreMenu, onDismissRequest = { showMoreMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Organize recipe") },
                        onClick = { showMoreMenu = false; showOrganizer = true },
                    )
                    DropdownMenuItem(
                        text = {
                            val pendingCount = detail.handwrittenNotes.size.takeIf {
                                it > 0 && detail.appState?.importedNotesReviewStatus.orEmpty() == "pending"
                            }
                            Text(
                                pendingCount?.let { "$it imported ${if (it == 1) "note" else "notes"} to review" }
                                    ?: "Original Recipe",
                            )
                        },
                        onClick = { showMoreMenu = false; onShowOriginalRecipe() },
                    )
                    DropdownMenuItem(
                        text = { Text(if (favorite) "Remove from favorites" else "Add to favorites") },
                        leadingIcon = { Icon(if (favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, contentDescription = null) },
                        onClick = { showMoreMenu = false; onFavoriteChanged(favorite) },
                    )
                }
            }
        }
    }
    if (expanded) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.weight(1f)) { metadata() }
            Spacer(Modifier.width(12.dp))
            actions()
        }
    } else {
        Column(modifier = Modifier.fillMaxWidth()) {
            metadata()
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { actions() }
        }
    }
    if (showOrganizer) {
        OrganizerDialog(
            detail = detail,
            onDismiss = { showOrganizer = false },
            onCategoryChanged = onCategoryChanged,
            onCollectionChanged = onCollectionChanged,
            onCollectionCreated = onCollectionCreated,
        )
    }
    if (showPlanDialog) {
        MealPlanDateDialog(
            recipeTitle = detail.recipe.title,
            companion = companion,
            onAddUnavailableToShopping = onAddUnavailableToShopping,
            onDismiss = { showPlanDialog = false },
            onDateSelected = { date ->
                onAddToMealPlan(date)
                showPlanDialog = false
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecipeMetadata(
    detail: RecipeDetailUi,
    companion: RecipeCompanionUi,
    onRatingChanged: (Int?) -> Unit,
    onCategoryClick: () -> Unit,
) {
    val lastMade = companion.sessions.firstOrNull()?.let { it.finishedAt ?: it.startedAt }
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            detail.recipe.sourcePublisher.ifBlank { "Source unknown" },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterVertically),
        )
        Text("·", color = MaterialTheme.colorScheme.outline, modifier = Modifier.align(Alignment.CenterVertically))
        Text(
            detail.appState?.category ?: "Choose category",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.align(Alignment.CenterVertically).clickable(onClick = onCategoryClick),
        )
        Text("·", color = MaterialTheme.colorScheme.outline, modifier = Modifier.align(Alignment.CenterVertically))
        RatingRow(detail.appState?.personalRating, onRatingChanged)
        if (companion.madeCount > 0) {
            Text("· Made ${companion.madeCount}×", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.align(Alignment.CenterVertically))
            lastMade?.let {
                Text("· Last ${formatSessionDateShort(it)}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.align(Alignment.CenterVertically))
            }
        }
    }
}

@Composable
private fun MealPlanDateDialog(
    recipeTitle: String,
    companion: RecipeCompanionUi,
    onAddUnavailableToShopping: () -> Unit,
    onDismiss: () -> Unit,
    onDateSelected: (LocalDate) -> Unit,
) {
    val today = remember { LocalDate.now() }
    var dayCount by remember { mutableStateOf(14) }
    var selectedDate by remember { mutableStateOf(today) }
    val dates = remember(today, dayCount) { (0 until dayCount).map { today.plusDays(it.toLong()) } }
    val rangeLabel = "${today.format(DateTimeFormatter.ofPattern("MMM d"))} – ${dates.last().format(DateTimeFormatter.ofPattern("MMM d"))}"
    val unavailableCount = companion.lowCount + companion.neededCount + companion.unknownCount

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("Add to meal plan") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(recipeTitle, style = MaterialTheme.typography.titleMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(rangeLabel, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    FilterChip(
                        selected = dayCount == 7,
                        onClick = { dayCount = 7 },
                        label = { Text("7 days") },
                    )
                    FilterChip(
                        selected = dayCount == 14,
                        onClick = { dayCount = 14 },
                        label = { Text("14 days") },
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    dates.chunked(7).forEach { week ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            week.forEach { date ->
                                val isToday = date == today
                                val isSelected = date == selectedDate
                                Surface(
                                    onClick = { selectedDate = date },
                                    modifier = Modifier.weight(1f).height(76.dp),
                                    shape = MaterialTheme.shapes.medium,
                                    color = when {
                                        isSelected -> MaterialTheme.colorScheme.primaryContainer
                                        isToday -> MaterialTheme.colorScheme.secondaryContainer
                                        else -> MaterialTheme.colorScheme.surfaceVariant
                                    },
                                    border = if (isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
                                ) {
                                    Column(
                                        modifier = Modifier.padding(vertical = 8.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center,
                                    ) {
                                        Text(
                                            if (isToday) "Today" else date.format(DateTimeFormatter.ofPattern("EEE")),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Text(
                                            date.dayOfMonth.toString(),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Text(
                                            date.format(DateTimeFormatter.ofPattern("MMM")),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text("Ingredient check", style = MaterialTheme.typography.titleSmall)
                        Text(
                            buildList {
                                add("${companion.availableCount} have")
                                if (companion.needCount > 0) add("${companion.needCount} need")
                                if (companion.unknownCount > 0) add("${companion.unknownCount} unsure")
                            }.joinToString(" · "),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (unavailableCount > 0) {
                            TextButton(onClick = onAddUnavailableToShopping) {
                                Icon(Icons.Filled.ShoppingCart, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(5.dp))
                                Text("Add $unavailableCount to Shopping")
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onDateSelected(selectedDate) }) {
                Text("Add to ${selectedDate.format(DateTimeFormatter.ofPattern("EEE, MMM d"))}")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun PossibleCookingSessionDialog(
    recipeTitle: String,
    session: CookingSessionEntity,
    onConfirm: (String, Int?) -> Unit,
    onDismiss: () -> Unit,
) {
    var notes by remember { mutableStateOf("") }
    var rating by remember { mutableStateOf<Int?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("Did you make $recipeTitle?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "You actively used this recipe for ${formatRecordedDuration(session.durationMillis) ?: "a while"}. " +
                        "It will count as made only if you confirm it.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                RatingRow(rating) { rating = it }
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Session note (optional)") },
                    minLines = 2,
                )
            }
        },
        confirmButton = { Button(onClick = { onConfirm(notes, rating) }) { Text("Save as made") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Not this time") } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OriginalRecipeSideSheet(
    detail: RecipeDetailUi,
    onDismiss: () -> Unit,
    onPromoteNote: (String) -> Unit,
    onReviewStatusChanged: (String) -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    RightSideSheet(title = "Original Recipe", onDismiss = onDismiss) {
        item {
            Text("Source", style = MaterialTheme.typography.titleMedium)
            Surface(
                modifier = Modifier.fillMaxWidth().padding(top = 7.dp),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        detail.recipe.sourcePublisher.ifBlank { "Source not identified" },
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (detail.recipe.sourceUrl.isNotBlank()) {
                        TextButton(onClick = { runCatching { uriHandler.openUri(detail.recipe.sourceUrl) } }) {
                            Text("View original source")
                        }
                    }
                    detail.sourceEvidence.map { it.evidenceText.trim() }
                        .filter(String::isNotBlank)
                        .distinct()
                        .take(4)
                        .forEach { evidence ->
                            Text(evidence, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                }
            }
        }
        if (detail.handwrittenNotes.isNotEmpty()) {
            item { Text("Imported notes", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 18.dp)) }
            items(detail.handwrittenNotes, key = { "note:${it.pageId}" }) { note ->
                val importedText = note.transcription.ifBlank { note.ocrDraft }.trim()
                val reviewStatus = detail.appState?.importedNotesReviewStatus ?: "pending"
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f),
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            importedText.ifBlank { "No readable note text" },
                            style = MaterialTheme.typography.bodyMedium,
                            fontStyle = if (importedText.isBlank()) FontStyle.Italic else FontStyle.Normal,
                        )
                        if (importedText.isNotBlank() && reviewStatus == "pending") {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(onClick = { onPromoteNote(importedText) }) {
                                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(5.dp))
                                    Text("Add to My Notes")
                                }
                                TextButton(onClick = { onReviewStatusChanged("kept") }) { Text("Keep as reference") }
                                TextButton(onClick = { onReviewStatusChanged("dismissed") }) { Text("Dismiss") }
                            }
                        } else if (reviewStatus != "pending") {
                            Text(
                                when (reviewStatus) {
                                    "promoted" -> "Added to My Notes"
                                    "kept" -> "Kept as reference"
                                    else -> "Reviewed"
                                },
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }
                }
            }
        }
        if (detail.pages.isNotEmpty()) {
            item { Text("Original pages", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 18.dp)) }
            items(detail.pages, key = { "page:${it.pageRef}:${it.pageNumber}" }) { page ->
                val scan = page.scanFilename ?: page.pageRef
                val pageNumber = page.pageNumber?.let { "Page $it · " }.orEmpty()
                Text(
                    "$pageNumber$scan",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 7.dp),
                )
            }
        }
        item {
            Text("OCR text", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 18.dp))
            Surface(
                modifier = Modifier.fillMaxWidth().padding(top = 7.dp),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Text(
                    detail.recipe.rawText.ifBlank { "No OCR text available." },
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(14.dp),
                )
            }
        }
        item {
            Text("Import metadata", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 18.dp))
            Text(
                "Imported ${formatSessionDate(detail.recipe.lastImportedAt)} · Schema ${detail.recipe.importSchemaVersion}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 7.dp, bottom = 20.dp),
            )
        }
    }
}

@Composable
private fun RightSideSheet(
    title: String,
    onDismiss: () -> Unit,
    content: LazyListScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) {
            Surface(
                modifier = Modifier.fillMaxHeight().fillMaxWidth(0.46f).widthIn(min = 380.dp, max = 600.dp),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp,
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Filled.Close, contentDescription = "Close $title")
                        }
                    }
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                        content = content,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OrganizerDialog(
    detail: RecipeDetailUi,
    onDismiss: () -> Unit,
    onCategoryChanged: (String?) -> Unit,
    onCollectionChanged: (String, Boolean) -> Unit,
    onCollectionCreated: (String) -> Unit,
) {
    var newCollectionName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("Organize recipe") },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Text("Category", style = MaterialTheme.typography.titleSmall)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        RecipeCategories.all.forEach { category ->
                            FilterChip(
                                selected = detail.appState?.category == category,
                                onClick = { onCategoryChanged(category) },
                                label = { Text(category) },
                            )
                        }
                    }
                }
                item {
                    Text("Collections", style = MaterialTheme.typography.titleSmall)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        detail.collections.forEach { collection ->
                            val selected = collection.id in detail.collectionIds
                            FilterChip(
                                selected = selected,
                                onClick = { onCollectionChanged(collection.id, !selected) },
                                label = { Text(collection.name) },
                            )
                        }
                    }
                }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = newCollectionName,
                            onValueChange = { newCollectionName = it },
                            modifier = Modifier.weight(1f),
                            label = { Text("New collection") },
                            singleLine = true,
                        )
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = {
                                onCollectionCreated(newCollectionName)
                                newCollectionName = ""
                            },
                            enabled = newCollectionName.isNotBlank(),
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("Add")
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

@Composable
private fun RatingRow(rating: Int?, onRatingChanged: (Int?) -> Unit) {
    Row(modifier = Modifier.semantics { contentDescription = "Recipe rating" }) {
        (1..5).forEach { value ->
            IconButton(
                onClick = { onRatingChanged(if (rating == value) null else value) },
                modifier = Modifier.size(24.dp),
            ) {
                Icon(
                    if ((rating ?: 0) >= value) Icons.Filled.Star else Icons.Filled.StarBorder,
                    contentDescription = "$value stars",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(17.dp),
                )
            }
        }
    }
}

@Composable
private fun IngredientsCard(
    detail: RecipeDetailUi,
    companion: RecipeCompanionUi,
    onStatusChanged: (String, String, PantryStatus, Boolean) -> Unit,
    onAddUnavailableToShopping: () -> Unit,
) {
    var showAvailability by remember { mutableStateOf(false) }
    ContentCard(title = "Ingredients", icon = Icons.Filled.RestaurantMenu) {
        if (detail.ingredients.isEmpty()) {
            Text("No ingredients were extracted for this recipe.", style = MaterialTheme.typography.bodyMedium)
        } else {
            val reviewCount = detail.ingredients.count { it.parseStatus == "needs_review" }
            val availabilityByIngredient = companion.ingredients.associateBy { it.ingredient.id }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (reviewCount > 0) {
                    Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.tertiaryContainer) {
                        Text(
                            "$reviewCount ${if (reviewCount == 1) "line may" else "lines may"} need cleanup",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                }
                detail.ingredients.forEach { ingredient ->
                    IngredientRow(
                        text = listOf(ingredient.quantity, ingredient.unit, ingredient.item)
                            .filter { it.isNotBlank() }.joinToString(" ").ifBlank { ingredient.rawText },
                        availability = availabilityByIngredient[ingredient.id],
                    )
                }
                if (companion.ingredients.isNotEmpty()) {
                    val unavailableCount = companion.lowCount + companion.neededCount + companion.unknownCount
                    Spacer(Modifier.height(4.dp))
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                            Text(
                                buildList {
                                    add("${companion.availableCount} have")
                                    if (companion.needCount > 0) add("${companion.needCount} need")
                                    if (companion.unknownCount > 0) add("${companion.unknownCount} unsure")
                                }.joinToString(" · "),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                TextButton(onClick = { showAvailability = true }) { Text("Check ingredients") }
                                Spacer(Modifier.weight(1f))
                                if (unavailableCount > 0) {
                                    TextButton(onClick = onAddUnavailableToShopping) {
                                        Icon(Icons.Filled.ShoppingCart, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Add $unavailableCount to Shopping")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (showAvailability) {
        AvailabilityDialog(
            ingredients = companion.ingredients,
            onDismiss = { showAvailability = false },
            onStatusChanged = onStatusChanged,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AvailabilityDialog(
    ingredients: List<IngredientAvailabilityUi>,
    onDismiss: () -> Unit,
    onStatusChanged: (String, String, PantryStatus, Boolean) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("Ingredient availability") },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(ingredients, key = { it.ingredient.id }) { ingredient ->
                    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant) {
                        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                            Text(ingredient.displayName, style = MaterialTheme.typography.titleSmall)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf(PantryStatus.HAVE, PantryStatus.DONT_HAVE, PantryStatus.UNKNOWN).forEach { status ->
                                    FilterChip(
                                        selected = ingredient.status == status ||
                                            (status == PantryStatus.DONT_HAVE && ingredient.status == PantryStatus.LOW),
                                        onClick = {
                                            onStatusChanged(
                                                ingredient.ingredientKey,
                                                ingredient.displayName,
                                                status,
                                                ingredient.isStaple,
                                            )
                                        },
                                        label = { Text(status.label) },
                                    )
                                }
                                FilterChip(
                                    selected = ingredient.isStaple,
                                    onClick = {
                                        onStatusChanged(
                                            ingredient.ingredientKey,
                                            ingredient.displayName,
                                            ingredient.status,
                                            !ingredient.isStaple,
                                        )
                                    },
                                    label = { Text("Staple") },
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

@Composable
private fun InstructionsCard(detail: RecipeDetailUi) {
    ContentCard(
        title = "Instructions",
        icon = Icons.AutoMirrored.Filled.MenuBook,
        headerMeta = CookingTimeParser.extract(detail.recipe.rawText)?.let { "Cook: $it" },
    ) {
        if (detail.instructions.isEmpty()) {
            Text("No instructions were extracted for this recipe.", style = MaterialTheme.typography.bodyMedium)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                detail.instructions.forEach { instruction ->
                    InstructionRow(instruction.displayOrder, instruction.text)
                }
            }
        }
    }
}

@Composable
private fun ContentCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    headerMeta: String? = null,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                headerMeta?.let { meta ->
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Filled.Schedule,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                meta,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            content()
        }
    }
}

@Composable
private fun IngredientRow(text: String, availability: IngredientAvailabilityUi?) {
    val needsAttention = availability?.status == PantryStatus.LOW || availability?.status == PantryStatus.DONT_HAVE
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (needsAttention) MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.38f) else Color.Transparent,
                MaterialTheme.shapes.small,
            )
            .padding(horizontal = 6.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        when {
            availability?.status == PantryStatus.HAVE ||
                (availability?.isStaple == true && availability.status == PantryStatus.UNKNOWN) -> {
                Text("Have", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            needsAttention -> {
                Text("Need", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun IngredientStatusMark(availability: IngredientAvailabilityUi?) {
    val isAvailable = availability?.status == PantryStatus.HAVE ||
        (availability?.isStaple == true && availability.status == PantryStatus.UNKNOWN)
    val color = when {
        isAvailable -> MaterialTheme.colorScheme.primary
        availability?.status == PantryStatus.DONT_HAVE -> MaterialTheme.colorScheme.error
        availability?.status == PantryStatus.LOW -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.outline
    }
    Surface(
        modifier = Modifier.padding(top = 2.dp).size(21.dp),
        shape = CircleShape,
        color = if (isAvailable) color else Color.Transparent,
        border = BorderStroke(1.5.dp, color),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                when {
                    isAvailable -> "✓"
                    availability?.status == PantryStatus.LOW -> "!"
                    else -> ""
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (isAvailable) MaterialTheme.colorScheme.onPrimary else color,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun InstructionRow(order: Int, text: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier.size(32.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(order.toString(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(14.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun ReviewFlagsSection(flags: List<String>) {
    Surface(
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Unresolved review items" },
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.tertiaryContainer,
    ) {
        Row(modifier = Modifier.padding(18.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
            Spacer(Modifier.width(12.dp))
            Column {
                Text("A quick check would help", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
                flags.forEach { Text("• ${it.replace('_', ' ')}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer) }
            }
        }
    }
}

@Composable
private fun HandwrittenNotesCard(detail: RecipeDetailUi) {
    ContentCard(title = "Notes from the original", icon = Icons.AutoMirrored.Filled.StickyNote2) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            detail.handwrittenNotes.forEach { note ->
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            note.transcription.ifBlank { "Transcription pending" },
                            style = MaterialTheme.typography.bodyLarge,
                            fontStyle = if (note.transcription.isBlank()) FontStyle.Italic else FontStyle.Normal,
                        )
                        if (note.ocrDraft.isNotBlank() && note.ocrDraft != note.transcription) {
                            Text("OCR draft: ${note.ocrDraft}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScanPagesCard(detail: RecipeDetailUi) {
    ContentCard(title = "Original pages", icon = Icons.Filled.Description) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            detail.pages.forEach { page ->
                val scan = page.scanFilename ?: page.pageRef
                val pageNum = page.pageNumber?.let { "Page $it" }.orEmpty()
                Text(if (pageNum.isBlank()) scan else "$pageNum · $scan", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun CookingHistoryCard(sessions: List<CookingSessionEntity>) {
    var showAllHistory by remember { mutableStateOf(false) }
    ContentCard(title = "Cooking history", icon = Icons.Filled.History) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val lastMade = sessions.first().finishedAt ?: sessions.first().startedAt
            Text(
                "Made ${sessions.size} ${if (sessions.size == 1) "time" else "times"} · Last made ${formatSessionDate(lastMade)}",
                style = MaterialTheme.typography.titleMedium,
            )
            sessions.take(2).forEach { session ->
                CookingSessionRow(session)
            }
            if (sessions.size > 2) {
                TextButton(onClick = { showAllHistory = true }) {
                    Text("View all ${sessions.size} →")
                }
            }
        }
    }
    if (showAllHistory) {
        AllCookingHistorySideSheet(sessions = sessions, onDismiss = { showAllHistory = false })
    }
}

@Composable
private fun CookingSessionRow(session: CookingSessionEntity, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            val summary = buildList {
                add(formatSessionDate(session.finishedAt ?: session.startedAt))
                formatRecordedDuration(session.durationMillis)?.let(::add)
            }.joinToString(" · ")
            Text(summary, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            session.rating?.let { rating ->
                Text(ratingStars(rating), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
        }
        if (session.notes.isNotBlank()) {
            Text("“${session.notes}”", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 3.dp))
        }
        HorizontalDivider(modifier = Modifier.padding(top = 10.dp), color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun AllCookingHistorySideSheet(sessions: List<CookingSessionEntity>, onDismiss: () -> Unit) {
    val sessionsByYear = sessions.groupBy { sessionYear(it.finishedAt ?: it.startedAt) }.toSortedMap(reverseOrder())
    RightSideSheet(title = "Cooking History", onDismiss = onDismiss) {
        sessionsByYear.forEach { (year, yearSessions) ->
            item { Text(year.toString(), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 10.dp, bottom = 2.dp)) }
            items(yearSessions, key = { it.id }) { session ->
                CookingSessionRow(session, Modifier.fillMaxWidth().padding(top = 8.dp))
            }
        }
    }
}

private fun formatSessionDate(timestamp: Long): String = Instant.ofEpochMilli(timestamp)
    .atZone(ZoneId.systemDefault())
    .toLocalDate()
    .format(DateTimeFormatter.ofPattern("MMM d, yyyy"))

private fun formatSessionDateShort(timestamp: Long): String = Instant.ofEpochMilli(timestamp)
    .atZone(ZoneId.systemDefault())
    .toLocalDate()
    .format(DateTimeFormatter.ofPattern("MMM d"))

private fun sessionYear(timestamp: Long): Int = Instant.ofEpochMilli(timestamp)
    .atZone(ZoneId.systemDefault())
    .year

private fun formatRecordedDuration(milliseconds: Long?): String? {
    val minutes = (milliseconds ?: return null) / 60_000
    if (minutes <= 0) return null
    val hours = minutes / 60
    val remaining = minutes % 60
    return if (hours > 0) "${hours} hr ${remaining} min" else "$minutes min"
}

private fun ratingStars(rating: Int): String = buildString {
    repeat(5) { index -> append(if (index < rating.coerceIn(0, 5)) '★' else '☆') }
}

@Composable
private fun NotesSection(initialNotes: String, onNotesChanged: (String) -> Unit) {
    var draft by remember(initialNotes) { mutableStateOf(initialNotes) }
    var editing by remember { mutableStateOf(false) }
    ContentCard(title = "My Notes", icon = Icons.Filled.EditNote) {
        when {
            editing -> {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("What would you change next time?") },
                    minLines = 2,
                    shape = MaterialTheme.shapes.medium,
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { draft = initialNotes; editing = false }) { Text("Cancel") }
                    Button(onClick = { onNotesChanged(draft.trim()); editing = false }) { Text("Save") }
                }
            }
            initialNotes.isBlank() -> {
                TextButton(onClick = { draft = ""; editing = true }) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Add note")
                }
            }
            else -> {
                Text(initialNotes, style = MaterialTheme.typography.bodyLarge)
                TextButton(onClick = { draft = initialNotes; editing = true }) { Text("Edit") }
            }
        }
    }
}

@Composable
private fun SourceDetailsSection(detail: RecipeDetailUi) {
    var expanded by remember { mutableStateOf(false) }
    val publisher = detail.recipe.sourcePublisher
    val url = detail.recipe.sourceUrl
    Column(modifier = Modifier.fillMaxWidth()) {
        TextButton(onClick = { expanded = !expanded }) {
            Text(if (expanded) "Hide source details" else "Source details")
        }
        if (expanded) {
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(publisher.ifBlank { "Source not yet identified" }, style = MaterialTheme.typography.titleMedium)
                    if (url.isNotBlank()) Text(url, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    detail.sourceEvidence
                        .map { it.evidenceText.trim() }
                        .filter { it.isNotBlank() && !it.equals(url, ignoreCase = true) && !it.equals(publisher, ignoreCase = true) }
                        .distinct()
                        .forEach { evidence ->
                            Text(evidence, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                }
            }
        }
    }
}

@Composable
private fun RawOcrSection(rawText: String) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth()) {
        TextButton(onClick = { expanded = !expanded }) {
            Text(if (expanded) "Hide original OCR" else "Show original OCR")
        }
        if (expanded) {
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant) {
                Text(rawText.ifBlank { "No OCR text available." }, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(18.dp))
            }
        }
    }
}
