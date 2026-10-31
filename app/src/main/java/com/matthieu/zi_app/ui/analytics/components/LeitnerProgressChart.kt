package com.matthieu.zi_app.ui.analytics.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.matthieu.zi_app.data.local.entity.ProgressRecordEntity
import com.matthieu.zi_app.ui.theme.LeitnerBoxColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

private val boxColors = LeitnerBoxColors

@Composable
fun LeitnerProgressChart(
    records: List<ProgressRecordEntity>,
    modifier: Modifier = Modifier
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
            ) {
                val anim = animatedProgress.value
                val width = size.width
                val height = size.height
                val bottomPadding = 24.dp.toPx()
                val leftPadding = 36.dp.toPx()
                val chartWidth = width - leftPadding
                val chartHeight = height - bottomPadding

                if (chartWidth <= 0 || chartHeight <= 0) return@Canvas

                // 1. Compute totals per record using all relevant boxes
                val pointTotals = records.map { rec ->
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

                // 3. Draw X Axis Date Labels (Start, Middle, End)
                if (records.isNotEmpty()) {
                    val dateFormatter = SimpleDateFormat("MMM d", Locale.getDefault())
                    val xIndices = when {
                        records.size <= 2 -> listOf(0, records.size - 1)
                        else -> listOf(0, records.size / 2, records.size - 1)
                    }.distinct()

                    xIndices.forEach { idx ->
                        val rec = records[idx]
                        val xPos = if (records.size == 1) {
                            leftPadding + chartWidth / 2f
                        } else {
                            leftPadding + (idx.toFloat() / (records.size - 1)) * chartWidth
                        }

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

                // 4. Draw Cumulative Stacked Area Curves for each relevant tier (from top level down to 0)
                val cumulativeSeries = (displayMaxBox downTo 0).map { level ->
                    level to records.map { rec ->
                        (0..level).sumOf { rec.getBoxCount(it) }.toFloat()
                    }
                }

                for ((level, series) in cumulativeSeries) {
                    val color = boxColors[level]

                    val path = Path()
                    val linePath = Path()

                    val firstX = leftPadding
                    val firstY = chartHeight - (series[0] / maxTotal.toFloat()) * chartHeight * anim

                    path.moveTo(firstX, chartHeight)
                    path.lineTo(firstX, firstY)
                    linePath.moveTo(firstX, firstY)

                    if (records.size == 1) {
                        val lastX = leftPadding + chartWidth
                        path.lineTo(lastX, firstY)
                        path.lineTo(lastX, chartHeight)
                        path.close()

                        linePath.lineTo(lastX, firstY)
                    } else {
                        for (i in 1 until records.size) {
                            val prevX = leftPadding + ((i - 1).toFloat() / (records.size - 1)) * chartWidth
                            val prevY = chartHeight - (series[i - 1] / maxTotal.toFloat()) * chartHeight * anim

                            val curX = leftPadding + (i.toFloat() / (records.size - 1)) * chartWidth
                            val curY = chartHeight - (series[i] / maxTotal.toFloat()) * chartHeight * anim

                            // Smooth cubic bezier
                            val midX = (prevX + curX) / 2f
                            path.cubicTo(midX, prevY, midX, curY, curX, curY)
                            linePath.cubicTo(midX, prevY, midX, curY, curX, curY)
                        }

                        val lastX = leftPadding + chartWidth
                        path.lineTo(lastX, chartHeight)
                        path.close()
                    }

                    // Fill stacked gradient
                    drawPath(
                        path = path,
                        brush = Brush.verticalGradient(
                            colors = listOf(color.copy(alpha = 0.55f), color.copy(alpha = 0.15f)),
                            startY = 0f,
                            endY = chartHeight
                        )
                    )

                    // Line border
                    drawPath(
                        path = linePath,
                        color = color.copy(alpha = 0.9f),
                        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Chart Legend: only display boxes that are relevant (from displayMaxBox down to 0)
            val relevantBoxes = (0..displayMaxBox).reversed()
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
