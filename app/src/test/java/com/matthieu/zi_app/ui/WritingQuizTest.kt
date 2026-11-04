package com.matthieu.zi_app.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.matthieu.zi_app.Vocabulary
import com.matthieu.zi_app.data.local.AndZiDatabase
import com.matthieu.zi_app.data.local.entity.CharPinyinEntity
import com.matthieu.zi_app.data.local.entity.CharacterStrokeEntity
import com.matthieu.zi_app.data.local.entity.ChineseWordEntity
import com.matthieu.zi_app.data.local.entity.EnglishTranslationEntity
import com.matthieu.zi_app.data.local.entity.LeitnerRecordEntity
import com.matthieu.zi_app.data.local.entity.WordSegmentEntity
import com.matthieu.zi_app.data.preferences.UserPreferences
import com.matthieu.zi_app.data.repository.StudyRepository
import com.matthieu.zi_app.data.repository.VocabularyRepository
import com.matthieu.zi_app.domain.writing.HanziPoint
import com.matthieu.zi_app.domain.writing.WritingScaffoldingLevel
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class WritingQuizTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var andZiDb: AndZiDatabase
    private lateinit var vocRepo: VocabularyRepository
    private lateinit var studyRepo: StudyRepository
    private lateinit var userPrefs: UserPreferences

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        andZiDb = AndZiDatabase.createInMemory(context)
        userPrefs = UserPreferences(context)
        userPrefs.setWritingQuizEnabled(true)

        vocRepo = VocabularyRepository(andZiDb.vocabularyDao())
        studyRepo = StudyRepository(andZiDb.leitnerDao())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        andZiDb.close()
    }

    @Test
    fun testWritingQuizGeneratedWhenEnabledAndStrokesAvailable() = runBlocking {
        // Insert character with stroke data: "一" (1 stroke: 250,500 to 750,500)
        andZiDb.vocabularyDao().insertCharacterStrokes(
            listOf(
                CharacterStrokeEntity(
                    hanzi = "一",
                    strokes = "[\"M 250 500 L 750 500\"]",
                    medians = "[[[250, 500], [500, 500], [750, 500]]]",
                    strokeCount = 1
                )
            )
        )
        andZiDb.vocabularyDao().insertCharPinyin(
            CharPinyinEntity(hanzi = "一", pinyin = "yi1")
        )
        val wordId = andZiDb.vocabularyDao().insertWord(
            ChineseWordEntity(chinese = "一", pinyin = "yi1", length = 1)
        )
        andZiDb.vocabularyDao().insertTranslations(
            listOf(EnglishTranslationEntity(cwordId = wordId, translation = "one"))
        )

        val now = System.currentTimeMillis()
        andZiDb.leitnerDao().insertOrUpdateRecord(
            LeitnerRecordEntity(hanzi = "一", box = 1, expTime = now - 1000L)
        )

        // With index = 3, in candidate list [TONE, ENGLISH, PINYIN, WRITING], 3 % 4 = 3 -> WRITING
        val viewModel = StudyViewModel(studyRepo, vocRepo, userPreferences = userPrefs)

        val state = withTimeout(3000) {
            viewModel.uiState.filter { !it.isLoading && it.currentCard != null }.first()
        }

        var card = state.currentCard!!
        // Advance rotation to WRITING card
        while (card.quizType != QuizType.WRITING) {
            viewModel.nextCard()
            card = withTimeout(3000) {
                viewModel.uiState.filter { !it.isLoading && it.currentCard != null && it.currentCard?.hanzi == "一" }.first().currentCard!!
            }
        }

        // Check that WRITING quiz is generated and contains stroke geometry
        assertEquals("一", card.hanzi)
        assertEquals(QuizType.WRITING, card.quizType)
        assertNotNull(card.writingCharacter)
        assertEquals(1, card.writingCharacter!!.strokeCount)
    }

    @Test
    fun testWritingQuizDisabledInPreferencesNeverProducesWritingQuiz() = runBlocking {
        userPrefs.setWritingQuizEnabled(false)

        andZiDb.vocabularyDao().insertCharacterStrokes(
            listOf(
                CharacterStrokeEntity(
                    hanzi = "一",
                    strokes = "[\"M 250 500 L 750 500\"]",
                    medians = "[[[250, 500], [500, 500], [750, 500]]]",
                    strokeCount = 1
                )
            )
        )
        andZiDb.vocabularyDao().insertCharPinyin(
            CharPinyinEntity(hanzi = "一", pinyin = "yi1")
        )
        val wordId = andZiDb.vocabularyDao().insertWord(
            ChineseWordEntity(chinese = "一", pinyin = "yi1", length = 1)
        )
        andZiDb.vocabularyDao().insertTranslations(
            listOf(EnglishTranslationEntity(cwordId = wordId, translation = "one"))
        )

        val now = System.currentTimeMillis()
        andZiDb.leitnerDao().insertOrUpdateRecord(
            LeitnerRecordEntity(hanzi = "一", box = 1, expTime = now - 1000L)
        )

        val viewModel = StudyViewModel(studyRepo, vocRepo, userPreferences = userPrefs)

        val state = withTimeout(3000) {
            viewModel.uiState.filter { !it.isLoading && it.currentCard != null }.first()
        }

        assertFalse("Writing quiz must not be generated when disabled", state.currentCard!!.quizType == QuizType.WRITING)
    }

    @Test
    fun testStrokeDrawingAndValidationWorkflow() = runBlocking {
        // Character "十" with 2 strokes: stroke 0 horizontal, stroke 1 vertical
        andZiDb.vocabularyDao().insertCharacterStrokes(
            listOf(
                CharacterStrokeEntity(
                    hanzi = "十",
                    strokes = "[\"M 200 500 L 800 500\", \"M 500 800 L 500 200\"]",
                    medians = "[[[200, 500], [800, 500]], [[500, 800], [500, 200]]]",
                    strokeCount = 2
                )
            )
        )
        andZiDb.vocabularyDao().insertCharPinyin(
            CharPinyinEntity(hanzi = "十", pinyin = "shi2")
        )
        val wordId = andZiDb.vocabularyDao().insertWord(
            ChineseWordEntity(chinese = "十", pinyin = "shi2", length = 1)
        )
        andZiDb.vocabularyDao().insertTranslations(
            listOf(EnglishTranslationEntity(cwordId = wordId, translation = "ten"))
        )

        val now = System.currentTimeMillis()
        andZiDb.leitnerDao().insertOrUpdateRecord(
            LeitnerRecordEntity(hanzi = "十", box = 1, expTime = now - 1000L)
        )

        val viewModel = StudyViewModel(studyRepo, vocRepo, userPreferences = userPrefs)
        val state = withTimeout(3000) {
            viewModel.uiState.filter { !it.isLoading && it.currentCard != null }.first()
        }
        var currentCard = state.currentCard!!
        while (currentCard.quizType != QuizType.WRITING) {
            viewModel.nextCard()
            currentCard = withTimeout(3000) {
                viewModel.uiState.filter { !it.isLoading && it.currentCard != null && it.currentCard?.hanzi == "十" }.first().currentCard!!
            }
        }

        // Test drawing incorrect stroke (vertical stroke when horizontal stroke is expected)
        val wrongVerticalStroke = listOf(HanziPoint(500f, 800f), HanziPoint(500f, 200f))
        val initialShakes = viewModel.uiState.value.writingErrorShakeTrigger
        viewModel.onUserStrokeDrawn(wrongVerticalStroke)
        assertEquals("Incorrect stroke should trigger error shake", initialShakes + 1, viewModel.uiState.value.writingErrorShakeTrigger)
        assertEquals("Current stroke index should not advance", 0, viewModel.uiState.value.currentStrokeIndex)

        // Draw correct stroke 0
        val correctHorizontalStroke = listOf(HanziPoint(205f, 502f), HanziPoint(795f, 498f))
        viewModel.onUserStrokeDrawn(correctHorizontalStroke)
        assertEquals("Stroke index should advance to 1", 1, viewModel.uiState.value.currentStrokeIndex)
        assertEquals(1, viewModel.uiState.value.completedStrokes.size)

        // Test Undo
        viewModel.undoWritingStroke()
        assertEquals("Stroke index should revert to 0 on undo", 0, viewModel.uiState.value.currentStrokeIndex)
        assertEquals(0, viewModel.uiState.value.completedStrokes.size)

        // Draw stroke 0 and stroke 1 to complete character
        viewModel.onUserStrokeDrawn(correctHorizontalStroke)
        val correctVerticalStroke = listOf(HanziPoint(500f, 795f), HanziPoint(500f, 205f))
        viewModel.onUserStrokeDrawn(correctVerticalStroke)

        // Check completion in unassisted test mode -> should promote card!
        val completedState = withTimeout(3000) {
            viewModel.uiState.filter { it.isAnswerRevealed }.first()
        }
        assertTrue("Answer should be revealed as correct", completedState.isCorrect == true)
        val updatedRecord = studyRepo.getLeitnerState("十")
        assertNotNull(updatedRecord)
        assertEquals("Unassisted test mode advances box to 2", 2, updatedRecord!!.box)
    }

    @Test
    fun testGuidedWritingAppliesStrokeComplexityPenalty() = runBlocking {
        // Simple 2-stroke character "十"
        andZiDb.vocabularyDao().insertCharacterStrokes(
            listOf(
                CharacterStrokeEntity(
                    hanzi = "十",
                    strokes = "[\"M 200 500 L 800 500\", \"M 500 800 L 500 200\"]",
                    medians = "[[[200, 500], [800, 500]], [[500, 800], [500, 200]]]",
                    strokeCount = 2
                )
            )
        )
        andZiDb.vocabularyDao().insertCharPinyin(
            CharPinyinEntity(hanzi = "十", pinyin = "shi2")
        )
        val wordId = andZiDb.vocabularyDao().insertWord(
            ChineseWordEntity(chinese = "十", pinyin = "shi2", length = 1)
        )
        andZiDb.vocabularyDao().insertTranslations(
            listOf(EnglishTranslationEntity(cwordId = wordId, translation = "ten"))
        )

        val now = System.currentTimeMillis()
        // Put "十" in Box 3
        andZiDb.leitnerDao().insertOrUpdateRecord(
            LeitnerRecordEntity(hanzi = "十", box = 3, expTime = now - 1000L, streak = 3)
        )

        val viewModel = StudyViewModel(studyRepo, vocRepo, userPreferences = userPrefs)
        val state = withTimeout(3000) {
            viewModel.uiState.filter { !it.isLoading && it.currentCard != null }.first()
        }
        var currentCard = state.currentCard!!
        while (currentCard.quizType != QuizType.WRITING) {
            viewModel.nextCard()
            currentCard = withTimeout(3000) {
                viewModel.uiState.filter { !it.isLoading && it.currentCard != null && it.currentCard?.hanzi == "十" }.first().currentCard!!
            }
        }

        // Switch to Guided Mode
        viewModel.setWritingScaffoldingLevel(WritingScaffoldingLevel.GUIDED_FULL)
        assertTrue(viewModel.uiState.value.hasUsedWritingGuide)

        // Complete strokes with guidance
        val stroke0 = listOf(HanziPoint(205f, 502f), HanziPoint(795f, 498f))
        val stroke1 = listOf(HanziPoint(500f, 795f), HanziPoint(500f, 205f))
        viewModel.onUserStrokeDrawn(stroke0)
        viewModel.onUserStrokeDrawn(stroke1)

        val completedState = withTimeout(3000) {
            viewModel.uiState.filter { it.isAnswerRevealed }.first()
        }
        assertTrue("Completed with guidance should reveal card", completedState.isAnswerRevealed)

        // For simple characters (<= 3 strokes), guided completion demotes by 1 box (3 -> 2)
        val updatedRecord = studyRepo.getLeitnerState("十")
        assertNotNull(updatedRecord)
        assertEquals("Simple character with guidance should demote box from 3 to 2", 2, updatedRecord!!.box)
        assertEquals("Streak should be reset on assisted completion of simple character", 0, updatedRecord.streak)
    }

    @Test
    fun testGuidedWritingPreservesBoxForComplexCharacter() = runBlocking {
        // Complex character with 8 strokes
        val strokePaths = (0 until 8).map { "\"M 200 500 L 800 500\"" }.joinToString(",")
        val medians = (0 until 8).map { "[[200, 500], [800, 500]]" }.joinToString(",")
        andZiDb.vocabularyDao().insertCharacterStrokes(
            listOf(
                CharacterStrokeEntity(
                    hanzi = "学",
                    strokes = "[$strokePaths]",
                    medians = "[$medians]",
                    strokeCount = 8
                )
            )
        )
        andZiDb.vocabularyDao().insertCharPinyin(
            CharPinyinEntity(hanzi = "学", pinyin = "xue2")
        )
        val wordId = andZiDb.vocabularyDao().insertWord(
            ChineseWordEntity(chinese = "学", pinyin = "xue2", length = 1)
        )
        andZiDb.vocabularyDao().insertTranslations(
            listOf(EnglishTranslationEntity(cwordId = wordId, translation = "study"))
        )

        val now = System.currentTimeMillis()
        andZiDb.leitnerDao().insertOrUpdateRecord(
            LeitnerRecordEntity(hanzi = "学", box = 4, expTime = now - 1000L, streak = 5)
        )

        val viewModel = StudyViewModel(studyRepo, vocRepo, userPreferences = userPrefs)
        val state = withTimeout(3000) {
            viewModel.uiState.filter { !it.isLoading && it.currentCard != null }.first()
        }
        var currentCard = state.currentCard!!
        while (currentCard.quizType != QuizType.WRITING) {
            viewModel.nextCard()
            currentCard = withTimeout(3000) {
                viewModel.uiState.filter { !it.isLoading && it.currentCard != null && it.currentCard?.hanzi == "学" }.first().currentCard!!
            }
        }

        // Switch to Guided Mode
        viewModel.setWritingScaffoldingLevel(WritingScaffoldingLevel.GUIDED_DIRECTION)

        // Complete all 8 strokes
        for (i in 0 until 8) {
            viewModel.onUserStrokeDrawn(listOf(HanziPoint(205f, 502f), HanziPoint(795f, 498f)))
        }

        withTimeout(3000) {
            viewModel.uiState.filter { it.isAnswerRevealed }.first()
        }

        val updatedRecord = studyRepo.getLeitnerState("学")
        assertNotNull(updatedRecord)
        assertEquals("Complex character (>= 4 strokes) should preserve box", 4, updatedRecord!!.box)
        assertEquals("Complex character should preserve streak", 5, updatedRecord.streak)
    }

    @Test
    fun testWritingQuiz_whenContainingWordHasRepeatedCharacter_doesNotUseRepeatedWord() = runBlocking {
        // Character "妈" (3 strokes for simplicity in test data)
        andZiDb.vocabularyDao().insertCharacterStrokes(
            listOf(
                CharacterStrokeEntity(
                    hanzi = "妈",
                    strokes = "[\"M 250 500 L 750 500\", \"M 250 500 L 750 500\", \"M 250 500 L 750 500\"]",
                    medians = "[[[250, 500], [750, 500]], [[250, 500], [750, 500]], [[250, 500], [750, 500]]]",
                    strokeCount = 3
                )
            )
        )
        vocRepo.addVocabulary(Vocabulary("妈", "ma1", arrayListOf("mom")))
        // Containing compound word repeats the character "妈": "妈妈"
        vocRepo.addVocabulary(Vocabulary("妈妈", "ma1ma5", arrayListOf("mother")))

        val now = System.currentTimeMillis()
        andZiDb.leitnerDao().insertOrUpdateRecord(
            LeitnerRecordEntity(hanzi = "妈", box = 2, expTime = now - 1000L)
        )
        // Keep compound word "妈妈" in vocabulary but unexpired so only "妈" is drawn from study queue
        andZiDb.leitnerDao().insertOrUpdateRecord(
            LeitnerRecordEntity(hanzi = "妈妈", box = 1, expTime = now + 100000L)
        )

        val viewModel = StudyViewModel(studyRepo, vocRepo, userPreferences = userPrefs)
        val state = withTimeout(3000) {
            viewModel.uiState.filter { !it.isLoading && it.currentCard != null }.first()
        }
        var currentCard = state.currentCard!!
        while (currentCard.quizType != QuizType.WRITING || currentCard.hanzi != "妈") {
            viewModel.nextCard()
            currentCard = withTimeout(3000) {
                viewModel.uiState.filter { !it.isLoading && it.currentCard != null }.first()
            }.currentCard!!
        }

        assertEquals(QuizType.WRITING, currentCard.quizType)
        // Since the only containing word is "妈妈" which repeats "妈", it must NOT be used for writing quiz!
        assertTrue(
            "Word slots must be empty rather than showing repeated character '妈妈'",
            currentCard.wordSlots.isEmpty()
        )
        // No slot should show "妈"
        val displayedNonTargetSlots = currentCard.wordSlots.filterIndexed { idx, _ -> idx != currentCard.clozeTargetIndex }
        assertFalse("Character being tested must never be shown in non-target slots", displayedNonTargetSlots.contains("妈"))
    }

    @Test
    fun testWritingQuiz_whenMultipleContainingWordsExist_prefersNonRepeatingWord() = runBlocking {
        // Character "天" (4 strokes)
        andZiDb.vocabularyDao().insertCharacterStrokes(
            listOf(
                CharacterStrokeEntity(
                    hanzi = "天",
                    strokes = "[\"M 250 500 L 750 500\", \"M 250 500 L 750 500\", \"M 250 500 L 750 500\", \"M 250 500 L 750 500\"]",
                    medians = "[[[250, 500], [750, 500]], [[250, 500], [750, 500]], [[250, 500], [750, 500]], [[250, 500], [750, 500]]]",
                    strokeCount = 4
                )
            )
        )
        vocRepo.addVocabulary(Vocabulary("天", "tian1", arrayListOf("sky; day")))
        // Two containing compound words: "天天" (repeats "天") and "天气" (does not repeat "天")
        vocRepo.addVocabulary(Vocabulary("天天", "tian1tian1", arrayListOf("every day")))
        vocRepo.addVocabulary(Vocabulary("天气", "tian1qi4", arrayListOf("weather")))

        val now = System.currentTimeMillis()
        andZiDb.leitnerDao().insertOrUpdateRecord(
            LeitnerRecordEntity(hanzi = "天", box = 2, expTime = now - 1000L)
        )
        // Keep compound words and constituent characters in vocabulary but unexpired so only "天" is drawn from study queue
        andZiDb.leitnerDao().insertOrUpdateRecord(
            LeitnerRecordEntity(hanzi = "天天", box = 1, expTime = now + 100000L)
        )
        andZiDb.leitnerDao().insertOrUpdateRecord(
            LeitnerRecordEntity(hanzi = "天气", box = 1, expTime = now + 100000L)
        )
        andZiDb.leitnerDao().insertOrUpdateRecord(
            LeitnerRecordEntity(hanzi = "气", box = 1, expTime = now + 100000L)
        )

        val viewModel = StudyViewModel(studyRepo, vocRepo, userPreferences = userPrefs)
        val state = withTimeout(3000) {
            viewModel.uiState.filter { !it.isLoading && it.currentCard != null }.first()
        }
        var currentCard = state.currentCard!!
        while (currentCard.quizType != QuizType.WRITING || currentCard.hanzi != "天") {
            viewModel.nextCard()
            currentCard = withTimeout(3000) {
                viewModel.uiState.filter { !it.isLoading && it.currentCard != null }.first()
            }.currentCard!!
        }

        assertEquals(QuizType.WRITING, currentCard.quizType)
        // Word shown must be "天气", NOT "天天"
        assertEquals("天气", currentCard.revealedHanzi)
        assertEquals(listOf("天", "气"), currentCard.wordSlots)
        assertEquals(0, currentCard.clozeTargetIndex)
        // Visible slots should only contain "气", never "天"
        val displayedNonTargetSlots = currentCard.wordSlots.filterIndexed { idx, _ -> idx != currentCard.clozeTargetIndex }
        assertEquals(listOf("气"), displayedNonTargetSlots)
        assertFalse(displayedNonTargetSlots.contains("天"))
    }
}
