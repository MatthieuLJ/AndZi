package com.matthieu.zi_app.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.matthieu.zi_app.PinyinParser
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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

    private suspend fun collectPresentedCards(viewModel: StudyViewModel, maxSteps: Int = 20): List<com.matthieu.zi_app.ui.study.StudyCard> {
        val collected = mutableListOf<com.matthieu.zi_app.ui.study.StudyCard>()
        var steps = 0
        while (steps < maxSteps) {
            val state = withTimeout(3000) {
                viewModel.uiState.filter { !it.isLoading }.first()
            }
            if (state.isSessionFinished || state.currentCard == null) break
            val card = state.currentCard!!
            collected.add(card)
            viewModel.rateCard(ReviewGrade.GOOD)
            steps++
        }
        return collected
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
        val presentedCards = collectPresentedCards(viewModel)

        val multiCharCards = presentedCards.filter { it.hanzi.length > 1 }
        assertTrue(multiCharCards.isNotEmpty())
        assertTrue(multiCharCards.none { it.quizType == QuizType.TONE })
        assertTrue(multiCharCards.all { it.quizType in listOf(QuizType.CLOZE, QuizType.WORD_ASSEMBLY, QuizType.ENGLISH, QuizType.PAIR_MATCH) })
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
        assertEquals(4, card.mcqOptions.size)
        assertTrue(card.mcqOptions.contains("question particle"))
        assertEquals(4, card.mcqOptions.distinct().size)
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

        // Prioritize 长 as highest box due so it is presented first (index = 0 -> TONE quiz)
        andZiDb.leitnerDao().insertOrUpdateRecord(
            com.matthieu.zi_app.data.local.entity.LeitnerRecordEntity(
                hanzi = "长",
                box = 3,
                expTime = 0L,
                numTested = 3,
                numWrong = 0,
                streak = 3,
                dateAdded = System.currentTimeMillis()
            )
        )

        val viewModel = StudyViewModel(studyRepo, vocRepo)
        val state = withTimeout(3000) {
            viewModel.uiState.filter { !it.isLoading && it.currentCard != null }.first()
        }

        val card = state.currentCard!!
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

        // Prioritize "明天" as highest box due so it is presented first (index = 0 -> WORD_ASSEMBLY)
        andZiDb.leitnerDao().insertOrUpdateRecord(
            com.matthieu.zi_app.data.local.entity.LeitnerRecordEntity(
                hanzi = "明天",
                box = 1,
                expTime = 0L,
                numTested = 0,
                numWrong = 0,
                streak = 0,
                dateAdded = System.currentTimeMillis()
            )
        )

        val viewModel = StudyViewModel(studyRepo, vocRepo)
        val state = withTimeout(3000) {
            viewModel.uiState.filter { !it.isLoading && it.currentCard != null }.first()
        }

        val card = state.currentCard!!
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
    fun testWordAssemblyQuiz_withRepeatedCharacters_deduplicatesTilesAndAllowsMultipleUses() = runBlocking {
        vocRepo.addVocabulary(Vocabulary("妈妈", "ma1ma5", arrayListOf("mother", "mom")))
        vocRepo.addVocabulary(Vocabulary("水", "shui3", arrayListOf("water")))
        vocRepo.addVocabulary(Vocabulary("火", "huo3", arrayListOf("fire")))
        vocRepo.addVocabulary(Vocabulary("木", "mu4", arrayListOf("wood")))

        andZiDb.leitnerDao().insertOrUpdateRecord(
            com.matthieu.zi_app.data.local.entity.LeitnerRecordEntity(
                hanzi = "妈妈",
                box = 1,
                expTime = 0L,
                numTested = 0,
                numWrong = 0,
                streak = 0,
                dateAdded = System.currentTimeMillis()
            )
        )

        val viewModel = StudyViewModel(studyRepo, vocRepo)
        val state = withTimeout(3000) {
            viewModel.uiState.filter { !it.isLoading && it.currentCard != null }.first()
        }

        val card = state.currentCard!!
        assertEquals("妈妈", card.hanzi)
        assertEquals(QuizType.WORD_ASSEMBLY, card.quizType)
        assertEquals(listOf("妈", "妈"), card.wordSlots)

        // Verify that '妈' only appears ONCE in scrambledTiles
        assertEquals(1, card.scrambledTiles.count { it == "妈" })

        val maIdx = card.scrambledTiles.indexOf("妈")
        assertTrue(maIdx >= 0)

        // 1. Select '妈' once
        viewModel.selectAssemblyTile(maIdx, "妈")
        var current = viewModel.uiState.value
        assertEquals(listOf("妈"), current.assembledChars)
        assertEquals(listOf(maIdx), current.usedTileIndices)
        assertFalse(current.isAnswerRevealed)

        // 2. Test undo
        viewModel.undoAssemblyTile()
        current = viewModel.uiState.value
        assertTrue(current.assembledChars.isEmpty())
        assertTrue(current.usedTileIndices.isEmpty())

        // 3. Select '妈' twice (first and second slot)
        viewModel.selectAssemblyTile(maIdx, "妈")
        current = viewModel.uiState.value
        assertEquals(listOf("妈"), current.assembledChars)
        assertEquals(listOf(maIdx), current.usedTileIndices)

        viewModel.selectAssemblyTile(maIdx, "妈")
        val completedState = withTimeout(3000) {
            viewModel.uiState.filter { it.isAnswerRevealed }.first()
        }
        assertTrue(completedState.isAnswerRevealed)
        assertEquals(true, completedState.isCorrect)
        assertEquals(listOf("妈", "妈"), completedState.assembledChars)
        assertEquals(listOf(maIdx, maIdx), completedState.usedTileIndices)
    }

    @Test
    fun testDistractorCountScalesWithLeitnerBox() = runBlocking {
        // Box 0 word (no English so index % 2 == 0 gives WORD_ASSEMBLY)
        vocRepo.addVocabulary(Vocabulary("明天", "ming2tian1", arrayListOf()))
        andZiDb.leitnerDao().insertOrUpdateRecord(
            com.matthieu.zi_app.data.local.entity.LeitnerRecordEntity(
                hanzi = "明天",
                box = 2,
                expTime = 0L,
                numTested = 0,
                numWrong = 0,
                streak = 0,
                dateAdded = System.currentTimeMillis()
            )
        )

        // Box 5 word
        vocRepo.addVocabulary(Vocabulary("学习", "xue2xi2", arrayListOf()))
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
        val presented = collectPresentedCards(viewModel, maxSteps = 5)

        val box2Card = presented.first { it.hanzi == "明天" }
        val box5Card = presented.first { it.hanzi == "学习" }

        // Box 5 card should have more tiles/distractors than Box 2 card
        assertTrue(box5Card.scrambledTiles.size > box2Card.scrambledTiles.size)
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

        val viewModel = StudyViewModel(studyRepo, vocRepo)
        val presentedCards = collectPresentedCards(viewModel)

        assertEquals(3, presentedCards.size)

        // Find cards for subcharacters "明" and "天"
        val mingCard = presentedCards.first { it.hanzi == "明" }
        val tianCard = presentedCards.first { it.hanzi == "天" }

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
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dictDb = AndZiDatabase.createInMemory(context)
        val dictRepo = VocabularyRepository(dictDb.vocabularyDao())

        // Word only in the reference dictionary (NOT in user's study stack)
        dictRepo.addVocabulary(Vocabulary("孩子", "hai2zi5", arrayListOf("child")))

        // Compound words in user's active study stack with neutral tone sub-character "子" (zi5)
        vocRepo.addVocabulary(Vocabulary("桌子", "zhuo1zi5", arrayListOf("table", "desk")))
        vocRepo.addVocabulary(Vocabulary("椅子", "yi3zi5", arrayListOf("chair")))

        val viewModel = StudyViewModel(studyRepo, vocRepo, dictRepo)
        val presentedCards = collectPresentedCards(viewModel)

        // Sub-character "子" has neutral tone 5 and no user translations
        val ziCard = presentedCards.first { it.hanzi == "子" }
        assertFalse("Neutral tone sub-character cannot be TONE quiz", ziCard.quizType == QuizType.TONE)
        assertFalse("Sub-character without user translations cannot be ENGLISH quiz", ziCard.quizType == QuizType.ENGLISH)

        // Should be CLOZE quiz inside a containing compound word ("桌子" or "椅子")
        assertEquals(QuizType.CLOZE, ziCard.quizType)
        assertEquals(2, ziCard.wordSlots.size)
        assertEquals("子", ziCard.wordSlots[1])
        assertEquals(1, ziCard.clozeTargetIndex)

        // Whole word and whole word pinyin must be populated for reveal
        assertTrue(ziCard.revealedHanzi in listOf("桌子", "椅子"))
        assertTrue(ziCard.displayPinyin in listOf("zhuo1zi5", "yi3zi5"))

        // Related study words must contain study stack words using "子" ("桌子", "椅子"), and NOT dictionary-only "孩子"
        val relatedChinese = ziCard.relatedStudyWords.map { it.chinese }
        assertTrue(relatedChinese.contains("桌子"))
        assertTrue(relatedChinese.contains("椅子"))
        assertFalse("Dictionary-only word should not be in relatedStudyWords", relatedChinese.contains("孩子"))

        dictDb.close()
    }

    @Test
    fun testEnglishQuizGeneratesFourOptionsWithActiveVocabularyDistractors() = runBlocking {
        // Add 5 words to active vocabulary
        vocRepo.addVocabulary(Vocabulary("苹果", "ping2guo3", arrayListOf("apple")))
        vocRepo.addVocabulary(Vocabulary("香蕉", "xiang1jiao1", arrayListOf("banana")))
        vocRepo.addVocabulary(Vocabulary("橘子", "ju2zi5", arrayListOf("orange")))
        vocRepo.addVocabulary(Vocabulary("西瓜", "xi1gua1", arrayListOf("watermelon")))
        vocRepo.addVocabulary(Vocabulary("葡萄", "pu2tao5", arrayListOf("grape")))

        val now = System.currentTimeMillis()
        for (w in listOf("苹果", "香蕉", "橘子", "西瓜", "葡萄")) {
            andZiDb.leitnerDao().insertOrUpdateRecord(
                com.matthieu.zi_app.data.local.entity.LeitnerRecordEntity(
                    hanzi = w,
                    box = 1,
                    expTime = now - 1000L,
                    numTested = 0,
                    numWrong = 0,
                    streak = 0,
                    dateAdded = now
                )
            )
        }
        for (char in listOf("苹", "果", "香", "蕉", "橘", "子", "西", "瓜", "葡", "萄")) {
            andZiDb.leitnerDao().insertOrUpdateRecord(
                com.matthieu.zi_app.data.local.entity.LeitnerRecordEntity(
                    hanzi = char,
                    box = 1,
                    expTime = now + 100_000L,
                    numTested = 0,
                    numWrong = 0,
                    streak = 0,
                    dateAdded = now
                )
            )
        }

        // Set constituent single characters to future expiry so only the 5 test words are tested
        val allChars = vocRepo.getAllDistinctHanzi()
        for (c in allChars) {
            andZiDb.leitnerDao().insertOrUpdateRecord(
                com.matthieu.zi_app.data.local.entity.LeitnerRecordEntity(
                    hanzi = c,
                    box = 5,
                    expTime = now + 999999L,
                    numTested = 1,
                    numWrong = 0,
                    streak = 1,
                    dateAdded = now
                )
            )
        }

        val viewModel = StudyViewModel(studyRepo, vocRepo)
        val presentedCards = collectPresentedCards(viewModel, maxSteps = 25)

        val englishCards = presentedCards.filter { it.quizType == QuizType.ENGLISH }
        assertTrue("Should have at least one English MCQ card", englishCards.isNotEmpty())

        val allActiveMeanings = setOf("apple", "banana", "orange", "watermelon", "grape")
        for (card in englishCards) {
            assertEquals("English MCQ must have exactly 4 options", 4, card.mcqOptions.size)
            assertEquals("All options in English MCQ must be unique", 4, card.mcqOptions.distinct().size)
            assertTrue("Options must contain the correct answer", card.mcqOptions.contains(card.english.first()))

            // Verify distractors are sourced from the user's active vocabulary meanings
            val activeDistractors = card.mcqOptions.filter { allActiveMeanings.contains(it) }
            assertTrue("Distractors should come from active vocabulary", activeDistractors.size >= 3)
        }
    }

    @Test
    fun testEnglishQuizSupplementsFromDictionaryWhenStudyWordsAreFew() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dictDb = AndZiDatabase.createInMemory(context)
        val dictRepo = VocabularyRepository(dictDb.vocabularyDao())

        // Populate dictionary with diverse entries
        dictRepo.addVocabulary(Vocabulary("水", "shui3", arrayListOf("water")))
        dictRepo.addVocabulary(Vocabulary("火", "huo3", arrayListOf("fire")))
        dictRepo.addVocabulary(Vocabulary("木", "mu4", arrayListOf("tree; wood")))
        dictRepo.addVocabulary(Vocabulary("山", "shan1", arrayListOf("mountain")))
        dictRepo.addVocabulary(Vocabulary("日", "ri4", arrayListOf("sun; day")))

        // User active vocabulary only has 1 word
        vocRepo.addVocabulary(Vocabulary("猫", "mao1", arrayListOf("cat")))
        val now = System.currentTimeMillis()
        andZiDb.leitnerDao().insertOrUpdateRecord(
            com.matthieu.zi_app.data.local.entity.LeitnerRecordEntity(
                hanzi = "猫",
                box = 0,
                expTime = now - 1000L,
                numTested = 0,
                numWrong = 0,
                streak = 0,
                dateAdded = now
            )
        )

        val viewModel = StudyViewModel(studyRepo, vocRepo, dictRepo)
        val state = withTimeout(3000) {
            viewModel.uiState.filter { !it.isLoading && it.queue.isNotEmpty() }.first()
        }

        val card = state.queue.first { it.hanzi == "猫" }
        assertTrue("mcqOptions should not be empty", card.mcqOptions.isEmpty() || card.mcqOptions.size == 4)
        if (card.quizType == QuizType.ENGLISH) {
            assertEquals(4, card.mcqOptions.size)
            assertTrue(card.mcqOptions.contains("cat"))
            assertEquals(4, card.mcqOptions.distinct().size)
        }

        dictDb.close()
    }

    @Test
    fun testContinuousStudyWithoutBatchLimit_andDynamicNextCardSelection() = runBlocking {
        // Insert 35 distinct due items (exceeding the old 30-card batch limit)
        val now = System.currentTimeMillis()
        for (i in 1..35) {
            val hanzi = (0x4E00 + i).toChar().toString()
            andZiDb.vocabularyDao().insertCharPinyin(
                com.matthieu.zi_app.data.local.entity.CharPinyinEntity(hanzi = hanzi, pinyin = "yi1")
            )
            andZiDb.leitnerDao().insertOrUpdateRecord(
                com.matthieu.zi_app.data.local.entity.LeitnerRecordEntity(
                    hanzi = hanzi,
                    box = 1,
                    expTime = now - (1000L * i),
                    numTested = 1,
                    numWrong = 0,
                    streak = 1,
                    dateAdded = now - 100_000L
                )
            )
        }

        val viewModel = StudyViewModel(studyRepo, vocRepo)

        // Verify only 1 card is loaded at a time (no pre-fetched batch of 30)
        val initialState = withTimeout(3000) {
            viewModel.uiState.filter { !it.isLoading && it.currentCard != null }.first()
        }
        assertEquals(1, initialState.queue.size)
        assertEquals(35, initialState.remainingDueCount)

        // Fail the first card: it should remain due and be re-tested before session finishes
        val firstCardHanzi = initialState.currentCard!!.hanzi
        viewModel.submitToneAnswer(4) // Wrong tone (correct is 1)
        withTimeout(3000) {
            viewModel.uiState.filter { it.isAnswerRevealed }.first()
        }
        viewModel.nextCard()

        // Answer all remaining cards (35 total items, including the 1 failed card that gets re-tested)
        val allPresented = collectPresentedCards(viewModel, maxSteps = 50)

        // We failed 1 card initially and then passed all 35 cards -> 35 more presentations
        assertEquals(35, allPresented.size)
        assertTrue("Failed card must be dynamically re-queued and tested again", allPresented.any { it.hanzi == firstCardHanzi })
        assertTrue("Session should only finish after all 35 due cards are passed", viewModel.uiState.value.isSessionFinished)
    }

    @Test
    fun testEnglishQuizRandomizesTranslationsAndExcludesSameWordTranslationsFromDistractors() = runBlocking {
        val meanings = arrayListOf("alpha meaning", "beta meaning", "gamma meaning")
        // Neutral tone single character with English translations always gets QuizType.ENGLISH
        vocRepo.addVocabulary(Vocabulary("吗", "ma5", meanings))

        val pickedMeanings = mutableSetOf<String>()
        repeat(25) {
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
                viewModel.uiState.filter { !it.isLoading && it.currentCard != null }.first()
            }
            val card = state.currentCard!!
            assertEquals(QuizType.ENGLISH, card.quizType)

            val matchingOptions = card.mcqOptions.filter { it in meanings }
            assertEquals(
                "Exactly one of the word's translations should appear in mcqOptions",
                1,
                matchingOptions.size
            )
            pickedMeanings.add(matchingOptions.first())
        }

        assertTrue(
            "English quiz should pick translations other than just the first one",
            pickedMeanings.any { it != "alpha meaning" }
        )
    }

    @Test
    fun testMarkAnswerAsAcceptableOverridesFailedEnglishQuizToCorrect() = runBlocking {
        vocRepo.addVocabulary(Vocabulary("吗", "ma5", arrayListOf("question particle")))
        val now = System.currentTimeMillis()
        andZiDb.leitnerDao().insertOrUpdateRecord(
            com.matthieu.zi_app.data.local.entity.LeitnerRecordEntity(
                hanzi = "吗",
                box = 2,
                expTime = now - 10_000L,
                numTested = 2,
                numWrong = 0,
                streak = 2,
                dateAdded = now - 100_000L
            )
        )

        val viewModel = StudyViewModel(studyRepo, vocRepo)
        val state = withTimeout(3000) {
            viewModel.uiState.filter { !it.isLoading && it.currentCard != null }.first()
        }
        assertEquals(QuizType.ENGLISH, state.currentCard?.quizType)

        // Submit a synonym answer that wasn't the exact stored translation
        viewModel.submitEnglishAnswer("interrogative particle")

        val wrongState = withTimeout(3000) {
            viewModel.uiState.filter { it.isAnswerRevealed && it.isCorrect == false }.first()
        }
        assertEquals(false, wrongState.isCorrect)
        assertEquals(1, wrongState.sessionStats.totalReviewed)
        assertEquals(0, wrongState.sessionStats.correctCount)
        assertEquals(1, wrongState.sessionStats.lapseCount)

        val demotedLeitner = studyRepo.getLeitnerState("吗")!!
        assertEquals(0, demotedLeitner.box)
        assertEquals(1, demotedLeitner.numWrong)

        // User marks their answer as acceptable
        viewModel.markAnswerAsAcceptable()

        val acceptedState = withTimeout(3000) {
            viewModel.uiState.filter { it.isAnswerRevealed && it.isCorrect == true }.first()
        }
        assertEquals(true, acceptedState.isCorrect)
        assertEquals(1, acceptedState.sessionStats.totalReviewed)
        assertEquals(1, acceptedState.sessionStats.correctCount)
        assertEquals(0, acceptedState.sessionStats.lapseCount)

        val promotedLeitner = studyRepo.getLeitnerState("吗")!!
        assertEquals(3, promotedLeitner.box)
        assertEquals(0, promotedLeitner.numWrong)
        assertEquals(3, promotedLeitner.streak)
    }

    @Test
    fun testToneQuizContextWordsOnlyIncludesStudiedWords_andEmptyWhenNoStudiedCompoundWords() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dictDb = AndZiDatabase.createInMemory(context)
        val dictRepo = VocabularyRepository(dictDb.vocabularyDao())

        // Dictionary has polyphonic readings and compound words for "长"
        dictRepo.addVocabulary(Vocabulary("长", "chang2", arrayListOf("long")))
        dictRepo.addVocabulary(Vocabulary("长", "zhang3", arrayListOf("to grow")))
        dictRepo.addVocabulary(Vocabulary("长度", "chang2du4", arrayListOf("length")))
        dictRepo.addVocabulary(Vocabulary("长城", "chang2cheng2", arrayListOf("Great Wall")))

        // User is ONLY studying the character "长" itself (no compound words in vocRepo)
        vocRepo.addVocabulary(Vocabulary("长", "chang2", arrayListOf("long")))
        andZiDb.leitnerDao().insertOrUpdateRecord(
            com.matthieu.zi_app.data.local.entity.LeitnerRecordEntity(
                hanzi = "长",
                box = 1,
                expTime = 0L,
                numTested = 1,
                numWrong = 0,
                streak = 1,
                dateAdded = System.currentTimeMillis()
            )
        )

        val viewModel = StudyViewModel(studyRepo, vocRepo, dictRepo)
        val state = withTimeout(3000) {
            viewModel.uiState.filter { !it.isLoading && it.currentCard != null }.first()
        }
        val card = state.currentCard!!
        assertEquals("长", card.hanzi)
        assertEquals(QuizType.TONE, card.quizType)
        assertTrue(
            "When no compound words are being studied, contextWords must be empty (not pulled from dictionary)",
            card.contextWords.isEmpty()
        )

        dictDb.close()
    }

    @Test
    fun testAreWordsSimilar_correctlyIdentifiesCommonCharacters() {
        assertTrue(StudyViewModel.areWordsSimilar("明天", "明"))
        assertTrue(StudyViewModel.areWordsSimilar("明天", "天"))
        assertTrue(StudyViewModel.areWordsSimilar("明天", "后天"))
        assertTrue(StudyViewModel.areWordsSimilar("学习", "学校"))
        assertTrue(StudyViewModel.areWordsSimilar("对不起", "起床"))

        assertFalse(StudyViewModel.areWordsSimilar("明天", "学习"))
        assertFalse(StudyViewModel.areWordsSimilar("你好", "世界"))
        assertFalse(StudyViewModel.areWordsSimilar("明", "天"))
        assertFalse(StudyViewModel.areWordsSimilar(null, "学习"))
        assertFalse(StudyViewModel.areWordsSimilar("学习", null))
        assertFalse(StudyViewModel.areWordsSimilar("", "学习"))
        assertFalse(StudyViewModel.areWordsSimilar("学习", ""))
    }

    @Test
    fun testConsecutiveTestsAvoidSimilarWordsWhenAlternativesAvailable() = runBlocking {
        // Add vocabulary items: "明天", "明", "天", "你好"
        vocRepo.addVocabulary(Vocabulary("明天", "ming2tian1", arrayListOf("tomorrow")))
        vocRepo.addVocabulary(Vocabulary("明", "ming2", arrayListOf("bright")))
        vocRepo.addVocabulary(Vocabulary("天", "tian1", arrayListOf("sky; day")))
        vocRepo.addVocabulary(Vocabulary("你好", "ni3hao3", arrayListOf("hello")))

        // Add items to leitner with explicit overdue times:
        // "明天" is most overdue (1), followed by "明" (2), "天" (3), "你好" (4).
        val now = System.currentTimeMillis()
        andZiDb.leitnerDao().insertOrUpdateRecord(
            com.matthieu.zi_app.data.local.entity.LeitnerRecordEntity("明天", 1, now - 40_000L, 1, 0, 1, now)
        )
        andZiDb.leitnerDao().insertOrUpdateRecord(
            com.matthieu.zi_app.data.local.entity.LeitnerRecordEntity("明", 1, now - 30_000L, 1, 0, 1, now)
        )
        andZiDb.leitnerDao().insertOrUpdateRecord(
            com.matthieu.zi_app.data.local.entity.LeitnerRecordEntity("天", 1, now - 20_000L, 1, 0, 1, now)
        )
        andZiDb.leitnerDao().insertOrUpdateRecord(
            com.matthieu.zi_app.data.local.entity.LeitnerRecordEntity("你好", 1, now - 10_000L, 1, 0, 1, now)
        )

        val viewModel = StudyViewModel(studyRepo, vocRepo)

        // Card 1: "明天" is the most overdue card
        val state1 = withTimeout(3000) {
            viewModel.uiState.filter { !it.isLoading && it.currentCard != null }.first()
        }
        val card1 = state1.currentCard!!.hanzi
        assertEquals("明天", card1)

        // Rate Card 1 with AGAIN so it resets to box 0 and stays due
        viewModel.rateCard(ReviewGrade.AGAIN)

        // Card 2:
        // Queue candidates order by overdue time: "明" (-30s), "天" (-20s), "你好" (-10s).
        // Without similarity avoidance, "明" would be picked next because it is most overdue.
        // With similarity avoidance, both "明" and "天" are skipped because they share characters with "明天".
        // "你好" MUST be chosen instead!
        val state2 = withTimeout(3000) {
            viewModel.uiState.filter { !it.isLoading && it.currentCard != null && it.currentCard!!.hanzi != card1 }.first()
        }
        val card2 = state2.currentCard!!.hanzi
        assertEquals("你好", card2)
        assertFalse(
            "Consecutive card $card2 should not share any character with previous card $card1",
            StudyViewModel.areWordsSimilar(card1, card2)
        )

        // Rate Card 2 with AGAIN
        viewModel.rateCard(ReviewGrade.AGAIN)

        // Card 3: Now last tested was "你好". Candidates: "明" (-30s), "天" (-20s).
        // Neither shares any characters with "你好", so the most overdue non-similar candidate "明" is picked!
        val state3 = withTimeout(3000) {
            viewModel.uiState.filter { !it.isLoading && it.currentCard != null && it.currentCard!!.hanzi != card2 }.first()
        }
        val card3 = state3.currentCard!!.hanzi
        assertEquals("明", card3)
        assertFalse(
            "Consecutive card $card3 should not share any character with previous card $card2",
            StudyViewModel.areWordsSimilar(card2, card3)
        )

        // Rate Card 3 with AGAIN
        viewModel.rateCard(ReviewGrade.AGAIN)

        // Card 4: Now last tested was "明". Candidates: "天" (-20s), "你好", "明天".
        // "天" shares NO characters with "明", so "天" is picked!
        val state4 = withTimeout(3000) {
            viewModel.uiState.filter { !it.isLoading && it.currentCard != null && it.currentCard!!.hanzi != card3 }.first()
        }
        val card4 = state4.currentCard!!.hanzi
        assertEquals("天", card4)
        assertFalse(
            "Consecutive card $card4 should not share any character with previous card $card3",
            StudyViewModel.areWordsSimilar(card3, card4)
        )
    }

    @Test
    fun testConsecutiveTestsFallBackWhenAllRemainingCandidatesAreSimilar() = runBlocking {
        // Only "明天" and "明" are in study
        vocRepo.addVocabulary(Vocabulary("明天", "ming2tian1", arrayListOf("tomorrow")))
        vocRepo.addVocabulary(Vocabulary("明", "ming2", arrayListOf("bright")))

        val now = System.currentTimeMillis()
        andZiDb.leitnerDao().insertOrUpdateRecord(
            com.matthieu.zi_app.data.local.entity.LeitnerRecordEntity("明天", 1, now - 20_000L, 1, 0, 1, now)
        )
        andZiDb.leitnerDao().insertOrUpdateRecord(
            com.matthieu.zi_app.data.local.entity.LeitnerRecordEntity("明", 1, now - 10_000L, 1, 0, 1, now)
        )

        val viewModel = StudyViewModel(studyRepo, vocRepo)

        val state1 = withTimeout(3000) {
            viewModel.uiState.filter { !it.isLoading && it.currentCard != null }.first()
        }
        val card1 = state1.currentCard!!.hanzi
        assertEquals("明天", card1)

        // Rate Card 1 with AGAIN
        viewModel.rateCard(ReviewGrade.AGAIN)

        // Even though "明" shares '明' with "明天", it should still be presented as fallback
        val state2 = withTimeout(3000) {
            viewModel.uiState.filter { !it.isLoading && it.currentCard != null && it.currentCard!!.hanzi != card1 }.first()
        }
        val card2 = state2.currentCard!!.hanzi
        assertEquals("明", card2)
    }

    @Test
    fun testStudySessionAnswersRecordDailyStreak() = runBlocking {
        vocRepo.addVocabulary(Vocabulary("猫", "mao1", arrayListOf("cat")))
        studyRepo.recordReviewGrade("猫", ReviewGrade.AGAIN)

        val context = ApplicationProvider.getApplicationContext<Context>()
        val userPreferences = com.matthieu.zi_app.data.preferences.UserPreferences(context)
        userPreferences.resetStreak()
        assertEquals(0, userPreferences.getStreakDays())

        val viewModel = StudyViewModel(studyRepo, vocRepo, userPreferences = userPreferences)

        withTimeout(3000) {
            viewModel.uiState.filter { !it.isLoading && it.currentCard != null }.first()
        }

        // Submit answer (Tone 1)
        viewModel.submitToneAnswer(1)

        withTimeout(3000) {
            viewModel.uiState.filter { it.isAnswerRevealed }.first()
        }

        // Daily streak should now be recorded as 1
        assertEquals(1, userPreferences.getStreakDays())
    }

    @Test
    fun testClozeDistractors_lowBox_onlyKnownStudyCharactersSuggested() = runBlocking {
        vocRepo.addVocabulary(Vocabulary("猫咪", "mao1mi1", arrayListOf("kitty")))
        vocRepo.addVocabulary(Vocabulary("乐曲", "yue4qu3", arrayListOf("music tune")))
        vocRepo.addVocabulary(Vocabulary("小狗", "xiao3gou3", arrayListOf("puppy")))

        val now = System.currentTimeMillis()
        // Low box: Box 1 (not well known)
        // 猫咪 expires earlier so it is tested first at index 0 (WORD_ASSEMBLY), leaving 乐曲 for index 1 (CLOZE)
        andZiDb.leitnerDao().insertOrUpdateRecord(
            com.matthieu.zi_app.data.local.entity.LeitnerRecordEntity(hanzi = "猫咪", box = 1, expTime = now - 2000L)
        )
        andZiDb.leitnerDao().insertOrUpdateRecord(
            com.matthieu.zi_app.data.local.entity.LeitnerRecordEntity(hanzi = "乐曲", box = 1, expTime = now - 1000L)
        )
        andZiDb.leitnerDao().insertOrUpdateRecord(
            com.matthieu.zi_app.data.local.entity.LeitnerRecordEntity(hanzi = "乐", box = 1, expTime = now + 100_000L)
        )
        andZiDb.leitnerDao().insertOrUpdateRecord(
            com.matthieu.zi_app.data.local.entity.LeitnerRecordEntity(hanzi = "曲", box = 1, expTime = now + 100_000L)
        )

        // Add similar characters (unstudied, not in vocabulary) to char_similarity table
        andZiDb.vocabularyDao().insertCharSimilarities(
            listOf(
                com.matthieu.zi_app.data.local.entity.CharSimilarityEntity(
                    hanzi = "乐",
                    similarHanzi = "车",
                    score = 0.550f,
                    sharedComponent = "东-bridge"
                ),
                com.matthieu.zi_app.data.local.entity.CharSimilarityEntity(
                    hanzi = "曲",
                    similarHanzi = "田",
                    score = 0.600f,
                    sharedComponent = "由"
                )
            )
        )

        val viewModel = StudyViewModel(studyRepo, vocRepo)
        val presented = collectPresentedCards(viewModel, maxSteps = 5)
        val card = presented.first { it.quizType == QuizType.CLOZE && it.hanzi == "乐曲" }

        // In Box 1, unstudied characters from CEDICT ("车", "田") should NOT be suggested
        assertFalse(card.scrambledTiles.contains("车"))
        assertFalse(card.scrambledTiles.contains("田"))
        // All distractors should be from the known study list
        val knownChars = setOf("乐", "曲", "猫", "咪", "小", "狗")
        assertTrue(card.scrambledTiles.all { it in knownChars })
    }

    @Test
    fun testClozeDistractors_highBox_suggestsUnstudiedSimilarCharactersFromCedict() = runBlocking {
        vocRepo.addVocabulary(Vocabulary("猫咪", "mao1mi1", arrayListOf("kitty")))
        vocRepo.addVocabulary(Vocabulary("乐曲", "yue4qu3", arrayListOf("music tune")))

        val now = System.currentTimeMillis()
        // High box: Box 5 ("really well known")
        // 猫咪 expires earlier so it is tested first at index 0 (WORD_ASSEMBLY), leaving 乐曲 for index 1 (CLOZE)
        andZiDb.leitnerDao().insertOrUpdateRecord(
            com.matthieu.zi_app.data.local.entity.LeitnerRecordEntity(hanzi = "猫咪", box = 5, expTime = now - 2000L)
        )
        andZiDb.leitnerDao().insertOrUpdateRecord(
            com.matthieu.zi_app.data.local.entity.LeitnerRecordEntity(hanzi = "乐曲", box = 5, expTime = now - 1000L)
        )
        andZiDb.leitnerDao().insertOrUpdateRecord(
            com.matthieu.zi_app.data.local.entity.LeitnerRecordEntity(hanzi = "乐", box = 5, expTime = now + 100_000L)
        )
        andZiDb.leitnerDao().insertOrUpdateRecord(
            com.matthieu.zi_app.data.local.entity.LeitnerRecordEntity(hanzi = "曲", box = 5, expTime = now + 100_000L)
        )

        // Add unstudied similar characters from CEDICT for "乐" and "曲"
        andZiDb.vocabularyDao().insertCharSimilarities(
            listOf(
                com.matthieu.zi_app.data.local.entity.CharSimilarityEntity(
                    hanzi = "乐",
                    similarHanzi = "东",
                    score = 0.800f,
                    sharedComponent = "小"
                ),
                com.matthieu.zi_app.data.local.entity.CharSimilarityEntity(
                    hanzi = "乐",
                    similarHanzi = "车",
                    score = 0.550f,
                    sharedComponent = "东-bridge"
                ),
                com.matthieu.zi_app.data.local.entity.CharSimilarityEntity(
                    hanzi = "曲",
                    similarHanzi = "田",
                    score = 0.600f,
                    sharedComponent = "由"
                )
            )
        )

        val viewModel = StudyViewModel(studyRepo, vocRepo)
        val presented = collectPresentedCards(viewModel, maxSteps = 5)
        val card = presented.first { it.quizType == QuizType.CLOZE && it.hanzi == "乐曲" }

        // For Box 5 (really well known), unstudied similar characters from CEDICT ("东", "车", or "田") ARE suggested!
        val suggestedUnstudied = listOf("东", "车", "田")
        val hasUnstudiedDistractor = card.scrambledTiles.any { it in suggestedUnstudied }
        assertTrue("Expected scrambled tiles to include unstudied CEDICT similar character, but had: ${card.scrambledTiles}", hasUnstudiedDistractor)
    }

    @Test
    fun testClozeDistractors_lowBox_similarCharacterCountCanBeZero() = runBlocking {
        vocRepo.addVocabulary(Vocabulary("猫咪", "mao1mi1", arrayListOf("kitty")))
        vocRepo.addVocabulary(Vocabulary("乐曲", "yue4qu3", arrayListOf("music tune")))
        vocRepo.addVocabulary(Vocabulary("东西", "dong1xi1", arrayListOf("thing")))
        vocRepo.addVocabulary(Vocabulary("小狗", "xiao3gou3", arrayListOf("puppy")))

        val now = System.currentTimeMillis()
        andZiDb.leitnerDao().insertOrUpdateRecord(
            com.matthieu.zi_app.data.local.entity.LeitnerRecordEntity(hanzi = "乐", box = 0, expTime = now + 100_000L)
        )
        andZiDb.leitnerDao().insertOrUpdateRecord(
            com.matthieu.zi_app.data.local.entity.LeitnerRecordEntity(hanzi = "曲", box = 0, expTime = now + 100_000L)
        )

        // "东" is in vocabulary ("东西") and is visually similar to "乐"
        andZiDb.vocabularyDao().insertCharSimilarities(
            listOf(
                com.matthieu.zi_app.data.local.entity.CharSimilarityEntity(
                    hanzi = "乐",
                    similarHanzi = "东",
                    score = 0.800f,
                    sharedComponent = "小"
                )
            )
        )

        // Across multiple Cloze card trials for Box 0, numSimilarTarget can be 0 or 1
        var seenZeroSimilar = false
        var seenOneSimilar = false
        for (i in 0 until 50) {
            // Keep both words due for each trial so the queue always yields 乐曲 at index 1 (CLOZE)
            andZiDb.leitnerDao().insertOrUpdateRecord(
                com.matthieu.zi_app.data.local.entity.LeitnerRecordEntity(hanzi = "猫咪", box = 0, expTime = now - 2000L)
            )
            andZiDb.leitnerDao().insertOrUpdateRecord(
                com.matthieu.zi_app.data.local.entity.LeitnerRecordEntity(hanzi = "乐曲", box = 0, expTime = now - 1000L)
            )
            val viewModel = StudyViewModel(studyRepo, vocRepo)
            val presented = collectPresentedCards(viewModel, maxSteps = 5)
            val card = presented.firstOrNull { it.quizType == QuizType.CLOZE && it.hanzi == "乐曲" }
            if (card != null && card.wordSlots[card.clozeTargetIndex] == "乐") {
                if (!card.scrambledTiles.contains("东")) {
                    seenZeroSimilar = true
                } else {
                    seenOneSimilar = true
                }
                if (seenZeroSimilar && seenOneSimilar) break
            }
        }
        assertTrue("Expected to observe 0 similar characters in at least one Box 0 trial", seenZeroSimilar)
        assertTrue("Expected to observe 1 similar character in at least one Box 0 trial", seenOneSimilar)
    }

    @Test
    fun testConsecutiveTestsAvoidCharactersFromClozeWordAskedAbout() = runBlocking {
        // "乐" is in vocabulary without user English translations, but has a containing compound word "乐曲"
        // At index 1, "乐" gets QuizType.CLOZE with wordSlots = ["乐", "曲"] (asking about "乐曲")
        vocRepo.addVocabulary(Vocabulary("猫咪", "mao1mi1", arrayListOf("kitty")))
        vocRepo.addVocabulary(Vocabulary("乐曲", "yue4qu3", arrayListOf("music tune")))
        andZiDb.vocabularyDao().insertCharPinyin(
            com.matthieu.zi_app.data.local.entity.CharPinyinEntity(hanzi = "乐", pinyin = "yue4")
        )
        // Also add "曲" and an unrelated word "你好"
        vocRepo.addVocabulary(Vocabulary("曲", "qu3", arrayListOf("song; tune")))
        vocRepo.addVocabulary(Vocabulary("你好", "ni3hao3", arrayListOf("hello")))

        val now = System.currentTimeMillis()
        // "猫咪" tested 0th (index 0 -> WORD_ASSEMBLY)
        andZiDb.leitnerDao().insertOrUpdateRecord(
            com.matthieu.zi_app.data.local.entity.LeitnerRecordEntity(hanzi = "猫咪", box = 1, expTime = now - 40_000L)
        )
        // "乐" tested 1st (index 1 -> CLOZE inside "乐曲")
        andZiDb.leitnerDao().insertOrUpdateRecord(
            com.matthieu.zi_app.data.local.entity.LeitnerRecordEntity(hanzi = "乐", box = 1, expTime = now - 30_000L)
        )
        // "曲" is second most overdue remaining (would be tested next without fix)
        andZiDb.leitnerDao().insertOrUpdateRecord(
            com.matthieu.zi_app.data.local.entity.LeitnerRecordEntity(hanzi = "曲", box = 1, expTime = now - 20_000L)
        )
        // "你好" is third overdue remaining (unrelated word)
        andZiDb.leitnerDao().insertOrUpdateRecord(
            com.matthieu.zi_app.data.local.entity.LeitnerRecordEntity(hanzi = "你好", box = 1, expTime = now - 10_000L)
        )

        val viewModel = StudyViewModel(studyRepo, vocRepo)

        // Card 0: "猫咪"
        val state0 = withTimeout(3000) {
            viewModel.uiState.filter { !it.isLoading && it.currentCard != null }.first()
        }
        assertEquals("猫咪", state0.currentCard!!.hanzi)
        viewModel.rateCard(ReviewGrade.GOOD)

        // Card 1: "乐" presented as CLOZE inside "乐曲"
        val state1 = withTimeout(3000) {
            viewModel.uiState.filter { !it.isLoading && it.currentCard != null }.first()
        }
        val card1 = state1.currentCard!!
        assertEquals("乐", card1.hanzi)
        assertEquals(QuizType.CLOZE, card1.quizType)
        assertEquals("乐曲", card1.revealedHanzi)

        // Rate Card 1 with AGAIN
        viewModel.rateCard(ReviewGrade.AGAIN)

        // Card 2:
        // Even though "曲" is more overdue than "你好", "曲" was part of the word "乐曲" asked about in Card 1.
        // Therefore, Card 2 must avoid "曲" and pick the alternative "你好"!
        val state2 = withTimeout(3000) {
            viewModel.uiState.filter { !it.isLoading && it.currentCard != null }.first()
        }
        val card2 = state2.currentCard!!
        assertEquals("你好", card2.hanzi)
        assertFalse(
            "Card 2 should not involve characters from Card 1's tested word ('${card1.revealedHanzi}')",
            StudyViewModel.areWordsSimilar(card2.hanzi, card1.revealedHanzi)
        )
    }

    @Test
    fun testToneAndPinyinQuizzesBothExistAndRotateForSingleCharacters() = runBlocking {
        // Add single character "乐" without user English
        andZiDb.vocabularyDao().insertCharPinyin(
            com.matthieu.zi_app.data.local.entity.CharPinyinEntity(hanzi = "乐", pinyin = "le4")
        )

        andZiDb.leitnerDao().insertOrUpdateRecord(
            com.matthieu.zi_app.data.local.entity.LeitnerRecordEntity(hanzi = "乐", box = 1, expTime = 0L)
        )

        val viewModel = StudyViewModel(studyRepo, vocRepo)

        // Card 1 at index 0 is TONE quiz
        val state1 = withTimeout(20_000) {
            viewModel.uiState.filter { !it.isLoading && it.currentCard != null }.first()
        }
        val card1 = state1.currentCard!!
        assertEquals("乐", card1.hanzi)
        assertEquals(QuizType.TONE, card1.quizType)

        // Submit tone answer (Tone 4)
        viewModel.submitToneAnswer(4)
        val afterCard1 = withTimeout(20_000) {
            viewModel.uiState.filter { it.isAnswerRevealed }.first()
        }
        assertTrue(afterCard1.isCorrect == true)

        // Reset Leitner record for "乐" so it stays due for next card at index 1
        andZiDb.leitnerDao().insertOrUpdateRecord(
            com.matthieu.zi_app.data.local.entity.LeitnerRecordEntity(hanzi = "乐", box = 1, expTime = 0L)
        )
        viewModel.rateCard(ReviewGrade.AGAIN)

        // Card 2 at index 1 is PINYIN quiz!
        val state2 = withTimeout(20_000) {
            viewModel.uiState.filter { !it.isLoading && it.currentCard != null && !it.isAnswerRevealed }.first()
        }
        val card2 = state2.currentCard!!
        assertEquals("乐", card2.hanzi)
        assertEquals(QuizType.PINYIN, card2.quizType)

        // Options should have enough choices (>= 4)
        assertTrue("PINYIN quiz should have at least 4 choices, had: ${card2.mcqOptions.size}", card2.mcqOptions.size >= 4)
        assertTrue("PINYIN quiz options should include correct syllable 'le'", card2.mcqOptions.contains("le"))

        // Submitting Pinyin answer (split selection: syllable "le" and tone 4)
        viewModel.selectPinyinSyllable("le")
        assertFalse(viewModel.uiState.value.isAnswerRevealed)
        assertEquals("le", viewModel.uiState.value.selectedOption)

        viewModel.selectPinyinTone(4)
        val afterCard2 = withTimeout(20_000) {
            viewModel.uiState.filter { it.isAnswerRevealed }.first()
        }
        assertTrue(afterCard2.isCorrect == true)
    }

    @Test
    fun testPinyinQuiz_highBox_mixesInPinyinFromSimilarCharacters() = runBlocking {
        vocRepo.addVocabulary(Vocabulary("乐", "le4", arrayListOf()))
        andZiDb.vocabularyDao().insertCharPinyin(
            com.matthieu.zi_app.data.local.entity.CharPinyinEntity(hanzi = "乐", pinyin = "le4")
        )
        // Add similar characters to char_pinyin: 东 (dong1), 车 (che1)
        andZiDb.vocabularyDao().insertCharPinyin(
            com.matthieu.zi_app.data.local.entity.CharPinyinEntity(hanzi = "东", pinyin = "dong1")
        )
        andZiDb.vocabularyDao().insertCharPinyin(
            com.matthieu.zi_app.data.local.entity.CharPinyinEntity(hanzi = "车", pinyin = "che1")
        )

        // Add similarity link
        andZiDb.vocabularyDao().insertCharSimilarities(
            listOf(
                com.matthieu.zi_app.data.local.entity.CharSimilarityEntity(
                    hanzi = "乐",
                    similarHanzi = "东",
                    score = 0.800f,
                    sharedComponent = "小"
                ),
                com.matthieu.zi_app.data.local.entity.CharSimilarityEntity(
                    hanzi = "乐",
                    similarHanzi = "车",
                    score = 0.550f,
                    sharedComponent = "东-bridge"
                )
            )
        )

        val now = System.currentTimeMillis()
        // Box 5 ("well known") for "乐" and set "东", "车" as not due so "乐" is the only due candidate
        andZiDb.leitnerDao().insertOrUpdateRecord(
            com.matthieu.zi_app.data.local.entity.LeitnerRecordEntity(hanzi = "乐", box = 5, expTime = now - 10_000L)
        )
        andZiDb.leitnerDao().insertOrUpdateRecord(
            com.matthieu.zi_app.data.local.entity.LeitnerRecordEntity(hanzi = "东", box = 5, expTime = now + 100_000L)
        )
        andZiDb.leitnerDao().insertOrUpdateRecord(
            com.matthieu.zi_app.data.local.entity.LeitnerRecordEntity(hanzi = "车", box = 5, expTime = now + 100_000L)
        )

        val viewModel = StudyViewModel(studyRepo, vocRepo)
        // Card at index 0 is TONE, advance to PINYIN at index 1
        val state1 = withTimeout(3000) {
            viewModel.uiState.filter { !it.isLoading && it.currentCard != null }.first()
        }
        assertEquals(QuizType.TONE, state1.currentCard!!.quizType)
        viewModel.nextCard()

        val state2 = withTimeout(3000) {
            viewModel.uiState.filter { !it.isLoading && it.currentCard != null && it.currentCard!!.quizType == QuizType.PINYIN }.first()
        }
        val card = state2.currentCard!!
        assertEquals(QuizType.PINYIN, card.quizType)
        // For Box 5, options count is 6
        assertEquals(6, card.mcqOptions.size)

        // Options must include correct answer "le"
        assertTrue(card.mcqOptions.contains("le"))

        // For well known Box 5, options mix in syllables from visually similar characters ("dong" and/or "che")
        val similarSyllables = listOf("dong", "che")
        val hasSimilarSyllable = card.mcqOptions.any { it in similarSyllables }
        assertTrue(
            "Expected options for Box 5 character '乐' to mix in syllables from similar characters ('东'/'车'), had: ${card.mcqOptions}",
            hasSimilarSyllable
        )
    }

    @Test
    fun testNeutralToneCharacter_neverHasToneQuiz_andPinyinQuizDoesNotRequireTone() = runBlocking {
        // "的" has neutral tone (de5 / tone 5 or 0)
        vocRepo.addVocabulary(Vocabulary("的", "de5", arrayListOf()))
        andZiDb.leitnerDao().insertOrUpdateRecord(
            com.matthieu.zi_app.data.local.entity.LeitnerRecordEntity(hanzi = "的", box = 1, expTime = 0L)
        )

        val viewModel = StudyViewModel(studyRepo, vocRepo)
        val initialCard = withTimeout(3000) {
            viewModel.uiState.filter { !it.isLoading && it.currentCard != null }.first()
        }.currentCard!!

        // 1. First card must be PINYIN (not TONE)
        assertEquals("的", initialCard.hanzi)
        assertEquals(QuizType.PINYIN, initialCard.quizType)
        assertEquals(0, initialCard.tone) // tone is 0 for neutral
        assertTrue(initialCard.mcqOptions.contains("de"))

        // 2. Cycling through cards should NEVER produce a TONE quiz
        for (i in 1..4) {
            viewModel.nextCard()
            val card = withTimeout(3000) {
                viewModel.uiState.filter { !it.isLoading && it.currentCard != null }.first()
            }.currentCard!!
            assertFalse("Neutral tone character should never have TONE quiz", card.quizType == QuizType.TONE)
        }

        // 3. For neutral tone character, selecting syllable alone immediately completes the quiz without tone prompt
        viewModel.selectPinyinSyllable("de")
        val afterSelect = withTimeout(3000) {
            viewModel.uiState.filter { it.isAnswerRevealed }.first()
        }
        assertTrue("Selecting syllable for neutral-tone character should immediately reveal answer", afterSelect.isAnswerRevealed)
        assertEquals(true, afterSelect.isCorrect)
    }

    @Test
    fun testPinyinQuiz_splitSyllableAndTone_selectionOrderAndIncorrectTone() = runBlocking {
        vocRepo.addVocabulary(Vocabulary("水", "shui3", arrayListOf()))
        andZiDb.leitnerDao().insertOrUpdateRecord(
            com.matthieu.zi_app.data.local.entity.LeitnerRecordEntity(hanzi = "水", box = 1, expTime = 0L)
        )

        val viewModel = StudyViewModel(studyRepo, vocRepo)
        // Candidates for Box 1 without English: [TONE, PINYIN]
        val card0 = withTimeout(3000) {
            viewModel.uiState.filter { !it.isLoading && it.currentCard != null }.first()
        }.currentCard!!
        assertEquals(QuizType.TONE, card0.quizType)

        viewModel.nextCard()
        val pinyinCard = withTimeout(3000) {
            viewModel.uiState.filter { !it.isLoading && it.currentCard?.quizType == QuizType.PINYIN }.first()
        }.currentCard!!

        assertEquals("水", pinyinCard.hanzi)
        assertEquals(3, pinyinCard.tone)
        assertEquals("shui", pinyinCard.syllable)

        // Test selecting tone first, then syllable
        viewModel.selectPinyinTone(2) // Incorrect tone (2 instead of 3)
        assertFalse(viewModel.uiState.value.isAnswerRevealed)
        assertEquals(2, viewModel.uiState.value.selectedPinyinTone)

        viewModel.selectPinyinSyllable("shui") // Correct syllable
        val revealedState = withTimeout(3000) {
            viewModel.uiState.filter { it.isAnswerRevealed }.first()
        }
        assertTrue(revealedState.isAnswerRevealed)
        assertEquals(false, revealedState.isCorrect) // Incorrect because tone was wrong!
    }

    @Test
    fun testWordAssembly_wellKnownConstituentCharacter_mixesInSimilarCharactersFromCedict() = runBlocking {
        vocRepo.addVocabulary(Vocabulary("乐曲", "yue4qu3", arrayListOf("music tune")))
        val now = System.currentTimeMillis()
        // "乐" is well known (Box 5)
        andZiDb.leitnerDao().insertOrUpdateRecord(
            com.matthieu.zi_app.data.local.entity.LeitnerRecordEntity(hanzi = "乐", box = 5, expTime = now + 100_000L)
        )
        // "乐曲" is due at index 0 (WORD_ASSEMBLY)
        andZiDb.leitnerDao().insertOrUpdateRecord(
            com.matthieu.zi_app.data.local.entity.LeitnerRecordEntity(hanzi = "乐曲", box = 5, expTime = now - 1000L)
        )
        // Add visual similarities for "乐": "东" and "车" (neither is in user vocabulary)
        andZiDb.vocabularyDao().insertCharSimilarities(
            listOf(
                com.matthieu.zi_app.data.local.entity.CharSimilarityEntity(
                    hanzi = "乐",
                    similarHanzi = "东",
                    score = 0.800f,
                    sharedComponent = "小"
                ),
                com.matthieu.zi_app.data.local.entity.CharSimilarityEntity(
                    hanzi = "乐",
                    similarHanzi = "车",
                    score = 0.550f,
                    sharedComponent = "东-bridge"
                )
            )
        )

        val viewModel = StudyViewModel(studyRepo, vocRepo)
        val state = withTimeout(3000) {
            viewModel.uiState.filter { !it.isLoading && it.currentCard != null }.first()
        }
        val card = state.currentCard!!
        assertEquals("乐曲", card.hanzi)
        assertEquals(QuizType.WORD_ASSEMBLY, card.quizType)

        // For well known Box 5 character, tiles must mix in visually similar character from CEDICT
        val similarChars = listOf("东", "车")
        val hasSimilarDistractor = card.scrambledTiles.any { it in similarChars }
        assertTrue(
            "Expected word assembly scrambled tiles for '乐曲' to include similar characters for '乐' ('东'/'车'), had: ${card.scrambledTiles}",
            hasSimilarDistractor
        )
    }

    @Test
    fun testWordAssembly_lowBoxConstituentCharacter_avoidsUnstudiedSimilarCharacters() = runBlocking {
        vocRepo.addVocabulary(Vocabulary("乐曲", "yue4qu3", arrayListOf("music tune")))
        vocRepo.addVocabulary(Vocabulary("小狗", "xiao3gou3", arrayListOf("puppy")))
        vocRepo.addVocabulary(Vocabulary("大马", "da4ma3", arrayListOf("big horse")))

        val now = System.currentTimeMillis()
        // Low box: Box 0 (unlearned)
        andZiDb.leitnerDao().insertOrUpdateRecord(
            com.matthieu.zi_app.data.local.entity.LeitnerRecordEntity(hanzi = "乐", box = 0, expTime = now + 100_000L)
        )
        andZiDb.leitnerDao().insertOrUpdateRecord(
            com.matthieu.zi_app.data.local.entity.LeitnerRecordEntity(hanzi = "曲", box = 0, expTime = now + 100_000L)
        )
        andZiDb.leitnerDao().insertOrUpdateRecord(
            com.matthieu.zi_app.data.local.entity.LeitnerRecordEntity(hanzi = "小狗", box = 0, expTime = now + 100_000L)
        )
        andZiDb.leitnerDao().insertOrUpdateRecord(
            com.matthieu.zi_app.data.local.entity.LeitnerRecordEntity(hanzi = "小", box = 0, expTime = now + 100_000L)
        )
        andZiDb.leitnerDao().insertOrUpdateRecord(
            com.matthieu.zi_app.data.local.entity.LeitnerRecordEntity(hanzi = "狗", box = 0, expTime = now + 100_000L)
        )
        andZiDb.leitnerDao().insertOrUpdateRecord(
            com.matthieu.zi_app.data.local.entity.LeitnerRecordEntity(hanzi = "大马", box = 0, expTime = now + 100_000L)
        )
        andZiDb.leitnerDao().insertOrUpdateRecord(
            com.matthieu.zi_app.data.local.entity.LeitnerRecordEntity(hanzi = "大", box = 0, expTime = now + 100_000L)
        )
        andZiDb.leitnerDao().insertOrUpdateRecord(
            com.matthieu.zi_app.data.local.entity.LeitnerRecordEntity(hanzi = "马", box = 0, expTime = now + 100_000L)
        )
        andZiDb.leitnerDao().insertOrUpdateRecord(
            com.matthieu.zi_app.data.local.entity.LeitnerRecordEntity(hanzi = "乐曲", box = 0, expTime = now - 1000L)
        )

        // "东" and "车" are similar to "乐", but NOT in user study list
        andZiDb.vocabularyDao().insertCharSimilarities(
            listOf(
                com.matthieu.zi_app.data.local.entity.CharSimilarityEntity(
                    hanzi = "乐",
                    similarHanzi = "东",
                    score = 0.800f,
                    sharedComponent = "小"
                ),
                com.matthieu.zi_app.data.local.entity.CharSimilarityEntity(
                    hanzi = "乐",
                    similarHanzi = "车",
                    score = 0.550f,
                    sharedComponent = "东-bridge"
                )
            )
        )

        val viewModel = StudyViewModel(studyRepo, vocRepo)
        val state = withTimeout(3000) {
            viewModel.uiState.filter { !it.isLoading && it.currentCard != null }.first()
        }
        val card = state.currentCard!!
        assertEquals("乐曲", card.hanzi)
        assertEquals(QuizType.WORD_ASSEMBLY, card.quizType)

        // Low box characters must NOT include unstudied characters from CEDICT ("东", "车")
        val unstudiedCedict = listOf("东", "车")
        val hasUnstudied = card.scrambledTiles.any { it in unstudiedCedict }
        assertFalse(
            "Low box characters should not receive unstudied CEDICT distractors, had: ${card.scrambledTiles}",
            hasUnstudied
        )
    }

    @Test
    fun testToneQuiz_answerWithoutRevealingContextWords_getsEasyBonusScore() = runBlocking {
        vocRepo.addVocabulary(Vocabulary("长", "chang2", arrayListOf("long")))
        vocRepo.addVocabulary(Vocabulary("长度", "chang2du4", arrayListOf("length")))
        vocRepo.addVocabulary(Vocabulary("长城", "chang2cheng2", arrayListOf("Great Wall")))

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
        // Mark other constituent characters and compound words as not due so "长" is selected
        for (other in listOf("城", "度", "长城", "长度")) {
            andZiDb.leitnerDao().insertOrUpdateRecord(
                com.matthieu.zi_app.data.local.entity.LeitnerRecordEntity(
                    hanzi = other, box = 5, expTime = Long.MAX_VALUE, numTested = 5, numWrong = 0, streak = 5, dateAdded = 0L
                )
            )
        }

        val viewModel = StudyViewModel(studyRepo, vocRepo)
        val state = withTimeout(3000) {
            viewModel.uiState.filter { !it.isLoading && it.currentCard != null }.first()
        }
        val card = state.currentCard!!
        assertEquals("长", card.hanzi)
        assertEquals(QuizType.TONE, card.quizType)
        assertTrue(card.contextWords.isNotEmpty())
        assertFalse(state.isContextWordsRevealed)

        // Submit correct tone (2) WITHOUT revealing context words
        viewModel.submitToneAnswer(2)

        val answeredState = withTimeout(3000) {
            viewModel.uiState.filter { it.isAnswerRevealed }.first()
        }
        assertTrue(answeredState.isCorrect == true)

        // EASY review promotes Box 0 card directly to Box 2
        val leitnerState = studyRepo.getLeitnerState("长")!!
        assertEquals(2, leitnerState.box)
        assertEquals(1, leitnerState.streak)
    }

    @Test
    fun testToneQuiz_answerAfterRevealingContextWords_getsNormalGoodScore() = runBlocking {
        vocRepo.addVocabulary(Vocabulary("长", "chang2", arrayListOf("long")))
        vocRepo.addVocabulary(Vocabulary("长度", "chang2du4", arrayListOf("length")))
        vocRepo.addVocabulary(Vocabulary("长城", "chang2cheng2", arrayListOf("Great Wall")))

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
        // Mark other constituent characters and compound words as not due so "长" is selected
        for (other in listOf("城", "度", "长城", "长度")) {
            andZiDb.leitnerDao().insertOrUpdateRecord(
                com.matthieu.zi_app.data.local.entity.LeitnerRecordEntity(
                    hanzi = other, box = 5, expTime = Long.MAX_VALUE, numTested = 5, numWrong = 0, streak = 5, dateAdded = 0L
                )
            )
        }

        val viewModel = StudyViewModel(studyRepo, vocRepo)
        val state = withTimeout(3000) {
            viewModel.uiState.filter { !it.isLoading && it.currentCard != null }.first()
        }
        val card = state.currentCard!!
        assertEquals("长", card.hanzi)
        assertEquals(QuizType.TONE, card.quizType)
        assertTrue(card.contextWords.isNotEmpty())
        assertFalse(state.isContextWordsRevealed)

        // Explicitly reveal context words before answering
        viewModel.revealContextWords()
        val revealedState = viewModel.uiState.value
        assertTrue(revealedState.isContextWordsRevealed)

        // Submit correct tone (2) WITH context words revealed
        viewModel.submitToneAnswer(2)

        val answeredState = withTimeout(3000) {
            viewModel.uiState.filter { it.isAnswerRevealed }.first()
        }
        assertTrue(answeredState.isCorrect == true)

        // Normal GOOD review promotes Box 0 card to Box 1 with streak = 1
        val leitnerState = studyRepo.getLeitnerState("长")!!
        assertEquals(1, leitnerState.box)
        assertEquals(1, leitnerState.streak)
    }

    @Test
    fun testPinyinQuiz_contextWordsScoringBonus() = runBlocking {
        // Setup a single character tested via Pinyin Quiz with context words
        vocRepo.addVocabulary(Vocabulary("行", "xing2", arrayListOf("to walk", "capable")))
        vocRepo.addVocabulary(Vocabulary("行动", "xing2dong4", arrayListOf("action")))

        andZiDb.leitnerDao().insertOrUpdateRecord(
            com.matthieu.zi_app.data.local.entity.LeitnerRecordEntity(
                hanzi = "行",
                box = 1,
                expTime = 0L,
                numTested = 1,
                numWrong = 0,
                streak = 1,
                dateAdded = System.currentTimeMillis()
            )
        )

        val viewModel = StudyViewModel(studyRepo, vocRepo)
        val state = withTimeout(3000) {
            viewModel.uiState.filter { !it.isLoading && it.currentCard != null }.first()
        }
        val card = state.currentCard!!
        assertEquals("行", card.hanzi)

        if (card.quizType == QuizType.PINYIN && card.contextWords.isNotEmpty()) {
            assertFalse(state.isContextWordsRevealed)
            // Answer without revealing context words -> EASY bonus
            viewModel.submitPinyinAnswer("xíng")

            val answeredState = withTimeout(3000) {
                viewModel.uiState.filter { it.isAnswerRevealed }.first()
            }
            assertTrue(answeredState.isCorrect == true)

            val leitnerState = studyRepo.getLeitnerState("行")!!
            // Easy promotion from Box 1 gives Box 2 with streak + 2 (1 + 2 = 3 -> streak reset to 0 and numWrong reduced)
            assertEquals(2, leitnerState.box)
        }
    }

    @Test
    fun testPinyinQuiz_relatedStudyWords_filtersForSpecificPinyin() = runBlocking {
        // Setup polyphonic character "长" (chang2 / zhang3)
        // Add "长" (chang2, "long"), compound "长城" (chang2cheng2, "Great Wall"), and compound "长大" (zhang3da4, "to grow up")
        vocRepo.addVocabulary(Vocabulary("长", "chang2", arrayListOf("long")))
        vocRepo.addVocabulary(Vocabulary("长城", "chang2cheng2", arrayListOf("Great Wall")))
        vocRepo.addVocabulary(Vocabulary("长大", "zhang3da4", arrayListOf("to grow up")))

        andZiDb.leitnerDao().insertOrUpdateRecord(
            com.matthieu.zi_app.data.local.entity.LeitnerRecordEntity(
                hanzi = "长",
                box = 1,
                expTime = 0L
            )
        )

        val viewModel = StudyViewModel(studyRepo, vocRepo)
        val card = withTimeout(3000) {
            viewModel.uiState.filter { !it.isLoading && it.currentCard != null }.first()
        }.currentCard!!

        val pinyinCard = if (card.quizType == QuizType.PINYIN) {
            card
        } else {
            var current = card
            for (i in 1..10) {
                viewModel.nextCard()
                current = withTimeout(3000) {
                    viewModel.uiState.filter { !it.isLoading && it.currentCard != null }.first()
                }.currentCard!!
                if (current.quizType == QuizType.PINYIN && current.hanzi == "长") break
            }
            current
        }

        if (pinyinCard.quizType == QuizType.PINYIN && pinyinCard.hanzi == "长") {
            val relatedWords = pinyinCard.relatedStudyWords.map { it.chinese }
            assertTrue("Related study words should contain '长'", relatedWords.contains("长"))
            assertTrue("Related study words for chang2 should contain '长城'", relatedWords.contains("长城"))
            assertFalse("Related study words for chang2 MUST NOT contain '长大' (zhang3)", relatedWords.contains("长大"))
        }
    }

    @Test
    fun testWordAssembly_whenWrongWordExistsInDictionary_marksAlternativeValidAndCanBeAccepted() = runBlocking {
        vocRepo.addVocabulary(Vocabulary("外国", "wai4guo2", arrayListOf("foreign country", "foreign")))
        vocRepo.addVocabulary(Vocabulary("国外", "guo2wai4", arrayListOf("abroad", "external")))

        val now = System.currentTimeMillis()
        andZiDb.leitnerDao().insertOrUpdateRecord(
            com.matthieu.zi_app.data.local.entity.LeitnerRecordEntity(hanzi = "外国", box = 1, expTime = now - 1000L)
        )
        andZiDb.leitnerDao().insertOrUpdateRecord(
            com.matthieu.zi_app.data.local.entity.LeitnerRecordEntity(hanzi = "国外", box = 1, expTime = now + 100000L)
        )

        val viewModel = StudyViewModel(studyRepo, vocRepo)
        val initialCard = withTimeout(3000) {
            viewModel.uiState.filter { !it.isLoading && it.currentCard != null }.first()
        }.currentCard!!

        var card = initialCard
        var steps = 0
        while ((card.quizType != QuizType.WORD_ASSEMBLY || card.hanzi != "外国") && steps < 10) {
            viewModel.nextCard()
            card = withTimeout(3000) {
                viewModel.uiState.filter { !it.isLoading && it.currentCard != null }.first()
            }.currentCard!!
            steps++
        }

        assertEquals("外国", card.hanzi)
        assertEquals(QuizType.WORD_ASSEMBLY, card.quizType)

        val guoIndex = card.scrambledTiles.indexOf("国")
        val waiIndex = card.scrambledTiles.indexOf("外")
        assertTrue(guoIndex >= 0 && waiIndex >= 0)

        viewModel.selectAssemblyTile(guoIndex, "国")
        viewModel.selectAssemblyTile(waiIndex, "外")

        val stateAfter = withTimeout(3000) {
            viewModel.uiState.filter { it.isAnswerRevealed }.first()
        }

        assertEquals(false, stateAfter.isCorrect)
        assertEquals("国外", stateAfter.selectedOption)
        assertTrue("Alternative word '国外' exists so isAlternativeWordValid must be true", stateAfter.isAlternativeWordValid)
        assertNotNull(stateAfter.alternativeWordMeaning)
        assertTrue(stateAfter.alternativeWordMeaning!!.contains("abroad") || stateAfter.alternativeWordMeaning!!.contains("external"))

        viewModel.markAnswerAsAcceptable()

        val stateAccepted = withTimeout(3000) {
            viewModel.uiState.filter { it.isCorrect == true }.first()
        }
        assertEquals(true, stateAccepted.isCorrect)

        val leitner = studyRepo.getLeitnerState("外国")!!
        assertEquals(2, leitner.box)
    }

    @Test
    fun testWordAssembly_whenWrongWordDoesNotExist_isAlternativeValidIsFalse() = runBlocking {
        vocRepo.addVocabulary(Vocabulary("外国", "wai4guo2", arrayListOf("foreign country")))
        vocRepo.addVocabulary(Vocabulary("吃饭", "chi1fan4", arrayListOf("eat food")))

        val now = System.currentTimeMillis()
        andZiDb.leitnerDao().insertOrUpdateRecord(
            com.matthieu.zi_app.data.local.entity.LeitnerRecordEntity(hanzi = "外国", box = 1, expTime = now - 1000L)
        )
        andZiDb.leitnerDao().insertOrUpdateRecord(
            com.matthieu.zi_app.data.local.entity.LeitnerRecordEntity(hanzi = "吃饭", box = 1, expTime = now + 100000L)
        )

        val viewModel = StudyViewModel(studyRepo, vocRepo)
        var card = withTimeout(3000) {
            viewModel.uiState.filter { !it.isLoading && it.currentCard != null }.first()
        }.currentCard!!

        var steps = 0
        while ((card.quizType != QuizType.WORD_ASSEMBLY || card.hanzi != "外国") && steps < 10) {
            viewModel.nextCard()
            card = withTimeout(3000) {
                viewModel.uiState.filter { !it.isLoading && it.currentCard != null }.first()
            }.currentCard!!
            steps++
        }

        val waiIndex = card.scrambledTiles.indexOf("外")
        val chiIndex = card.scrambledTiles.indexOf("吃")
        if (chiIndex >= 0 && waiIndex >= 0) {
            viewModel.selectAssemblyTile(chiIndex, "吃")
            viewModel.selectAssemblyTile(waiIndex, "外")

            val stateAfter = withTimeout(3000) {
                viewModel.uiState.filter { it.isAnswerRevealed }.first()
            }

            assertEquals(false, stateAfter.isCorrect)
            assertFalse("Nonsense word '吃外' does not exist so isAlternativeWordValid must be false", stateAfter.isAlternativeWordValid)
            assertNull(stateAfter.alternativeWordMeaning)
        }
    }

    @Test
    fun testPairMatchQuiz_generatesDistinctTilesAndSupportsInteractiveMatching() = runBlocking {
        vocRepo.addVocabulary(Vocabulary("飞机", "fei1ji1", arrayListOf("airplane")))
        vocRepo.addVocabulary(Vocabulary("苹果", "ping2guo3", arrayListOf("apple")))
        vocRepo.addVocabulary(Vocabulary("汽车", "qi4che1", arrayListOf("car")))

        val now = System.currentTimeMillis()
        andZiDb.leitnerDao().insertOrUpdateRecord(
            com.matthieu.zi_app.data.local.entity.LeitnerRecordEntity(hanzi = "飞机", box = 1, expTime = now - 1000L)
        )
        // Keep companion words unexpired in queue so only 飞机 is tested as primary
        andZiDb.leitnerDao().insertOrUpdateRecord(
            com.matthieu.zi_app.data.local.entity.LeitnerRecordEntity(hanzi = "苹果", box = 1, expTime = now + 100000L)
        )
        andZiDb.leitnerDao().insertOrUpdateRecord(
            com.matthieu.zi_app.data.local.entity.LeitnerRecordEntity(hanzi = "汽车", box = 1, expTime = now + 100000L)
        )
        // Set constituent chars unexpired too
        for (char in listOf("飞", "机", "苹", "果", "汽", "车")) {
            andZiDb.leitnerDao().insertOrUpdateRecord(
                com.matthieu.zi_app.data.local.entity.LeitnerRecordEntity(hanzi = char, box = 1, expTime = now + 100000L)
            )
        }

        val viewModel = StudyViewModel(studyRepo, vocRepo)
        var state = withTimeout(3000) {
            viewModel.uiState.filter { !it.isLoading && it.currentCard != null }.first()
        }
        var card = state.currentCard!!

        // Advance until PAIR_MATCH
        var steps = 0
        while (card.quizType != QuizType.PAIR_MATCH && steps < 10) {
            viewModel.nextCard()
            state = withTimeout(3000) {
                viewModel.uiState.filter { !it.isLoading && it.currentCard != null }.first()
            }
            card = state.currentCard!!
            steps++
        }

        assertEquals(QuizType.PAIR_MATCH, card.quizType)
        assertEquals(3, card.matchPairs.size)
        assertEquals(setOf("飞", "苹", "汽"), card.leftMatchTiles.toSet())
        assertEquals(setOf("机", "果", "车"), card.rightMatchTiles.toSet())

        // 1. Mismatch test: pair 飞 with 车
        val leftFeiIdx = card.leftMatchTiles.indexOf("飞")
        val rightCheIdx = card.rightMatchTiles.indexOf("车")
        viewModel.selectMatchLeftTile(leftFeiIdx)
        assertEquals(leftFeiIdx, viewModel.uiState.value.selectedLeftMatchIndex)
        viewModel.selectMatchRightTile(rightCheIdx)

        // Error shake triggered, selections cleared, words marked as failed
        val afterMismatch = viewModel.uiState.value
        assertEquals(1, afterMismatch.matchErrorShakeTrigger)
        assertEquals(null, afterMismatch.selectedLeftMatchIndex)
        assertEquals(null, afterMismatch.selectedRightMatchIndex)
        assertTrue(afterMismatch.failedMatchWords.contains("飞机"))
        assertTrue(afterMismatch.failedMatchWords.contains("汽车"))
        assertTrue(afterMismatch.matchedWords.isEmpty())

        // 2. Correct match: 飞 + 机
        val rightJiIdx = card.rightMatchTiles.indexOf("机")
        viewModel.selectMatchLeftTile(leftFeiIdx)
        viewModel.selectMatchRightTile(rightJiIdx)
        assertTrue(viewModel.uiState.value.matchedWords.contains("飞机"))

        // 3. Correct match: 苹 + 果
        val leftPingIdx = card.leftMatchTiles.indexOf("苹")
        val rightGuoIdx = card.rightMatchTiles.indexOf("果")
        viewModel.selectMatchLeftTile(leftPingIdx)
        viewModel.selectMatchRightTile(rightGuoIdx)
        assertTrue(viewModel.uiState.value.matchedWords.contains("苹果"))

        // 4. Correct match: 汽 + 车
        val leftQiIdx = card.leftMatchTiles.indexOf("汽")
        viewModel.selectMatchLeftTile(leftQiIdx)
        viewModel.selectMatchRightTile(rightCheIdx)

        val revealedState = withTimeout(3000) {
            viewModel.uiState.filter { it.isAnswerRevealed }.first()
        }
        assertTrue(revealedState.isAnswerRevealed)
        // Failed because prior error occurred on 飞机
        assertEquals(false, revealedState.isCorrect)
    }

    @Test
    fun testPairMatchQuiz_allCorrectPromotesCard() = runBlocking {
        vocRepo.addVocabulary(Vocabulary("飞机", "fei1ji1", arrayListOf("airplane")))
        vocRepo.addVocabulary(Vocabulary("苹果", "ping2guo3", arrayListOf("apple")))
        vocRepo.addVocabulary(Vocabulary("汽车", "qi4che1", arrayListOf("car")))

        val now = System.currentTimeMillis()
        andZiDb.leitnerDao().insertOrUpdateRecord(
            com.matthieu.zi_app.data.local.entity.LeitnerRecordEntity(hanzi = "飞机", box = 1, expTime = now - 1000L)
        )
        andZiDb.leitnerDao().insertOrUpdateRecord(
            com.matthieu.zi_app.data.local.entity.LeitnerRecordEntity(hanzi = "苹果", box = 1, expTime = now + 100000L)
        )
        andZiDb.leitnerDao().insertOrUpdateRecord(
            com.matthieu.zi_app.data.local.entity.LeitnerRecordEntity(hanzi = "汽车", box = 1, expTime = now + 100000L)
        )
        for (char in listOf("飞", "机", "苹", "果", "汽", "车")) {
            andZiDb.leitnerDao().insertOrUpdateRecord(
                com.matthieu.zi_app.data.local.entity.LeitnerRecordEntity(hanzi = char, box = 1, expTime = now + 100000L)
            )
        }

        val viewModel = StudyViewModel(studyRepo, vocRepo)
        var state = withTimeout(3000) {
            viewModel.uiState.filter { !it.isLoading && it.currentCard != null }.first()
        }
        var card = state.currentCard!!

        var steps = 0
        while (card.quizType != QuizType.PAIR_MATCH && steps < 10) {
            viewModel.nextCard()
            state = withTimeout(3000) {
                viewModel.uiState.filter { !it.isLoading && it.currentCard != null }.first()
            }
            card = state.currentCard!!
            steps++
        }

        assertEquals(QuizType.PAIR_MATCH, card.quizType)

        // Match all 3 pairs with zero errors
        for (pair in card.matchPairs) {
            val lIdx = card.leftMatchTiles.indexOf(pair.firstChar)
            val rIdx = card.rightMatchTiles.indexOf(pair.secondChar)
            viewModel.selectMatchLeftTile(lIdx)
            viewModel.selectMatchRightTile(rIdx)
        }

        val revealedState = withTimeout(3000) {
            viewModel.uiState.filter { it.isAnswerRevealed }.first()
        }
        assertTrue(revealedState.isAnswerRevealed)
        assertEquals(true, revealedState.isCorrect)

        // Primary word in test is promoted to Box 2
        val updated = studyRepo.getLeitnerState("飞机")!!
        assertEquals(2, updated.box)

        // Non-due companion words have their timer refreshed without updating box
        assertEquals(1, studyRepo.getLeitnerState("苹果")?.box)
        assertTrue(studyRepo.getLeitnerState("苹果")!!.expTime > now)
        assertEquals(1, studyRepo.getLeitnerState("汽车")?.box)
        assertTrue(studyRepo.getLeitnerState("汽车")!!.expTime > now)

        // All characters used or displayed have their timer refreshed without updating box
        for (char in listOf("飞", "机", "苹", "果", "汽", "车")) {
            assertEquals(1, studyRepo.getLeitnerState(char)?.box)
            assertTrue(studyRepo.getLeitnerState(char)!!.expTime > now)
        }
    }

    @Test
    fun testPinyinQuiz_toneOnlyMistake_appliesReviewGradeHard_preservesBox() = runBlocking {
        vocRepo.addVocabulary(Vocabulary("学", "xue2", arrayListOf("to learn")))
        val now = System.currentTimeMillis()
        andZiDb.leitnerDao().insertOrUpdateRecord(
            com.matthieu.zi_app.data.local.entity.LeitnerRecordEntity(
                hanzi = "学",
                box = 3,
                expTime = now - 1000L,
                numTested = 3,
                numWrong = 0,
                streak = 3,
                dateAdded = now
            )
        )

        val viewModel = StudyViewModel(studyRepo, vocRepo)
        var state = withTimeout(3000) {
            viewModel.uiState.filter { !it.isLoading && it.currentCard != null }.first()
        }
        var card = state.currentCard!!

        var steps = 0
        while (card.quizType != QuizType.PINYIN && steps < 10) {
            viewModel.nextCard()
            state = withTimeout(3000) {
                viewModel.uiState.filter { !it.isLoading && it.currentCard != null }.first()
            }
            card = state.currentCard!!
            steps++
        }
        assertEquals(QuizType.PINYIN, card.quizType)

        // Correct syllable "xue", but WRONG tone 3 instead of tone 2
        viewModel.selectPinyinSyllable("xue")
        viewModel.selectPinyinTone(3)

        val answeredState = withTimeout(3000) {
            viewModel.uiState.filter { it.isAnswerRevealed }.first()
        }
        assertTrue(answeredState.isAnswerRevealed)

        val updatedRecord = studyRepo.getLeitnerState("学")!!
        // Box must be preserved at Box 3 (NOT demoted to Box 0!)
        assertEquals(3, updatedRecord.box)
        // Next review timer should be refreshed and in future
        assertTrue(updatedRecord.expTime > now)
    }

    @Test
    fun testPinyinQuiz_syllableMistake_appliesReviewGradeAgain_demotesBox() = runBlocking {
        vocRepo.addVocabulary(Vocabulary("学", "xue2", arrayListOf("to learn")))
        val now = System.currentTimeMillis()
        andZiDb.leitnerDao().insertOrUpdateRecord(
            com.matthieu.zi_app.data.local.entity.LeitnerRecordEntity(
                hanzi = "学",
                box = 4,
                expTime = now - 1000L,
                numTested = 4,
                numWrong = 0,
                streak = 4,
                dateAdded = now
            )
        )

        val viewModel = StudyViewModel(studyRepo, vocRepo)
        var state = withTimeout(3000) {
            viewModel.uiState.filter { !it.isLoading && it.currentCard != null }.first()
        }
        var card = state.currentCard!!

        var steps = 0
        while (card.quizType != QuizType.PINYIN && steps < 10) {
            viewModel.nextCard()
            state = withTimeout(3000) {
                viewModel.uiState.filter { !it.isLoading && it.currentCard != null }.first()
            }
            card = state.currentCard!!
            steps++
        }
        assertEquals(QuizType.PINYIN, card.quizType)

        // WRONG syllable "shang" (completely incorrect base radical)
        viewModel.selectPinyinSyllable("shang")
        viewModel.selectPinyinTone(2)

        val answeredState = withTimeout(3000) {
            viewModel.uiState.filter { it.isAnswerRevealed }.first()
        }
        assertTrue(answeredState.isAnswerRevealed)

        val updatedRecord = studyRepo.getLeitnerState("学")!!
        // Full demotion applied for wrong syllable
        assertTrue(updatedRecord.box < 4)
    }

    @Test
    fun testWordAssemblyQuiz_refreshesConstituentCharacterTimersWithoutUpdatingBox() = runBlocking {
        vocRepo.addVocabulary(Vocabulary("学习", "xue2xi2", arrayListOf("to study")))
        val now = System.currentTimeMillis()
        andZiDb.leitnerDao().insertOrUpdateRecord(
            com.matthieu.zi_app.data.local.entity.LeitnerRecordEntity(hanzi = "学习", box = 1, expTime = now - 1000L)
        )
        // Constituent characters at Box 2 with unexpired expTime so only 学习 is queued for test
        andZiDb.leitnerDao().insertOrUpdateRecord(
            com.matthieu.zi_app.data.local.entity.LeitnerRecordEntity(hanzi = "学", box = 2, expTime = now + 100_000L)
        )
        andZiDb.leitnerDao().insertOrUpdateRecord(
            com.matthieu.zi_app.data.local.entity.LeitnerRecordEntity(hanzi = "习", box = 2, expTime = now + 100_000L)
        )

        val viewModel = StudyViewModel(studyRepo, vocRepo)
        var state = withTimeout(3000) {
            viewModel.uiState.filter { !it.isLoading && it.currentCard != null }.first()
        }
        var card = state.currentCard!!

        var steps = 0
        while (card.quizType != QuizType.WORD_ASSEMBLY && steps < 10) {
            viewModel.nextCard()
            state = withTimeout(3000) {
                viewModel.uiState.filter { !it.isLoading && it.currentCard != null }.first()
            }
            card = state.currentCard!!
            steps++
        }
        assertEquals(QuizType.WORD_ASSEMBLY, card.quizType)

        // Correctly assemble "学习"
        val xueIdx = card.scrambledTiles.indexOf("学")
        val xiIdx = card.scrambledTiles.indexOf("习")
        viewModel.selectAssemblyTile(xueIdx, "学")
        viewModel.selectAssemblyTile(xiIdx, "习")

        val answeredState = withTimeout(3000) {
            viewModel.uiState.filter { it.isAnswerRevealed }.first()
        }
        assertTrue(answeredState.isAnswerRevealed)

        // Compound word promoted from Box 1 to Box 2
        assertEquals(2, studyRepo.getLeitnerState("学习")!!.box)

        // Constituent characters MUST preserve Box 2, while having expTime refreshed into the future
        val xueRecord = studyRepo.getLeitnerState("学")!!
        assertEquals(2, xueRecord.box)
        assertTrue(xueRecord.expTime > now)

        val xiRecord = studyRepo.getLeitnerState("习")!!
        assertEquals(2, xiRecord.box)
        assertTrue(xiRecord.expTime > now)
    }

    @Test
    fun testPairMatchQuiz_highBox_scalesTargetPairsAndPrioritizesDueWords() = runBlocking {
        vocRepo.addVocabulary(Vocabulary("飞机", "fei1ji1", arrayListOf("airplane")))
        vocRepo.addVocabulary(Vocabulary("苹果", "ping2guo3", arrayListOf("apple")))
        vocRepo.addVocabulary(Vocabulary("牛奶", "niu2nai3", arrayListOf("milk")))
        vocRepo.addVocabulary(Vocabulary("游泳", "you2yong3", arrayListOf("swimming")))
        vocRepo.addVocabulary(Vocabulary("米饭", "mi3fan4", arrayListOf("cooked rice")))

        val now = System.currentTimeMillis()
        // Primary word at Box 6 (Mastery / High box tier -> target 5 pairs)
        andZiDb.leitnerDao().insertOrUpdateRecord(
            com.matthieu.zi_app.data.local.entity.LeitnerRecordEntity(hanzi = "飞机", box = 6, expTime = now - 1000L)
        )
        // Companion candidate words in active vocabulary
        andZiDb.leitnerDao().insertOrUpdateRecord(
            com.matthieu.zi_app.data.local.entity.LeitnerRecordEntity(hanzi = "苹果", box = 2, expTime = now + 100_000L)
        )
        andZiDb.leitnerDao().insertOrUpdateRecord(
            com.matthieu.zi_app.data.local.entity.LeitnerRecordEntity(hanzi = "牛奶", box = 1, expTime = now + 100_000L)
        )
        andZiDb.leitnerDao().insertOrUpdateRecord(
            com.matthieu.zi_app.data.local.entity.LeitnerRecordEntity(hanzi = "游泳", box = 3, expTime = now + 100_000L)
        )
        andZiDb.leitnerDao().insertOrUpdateRecord(
            com.matthieu.zi_app.data.local.entity.LeitnerRecordEntity(hanzi = "米饭", box = 1, expTime = now + 100_000L)
        )
        for (char in listOf("飞", "机", "苹", "果", "牛", "奶", "游", "泳", "米", "饭")) {
            andZiDb.leitnerDao().insertOrUpdateRecord(
                com.matthieu.zi_app.data.local.entity.LeitnerRecordEntity(hanzi = char, box = 1, expTime = now + 100_000L)
            )
        }

        val viewModel = StudyViewModel(studyRepo, vocRepo)
        var state = withTimeout(3000) {
            viewModel.uiState.filter { !it.isLoading && it.currentCard != null }.first()
        }
        var card = state.currentCard!!

        var steps = 0
        while ((card.quizType != QuizType.PAIR_MATCH || card.hanzi != "飞机") && steps < 15) {
            viewModel.nextCard()
            state = withTimeout(3000) {
                viewModel.uiState.filter { !it.isLoading && it.currentCard != null }.first()
            }
            card = state.currentCard!!
            steps++
        }

        assertEquals(QuizType.PAIR_MATCH, card.quizType)
        // High box card scaled up to 5 pairs (10 tiles)
        assertEquals(5, card.matchPairs.size)
        assertEquals(5, card.leftMatchTiles.size)
        assertEquals(5, card.rightMatchTiles.size)

        // Verify due companion words were prioritized and included
        val companionWords = card.matchPairs.map { it.word }.toSet()
        assertTrue(companionWords.contains("飞机"))
        assertTrue(companionWords.contains("苹果"))
        assertTrue(companionWords.contains("牛奶"))
        assertTrue(companionWords.contains("游泳"))
        assertTrue(companionWords.contains("米饭"))
    }

    @Test
    fun testStudySessionProgressPrioritizationWhenBadgeUnlocked() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences(com.matthieu.zi_app.data.preferences.UserPreferences.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val userPrefs = com.matthieu.zi_app.data.preferences.UserPreferences(context)

        val dictDb = AndZiDatabase.createInMemory(context)
        val dictRepo = VocabularyRepository(dictDb.vocabularyDao())

        // Add 1 HSK 1 vocabulary word
        dictRepo.insertHskVocab(
            listOf(
                com.matthieu.zi_app.data.local.entity.HskVocabEntity(hanzi = "水", hskLevel = 1, freqRank = 1)
            )
        )

        // Add "水" to local study vocabulary at box 2
        vocRepo.addVocabulary(Vocabulary("水", "shui3", arrayListOf("water")))
        studyRepo.recordReviewGrade("水", ReviewGrade.GOOD, 1000L) // box 1
        studyRepo.recordReviewGrade("水", ReviewGrade.GOOD, 2000L) // box 2

        val viewModel = StudyViewModel(
            studyRepository = studyRepo,
            vocabularyRepository = vocRepo,
            dictionaryRepository = dictRepo,
            userPreferences = userPrefs
        )

        withTimeout(3000) {
            viewModel.uiState.filter { !it.isLoading && it.queue.isNotEmpty() }.first()
        }

        viewModel.submitToneAnswer(3)
        viewModel.rateCard(ReviewGrade.GOOD)

        val finishedState = withTimeout(3000) {
            viewModel.uiState.filter { it.isSessionFinished }.first()
        }
        assertTrue(finishedState.isSessionFinished)

        // When a badge is acquired, ONLY the badge is shown (recordIncreases is empty)
        assertEquals(1, finishedState.newlyUnlockedBadges.size)
        assertEquals(com.matthieu.zi_app.domain.badges.BadgeId.HSK_1, finishedState.newlyUnlockedBadges.first())
        assertTrue(finishedState.recordIncreases.isEmpty())
        dictDb.close()
    }

    @Test
    fun testStudySessionProgressPrioritizationWhenNoBadgeUnlocked() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences(com.matthieu.zi_app.data.preferences.UserPreferences.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val userPrefs = com.matthieu.zi_app.data.preferences.UserPreferences(context)

        val dictDb = AndZiDatabase.createInMemory(context)
        val dictRepo = VocabularyRepository(dictDb.vocabularyDao())

        // Add 2 words in HSK 1
        dictRepo.insertHskVocab(
            listOf(
                com.matthieu.zi_app.data.local.entity.HskVocabEntity(hanzi = "水", hskLevel = 1, freqRank = 1),
                com.matthieu.zi_app.data.local.entity.HskVocabEntity(hanzi = "火", hskLevel = 1, freqRank = 2)
            )
        )

        // Add "水" at box 2
        vocRepo.addVocabulary(Vocabulary("水", "shui3", arrayListOf("water")))
        studyRepo.recordReviewGrade("水", ReviewGrade.GOOD, 1000L) // box 1
        studyRepo.recordReviewGrade("水", ReviewGrade.GOOD, 2000L) // box 2

        val viewModel = StudyViewModel(
            studyRepository = studyRepo,
            vocabularyRepository = vocRepo,
            dictionaryRepository = dictRepo,
            userPreferences = userPrefs
        )

        withTimeout(3000) {
            viewModel.uiState.filter { !it.isLoading && it.queue.isNotEmpty() }.first()
        }

        viewModel.submitToneAnswer(3)
        viewModel.rateCard(ReviewGrade.GOOD)

        val finishedState = withTimeout(3000) {
            viewModel.uiState.filter { it.isSessionFinished }.first()
        }
        assertTrue(finishedState.isSessionFinished)

        // No badge unlocked
        assertTrue(finishedState.newlyUnlockedBadges.isEmpty())
        // Exactly ONE progress message shown: HSK progress is prioritized
        assertEquals(1, finishedState.recordIncreases.size)
        assertTrue(finishedState.recordIncreases.first().startsWith("HSK 1 progress:"))
        dictDb.close()
    }
}



