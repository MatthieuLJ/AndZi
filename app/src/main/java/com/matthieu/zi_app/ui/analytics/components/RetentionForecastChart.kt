package com.matthieu.zi_app.ui.analytics.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.matthieu.zi_app.domain.srs.ForecastPoint
import com.matthieu.zi_app.domain.srs.MemoryForecastData
import java.util.Locale
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun RetentionForecastChart(
    forecastData: MemoryForecastData,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    val animatedProgress = remember { Animatable(0f) }
    var scrubFraction by remember { mutableStateOf<Float?>(null) }

    LaunchedEffect(forecastData) {
        animatedProgress.snapTo(0f)
        animatedProgress.animateTo(1f, animationSpec = tween(durationMillis = 650))
    }

    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.tertiary
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val surfaceColor = MaterialTheme.colorScheme.surface
    val outlineVariant = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
    val errorColor = MaterialTheme.colorScheme.error

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Retention & Workload Forecast",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Logarithmic decay curve & scheduled reviews",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (forecastData.points.isEmpty() || forecastData.totalCards == 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(210.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Add cards to study to view memory retention forecast",
                        style = MaterialTheme.typography.bodyMedium,
                        color = onSurfaceVariant
                    )
                }
            } else {
                // Interactive Scrub Info Banner
                val activeScrub = scrubFraction
                val selectedPoint = if (activeScrub != null && forecastData.points.isNotEmpty()) {
                    val index = (activeScrub * (forecastData.points.size - 1))
                        .roundToInt()
                        .coerceIn(0, forecastData.points.size - 1)
                    forecastData.points[index]
                } else null

                if (selectedPoint != null) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "At ${selectedPoint.label}: ${String.format(Locale.getDefault(), "%.1f%%", selectedPoint.retentionPercentage)} retention",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "~${selectedPoint.expectedRetainedWords.roundToInt()} words retained",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }

                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(210.dp)
                        .padding(top = 8.dp, bottom = 4.dp, start = 8.dp, end = 8.dp)
                        .pointerInput(forecastData) {
                            detectTapGestures(
                                onPress = { offset ->
                                    val leftPadding = 38.dp.toPx()
                                    val chartWidth = size.width - leftPadding
                                    val frac = ((offset.x - leftPadding) / chartWidth).coerceIn(0f, 1f)
                                    scrubFraction = frac
                                }
                            )
                        }
                        .pointerInput(forecastData) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    val leftPadding = 38.dp.toPx()
                                    val chartWidth = size.width - leftPadding
                                    val frac = ((offset.x - leftPadding) / chartWidth).coerceIn(0f, 1f)
                                    scrubFraction = frac
                                },
                                onDragEnd = { scrubFraction = null },
                                onDragCancel = { scrubFraction = null },
                                onDrag = { change, _ ->
                                    val leftPadding = 38.dp.toPx()
                                    val chartWidth = size.width - leftPadding
                                    val frac = ((change.position.x - leftPadding) / chartWidth).coerceIn(0f, 1f)
                                    scrubFraction = frac
                                }
                            )
                        }
                ) {
                    val anim = animatedProgress.value
                    val width = size.width
                    val height = size.height
                    val bottomPadding = 24.dp.toPx()
                    val leftPadding = 38.dp.toPx()
                    val chartWidth = width - leftPadding
                    val chartHeight = height - bottomPadding

                    if (chartWidth <= 0 || chartHeight <= 0) return@Canvas

                    // 1. Draw Y-Axis Gridlines (0%, 50%, 80% Target, 100%)
                    val yLevels = listOf(0f, 0.5f, 0.8f, 1.0f)
                    for (level in yLevels) {
                        val yPos = chartHeight - (level * chartHeight)
                        val isTarget = level == 0.8f

                        drawLine(
                            color = if (isTarget) errorColor.copy(alpha = 0.5f) else outlineVariant,
                            start = Offset(leftPadding, yPos),
                            end = Offset(width, yPos),
                            strokeWidth = if (isTarget) 1.5.dp.toPx() else 1.dp.toPx(),
                            pathEffect = if (isTarget) PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f) else null
                        )

                        val label = if (isTarget) "80% Target" else "${(level * 100).toInt()}%"
                        val textLayout = textMeasurer.measure(
                            text = label,
                            style = TextStyle(
                                fontSize = 9.sp,
                                color = if (isTarget) errorColor else onSurfaceVariant.copy(alpha = 0.7f),
                                fontWeight = if (isTarget) FontWeight.SemiBold else FontWeight.Normal
                            )
                        )
                        drawText(
                            textLayoutResult = textLayout,
                            topLeft = Offset(leftPadding - textLayout.size.width - 4.dp.toPx(), yPos - textLayout.size.height / 2f)
                        )
                    }

                    // 2. Draw Due Workload Histogram Bars at Base
                    val maxBucketCount = max(forecastData.dueBuckets.maxOfOrNull { it.count } ?: 1, 1).toFloat()
                    val maxBarHeight = chartHeight * 0.35f
                    val bucketCount = forecastData.dueBuckets.size

                    forecastData.dueBuckets.forEachIndexed { idx, bucket ->
                        if (bucket.count > 0) {
                            val bucketFraction = (idx.toFloat() + 0.5f) / bucketCount.toFloat()
                            val barX = leftPadding + (bucketFraction * chartWidth)
                            val barHeight = (bucket.count.toFloat() / maxBucketCount) * maxBarHeight * anim
                            val barWidth = (chartWidth / (bucketCount * 2.2f))

                            drawRoundRect(
                                color = secondaryColor.copy(alpha = 0.45f),
                                topLeft = Offset(barX - barWidth / 2f, chartHeight - barHeight),
                                size = Size(barWidth, barHeight),
                                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                            )

                            // Workload count label above bar
                            val countText = textMeasurer.measure(
                                text = "${bucket.count}",
                                style = TextStyle(fontSize = 8.sp, color = secondaryColor, fontWeight = FontWeight.Bold)
                            )
                            drawText(
                                textLayoutResult = countText,
                                topLeft = Offset(barX - countText.size.width / 2f, chartHeight - barHeight - countText.size.height - 2.dp.toPx())
                            )
                        }
                    }

                    // 3. Draw Logarithmic X-Axis Ticks & Labels
                    val xLabels = listOf(
                        Pair("Today", 0f),
                        Pair("+1d", 0.18f),
                        Pair("+3d", 0.34f),
                        Pair("+7d", 0.52f),
                        Pair("+14d", 0.68f),
                        Pair("+30d", 0.84f),
                        Pair("+90d", 1.0f)
                    )

                    xLabels.forEach { (label, frac) ->
                        val xPos = leftPadding + (frac * chartWidth)
                        val textLayout = textMeasurer.measure(
                            text = label,
                            style = TextStyle(fontSize = 9.sp, color = onSurfaceVariant.copy(alpha = 0.75f))
                        )
                        val textX = (xPos - textLayout.size.width / 2f).coerceIn(leftPadding, width - textLayout.size.width)
                        drawText(
                            textLayoutResult = textLayout,
                            topLeft = Offset(textX, height - textLayout.size.height)
                        )
                    }

                    // 4. Draw Continuous Smooth Retention Curve
                    val points = forecastData.points
                    if (points.isNotEmpty()) {
                        val path = Path()
                        val linePath = Path()

                        val firstPoint = points.first()
                        val firstX = leftPadding
                        val firstY = chartHeight - (firstPoint.retentionPercentage / 100f) * chartHeight * anim

                        path.moveTo(firstX, chartHeight)
                        path.lineTo(firstX, firstY)
                        linePath.moveTo(firstX, firstY)

                        for (i in 1 until points.size) {
                            val prevFrac = (i - 1).toFloat() / (points.size - 1).toFloat()
                            val curFrac = i.toFloat() / (points.size - 1).toFloat()

                            val prevX = leftPadding + (prevFrac * chartWidth)
                            val prevY = chartHeight - (points[i - 1].retentionPercentage / 100f) * chartHeight * anim

                            val curX = leftPadding + (curFrac * chartWidth)
                            val curY = chartHeight - (points[i].retentionPercentage / 100f) * chartHeight * anim

                            val midX = (prevX + curX) / 2f
                            path.cubicTo(midX, prevY, midX, curY, curX, curY)
                            linePath.cubicTo(midX, prevY, midX, curY, curX, curY)
                        }

                        path.lineTo(leftPadding + chartWidth, chartHeight)
                        path.close()

                        // Gradient fill
                        drawPath(
                            path = path,
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    primaryColor.copy(alpha = 0.45f),
                                    primaryColor.copy(alpha = 0.05f)
                                ),
                                startY = 0f,
                                endY = chartHeight
                            )
                        )

                        // Line stroke
                        drawPath(
                            path = linePath,
                            color = primaryColor,
                            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                        )

                        // 5. Draw Interactive Scrub Indicator
                        val scrub = scrubFraction
                        if (scrub != null) {
                            val scrubX = leftPadding + (scrub * chartWidth)
                            val scrubIndex = (scrub * (points.size - 1)).roundToInt().coerceIn(0, points.size - 1)
                            val scrubPoint = points[scrubIndex]
                            val scrubY = chartHeight - (scrubPoint.retentionPercentage / 100f) * chartHeight * anim

                            drawLine(
                                color = primaryColor.copy(alpha = 0.7f),
                                start = Offset(scrubX, 0f),
                                end = Offset(scrubX, chartHeight),
                                strokeWidth = 1.5.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                            )

                            drawCircle(
                                color = surfaceColor,
                                radius = 6.dp.toPx(),
                                center = Offset(scrubX, scrubY)
                            )
                            drawCircle(
                                color = primaryColor,
                                radius = 4.5.dp.toPx(),
                                center = Offset(scrubX, scrubY)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Chart Legend
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LegendItem(
                        color = primaryColor,
                        label = "Retention Decay Curve"
                    )
                    LegendItem(
                        color = secondaryColor.copy(alpha = 0.6f),
                        label = "Cards Due Workload"
                    )
                    LegendItem(
                        color = errorColor.copy(alpha = 0.8f),
                        label = "80% Target Line"
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
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
