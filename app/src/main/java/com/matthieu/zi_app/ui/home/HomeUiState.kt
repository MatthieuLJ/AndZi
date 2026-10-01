package com.matthieu.zi_app.ui.home

import com.matthieu.zi_app.domain.srs.MemoryHealthData

data class HomeUiState(
    val dueCount: Int = 0,
    val streakDays: Int = 0,
    val totalLearned: Int = 0,
    val memoryHealth: MemoryHealthData = MemoryHealthData(),
    val boxDistribution: Map<Int, Int> = emptyMap(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)
