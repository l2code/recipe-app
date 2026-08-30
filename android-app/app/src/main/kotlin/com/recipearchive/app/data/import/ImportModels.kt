package com.recipearchive.app.data.import

import com.recipearchive.app.data.local.entity.ImportStatus

data class SkippedRecipe(val index: Int, val id: String?, val reason: String)

sealed interface ImportOutcome {
    data class Completed(
        val runId: Long,
        val status: ImportStatus,
        val importedRecipeCount: Int,
        val insertedCount: Int,
        val updatedCount: Int,
        val skipped: List<SkippedRecipe>,
    ) : ImportOutcome

    data class Failed(val runId: Long?, val reason: String) : ImportOutcome
}
