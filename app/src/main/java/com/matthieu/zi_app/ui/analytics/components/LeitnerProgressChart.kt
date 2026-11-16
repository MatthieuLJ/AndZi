package com.matthieu.zi_app.ui.analytics.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.matthieu.zi_app.data.local.entity.ProgressRecordEntity
import com.matthieu.zi_app.ui.home.TimeRange
import com.matthieu.zi_app.ui.theme.LeitnerBoxColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

private val boxColors = LeitnerBoxColors

@Composable
fun LeitnerProgressChart(
    records: List<ProgressRecordEntity>,
    modifier: Modifier = Modifier,
    timeRange: TimeRange = TimeRange.ALL
) {
    val textMeasurer = rememberTextMeasurer()
    val animatedProgress = remember { Animatable(0f) }

    LaunchedEffect(records) {
        animatedProgress.snapTo(0f)
        animatedProgress.animateTo(1f, animationSpec = tween(durationMillis = 650))
    }

    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)

    Column(modifier = modifier) {
        if (records.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No study progress history yet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = onSurfaceVariant
                )
            }
        } else {
            val sortedRecords = remember(records) { records.sortedBy { it.date } }
            val minDate = sortedRecords.first().date
            val maxRecordedDate = sortedRecords.last().date
            val now = System.currentTimeMillis()
            val maxDate = max(now, maxRecordedDate)
            val totalSpan = max(maxDate - minDate, 1L)

            val windowDuration = if (timeRange == TimeRange.ALL) {
                totalSpan
            } else {
                timeRange.durationMillis
            }

            val canDrag = timeRange != TimeRange.ALL && totalSpan > windowDuration && windowDuration > 0L

            var scrollOffset by remember { mutableFloatStateOf(0f) }
            LaunchedEffect(timeRange, records) {
                scrollOffset = 0f
            }

            // Extend effective records up to maxDate if latest record was before maxDate
            val effectiveRecords = remember(sortedRecords, maxDate) {
                if (sortedRecords.isEmpty()) emptyList()
                else if (sortedRecords.last().date < maxDate) {
                    sortedRecords + sortedRecords.last().copy(date = maxDate)
                } else {
                    sortedRecords
                }
            }

            // Determine maximum relevant box currently present in records (e.g. up to Box 6)
            val maxRelevantBox = (0..9).reversed().firstOrNull { box ->
                records.any { it.getBoxCount(box) > 0 }
            } ?: 0
            val displayMaxBox = max(maxRelevantBox, 1)

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(210.dp)
                    .padding(top = 8.dp, bottom = 4.dp, start = 8.dp, end = 8.dp)
                    .then(
                        if (canDrag) {
                            Modifier.pointerInput(canDrag, totalSpan, windowDuration) {
                                val leftPaddingPx = 36.dp.toPx()
                                val chartWidthPx = size.width - leftPaddingPx
                                if (chartWidthPx <= 0) return@pointerInput
                                val totalWidth = chartWidthPx * (totalSpan.toFloat() / windowDuration.toFloat())
                                val maxScroll = max(totalWidth - chartWidthPx, 0f)

                                detectHorizontalDragGestures { change, dragAmount ->
                                    change.consume()
                                    scrollOffset = (scrollOffset + dragAmount).coerceIn(0f, maxScroll)
                                }
                            }
                        } else Modifier
                    )
            ) {
                val anim = animatedProgress.value
                val width = size.width
                val height = size.height
                val bottomPadding = 24.dp.toPx()
                val leftPadding = 36.dp.toPx()
                val chartWidth = width - leftPadding
                val chartHeight = height - bottomPadding

                if (chartWidth <= 0 || chartHeight <= 0) return@Canvas

                val totalContentWidth = if (canDrag) {
                    chartWidth * (totalSpan.toFloat() / windowDuration.toFloat())
                } else {
                    chartWidth
                }
                val maxScrollPx = if (canDrag) max(totalContentWidth - chartWidth, 0f) else 0f

                fun getX(timestamp: Long): Float {
                    return if (effectiveRecords.size == 1 && !canDrag) {
                        leftPadding + chartWidth / 2f
                    } else {
                        val progress = (timestamp - minDate).toFloat() / totalSpan.toFloat()
                        val xInContent = progress * totalContentWidth
                        leftPadding + xInContent - maxScrollPx + scrollOffset
                    }
                }

                // 1. Compute totals per record using all relevant boxes
                val pointTotals = effectiveRecords.map { rec ->
                    val sum = (0..displayMaxBox).sumOf { rec.getBoxCount(it) }
                    max(sum, 1)
                }
                val maxTotal = max(pointTotals.maxOrNull() ?: 10, 5)

                // 2. Draw Y Axis Gridlines (0, 50%, 100%)
                val steps = 3
                for (i in 0..steps) {
                    val yVal = i * (maxTotal / steps.toFloat())
                    val yPos = chartHeight - (i.toFloat() / steps.toFloat()) * chartHeight

                    drawLine(
                        color = gridColor,
                        start = Offset(leftPadding, yPos),
                        end = Offset(width, yPos),
                        strokeWidth = 1.dp.toPx()
                    )

                    val label = yVal.toInt().toString()
                    val textLayout = textMeasurer.measure(
                        text = label,
                        style = TextStyle(fontSize = 10.sp, color = onSurfaceVariant.copy(alpha = 0.7f))
                    )
                    drawText(
                        textLayoutResult = textLayout,
                        topLeft = Offset(leftPadding - textLayout.size.width - 6.dp.toPx(), yPos - textLayout.size.height / 2f)
                    )
                }

                // 3. Draw X Axis Date Labels
                val dateFormatter = SimpleDateFormat("MMM d", Locale.getDefault())
                if (canDrag) {
                    val tLeft = minDate + (((maxScrollPx - scrollOffset) / totalContentWidth) * totalSpan).toLong()
                    val tRight = minDate + (((chartWidth + maxScrollPx - scrollOffset) / totalContentWidth) * totalSpan).toLong()
                    val tMid = (tLeft + tRight) / 2

                    val dateLeft = dateFormatter.format(Date(tLeft.coerceIn(minDate, maxDate)))
                    val dateMid = dateFormatter.format(Date(tMid.coerceIn(minDate, maxDate)))
                    val dateRight = dateFormatter.format(Date(tRight.coerceIn(minDate, maxDate)))

                    val leftLayout = textMeasurer.measure(
                        text = dateLeft,
                        style = TextStyle(fontSize = 10.sp, color = onSurfaceVariant.copy(alpha = 0.7f))
                    )
                    val rightLayout = textMeasurer.measure(
                        text = dateRight,
                        style = TextStyle(fontSize = 10.sp, color = onSurfaceVariant.copy(alpha = 0.7f))
                    )

                    drawText(
                        textLayoutResult = leftLayout,
                        topLeft = Offset(leftPadding + 2.dp.toPx(), height - leftLayout.size.height)
                    )
                    drawText(
                        textLayoutResult = rightLayout,
                        topLeft = Offset(width - rightLayout.size.width - 2.dp.toPx(), height - rightLayout.size.height)
                    )

                    if (dateLeft != dateRight) {
                        val midLayout = textMeasurer.measure(
                            text = dateMid,
                            style = TextStyle(fontSize = 10.sp, color = onSurfaceVariant.copy(alpha = 0.7f))
                        )
                        val midX = leftPadding + chartWidth / 2f - midLayout.size.width / 2f
                        if (midX > leftPadding + leftLayout.size.width + 8.dp.toPx() &&
                            midX + midLayout.size.width < width - rightLayout.size.width - 8.dp.toPx()
                        ) {
                            drawText(
                                textLayoutResult = midLayout,
                                topLeft = Offset(midX, height - midLayout.size.height)
                            )
                        }
                    }
                } else if (effectiveRecords.isNotEmpty()) {
                    if (effectiveRecords.size == 1) {
                        val dateStr = dateFormatter.format(Date(effectiveRecords[0].date))
                        val textLayout = textMeasurer.measure(
                            text = dateStr,
                            style = TextStyle(fontSize = 10.sp, color = onSurfaceVariant.copy(alpha = 0.7f))
                        )
                        drawText(
                            textLayoutResult = textLayout,
                            topLeft = Offset(leftPadding + chartWidth / 2f - textLayout.size.width / 2f, height - textLayout.size.height)
                        )
                    } else {
                        val xIndices = when {
                            effectiveRecords.size <= 2 -> listOf(0, effectiveRecords.size - 1)
                            else -> listOf(0, effectiveRecords.size / 2, effectiveRecords.size - 1)
                        }.distinct()

                        xIndices.forEach { idx ->
                            val rec = effectiveRecords[idx]
                            val xPos = getX(rec.date)
                            val dateStr = dateFormatter.format(Date(rec.date))
                            val textLayout = textMeasurer.measure(
                                text = dateStr,
                                style = TextStyle(fontSize = 10.sp, color = onSurfaceVariant.copy(alpha = 0.7f))
                            )
                            val textX = (xPos - textLayout.size.width / 2f).coerceIn(leftPadding, width - textLayout.size.width)
                            drawText(
                                textLayoutResult = textLayout,
                                topLeft = Offset(textX, height - textLayout.size.height)
                            )
                        }
                    }
                }

                // 4. Draw Discrete Stacked Area Bands without color blending (Box 0 on top, Box 9 at the bottom)
                // Wrapped in clipRect so scrolled curves never leak outside the chart boundaries
                clipRect(left = leftPadding, top = 0f, right = width, bottom = chartHeight) {
                    for (level in 0..displayMaxBox) {
                        val color = boxColors[level]
                        val upperSeries = effectiveRecords.map { rec ->
                            (level..displayMaxBox).sumOf { rec.getBoxCount(it) }.toFloat()
                        }
                        val lowerSeries = effectiveRecords.map { rec ->
                            ((level + 1)..displayMaxBox).sumOf { rec.getBoxCount(it) }.toFloat()
                        }

                        // Skip if this box is empty across all records
                        val hasCards = effectiveRecords.any { rec -> rec.getBoxCount(level) > 0 }
                        if (!hasCards) continue

                        val path = Path()
                        val linePath = Path()

                        val firstX = getX(effectiveRecords[0].date)
                        val firstUpperY = chartHeight - (upperSeries[0] / maxTotal.toFloat()) * chartHeight * anim
                        val firstLowerY = chartHeight - (lowerSeries[0] / maxTotal.toFloat()) * chartHeight * anim

                        // Upper curve: left to right
                        path.moveTo(firstX, firstUpperY)
                        linePath.moveTo(firstX, firstUpperY)

                        if (effectiveRecords.size == 1) {
                            val lastX = leftPadding + chartWidth
                            path.lineTo(lastX, firstUpperY)
                            linePath.lineTo(lastX, firstUpperY)

                            path.lineTo(lastX, firstLowerY)
                            path.lineTo(firstX, firstLowerY)
                            path.close()
                        } else {
                            for (i in 1 until effectiveRecords.size) {
                                val prevX = getX(effectiveRecords[i - 1].date)
                                val prevUpperY = chartHeight - (upperSeries[i - 1] / maxTotal.toFloat()) * chartHeight * anim
                                val curX = getX(effectiveRecords[i].date)
                                val curUpperY = chartHeight - (upperSeries[i] / maxTotal.toFloat()) * chartHeight * anim

                                val midX = (prevX + curX) / 2f
                                path.cubicTo(midX, prevUpperY, midX, curUpperY, curX, curUpperY)
                                linePath.cubicTo(midX, prevUpperY, midX, curUpperY, curX, curUpperY)
                            }

                            // Right edge: down to lower curve
                            val lastIndex = effectiveRecords.size - 1
                            val lastLowerY = chartHeight - (lowerSeries[lastIndex] / maxTotal.toFloat()) * chartHeight * anim
                            path.lineTo(getX(effectiveRecords[lastIndex].date), lastLowerY)

                            // Lower curve: right to left
                            for (i in (effectiveRecords.size - 1) downTo 1) {
                                val curX = getX(effectiveRecords[i].date)
                                val curLowerY = chartHeight - (lowerSeries[i] / maxTotal.toFloat()) * chartHeight * anim
                                val prevX = getX(effectiveRecords[i - 1].date)
                                val prevLowerY = chartHeight - (lowerSeries[i - 1] / maxTotal.toFloat()) * chartHeight * anim

                                val midX = (prevX + curX) / 2f
                                path.cubicTo(midX, curLowerY, midX, prevLowerY, prevX, prevLowerY)
                            }

                            path.close()
                        }

                        // Fill each band cleanly with its own solid box color (no transparency bleed-through)
                        drawPath(
                            path = path,
                            color = color.copy(alpha = 0.88f)
                        )

                        // Line border along the top of the band
                        drawPath(
                            path = linePath,
                            color = color,
                            style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                }
            }

            if (canDrag) {
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "← Drag horizontally to explore history →",
                        style = MaterialTheme.typography.labelSmall,
                        color = onSurfaceVariant.copy(alpha = 0.65f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Chart Legend: display boxes from Box 0 up to displayMaxBox (matching top-to-bottom graph order)
            val relevantBoxes = (0..displayMaxBox).toList()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 4.dp),
                horizontalArrangement = if (displayMaxBox <= 4) Arrangement.SpaceBetween else Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                relevantBoxes.forEach { box ->
                    LegendItem(
                        color = boxColors[box],
                        label = when (box) {
                            0 -> "Box 0"
                            9 -> "Box 9 (Mastered)"
                            else -> "Box $box"
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun LegendItem(
    color: Color,
    label: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, CircleShape)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
