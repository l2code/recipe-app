package com.recipearchive.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.recipearchive.app.data.local.entity.WebImportHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WebImportHistoryDao {
    @Insert
    suspend fun insert(entry: WebImportHistoryEntity): Long

    @Query("SELECT * FROM web_import_history ORDER BY importedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<WebImportHistoryEntity>>

    @Query(
        "SELECT * FROM web_import_history WHERE status = 'SUCCESS' AND url != '' " +
            "ORDER BY importedAt DESC LIMIT :limit",
    )
    fun observeRecentSuccessful(limit: Int): Flow<List<WebImportHistoryEntity>>
}
