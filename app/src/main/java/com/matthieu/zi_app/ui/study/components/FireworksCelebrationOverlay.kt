package com.matthieu.zi_app.ui.study.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.matthieu.zi_app.domain.badges.BadgeId
import com.matthieu.zi_app.domain.badges.BadgeTier
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private class FireworkParticle(
    var x: Float,
    var y: Float,
    val vx: Float,
    val vy: Float,
    val color: Color,
    val radius: Float,
    val birthTime: Float
)

@Composable
fun FireworksCelebrationOverlay(
    unlockedBadges: List<BadgeId>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.78f)),
            contentAlignment = Alignment.Center
        ) {
            // Fireworks Canvas background
            FireworksCanvas(modifier = Modifier.fillMaxSize())

            // Foreground Celebration Card
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .padding(16.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFFFD700).copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "Celebration",
                            tint = Color(0xFFFFB300),
                            modifier = Modifier.size(44.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = if (unlockedBadges.size > 1) "Badges Unlocked!" else "Badge Unlocked!",
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Congratulations on reaching a new milestone in your Chinese studies!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Display all unlocked badges
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        unlockedBadges.forEach { badge ->
                            CelebrationBadgeCard(badge = badge)
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = onDismiss,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text(
                            text = "Awesome! Continue",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CelebrationBadgeCard(
    badge: BadgeId,
    modifier: Modifier = Modifier
) {
    val tierColor = when (badge.tier) {
        BadgeTier.BRONZE -> Color(0xFFCD7F32)
        BadgeTier.SILVER -> Color(0xFF9E9E9E)
        BadgeTier.GOLD -> Color(0xFFFFB300)
        BadgeTier.DIAMOND -> Color(0xFF00B0FF)
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = tierColor.copy(alpha = 0.12f),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, tierColor.copy(alpha = 0.6f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(tierColor.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center
            ) {
                val icon = when (badge.tier) {
                    BadgeTier.BRONZE -> Icons.Default.MilitaryTech
                    BadgeTier.SILVER -> Icons.Default.WorkspacePremium
                    BadgeTier.GOLD -> Icons.Default.EmojiEvents
                    BadgeTier.DIAMOND -> Icons.Default.Star
                }
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tierColor,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = badge.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = badge.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun FireworksCanvas(modifier: Modifier = Modifier) {
    val progress = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 3500, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            )
        )
    }

    val particlePalette = remember {
        listOf(
            Color(0xFFFFD700), // Gold
            Color(0xFFFF5722), // Deep Orange
            Color(0xFF00E676), // Bright Green
            Color(0xFF00E5FF), // Cyan
            Color(0xFFE040FB), // Magenta
            Color(0xFFFF1744), // Red
            Color(0xFFFFEB3B)  // Yellow
        )
    }

    val particles = remember {
        val list = mutableListOf<FireworkParticle>()
        val rnd = Random(42)
        val centers = listOf(
            Offset(0.25f, 0.35f),
            Offset(0.75f, 0.30f),
            Offset(0.50f, 0.22f),
            Offset(0.35f, 0.55f),
            Offset(0.68f, 0.60f)
        )

        for ((idx, center) in centers.withIndex()) {
            val burstTime = idx * 0.18f
            val count = 28
            for (i in 0 until count) {
                val angle = (2 * Math.PI * i / count).toFloat() + rnd.nextFloat() * 0.3f
                val speed = 140f + rnd.nextFloat() * 260f
                val vx = cos(angle) * speed
                val vy = sin(angle) * speed
                val color = particlePalette[rnd.nextInt(particlePalette.size)]
                val radius = 3.5f + rnd.nextFloat() * 4.5f
                list.add(FireworkParticle(center.x, center.y, vx, vy, color, radius, burstTime))
            }
        }
        list
    }

    Canvas(modifier = modifier) {
        val t = progress.value
        val w = size.width
        val h = size.height

        for (p in particles) {
            val age = (t - p.birthTime).let { if (it < 0f) it + 1f else it }
            if (age in 0f..0.85f) {
                val lifeFraction = age / 0.85f
                val alpha = (1f - lifeFraction).coerceIn(0f, 1f)
                val currentX = p.x * w + p.vx * age
                val currentY = p.y * h + p.vy * age + 180f * age * age // gravity effect

                drawCircle(
                    color = p.color.copy(alpha = alpha),
                    radius = p.radius * (1f - lifeFraction * 0.4f),
                    center = Offset(currentX, currentY)
                )
            }
        }
    }
}
