package com.matthieu.zi_app.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.matthieu.zi_app.Vocabulary
import com.matthieu.zi_app.data.local.AndZiDatabase
import com.matthieu.zi_app.data.local.entity.HskVocabEntity
import com.matthieu.zi_app.data.repository.StudyRepository
import com.matthieu.zi_app.data.repository.VocabularyRepository
import com.matthieu.zi_app.domain.srs.ReviewGrade
import com.matthieu.zi_app.ui.hsk.HskBrowserUiState
import com.matthieu.zi_app.ui.hsk.HskBrowserViewModel
import com.matthieu.zi_app.ui.hsk.HskFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
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
class HskBrowserViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var dictDb: AndZiDatabase
    private lateinit var userDb: AndZiDatabase
    private lateinit var dictRepo: VocabularyRepository
    private lateinit var userVocRepo: VocabularyRepository
    private lateinit var studyRepo: StudyRepository

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        dictDb = AndZiDatabase.createInMemory(context)
        userDb = AndZiDatabase.createInMemory(context)

        dictRepo = VocabularyRepository(dictDb.vocabularyDao())
        userVocRepo = VocabularyRepository(userDb.vocabularyDao())
        studyRepo = StudyRepository(userDb.leitnerDao())
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
        dictDb.close()
        userDb.close()
    }

    private suspend fun seedTestData() {
        // HSK 1 words
        dictRepo.addVocabulary(Vocabulary("你", "ni3", arrayListOf("you")))
        dictRepo.addVocabulary(Vocabulary("好", "hao3", arrayListOf("good")))
        dictRepo.addVocabulary(Vocabulary("我", "wo3", arrayListOf("I", "me")))

        // HSK 2 words
        dictRepo.addVocabulary(Vocabulary("时间", "shi2jian1", arrayListOf("time")))
        dictRepo.addVocabulary(Vocabulary("机场", "ji1chang3", arrayListOf("airport")))

        dictRepo.insertHskVocab(
            listOf(
                HskVocabEntity("你", hskLevel = 1, freqRank = 1, isChar = 1),
                HskVocabEntity("好", hskLevel = 1, freqRank = 2, isChar = 1),
                HskVocabEntity("我", hskLevel = 1, freqRank = 3, isChar = 1),
                HskVocabEntity("时间", hskLevel = 2, freqRank = 50, isChar = 0),
                HskVocabEntity("机场", hskLevel = 2, freqRank = 100, isChar = 0)
            )
        )
    }

    @Test
    fun testInitialLoad_loadsLevel1WordsAndStats() = runBlocking {
        seedTestData()

        val viewModel = HskBrowserViewModel(
            dictionaryRepository = dictRepo,
            localVocabularyRepository = userVocRepo,
            studyRepository = studyRepo,
            initialLevel = 1
        )

        val state = viewModel.uiState.filter { !it.isLoading && it.words.isNotEmpty() }.first()
        assertEquals(1, state.selectedLevel)
        assertEquals(3, state.words.size)
        assertTrue(state.words.all { it.hskLevel == 1 })
        assertTrue(state.words.all { !it.isStudied })

        val level1Stats = state.levelStats[1]
        assertNotNull(level1Stats)
        assertEquals(3, level1Stats?.totalWords)
        assertEquals(0, level1Stats?.studiedWords)
        assertEquals(3, level1Stats?.unlearnedWords)
    }

    @Test
    fun testFilters_unlearnedAndStudied() = runBlocking {
        seedTestData()

        // User already studies "你"
        userVocRepo.addVocabulary(Vocabulary("你", "ni3", arrayListOf("you")))
        studyRepo.recordReviewGrade("你", ReviewGrade.GOOD)

        val viewModel = HskBrowserViewModel(
            dictionaryRepository = dictRepo,
            localVocabularyRepository = userVocRepo,
            studyRepository = studyRepo,
            initialLevel = 1
        )

        // Initial ALL filter
        val allState = viewModel.uiState.filter { !it.isLoading && it.words.isNotEmpty() }.first()
        assertEquals(3, allState.words.size)

        // Filter UNLEARNED
        viewModel.selectFilter(HskFilter.UNLEARNED)
        val unlearnedState = viewModel.uiState.filter { it.selectedFilter == HskFilter.UNLEARNED }.first()
        assertEquals(2, unlearnedState.words.size)
        assertFalse(unlearnedState.words.any { it.vocabulary.chinese == "你" })
        assertTrue(unlearnedState.words.any { it.vocabulary.chinese == "好" })
        assertTrue(unlearnedState.words.any { it.vocabulary.chinese == "我" })

        // Filter STUDIED
        viewModel.selectFilter(HskFilter.STUDIED)
        val studiedState = viewModel.uiState.filter { it.selectedFilter == HskFilter.STUDIED }.first()
        assertEquals(1, studiedState.words.size)
        assertEquals("你", studiedState.words.first().vocabulary.chinese)
        assertTrue(studiedState.words.first().isStudied)
    }

    @Test
    fun testToggleStudy_addsAndRemovesCard() = runBlocking {
        seedTestData()

        val viewModel = HskBrowserViewModel(
            dictionaryRepository = dictRepo,
            localVocabularyRepository = userVocRepo,
            studyRepository = studyRepo,
            initialLevel = 1
        )

        val state = viewModel.uiState.filter { !it.isLoading && it.words.isNotEmpty() }.first()
        val entry = state.words.first { it.vocabulary.chinese == "好" }
        assertFalse(entry.isStudied)

        // 1. Add "好" to study
        viewModel.toggleStudy(entry)

        val updatedState = viewModel.uiState.first { s: HskBrowserUiState ->
            s.words.any { it.vocabulary.chinese == "好" && it.isStudied } && s.levelStats[1]?.studiedWords == 1
        }

        val updatedEntry = updatedState.words.first { it.vocabulary.chinese == "好" }
        assertTrue(updatedEntry.isStudied)
        assertEquals(0, updatedEntry.leitnerBox)
        assertEquals(1, updatedState.levelStats[1]?.studiedWords)

        // Verify in database
        val inUserDb = userVocRepo.getVocabularyForChinese("好")
        assertEquals(1, inUserDb.size)
        val leitnerState = studyRepo.getLeitnerState("好")
        assertNotNull(leitnerState)
        assertEquals(0, leitnerState?.box)

        // 2. Remove "好" from study
        viewModel.toggleStudy(updatedEntry)

        val removedState = viewModel.uiState.first { s: HskBrowserUiState ->
            s.words.any { it.vocabulary.chinese == "好" && !it.isStudied } && s.levelStats[1]?.studiedWords == 0
        }

        val removedEntry = removedState.words.first { it.vocabulary.chinese == "好" }
        assertFalse(removedEntry.isStudied)
        assertEquals(0, removedState.levelStats[1]?.studiedWords)
        assertNull(studyRepo.getLeitnerState("好"))
    }

    @Test
    fun testSearchQuery_filtersWords() = runBlocking {
        seedTestData()

        val viewModel = HskBrowserViewModel(
            dictionaryRepository = dictRepo,
            localVocabularyRepository = userVocRepo,
            studyRepository = studyRepo,
            initialLevel = 1
        )

        viewModel.uiState.filter { !it.isLoading && it.words.isNotEmpty() }.first()

        // Search by pinyin
        viewModel.onSearchQueryChange("ni3")
        val pinyinState = viewModel.uiState.filter { it.searchQuery == "ni3" }.first()
        assertEquals(1, pinyinState.words.size)
        assertEquals("你", pinyinState.words.first().vocabulary.chinese)

        // Search by English
        viewModel.onSearchQueryChange("good")
        val engState = viewModel.uiState.filter { it.searchQuery == "good" }.first()
        assertEquals(1, engState.words.size)
        assertEquals("好", engState.words.first().vocabulary.chinese)
    }

    @Test
    fun testSwitchLevel_loadsLevel2Words() = runBlocking {
        seedTestData()

        val viewModel = HskBrowserViewModel(
            dictionaryRepository = dictRepo,
            localVocabularyRepository = userVocRepo,
            studyRepository = studyRepo,
            initialLevel = 1
        )

        viewModel.uiState.filter { !it.isLoading && it.words.isNotEmpty() }.first()

        // Switch to HSK 2
        viewModel.selectLevel(2)
        val level2State = viewModel.uiState.filter { !it.isLoading && it.selectedLevel == 2 }.first()
        assertEquals(2, level2State.selectedLevel)
        assertEquals(2, level2State.words.size)
        assertTrue(level2State.words.all { it.hskLevel == 2 })
        assertTrue(level2State.words.any { it.vocabulary.chinese == "时间" })
        assertTrue(level2State.words.any { it.vocabulary.chinese == "机场" })
    }
}

