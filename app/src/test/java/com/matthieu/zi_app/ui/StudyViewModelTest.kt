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

        val viewModel = StudyViewModel(studyRepo, vocRepo)
        val presentedCards = collectPresentedCards(viewModel, maxSteps = 15)

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
}


