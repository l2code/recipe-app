package com.recipearchive.app.data.webimport

import com.recipearchive.app.data.local.entity.WebImportOutcomeStatus

/** A previously-successful URL import, shown as a one-tap "Saved Link" quick source. */
data class SavedLinkUi(
    val url: String,
    val title: String,
    val domain: String,
    val importedAt: Long,
)

/** One row in the full Import History screen -- every attempt, successful or not. */
data class ImportHistoryEntryUi(
    val id: Long,
    val url: String,
    val title: String,
    val domain: String,
    val status: WebImportOutcomeStatus,
    val errorMessage: String?,
    val recipeId: String?,
    val importedAt: Long,
)
