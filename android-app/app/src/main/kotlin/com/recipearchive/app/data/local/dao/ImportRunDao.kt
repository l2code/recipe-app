package com.recipearchive.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.recipearchive.app.data.local.entity.ImportRunEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ImportRunDao {
    @Insert
    suspend fun insert(run: ImportRunEntity): Long

    @Query("SELECT * FROM import_runs ORDER BY id DESC LIMIT 1")
    suspend fun getLatest(): ImportRunEntity?

    @Query("SELECT * FROM import_runs ORDER BY id DESC LIMIT 1")
    fun observeLatest(): Flow<ImportRunEntity?>

    @Query("SELECT * FROM import_runs ORDER BY id DESC")
    fun observeAll(): Flow<List<ImportRunEntity>>
}
