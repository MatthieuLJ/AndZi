package com.matthieu.zi_app.ui.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.matthieu.zi_app.data.local.dao.LeitnerDao
import com.matthieu.zi_app.data.local.entity.LeitnerRecordEntity
import com.matthieu.zi_app.data.local.entity.ProgressRecordEntity
import com.matthieu.zi_app.data.repository.ProgressRepository
import com.matthieu.zi_app.data.repository.StudyRepository
import com.matthieu.zi_app.ui.home.TimeRange
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AnalyticsViewModel(
    private val studyRepository: StudyRepository,
    private val progressRepository: ProgressRepository,
    private val leitnerDao: LeitnerDao? = null,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) : ViewModel() {

    private val _uiState = MutableStateFlow(AnalyticsUiState(isLoading = true))
    val uiState: StateFlow<AnalyticsUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null
    private var chartJob: Job? = null

    init {
        loadData()
    }

    fun refresh() {
        loadData()
    }

    fun selectTimeRange(timeRange: TimeRange) {
        _uiState.update { it.copy(selectedTimeRange = timeRange) }
        chartJob?.cancel()
        chartJob = viewModelScope.launch(dispatcher) {
            try {
                val (allHistory, filtered) = withContext(dispatcher) {
                    val now = System.currentTimeMillis()
                    val history = _uiState.value.allProgressRecords.ifEmpty {
                        progressRepository.getAllProgress()
                    }
                    history to filterHistoryByRange(history, timeRange, now)
                }
                _uiState.update {
                    it.copy(
                        allProgressRecords = allHistory,
                        progressRecords = filtered
                    )
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
        }
    }

    fun loadData() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch(dispatcher) {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val now = System.currentTimeMillis()

                val boxCounts = studyRepository.getBoxCounts()
                val boxMap = (0..9).associateWith { box ->
                    boxCounts.find { it.box == box }?.count ?: 0
                }
                val totalCards = boxMap.values.sum()

                val allRecords: List<LeitnerRecordEntity> = leitnerDao?.getAllStudyRecords()
                    ?: studyRepository.getAllRecords().map {
                        LeitnerRecordEntity(
                            hanzi = it.hanzi,
                            box = it.box,
                            expTime = it.expTime,
                            numTested = it.numTested,
                            numWrong = it.numWrong,
                            streak = it.streak,
                            dateAdded = it.dateAdded
                        )
                    }

                val totalTested = allRecords.sumOf { it.numTested }
                val totalWrong = allRecords.sumOf { it.numWrong }
                val accuracy = if (totalTested > 0) {
                    ((totalTested - totalWrong).toFloat() / totalTested.toFloat()) * 100f
                } else 0f

                val history = progressRepository.getAllProgress()
                val oldestDate = history.minOfOrNull { it.date } ?: now
                val newestDate = history.maxOfOrNull { it.date } ?: now
                val totalSpanMillis = kotlin.math.max(now - oldestDate, newestDate - oldestDate)

                val availableRanges = mutableListOf<TimeRange>()
                availableRanges.add(TimeRange.DAYS_7)
                if (totalSpanMillis >= TimeRange.DAYS_30.durationMillis) {
                    availableRanges.add(TimeRange.DAYS_30)
                }
                if (totalSpanMillis >= TimeRange.DAYS_90.durationMillis) {
                    availableRanges.add(TimeRange.DAYS_90)
                }
                availableRanges.add(TimeRange.ALL)

                val currentRange = _uiState.value.selectedTimeRange
                val effectiveRange = if (currentRange in availableRanges) currentRange else TimeRange.DAYS_7
                val filteredHistory = filterHistoryByRange(history, effectiveRange, now)

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        overallAccuracy = accuracy,
                        totalTested = totalTested,
                        totalWrong = totalWrong,
                        totalCards = totalCards,
                        boxDistribution = boxMap,
                        progressRecords = filteredHistory,
                        allProgressRecords = history,
                        selectedTimeRange = effectiveRange,
                        availableTimeRanges = availableRanges,
                        errorMessage = null
                    )
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.localizedMessage ?: "Failed to load analytics"
                    )
                }
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
        val filtered = records.filter { it.date >= minDate }
        if (filtered.isEmpty() && records.isNotEmpty()) {
            return listOf(records.last())
        }
        return filtered
    }

    override fun onCleared() {
        super.onCleared()
        loadJob?.cancel()
        chartJob?.cancel()
    }

    companion object {
        fun provideFactory(
            studyRepository: StudyRepository,
            progressRepository: ProgressRepository,
            leitnerDao: LeitnerDao? = null,
            dispatcher: CoroutineDispatcher = Dispatchers.Default
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return AnalyticsViewModel(
                    studyRepository = studyRepository,
                    progressRepository = progressRepository,
                    leitnerDao = leitnerDao,
                    dispatcher = dispatcher
                ) as T
            }
        }
    }
}
