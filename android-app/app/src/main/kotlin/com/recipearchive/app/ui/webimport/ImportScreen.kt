package com.recipearchive.app.ui.webimport

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.RateReview
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.recipearchive.app.data.webimport.SavedLinkUi

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(
    viewModel: ImportViewModel,
    onImported: (String) -> Unit,
    onOpenHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()
    val savedLinks by viewModel.savedLinks.collectAsState()
    val nytAccountState by viewModel.nytAccountState.collectAsState()
    val previewState by viewModel.previewState.collectAsState()

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is ImportEvent.Imported -> onImported(event.recipeId)
            }
        }
    }

    if (uiState.pasteTextDialogOpen) {
        PasteTextDialog(
            text = uiState.pastedText,
            onTextChanged = viewModel::onPastedTextChanged,
            onConfirm = viewModel::importPastedText,
            onDismiss = viewModel::dismissPasteTextDialog,
        )
    }

    if (uiState.manageAccountsDialogOpen) {
        ManageAccountsDialog(
            nytAccountState = nytAccountState,
            onRemoveNyt = viewModel::removeNytCredentials,
            onDismiss = viewModel::dismissManageAccounts,
        )
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Import Recipes", style = MaterialTheme.typography.titleLarge) },
                actions = {
                    IconButton(onClick = onOpenHistory) {
                        Icon(Icons.Filled.History, contentDescription = "Import History")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            ImportFromUrlCard(uiState, viewModel::onUrlChanged, viewModel::importRecipe, viewModel::reviewBeforeImport)
            QuickSourcesCard(
                infoMessage = uiState.infoMessage,
                savedLinksExpanded = uiState.savedLinksExpanded,
                savedLinks = savedLinks,
                onSourceSelected = viewModel::onQuickSourceSelected,
                onDismissInfo = viewModel::dismissInfoMessage,
                onSavedLinkSelected = viewModel::importSavedLink,
            )
            NytAccountCard(
                state = nytAccountState,
                onEmailChanged = viewModel::onNytEmailChanged,
                onPasswordChanged = viewModel::onNytPasswordChanged,
                onSave = viewModel::saveNytCredentials,
                onTestLogin = viewModel::testNytLogin,
                onManageAccounts = viewModel::openManageAccounts,
            )
            if (previewState != null) {
                ImportPreviewCard(
                    preview = previewState!!,
                    onTitleChanged = viewModel::onPreviewTitleChanged,
                    onIngredientsChanged = viewModel::onPreviewIngredientsChanged,
                    onInstructionsChanged = viewModel::onPreviewInstructionsChanged,
                    onDiscard = viewModel::discardPreview,
                    onConfirm = viewModel::confirmPreviewImport,
                )
            }
            WhatHappensNextCard()
        }
    }
}

@Composable
private fun PasteTextDialog(
    text: String,
    onTextChanged: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Paste recipe text") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = onTextChanged,
                modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp),
                placeholder = { Text("Paste a title, ingredients, and instructions...") },
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = text.isNotBlank()) { Text("Import") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun ImportFromUrlCard(
    uiState: ImportUiState,
    onUrlChanged: (String) -> Unit,
    onImportClick: () -> Unit,
    onReviewClick: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Import from URL", style = MaterialTheme.typography.titleLarge)
            Text(
                "Paste a link to any recipe page and we'll fetch, parse, and save it to your library.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = uiState.url,
                onValueChange = onUrlChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Recipe URL") },
                placeholder = { Text("https://example.com/recipes/...") },
                singleLine = true,
                isError = uiState.errorMessage != null,
                supportingText = uiState.errorMessage?.let { { Text(it) } },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = onReviewClick,
                    enabled = !uiState.isLoading,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.RateReview, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Review First")
                }
                Button(
                    onClick = onImportClick,
                    enabled = !uiState.isLoading,
                    modifier = Modifier.weight(1f),
                ) {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary)
                        Spacer(Modifier.width(10.dp))
                        Text("Importing…")
                    } else {
                        Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Import Recipe")
                    }
                }
            }
        }
    }
}

@Composable
private fun ImportPreviewCard(
    preview: PreviewUiState,
    onTitleChanged: (String) -> Unit,
    onIngredientsChanged: (String) -> Unit,
    onInstructionsChanged: (String) -> Unit,
    onDiscard: () -> Unit,
    onConfirm: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Import Preview", style = MaterialTheme.typography.titleLarge)
            Text(
                preview.domain.ifBlank { preview.publisher },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = preview.title,
                onValueChange = onTitleChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Title") },
                singleLine = true,
            )
            OutlinedTextField(
                value = preview.ingredientsText,
                onValueChange = onIngredientsChanged,
                modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
                label = { Text("Ingredients (one per line)") },
            )
            OutlinedTextField(
                value = preview.instructionsText,
                onValueChange = onInstructionsChanged,
                modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
                label = { Text("Instructions (one per line)") },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onDiscard, enabled = !preview.isSaving, modifier = Modifier.weight(1f)) {
                    Text("Discard")
                }
                Button(onClick = onConfirm, enabled = !preview.isSaving, modifier = Modifier.weight(1f)) {
                    if (preview.isSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Text("Import This Recipe")
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickSourcesCard(
    infoMessage: String?,
    savedLinksExpanded: Boolean,
    savedLinks: List<SavedLinkUi>,
    onSourceSelected: (QuickSource) -> Unit,
    onDismissInfo: () -> Unit,
    onSavedLinkSelected: (SavedLinkUi) -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Quick Sources", style = MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                QuickSourceTile(Icons.Filled.RestaurantMenu, "NYT Cooking", Modifier.weight(1f)) {
                    onSourceSelected(QuickSource.NYT_COOKING)
                }
                QuickSourceTile(Icons.Filled.Public, "Any Website", Modifier.weight(1f)) {
                    onSourceSelected(QuickSource.ANY_WEBSITE)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                QuickSourceTile(Icons.Filled.ContentPaste, "Paste Text", Modifier.weight(1f)) {
                    onSourceSelected(QuickSource.PASTE_TEXT)
                }
                QuickSourceTile(Icons.Filled.Link, "Saved Link", Modifier.weight(1f)) {
                    onSourceSelected(QuickSource.SAVED_LINK)
                }
            }
            if (infoMessage != null) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    onClick = onDismissInfo,
                ) {
                    Text(
                        infoMessage,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
            if (savedLinksExpanded) {
                HorizontalDivider()
                if (savedLinks.isEmpty()) {
                    Text(
                        "No saved links yet. Recipes you import from a URL show up here for one-tap reimport.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(savedLinks, key = { it.url }) { link -> SavedLinkRow(link, onSavedLinkSelected) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SavedLinkRow(link: SavedLinkUi, onClick: (SavedLinkUi) -> Unit) {
    Surface(
        onClick = { onClick(link) },
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Link, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(link.title.ifBlank { "Untitled recipe" }, style = MaterialTheme.typography.titleMedium)
                Text(link.domain, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun QuickSourceTile(icon: ImageVector, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun NytAccountCard(
    state: NytAccountUiState,
    onEmailChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onSave: () -> Unit,
    onTestLogin: () -> Unit,
    onManageAccounts: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.RestaurantMenu, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("NYT Cooking account", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = if (state.isSaved) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Text(
                        if (state.isSaved) "Saved" else "Not connected",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
            OutlinedTextField(
                value = state.email,
                onValueChange = onEmailChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Email") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            )
            OutlinedTextField(
                value = state.password,
                onValueChange = onPasswordChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            )
            if (state.statusMessage != null) {
                Text(state.statusMessage, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onTestLogin, modifier = Modifier.weight(1f)) { Text("Test Login") }
                Button(onClick = onSave, modifier = Modifier.weight(1f)) { Text("Save Credentials") }
            }
            Text(
                "Credentials are stored securely on this device for your reference. We never sign in to " +
                    "NYT Cooking -- recipe pages are fetched from the public page, the same content their " +
                    "site serves to search engines.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onManageAccounts) { Text("Manage Source Accounts") }
        }
    }
}

@Composable
private fun ManageAccountsDialog(
    nytAccountState: NytAccountUiState,
    onRemoveNyt: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Manage Source Accounts") },
        text = {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("NYT Cooking", style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (nytAccountState.isSaved) "Credentials saved" else "Not connected",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (nytAccountState.isSaved) {
                        TextButton(onClick = onRemoveNyt) { Text("Remove") }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        },
    )
}

@Composable
private fun WhatHappensNextCard() {
    val steps = listOf(
        "Fetch page" to "We load the page you linked to.",
        "Parse recipe" to "We pull out the title, ingredients, and steps.",
        "Review" to "Check the imported recipe in your library.",
        "Save" to "It's saved alongside your archive recipes.",
    )
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("What happens next", style = MaterialTheme.typography.titleLarge)
            steps.forEachIndexed { index, (title, description) ->
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.secondary,
                    )
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("${index + 1}. $title", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
