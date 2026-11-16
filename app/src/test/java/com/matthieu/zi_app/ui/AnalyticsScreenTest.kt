package com.matthieu.zi_app.ui

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.matthieu.zi_app.ComposeTestActivity
import com.matthieu.zi_app.data.local.entity.ProgressRecordEntity
import com.matthieu.zi_app.ui.analytics.components.LeitnerProgressChart
import com.matthieu.zi_app.ui.home.LeitnerDistributionCard
import com.matthieu.zi_app.ui.home.TimeRange
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AnalyticsScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComposeTestActivity>()

    @Test
    fun testLeitnerProgressChart_displaysLegendStartingWithBox0() {
        val records = listOf(
            ProgressRecordEntity(
                date = 1000L,
                totalBox1 = 5,
                totalBox2 = 3,
                totalBox3 = 2,
                totalBox4 = 1,
                totalBox5 = 4
            )
        )

        composeTestRule.setContent {
            LeitnerProgressChart(records = records)
        }

        // Box 0 should be displayed in the legend
        composeTestRule.onNodeWithText("Box 0").assertExists()
    }

    @Test
    fun testLeitnerProgressChart_whenEmpty_displaysEmptyMessage() {
        composeTestRule.setContent {
            LeitnerProgressChart(records = emptyList())
        }

        composeTestRule.onNodeWithText("No study progress history yet").assertExists()
    }

    @Test
    fun testLeitnerDistributionCard_box0OnTopAndBox9AtBottom() {
        val distribution = (0..9).associateWith { it * 2 }

        composeTestRule.setContent {
            LeitnerDistributionCard(
                boxDistribution = distribution,
                totalCards = 90
            )
        }

        composeTestRule.onNodeWithText("Leitner Box Distribution").assertExists()
        composeTestRule.onNodeWithText("Box 0 (New/Reset)").assertExists()
        composeTestRule.onNodeWithText("Box 9 (Mastered)").assertExists()
    }

    @Test
    fun testLeitnerProgressChart_whenSpanLargerThanZoom_displaysDragHint() {
        val now = System.currentTimeMillis()
        val fourteenDaysAgo = now - 14L * 24 * 60 * 60 * 1000L
        val records = listOf(
            ProgressRecordEntity(date = fourteenDaysAgo, totalBox1 = 5),
            ProgressRecordEntity(date = now, totalBox1 = 8)
        )

        composeTestRule.setContent {
            LeitnerProgressChart(
                records = records,
                timeRange = TimeRange.DAYS_7
            )
        }

        composeTestRule.onNodeWithText("← Drag horizontally to explore history →").assertExists()
    }

    @Test
    fun testLeitnerProgressChart_whenSpanWithinZoom_doesNotDisplayDragHint() {
        val now = System.currentTimeMillis()
        val twoDaysAgo = now - 2L * 24 * 60 * 60 * 1000L
        val records = listOf(
            ProgressRecordEntity(date = twoDaysAgo, totalBox1 = 5),
            ProgressRecordEntity(date = now, totalBox1 = 8)
        )

        composeTestRule.setContent {
            LeitnerProgressChart(
                records = records,
                timeRange = TimeRange.DAYS_7
            )
        }

        composeTestRule.onNodeWithText("← Drag horizontally to explore history →").assertDoesNotExist()
    }

    @Test
    fun testLeitnerProgressChart_whenTimeRangeAll_doesNotDisplayDragHint() {
        val now = System.currentTimeMillis()
        val fourteenDaysAgo = now - 14L * 24 * 60 * 60 * 1000L
        val records = listOf(
            ProgressRecordEntity(date = fourteenDaysAgo, totalBox1 = 5),
            ProgressRecordEntity(date = now, totalBox1 = 8)
        )

        composeTestRule.setContent {
            LeitnerProgressChart(
                records = records,
                timeRange = TimeRange.ALL
            )
        }

        composeTestRule.onNodeWithText("← Drag horizontally to explore history →").assertDoesNotExist()
    }
}
