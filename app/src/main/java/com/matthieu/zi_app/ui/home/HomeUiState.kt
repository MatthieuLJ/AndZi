package com.matthieu.zi_app.ui.home

import com.matthieu.zi_app.data.local.entity.ProgressRecordEntity
import com.matthieu.zi_app.domain.srs.MemoryForecastData
import com.matthieu.zi_app.domain.srs.MemoryHealthData

data class HomeUiState(
    // Phase 1: Fast Study Queue State (Instant, < 5ms)
    val dueCount: Int = 0,
    val streakDays: Int = 0,
    val totalLearned: Int = 0,
    val isQuickDataLoaded: Boolean = false,

    // Phase 2: Asynchronous Analytics State (Background task)
    val wordsKnown: Int = 0,
    val totalWords: Int = 0,
    val distinctCharactersCount: Int = 0,
    val charactersKnown: Int = 0,
    val overallAccuracy: Float = 0f,
    val totalReviewsCount: Int = 0,
    val progressRecords: List<ProgressRecordEntity> = emptyList(),
    val selectedTimeRange: TimeRange = TimeRange.DAYS_30,
    val memoryHealth: MemoryHealthData = MemoryHealthData(),
    val forecastData: MemoryForecastData = MemoryForecastData(),
    val boxDistribution: Map<Int, Int> = emptyMap(),
    val isAnalyticsLoading: Boolean = true,

    // General loading state (true until fast data is loaded)
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)
