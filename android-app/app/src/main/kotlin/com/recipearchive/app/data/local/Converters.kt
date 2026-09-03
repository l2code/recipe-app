package com.recipearchive.app.data.local

import androidx.room.TypeConverter
import com.recipearchive.app.data.local.entity.ImportStatus
import com.recipearchive.app.data.local.entity.WebImportOutcomeStatus

class Converters {
    @TypeConverter
    fun fromImportStatus(value: ImportStatus): String = value.name

    @TypeConverter
    fun toImportStatus(value: String): ImportStatus = ImportStatus.valueOf(value)

    @TypeConverter
    fun fromWebImportOutcomeStatus(value: WebImportOutcomeStatus): String = value.name

    @TypeConverter
    fun toWebImportOutcomeStatus(value: String): WebImportOutcomeStatus = WebImportOutcomeStatus.valueOf(value)
}
