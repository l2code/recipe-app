package com.recipearchive.app.data.local

import androidx.room.TypeConverter
import com.recipearchive.app.data.local.entity.ImportStatus

class Converters {
    @TypeConverter
    fun fromImportStatus(value: ImportStatus): String = value.name

    @TypeConverter
    fun toImportStatus(value: String): ImportStatus = ImportStatus.valueOf(value)
}
