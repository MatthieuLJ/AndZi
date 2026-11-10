package com.matthieu.zi_app.ui

import androidx.compose.ui.test.junit4.createAndroidComposeRule
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

        // STREAK_7 is unlocked -> Should be visible
        composeTestRule.onNodeWithText("7-Day Streak").assertExists()
        composeTestRule.onNodeWithText("Unlocked").assertExists()

        // STREAK_31 is next upcoming -> Should be visible with progress
        composeTestRule.onNodeWithText("31-Day Streak").assertExists()
        composeTestRule.onNodeWithText("12 / 31").assertExists()

        // STREAK_365 and STREAK_730 are distant locked badges -> Should NOT exist (rows removed)
        composeTestRule.onNodeWithText("1-Year Streak").assertDoesNotExist()
        composeTestRule.onNodeWithText("2-Year Streak").assertDoesNotExist()
    }
}
