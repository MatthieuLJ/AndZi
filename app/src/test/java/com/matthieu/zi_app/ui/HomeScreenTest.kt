package com.matthieu.zi_app.ui

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.matthieu.zi_app.ComposeTestActivity
import com.matthieu.zi_app.ui.home.DailyReviewCard
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HomeScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComposeTestActivity>()

    @Test
    fun testDailyReviewCardWhenNoCardsDue_practiceButtonIsDisabled() {
        composeTestRule.setContent {
            DailyReviewCard(
                dueCount = 0,
                onStartReview = {}
            )
        }

        // Verify "0" and "Cards Due" are displayed
        composeTestRule.onNodeWithText("0").assertExists()
        composeTestRule.onNodeWithText("Cards Due").assertExists()

        // Verify "Practice All Cards" button is displayed and disabled (grayed out)
        composeTestRule.onNodeWithText("Practice All Cards")
            .assertExists()
            .assertIsNotEnabled()
    }

    @Test
    fun testDailyReviewCardWhenOneCardDue_startReviewButtonIsEnabled() {
        composeTestRule.setContent {
            DailyReviewCard(
                dueCount = 1,
                onStartReview = {}
            )
        }

        // Verify singular "Card Due"
        composeTestRule.onNodeWithText("1").assertExists()
        composeTestRule.onNodeWithText("Card Due").assertExists()

        composeTestRule.onNodeWithText("Start Review Session (1)")
            .assertExists()
            .assertIsEnabled()
    }

    @Test
    fun testDailyReviewCardWhenMultipleCardsDue_startReviewButtonIsEnabled() {
        composeTestRule.setContent {
            DailyReviewCard(
                dueCount = 5,
                onStartReview = {}
            )
        }

        // Verify plural "Cards Due" and button label with count
        composeTestRule.onNodeWithText("5").assertExists()
        composeTestRule.onNodeWithText("Cards Due").assertExists()

        composeTestRule.onNodeWithText("Start Review Session (5)")
            .assertExists()
            .assertIsEnabled()
    }
}
