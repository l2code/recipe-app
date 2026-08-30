package com.recipearchive.app.data.repository

import com.recipearchive.app.data.local.entity.HandwrittenNoteEntity
import com.recipearchive.app.data.local.entity.IngredientEntity
import com.recipearchive.app.data.local.entity.InstructionEntity
import com.recipearchive.app.data.local.entity.RecipeAppStateEntity
import com.recipearchive.app.data.local.entity.RecipeEntity
import com.recipearchive.app.data.local.entity.RecipePageEntity
import com.recipearchive.app.data.local.entity.RecipeReviewFlagEntity
import com.recipearchive.app.data.local.entity.SourceEvidenceEntity

data class RecipeDetailUi(
    val recipe: RecipeEntity,
    val ingredients: List<IngredientEntity>,
    val instructions: List<InstructionEntity>,
    val pages: List<RecipePageEntity>,
    val handwrittenNotes: List<HandwrittenNoteEntity>,
    val sourceEvidence: List<SourceEvidenceEntity>,
    val reviewFlags: List<RecipeReviewFlagEntity>,
    val appState: RecipeAppStateEntity?,
)
