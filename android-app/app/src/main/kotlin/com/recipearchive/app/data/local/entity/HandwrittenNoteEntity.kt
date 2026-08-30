package com.recipearchive.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "handwritten_notes",
    foreignKeys = [
        ForeignKey(
            entity = RecipeEntity::class,
            parentColumns = ["id"],
            childColumns = ["recipeId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("recipeId")],
)
data class HandwrittenNoteEntity(
    @PrimaryKey val pageId: String,
    val recipeId: String,
    val scan: String,
    val page: Int,
    val imagePath: String,
    val ocrDraft: String,
    val transcription: String,
    val status: String,
    val reasons: String,
)
