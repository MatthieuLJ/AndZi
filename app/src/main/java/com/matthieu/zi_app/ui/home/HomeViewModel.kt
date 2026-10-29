package com.matthieu.zi_app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.matthieu.zi_app.data.local.dao.LeitnerDao
import com.matthieu.zi_app.data.local.entity.LeitnerRecordEntity
import com.matthieu.zi_app.data.local.entity.ProgressRecordEntity
import com.matthieu.zi_app.data.preferences.UserPreferences
import com.matthieu.zi_app.data.repository.ProgressRepository
import com.matthieu.zi_app.data.repository.StudyRepository
import com.matthieu.zi_app.data.repository.VocabularyRepository
import com.matthieu.zi_app.domain.srs.MemoryForecastCalculator
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

class HomeViewModel(
    private val studyRepository: StudyRepository,
    private val progressRepository: ProgressRepository,
    private val userPreferences: UserPreferences? = null,
    private val leitnerDao: LeitnerDao? = null,
    private val vocabularyRepository: VocabularyRepository? = null,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState(isLoading = true, isAnalyticsLoading = true))
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var analyticsJob: Job? = null
    private var chartJob: Job? = null

    init {
        loadDashboardData()
    }

    fun selectTimeRange(timeRange: TimeRange) {
        _uiState.update { it.copy(selectedTimeRange = timeRange) }
        chartJob?.cancel()
        chartJob = viewModelScope.launch {
            try {
                val filtered = withContext(dispatcher) {
                    val now = System.currentTimeMillis()
                    val allHistory = progressRepository.getAllProgress()
                    filterHistoryByRange(allHistory, timeRange, now)
                }
                _uiState.update { it.copy(progressRecords = filtered) }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
        }
    }

    fun refresh() {
        loadDashboardData()
    }

    fun loadDashboardData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, isAnalyticsLoading = true) }
            try {
                val now = System.currentTimeMillis()

                // Phase 1: Fast Study Queue State (< 5ms)
                val due = studyRepository.getNumExpired(now)
                val boxCounts = studyRepository.getBoxCounts()
                val boxMap = (0..9).associateWith { box ->
                    boxCounts.find { it.box == box }?.count ?: 0
                }
                val totalLearned = boxMap.values.sum()

                val streakDays = userPreferences?.let { prefs ->
                    val recorded = prefs.getStreakDays(now)
                    if (recorded == 0 && prefs.hasNoRecordedStudy() && totalLearned > 0) {
                        1
                    } else {
                        recorded
                    }
                } ?: if (totalLearned > 0) 1 else 0

                _uiState.update {
                    it.copy(
                        dueCount = due,
                        totalLearned = totalLearned,
                        boxDistribution = boxMap,
                        streakDays = streakDays,
                        isQuickDataLoaded = true,
                        isLoading = false,
                        errorMessage = null
                    )
                }

                // Phase 2: Asynchronous Analytics & History Loading (in background)
                loadAnalyticsAsync(boxMap, totalLearned, now)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isAnalyticsLoading = false,
                        errorMessage = e.localizedMessage ?: "Failed to load dashboard"
                    )
                }
            }
        }
    }

    private fun loadAnalyticsAsync(
        boxMap: Map<Int, Int>,
        totalLearned: Int,
        now: Long
    ) {
        analyticsJob?.cancel()
        analyticsJob = viewModelScope.launch {
            try {
                val analyticsData = withContext(dispatcher) {
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

                    var history = progressRepository.getAllProgress()
                    val b0 = boxMap[0] ?: 0
                    val b1 = boxMap[1] ?: 0
                    val b2 = boxMap[2] ?: 0
                    val b3 = boxMap[3] ?: 0
                    val b4Plus = (4..9).sumOf { boxMap[it] ?: 0 }

                    val exp2 = allRecords.count { it.box == 1 && it.expTime < now }
                    val exp3 = allRecords.count { it.box == 2 && it.expTime < now }
                    val exp4 = allRecords.count { it.box == 3 && it.expTime < now }
                    val exp5 = allRecords.count { it.box >= 4 && it.expTime < now }

                    val latest = history.maxByOrNull { it.date }
                    val shouldRecordSnapshot = totalLearned > 0 && (
                        latest == null ||
                        (now - latest.date >= 15 * 60 * 1000L) ||
                        latest.b1 != b0 ||
                        latest.b2 != b1 ||
                        latest.b3 != b2 ||
                        latest.b4 != b3 ||
                        latest.b5 != b4Plus
                    )

                    if (shouldRecordSnapshot) {
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

                    val reviewStates = studyRepository.getAllReviewStates()
                    val memoryHealth = MemoryForecastCalculator.calculateMemoryHealth(reviewStates, now)
                    val forecastData = MemoryForecastCalculator.calculateForecast(reviewStates, now)

                    val allWordsFromDao = leitnerDao?.getAllDistinctCwords() ?: emptyList()
                    val allCharsFromDb = leitnerDao?.getAllDistinctCharacters() ?: emptyList()

                    val allWords = if (allWordsFromDao.isNotEmpty()) {
                        allWordsFromDao
                    } else {
                        vocabularyRepository?.getAllDistinctWords() ?: allRecords.map { it.hanzi }
                    }

                    val distinctChars = allCharsFromDb.toMutableSet()
                    if (vocabularyRepository != null) {
                        distinctChars.addAll(vocabularyRepository.getAllDistinctHanzi())
                    }
                    for (word in allWords) {
                        var charIdx = 0
                        while (charIdx < word.length) {
                            val cp = word.codePointAt(charIdx)
                            distinctChars.add(String(Character.toChars(cp)))
                            charIdx += Character.charCount(cp)
                        }
                    }

                    val leitnerMap = allRecords.associateBy { it.hanzi }

                    val masteredWords = if (allWords.isNotEmpty()) {
                        allWords.filter { (leitnerMap[it]?.box ?: 0) >= 5 }
                    } else {
                        allRecords.filter { it.box >= 5 }.map { it.hanzi }
                    }

                    val wordsKnown = masteredWords.size
                    val totalWordsCount = if (allWords.isNotEmpty()) allWords.size else allRecords.size

                    val distinctCharactersCount = if (distinctChars.isNotEmpty()) {
                        distinctChars.size
                    } else {
                        allRecords.map { it.hanzi }.flatMap { word ->
                            val chars = mutableListOf<String>()
                            var i = 0
                            while (i < word.length) {
                                val cp = word.codePointAt(i)
                                chars.add(String(Character.toChars(cp)))
                                i += Character.charCount(cp)
                            }
                            chars
                        }.distinct().size
                    }

                    val charsInMasteredWords = buildSet {
                        for (word in masteredWords) {
                            var i = 0
                            while (i < word.length) {
                                val cp = word.codePointAt(i)
                                add(String(Character.toChars(cp)))
                                i += Character.charCount(cp)
                            }
                        }
                    }

                    val charactersKnown = if (distinctChars.isNotEmpty()) {
                        distinctChars.count { char ->
                            (leitnerMap[char]?.box ?: 0) >= 5 || char in charsInMasteredWords
                        }
                    } else {
                        charsInMasteredWords.size
                    }

                    val totalTested = allRecords.sumOf { it.numTested }
                    val totalWrong = allRecords.sumOf { it.numWrong }
                    val accuracy = if (totalTested > 0) {
                        ((totalTested - totalWrong).toFloat() / totalTested.toFloat()) * 100f
                    } else 0f

                    val currentRange = _uiState.value.selectedTimeRange
                    val filteredHistory = filterHistoryByRange(history, currentRange, now)

                    HomeAnalyticsData(
                        wordsKnown = wordsKnown,
                        totalWords = totalWordsCount,
                        distinctCharactersCount = distinctCharactersCount,
                        charactersKnown = charactersKnown,
                        overallAccuracy = accuracy,
                        totalReviewsCount = totalTested,
                        progressRecords = filteredHistory,
                        memoryHealth = memoryHealth,
                        forecastData = forecastData
                    )
                }

                _uiState.update {
                    it.copy(
                        wordsKnown = analyticsData.wordsKnown,
                        totalWords = analyticsData.totalWords,
                        distinctCharactersCount = analyticsData.distinctCharactersCount,
                        charactersKnown = analyticsData.charactersKnown,
                        overallAccuracy = analyticsData.overallAccuracy,
                        totalReviewsCount = analyticsData.totalReviewsCount,
                        progressRecords = analyticsData.progressRecords,
                        memoryHealth = analyticsData.memoryHealth,
                        forecastData = analyticsData.forecastData,
                        isAnalyticsLoading = false
                    )
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                android.util.Log.e("HomeViewModel", "Failed to load async analytics", e)
                _uiState.update {
                    it.copy(isAnalyticsLoading = false)
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

    companion object {
        fun provideFactory(
            studyRepository: StudyRepository,
            progressRepository: ProgressRepository,
            userPreferences: UserPreferences? = null,
            leitnerDao: LeitnerDao? = null,
            vocabularyRepository: VocabularyRepository? = null,
            dispatcher: CoroutineDispatcher = Dispatchers.Default
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return HomeViewModel(
                    studyRepository = studyRepository,
                    progressRepository = progressRepository,
                    userPreferences = userPreferences,
                    leitnerDao = leitnerDao,
                    vocabularyRepository = vocabularyRepository,
                    dispatcher = dispatcher
                ) as T
            }
        }
    }
}

private data class HomeAnalyticsData(
    val wordsKnown: Int,
    val totalWords: Int,
    val distinctCharactersCount: Int,
    val charactersKnown: Int,
    val overallAccuracy: Float,
    val totalReviewsCount: Int,
    val progressRecords: List<ProgressRecordEntity>,
    val memoryHealth: com.matthieu.zi_app.domain.srs.MemoryHealthData,
    val forecastData: com.matthieu.zi_app.domain.srs.MemoryForecastData
)
