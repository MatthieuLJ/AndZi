package com.matthieu.zi_app.ui

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.matthieu.zi_app.ComposeTestActivity
import com.matthieu.zi_app.Vocabulary
import com.matthieu.zi_app.ui.dictionary.DictionaryResultCard
import com.matthieu.zi_app.ui.dictionary.WordDetailSheetContent
import com.matthieu.zi_app.ui.theme.AndZiTheme
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class DictionaryScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComposeTestActivity>()

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        com.matthieu.zi_app.Pinyin.prepare_pinyin_process()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testWordDetailSheetContent_displaysMultiCharWordAndPinyinBelow() {
        val word = Vocabulary(
            "普通话",
            "pu3tong1hua4",
            arrayListOf("Mandarin Chinese", "Standard Chinese")
        )

        var studyClicked = false
        var toggledTranslation: String? = null

        composeTestRule.setContent {
            AndZiTheme {
                WordDetailSheetContent(
                    word = word,
                    polyReadings = emptyList(),
                    constituents = listOf("普", "通", "话"),
                    compounds = emptyList(),
                    isInStudy = false,
                    currentBox = null,
                    selectedTranslations = setOf("Mandarin Chinese"),
                    onToggleStudy = { studyClicked = true },
                    onToggleTranslation = { toggledTranslation = it },
                    onSelectConstituent = {},
                    onSelectCompound = {}
                )
            }
        }

        // Verify Chinese word, Pinyin, and Definitions
        composeTestRule.onNodeWithText("普通话").assertExists()
        composeTestRule.onNodeWithText("pǔtōnghuà").assertExists()
        composeTestRule.onNodeWithText("1. Mandarin Chinese").assertExists()
        composeTestRule.onNodeWithText("In Study").assertExists()
        composeTestRule.onNodeWithText("2. Standard Chinese").assertExists()

        // Verify clicking translation triggers onToggleTranslation
        composeTestRule.onNodeWithText("2. Standard Chinese").performClick()
        org.junit.Assert.assertEquals("Standard Chinese", toggledTranslation)

        // Verify Study button exists and can be clicked
        val studyButton = composeTestRule.onNodeWithText("Study").assertExists()
        studyButton.performClick()
        assertTrue(studyClicked)
    }

    @Test
    fun testWordDetailSheetContent_whenStudying_showsStudyingButtonAndBoxBadge() {
        val word = Vocabulary(
            "图书馆",
            "tu2shu1guan3",
            arrayListOf("library")
        )

        composeTestRule.setContent {
            AndZiTheme {
                WordDetailSheetContent(
                    word = word,
                    polyReadings = emptyList(),
                    constituents = listOf("图", "书", "馆"),
                    compounds = emptyList(),
                    isInStudy = true,
                    currentBox = 3,
                    selectedTranslations = setOf("library"),
                    onToggleStudy = {},
                    onToggleTranslation = {},
                    onSelectConstituent = {},
                    onSelectCompound = {}
                )
            }
        }

        // Verify Chinese word and Pinyin
        composeTestRule.onNodeWithText("图书馆").assertExists()
        composeTestRule.onNodeWithText("túshūguǎn").assertExists()

        // Verify "SRS: Box 3" badge and "Studying" button
        composeTestRule.onNodeWithText("SRS: Box 3").assertExists()
        composeTestRule.onNodeWithText("Studying").assertExists().assertHasClickAction()
    }

    @Test
    fun testDictionaryResultCard_rendersMultiCharWord() {
        val word = Vocabulary(
            "对不起",
            "dui4bu5qi3",
            arrayListOf("I'm sorry", "excuse me")
        )

        var cardClicked = false

        composeTestRule.setContent {
            AndZiTheme {
                DictionaryResultCard(
                    word = word,
                    onClick = { cardClicked = true }
                )
            }
        }

        composeTestRule.onNodeWithText("对不起").assertExists()
        composeTestRule.onNodeWithText("duìbuqǐ").assertExists()
        composeTestRule.onNodeWithText("I'm sorry; excuse me").assertExists()

        composeTestRule.onNodeWithText("对不起").performClick()
        assertTrue(cardClicked)
    }

    @Test
    fun testDictionaryScreen_showsSuggestionBannerAndShuffleButton() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val dictDb = com.matthieu.zi_app.data.local.AndZiDatabase.createInMemory(context)
        val userDb = com.matthieu.zi_app.data.local.AndZiDatabase.createInMemory(context)
        val dictRepo = com.matthieu.zi_app.data.repository.VocabularyRepository(dictDb.vocabularyDao())
        val userVocRepo = com.matthieu.zi_app.data.repository.VocabularyRepository(userDb.vocabularyDao())
        val studyRepo = com.matthieu.zi_app.data.repository.StudyRepository(userDb.leitnerDao())

        kotlinx.coroutines.runBlocking {
            dictRepo.insertHskVocab(
                listOf(
                    com.matthieu.zi_app.data.local.entity.HskVocabEntity("学生", hskLevel = 1, freqRank = 100, isChar = 0)
                )
            )
            dictRepo.addVocabulary(Vocabulary("学生", "xue2sheng1", arrayListOf("student")))
        }

        val viewModel = com.matthieu.zi_app.ui.dictionary.DictionaryViewModel(dictRepo, userVocRepo, studyRepo, debounceMillis = 0L)

        composeTestRule.setContent {
            AndZiTheme {
                com.matthieu.zi_app.ui.dictionary.DictionaryScreen(viewModel = viewModel)
            }
        }

        composeTestRule.waitUntil(3000) {
            viewModel.uiState.value.isAutoSuggested && viewModel.uiState.value.suggestedWord != null
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("✨ Suggested for you").assertExists()
        composeTestRule.onNodeWithContentDescription("Suggest another word").assertExists().assertHasClickAction()

        dictDb.close()
        userDb.close()
    }

    @Test
    fun testWordDetailSheetContent_withManyTranslations_canScrollAndSelect() {
        val word = Vocabulary(
            "等",
            "deng3",
            arrayListOf(
                "class; rank; grade",
                "equal to; same as",
                "to wait for; to await",
                "et cetera; and so on",
                "after (a condition is satisfied); once",
                "plural suffix for pronouns and nouns"
            )
        )
        var toggledTranslation: String? = null

        composeTestRule.setContent {
            AndZiTheme {
                WordDetailSheetContent(
                    word = word,
                    polyReadings = emptyList(),
                    constituents = emptyList(),
                    compounds = listOf(Vocabulary("等等", "deng3deng3", arrayListOf("etc."))),
                    isInStudy = true,
                    currentBox = 0,
                    selectedTranslations = setOf("to wait for; to await", "class; rank; grade"),
                    onToggleStudy = {},
                    onToggleTranslation = { toggledTranslation = it },
                    onSelectConstituent = {},
                    onSelectCompound = {}
                )
            }
        }

        composeTestRule.onNodeWithText("等").assertExists()
        composeTestRule.onNodeWithText("děng").assertExists()
        composeTestRule.onNodeWithText("SRS: Box 0").assertExists()

        // Verify last definition exists and can be scrolled to and clicked
        val lastDef = composeTestRule.onNodeWithText("6. plural suffix for pronouns and nouns")
        lastDef.performScrollTo()
        lastDef.assertExists()
        lastDef.performClick()
        org.junit.Assert.assertEquals("plural suffix for pronouns and nouns", toggledTranslation)

        // Verify compound words are also reachable via scroll
        val compoundNode = composeTestRule.onNodeWithText("等等")
        compoundNode.performScrollTo()
        compoundNode.assertExists()
    }

    @Test
    fun testWordDetailSheetContent_canTypeAndAddCustomTranslation() {
        val word = Vocabulary(
            "照顾",
            "zhao4gu5",
            arrayListOf("to take care of")
        )
        var addedCustomTranslation: String? = null

        composeTestRule.setContent {
            AndZiTheme {
                WordDetailSheetContent(
                    word = word,
                    polyReadings = emptyList(),
                    constituents = listOf("照", "顾"),
                    compounds = emptyList(),
                    isInStudy = true,
                    currentBox = 0,
                    selectedTranslations = setOf("to take care of"),
                    onToggleStudy = {},
                    onToggleTranslation = {},
                    onSelectConstituent = {},
                    onSelectCompound = {},
                    onAddCustomTranslation = { addedCustomTranslation = it }
                )
            }
        }

        val inputField = composeTestRule.onNodeWithText("Add custom translation...")
        inputField.performScrollTo()
        inputField.assertExists()
        inputField.performTextInput("to care for")

        val addButton = composeTestRule.onNodeWithText("Add")
        addButton.performScrollTo()
        addButton.performClick()

        org.junit.Assert.assertEquals("to care for", addedCustomTranslation)
    }
}
