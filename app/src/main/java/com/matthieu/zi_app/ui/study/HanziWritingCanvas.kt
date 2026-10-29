package com.matthieu.zi_app.ui.study

import android.graphics.Matrix
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.PathParser
import com.matthieu.zi_app.domain.writing.HanziCharacter
import com.matthieu.zi_app.domain.writing.HanziPoint
import com.matthieu.zi_app.domain.writing.HanziStroke
import com.matthieu.zi_app.domain.writing.WritingScaffoldingLevel
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Interactive Compose Rice Grid (米字格) Canvas for writing Chinese characters.
 * Supports real-time stroke drawing, vector rendering, and progressive scaffolding.
 */
@Composable
fun HanziWritingCanvas(
    character: HanziCharacter,
    completedStrokes: List<HanziStroke>,
    currentStrokeIndex: Int,
    scaffoldingLevel: WritingScaffoldingLevel,
    isAnswerRevealed: Boolean,
    errorShakeTrigger: Int,
    strokeColor: Color = MaterialTheme.colorScheme.primary,
    onStrokeDrawn: (List<HanziPoint>) -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 280.dp
) {
    val currentPoints = remember { mutableStateListOf<Offset>() }
    val shakeOffset = remember { Animatable(0f) }
    val pulseAnim = remember { Animatable(1f) }

    // Pulsating indicator for guided start points
    LaunchedEffect(currentStrokeIndex, scaffoldingLevel) {
        pulseAnim.snapTo(1f)
        pulseAnim.animateTo(
            targetValue = 1.4f,
            animationSpec = infiniteRepeatable(
                animation = tween(600, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            )
        )
    }

    // Shake animation on incorrect stroke
    LaunchedEffect(errorShakeTrigger) {
        if (errorShakeTrigger > 0) {
            shakeOffset.snapTo(0f)
            shakeOffset.animateTo(
                targetValue = 12f,
                animationSpec = tween(50)
            )
            shakeOffset.animateTo(
                targetValue = -12f,
                animationSpec = tween(50)
            )
            shakeOffset.animateTo(
                targetValue = 0f,
                animationSpec = tween(50)
            )
        }
    }

    val outlineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
    val activeStrokeColor = if (isAnswerRevealed) MaterialTheme.colorScheme.primary else strokeColor
    val guideColor = MaterialTheme.colorScheme.tertiary

    Box(
        modifier = modifier
            .size(size)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(currentStrokeIndex, isAnswerRevealed) {
                    if (isAnswerRevealed) return@pointerInput
                    detectDragGestures(
                        onDragStart = { offset ->
                            currentPoints.clear()
                            currentPoints.add(offset)
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            currentPoints.add(change.position)
                        },
                        onDragEnd = {
                            if (currentPoints.size >= 2) {
                                val minDim = minOf(this.size.width, this.size.height).toFloat()
                                val scale = minDim / 1024f
                                val offX = (this.size.width - 1024f * scale) / 2f
                                val offY = (this.size.height - 1024f * scale) / 2f

                                val hanziPoints = currentPoints.map { pt ->
                                    val hx = (pt.x - offX) / scale
                                    val hy = 900f - ((pt.y - offY) / scale)
                                    HanziPoint(hx, hy)
                                }
                                onStrokeDrawn(hanziPoints)
                            }
                            currentPoints.clear()
                        },
                        onDragCancel = {
                            currentPoints.clear()
                        }
                    )
                }
        ) {
            val canvasWidth = this.size.width
            val canvasHeight = this.size.height
            val minDim = this.size.minDimension
            val scale = minDim / 1024f
            val offX = (canvasWidth - 1024f * scale) / 2f + shakeOffset.value
            val offY = (canvasHeight - 1024f * scale) / 2f

            fun toCanvasOffset(p: HanziPoint): Offset {
                return Offset(
                    x = offX + p.x * scale,
                    y = offY + (900f - p.y) * scale
                )
            }

            // 1. Draw Traditional Rice Grid (米字格)
            val strokeDashed = Stroke(
                width = 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
            )
            val strokeSolid = Stroke(width = 2.dp.toPx())

            // Outer bounding border
            drawRect(
                color = gridColor,
                topLeft = Offset(offX, offY),
                size = androidx.compose.ui.geometry.Size(1024f * scale, 1024f * scale),
                style = strokeSolid
            )
            // Horizontal and vertical dividing lines
            val centerX = offX + (512f * scale)
            val centerY = offY + (512f * scale)
            drawLine(gridColor, Offset(offX, centerY), Offset(offX + 1024f * scale, centerY), strokeWidth = 1.5.dp.toPx())
            drawLine(gridColor, Offset(centerX, offY), Offset(centerX, offY + 1024f * scale), strokeWidth = 1.5.dp.toPx())

            // Diagonals (dashed)
            drawLine(gridColor, Offset(offX, offY), Offset(offX + 1024f * scale, offY + 1024f * scale), pathEffect = strokeDashed.pathEffect, strokeWidth = strokeDashed.width)
            drawLine(gridColor, Offset(offX, offY + 1024f * scale), Offset(offX + 1024f * scale, offY), pathEffect = strokeDashed.pathEffect, strokeWidth = strokeDashed.width)

            val matrix = Matrix()
            matrix.setScale(scale, -scale)
            matrix.postTranslate(offX, offY + 900f * scale)

            // 2. Render Full Silhouette Outline when Level 1 or Revealed
            if (scaffoldingLevel == WritingScaffoldingLevel.GUIDED_FULL || isAnswerRevealed) {
                for (stroke in character.strokes) {
                    try {
                        val androidPath = PathParser.createPathFromPathData(stroke.svgPath)
                        androidPath.transform(matrix)
                        drawPath(
                            path = androidPath.asComposePath(),
                            color = outlineColor
                        )
                    } catch (_: Exception) { }
                }
            }

            // 3. Render Completed Strokes
            for (stroke in completedStrokes) {
                try {
                    val androidPath = PathParser.createPathFromPathData(stroke.svgPath)
                    androidPath.transform(matrix)
                    drawPath(
                        path = androidPath.asComposePath(),
                        color = activeStrokeColor
                    )
                } catch (_: Exception) { }
            }

            // 4. Render Scaffolding Guidance (Start Dot & Direction Arrow)
            if (!isAnswerRevealed && currentStrokeIndex in character.strokes.indices) {
                val currentStroke = character.strokes[currentStrokeIndex]
                val startPoint = currentStroke.getStartingPoint()
                val startOffset = toCanvasOffset(startPoint)

                val showStartDot = when (scaffoldingLevel) {
                    WritingScaffoldingLevel.GUIDED_FULL,
                    WritingScaffoldingLevel.GUIDED_DIRECTION -> true
                    WritingScaffoldingLevel.GUIDED_FIRST_STROKE -> currentStrokeIndex == 0
                    WritingScaffoldingLevel.TEST -> false
                }

                if (showStartDot) {
                    // Pulsating circle at start position
                    drawCircle(
                        color = guideColor.copy(alpha = 0.35f),
                        radius = 16.dp.toPx() * pulseAnim.value,
                        center = startOffset
                    )
                    drawCircle(
                        color = guideColor,
                        radius = 8.dp.toPx(),
                        center = startOffset
                    )

                    // Direction arrow along initial trajectory (Levels 1 & 2)
                    if (scaffoldingLevel in listOf(WritingScaffoldingLevel.GUIDED_FULL, WritingScaffoldingLevel.GUIDED_DIRECTION)) {
                        if (currentStroke.points.size >= 2) {
                            val secondPoint = currentStroke.points[minOf(1, currentStroke.points.size - 1)]
                            val secondOffset = toCanvasOffset(secondPoint)
                            val angle = atan2(secondOffset.y - startOffset.y, secondOffset.x - startOffset.x)

                            val arrowLength = 26.dp.toPx()
                            val arrowTip = Offset(
                                x = startOffset.x + (arrowLength * cos(angle)).toFloat(),
                                y = startOffset.y + (arrowLength * sin(angle)).toFloat()
                            )

                            drawLine(
                                color = guideColor,
                                start = startOffset,
                                end = arrowTip,
                                strokeWidth = 3.dp.toPx(),
                                cap = StrokeCap.Round
                            )

                            // Arrowhead
                            val headAngle1 = angle + (5.0 * PI / 6.0).toFloat()
                            val headAngle2 = angle - (5.0 * PI / 6.0).toFloat()
                            val headLen = 10.dp.toPx()
                            drawLine(
                                color = guideColor,
                                start = arrowTip,
                                end = Offset(arrowTip.x + headLen * cos(headAngle1), arrowTip.y + headLen * sin(headAngle1)),
                                strokeWidth = 3.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                            drawLine(
                                color = guideColor,
                                start = arrowTip,
                                end = Offset(arrowTip.x + headLen * cos(headAngle2), arrowTip.y + headLen * sin(headAngle2)),
                                strokeWidth = 3.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                        }
                    }
                }
            }

            // 5. Render Active User Drag Polyline
            if (currentPoints.size >= 2) {
                val userPath = Path()
                userPath.moveTo(currentPoints.first().x, currentPoints.first().y)
                for (i in 1 until currentPoints.size) {
                    userPath.lineTo(currentPoints[i].x, currentPoints[i].y)
                }
                drawPath(
                    path = userPath,
                    color = activeStrokeColor,
                    style = Stroke(
                        width = 8.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }
        }
    }
}
