package com.matthieu.zi_app.ui.analytics.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

private val tierColors = listOf(
    Color(0xFFE57373), // Box 0: Coral / Red
    Color(0xFFFFB74D), // Box 1: Orange
    Color(0xFFFFD54F), // Box 2: Amber
    Color(0xFF64B5F6), // Box 3: Blue
    Color(0xFF81C784)  // Box 4+: Emerald / Mastered
)

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

                // 1. Compute totals per record
                val pointTotals = records.map {
                    val sum = it.totalBox1 + it.totalBox2 + it.totalBox3 + it.totalBox4 + it.totalBox5
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

                // 4. Draw Cumulative Stacked Area Curves for each tier
                // Tier 4+: totalBox1+2+3+4+5
                // Tier 3: totalBox1+2+3+4
                // Tier 2: totalBox1+2+3
                // Tier 1: totalBox1+2
                // Tier 0: totalBox1
                val cumulativeSeries = listOf(
                    records.map { (it.totalBox1 + it.totalBox2 + it.totalBox3 + it.totalBox4 + it.totalBox5).toFloat() },
                    records.map { (it.totalBox1 + it.totalBox2 + it.totalBox3 + it.totalBox4).toFloat() },
                    records.map { (it.totalBox1 + it.totalBox2 + it.totalBox3).toFloat() },
                    records.map { (it.totalBox1 + it.totalBox2).toFloat() },
                    records.map { it.totalBox1.toFloat() }
                )

                // Render in reverse order (Tier 4+ background down to Tier 0 foreground)
                for (seriesIndex in 0 until cumulativeSeries.size) {
                    val series = cumulativeSeries[seriesIndex]
                    val tierIdx = cumulativeSeries.size - 1 - seriesIndex
                    val color = tierColors[tierIdx]

                    val path = Path()
                    val linePath = Path()

                    val firstX = leftPadding
                    val firstY = chartHeight - (series[0] / maxTotal.toFloat()) * chartHeight * anim

                    path.moveTo(firstX, chartHeight)
                    path.lineTo(firstX, firstY)
                    linePath.moveTo(firstX, firstY)

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

                    val lastX = if (records.size == 1) leftPadding + chartWidth else leftPadding + chartWidth
                    path.lineTo(lastX, chartHeight)
                    path.close()

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

            Spacer(modifier = Modifier.height(8.dp))

            // Chart Legend
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                LegendItem(color = tierColors[4], label = "Mastered (4+)")
                LegendItem(color = tierColors[3], label = "Box 3")
                LegendItem(color = tierColors[2], label = "Box 2")
                LegendItem(color = tierColors[1], label = "Box 1")
                LegendItem(color = tierColors[0], label = "Box 0")
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
