package com.matthieu.zi_app.ui.analytics.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.matthieu.zi_app.domain.srs.MemoryHealthData
import java.util.Locale
import kotlin.math.roundToInt

private val StrongColor = Color(0xFF43A047)   // Green (>90%)
private val MaturingColor = Color(0xFFFFB74D) // Amber (70-90%)
private val DueColor = Color(0xFFE57373)      // Coral / Red (<70%)

@Composable
fun MemoryHealthCard(
    memoryHealth: MemoryHealthData,
    modifier: Modifier = Modifier
) {
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
                        text = "Vocabulary Memory Health",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Live Bayesian retention strength",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = when {
                        memoryHealth.retentionPercentage >= 85f -> StrongColor.copy(alpha = 0.15f)
                        memoryHealth.retentionPercentage >= 70f -> MaturingColor.copy(alpha = 0.15f)
                        else -> DueColor.copy(alpha = 0.15f)
                    }
                ) {
                    Text(
                        text = String.format(Locale.getDefault(), "%.1f%% Recall", memoryHealth.retentionPercentage),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = when {
                            memoryHealth.retentionPercentage >= 85f -> StrongColor
                            memoryHealth.retentionPercentage >= 70f -> Color(0xFFFB8C00)
                            else -> Color(0xFFD32F2F)
                        },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Total Retained Words Text
            Text(
                text = "${memoryHealth.totalRetainedWords.roundToInt()} / ${memoryHealth.totalActiveWords} words retained",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Multi-segment horizontal health bar
            val total = maxOf(memoryHealth.totalActiveWords, 1).toFloat()
            val strongFraction by animateFloatAsState(
                targetValue = memoryHealth.strongCount / total,
                label = "strong_bar"
            )
            val maturingFraction by animateFloatAsState(
                targetValue = memoryHealth.maturingCount / total,
                label = "maturing_bar"
            )
            val dueFraction by animateFloatAsState(
                targetValue = memoryHealth.dueCount / total,
                label = "due_bar"
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                if (strongFraction > 0f) {
                    Box(
                        modifier = Modifier
                            .weight(strongFraction)
                            .fillMaxHeight()
                            .background(StrongColor)
                    )
                }
                if (maturingFraction > 0f) {
                    Box(
                        modifier = Modifier
                            .weight(maturingFraction)
                            .fillMaxHeight()
                            .background(MaturingColor)
                    )
                }
                if (dueFraction > 0f) {
                    Box(
                        modifier = Modifier
                            .weight(dueFraction)
                            .fillMaxHeight()
                            .background(DueColor)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Legend
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                HealthLegendItem(
                    color = StrongColor,
                    label = "Strong (>90%)",
                    count = memoryHealth.strongCount
                )
                HealthLegendItem(
                    color = MaturingColor,
                    label = "Maturing (70-90%)",
                    count = memoryHealth.maturingCount
                )
                HealthLegendItem(
                    color = DueColor,
                    label = "Due (<70%)",
                    count = memoryHealth.dueCount
                )
            }
        }
    }
}

@Composable
private fun HealthLegendItem(
    color: Color,
    label: String,
    count: Int,
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
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "$count $label",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
