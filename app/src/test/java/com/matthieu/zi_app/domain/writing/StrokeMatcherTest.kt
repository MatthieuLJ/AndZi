package com.matthieu.zi_app.domain.writing

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StrokeMatcherTest {

    @Test
    fun testHorizontalStrokeMatch() {
        val stroke = HanziStroke(
            strokeNum = 0,
            svgPath = "M 250 500 L 750 500",
            points = listOf(
                HanziPoint(250f, 500f),
                HanziPoint(500f, 500f),
                HanziPoint(750f, 500f)
            )
        )
        val character = HanziCharacter(
            hanzi = "一",
            strokes = listOf(stroke),
            strokeCount = 1
        )

        // User draws forward horizontally
        val userPoints = listOf(
            HanziPoint(260f, 505f),
            HanziPoint(490f, 498f),
            HanziPoint(740f, 502f)
        )
        val result = StrokeMatcher.strokeMatches(userPoints, character, 0)
        assertTrue("Forward horizontal stroke should match", result.isMatch)
        assertFalse("Should not be backwards", result.isStrokeBackwards)

        // User draws backwards (right to left)
        val backwardsPoints = listOf(
            HanziPoint(740f, 502f),
            HanziPoint(490f, 498f),
            HanziPoint(260f, 505f)
        )
        val backwardsResult = StrokeMatcher.strokeMatches(backwardsPoints, character, 0)
        assertFalse("Backwards stroke should not match as valid", backwardsResult.isMatch)
        assertTrue("Backwards stroke should be detected as backwards", backwardsResult.isStrokeBackwards)

        // User draws vertical stroke
        val verticalPoints = listOf(
            HanziPoint(500f, 750f),
            HanziPoint(500f, 500f),
            HanziPoint(500f, 250f)
        )
        val verticalResult = StrokeMatcher.strokeMatches(verticalPoints, character, 0)
        assertFalse("Perpendicular vertical stroke should not match horizontal stroke", verticalResult.isMatch)
    }

    @Test
    fun testMultiStrokeOrderDetection() {
        // Character with 2 strokes: 十 (horizontal stroke 0, vertical stroke 1)
        val stroke0 = HanziStroke(
            strokeNum = 0,
            svgPath = "M 200 500 L 800 500",
            points = listOf(HanziPoint(200f, 500f), HanziPoint(800f, 500f))
        )
        val stroke1 = HanziStroke(
            strokeNum = 1,
            svgPath = "M 500 800 L 500 200",
            points = listOf(HanziPoint(500f, 800f), HanziPoint(500f, 200f))
        )
        val character = HanziCharacter(
            hanzi = "十",
            strokes = listOf(stroke0, stroke1),
            strokeCount = 2
        )

        // If user draws the vertical stroke when stroke 0 is expected
        val userVertical = listOf(HanziPoint(505f, 795f), HanziPoint(495f, 205f))
        val result = StrokeMatcher.strokeMatches(userVertical, character, 0)
        assertFalse("Drawing stroke 1 out of order should not pass as stroke 0", result.isMatch)
    }
}

