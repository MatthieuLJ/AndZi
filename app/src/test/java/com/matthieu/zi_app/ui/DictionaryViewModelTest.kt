package com.matthieu.zi_app.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.matthieu.zi_app.Vocabulary
import com.matthieu.zi_app.data.local.AndZiDatabase
import com.matthieu.zi_app.data.local.entity.HskVocabEntity
import com.matthieu.zi_app.data.repository.StudyRepository
import com.matthieu.zi_app.data.repository.VocabularyRepository
import com.matthieu.zi_app.domain.srs.ReviewGrade
import com.matthieu.zi_app.ui.dictionary.DictionaryViewModel
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
class DictionaryViewModelTest {

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

    @Test
    fun testDictionarySearchAndDecomposition() = runBlocking {
        val word1 = Vocabulary("明", "ming2", arrayListOf("bright", "clear"))
        val word2 = Vocabulary("明天", "ming2tian1", arrayListOf("tomorrow"))
        dictRepo.addVocabulary(word1)
        dictRepo.addVocabulary(word2)

        val viewModel = DictionaryViewModel(dictRepo, userVocRepo, studyRepo, debounceMillis = 0L)

        viewModel.onSearchQueryChanged("明")
        val state = withTimeout(3000) {
            viewModel.uiState.filter { !it.isSearching && it.results.size == 2 }.first()
        }
        assertEquals(2, state.results.size)

        // Select "明天" (compound word) -> constituents should include "明" and "天"
        viewModel.selectWord(word2)
        val selectedState = withTimeout(3000) {
            viewModel.uiState.filter { it.selectedWord?.chinese == "明天" }.first()
        }
        assertEquals("明天", selectedState.selectedWord?.chinese)
        assertEquals(2, selectedState.constituentCharacters.size)
        assertEquals("明", selectedState.constituentCharacters[0])
        assertEquals("天", selectedState.constituentCharacters[1])

        // Add to study queue
        viewModel.toggleAddToStudy(word2)
        val studiedState = withTimeout(3000) {
            viewModel.uiState.filter { it.isInStudyQueue }.first()
        }
        assertTrue(studiedState.isInStudyQueue)
    }

    @Test
    fun testToggleTranslationForStudy() = runBlocking {
        val word = Vocabulary(
            "照顾",
            "zhao4gu5",
            arrayListOf("to take care of", "to look after", "to attend to")
        )
        dictRepo.addVocabulary(word)

        val viewModel = DictionaryViewModel(dictRepo, userVocRepo, studyRepo, debounceMillis = 0L)
        viewModel.selectWord(word)

        val initialSelectedState = withTimeout(3000) {
            viewModel.uiState.filter { it.selectedWord?.chinese == "照顾" }.first()
        }
        assertEquals(emptySet<String>(), initialSelectedState.selectedTranslations)

        // 1. Toggle "to take care of" to add it
        viewModel.toggleTranslationForStudy("to take care of")
        val state1 = withTimeout(3000) {
            viewModel.uiState.filter { it.selectedTranslations.contains("to take care of") }.first()
        }
        assertTrue(state1.isInStudyQueue)
        assertEquals(setOf("to take care of"), state1.selectedTranslations)

        // Verify in user DB
        val userVoc1 = userVocRepo.getVocabularyForChinese("照顾").first()
        assertEquals(listOf("to take care of"), userVoc1.english)

        // 2. Toggle "to look after" to also add it
        viewModel.toggleTranslationForStudy("to look after")
        val state2 = withTimeout(3000) {
            viewModel.uiState.filter { it.selectedTranslations.contains("to look after") }.first()
        }
        assertEquals(setOf("to take care of", "to look after"), state2.selectedTranslations)

        val userVoc2 = userVocRepo.getVocabularyForChinese("照顾").first()
        assertEquals(2, userVoc2.english.size)
        assertTrue(userVoc2.english.contains("to take care of"))
        assertTrue(userVoc2.english.contains("to look after"))

        // 3. Toggle "to take care of" again to remove it
        viewModel.toggleTranslationForStudy("to take care of")
        val state3 = withTimeout(3000) {
            viewModel.uiState.filter { !it.selectedTranslations.contains("to take care of") }.first()
        }
        assertEquals(setOf("to look after"), state3.selectedTranslations)

        val userVoc3 = userVocRepo.getVocabularyForChinese("照顾").first()
        assertEquals(listOf("to look after"), userVoc3.english)
    }

    @Test
    fun testToggleAddToStudyRemovesWordAndDeselectsTranslationsWhenAlreadyStudying() = runBlocking {
        val word = Vocabulary(
            "学习",
            "xue2xi2",
            arrayListOf("to study", "to learn")
        )
        dictRepo.addVocabulary(word)

        val viewModel = DictionaryViewModel(dictRepo, userVocRepo, studyRepo, debounceMillis = 0L)
        viewModel.selectWord(word)

        // 1. Initial State: not in study
        val initialSelectedState = withTimeout(3000) {
            viewModel.uiState.filter { it.selectedWord?.chinese == "学习" }.first()
        }
        assertFalse(initialSelectedState.isInStudyQueue)
        assertEquals(emptySet<String>(), initialSelectedState.selectedTranslations)

        // 2. Click "Study" button -> added to study, all translations selected
        viewModel.toggleAddToStudy(word)
        val addedState = withTimeout(3000) {
            viewModel.uiState.filter { it.isInStudyQueue }.first()
        }
        assertTrue(addedState.isInStudyQueue)
        assertEquals(setOf("to study", "to learn"), addedState.selectedTranslations)
        assertEquals(0, addedState.currentBox)

        // Verify in DB
        val userVoc = userVocRepo.getVocabularyForChinese("学习")
        assertEquals(1, userVoc.size)
        assertEquals(listOf("to study", "to learn"), userVoc.first().english)
        val leitner = studyRepo.getLeitnerState("学习")
        assertEquals(0, leitner?.box)

        // 3. Click "Studying" button -> removed from study, all translations deselected
        viewModel.toggleAddToStudy(word)
        val removedState = withTimeout(3000) {
            viewModel.uiState.filter { !it.isInStudyQueue }.first()
        }
        assertFalse(removedState.isInStudyQueue)
        assertNull(removedState.currentBox)
        assertEquals(emptySet<String>(), removedState.selectedTranslations)

        // Verify removed in DB
        val userVocAfter = userVocRepo.getVocabularyForChinese("学习")
        assertTrue(userVocAfter.isEmpty())
        val leitnerAfter = studyRepo.getLeitnerState("学习")
        assertNull(leitnerAfter)
    }

    @Test
    fun testAddingMultiCharacterWordAddsWordAndSubCharactersToStudyQueueWithoutEnglishTranslations() = runBlocking {
        val word = Vocabulary(
            "明天",
            "ming2tian1",
            arrayListOf("tomorrow")
        )
        dictRepo.addVocabulary(word)

        val viewModel = DictionaryViewModel(dictRepo, userVocRepo, studyRepo, debounceMillis = 0L)
        viewModel.selectWord(word)
        viewModel.toggleAddToStudy(word)

        val state = withTimeout(3000) {
            viewModel.uiState.filter { it.isInStudyQueue }.first()
        }
        assertTrue(state.isInStudyQueue)

        // Verify compound word has translation in user DB
        val wordInUserDb = userVocRepo.getVocabularyForChinese("明天")
        assertEquals(1, wordInUserDb.size)
        assertEquals(listOf("tomorrow"), wordInUserDb.first().english)

        // Verify individual sub-characters do NOT have translations in user DB
        val mingInUserDb = userVocRepo.getVocabularyForChinese("明")
        assertTrue(mingInUserDb.isEmpty() || mingInUserDb.all { it.english.isEmpty() })
        val tianInUserDb = userVocRepo.getVocabularyForChinese("天")
        assertTrue(tianInUserDb.isEmpty() || tianInUserDb.all { it.english.isEmpty() })

        // Check study queue in studyRepo: includes "明天", "明", and "天"
        val queue = studyRepo.getStudyQueue(limit = 10, now = System.currentTimeMillis() + 1000L)
        assertTrue(queue.contains("明天"))
        assertTrue(queue.contains("明"))
        assertTrue(queue.contains("天"))
        assertEquals(3, queue.size)

        // Verify box counts: 3 items in Box 0
        val boxCounts = studyRepo.getBoxCounts().associate { it.box to it.count }
        assertEquals(3, boxCounts[0])
    }

    @Test
    fun testInitialWordSuggestionLoadsAndExecutesSearch() = runBlocking {
        dictRepo.insertHskVocab(
            listOf(
                com.matthieu.zi_app.data.local.entity.HskVocabEntity("你", hskLevel = 1, freqRank = 1, isChar = 1)
            )
        )
        dictRepo.addVocabulary(Vocabulary("你", "ni3", arrayListOf("you")))

        val viewModel = DictionaryViewModel(dictRepo, userVocRepo, studyRepo, debounceMillis = 0L)

        val state = withTimeout(3000) {
            viewModel.uiState.filter { it.isAutoSuggested && it.query.isNotEmpty() && !it.isSearching && it.results.isNotEmpty() }.first()
        }

        assertEquals("你", state.query)
        assertEquals("你", state.suggestedWord)
        assertTrue(state.isAutoSuggested)
        assertNotNull(state.suggestionReason)
        assertEquals(1, state.results.size)
        assertEquals("你", state.results[0].chinese)
    }

    @Test
    fun testRefreshSuggestionUpdatesQueryAndReason() = runBlocking {
        dictRepo.insertHskVocab(
            listOf(
                com.matthieu.zi_app.data.local.entity.HskVocabEntity("你", hskLevel = 1, freqRank = 1, isChar = 1),
                com.matthieu.zi_app.data.local.entity.HskVocabEntity("好", hskLevel = 1, freqRank = 2, isChar = 1)
            )
        )
        dictRepo.addVocabulary(Vocabulary("你", "ni3", arrayListOf("you")))
        dictRepo.addVocabulary(Vocabulary("好", "hao3", arrayListOf("good")))

        val viewModel = DictionaryViewModel(dictRepo, userVocRepo, studyRepo, debounceMillis = 0L)

        // Wait for initial suggestion
        withTimeout(3000) {
            viewModel.uiState.filter { it.isAutoSuggested && it.query.isNotEmpty() }.first()
        }

        // Trigger refresh and wait for suggested query and search to complete
        viewModel.refreshSuggestion()

        val refreshedState = withTimeout(3000) {
            viewModel.uiState.filter { it.isAutoSuggested && it.query in listOf("你", "好") && !it.isSearching && it.results.isNotEmpty() }.first()
        }

        assertTrue(refreshedState.query in listOf("你", "好"))
        assertTrue(refreshedState.isAutoSuggested)
        assertNotNull(refreshedState.suggestionReason)
    }

    @Test
    fun testMultipleRefreshSuggestionsProvideDifferentWords() = runBlocking {
        dictRepo.insertHskVocab(
            listOf(
                com.matthieu.zi_app.data.local.entity.HskVocabEntity("你", hskLevel = 1, freqRank = 1, isChar = 1),
                com.matthieu.zi_app.data.local.entity.HskVocabEntity("好", hskLevel = 1, freqRank = 2, isChar = 1),
                com.matthieu.zi_app.data.local.entity.HskVocabEntity("他", hskLevel = 1, freqRank = 3, isChar = 1)
            )
        )
        dictRepo.addVocabulary(Vocabulary("你", "ni3", arrayListOf("you")))
        dictRepo.addVocabulary(Vocabulary("好", "hao3", arrayListOf("good")))
        dictRepo.addVocabulary(Vocabulary("他", "ta1", arrayListOf("he")))

        val viewModel = DictionaryViewModel(dictRepo, userVocRepo, studyRepo, debounceMillis = 0L)

        // 1. Initial suggestion
        val s1 = withTimeout(3000) {
            viewModel.uiState.filter { it.isAutoSuggested && it.query.isNotEmpty() && !it.isSearching }.first()
        }
        val firstWord = s1.query

        // 2. First refresh -> must be different from firstWord
        viewModel.refreshSuggestion()
        val s2 = withTimeout(3000) {
            viewModel.uiState.filter { it.isAutoSuggested && it.query != firstWord && !it.isSearching }.first()
        }
        val secondWord = s2.query
        assertTrue(secondWord != firstWord)

        // 3. Second refresh -> must be different from both firstWord and secondWord
        viewModel.refreshSuggestion()
        val s3 = withTimeout(3000) {
            viewModel.uiState.filter { it.isAutoSuggested && it.query != firstWord && it.query != secondWord && !it.isSearching }.first()
        }
        val thirdWord = s3.query
        assertTrue(thirdWord != firstWord && thirdWord != secondWord)
    }

    @Test
    fun testManualSearchClearsAutoSuggestedFlag() = runBlocking {
        dictRepo.insertHskVocab(
            listOf(
                com.matthieu.zi_app.data.local.entity.HskVocabEntity("你", hskLevel = 1, freqRank = 1, isChar = 1)
            )
        )
        dictRepo.addVocabulary(Vocabulary("你", "ni3", arrayListOf("you")))
        dictRepo.addVocabulary(Vocabulary("他", "ta1", arrayListOf("he", "him")))

        val viewModel = DictionaryViewModel(dictRepo, userVocRepo, studyRepo, debounceMillis = 0L)

        // Wait for initial suggestion
        withTimeout(3000) {
            viewModel.uiState.filter { it.isAutoSuggested }.first()
        }

        // User manually types "他"
        viewModel.onSearchQueryChanged("他")
        val manualState = withTimeout(3000) {
            viewModel.uiState.filter { it.query == "他" && !it.isSearching }.first()
        }

        assertFalse(manualState.isAutoSuggested)
        assertNull(manualState.suggestionReason)
    }

    @Test
    fun testDictionarySearchSortsByHskAndFrequency() = runBlocking {
        // Insert words in CEDICT repository
        dictRepo.addVocabulary(Vocabulary("打字", "da3zi4", arrayListOf("to type")))
        dictRepo.addVocabulary(Vocabulary("大", "da4", arrayListOf("big", "large")))
        dictRepo.addVocabulary(Vocabulary("大家", "da4jia1", arrayListOf("everyone", "all")))
        dictRepo.addVocabulary(Vocabulary("大意", "da4yi4", arrayListOf("general idea; careless")))

        dictRepo.insertHskVocab(
            listOf(
                com.matthieu.zi_app.data.local.entity.HskVocabEntity("大", hskLevel = 1, freqRank = 20, isChar = 1),
                com.matthieu.zi_app.data.local.entity.HskVocabEntity("大家", hskLevel = 1, freqRank = 45, isChar = 0),
                com.matthieu.zi_app.data.local.entity.HskVocabEntity("打字", hskLevel = 3, freqRank = 800, isChar = 0),
                com.matthieu.zi_app.data.local.entity.HskVocabEntity("大意", hskLevel = 5, freqRank = 2500, isChar = 0)
            )
        )

        val viewModel = DictionaryViewModel(dictRepo, userVocRepo, studyRepo, debounceMillis = 0L)

        // Search for "大"
        viewModel.onSearchQueryChanged("大")
        val state = withTimeout(3000) {
            viewModel.uiState.filter { it.query == "大" && !it.isSearching && it.results.size == 3 }.first()
        }

        // Exact match ("大") comes first, then "大家" (HSK 1, freq 45), then "大意" (HSK 5, freq 2500)
        assertEquals("大", state.results[0].chinese)
        assertEquals("大家", state.results[1].chinese)
        assertEquals("大意", state.results[2].chinese)
    }

    @Test
    fun testAddCustomTranslationPersistsAndSelectsTranslation() = runBlocking {
        val word = Vocabulary(
            "照顾",
            "zhao4gu5",
            arrayListOf("to take care of", "to look after")
        )
        dictRepo.addVocabulary(word)

        val viewModel = DictionaryViewModel(dictRepo, userVocRepo, studyRepo, debounceMillis = 0L)
        viewModel.selectWord(word)

        withTimeout(3000) {
            viewModel.uiState.filter { it.selectedWord?.chinese == "照顾" }.first()
        }

        // Add a custom translation manually
        viewModel.addCustomTranslation("  to care for  ")

        val stateAfterCustom = withTimeout(3000) {
            viewModel.uiState.filter { it.selectedTranslations.contains("to care for") }.first()
        }

        assertTrue(stateAfterCustom.isInStudyQueue)
        assertEquals(setOf("to care for"), stateAfterCustom.selectedTranslations)
        assertTrue(stateAfterCustom.selectedWord!!.english.contains("to care for"))

        // Verify in user DB
        val userVoc = userVocRepo.getVocabularyForChinese("照顾").first()
        assertEquals(listOf("to care for"), userVoc.english)

        // Clear selection and re-select the dictionary word -> custom translation should still be merged and selected
        viewModel.clearSelection()
        viewModel.selectWord(word)
        val reselectedState = withTimeout(3000) {
            viewModel.uiState.filter { it.selectedWord?.chinese == "照顾" }.first()
        }
        assertTrue(reselectedState.selectedWord!!.english.contains("to care for"))
        assertTrue(reselectedState.selectedTranslations.contains("to care for"))
    }

    @Test
    fun testWordNotInStudyHasNoTranslationsSelectedEvenWithLeitnerRecord() = runBlocking {
        // Compound word is in user study list
        val compoundWord = Vocabulary("明天", "ming2tian1", arrayListOf("tomorrow"))
        dictRepo.addVocabulary(compoundWord)
        userVocRepo.addVocabulary(compoundWord)
        studyRepo.recordReviewGrade("明天", ReviewGrade.GOOD)

        // Constituent character "明" exists in dictionary
        val charWord = Vocabulary("明", "ming2", arrayListOf("bright", "clear"))
        dictRepo.addVocabulary(charWord)

        // Even if "明" was reviewed in character-based quizzes (so it has a Leitner record)
        studyRepo.recordReviewGrade("明", ReviewGrade.GOOD)

        val viewModel = DictionaryViewModel(dictRepo, userVocRepo, studyRepo, debounceMillis = 0L)

        // Directly select character "明"
        viewModel.selectWord(charWord)
        val selectedState = withTimeout(3000) {
            viewModel.uiState.filter { it.selectedWord?.chinese == "明" }.first()
        }

        // None of the dictionary translations should be pre-selected as "In Study"
        assertEquals("明", selectedState.selectedWord?.chinese)
        assertTrue(selectedState.selectedWord?.english?.contains("bright") == true)
        assertTrue(selectedState.selectedWord?.english?.contains("clear") == true)
        assertEquals(emptySet<String>(), selectedState.selectedTranslations)
        assertFalse(selectedState.isInStudyQueue)
        assertNull(selectedState.currentBox)
    }

    @Test
    fun testSelectCharacterFromConstituent_dismissesSheetAndSearchesCharacter() = runBlocking {
        val word1 = Vocabulary("明天", "ming2tian1", arrayListOf("tomorrow"))
        val word2 = Vocabulary("天", "tian1", arrayListOf("day", "sky"))
        val word3 = Vocabulary("天气", "tian1qi4", arrayListOf("weather"))
        dictRepo.addVocabulary(word1)
        dictRepo.addVocabulary(word2)
        dictRepo.addVocabulary(word3)

        val viewModel = DictionaryViewModel(dictRepo, userVocRepo, studyRepo, debounceMillis = 0L)

        // User opens "明天"
        viewModel.selectWord(word1)
        withTimeout(3000) {
            viewModel.uiState.filter { it.selectedWord?.chinese == "明天" }.first()
        }

        // Tap constituent character "天"
        viewModel.selectCharacterFromConstituent("天")
        val stateAfterTap = withTimeout(3000) {
            viewModel.uiState.filter { it.query == "天" && !it.isSearching && it.results.isNotEmpty() }.first()
        }

        // Bottom sheet is dismissed
        assertNull(stateAfterTap.selectedWord)
        // Search query is set to "天"
        assertEquals("天", stateAfterTap.query)
        // Results include both words starting with "天" and words containing "天"
        val resultWords = stateAfterTap.results.map { it.chinese }
        assertTrue(resultWords.contains("天"))
        assertTrue(resultWords.contains("天气"))
        assertTrue(resultWords.contains("明天"))
    }
}
