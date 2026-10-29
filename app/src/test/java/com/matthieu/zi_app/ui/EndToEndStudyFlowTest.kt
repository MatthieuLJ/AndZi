package com.matthieu.zi_app.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.matthieu.zi_app.Vocabulary
import com.matthieu.zi_app.data.local.AndZiDatabase
import com.matthieu.zi_app.data.local.ProgressDatabase
import com.matthieu.zi_app.data.repository.ProgressRepository
import com.matthieu.zi_app.data.repository.StudyRepository
import com.matthieu.zi_app.data.repository.VocabularyRepository
import com.matthieu.zi_app.domain.srs.ReviewGrade
import com.matthieu.zi_app.ui.dictionary.DictionaryViewModel
import com.matthieu.zi_app.ui.home.HomeViewModel
import com.matthieu.zi_app.ui.study.QuizType
import com.matthieu.zi_app.ui.study.StudyViewModel
import com.matthieu.zi_app.ui.vocabulary.VocabularyListViewModel
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class EndToEndStudyFlowTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var dictDb: AndZiDatabase
    private lateinit var userDb: AndZiDatabase
    private lateinit var progressDb: ProgressDatabase

    private lateinit var dictRepo: VocabularyRepository
    private lateinit var userVocRepo: VocabularyRepository
    private lateinit var studyRepo: StudyRepository
    private lateinit var progressRepo: ProgressRepository

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        dictDb = AndZiDatabase.createInMemory(context)
        userDb = AndZiDatabase.createInMemory(context)
        progressDb = ProgressDatabase.createInMemory(context)

        dictRepo = VocabularyRepository(dictDb.vocabularyDao())
        userVocRepo = VocabularyRepository(userDb.vocabularyDao())
        studyRepo = StudyRepository(userDb.leitnerDao())
        progressRepo = ProgressRepository(progressDb.progressDao())
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
        dictDb.close()
        userDb.close()
        progressDb.close()
    }

    @Test
    fun testCompleteEndToEndStudyAndAnalyticsLifecycle() = runBlocking {
        // --- Step 1: Pre-populate reference dictionary ---
        val word1 = Vocabulary("明", "ming2", arrayListOf("bright", "clear"))
        val word2 = Vocabulary("明天", "ming2tian1", arrayListOf("tomorrow"))
        val word3 = Vocabulary("朋友", "peng2you5", arrayListOf("friend", "pal"))
        dictRepo.addVocabulary(word1)
        dictRepo.addVocabulary(word2)
        dictRepo.addVocabulary(word3)

        // --- Step 2: User searches dictionary and adds "明天" to study ---
        val dictViewModel = DictionaryViewModel(dictRepo, userVocRepo, studyRepo, debounceMillis = 0L)
        dictViewModel.onSearchQueryChanged("明天")

        val searchResultState = withTimeout(3000) {
            dictViewModel.uiState.filter { !it.isSearching && it.results.isNotEmpty() }.first()
        }
        val foundWord = searchResultState.results.first { it.chinese == "明天" }
        dictViewModel.selectWord(foundWord)

        withTimeout(3000) {
            dictViewModel.uiState.filter { it.selectedWord != null }.first()
        }

        // Add "tomorrow" translation to study
        dictViewModel.toggleTranslationForStudy("tomorrow")

        withTimeout(3000) {
            dictViewModel.uiState.filter { it.selectedTranslations.contains("tomorrow") }.first()
        }

        // Verify word is saved in local repository
        val localWords = userVocRepo.getAllVocabulary()
        assertEquals(1, localWords.size)
        assertEquals("明天", localWords.first().chinese)
        assertEquals(listOf("tomorrow"), localWords.first().english)

        // --- Step 3: Active Vocabulary Screen shows active cards with translations ---
        val vocabListViewModel = VocabularyListViewModel(userVocRepo, dictRepo, studyRepo)
        val activeCardsState = withTimeout(3000) {
            vocabListViewModel.uiState.filter { it.items.isNotEmpty() }.first()
        }
        assertEquals(1, activeCardsState.items.size)
        assertEquals("明天", activeCardsState.items.first().vocabulary.chinese)
        // Ensure single sub-characters without translations (like "天") are excluded
        assertTrue(activeCardsState.items.none { it.vocabulary.chinese == "天" })

        // --- Step 4: Home Dashboard shows review count and Leitner distribution ---
        val homeViewModel = HomeViewModel(studyRepo, progressRepo)
        homeViewModel.loadDashboardData()
        val homeState = withTimeout(3000) {
            homeViewModel.uiState.filter { !it.isLoading && it.totalLearned >= 1 }.first()
        }
        assertTrue(homeState.dueCount >= 1)
        assertTrue((homeState.boxDistribution[0] ?: 0) >= 1)

        // --- Step 5: Start Study Session and Answer Correctly ---
        val studyViewModel = StudyViewModel(studyRepo, userVocRepo, dictRepo)
        val studyState = withTimeout(3000) {
            studyViewModel.uiState.filter { !it.isLoading && it.currentCard != null }.first()
        }
        val currentCard = studyState.currentCard
        assertNotNull(currentCard)

        if ((currentCard!!.quizType == QuizType.TONE || currentCard.quizType == QuizType.PINYIN) && currentCard.contextWords.isNotEmpty()) {
            studyViewModel.revealContextWords()
        }

        // Submit correct answer based on card quiz type
        when (currentCard.quizType) {
            QuizType.TONE -> studyViewModel.submitToneAnswer(currentCard.tone)
            QuizType.PINYIN -> studyViewModel.submitPinyinAnswer(com.matthieu.zi_app.PinyinParser.convert_to_pretty_version(currentCard.pinyin))
            QuizType.ENGLISH -> studyViewModel.submitEnglishAnswer(currentCard.english.first())
            QuizType.CLOZE -> studyViewModel.submitClozeAnswer(currentCard.wordSlots[currentCard.clozeTargetIndex])
            QuizType.WORD_ASSEMBLY -> {
                currentCard.wordSlots.forEach { ch ->
                    val idx = currentCard.scrambledTiles.indexOf(ch)
                    studyViewModel.selectAssemblyTile(idx, ch)
                }
            }
            QuizType.WRITING -> {
                currentCard.writingCharacter?.strokes?.forEach { stroke ->
                    studyViewModel.onUserStrokeDrawn(stroke.points)
                }
            }
        }

        val afterAnswerState = withTimeout(3000) {
            studyViewModel.uiState.filter { it.isAnswerRevealed && it.isCorrect == true }.first()
        }
        assertTrue(afterAnswerState.isCorrect == true)

        // Verify SRS progression: card promoted to Box 1
        val updatedLeitner = studyRepo.getLeitnerState(currentCard.hanzi)
        assertNotNull(updatedLeitner)
        assertEquals(1, updatedLeitner!!.box)
        assertEquals(1, updatedLeitner.streak)
        assertEquals(1, updatedLeitner.numTested)
        assertEquals(0, updatedLeitner.numWrong)

        // --- Step 6: Progress Sync & Analytics Tracking ---
        val boxCounts = studyRepo.getBoxCounts()
        val boxMap = (0..9).associateWith { 0 }.toMutableMap()
        for (bc in boxCounts) {
            boxMap[bc.box] = bc.count
        }
        val now = System.currentTimeMillis()
        progressRepo.recordProgressSnapshot(
            totalBox1 = boxMap[0] ?: 0,
            totalBox2 = boxMap[1] ?: 0,
            totalBox3 = boxMap[2] ?: 0,
            totalBox4 = boxMap[3] ?: 0,
            totalBox5 = (4..9).sumOf { boxMap[it] ?: 0 },
            expiredBox2 = 0,
            expiredBox3 = 0,
            expiredBox4 = 0,
            expiredBox5 = 0,
            date = now
        )

        val finalHomeViewModel = HomeViewModel(studyRepo, progressRepo, leitnerDao = userDb.leitnerDao())
        finalHomeViewModel.selectTimeRange(com.matthieu.zi_app.ui.home.TimeRange.DAYS_7)
        val finalHomeState = withTimeout(3000) {
            finalHomeViewModel.uiState.filter { !it.isAnalyticsLoading && it.progressRecords.isNotEmpty() }.first()
        }
        assertTrue(finalHomeState.progressRecords.isNotEmpty())
        assertEquals(3, finalHomeState.totalLearned)
        assertEquals(1, finalHomeState.boxDistribution[1])
        assertEquals(2, finalHomeState.boxDistribution[0] ?: 0)
    }

    @Test
    fun testStudyCardDemotionAndResetFlow() = runBlocking {
        val word = Vocabulary("苹果", "ping2guo3", arrayListOf("apple"))
        userVocRepo.addVocabulary(word)
        studyRepo.recordReviewGrade("苹果", ReviewGrade.GOOD)

        var state = studyRepo.getLeitnerState("苹果")
        assertEquals(1, state?.box)

        // User makes an error in study session -> demoted
        studyRepo.recordReviewGrade("苹果", ReviewGrade.AGAIN)
        state = studyRepo.getLeitnerState("苹果")
        assertEquals(0, state?.box)
        assertEquals(0, state?.streak)
        assertEquals(1, state?.numWrong)

        // User restarts card via studyRepo.restartStudy
        val resetState = studyRepo.restartStudy("苹果")
        assertEquals(0, resetState.box)
        assertEquals(0, resetState.streak)
    }
}
