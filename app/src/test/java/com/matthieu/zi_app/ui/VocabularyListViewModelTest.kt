package com.matthieu.zi_app.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.matthieu.zi_app.Vocabulary
import com.matthieu.zi_app.data.local.AndZiDatabase
import com.matthieu.zi_app.data.repository.StudyRepository
import com.matthieu.zi_app.data.repository.VocabularyRepository
import com.matthieu.zi_app.domain.srs.ReviewGrade
import com.matthieu.zi_app.ui.vocabulary.ActiveCardTab
import com.matthieu.zi_app.ui.vocabulary.ActiveVocabularyItem
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class VocabularyListViewModelTest {

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
    fun testLoadActiveVocabularyAndSearchFilter() = runBlocking {
        val word1 = Vocabulary("明", "ming2", arrayListOf("bright", "clear"))
        val word2 = Vocabulary("明天", "ming2tian1", arrayListOf("tomorrow"))
        val word3 = Vocabulary("好", "hao3", arrayListOf("good", "well"))

        userVocRepo.addVocabulary(word1)
        userVocRepo.addVocabulary(word2)
        userVocRepo.addVocabulary(word3)

        studyRepo.recordReviewGrade("明", ReviewGrade.GOOD)
        studyRepo.recordReviewGrade("明天", ReviewGrade.AGAIN)

        val viewModel = VocabularyListViewModel(userVocRepo, dictRepo, studyRepo)

        val state = withTimeout(3000) {
            viewModel.uiState.filter { it.items.size == 3 }.first()
        }
        assertEquals(3, state.items.size)
        // Verify "天" (which is only part of "明天" and has no translation) is not in the active vocabulary list
        assertTrue(state.items.none { it.vocabulary.chinese == "天" })

        // Filter by Hanzi
        viewModel.onSearchQueryChanged("明")
        val filteredHanzi = viewModel.uiState.value.filteredItems
        assertEquals(2, filteredHanzi.size)

        // Filter by Pinyin
        viewModel.onSearchQueryChanged("hao")
        val filteredPinyin = viewModel.uiState.value.filteredItems
        assertEquals(1, filteredPinyin.size)
        assertEquals("好", filteredPinyin.first().vocabulary.chinese)

        // Filter by English
        viewModel.onSearchQueryChanged("tomorrow")
        val filteredEnglish = viewModel.uiState.value.filteredItems
        assertEquals(1, filteredEnglish.size)
        assertEquals("明天", filteredEnglish.first().vocabulary.chinese)
    }

    @Test
    fun testWordsWithoutTranslationsAreExcluded() = runBlocking {
        // Add a compound word that introduces characters into char_pinyin
        val word = Vocabulary("中国人", "zhong1guo2ren2", arrayListOf("Chinese person"))
        userVocRepo.addVocabulary(word)

        val viewModel = VocabularyListViewModel(userVocRepo, dictRepo, studyRepo)
        val state = withTimeout(3000) {
            viewModel.uiState.filter { it.items.isNotEmpty() }.first()
        }

        // Only "中国人" should appear because individual characters "中", "国", "人" do not have their own translations
        assertEquals(1, state.items.size)
        assertEquals("中国人", state.items.first().vocabulary.chinese)
        assertTrue(state.items.first().vocabulary.english.isNotEmpty())
    }

    @Test
    fun testSelectWordAndToggleDefinitions() = runBlocking {
        val word = Vocabulary("苹果", "ping2guo3", arrayListOf("apple", "fruit"))
        userVocRepo.addVocabulary(word)
        studyRepo.recordReviewGrade("苹果", ReviewGrade.GOOD)

        val viewModel = VocabularyListViewModel(userVocRepo, dictRepo, studyRepo)
        val initialItems = withTimeout(3000) {
            viewModel.uiState.filter { it.items.isNotEmpty() }.first()
        }
        val targetItem = initialItems.items.first { it.vocabulary.chinese == "苹果" }

        viewModel.selectItem(targetItem)
        val selectedState = withTimeout(3000) {
            viewModel.uiState.filter { it.selectedWord?.chinese == "苹果" }.first()
        }
        assertEquals("苹果", selectedState.selectedWord?.chinese)
        assertEquals(2, selectedState.constituentCharacters.size)
        assertEquals("苹", selectedState.constituentCharacters[0])
        assertEquals("果", selectedState.constituentCharacters[1])
        assertTrue(selectedState.selectedTranslations.contains("apple"))
        assertTrue(selectedState.selectedTranslations.contains("fruit"))

        // Toggle "fruit" off
        viewModel.toggleTranslationForStudy("fruit")
        val updatedState = withTimeout(3000) {
            viewModel.uiState.filter { !it.selectedTranslations.contains("fruit") }.first()
        }
        assertEquals(setOf("apple"), updatedState.selectedTranslations)

        // Verify in user DB
        val updatedVoc = userVocRepo.getVocabularyForChinese("苹果").first()
        assertEquals(listOf("apple"), updatedVoc.english)
    }

    @Test
    fun testRemoveFromStudy() = runBlocking {
        val word = Vocabulary("北京", "bei3jing1", arrayListOf("Beijing", "capital"))
        userVocRepo.addVocabulary(word)
        studyRepo.recordReviewGrade("北京", ReviewGrade.GOOD)

        val viewModel = VocabularyListViewModel(userVocRepo, dictRepo, studyRepo)
        val initialItems = withTimeout(3000) {
            viewModel.uiState.filter { it.items.any { item -> item.vocabulary.chinese == "北京" } }.first()
        }
        val targetItem = initialItems.items.first { it.vocabulary.chinese == "北京" }

        viewModel.selectItem(targetItem)
        val selectedState = withTimeout(3000) {
            viewModel.uiState.filter { it.selectedWord?.chinese == "北京" && it.isInStudyQueue }.first()
        }
        assertTrue(selectedState.isInStudyQueue)

        viewModel.removeFromStudy(targetItem.vocabulary)

        val updatedState = withTimeout(3000) {
            viewModel.uiState.filter { !it.isInStudyQueue && it.items.none { item -> item.vocabulary.chinese == "北京" } }.first()
        }
        assertFalse(updatedState.isInStudyQueue)

        // Verify in DB
        val leitnerState = studyRepo.getLeitnerState("北京")
        assertEquals(null, leitnerState)
    }

    @Test
    fun testBoxRangeFilteringAndAdaptation() = runBlocking {
        val word1 = Vocabulary("一", "yi1", arrayListOf("one"))
        val word3 = Vocabulary("三", "san1", arrayListOf("three"))
        val word5 = Vocabulary("五", "wu3", arrayListOf("five"))
        val word7 = Vocabulary("七", "qi1", arrayListOf("seven"))

        userVocRepo.addVocabulary(word1)
        userVocRepo.addVocabulary(word3)
        userVocRepo.addVocabulary(word5)
        userVocRepo.addVocabulary(word7)

        // Advance words to specific boxes
        // word1: Box 1
        studyRepo.recordReviewGrade("一", ReviewGrade.GOOD)
        // word3: Box 3
        repeat(3) { studyRepo.recordReviewGrade("三", ReviewGrade.GOOD) }
        // word5: Box 5
        repeat(5) { studyRepo.recordReviewGrade("五", ReviewGrade.GOOD) }
        // word7: Box 7
        repeat(7) { studyRepo.recordReviewGrade("七", ReviewGrade.GOOD) }

        val viewModel = VocabularyListViewModel(userVocRepo, dictRepo, studyRepo)
        val state = withTimeout(3000) {
            viewModel.uiState.filter { it.items.size == 4 }.first()
        }

        // Verify bounds adapt strictly to existing boxes (1 to 7, not up to 9)
        assertEquals(1, state.minAvailableBox)
        assertEquals(7, state.maxAvailableBox)
        assertEquals(setOf(1, 3, 5, 7), state.boxCounts.keys)
        assertFalse(state.boxCounts.containsKey(8))
        assertFalse(state.boxCounts.containsKey(9))
        assertFalse(state.boxCounts.containsKey(0))

        // Filter for range 2..5 (should include Box 3 and Box 5)
        viewModel.onBoxRangeChanged(2..5)
        val rangeState = viewModel.uiState.value
        assertEquals(2..5, rangeState.selectedBoxRange)
        assertEquals(2, rangeState.filteredItems.size)
        assertTrue(rangeState.filteredItems.any { it.vocabulary.chinese == "三" })
        assertTrue(rangeState.filteredItems.any { it.vocabulary.chinese == "五" })
        assertFalse(rangeState.filteredItems.any { it.vocabulary.chinese == "一" })
        assertFalse(rangeState.filteredItems.any { it.vocabulary.chinese == "七" })

        // Single box selection (Box 7)
        viewModel.onBoxRangeChanged(7..7)
        val singleBoxState = viewModel.uiState.value
        assertEquals(7..7, singleBoxState.selectedBoxRange)
        assertEquals(1, singleBoxState.filteredItems.size)
        assertEquals("七", singleBoxState.filteredItems.first().vocabulary.chinese)

        // Clear range filter
        viewModel.onBoxRangeChanged(null)
        val resetState = viewModel.uiState.value
        assertEquals(null, resetState.selectedBoxRange)
        assertEquals(4, resetState.filteredItems.size)

        // Full range selection (1..7) simplifies to null (All)
        viewModel.onBoxRangeChanged(1..7)
        assertEquals(null, viewModel.uiState.value.selectedBoxRange)
        assertEquals(4, viewModel.uiState.value.filteredItems.size)
    }

    @Test
    fun testCombinedBoxRangeAndSearchQuery() = runBlocking {
        val wordA = Vocabulary("大", "da4", arrayListOf("big"))
        val wordB = Vocabulary("小", "xiao3", arrayListOf("small"))
        val wordC = Vocabulary("大家", "da4jia1", arrayListOf("everyone"))

        userVocRepo.addVocabulary(wordA)
        userVocRepo.addVocabulary(wordB)
        userVocRepo.addVocabulary(wordC)

        studyRepo.recordReviewGrade("大", ReviewGrade.GOOD) // Box 1
        repeat(2) { studyRepo.recordReviewGrade("小", ReviewGrade.GOOD) } // Box 2
        repeat(2) { studyRepo.recordReviewGrade("大家", ReviewGrade.GOOD) } // Box 2

        val viewModel = VocabularyListViewModel(userVocRepo, dictRepo, studyRepo)
        withTimeout(3000) {
            viewModel.uiState.filter { it.items.size == 3 }.first()
        }

        // Filter by Box 2..2
        viewModel.onBoxRangeChanged(2..2)
        assertEquals(2, viewModel.uiState.value.filteredItems.size)

        // Search for "da" while box 2..2 filter is active
        viewModel.onSearchQueryChanged("da")
        val combinedState = viewModel.uiState.value
        assertEquals(1, combinedState.filteredItems.size)
        assertEquals("大家", combinedState.filteredItems.first().vocabulary.chinese)
    }

    @Test
    fun testConstituentCharacterHasNoTranslationsSelectedWhenNotInStudy() = runBlocking {
        // Compound word is in user study list
        val compoundWord = Vocabulary("明天", "ming2tian1", arrayListOf("tomorrow"))
        userVocRepo.addVocabulary(compoundWord)
        studyRepo.recordReviewGrade("明天", ReviewGrade.GOOD)

        // Constituent character "明" exists in dictionary with definitions, but was not added to user vocabulary
        val charWord = Vocabulary("明", "ming2", arrayListOf("bright", "clear"))
        dictRepo.addVocabulary(charWord)

        val viewModel = VocabularyListViewModel(userVocRepo, dictRepo, studyRepo)
        val initial = withTimeout(3000) {
            viewModel.uiState.filter { it.items.isNotEmpty() }.first()
        }

        // Clicking constituent character "明"
        viewModel.selectCharacterFromConstituent("明")
        val selectedState = withTimeout(3000) {
            viewModel.uiState.filter { it.selectedWord?.chinese == "明" }.first()
        }

        // The character definitions are visible, but NONE should be selected as in-study
        assertEquals("明", selectedState.selectedWord?.chinese)
        assertTrue(selectedState.selectedWord?.english?.contains("bright") == true)
        assertTrue(selectedState.selectedWord?.english?.contains("clear") == true)
        assertEquals(emptySet<String>(), selectedState.selectedTranslations)
        assertFalse(selectedState.isInStudyQueue)
        assertNull(selectedState.currentBox)
    }

    @Test
    fun testActiveCharacterCardsTabAndBoxDistribution() = runBlocking {
        // Add two compound words sharing character '学'
        val word1 = Vocabulary("学习", "xue2xi2", arrayListOf("to study"))
        val word2 = Vocabulary("学生", "xue2sheng1", arrayListOf("student"))
        userVocRepo.addVocabulary(word1)
        userVocRepo.addVocabulary(word2)

        // Advance words to Box 7
        repeat(7) {
            studyRepo.recordReviewGrade("学习", ReviewGrade.GOOD)
            studyRepo.recordReviewGrade("学生", ReviewGrade.GOOD)
        }

        // Advance constituent character '学' to Box 8
        repeat(8) {
            studyRepo.recordReviewGrade("学", ReviewGrade.GOOD)
        }

        val viewModel = VocabularyListViewModel(userVocRepo, dictRepo, studyRepo)
        val state = withTimeout(3000) {
            viewModel.uiState.filter { it.items.size == 2 && it.characterItems.isNotEmpty() }.first()
        }

        // On Active Word Cards tab (default):
        assertEquals(ActiveCardTab.WORDS, state.currentTab)
        assertEquals(2, state.items.size)
        // Word cards only go up to Box 7
        assertEquals(2, state.boxCounts[7])
        assertNull(state.boxCounts[8])

        // Switch to Active Character Cards tab:
        viewModel.selectTab(ActiveCardTab.CHARACTERS)
        val charState = withTimeout(3000) {
            viewModel.uiState.filter { it.currentTab == ActiveCardTab.CHARACTERS }.first()
        }

        // Characters list includes '学', '习', '生'
        assertTrue(charState.characterItems.any { it.character == "学" })
        assertTrue(charState.characterItems.any { it.character == "习" })
        assertTrue(charState.characterItems.any { it.character == "生" })

        // Character '学' is in Box 8 and lists both containing words
        val xueChar = charState.characterItems.first { it.character == "学" }
        assertEquals(8, xueChar.leitnerState?.box)
        assertTrue(xueChar.pinyins.contains("xue2"))
        assertTrue(xueChar.containingWords.contains("学习"))
        assertTrue(xueChar.containingWords.contains("学生"))

        // Box counts on Character tab now shows Box 8!
        assertEquals(1, charState.boxCounts[8])

        // Search on character cards tab
        viewModel.onSearchQueryChanged("生")
        val searched = withTimeout(3000) {
            viewModel.uiState.filter { it.query == "生" }.first()
        }
        assertTrue(searched.filteredCharacterItems.any { it.character == "生" })

        // Box filter on character cards tab for Box 8
        viewModel.onSearchQueryChanged("")
        viewModel.onBoxRangeChanged(8..8)
        val boxFiltered = withTimeout(3000) {
            viewModel.uiState.filter { it.selectedBoxRange == 8..8 }.first()
        }
        assertEquals(1, boxFiltered.filteredCharacterItems.size)
        assertEquals("学", boxFiltered.filteredCharacterItems.first().character)
    }
}
