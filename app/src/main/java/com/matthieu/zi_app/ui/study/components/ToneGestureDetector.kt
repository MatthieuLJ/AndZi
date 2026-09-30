package com.matthieu.zi_app.ui.study.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.atan2

/**
 * Detects directional tone strokes and gestures on a flashcard:
 * - Horizontal Right -> Tone 1 (Flat ¯)
 * - Up-Right / Upward -> Tone 2 (Rising ˊ)
 * - V-shape (Down then Up) / Leftward -> Tone 3 (Dip ˇ)
 * - Down-Right / Downward -> Tone 4 (Falling ˋ)
 * - Quick Tap -> Tone 5 (Neutral ·)
 */
fun Modifier.detectToneGestures(
    enabled: Boolean = true,
    onToneDetected: (Int) -> Unit
): Modifier {
    if (!enabled) return this

    return this.pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val points = mutableListOf<Offset>()
            points.add(down.position)

            var isDrag = false
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break

                if (change.pressed) {
                    points.add(change.position)
                    val dist = (change.position - down.position).getDistance()
                    if (dist > 15f) {
                        isDrag = true
                    }
                    change.consume()
                } else {
                    change.consume()
                    break
                }
            }

            if (!isDrag && points.size <= 3) {
                // Quick Tap -> Neutral Tone
                onToneDetected(5)
            } else if (points.size >= 2) {
                val tone = classifyToneGesture(points)
                if (tone != null) {
                    onToneDetected(tone)
                }
            }
        }
    }
}

/**
 * Classifies a sequence of touch coordinates into a Mandarin tone (1, 2, 3, 4, 5).
 */
fun classifyToneGesture(points: List<Offset>): Int? {
    if (points.size < 2) return null

    val start = points.first()
    val end = points.last()

    // 1. Calculate total stroke path length
    var pathLength = 0f
    for (i in 1 until points.size) {
        pathLength += (points[i] - points[i - 1]).getDistance()
    }
    if (pathLength < 25f) return null

    // 2. Check for Third Tone (V-shape / Dip ˇ)
    // Find highest Y (trough / bottom point on screen)
    var maxIdx = 0
    var maxY = points[0].y
    for (i in 1 until points.size) {
        if (points[i].y > maxY) {
            maxY = points[i].y
            maxIdx = i
        }
    }

    // Measure the downward dip before trough and upward rise after trough
    val minYBefore = points.subList(0, maxIdx + 1).minOf { it.y }
    val minYAfter = points.subList(maxIdx, points.size).minOf { it.y }
    val downwardDrop = maxY - minYBefore
    val upwardRise = maxY - minYAfter

    // If there is a downward drop followed by an upward rise, it is a 3rd tone (V-shape dip)
    if (downwardDrop >= 18f && upwardRise >= 18f && maxIdx > 0 && maxIdx < points.size - 1) {
        return 3
    }

    // 3. Directional vectors for straight swipes (Tone 1, 2, 4, or swipe-left 3)
    val dx = end.x - start.x
    val dy = end.y - start.y
    val angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()

    return when {
        // Horizontal Right (Flat ¯) -> Tone 1
        angle in -25f..25f && dx > 20f -> 1
        // Rising (Up-Right or Up ˊ) -> Tone 2
        angle in -120f..-25f -> 2
        // Falling (Down-Right or Down ˋ) -> Tone 4
        angle in 25f..120f -> 4
        // Leftward Swipe (←) -> Tone 3 (Alternative gesture for dip)
        angle > 135f || angle < -135f -> 3
        else -> 1
    }
}
