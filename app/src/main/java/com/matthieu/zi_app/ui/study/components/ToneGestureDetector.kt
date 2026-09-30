package com.matthieu.zi_app.ui.study.components

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.abs

/**
 * Detects directional tone swipes on a flashcard:
 * - Swipe Right -> Tone 1 (Flat ¯)
 * - Swipe Up-Right / Up -> Tone 2 (Rising ˊ)
 * - Swipe Down-then-Up / Left -> Tone 3 (Dip ˇ)
 * - Swipe Down-Right / Down -> Tone 4 (Falling ˋ)
 * - Quick Tap -> Tone 5 (Neutral ·)
 */
fun Modifier.detectToneGestures(
    enabled: Boolean = true,
    onToneDetected: (Int) -> Unit
): Modifier {
    if (!enabled) return this

    return this
        .pointerInput(Unit) {
            detectTapGestures(
                onTap = {
                    onToneDetected(5) // Neutral tone on tap
                }
            )
        }
        .pointerInput(Unit) {
            var totalDrag = Offset.Zero
            detectDragGestures(
                onDragStart = { totalDrag = Offset.Zero },
                onDrag = { _, dragAmount -> totalDrag += dragAmount },
                onDragEnd = {
                    val dx = totalDrag.x
                    val dy = totalDrag.y
                    val absDx = abs(dx)
                    val absDy = abs(dy)

                    if (absDx < 40f && absDy < 40f) {
                        return@detectDragGestures
                    }

                    val tone = when {
                        // Horizontal Right -> Tone 1 (Flat)
                        dx > 60f && absDy < 50f -> 1
                        // Up-Right or Up -> Tone 2 (Rising)
                        dy < -50f -> 2
                        // Down-Right or Down -> Tone 4 (Falling)
                        dy > 50f -> 4
                        // Left / Up-Left -> Tone 3 (Dip)
                        dx < -50f -> 3
                        else -> 1
                    }
                    onToneDetected(tone)
                }
            )
        }
}
