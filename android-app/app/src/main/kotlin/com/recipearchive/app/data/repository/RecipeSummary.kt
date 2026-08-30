package com.recipearchive.app.data.repository

data class RecipeSummary(
    val id: String,
    val title: String,
    val sourcePublisher: String,
    val hasReviewFlags: Boolean,
    val isFavorite: Boolean,
)
