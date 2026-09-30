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
        studyRepo.recordReviewResult("字", isCorrect = true, now = now)

        val viewModel = AnalyticsViewModel(progressRepo, studyRepo, andZiDb.leitnerDao())

        val state = withTimeout(3000) {
            viewModel.uiState.filter { !it.isLoading }.first()
        }
        assertFalse(state.isLoading)
        assertEquals(1, state.totalWordsStudied)
        assertEquals(100f, state.overallAccuracy)
        assertEquals(1, state.currentStreak)

        // Select 7D range
        viewModel.selectTimeRange(TimeRange.DAYS_7)
        val rangeState = withTimeout(3000) {
            viewModel.uiState.filter { it.selectedTimeRange == TimeRange.DAYS_7 }.first()
        }
        assertEquals(TimeRange.DAYS_7, rangeState.selectedTimeRange)
    }
}
