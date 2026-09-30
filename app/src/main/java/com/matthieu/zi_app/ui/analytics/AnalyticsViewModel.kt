package com.matthieu.zi_app.ui.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.matthieu.zi_app.data.local.dao.LeitnerDao
import com.matthieu.zi_app.data.local.entity.ProgressRecordEntity
import com.matthieu.zi_app.data.repository.ProgressRepository
import com.matthieu.zi_app.data.repository.StudyRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AnalyticsViewModel(
    private val progressRepository: ProgressRepository,
    private val studyRepository: StudyRepository,
    private val leitnerDao: LeitnerDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(AnalyticsUiState(isLoading = true))
    val uiState: StateFlow<AnalyticsUiState> = _uiState.asStateFlow()

    init {
        loadAnalyticsData()
    }

    fun selectTimeRange(timeRange: TimeRange) {
        _uiState.update { it.copy(selectedTimeRange = timeRange) }
        loadChartData(timeRange)
    }

    fun refresh() {
        loadAnalyticsData()
    }

    private fun loadAnalyticsData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val now = System.currentTimeMillis()
                val allRecords = leitnerDao.getAllRecords()
                val dueCount = studyRepository.getNumExpired(now)
                val boxCounts = studyRepository.getBoxCounts()

                val boxMap = (0..9).associateWith { 0 }.toMutableMap()
                for (bc in boxCounts) {
                    boxMap[bc.box] = bc.count
                }

                val totalWords = boxMap.values.sum()
                val totalTested = allRecords.sumOf { it.numTested }
                val totalWrong = allRecords.sumOf { it.numWrong }
                val accuracy = if (totalTested > 0) {
                    ((totalTested - totalWrong).toFloat() / totalTested.toFloat()) * 100f
                } else 100f
                val streak = allRecords.maxOfOrNull { it.streak } ?: 0
                val mastered = allRecords.count { it.box >= 5 }

                // Check if progress history exists; if empty, record a baseline snapshot
                var history = progressRepository.getAllProgress()
                if (history.isEmpty() && totalWords > 0) {
                    val b0 = boxMap[0] ?: 0
                    val b1 = boxMap[1] ?: 0
                    val b2 = boxMap[2] ?: 0
                    val b3 = boxMap[3] ?: 0
                    val b4Plus = (4..9).sumOf { boxMap[it] ?: 0 }

                    val exp2 = allRecords.count { it.box == 1 && it.expTime < now }
                    val exp3 = allRecords.count { it.box == 2 && it.expTime < now }
                    val exp4 = allRecords.count { it.box == 3 && it.expTime < now }
                    val exp5 = allRecords.count { it.box >= 4 && it.expTime < now }

                    progressRepository.recordProgressSnapshot(
                        totalBox1 = b0,
                        totalBox2 = b1,
                        totalBox3 = b2,
                        totalBox4 = b3,
                        totalBox5 = b4Plus,
                        expiredBox2 = exp2,
                        expiredBox3 = exp3,
                        expiredBox4 = exp4,
                        expiredBox5 = exp5,
                        date = now
                    )
                    history = progressRepository.getAllProgress()
                }

                val currentRange = _uiState.value.selectedTimeRange
                val filteredHistory = filterHistoryByRange(history, currentRange, now)

                _uiState.update {
                    it.copy(
                        progressRecords = filteredHistory,
                        totalWordsStudied = totalWords,
                        cardsDueToday = dueCount,
                        overallAccuracy = accuracy,
                        currentStreak = streak,
                        totalReviewsCount = totalTested,
                        masteredWordsCount = mastered,
                        boxDistribution = boxMap,
                        isLoading = false,
                        errorMessage = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.localizedMessage ?: "Failed to load analytics"
                    )
                }
            }
        }
    }

    private fun loadChartData(timeRange: TimeRange) {
        viewModelScope.launch {
            try {
                val now = System.currentTimeMillis()
                val allHistory = progressRepository.getAllProgress()
                val filtered = filterHistoryByRange(allHistory, timeRange, now)
                _uiState.update { it.copy(progressRecords = filtered) }
            } catch (e: Exception) {
                // Keep existing chart records
            }
        }
    }

    private fun filterHistoryByRange(
        records: List<ProgressRecordEntity>,
        timeRange: TimeRange,
        now: Long
    ): List<ProgressRecordEntity> {
        if (timeRange == TimeRange.ALL) return records
        val minDate = now - timeRange.durationMillis
        return records.filter { it.date >= minDate }
    }

    companion object {
        fun provideFactory(
            progressRepository: ProgressRepository,
            studyRepository: StudyRepository,
            leitnerDao: LeitnerDao
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return AnalyticsViewModel(
                    progressRepository,
                    studyRepository,
                    leitnerDao
                ) as T
            }
        }
    }
}
