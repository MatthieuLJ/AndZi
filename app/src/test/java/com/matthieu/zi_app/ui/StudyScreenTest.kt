package com.matthieu.zi_app.ui

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.matthieu.zi_app.ComposeTestActivity
import com.matthieu.zi_app.Pinyin
import com.matthieu.zi_app.ui.study.QuizType
import com.matthieu.zi_app.ui.study.StudyCard
import com.matthieu.zi_app.ui.study.StudyScreenContent
import com.matthieu.zi_app.ui.study.StudyUiState
import com.matthieu.zi_app.ui.theme.AndZiTheme
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class StudyScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComposeTestActivity>()

    @Before
    fun setUp() {
        Pinyin.prepare_pinyin_process()
    }

    @Test
    fun testClozeQuiz_showsEnglishTranslationOnTop_notFullChineseWord() {
        val card = StudyCard(
            hanzi = "图书馆",
            pinyin = "tu2shu1guan3",
            english = listOf("library", "building for books"),
            quizType = QuizType.CLOZE,
            wordSlots = listOf("图", "书", "馆"),
            scrambledTiles = listOf("馆", "院", "楼", "店"),
            clozeTargetIndex = 2
        )
        val state = StudyUiState(
            queue = listOf(card),
            currentIndex = 0,
            isLoading = false,
            isAnswerRevealed = false
        )

        var selectedTile: String? = null

        composeTestRule.setContent {
            AndZiTheme {
                StudyScreenContent(
                    uiState = state,
                    onNavigateBack = {},
                    onToneSelected = {},
                    onEnglishSelected = {},
                    onClozeSelected = { selectedTile = it },
                    onRateGrade = {},
                    onNextCard = {}
                )
            }
        }

        // Verify English translation is shown in the top card, and prompt is present
        composeTestRule.onNodeWithText("library; building for books").assertExists()
        composeTestRule.onNodeWithText("Fill in the missing character").assertExists()

        // Verify that the blank slot "?" is present
        composeTestRule.onNodeWithText("?").assertExists()

        // Click candidate tile
        composeTestRule.onNodeWithText("馆").performClick()
        org.junit.Assert.assertEquals("馆", selectedTile)
    }

    @Test
    fun testWordAssemblyQuiz_showsEnglishTranslationOnTop_andHidesSolution() {
        val card = StudyCard(
            hanzi = "明天",
            pinyin = "ming2tian1",
            english = listOf("tomorrow"),
            quizType = QuizType.WORD_ASSEMBLY,
            wordSlots = listOf("明", "天"),
            scrambledTiles = listOf("天", "月", "明", "昨")
        )
        val state = StudyUiState(
            queue = listOf(card),
            currentIndex = 0,
            isLoading = false,
            isAnswerRevealed = false,
            assembledChars = emptyList(),
            usedTileIndices = emptyList()
        )

        var selectedIndex: Int? = null
        var selectedTile: String? = null

        composeTestRule.setContent {
            AndZiTheme {
                StudyScreenContent(
                    uiState = state,
                    onNavigateBack = {},
                    onToneSelected = {},
                    onEnglishSelected = {},
                    onClozeSelected = {},
                    onAssemblyTileSelected = { idx, tile ->
                        selectedIndex = idx
                        selectedTile = tile
                    },
                    onRateGrade = {},
                    onNextCard = {}
                )
            }
        }

        // Verify English translation is shown on top for Word Assembly
        composeTestRule.onNodeWithText("tomorrow").assertExists()
        composeTestRule.onNodeWithText("Assemble the compound word").assertExists()

        // Verify distractors are shown as tiles
        composeTestRule.onNodeWithText("月").assertExists()
        composeTestRule.onNodeWithText("昨").assertExists()

        // Tap a tile and verify assembly selection callback
        composeTestRule.onNodeWithText("明").performClick()
        org.junit.Assert.assertEquals(2, selectedIndex)
        org.junit.Assert.assertEquals("明", selectedTile)
    }

    @Test
    fun testToneQuiz_showsHanziOnTop() {
        val card = StudyCard(
            hanzi = "水",
            pinyin = "shui3",
            tone = 3,
            english = listOf("water"),
            quizType = QuizType.TONE
        )
        val state = StudyUiState(
            queue = listOf(card),
            currentIndex = 0,
            isLoading = false,
            isAnswerRevealed = false
        )

        composeTestRule.setContent {
            AndZiTheme {
                StudyScreenContent(
                    uiState = state,
                    onNavigateBack = {},
                    onToneSelected = {},
                    onEnglishSelected = {},
                    onClozeSelected = {},
                    onRateGrade = {},
                    onNextCard = {}
                )
            }
        }

        // For single-character tone quiz, the Hanzi should be displayed on top
        composeTestRule.onNodeWithText("水").assertExists()
        composeTestRule.onNodeWithText("Swipe or tap the correct tone").assertExists()
    }

    @Test
    fun testRevealedCard_showsFullChineseWordAndContinueButton() {
        val card = StudyCard(
            hanzi = "图书馆",
            pinyin = "tu2shu1guan3",
            english = listOf("library"),
            quizType = QuizType.CLOZE,
            wordSlots = listOf("图", "书", "馆"),
            scrambledTiles = listOf("馆", "院", "楼", "店"),
            clozeTargetIndex = 2
        )
        val state = StudyUiState(
            queue = listOf(card),
            currentIndex = 0,
            isLoading = false,
            isAnswerRevealed = true,
            isCorrect = true
        )

        var nextClicked = false

        composeTestRule.setContent {
            AndZiTheme {
                StudyScreenContent(
                    uiState = state,
                    onNavigateBack = {},
                    onToneSelected = {},
                    onEnglishSelected = {},
                    onClozeSelected = {},
                    onNextCard = { nextClicked = true }
                )
            }
        }

        // When revealed, full word is shown on top
        composeTestRule.onNodeWithText("图书馆").assertExists()
        composeTestRule.onNodeWithText("Correct!").assertExists()
        composeTestRule.onNodeWithText("Continue").assertExists().performScrollTo().assertHasClickAction().performClick()
        assertTrue(nextClicked)
    }

    @Test
    fun testEnglishMcqQuiz_multiLineOptions_renderAndCanBeSelected() {
        val longOption1 = "to take care of someone or something thoroughly in daily life"
        val longOption2 = "to look after children, patients or elderly people with great care"
        val card = StudyCard(
            hanzi = "照顾",
            pinyin = "zhao4gu5",
            english = listOf(longOption1),
            quizType = QuizType.ENGLISH,
            mcqOptions = listOf(longOption1, longOption2, "water, liquid", "to learn, to study")
        )
        val state = StudyUiState(
            queue = listOf(card),
            currentIndex = 0,
            isLoading = false,
            isAnswerRevealed = false
        )

        var selectedMeaning: String? = null

        composeTestRule.setContent {
            AndZiTheme {
                StudyScreenContent(
                    uiState = state,
                    onNavigateBack = {},
                    onToneSelected = {},
                    onEnglishSelected = { selectedMeaning = it },
                    onClozeSelected = {},
                    onRateGrade = {},
                    onNextCard = {}
                )
            }
        }

        // Verify Hanzi is on top
        composeTestRule.onNodeWithText("照顾").assertExists()

        // Verify long multi-line option is rendered and clickable
        composeTestRule.onNodeWithText(longOption2).assertExists().performClick()
        org.junit.Assert.assertEquals(longOption2, selectedMeaning)
    }

    @Test
    fun testToneQuiz_withContextWords_displaysContextWords() {
        val card = StudyCard(
            hanzi = "长",
            pinyin = "chang2",
            tone = 2,
            english = listOf("long"),
            quizType = QuizType.TONE,
            contextWords = listOf("长度", "长城")
        )
        val state = StudyUiState(
            queue = listOf(card),
            currentIndex = 0,
            isLoading = false,
            isAnswerRevealed = false
        )

        composeTestRule.setContent {
            AndZiTheme {
                StudyScreenContent(
                    uiState = state,
                    onNavigateBack = {},
                    onToneSelected = {},
                    onEnglishSelected = {},
                    onClozeSelected = {},
                    onRateGrade = {},
                    onNextCard = {}
                )
            }
        }

        // Verify Hanzi is on top
        composeTestRule.onNodeWithText("长").assertExists()
        // Verify Context words are rendered
        composeTestRule.onNodeWithText("In words: 长度, 长城").assertExists()
        // Verify tone prompt
        composeTestRule.onNodeWithText("Swipe or tap the correct tone").assertExists()
    }

    @Test
    fun testSingleCharClozeRevealed_showsWholeWordAndStudyWordsUsingCharacter() {
        val card = StudyCard(
            hanzi = "明",
            pinyin = "ming2",
            displayPinyin = "ming2tian1",
            english = listOf("tomorrow"),
            quizType = QuizType.CLOZE,
            wordSlots = listOf("明", "天"),
            scrambledTiles = listOf("明", "月", "日"),
            clozeTargetIndex = 0,
            relatedStudyWords = listOf(
                com.matthieu.zi_app.Vocabulary("明白", "ming2bai5", arrayListOf("clear", "to understand")),
                com.matthieu.zi_app.Vocabulary("聪明", "cong1ming5", arrayListOf("intelligent", "clever"))
            )
        )
        val state = StudyUiState(
            queue = listOf(card),
            currentIndex = 0,
            isLoading = false,
            isAnswerRevealed = true,
            isCorrect = true
        )

        composeTestRule.setContent {
            AndZiTheme {
                StudyScreenContent(
                    uiState = state,
                    onNavigateBack = {},
                    onToneSelected = {},
                    onEnglishSelected = {},
                    onClozeSelected = {},
                    onNextCard = {}
                )
            }
        }

        // Top card displays the whole word "明天" (not just "明") and its translation "tomorrow" below
        composeTestRule.onNodeWithText("明天").assertExists()
        composeTestRule.onNodeWithText("tomorrow").assertExists()

        // Below, dictionary-style view shows study words using "明"
        composeTestRule.onNodeWithText("Study words using 明").performScrollTo().assertExists()
        composeTestRule.onNodeWithText("明白").performScrollTo().assertExists()
        composeTestRule.onNodeWithText("clear; to understand").assertExists()
        composeTestRule.onNodeWithText("聪明").performScrollTo().assertExists()
        composeTestRule.onNodeWithText("intelligent; clever").assertExists()
    }

    @Test
    fun testEnglishQuizWrongAnswer_showsAcceptButtonAndInvokesCallback() {
        val card = StudyCard(
            hanzi = "高兴",
            pinyin = "gao1xing4",
            english = listOf("happy", "cheerful"),
            quizType = QuizType.ENGLISH,
            mcqOptions = listOf("cheerful", "glad", "water", "mountain")
        )
        val state = StudyUiState(
            queue = listOf(card),
            currentIndex = 0,
            isLoading = false,
            isAnswerRevealed = true,
            isCorrect = false,
            selectedOption = "glad"
        )

        var acceptClicked = false

        composeTestRule.setContent {
            AndZiTheme {
                StudyScreenContent(
                    uiState = state,
                    onNavigateBack = {},
                    onToneSelected = {},
                    onEnglishSelected = {},
                    onClozeSelected = {},
                    onAcceptAnswer = { acceptClicked = true },
                    onNextCard = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Needs Review").assertExists()
        composeTestRule.onNodeWithText("Your answer: glad").assertExists()
        composeTestRule.onNodeWithText("My answer was acceptable")
            .performScrollTo()
            .assertExists()
            .assertHasClickAction()
            .performClick()

        assertTrue(acceptClicked)
    }
}

