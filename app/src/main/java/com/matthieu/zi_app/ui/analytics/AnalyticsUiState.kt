package com.matthieu.zi_app.ui.analytics

import com.matthieu.zi_app.data.local.entity.ProgressRecordEntity
import com.matthieu.zi_app.ui.home.TimeRange

data class AnalyticsUiState(
    val isLoading: Boolean = true,
    val overallAccuracy: Float = 0f,
    val totalTested: Int = 0,
    val totalWrong: Int = 0,
    val totalCards: Int = 0,
    val boxDistribution: Map<Int, Int> = emptyMap(),
    val progressRecords: List<ProgressRecordEntity> = emptyList(),
    val allProgressRecords: List<ProgressRecordEntity> = emptyList(),
    val selectedTimeRange: TimeRange = TimeRange.DAYS_7,
    val availableTimeRanges: List<TimeRange> = listOf(TimeRange.DAYS_7, TimeRange.ALL),
    val errorMessage: String? = null
)
