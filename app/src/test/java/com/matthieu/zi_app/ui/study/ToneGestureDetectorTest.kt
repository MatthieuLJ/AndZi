package com.matthieu.zi_app.ui.study

import androidx.compose.ui.geometry.Offset
import com.matthieu.zi_app.ui.study.components.classifyToneGesture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ToneGestureDetectorTest {

    @Test
    fun testTone3_vShapeGesture_downAndUp() {
        // User starts at (100, 100), goes down to (150, 180), then goes up to (200, 90)
        val vShapePoints = listOf(
            Offset(100f, 100f),
            Offset(125f, 140f),
            Offset(150f, 180f),
            Offset(175f, 135f),
            Offset(200f, 90f)
        )
        val tone = classifyToneGesture(vShapePoints)
        assertEquals(3, tone)
    }

    @Test
    fun testTone3_shallowVShape_downAndUp() {
        // User draws a shallower V shape: (50, 50) -> (70, 85) -> (90, 55)
        val vShapePoints = listOf(
            Offset(50f, 50f),
            Offset(70f, 85f),
            Offset(90f, 55f)
        )
        val tone = classifyToneGesture(vShapePoints)
        assertEquals(3, tone)
    }

    @Test
    fun testTone3_swipeLeft() {
        // Alternative swipe left for tone 3: (200, 100) -> (100, 100)
        val swipeLeftPoints = listOf(
            Offset(200f, 100f),
            Offset(150f, 100f),
            Offset(100f, 100f)
        )
        val tone = classifyToneGesture(swipeLeftPoints)
        assertEquals(3, tone)
    }

    @Test
    fun testTone1_horizontalRight() {
        // Flat horizontal swipe: (50, 100) -> (200, 105)
        val flatPoints = listOf(
            Offset(50f, 100f),
            Offset(100f, 102f),
            Offset(150f, 103f),
            Offset(200f, 105f)
        )
        val tone = classifyToneGesture(flatPoints)
        assertEquals(1, tone)
    }

    @Test
    fun testTone2_risingSlash() {
        // Rising slash from bottom-left to top-right: (50, 200) -> (150, 80)
        val risingPoints = listOf(
            Offset(50f, 200f),
            Offset(100f, 140f),
            Offset(150f, 80f)
        )
        val tone = classifyToneGesture(risingPoints)
        assertEquals(2, tone)
    }

    @Test
    fun testTone4_fallingSlash() {
        // Falling slash from top-left to bottom-right: (50, 80) -> (150, 200)
        val fallingPoints = listOf(
            Offset(50f, 80f),
            Offset(100f, 140f),
            Offset(150f, 200f)
        )
        val tone = classifyToneGesture(fallingPoints)
        assertEquals(4, tone)
    }

    @Test
    fun testTooShortGesture_returnsNull() {
        // Tiny accidental movement: (100, 100) -> (105, 105)
        val tinyPoints = listOf(
            Offset(100f, 100f),
            Offset(105f, 105f)
        )
        val tone = classifyToneGesture(tinyPoints)
        assertNull(tone)
    }
}
