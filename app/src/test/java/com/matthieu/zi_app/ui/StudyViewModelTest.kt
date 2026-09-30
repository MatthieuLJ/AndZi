package com.matthieu.zi_app.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.matthieu.zi_app.Vocabulary
import com.matthieu.zi_app.data.local.AndZiDatabase
import com.matthieu.zi_app.data.repository.StudyRepository
import com.matthieu.zi_app.data.repository.VocabularyRepository
import com.matthieu.zi_app.domain.srs.ReviewGrade
import com.matthieu.zi_app.ui.study.StudyViewModel
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class StudyViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var andZiDb: AndZiDatabase
    private lateinit var vocRepo: VocabularyRepository
    private lateinit var studyRepo: StudyRepository

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        andZiDb = AndZiDatabase.createInMemory(context)

        vocRepo = VocabularyRepository(andZiDb.vocabularyDao())
        studyRepo = StudyRepository(andZiDb.leitnerDao())
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
        andZiDb.close()
    }

    @Test
    fun testStudySessionAnswerAndSRSFlow() = runBlocking {
        val voc = Vocabulary("学", "xue2", arrayListOf("to learn", "to study"))
        vocRepo.addVocabulary(voc)

        val viewModel = StudyViewModel(studyRepo, vocRepo)

        val state = withTimeout(3000) {
            viewModel.uiState.filter { !it.isLoading && it.queue.isNotEmpty() }.first()
        }
        assertFalse(state.isLoading)
        assertEquals(1, state.queue.size)
        assertEquals("学", state.currentCard?.hanzi)

        // Submit answer (Tone 2)
        viewModel.submitToneAnswer(2)

        val answeredState = withTimeout(3000) {
            viewModel.uiState.filter { it.isAnswerRevealed }.first()
        }
        assertTrue(answeredState.isAnswerRevealed)
        assertEquals(true, answeredState.isCorrect)

        // Rate card with Good
        viewModel.rateCard(ReviewGrade.GOOD)

        // Session should finish
        val finishedState = withTimeout(3000) {
            viewModel.uiState.filter { it.isSessionFinished }.first()
        }
        assertTrue(finishedState.isSessionFinished)
        assertEquals(1, finishedState.sessionStats.totalReviewed)
        assertEquals(1, finishedState.sessionStats.correctCount)
    }
}
