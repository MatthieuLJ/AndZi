package com.matthieu.zi_app.ui

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.matthieu.zi_app.ComposeTestActivity
import com.matthieu.zi_app.Pinyin
import com.matthieu.zi_app.domain.study.quiz.QuizAction
import com.matthieu.zi_app.ui.study.CollocationPair
import com.matthieu.zi_app.ui.study.QuizType
import com.matthieu.zi_app.ui.study.StudyCard
import com.matthieu.zi_app.ui.study.StudyScreenContent
import com.matthieu.zi_app.ui.study.StudyUiState
import com.matthieu.zi_app.ui.theme.AndZiTheme
import org.junit.Assert.assertEquals
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
    fun testWordAssemblyQuiz_repeatedCharacter_tileRemainsEnabledUntilCountReached() {
        val card = StudyCard(
            hanzi = "妈妈",
            pinyin = "ma1ma5",
            english = listOf("mother"),
            quizType = QuizType.WORD_ASSEMBLY,
            wordSlots = listOf("妈", "妈"),
            scrambledTiles = listOf("妈", "爸", "姐")
        )

        // When '妈' (tile 0) is used once, it should still be enabled because it appears twice in wordSlots
        val stateOneUsed = StudyUiState(
            queue = listOf(card),
            currentIndex = 0,
            isLoading = false,
            isAnswerRevealed = false,
            assembledChars = listOf("妈"),
            usedTileIndices = listOf(0)
        )

        composeTestRule.setContent {
            AndZiTheme {
                StudyScreenContent(
                    uiState = stateOneUsed,
                    onNavigateBack = {},
                    onToneSelected = {},
                    onEnglishSelected = {},
                    onClozeSelected = {},
                    onAssemblyTileSelected = { _, _ -> },
                    onRateGrade = {},
                    onNextCard = {}
                )
            }
        }

        // '妈' is used once, so candidate tile should still be enabled
        composeTestRule.onAllNodesWithText("妈").onLast().assertIsEnabled()
        // Non-repeated distractor '爸' is not used, so it's also enabled
        composeTestRule.onNodeWithText("爸").assertIsEnabled()
    }

    @Test
    fun testWordAssemblyQuiz_repeatedCharacter_tileBecomesDisabledWhenCountReached() {
        val card = StudyCard(
            hanzi = "妈妈",
            pinyin = "ma1ma5",
            english = listOf("mother"),
            quizType = QuizType.WORD_ASSEMBLY,
            wordSlots = listOf("妈", "妈"),
            scrambledTiles = listOf("妈", "爸", "姐")
        )

        // When '妈' (tile 0) is used twice, it should be disabled
        val stateTwoUsed = StudyUiState(
            queue = listOf(card),
            currentIndex = 0,
            isLoading = false,
            isAnswerRevealed = false,
            assembledChars = listOf("妈", "妈"),
            usedTileIndices = listOf(0, 0)
        )

        composeTestRule.setContent {
            AndZiTheme {
                StudyScreenContent(
                    uiState = stateTwoUsed,
                    onNavigateBack = {},
                    onToneSelected = {},
                    onEnglishSelected = {},
                    onClozeSelected = {},
                    onAssemblyTileSelected = { _, _ -> },
                    onRateGrade = {},
                    onNextCard = {}
                )
            }
        }

        // '妈' is used twice (the allowed count), so candidate tile should be disabled
        composeTestRule.onAllNodesWithText("妈").onLast().assertIsNotEnabled()
    }

    @Test
    fun testPinyinQuiz_withTone_displaysSyllableAndToneSections() {
        val card = StudyCard(
            hanzi = "水",
            pinyin = "shui3",
            syllable = "shui",
            tone = 3,
            english = listOf("water"),
            quizType = QuizType.PINYIN,
            mcqOptions = listOf("shui", "huo", "mu", "tu")
        )
        val state = StudyUiState(
            queue = listOf(card),
            currentIndex = 0,
            isLoading = false,
            isAnswerRevealed = false
        )

        var selectedSyllable: String? = null
        var selectedTone: Int? = null

        composeTestRule.setContent {
            AndZiTheme {
                StudyScreenContent(
                    uiState = state,
                    onNavigateBack = {},
                    onToneSelected = {},
                    onEnglishSelected = {},
                    onClozeSelected = {},
                    onPinyinSyllableSelected = { selectedSyllable = it },
                    onPinyinToneSelected = { selectedTone = it },
                    onRateGrade = {},
                    onNextCard = {}
                )
            }
        }

        // Verify headers and prompt
        composeTestRule.onNodeWithText("Select Pinyin and tone").assertExists()
        composeTestRule.onNodeWithText("1. Select Pinyin").assertExists()
        composeTestRule.onNodeWithText("2. Select Tone").assertExists()

        // Verify syllables exist
        composeTestRule.onNodeWithText("shui").assertExists()
        composeTestRule.onNodeWithText("huo").assertExists()

        // Verify tones exist
        composeTestRule.onNodeWithText("1st  Flat ¯").assertExists()
        composeTestRule.onNodeWithText("3rd  Dip ˇ").assertExists()

        // Perform clicks and assert callbacks
        composeTestRule.onNodeWithText("shui").performClick()
        org.junit.Assert.assertEquals("shui", selectedSyllable)

        composeTestRule.onNodeWithText("3rd  Dip ˇ").performScrollTo().performClick()
        org.junit.Assert.assertEquals(3, selectedTone)
    }

    @Test
    fun testPinyinQuiz_neutralTone_displaysOnlySyllablesWithoutToneSection() {
        val card = StudyCard(
            hanzi = "的",
            pinyin = "de5",
            syllable = "de",
            tone = 0, // neutral tone
            english = listOf("possessive particle"),
            quizType = QuizType.PINYIN,
            mcqOptions = listOf("de", "da", "dou", "di")
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
                    onPinyinSyllableSelected = {},
                    onPinyinToneSelected = {},
                    onRateGrade = {},
                    onNextCard = {}
                )
            }
        }

        // Verify prompt
        composeTestRule.onNodeWithText("Select the correct Pinyin").assertExists()

        // Verify tone section does NOT exist
        composeTestRule.onNodeWithText("2. Select Tone").assertDoesNotExist()
        composeTestRule.onNodeWithText("1st  Flat ¯").assertDoesNotExist()

        // Verify syllables exist
        composeTestRule.onNodeWithText("de").assertExists()
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
    fun testToneQuiz_withContextWords_hiddenByDefault_andRevealedOnClick() {
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
            isAnswerRevealed = false,
            isContextWordsRevealed = false
        )
        var revealClicked = false

        composeTestRule.setContent {
            AndZiTheme {
                StudyScreenContent(
                    uiState = state,
                    onNavigateBack = {},
                    onToneSelected = {},
                    onEnglishSelected = {},
                    onClozeSelected = {},
                    onRateGrade = {},
                    onRevealContextWords = { revealClicked = true },
                    onNextCard = {}
                )
            }
        }

        // Verify Hanzi is on top
        composeTestRule.onNodeWithText("长").assertExists()
        // Verify "As in…" button exists and words are not yet shown
        composeTestRule.onNodeWithText("As in…").assertExists().performClick()
        assertTrue(revealClicked)
        composeTestRule.onNodeWithText("As in: 长度, 长城").assertDoesNotExist()
        // Verify tone prompt
        composeTestRule.onNodeWithText("Swipe or tap the correct tone").assertExists()
    }

    @Test
    fun testToneQuiz_withContextWordsRevealed_displaysContextWords() {
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
            isAnswerRevealed = false,
            isContextWordsRevealed = true
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

        composeTestRule.onNodeWithText("长").assertExists()
        composeTestRule.onNodeWithText("As in: 长度, 长城").assertExists()
        composeTestRule.onNodeWithText("As in…").assertDoesNotExist()
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
    fun testPinyinQuizRevealed_showsStudyWordsUsingCharacter() {
        val card = StudyCard(
            hanzi = "长",
            pinyin = "chang2",
            syllable = "chang",
            tone = 2,
            english = listOf("long"),
            quizType = QuizType.PINYIN,
            mcqOptions = listOf("chang", "duan", "gao", "di"),
            relatedStudyWords = listOf(
                com.matthieu.zi_app.Vocabulary("长", "chang2", arrayListOf("long")),
                com.matthieu.zi_app.Vocabulary("长城", "chang2cheng2", arrayListOf("Great Wall"))
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

        composeTestRule.onNodeWithText("Study words using 长").performScrollTo().assertExists()
        composeTestRule.onNodeWithText("长城").performScrollTo().assertExists()
        composeTestRule.onNodeWithText("Great Wall").assertExists()
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

    @Test
    fun testWordAssemblyQuiz_whenIncorrect_displaysEnteredWrongAnswerOnResultPage() {
        val card = StudyCard(
            hanzi = "学习",
            pinyin = "xue2xi2",
            english = listOf("to study", "to learn"),
            quizType = QuizType.WORD_ASSEMBLY,
            wordSlots = listOf("学", "习"),
            scrambledTiles = listOf("学", "校", "习")
        )
        val state = StudyUiState(
            queue = listOf(card),
            currentIndex = 0,
            isLoading = false,
            isAnswerRevealed = true,
            isCorrect = false,
            selectedOption = "学校"
        )

        composeTestRule.setContent {
            AndZiTheme {
                StudyScreenContent(
                    uiState = state,
                    onNavigateBack = {},
                    onToneSelected = {},
                    onEnglishSelected = {},
                    onClozeSelected = {},
                    onAcceptAnswer = {},
                    onNextCard = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Needs Review").assertExists()
        composeTestRule.onNodeWithText("Your answer: 学校").assertExists()
    }

    @Test
    fun testStudyFinishedView_backToDashboardButtonCallsOnReturnHome() {
        val state = StudyUiState(
            queue = emptyList(),
            isLoading = false,
            isSessionFinished = true
        )
        var returnHomeClicked = false

        composeTestRule.setContent {
            AndZiTheme {
                StudyScreenContent(
                    uiState = state,
                    onNavigateBack = {},
                    onReturnHome = { returnHomeClicked = true },
                    onToneSelected = {},
                    onEnglishSelected = {},
                    onClozeSelected = {},
                    onNextCard = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Session Completed!").assertExists()
        composeTestRule.onNodeWithText("Back to Dashboard")
            .performScrollTo()
            .assertExists()
            .assertHasClickAction()
            .performClick()
        composeTestRule.waitForIdle()

        assertTrue(returnHomeClicked)
    }

    @Test
    fun testStudyFinishedView_closeIconCallsOnNavigateBack() {
        val state = StudyUiState(
            queue = emptyList(),
            isLoading = false,
            isSessionFinished = true
        )
        var navigateBackClicked = false

        composeTestRule.setContent {
            AndZiTheme {
                StudyScreenContent(
                    uiState = state,
                    onNavigateBack = { navigateBackClicked = true },
                    onReturnHome = {},
                    onToneSelected = {},
                    onEnglishSelected = {},
                    onClozeSelected = {},
                    onNextCard = {}
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Close Practice")
            .assertExists()
            .assertHasClickAction()
            .performClick()

        assertTrue(navigateBackClicked)
    }

    @Test
    fun testPairMatchView_rendersTilesAndInteracts() {
        val pairs = listOf(
            CollocationPair("飞机", "fēijī", listOf("airplane"), "飞", "机"),
            CollocationPair("苹果", "píngguǒ", listOf("apple"), "苹", "果"),
            CollocationPair("汽车", "qìchē", listOf("car"), "汽", "车")
        )
        val card = StudyCard(
            hanzi = "飞机",
            pinyin = "fei1ji1",
            english = listOf("airplane"),
            quizType = QuizType.PAIR_MATCH,
            matchPairs = pairs,
            leftMatchTiles = listOf("飞", "苹", "汽"),
            rightMatchTiles = listOf("机", "果", "车")
        )
        val state = StudyUiState(
            queue = listOf(card),
            currentIndex = 0,
            isLoading = false,
            isAnswerRevealed = false,
            matchedWords = setOf("飞机")
        )

        var selectedLeft: Int? = null
        var selectedRight: Int? = null

        composeTestRule.setContent {
            AndZiTheme {
                StudyScreenContent(
                    uiState = state,
                    onNavigateBack = {},
                    onToneSelected = {},
                    onEnglishSelected = {},
                    onClozeSelected = {},
                    onMatchLeftSelected = { selectedLeft = it },
                    onMatchRightSelected = { selectedRight = it },
                    onNextCard = {}
                )
            }
        }

        // Verify prompt and headers
        composeTestRule.onNodeWithText("Match the characters to form words")
            .assertExists()
        composeTestRule.onNodeWithText("Form Word Pairs")
            .assertExists()
        composeTestRule.onNodeWithText("1 / 3 pairs matched")
            .assertExists()

        // Tap left tile '苹' (index 1)
        composeTestRule.onNodeWithText("苹")
            .performScrollTo()
            .assertExists()
            .performClick()
        composeTestRule.waitForIdle()
        assertEquals(1, selectedLeft)

        // Tap right tile '果' (index 1)
        composeTestRule.onNodeWithText("果")
            .performScrollTo()
            .assertExists()
            .performClick()
        composeTestRule.waitForIdle()
        assertEquals(1, selectedRight)
    }

    @Test
    fun testStudyScreenContent_dispatchesUnifiedQuizAction() {
        val card = StudyCard(
            hanzi = "好",
            pinyin = "hao3",
            english = listOf("good"),
            currentBox = 1,
            quizType = QuizType.ENGLISH,
            mcqOptions = listOf("good", "bad", "water", "fire")
        )
        val state = StudyUiState(
            queue = listOf(card),
            currentIndex = 0,
            remainingDueCount = 1,
            isAnswerRevealed = false
        )

        var emittedAction: QuizAction? = null

        composeTestRule.setContent {
            AndZiTheme {
                StudyScreenContent(
                    uiState = state,
                    onNavigateBack = {},
                    onQuizAction = { emittedAction = it },
                    onNextCard = {}
                )
            }
        }

        // Tap the option "good"
        composeTestRule.onNodeWithText("good")
            .performScrollTo()
            .performClick()
        composeTestRule.waitForIdle()

        assertTrue(emittedAction is QuizAction.EnglishSelected)
        assertEquals("good", (emittedAction as QuizAction.EnglishSelected).option)
    }
}

