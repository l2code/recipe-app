package com.recipearchive.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class WebImportOutcomeStatus { SUCCESS, NOT_FOUND, NETWORK_ERROR, PARSE_ERROR }

/**
 * One row per URL/pasted-text import attempt, successful or not. Backs both the
 * "Saved Link" quick-reimport shortcut (successful, URL-backed rows) and the
 * full Import History screen (every attempt).
 */
@Entity(tableName = "web_import_history")
data class WebImportHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val title: String,
    val domain: String,
    val status: WebImportOutcomeStatus,
    val errorMessage: String?,
    val recipeId: String?,
    val importedAt: Long,
)
