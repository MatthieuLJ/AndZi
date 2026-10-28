package com.matthieu.zi_app.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.matthieu.zi_app.Vocabulary
import com.matthieu.zi_app.data.local.AndZiDatabase
import com.matthieu.zi_app.data.local.ProgressDatabase
import com.matthieu.zi_app.data.repository.ProgressRepository
import com.matthieu.zi_app.data.repository.StudyRepository
import com.matthieu.zi_app.data.repository.VocabularyRepository
import com.matthieu.zi_app.ui.analytics.AnalyticsViewModel
import com.matthieu.zi_app.ui.analytics.TimeRange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class AnalyticsViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var andZiDb: AndZiDatabase
    private lateinit var progressDb: ProgressDatabase
    private lateinit var vocRepo: VocabularyRepository
    private lateinit var studyRepo: StudyRepository
    private lateinit var progressRepo: ProgressRepository

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        andZiDb = AndZiDatabase.createInMemory(context)
        progressDb = ProgressDatabase.createInMemory(context)

        vocRepo = VocabularyRepository(andZiDb.vocabularyDao())
        studyRepo = StudyRepository(andZiDb.leitnerDao())
        progressRepo = ProgressRepository(progressDb.progressDao(), andZiDb.leitnerDao())
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
        andZiDb.close()
        progressDb.close()
    }

    @Test
    fun testAnalyticsDataLoadingAndFiltering() = runBlocking {
        val voc1 = Vocabulary("字", "zi4", arrayListOf("character"))
        vocRepo.addVocabulary(voc1)

        val now = 10_000_000L
        repeat(5) {
            studyRepo.recordReviewResult("字", isCorrect = true, now = now)
        }

        val viewModel = AnalyticsViewModel(progressRepo, studyRepo, andZiDb.leitnerDao())

        val state = withTimeout(3000) {
            viewModel.uiState.filter { !it.isLoading }.first()
        }
        assertFalse(state.isLoading)
        assertEquals(1, state.totalWordsStudied)
        assertEquals(1, state.wordsKnown)
        assertEquals(1, state.totalWords)
        assertEquals(1, state.distinctCharactersCount)
        assertEquals(1, state.charactersKnown)
        assertEquals(100f, state.overallAccuracy)
        assertEquals(5, state.currentStreak)

        // Select 7D range
        viewModel.selectTimeRange(TimeRange.DAYS_7)
        val rangeState = withTimeout(3000) {
            viewModel.uiState.filter { it.selectedTimeRange == TimeRange.DAYS_7 && !it.isLoading }.first()
        }
        assertEquals(TimeRange.DAYS_7, rangeState.selectedTimeRange)
    }

    @Test
    fun testUnstudiedVocabularyAnalytics() = runBlocking {
        // Add vocabulary items without reviewing them
        vocRepo.addVocabulary(Vocabulary("猫", "mao1", arrayListOf("cat")))
        vocRepo.addVocabulary(Vocabulary("狗", "gou3", arrayListOf("dog")))

        val viewModel = AnalyticsViewModel(progressRepo, studyRepo, andZiDb.leitnerDao(), vocRepo)

        val state = withTimeout(3000) {
            viewModel.uiState.filter { !it.isLoading }.first()
        }

        assertFalse(state.isLoading)
        // 2 vocabulary words + 2 constituent char_pinyin
        assertEquals(2, state.totalWordsStudied)
        assertEquals(0, state.wordsKnown)
        assertEquals(2, state.totalWords)
        assertEquals(2, state.distinctCharactersCount)
        assertEquals(0, state.charactersKnown)
        assertEquals(2, state.cardsDueToday)
        assertEquals(2, state.boxDistribution[0])
        assertEquals(0f, state.overallAccuracy)
        assertEquals(0, state.totalReviewsCount)

        // Memory health should accurately reflect 2 active words all in Due state
        assertEquals(2, state.memoryHealth.totalActiveWords)
        assertEquals(2, state.memoryHealth.dueCount)
        assertEquals(0, state.memoryHealth.strongCount)

        // Forecast should have 2 total cards
        assertEquals(2, state.forecastData.totalCards)
        assertFalse(state.forecastData.points.isEmpty())
        assertFalse(state.progressRecords.isEmpty())
    }

    @Test
    fun testCompoundWordsAndDistinctCharactersCount() = runBlocking {
        // Add compound words sharing characters: 学习 (学, 习), 学生 (学, 生), 大学 (大, 学)
        vocRepo.addVocabulary(Vocabulary("学习", "xue2xi2", arrayListOf("study")))
        vocRepo.addVocabulary(Vocabulary("学生", "xue2sheng1", arrayListOf("student")))
        vocRepo.addVocabulary(Vocabulary("大学", "da4xue2", arrayListOf("university")))

        // Review 学习 and 学生 to make them mastered (Box >= 5); leave 大学 unstudied (Box 0)
        repeat(5) { studyRepo.recordReviewResult("学习", isCorrect = true) }
        repeat(5) { studyRepo.recordReviewResult("学生", isCorrect = true) }

        val viewModel = AnalyticsViewModel(progressRepo, studyRepo, andZiDb.leitnerDao(), vocRepo)

        val state = withTimeout(3000) {
            viewModel.uiState.filter { !it.isLoading }.first()
        }

        assertFalse(state.isLoading)
        // Total words in deck: 3 distinct compound words
        assertEquals(3, state.totalWords)
        // Words mastered (Box >= 5): 2 (学习, 学生)
        assertEquals(2, state.wordsKnown)
        // Distinct characters in deck: 4 (学, 习, 生, 大)
        assertEquals(4, state.distinctCharactersCount)
        // Characters mastered (Box >= 5 or in mastered word): 3 (学, 习, 生)
        assertEquals(3, state.charactersKnown)
    }
}
