package com.matthieu.zi_app.ui

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
    fun testWordAssemblyQuiz_showsEnglishTranslationOnTop() {
        val card = StudyCard(
            hanzi = "明天",
            pinyin = "ming2tian1",
            english = listOf("tomorrow"),
            quizType = QuizType.WORD_ASSEMBLY,
            wordSlots = listOf("明", "天"),
            scrambledTiles = listOf("天", "明")
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

        // Verify English translation is shown on top for Word Assembly
        composeTestRule.onNodeWithText("tomorrow").assertExists()
        composeTestRule.onNodeWithText("Assemble the compound word").assertExists()
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
    fun testRevealedCard_showsFullChineseWordAndRatingButtons() {
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

        // When revealed, full word is shown on top
        composeTestRule.onNodeWithText("图书馆").assertExists()
        composeTestRule.onNodeWithText("Correct!").assertExists()
        composeTestRule.onNodeWithText("Good").assertExists().assertHasClickAction()
    }
}
