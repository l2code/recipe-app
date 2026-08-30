package com.recipearchive.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class ImportStatus { RUNNING, SUCCESS, PARTIAL, FAILED }

@Entity(tableName = "import_runs")
data class ImportRunEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bundleSchemaVersion: Int,
    val bundleGeneratedAt: String,
    val importStartedAt: Long,
    val importCompletedAt: Long?,
    val importedRecipeCount: Int,
    val insertedCount: Int,
    val updatedCount: Int,
    val deletedCount: Int,
    val errorCount: Int,
    val status: ImportStatus,
    val errorSummary: String?,
)
