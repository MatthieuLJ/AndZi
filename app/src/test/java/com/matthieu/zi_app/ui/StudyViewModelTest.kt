package com.matthieu.zi_app.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.matthieu.zi_app.Vocabulary
import com.matthieu.zi_app.data.local.AndZiDatabase
import com.matthieu.zi_app.data.repository.StudyRepository
import com.matthieu.zi_app.data.repository.VocabularyRepository
import com.matthieu.zi_app.domain.srs.ReviewGrade
import com.matthieu.zi_app.ui.study.QuizType
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

    @Test
    fun testMultiCharacterWordNeverGetsToneQuiz() = runBlocking {
        vocRepo.addVocabulary(Vocabulary("你好", "ni3hao3", arrayListOf("hello")))
        vocRepo.addVocabulary(Vocabulary("图书馆", "tu2shu1guan3", arrayListOf("library")))

        // Add Leitner due records for both words
        andZiDb.leitnerDao().insertOrUpdateRecord(
            com.matthieu.zi_app.data.local.entity.LeitnerRecordEntity(
                hanzi = "你好",
                box = 0,
                expTime = 0L,
                numTested = 0,
                numWrong = 0,
                streak = 0,
                dateAdded = System.currentTimeMillis()
            )
        )
        andZiDb.leitnerDao().insertOrUpdateRecord(
            com.matthieu.zi_app.data.local.entity.LeitnerRecordEntity(
                hanzi = "图书馆",
                box = 0,
                expTime = 0L,
                numTested = 0,
                numWrong = 0,
                streak = 0,
                dateAdded = System.currentTimeMillis()
            )
        )

        val viewModel = StudyViewModel(studyRepo, vocRepo)
        val state = withTimeout(3000) {
            viewModel.uiState.filter { !it.isLoading && it.queue.isNotEmpty() }.first()
        }

        val multiCharCards = state.queue.filter { it.hanzi.length > 1 }
        assertTrue(multiCharCards.isNotEmpty())
        assertTrue(multiCharCards.none { it.quizType == QuizType.TONE })
        assertTrue(multiCharCards.all { it.quizType in listOf(QuizType.CLOZE, QuizType.WORD_ASSEMBLY, QuizType.ENGLISH) })
    }

    @Test
    fun testNeutralToneCharacterNeverGetsToneQuiz() = runBlocking {
        vocRepo.addVocabulary(Vocabulary("吗", "ma5", arrayListOf("question particle")))
        andZiDb.leitnerDao().insertOrUpdateRecord(
            com.matthieu.zi_app.data.local.entity.LeitnerRecordEntity(
                hanzi = "吗",
                box = 0,
                expTime = 0L,
                numTested = 0,
                numWrong = 0,
                streak = 0,
                dateAdded = System.currentTimeMillis()
            )
        )

        val viewModel = StudyViewModel(studyRepo, vocRepo)
        val state = withTimeout(3000) {
            viewModel.uiState.filter { !it.isLoading && it.queue.isNotEmpty() }.first()
        }

        assertEquals(1, state.queue.size)
        val card = state.queue.first()
        assertEquals("吗", card.hanzi)
        assertFalse(card.quizType == QuizType.TONE)
        assertEquals(QuizType.ENGLISH, card.quizType)
    }

    @Test
    fun testPolyphonicCharacterToneQuizIncludesContextWords() = runBlocking {
        // Setup polyphonic character 长 with chang2 and zhang3
        vocRepo.addVocabulary(Vocabulary("长", "chang2", arrayListOf("long")))
        vocRepo.addVocabulary(Vocabulary("长度", "chang2du4", arrayListOf("length")))
        vocRepo.addVocabulary(Vocabulary("长城", "chang2cheng2", arrayListOf("Great Wall")))

        // Add second pronunciation reading
        andZiDb.vocabularyDao().insertCharPinyin(
            com.matthieu.zi_app.data.local.entity.CharPinyinEntity(hanzi = "长", pinyin = "zhang3")
        )

        // Leitner due for 长
        andZiDb.leitnerDao().insertOrUpdateRecord(
            com.matthieu.zi_app.data.local.entity.LeitnerRecordEntity(
                hanzi = "长",
                box = 0,
                expTime = 0L,
                numTested = 0,
                numWrong = 0,
                streak = 0,
                dateAdded = System.currentTimeMillis()
            )
        )

        val viewModel = StudyViewModel(studyRepo, vocRepo)
        val state = withTimeout(3000) {
            viewModel.uiState.filter { !it.isLoading && it.queue.isNotEmpty() }.first()
        }

        val card = state.queue.first { it.hanzi == "长" }
        assertEquals("长", card.hanzi)
        assertEquals(QuizType.TONE, card.quizType)
        // Check that contextWords are populated with multi-character words
        assertTrue(card.contextWords.isNotEmpty())
        assertTrue(card.contextWords.all { it.length > 1 })
        assertTrue(card.contextWords.contains("长度") || card.contextWords.contains("长城"))
    }
}
