package com.matthieu.zi_app.ui.navigation

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.matthieu.zi_app.ComposeTestActivity
import com.matthieu.zi_app.ui.theme.AndZiTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MainAppScaffoldTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComposeTestActivity>()

    @Test
    fun testMainAppScaffold_startTestFalse_showsHomeAndBottomNav() {
        composeTestRule.setContent {
            AndZiTheme {
                MainAppScaffold(
                    startTest = false
                )
            }
        }

        // Top bar title on Home screen
        composeTestRule.onNodeWithText("Daily Practice & SRS Review").assertExists()

        // Bottom navigation items are visible
        composeTestRule.onNodeWithText("Study").assertExists()
        composeTestRule.onNodeWithText("Dictionary").assertExists()
        composeTestRule.onNodeWithText("Analytics").assertExists()
    }

    @Test
    fun testMainAppScaffold_startTestTrue_opensStudyAndClosingReturnsHomeWithBottomNav() {
        var startTest = true
        composeTestRule.setContent {
            AndZiTheme {
                MainAppScaffold(
                    startTest = startTest,
                    onTestStarted = { startTest = false }
                )
            }
        }
        composeTestRule.waitForIdle()

        // The close practice button should exist in the TopAppBar of StudyScreen
        composeTestRule.onNodeWithContentDescription("Close Practice")
            .assertExists()
            .assertHasClickAction()
            .performClick()
        composeTestRule.waitForIdle()

        // After closing practice, we should be on HomeScreen with the bottom nav visible
        composeTestRule.onNodeWithText("Daily Practice & SRS Review").assertExists()
        composeTestRule.onNodeWithText("Study").assertExists()
        composeTestRule.onNodeWithText("Dictionary").assertExists()
        composeTestRule.onNodeWithText("Analytics").assertExists()

        // Tapping another bottom nav item (e.g. Dictionary) works
        composeTestRule.onNodeWithText("Dictionary").performClick()
        composeTestRule.waitForIdle()

        // Tapping Analytics works
        composeTestRule.onNodeWithText("Analytics").performClick()
        composeTestRule.waitForIdle()
    }
}
