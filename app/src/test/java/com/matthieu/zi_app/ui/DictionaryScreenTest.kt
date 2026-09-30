package com.matthieu.zi_app.ui

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.matthieu.zi_app.ComposeTestActivity
import com.matthieu.zi_app.Vocabulary
import com.matthieu.zi_app.ui.dictionary.DictionaryResultCard
import com.matthieu.zi_app.ui.dictionary.WordDetailSheetContent
import com.matthieu.zi_app.ui.theme.AndZiTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DictionaryScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComposeTestActivity>()

    @org.junit.Before
    fun setUp() {
        com.matthieu.zi_app.Pinyin.prepare_pinyin_process()
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
}
