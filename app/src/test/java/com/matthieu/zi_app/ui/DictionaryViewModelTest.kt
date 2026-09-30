package com.matthieu.zi_app.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.matthieu.zi_app.Vocabulary
import com.matthieu.zi_app.data.local.AndZiDatabase
import com.matthieu.zi_app.data.repository.StudyRepository
import com.matthieu.zi_app.data.repository.VocabularyRepository
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
}
