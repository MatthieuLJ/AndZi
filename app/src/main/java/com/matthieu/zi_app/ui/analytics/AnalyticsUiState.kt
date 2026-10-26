package com.matthieu.zi_app.ui.analytics

import com.matthieu.zi_app.data.local.entity.ProgressRecordEntity
import com.matthieu.zi_app.domain.srs.MemoryForecastData
import com.matthieu.zi_app.domain.srs.MemoryHealthData

enum class TimeRange(val label: String, val durationMillis: Long) {
    DAYS_7("7D", 7L * 24 * 60 * 60 * 1000L),
    DAYS_30("30D", 30L * 24 * 60 * 60 * 1000L),
    DAYS_90("90D", 90L * 24 * 60 * 60 * 1000L),
    ALL("All", Long.MAX_VALUE)
}

data class AnalyticsUiState(
    val selectedTimeRange: TimeRange = TimeRange.DAYS_30,
    val progressRecords: List<ProgressRecordEntity> = emptyList(),
    val memoryHealth: MemoryHealthData = MemoryHealthData(),
    val forecastData: MemoryForecastData = MemoryForecastData(),
    val wordsKnown: Int = 0,
    val totalWords: Int = 0,
    val distinctCharactersCount: Int = 0,
    val charactersKnown: Int = 0,
    val totalWordsStudied: Int = 0,
    val cardsDueToday: Int = 0,
    val overallAccuracy: Float = 0f,
    val currentStreak: Int = 0,
    val totalReviewsCount: Int = 0,
    val masteredWordsCount: Int = 0,
    val boxDistribution: Map<Int, Int> = emptyMap(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)
