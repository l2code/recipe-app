package com.recipearchive.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Import-owned recipe row. Every column here is fully replaced on each import;
 * app-owned state (favorite, rating, notes) lives in [RecipeAppStateEntity] instead.
 */
@Entity(tableName = "recipes")
data class RecipeEntity(
    @PrimaryKey val id: String,
    val title: String,
    val rawText: String,
    val wordCount: Int,
    val arrangementStatus: String,
    val duplicateStatus: String,
    val sourcePublisher: String,
    val sourceDomain: String,
    val sourceUrl: String,
    val sourceStatus: String,
    val importSchemaVersion: Int,
    val importGeneratedAt: String,
    val createdAt: Long,
    val lastImportedAt: Long,
)
