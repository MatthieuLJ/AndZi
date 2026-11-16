package com.matthieu.zi_app.ui

import android.content.Context
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import com.matthieu.zi_app.Vocabulary
import com.matthieu.zi_app.data.local.AndZiDatabase
import com.matthieu.zi_app.data.local.ProgressDatabase
import com.matthieu.zi_app.data.repository.ProgressRepository
import com.matthieu.zi_app.data.repository.StudyRepository
import com.matthieu.zi_app.data.repository.VocabularyRepository
import com.matthieu.zi_app.ui.analytics.AnalyticsViewModel
import com.matthieu.zi_app.ui.home.TimeRange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class AnalyticsViewModelTest {

    private lateinit var testDispatcher: TestDispatcher
    private lateinit var andZiDb: AndZiDatabase
    private lateinit var progressDb: ProgressDatabase
    private lateinit var vocRepo: VocabularyRepository
    private lateinit var studyRepo: StudyRepository
    private lateinit var progressRepo: ProgressRepository
    private var viewModel: AnalyticsViewModel? = null

    @Before
    fun setup() {
        testDispatcher = UnconfinedTestDispatcher()
        Dispatchers.setMain(testDispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        andZiDb = AndZiDatabase.createInMemory(context)
        progressDb = ProgressDatabase.createInMemory(context)

        vocRepo = VocabularyRepository(andZiDb.vocabularyDao())
        studyRepo = StudyRepository(andZiDb.leitnerDao())
        progressRepo = ProgressRepository(progressDb.progressDao(), andZiDb.leitnerDao())
    }

    @After
    fun teardown() = runBlocking {
        viewModel?.viewModelScope?.cancel()
        viewModel = null
        Dispatchers.resetMain()
        andZiDb.close()
        progressDb.close()
    }

    @Test
    fun testAnalyticsViewModelLoadsInitialDistributionAndRetention() = runBlocking {
        val voc1 = Vocabulary("你", "ni3", arrayListOf("you"))
        val voc2 = Vocabulary("好", "hao3", arrayListOf("good"))
        vocRepo.addVocabulary(voc1)
        vocRepo.addVocabulary(voc2)

        studyRepo.recordReviewResult("你", isCorrect = true, now = 1000L)
        studyRepo.recordReviewResult("好", isCorrect = false, now = 1000L)

        val vm = AnalyticsViewModel(studyRepo, progressRepo, leitnerDao = andZiDb.leitnerDao(), dispatcher = testDispatcher)
        viewModel = vm

        val state = withTimeout(3000) {
            vm.uiState.filter { !it.isLoading }.first()
        }

        assertNotNull(state)
        assertEquals(2, state.totalCards)
        assertEquals(2, state.totalTested)
        assertEquals(1, state.totalWrong)
        assertEquals(50.0f, state.overallAccuracy, 0.01f)
        assertEquals(1, state.boxDistribution[1])
        assertEquals(1, state.boxDistribution[0])
    }

    @Test
    fun testAnalyticsViewModelSelectTimeRangeFiltersHistory() = runBlocking {
        val now = System.currentTimeMillis()
        progressRepo.recordProgressSnapshot(
            totalBox1 = 5, totalBox2 = 2, totalBox3 = 1, totalBox4 = 0, totalBox5 = 0,
            expiredBox2 = 0, expiredBox3 = 0, expiredBox4 = 0, expiredBox5 = 0,
            date = now
        )

        val vm = AnalyticsViewModel(studyRepo, progressRepo, leitnerDao = andZiDb.leitnerDao(), dispatcher = testDispatcher)
        viewModel = vm

        vm.selectTimeRange(TimeRange.DAYS_7)
        val state = withTimeout(3000) {
            vm.uiState.filter { !it.isLoading && it.progressRecords.isNotEmpty() }.first()
        }

        assertEquals(TimeRange.DAYS_7, state.selectedTimeRange)
        assertTrue(state.progressRecords.isNotEmpty())
    }

    @Test
    fun testAvailableTimeRanges_whenSpanLessThan30Days_removes30DAnd90D() = runBlocking {
        val now = System.currentTimeMillis()
        // Record 10 days ago and today
        val tenDaysAgo = now - 10L * 24 * 60 * 60 * 1000L
        progressRepo.recordProgressSnapshot(
            totalBox1 = 5, totalBox2 = 0, totalBox3 = 0, totalBox4 = 0, totalBox5 = 0,
            expiredBox2 = 0, expiredBox3 = 0, expiredBox4 = 0, expiredBox5 = 0,
            date = tenDaysAgo
        )
        progressRepo.recordProgressSnapshot(
            totalBox1 = 6, totalBox2 = 0, totalBox3 = 0, totalBox4 = 0, totalBox5 = 0,
            expiredBox2 = 0, expiredBox3 = 0, expiredBox4 = 0, expiredBox5 = 0,
            date = now
        )

        val vm = AnalyticsViewModel(studyRepo, progressRepo, leitnerDao = andZiDb.leitnerDao(), dispatcher = testDispatcher)
        viewModel = vm

        val state = withTimeout(3000) {
            vm.uiState.filter { !it.isLoading }.first()
        }

        assertEquals(listOf(TimeRange.DAYS_7, TimeRange.ALL), state.availableTimeRanges)
    }

    @Test
    fun testAvailableTimeRanges_whenSpanBetween30And90Days_includes30DButRemoves90D() = runBlocking {
        val now = System.currentTimeMillis()
        // Record 45 days ago and today
        val fortyFiveDaysAgo = now - 45L * 24 * 60 * 60 * 1000L
        progressRepo.recordProgressSnapshot(
            totalBox1 = 5, totalBox2 = 0, totalBox3 = 0, totalBox4 = 0, totalBox5 = 0,
            expiredBox2 = 0, expiredBox3 = 0, expiredBox4 = 0, expiredBox5 = 0,
            date = fortyFiveDaysAgo
        )
        progressRepo.recordProgressSnapshot(
            totalBox1 = 6, totalBox2 = 0, totalBox3 = 0, totalBox4 = 0, totalBox5 = 0,
            expiredBox2 = 0, expiredBox3 = 0, expiredBox4 = 0, expiredBox5 = 0,
            date = now
        )

        val vm = AnalyticsViewModel(studyRepo, progressRepo, leitnerDao = andZiDb.leitnerDao(), dispatcher = testDispatcher)
        viewModel = vm

        val state = withTimeout(3000) {
            vm.uiState.filter { !it.isLoading }.first()
        }

        assertEquals(listOf(TimeRange.DAYS_7, TimeRange.DAYS_30, TimeRange.ALL), state.availableTimeRanges)
    }

    @Test
    fun testAvailableTimeRanges_whenSpan90DaysOrMore_includesAllOptions() = runBlocking {
        val now = System.currentTimeMillis()
        // Record 100 days ago and today
        val hundredDaysAgo = now - 100L * 24 * 60 * 60 * 1000L
        progressRepo.recordProgressSnapshot(
            totalBox1 = 5, totalBox2 = 0, totalBox3 = 0, totalBox4 = 0, totalBox5 = 0,
            expiredBox2 = 0, expiredBox3 = 0, expiredBox4 = 0, expiredBox5 = 0,
            date = hundredDaysAgo
        )
        progressRepo.recordProgressSnapshot(
            totalBox1 = 6, totalBox2 = 0, totalBox3 = 0, totalBox4 = 0, totalBox5 = 0,
            expiredBox2 = 0, expiredBox3 = 0, expiredBox4 = 0, expiredBox5 = 0,
            date = now
        )

        val vm = AnalyticsViewModel(studyRepo, progressRepo, leitnerDao = andZiDb.leitnerDao(), dispatcher = testDispatcher)
        viewModel = vm

        val state = withTimeout(3000) {
            vm.uiState.filter { !it.isLoading }.first()
        }

        assertEquals(listOf(TimeRange.DAYS_7, TimeRange.DAYS_30, TimeRange.DAYS_90, TimeRange.ALL), state.availableTimeRanges)
    }
}
