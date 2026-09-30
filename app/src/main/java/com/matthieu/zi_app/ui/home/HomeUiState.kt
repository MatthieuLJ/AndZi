package com.matthieu.zi_app.ui.home

data class HomeUiState(
    val dueCount: Int = 0,
    val streakDays: Int = 0,
    val totalLearned: Int = 0,
    val boxDistribution: Map<Int, Int> = emptyMap(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)
