package com.recipearchive.app.data.repository

data class RecipeSummary(
    val id: String,
    val title: String,
    val sourcePublisher: String,
    val hasReviewFlags: Boolean,
    val isFavorite: Boolean,
    val personalRating: Int? = null,
    val category: String? = null,
    val collectionIds: Set<String> = emptySet(),
    val madeCount: Int = 0,
    val lastMadeAt: Long? = null,
    val averageDurationMillis: Long? = null,
)
