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

    @Test
    fun testWordAssemblyTileSelectionAndUndo() = runBlocking {
        vocRepo.addVocabulary(Vocabulary("明天", "ming2tian1", arrayListOf()))
        vocRepo.addVocabulary(Vocabulary("水", "shui3", arrayListOf("water")))
        vocRepo.addVocabulary(Vocabulary("火", "huo3", arrayListOf("fire")))
        vocRepo.addVocabulary(Vocabulary("木", "mu4", arrayListOf("wood")))

        andZiDb.leitnerDao().insertOrUpdateRecord(
            com.matthieu.zi_app.data.local.entity.LeitnerRecordEntity(
                hanzi = "明天",
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

        val targetIndex = state.queue.indexOfFirst { it.quizType == QuizType.WORD_ASSEMBLY }
        while (viewModel.uiState.value.currentIndex < targetIndex) {
            viewModel.nextCard()
        }

        val card = viewModel.uiState.value.currentCard!!
        assertEquals("明天", card.hanzi)
        assertEquals(QuizType.WORD_ASSEMBLY, card.quizType)
        // Verify tiles have distractors from the study list
        assertTrue(card.scrambledTiles.size >= 4)
        assertTrue(card.scrambledTiles.contains("明"))
        assertTrue(card.scrambledTiles.contains("天"))

        // Find tile indices for '明' and '天'
        val mingIdx = card.scrambledTiles.indexOf("明")
        val tianIdx = card.scrambledTiles.indexOf("天")

        // 1. Select first tile
        viewModel.selectAssemblyTile(mingIdx, "明")
        var current = viewModel.uiState.value
        assertEquals(listOf("明"), current.assembledChars)
        assertEquals(listOf(mingIdx), current.usedTileIndices)
        assertFalse(current.isAnswerRevealed)

        // 2. Test Undo
        viewModel.undoAssemblyTile()
        current = viewModel.uiState.value
        assertTrue(current.assembledChars.isEmpty())
        assertTrue(current.usedTileIndices.isEmpty())

        // 3. Complete assembly correctly: '明' then '天'
        viewModel.selectAssemblyTile(mingIdx, "明")
        viewModel.selectAssemblyTile(tianIdx, "天")

        val completedState = withTimeout(3000) {
            viewModel.uiState.filter { it.isAnswerRevealed }.first()
        }
        assertTrue(completedState.isAnswerRevealed)
        assertEquals(true, completedState.isCorrect)
    }

    @Test
    fun testDistractorCountScalesWithLeitnerBox() = runBlocking {
        // Box 0 word
        vocRepo.addVocabulary(Vocabulary("明天", "ming2tian1", arrayListOf("tomorrow")))
        andZiDb.leitnerDao().insertOrUpdateRecord(
            com.matthieu.zi_app.data.local.entity.LeitnerRecordEntity(
                hanzi = "明天",
                box = 0,
                expTime = 0L,
                numTested = 0,
                numWrong = 0,
                streak = 0,
                dateAdded = System.currentTimeMillis()
            )
        )

        // Box 5 word
        vocRepo.addVocabulary(Vocabulary("学习", "xue2xi2", arrayListOf("to study")))
        andZiDb.leitnerDao().insertOrUpdateRecord(
            com.matthieu.zi_app.data.local.entity.LeitnerRecordEntity(
                hanzi = "学习",
                box = 5,
                expTime = 0L,
                numTested = 10,
                numWrong = 1,
                streak = 5,
                dateAdded = System.currentTimeMillis()
            )
        )

        // Additional study characters so higher box can pull more distractors from the study list
        vocRepo.addVocabulary(Vocabulary("水", "shui3", arrayListOf("water")))
        vocRepo.addVocabulary(Vocabulary("火", "huo3", arrayListOf("fire")))
        vocRepo.addVocabulary(Vocabulary("木", "mu4", arrayListOf("wood")))
        vocRepo.addVocabulary(Vocabulary("金", "jin1", arrayListOf("gold")))
        vocRepo.addVocabulary(Vocabulary("土", "tu3", arrayListOf("earth")))
        vocRepo.addVocabulary(Vocabulary("人", "ren2", arrayListOf("person")))
        vocRepo.addVocabulary(Vocabulary("大", "da4", arrayListOf("big")))

        val viewModel = StudyViewModel(studyRepo, vocRepo)
        val state = withTimeout(3000) {
            viewModel.uiState.filter { !it.isLoading && it.queue.isNotEmpty() }.first()
        }

        val box0Card = state.queue.first { it.hanzi == "明天" }
        val box5Card = state.queue.first { it.hanzi == "学习" }

        // Box 5 card should have more tiles/distractors than Box 0 card
        assertTrue(box5Card.scrambledTiles.size > box0Card.scrambledTiles.size)
    }

    @Test
    fun testDistractorsAreSourcedFromStudyListCharacters() = runBlocking {
        // Study list containing specific words and characters
        vocRepo.addVocabulary(Vocabulary("明天", "ming2tian1", arrayListOf("tomorrow")))
        vocRepo.addVocabulary(Vocabulary("水", "shui3", arrayListOf("water")))
        vocRepo.addVocabulary(Vocabulary("火", "huo3", arrayListOf("fire")))
        vocRepo.addVocabulary(Vocabulary("木", "mu4", arrayListOf("wood")))

        val now = System.currentTimeMillis()
        andZiDb.leitnerDao().insertOrUpdateRecord(
            com.matthieu.zi_app.data.local.entity.LeitnerRecordEntity(
                hanzi = "明天",
                box = 0,
                expTime = now - 1000L,
                numTested = 0,
                numWrong = 0,
                streak = 0,
                dateAdded = now
            )
        )
        andZiDb.leitnerDao().insertOrUpdateRecord(com.matthieu.zi_app.data.local.entity.LeitnerRecordEntity(hanzi = "水", box = 1, expTime = now + 100_000L))
        andZiDb.leitnerDao().insertOrUpdateRecord(com.matthieu.zi_app.data.local.entity.LeitnerRecordEntity(hanzi = "火", box = 1, expTime = now + 100_000L))
        andZiDb.leitnerDao().insertOrUpdateRecord(com.matthieu.zi_app.data.local.entity.LeitnerRecordEntity(hanzi = "木", box = 1, expTime = now + 100_000L))
        andZiDb.leitnerDao().insertOrUpdateRecord(com.matthieu.zi_app.data.local.entity.LeitnerRecordEntity(hanzi = "明", box = 1, expTime = now + 100_000L))
        andZiDb.leitnerDao().insertOrUpdateRecord(com.matthieu.zi_app.data.local.entity.LeitnerRecordEntity(hanzi = "天", box = 1, expTime = now + 100_000L))

        val viewModel = StudyViewModel(studyRepo, vocRepo)
        val state = withTimeout(3000) {
            viewModel.uiState.filter { !it.isLoading && it.queue.isNotEmpty() }.first()
        }

        val allStudyChars = setOf("明", "天", "水", "火", "木")
        val assemblyOrClozeCards = state.queue.filter { it.quizType == QuizType.WORD_ASSEMBLY || it.quizType == QuizType.CLOZE }
        assertTrue(assemblyOrClozeCards.isNotEmpty())

        // Verify all tiles generated for assembly/cloze cards are characters from the study list
        for (card in assemblyOrClozeCards) {
            assertTrue(card.scrambledTiles.isNotEmpty())
            for (tile in card.scrambledTiles) {
                assertTrue("Expected tile $tile to be from study characters $allStudyChars", allStudyChars.contains(tile))
            }
        }
    }

    @Test
    fun testWhenNotEnoughStudyCharacters_noExtraDistractorsAreAdded() = runBlocking {
        // Study list containing ONLY "明天" (no other words/characters in vocabulary)
        vocRepo.addVocabulary(Vocabulary("明天", "ming2tian1", arrayListOf("tomorrow")))
        val now = System.currentTimeMillis()
        andZiDb.leitnerDao().insertOrUpdateRecord(
            com.matthieu.zi_app.data.local.entity.LeitnerRecordEntity(
                hanzi = "明天",
                box = 5, // Box 5 normally requests 5 distractors, but study list only has "明" and "天"
                expTime = now - 1000L,
                numTested = 10,
                numWrong = 0,
                streak = 5,
                dateAdded = now
            )
        )
        andZiDb.leitnerDao().insertOrUpdateRecord(com.matthieu.zi_app.data.local.entity.LeitnerRecordEntity(hanzi = "明", box = 1, expTime = now + 100_000L))
        andZiDb.leitnerDao().insertOrUpdateRecord(com.matthieu.zi_app.data.local.entity.LeitnerRecordEntity(hanzi = "天", box = 1, expTime = now + 100_000L))

        val viewModel = StudyViewModel(studyRepo, vocRepo)
        val state = withTimeout(3000) {
            viewModel.uiState.filter { !it.isLoading && it.queue.isNotEmpty() }.first()
        }

        val card = state.queue.first { it.hanzi == "明天" }
        // Because there are no other characters in the study list, NO extra distractors should be added
        assertEquals(2, card.scrambledTiles.size)
        assertTrue(card.scrambledTiles.contains("明"))
        assertTrue(card.scrambledTiles.contains("天"))
    }

    @Test
    fun testConstituentSubCharactersWithoutTranslationsNeverGetEnglishQuiz() = runBlocking {
        // User added compound word "明天" with translation "tomorrow", which adds "明" and "天" to char_pinyin
        vocRepo.addVocabulary(Vocabulary("明天", "ming2tian1", arrayListOf("tomorrow")))

        val now = System.currentTimeMillis()
        val viewModel = StudyViewModel(studyRepo, vocRepo)
        val state = withTimeout(3000) {
            viewModel.uiState.filter { !it.isLoading && it.queue.size == 3 }.first()
        }

        assertEquals(3, state.queue.size)

        // Find cards for subcharacters "明" and "天"
        val mingCard = state.queue.first { it.hanzi == "明" }
        val tianCard = state.queue.first { it.hanzi == "天" }

        // Neither sub-character card should have QuizType.ENGLISH
        assertTrue("Sub-character '明' should not be ENGLISH quiz", mingCard.quizType != QuizType.ENGLISH)
        assertTrue("Sub-character '天' should not be ENGLISH quiz", tianCard.quizType != QuizType.ENGLISH)

        // Sub-characters should be TONE or CLOZE
        assertTrue(mingCard.quizType in listOf(QuizType.TONE, QuizType.CLOZE))
        assertTrue(tianCard.quizType in listOf(QuizType.TONE, QuizType.CLOZE))

        // If TONE quiz, english is empty; if CLOZE quiz, english provides compound word context ("tomorrow")
        if (mingCard.quizType == QuizType.TONE) {
            assertTrue(mingCard.english.isEmpty())
        } else {
            assertEquals(listOf("tomorrow"), mingCard.english)
        }

        if (tianCard.quizType == QuizType.TONE) {
            assertTrue(tianCard.english.isEmpty())
        } else {
            assertEquals(listOf("tomorrow"), tianCard.english)
        }
    }

    @Test
    fun testNeutralToneConstituentSubCharacterGetsClozeQuizInContainingWord() = runBlocking {
        // Compound word with neutral tone sub-character "子" (zi5)
        vocRepo.addVocabulary(Vocabulary("桌子", "zhuo1zi5", arrayListOf("table", "desk")))

        val viewModel = StudyViewModel(studyRepo, vocRepo)
        val state = withTimeout(3000) {
            viewModel.uiState.filter { !it.isLoading && it.queue.size == 3 }.first()
        }

        // Sub-character "子" has neutral tone 5 and no user translations
        val ziCard = state.queue.first { it.hanzi == "子" }
        assertFalse("Neutral tone sub-character cannot be TONE quiz", ziCard.quizType == QuizType.TONE)
        assertFalse("Sub-character without user translations cannot be ENGLISH quiz", ziCard.quizType == QuizType.ENGLISH)

        // Should be CLOZE quiz inside the containing compound word "桌子"
        assertEquals(QuizType.CLOZE, ziCard.quizType)
        assertEquals(listOf("table", "desk"), ziCard.english)
        assertEquals(2, ziCard.wordSlots.size)
        assertEquals("桌", ziCard.wordSlots[0])
        assertEquals("子", ziCard.wordSlots[1])
        assertEquals(1, ziCard.clozeTargetIndex)
    }
}
