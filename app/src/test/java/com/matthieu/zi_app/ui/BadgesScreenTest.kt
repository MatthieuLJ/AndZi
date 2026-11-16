package com.matthieu.zi_app.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import com.matthieu.zi_app.ComposeTestActivity
import com.matthieu.zi_app.domain.badges.BadgeCategory
import com.matthieu.zi_app.domain.badges.BadgeId
import com.matthieu.zi_app.domain.badges.BadgeItem
import com.matthieu.zi_app.domain.badges.BadgesSummary
import com.matthieu.zi_app.ui.badges.BadgesScreenContent
import com.matthieu.zi_app.ui.badges.BadgesUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BadgesScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComposeTestActivity>()

    @Test
    fun testOnlyUnlockedAndNextUpcomingBadgesAreDisplayed() {
        // Daily streak category has STREAK_7, STREAK_31, STREAK_365, STREAK_730
        // Simulate: STREAK_7 unlocked, STREAK_31 locked (next upcoming), STREAK_365 and STREAK_730 locked (distant)
        val badges = listOf(
            BadgeItem(
                badge = BadgeId.STREAK_7,
                isUnlocked = true,
                currentProgress = 7,
                target = 7
            ),
            BadgeItem(
                badge = BadgeId.STREAK_31,
                isUnlocked = false,
                currentProgress = 12,
                target = 31
            ),
            BadgeItem(
                badge = BadgeId.STREAK_365,
                isUnlocked = false,
                currentProgress = 12,
                target = 365
            ),
            BadgeItem(
                badge = BadgeId.STREAK_730,
                isUnlocked = false,
                currentProgress = 12,
                target = 730
            )
        )

        val summary = BadgesSummary(
            currentDailyStreak = 12,
            longestDailyStreak = 12,
            currentCorrectStreak = 5,
            longestCorrectStreak = 5,
            testsToday = 10,
            mostTestsInADay = 20,
            badges = badges
        )

        composeTestRule.setContent {
            BadgesScreenContent(
                uiState = BadgesUiState(
                    isLoading = false,
                    summary = summary
                ),
                onNavigateBack = {}
            )
        }

        // STREAK_7 is unlocked -> Should be visible (both in showcase and category list)
        composeTestRule.onAllNodesWithText("7-Day Streak").onFirst().assertExists()

        // "Unlocked" pill text under badges should NOT exist
        composeTestRule.onNodeWithText("Unlocked").assertDoesNotExist()

        // Cheesy personality names should NOT exist
        composeTestRule.onNodeWithText("The Eternal Flame").assertDoesNotExist()
        composeTestRule.onNodeWithText("The Jade Archer").assertDoesNotExist()
        composeTestRule.onNodeWithText("The Iron Dynamo").assertDoesNotExist()
        composeTestRule.onNodeWithText("The Imperial Scholar").assertDoesNotExist()

        // Ratio like "1 / 4 Unlocked" should NOT exist in showcase
        composeTestRule.onNodeWithText("1 / 4 Unlocked").assertDoesNotExist()

        // STREAK_31 is next upcoming -> Should be visible with progress
        composeTestRule.onNodeWithText("31-Day Streak").assertExists()
        composeTestRule.onNodeWithText("12 / 31").assertExists()

        // STREAK_365 and STREAK_730 are distant locked badges -> Should NOT exist (rows removed)
        composeTestRule.onNodeWithText("1-Year Streak").assertDoesNotExist()
        composeTestRule.onNodeWithText("2-Year Streak").assertDoesNotExist()
    }

    @Test
    fun testTopShowcaseShowsLatestBadgePerCategoryUpTo4() {
        val badges = listOf(
            // DAILY_STREAK: 7 and 31 unlocked -> latest is 31
            BadgeItem(badge = BadgeId.STREAK_7, isUnlocked = true, currentProgress = 31, target = 7),
            BadgeItem(badge = BadgeId.STREAK_31, isUnlocked = true, currentProgress = 31, target = 31),
            BadgeItem(badge = BadgeId.STREAK_365, isUnlocked = false, currentProgress = 31, target = 365),

            // CORRECT_STREAK: 10 and 20 unlocked -> latest is 20
            BadgeItem(badge = BadgeId.CORRECT_10, isUnlocked = true, currentProgress = 20, target = 10),
            BadgeItem(badge = BadgeId.CORRECT_20, isUnlocked = true, currentProgress = 20, target = 20),
            BadgeItem(badge = BadgeId.CORRECT_50, isUnlocked = false, currentProgress = 20, target = 50),

            // DAILY_TESTS: 50 unlocked -> latest is 50
            BadgeItem(badge = BadgeId.DAILY_TESTS_50, isUnlocked = true, currentProgress = 50, target = 50),
            BadgeItem(badge = BadgeId.DAILY_TESTS_100, isUnlocked = false, currentProgress = 50, target = 100),

            // HSK_LEVEL: HSK 1 and HSK 2 unlocked -> latest is HSK 2
            BadgeItem(badge = BadgeId.HSK_1, isUnlocked = true, currentProgress = 150, target = 150),
            BadgeItem(badge = BadgeId.HSK_2, isUnlocked = true, currentProgress = 150, target = 150),
            BadgeItem(badge = BadgeId.HSK_3, isUnlocked = false, currentProgress = 0, target = 300)
        )

        val summary = BadgesSummary(
            currentDailyStreak = 31,
            longestDailyStreak = 31,
            currentCorrectStreak = 20,
            longestCorrectStreak = 20,
            testsToday = 50,
            mostTestsInADay = 50,
            badges = badges
        )

        composeTestRule.setContent {
            BadgesScreenContent(
                uiState = BadgesUiState(
                    isLoading = false,
                    summary = summary
                ),
                onNavigateBack = {}
            )
        }

        // Top showcase should show latest badge of each category (up to 4 in 2x2 grid):
        // STREAK_31 (Daily Streak)
        composeTestRule.onAllNodesWithText("31-Day Streak").onFirst().assertExists()
        // CORRECT_20 (Correct Answers)
        composeTestRule.onAllNodesWithText("20 In A Row").onFirst().assertExists()
        // DAILY_TESTS_50 (Daily Volume)
        composeTestRule.onAllNodesWithText("50 In One Day").onFirst().assertExists()
        // HSK_2 (HSK Mastery)
        composeTestRule.onAllNodesWithText("HSK 2 Mastered").onFirst().assertExists()

        // No ratio like "4 / 11 Unlocked" in showcase header
        composeTestRule.onNodeWithText("4 / 11 Unlocked").assertDoesNotExist()
        // No "Unlocked" text under trophies
        composeTestRule.onNodeWithText("Unlocked").assertDoesNotExist()
        // No cheese names
        composeTestRule.onNodeWithText("The Eternal Flame").assertDoesNotExist()
        composeTestRule.onNodeWithText("The Jade Archer").assertDoesNotExist()
        composeTestRule.onNodeWithText("The Iron Dynamo").assertDoesNotExist()
        composeTestRule.onNodeWithText("The Imperial Scholar").assertDoesNotExist()
    }
}
