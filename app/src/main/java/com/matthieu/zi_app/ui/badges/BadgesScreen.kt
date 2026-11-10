package com.matthieu.zi_app.ui.badges

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.matthieu.zi_app.domain.badges.BadgeCategory
import com.matthieu.zi_app.domain.badges.BadgeItem
import com.matthieu.zi_app.domain.badges.BadgeTier

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BadgesScreen(
    viewModel: BadgesViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    BadgesScreenContent(
        uiState = uiState,
        onNavigateBack = onNavigateBack,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BadgesScreenContent(
    uiState: BadgesUiState,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Streaks & Badges",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Achievements and personal records",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier
    ) { innerPadding ->
        if (uiState.isLoading || uiState.summary == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else {
            val summary = uiState.summary!!

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Category 1: Longest Daily Streak
                item {
                    val dailyBadges = summary.badges.filter { it.badge.category == BadgeCategory.DAILY_STREAK }
                    BadgeCategorySection(
                        title = "Daily Streak",
                        subtitle = "Consistency over calendar days",
                        icon = Icons.Default.Whatshot,
                        iconTint = MaterialTheme.colorScheme.error,
                        primaryStatLabel = "Current Streak",
                        primaryStatValue = "${summary.currentDailyStreak} Days",
                        secondaryStatLabel = "All-Time Best",
                        secondaryStatValue = "${summary.longestDailyStreak} Days",
                        badges = dailyBadges
                    )
                }

                // Category 2: Longest Streak of Correct Tests
                item {
                    val correctBadges = summary.badges.filter { it.badge.category == BadgeCategory.CORRECT_STREAK }
                    BadgeCategorySection(
                        title = "Streak of Correct Tests",
                        subtitle = "Consecutive correct answers without mistakes",
                        icon = Icons.Default.CheckCircle,
                        iconTint = Color(0xFF10B981),
                        primaryStatLabel = "Current Streak",
                        primaryStatValue = "${summary.currentCorrectStreak} Tests",
                        secondaryStatLabel = "All-Time Best",
                        secondaryStatValue = "${summary.longestCorrectStreak} Tests",
                        badges = correctBadges
                    )
                }

                // Category 3: Most Tests in a Day
                item {
                    val dailyTestsBadges = summary.badges.filter { it.badge.category == BadgeCategory.DAILY_TESTS }
                    BadgeCategorySection(
                        title = "Most Tests in a Day",
                        subtitle = "Peak single-day practice volume",
                        icon = Icons.Default.Speed,
                        iconTint = Color(0xFF8E24AA),
                        primaryStatLabel = "Tests Today",
                        primaryStatValue = "${summary.testsToday} Tests",
                        secondaryStatLabel = "All-Time Best",
                        secondaryStatValue = "${summary.mostTestsInADay} Tests",
                        badges = dailyTestsBadges
                    )
                }

                // Category 4: HSK Mastery
                item {
                    val hskBadges = summary.badges.filter { it.badge.category == BadgeCategory.HSK_LEVEL }
                    BadgeCategorySection(
                        title = "HSK Mastery",
                        subtitle = "Reach Box 3+ for all words in each HSK level",
                        icon = Icons.Default.School,
                        iconTint = Color(0xFF2563EB),
                        primaryStatLabel = "Mastered Levels",
                        primaryStatValue = "${summary.hskMasteredLevelsCount} / 6",
                        secondaryStatLabel = "Box 3+ Words",
                        secondaryStatValue = "${summary.hskBox3PlusWordsCount} Words",
                        badges = hskBadges
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun BadgeCategorySection(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconTint: Color,
    primaryStatLabel: String,
    primaryStatValue: String,
    secondaryStatLabel: String,
    secondaryStatValue: String,
    badges: List<BadgeItem>,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(iconTint.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Stat pills
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = primaryStatLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = primaryStatValue,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                Surface(
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = secondaryStatLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = secondaryStatValue,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Badges Grid/List: only show unlocked badges and the next upcoming badge
            val nextUpcomingBadge = badges.firstOrNull { !it.isUnlocked }?.badge
            val visibleBadges = badges.filter { it.isUnlocked || it.badge == nextUpcomingBadge }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                visibleBadges.forEach { item ->
                    BadgeRowCard(
                        badgeItem = item,
                        isNextUpcoming = item.badge == nextUpcomingBadge
                    )
                }
            }
        }
    }
}

@Composable
fun BadgeRowCard(
    badgeItem: BadgeItem,
    isNextUpcoming: Boolean = false,
    modifier: Modifier = Modifier
) {
    val tierColor = when (badgeItem.badge.tier) {
        BadgeTier.BRONZE -> Color(0xFFCD7F32)
        BadgeTier.SILVER -> Color(0xFF9E9E9E)
        BadgeTier.GOLD -> Color(0xFFFFB300)
        BadgeTier.DIAMOND -> Color(0xFF00B0FF)
    }

    val isUnlocked = badgeItem.isUnlocked
    val containerColor = if (isUnlocked) {
        tierColor.copy(alpha = 0.08f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    }

    val border = if (isUnlocked) {
        BorderStroke(1.dp, tierColor.copy(alpha = 0.5f))
    } else if (isNextUpcoming) {
        BorderStroke(1.dp, tierColor.copy(alpha = 0.35f))
    } else {
        null
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = containerColor,
        border = border,
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Badge Icon
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(if (isUnlocked) tierColor.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                val icon = if (isUnlocked) {
                    when (badgeItem.badge.tier) {
                        BadgeTier.BRONZE -> Icons.Default.MilitaryTech
                        BadgeTier.SILVER -> Icons.Default.WorkspacePremium
                        BadgeTier.GOLD -> Icons.Default.EmojiEvents
                        BadgeTier.DIAMOND -> Icons.Default.Star
                    }
                } else {
                    Icons.Default.Lock
                }

                Icon(
                    imageVector = icon,
                    contentDescription = badgeItem.badge.title,
                    tint = if (isUnlocked) tierColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = badgeItem.badge.title,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = if (isUnlocked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    if (isUnlocked) {
                        Surface(
                            color = tierColor.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "Unlocked",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = tierColor,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    } else if (isNextUpcoming) {
                        Surface(
                            color = tierColor.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "${badgeItem.currentProgress} / ${badgeItem.target}",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = tierColor,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    } else {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "Locked",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = badgeItem.badge.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )

                if (!isUnlocked && isNextUpcoming) {
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { badgeItem.progressFraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = tierColor.copy(alpha = 0.85f),
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        strokeCap = StrokeCap.Round
                    )
                }
            }
        }
    }
}
