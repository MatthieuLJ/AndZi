package com.matthieu.zi_app.ui

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.matthieu.zi_app.ComposeTestActivity
import com.matthieu.zi_app.LeitnerState
import com.matthieu.zi_app.Vocabulary
import com.matthieu.zi_app.ui.vocabulary.ActiveVocabularyCard
import com.matthieu.zi_app.ui.vocabulary.ActiveVocabularyItem
import com.matthieu.zi_app.ui.vocabulary.ActiveWordDetailSheetContent
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class VocabularyListScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComposeTestActivity>()

    @Test
    fun testActiveVocabularyCard_displaysWordAndBoxBadge() {
        val word = Vocabulary("你好", "ni3hao3", arrayListOf("hello", "hi"))
        val item = ActiveVocabularyItem(
            vocabulary = word,
            leitnerState = LeitnerState(
                hanzi = "你好",
                box = 3,
                expTime = System.currentTimeMillis() + 100000,
                numTested = 5,
                numWrong = 1,
                streak = 2,
                dateAdded = System.currentTimeMillis()
            )
        )

        var cardClicked = false

        composeTestRule.setContent {
            ActiveVocabularyCard(
                item = item,
                onClick = { cardClicked = true }
            )
        }

        composeTestRule.onNodeWithText("你好").assertExists()
        composeTestRule.onNodeWithText("nǐhǎo").assertExists()
        composeTestRule.onNodeWithText("hello; hi").assertExists()
        composeTestRule.onNodeWithText("Box 3").assertExists()

        composeTestRule.onNodeWithText("你好").performClick()
        assertTrue(cardClicked)
    }

    @Test
    fun testActiveWordDetailSheetContent_displaysDetailsAndActions() {
        val word = Vocabulary("苹果", "ping2guo3", arrayListOf("apple", "fruit"))
        var toggledTrans = ""
        var removed = false
        var resetSRS = false

        composeTestRule.setContent {
            ActiveWordDetailSheetContent(
                word = word,
                polyReadings = emptyList(),
                constituents = listOf("苹", "果"),
                compounds = emptyList(),
                isInStudy = true,
                currentBox = 2,
                selectedTranslations = setOf("apple"),
                onToggleStudy = {},
                onToggleTranslation = { toggledTrans = it },
                onSelectConstituent = {},
                onSelectCompound = {},
                onResetSrs = { resetSRS = true },
                onRemoveFromStudy = { removed = true }
            )
        }

        composeTestRule.onNodeWithText("苹果").assertExists()
        composeTestRule.onNodeWithText("SRS: Box 2").assertExists()
        composeTestRule.onNodeWithText("1. apple").assertExists()
        composeTestRule.onNodeWithText("2. fruit").assertExists()
        composeTestRule.onNodeWithText("Constituent Characters").assertExists()
        composeTestRule.onNodeWithText("苹").assertExists()
        composeTestRule.onNodeWithText("果").assertExists()

        // Test definition toggle click
        composeTestRule.onNodeWithText("2. fruit").performClick()
        assertTrue(toggledTrans == "fruit")

        // Test Remove button click
        composeTestRule.onNodeWithText("Remove").performClick()
        assertTrue(removed)

        // Test Reset SRS button click
        composeTestRule.onNodeWithText("Reset SRS").performClick()
        assertTrue(resetSRS)
    }

    @Test
    fun testActiveWordDetailSheetContent_withManyTranslations_canScrollAndInteract() {
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
        var toggledTrans = ""
        var removed = false

        composeTestRule.setContent {
            ActiveWordDetailSheetContent(
                word = word,
                polyReadings = emptyList(),
                constituents = emptyList(),
                compounds = listOf(Vocabulary("等等", "deng3deng3", arrayListOf("etc."))),
                isInStudy = true,
                currentBox = 0,
                selectedTranslations = setOf("class; rank; grade"),
                onToggleStudy = {},
                onToggleTranslation = { toggledTrans = it },
                onSelectConstituent = {},
                onSelectCompound = {},
                onResetSrs = {},
                onRemoveFromStudy = { removed = true }
            )
        }

        composeTestRule.onNodeWithText("等").assertExists()
        composeTestRule.onNodeWithText("SRS: Box 0").assertExists()

        // Scroll to and click the 6th translation
        val lastDef = composeTestRule.onNodeWithText("6. plural suffix for pronouns and nouns")
        lastDef.performScrollTo()
        lastDef.assertExists()
        lastDef.performClick()
        org.junit.Assert.assertEquals("plural suffix for pronouns and nouns", toggledTrans)

        // Scroll to Remove button and click
        val removeButton = composeTestRule.onNodeWithText("Remove")
        removeButton.performScrollTo()
        removeButton.performClick()
        assertTrue(removed)

        // Verify compound words are reachable via scroll
        val compound = composeTestRule.onNodeWithText("等等")
        compound.performScrollTo()
        compound.assertExists()
    }
}
