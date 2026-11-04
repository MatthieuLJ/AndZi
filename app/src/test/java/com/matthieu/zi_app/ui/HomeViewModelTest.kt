package com.matthieu.zi_app.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.matthieu.zi_app.Vocabulary
import com.matthieu.zi_app.data.local.AndZiDatabase
import com.matthieu.zi_app.data.local.ProgressDatabase
import com.matthieu.zi_app.data.repository.ProgressRepository
import com.matthieu.zi_app.data.repository.StudyRepository
import com.matthieu.zi_app.data.repository.VocabularyRepository
import com.matthieu.zi_app.ui.home.HomeViewModel
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
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class HomeViewModelTest {

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
    fun testHomeViewModelLoadsCountsAndDistribution() = runBlocking {
        // Populate vocabulary and study items
        val voc1 = Vocabulary("你", "ni3", arrayListOf("you"))
        val voc2 = Vocabulary("好", "hao3", arrayListOf("good"))
        vocRepo.addVocabulary(voc1)
        vocRepo.addVocabulary(voc2)

        studyRepo.recordReviewResult("你", isCorrect = true, now = 1000L)
        studyRepo.recordReviewResult("好", isCorrect = true, now = 1000L)

        val viewModel = HomeViewModel(studyRepo, progressRepo, dispatcher = testDispatcher)

        val state = withTimeout(3000) {
            viewModel.uiState.filter { !it.isLoading }.first()
        }

        assertNotNull(state)
        assertEquals(2, state.totalLearned)
        assertEquals(2, state.boxDistribution[1])
    }

    @Test
    fun testHomeViewModelLoadsBox0AfterResetOrNewVocabulary() = runBlocking {
        // Add vocabulary items
        val voc1 = Vocabulary("你", "ni3", arrayListOf("you"))
        val voc2 = Vocabulary("好", "hao3", arrayListOf("good"))
        vocRepo.addVocabulary(voc1)
        vocRepo.addVocabulary(voc2)

        // Review "你" to Box 1, leave "好" unreviewed (Box 0)
        studyRepo.recordReviewResult("你", isCorrect = true, now = 1000L)

        val viewModel = HomeViewModel(studyRepo, progressRepo, dispatcher = testDispatcher)
        var state = withTimeout(3000) {
            viewModel.uiState.filter { !it.isLoading }.first()
        }

        assertEquals(2, state.totalLearned)
        assertEquals(1, state.boxDistribution[0]) // "好" is unreviewed -> Box 0
        assertEquals(1, state.boxDistribution[1]) // "你" is in Box 1

        // Reset all study scores -> everything falls back to Box 0
        studyRepo.resetAllScores()
        viewModel.loadDashboardData()

        state = withTimeout(3000) {
            viewModel.uiState.filter { !it.isLoading }.first()
        }

        assertEquals(2, state.totalLearned)
        assertEquals(2, state.boxDistribution[0]) // Both "你" and "好" are now in Box 0
        assertEquals(0, state.boxDistribution[1] ?: 0)
    }

    @Test
    fun testHomeViewModelLoadsEmptyDashboardWhenNoData() = runBlocking {
        val viewModel = HomeViewModel(studyRepo, progressRepo, dispatcher = testDispatcher)

        val state = withTimeout(3000) {
            viewModel.uiState.filter { !it.isLoading }.first()
        }

        assertNotNull(state)
        assertEquals(0, state.totalLearned)
        assertEquals(0, state.dueCount)
        assertEquals(0, state.streakDays)
        assertEquals(0, state.boxDistribution[0])
    }

    @Test
    fun testHomeViewModelWithUserPreferencesStreak() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = com.matthieu.zi_app.data.preferences.UserPreferences(context)
        prefs.resetStreak()

        val now = System.currentTimeMillis()
        prefs.recordStudySession(now - 86400000L) // Yesterday
        prefs.recordStudySession(now) // Today -> streak 2

        val viewModel = HomeViewModel(studyRepo, progressRepo, prefs, dispatcher = testDispatcher)

        val state = withTimeout(3000) {
            viewModel.uiState.filter { !it.isLoading }.first()
        }

        assertEquals(2, state.streakDays)
    }

    @Test
    fun testHomeViewModelLoadsAsyncAnalytics() = runBlocking {
        val voc1 = Vocabulary("字", "zi4", arrayListOf("character"))
        vocRepo.addVocabulary(voc1)

        val now = 10_000_000L
        repeat(5) {
            studyRepo.recordReviewResult("字", isCorrect = true, now = now)
        }

        val viewModel = HomeViewModel(
            studyRepository = studyRepo,
            progressRepository = progressRepo,
            leitnerDao = andZiDb.leitnerDao(),
            vocabularyRepository = vocRepo,
            dispatcher = testDispatcher
        )

        // Phase 1: Fast data loaded
        val quickState = withTimeout(3000) {
            viewModel.uiState.filter { it.isQuickDataLoaded }.first()
        }
        assertEquals(1, quickState.totalLearned)

        // Phase 2: Analytics data loaded
        val state = withTimeout(3000) {
            viewModel.uiState.filter { !it.isAnalyticsLoading }.first()
        }
        assertEquals(1, state.wordsKnown)
        assertEquals(1, state.totalWords)
        assertEquals(1, state.distinctCharactersCount)
        assertEquals(1, state.charactersKnown)
        assertEquals(100f, state.overallAccuracy)
        assertEquals(5, state.totalReviewsCount)

        // Select 7D range
        viewModel.selectTimeRange(com.matthieu.zi_app.ui.home.TimeRange.DAYS_7)
        val rangeState = withTimeout(3000) {
            viewModel.uiState.filter { it.selectedTimeRange == com.matthieu.zi_app.ui.home.TimeRange.DAYS_7 }.first()
        }
        assertEquals(com.matthieu.zi_app.ui.home.TimeRange.DAYS_7, rangeState.selectedTimeRange)
    }
}
