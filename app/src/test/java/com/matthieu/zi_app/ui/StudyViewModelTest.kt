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

    @Test
    fun testStudySessionWithCardsOnlyInCharPinyinOrDictionary() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dictDb = AndZiDatabase.createInMemory(context)
        val dictRepo = VocabularyRepository(dictDb.vocabularyDao())

        // Add word in dictionary DB
        dictRepo.addVocabulary(Vocabulary("你好", "ni3hao3", arrayListOf("hello")))

        // Add character in local char_pinyin & leitner (due now)
        andZiDb.vocabularyDao().insertCharPinyin(
            com.matthieu.zi_app.data.local.entity.CharPinyinEntity(hanzi = "你", pinyin = "ni3")
        )
        andZiDb.leitnerDao().insertOrUpdateRecord(
            com.matthieu.zi_app.data.local.entity.LeitnerRecordEntity(
                hanzi = "你",
                box = 0,
                expTime = 0L,
                numTested = 0,
                numWrong = 0,
                streak = 0,
                dateAdded = System.currentTimeMillis()
            )
        )

        val viewModel = StudyViewModel(studyRepo, vocRepo, dictRepo)

        val state = withTimeout(3000) {
            viewModel.uiState.filter { !it.isLoading && it.queue.isNotEmpty() }.first()
        }
        assertFalse(state.isLoading)
        assertFalse(state.isSessionFinished)
        assertEquals(1, state.queue.size)
        assertEquals("你", state.currentCard?.hanzi)
        assertEquals("ni3", state.currentCard?.pinyin)

        dictDb.close()
    }

    @Test
    fun testStudySessionFallbackWhenNoEntryInCwordOrDictionary() = runBlocking {
        // Insert only Leitner record directly without any vocabulary or dictionary entry
        andZiDb.leitnerDao().insertOrUpdateRecord(
            com.matthieu.zi_app.data.local.entity.LeitnerRecordEntity(
                hanzi = "字",
                box = 0,
                expTime = 0L,
                numTested = 0,
                numWrong = 0,
                streak = 0,
                dateAdded = System.currentTimeMillis()
            )
        )

        val viewModel = StudyViewModel(studyRepo, vocRepo, null)

        val state = withTimeout(3000) {
            viewModel.uiState.filter { !it.isLoading && it.queue.isNotEmpty() }.first()
        }
        assertFalse(state.isLoading)
        assertFalse(state.isSessionFinished)
        assertEquals(1, state.queue.size)
        assertEquals("字", state.currentCard?.hanzi)
    }
}
