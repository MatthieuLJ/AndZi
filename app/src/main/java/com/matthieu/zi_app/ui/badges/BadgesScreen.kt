package com.matthieu.zi_app.ui.badges

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.matthieu.zi_app.domain.badges.BadgeCategory
import com.matthieu.zi_app.domain.badges.BadgeItem
import com.matthieu.zi_app.domain.badges.BadgeTier
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class CategoryPersonality(
    val category: BadgeCategory,
    val title: String,
    val personalityName: String,
    val subtitle: String,
    val icon: ImageVector,
    val primaryColor: Color,
    val secondaryColor: Color
) {
    DAILY_STREAK(
        category = BadgeCategory.DAILY_STREAK,
        title = "Daily Streak",
        personalityName = "The Eternal Flame",
        subtitle = "Consistency & daily momentum",
        icon = Icons.Default.Whatshot,
        primaryColor = Color(0xFFFF5722),
        secondaryColor = Color(0xFFFF9800)
    ),
    CORRECT_STREAK(
        category = BadgeCategory.CORRECT_STREAK,
        title = "Streak of Correct Tests",
        personalityName = "The Jade Archer",
        subtitle = "Razor-sharp precision without lapses",
        icon = Icons.Default.CheckCircle,
        primaryColor = Color(0xFF10B981),
        secondaryColor = Color(0xFF06B6D4)
    ),
    DAILY_TESTS(
        category = BadgeCategory.DAILY_TESTS,
        title = "Most Tests in a Day",
        personalityName = "The Iron Dynamo",
        subtitle = "Relentless stamina & peak volume",
        icon = Icons.Default.Speed,
        primaryColor = Color(0xFF8B5CF6),
        secondaryColor = Color(0xFFEC4899)
    ),
    HSK_LEVEL(
        category = BadgeCategory.HSK_LEVEL,
        title = "HSK Mastery",
        personalityName = "The Imperial Scholar",
        subtitle = "Reach Box 3+ across classical HSK tiers",
        icon = Icons.Default.School,
        primaryColor = Color(0xFF2563EB),
        secondaryColor = Color(0xFFF59E0B)
    );

    companion object {
        fun forCategory(category: BadgeCategory): CategoryPersonality =
            entries.firstOrNull { it.category == category } ?: DAILY_STREAK
    }
}

private fun getTierColors(tier: BadgeTier): List<Color> = when (tier) {
    BadgeTier.BRONZE -> listOf(Color(0xFFE6A76E), Color(0xFFCD7F32), Color(0xFF8D5B2A), Color(0xFFE6A76E))
    BadgeTier.SILVER -> listOf(Color(0xFFFFFFFF), Color(0xFFCBD5E1), Color(0xFF94A3B8), Color(0xFF64748B), Color(0xFFFFFFFF))
    BadgeTier.GOLD -> listOf(Color(0xFFFFFBEB), Color(0xFFFFD700), Color(0xFFF59E0B), Color(0xFFB45309), Color(0xFFFFFBEB))
    BadgeTier.DIAMOND -> listOf(Color(0xFFE0F2FE), Color(0xFF67E8F9), Color(0xFF00E5FF), Color(0xFF0284C7), Color(0xFFE0F2FE))
}

private fun getTierPrimaryColor(tier: BadgeTier): Color = when (tier) {
    BadgeTier.BRONZE -> Color(0xFFCD7F32)
    BadgeTier.SILVER -> Color(0xFF94A3B8)
    BadgeTier.GOLD -> Color(0xFFFFB300)
    BadgeTier.DIAMOND -> Color(0xFF00B0FF)
}

private fun formatUnlockDate(millis: Long?): String {
    if (millis == null || millis <= 0L) return "Unlocked"
    return try {
        val dt = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
        dt.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))
    } catch (e: Exception) {
        "Unlocked"
    }
}

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
    var selectedBadgeForDetail by remember { mutableStateOf<BadgeItem?>(null) }

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
            val allUnlockedBadges = summary.badges.filter { it.isUnlocked }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Top Trophy Showcase
                item {
                    TrophyShowcaseCard(
                        unlockedBadges = allUnlockedBadges,
                        totalBadgesCount = summary.badges.size,
                        onBadgeClick = { selectedBadgeForDetail = it }
                    )
                }

                // Category 1: Daily Streak (Flame Personality)
                item {
                    val dailyBadges = summary.badges.filter { it.badge.category == BadgeCategory.DAILY_STREAK }
                    PersonalityBadgeCategoryCard(
                        personality = CategoryPersonality.DAILY_STREAK,
                        primaryStatLabel = "Current Streak",
                        primaryStatValue = "${summary.currentDailyStreak} Days",
                        secondaryStatLabel = "All-Time Best",
                        secondaryStatValue = "${summary.longestDailyStreak} Days",
                        badges = dailyBadges,
                        onBadgeClick = { selectedBadgeForDetail = it }
                    )
                }

                // Category 2: Streak of Correct Tests (Jade Archer Personality)
                item {
                    val correctBadges = summary.badges.filter { it.badge.category == BadgeCategory.CORRECT_STREAK }
                    PersonalityBadgeCategoryCard(
                        personality = CategoryPersonality.CORRECT_STREAK,
                        primaryStatLabel = "Current Streak",
                        primaryStatValue = "${summary.currentCorrectStreak} Tests",
                        secondaryStatLabel = "All-Time Best",
                        secondaryStatValue = "${summary.longestCorrectStreak} Tests",
                        badges = correctBadges,
                        onBadgeClick = { selectedBadgeForDetail = it }
                    )
                }

                // Category 3: Most Tests in a Day (Iron Dynamo Personality)
                item {
                    val dailyTestsBadges = summary.badges.filter { it.badge.category == BadgeCategory.DAILY_TESTS }
                    PersonalityBadgeCategoryCard(
                        personality = CategoryPersonality.DAILY_TESTS,
                        primaryStatLabel = "Tests Today",
                        primaryStatValue = "${summary.testsToday} Tests",
                        secondaryStatLabel = "All-Time Best",
                        secondaryStatValue = "${summary.mostTestsInADay} Tests",
                        badges = dailyTestsBadges,
                        onBadgeClick = { selectedBadgeForDetail = it }
                    )
                }

                // Category 4: HSK Mastery (Imperial Scholar Personality)
                item {
                    val hskBadges = summary.badges.filter { it.badge.category == BadgeCategory.HSK_LEVEL }
                    PersonalityBadgeCategoryCard(
                        personality = CategoryPersonality.HSK_LEVEL,
                        primaryStatLabel = "Mastered Levels",
                        primaryStatValue = "${summary.hskMasteredLevelsCount} / 6",
                        secondaryStatLabel = "Box 3+ Words",
                        secondaryStatValue = "${summary.hskBox3PlusWordsCount} Words",
                        badges = hskBadges,
                        onBadgeClick = { selectedBadgeForDetail = it }
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }

    selectedBadgeForDetail?.let { badgeItem ->
        BadgeDetailDialog(
            badgeItem = badgeItem,
            onDismiss = { selectedBadgeForDetail = null }
        )
    }
}

@Composable
private fun TrophyShowcaseCard(
    unlockedBadges: List<BadgeItem>,
    totalBadgesCount: Int,
    onBadgeClick: (BadgeItem) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            1.5.dp,
            Brush.linearGradient(
                listOf(Color(0xFFFFD700).copy(alpha = 0.6f), Color(0xFFF59E0B).copy(alpha = 0.2f))
            )
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    listOf(Color(0xFFFFD700).copy(alpha = 0.35f), Color(0xFFF59E0B).copy(alpha = 0.15f))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.EmojiEvents,
                            contentDescription = null,
                            tint = Color(0xFFFFB300),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Trophy Showcase",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Earned Medallions & Honors",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFFFD700).copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, Color(0xFFFFD700).copy(alpha = 0.4f))
                ) {
                    Text(
                        text = "${unlockedBadges.size} / $totalBadgesCount Unlocked",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFFD97706),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (unlockedBadges.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    unlockedBadges.forEach { badgeItem ->
                        BadgeMedallionGraphic(
                            badgeItem = badgeItem,
                            onClick = { onBadgeClick(badgeItem) }
                        )
                    }
                }
            } else {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = "Your Showcase Awaits",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Complete challenges below to unlock and display your medals here!",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PersonalityBadgeCategoryCard(
    personality: CategoryPersonality,
    primaryStatLabel: String,
    primaryStatValue: String,
    secondaryStatLabel: String,
    secondaryStatValue: String,
    badges: List<BadgeItem>,
    onBadgeClick: (BadgeItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val unlockedBadges = badges.filter { it.isUnlocked }
    val nextUpcomingBadge = badges.firstOrNull { !it.isUnlocked }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            1.2.dp,
            personality.primaryColor.copy(alpha = 0.35f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            // Category Header with Personality
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                listOf(personality.primaryColor.copy(alpha = 0.35f), personality.secondaryColor.copy(alpha = 0.15f))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = personality.icon,
                        contentDescription = null,
                        tint = personality.primaryColor,
                        modifier = Modifier.size(26.dp)
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = personality.title,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = personality.primaryColor.copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = personality.personalityName,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = personality.primaryColor,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = personality.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Two-Stat Pills
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    modifier = Modifier.weight(1f),
                    color = personality.primaryColor.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, personality.primaryColor.copy(alpha = 0.18f))
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
                    color = personality.secondaryColor.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, personality.secondaryColor.copy(alpha = 0.18f))
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
                            color = personality.primaryColor
                        )
                    }
                }
            }

            // Unlocked Badges Showcase for this category
            if (unlockedBadges.isNotEmpty()) {
                Spacer(modifier = Modifier.height(18.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = personality.primaryColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Unlocked Badges (${unlockedBadges.size})",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = personality.primaryColor
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    unlockedBadges.forEach { badgeItem ->
                        BadgeMedallionGraphic(
                            badgeItem = badgeItem,
                            onClick = { onBadgeClick(badgeItem) }
                        )
                    }
                }
            }

            // Next Upcoming Challenge Card
            if (nextUpcomingBadge != null) {
                Spacer(modifier = Modifier.height(18.dp))
                NextChallengeCard(
                    badgeItem = nextUpcomingBadge,
                    personality = personality
                )
            } else if (unlockedBadges.size == badges.size && badges.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFFFD700).copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, Color(0xFFFFD700).copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = Color(0xFFFFB300),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "All Category Badges Mastered!",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFFD97706)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BadgeMedallionGraphic(
    badgeItem: BadgeItem,
    onClick: (() -> Unit)? = null,
    size: Dp = 68.dp,
    modifier: Modifier = Modifier
) {
    val personality = CategoryPersonality.forCategory(badgeItem.badge.category)
    val tierColor = getTierPrimaryColor(badgeItem.badge.tier)
    val tierGradients = getTierColors(badgeItem.badge.tier)
    val starCount = when (badgeItem.badge.tier) {
        BadgeTier.BRONZE -> 1
        BadgeTier.SILVER -> 2
        BadgeTier.GOLD -> 3
        BadgeTier.DIAMOND -> 4
    }

    Column(
        modifier = modifier
            .widthIn(min = size + 16.dp, max = size + 36.dp)
            .clickable(enabled = onClick != null) { onClick?.invoke() },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Medallion Disc
        Box(
            modifier = Modifier
                .size(size)
                .shadow(elevation = 6.dp, shape = CircleShape, spotColor = tierColor)
                .clip(CircleShape)
                .background(Brush.sweepGradient(tierGradients))
                .padding(3.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(personality.primaryColor, personality.primaryColor.copy(alpha = 0.75f), Color(0xFF1E1E24))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = personality.icon,
                    contentDescription = badgeItem.badge.title,
                    tint = Color.White,
                    modifier = Modifier.size((size.value * 0.42f).dp)
                )

                // Stars Ribbon
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(starCount) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = Color(0xFFFFD700),
                            modifier = Modifier.size(8.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Badge Title
        Text(
            text = badgeItem.badge.title,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        // Unlocked pill
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = tierColor.copy(alpha = 0.15f)
        ) {
            Text(
                text = "Unlocked",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                color = tierColor,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
            )
        }
    }
}

@Composable
private fun NextChallengeCard(
    badgeItem: BadgeItem,
    personality: CategoryPersonality,
    modifier: Modifier = Modifier
) {
    val tierColor = getTierPrimaryColor(badgeItem.badge.tier)
    val remaining = (badgeItem.target - badgeItem.currentProgress).coerceAtLeast(0)

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        border = BorderStroke(1.dp, personality.primaryColor.copy(alpha = 0.25f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(personality.primaryColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = personality.primaryColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Next Challenge",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = personality.primaryColor
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = tierColor.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = badgeItem.badge.tier.name.lowercase().replaceFirstChar { it.uppercase() },
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                                    color = tierColor,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                        Text(
                            text = badgeItem.badge.title,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = personality.primaryColor.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = "${badgeItem.currentProgress} / ${badgeItem.target}",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = personality.primaryColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = badgeItem.badge.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Progress Bar with personality gradient
            LinearProgressIndicator(
                progress = { badgeItem.progressFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = personality.primaryColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                strokeCap = StrokeCap.Round
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = if (remaining > 0) "$remaining more to reach this achievement!" else "Milestone reached!",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
private fun BadgeDetailDialog(
    badgeItem: BadgeItem,
    onDismiss: () -> Unit
) {
    val personality = CategoryPersonality.forCategory(badgeItem.badge.category)
    val tierColor = getTierPrimaryColor(badgeItem.badge.tier)

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", fontWeight = FontWeight.Bold)
            }
        },
        title = null,
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                BadgeMedallionGraphic(
                    badgeItem = badgeItem,
                    size = 84.dp
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = badgeItem.badge.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "${personality.title} • ${badgeItem.badge.tier.name} Tier",
                    style = MaterialTheme.typography.bodySmall,
                    color = tierColor,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = badgeItem.badge.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(12.dp))

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                ) {
                    Text(
                        text = if (badgeItem.isUnlocked) {
                            "Earned on ${formatUnlockDate(badgeItem.unlockedAt)}"
                        } else {
                            "Status: In Progress (${badgeItem.currentProgress} / ${badgeItem.target})"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        },
        shape = RoundedCornerShape(20.dp)
    )
}
