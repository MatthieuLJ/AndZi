package com.matthieu.zi_app.domain.badges

enum class BadgeCategory(val title: String, val description: String) {
    DAILY_STREAK("Daily Streak", "Consistent daily study"),
    CORRECT_STREAK("Correct Answers", "Consecutive correct tests"),
    DAILY_TESTS("Daily Volume", "Total tests completed in a single day"),
    HSK_LEVEL("HSK Mastery", "All words in an HSK level reached Box 3+")
}

enum class BadgeTier {
    BRONZE,
    SILVER,
    GOLD,
    DIAMOND
}

enum class BadgeId(
    val category: BadgeCategory,
    val threshold: Int,
    val title: String,
    val description: String,
    val tier: BadgeTier
) {
    // 1. Longest daily streak
    STREAK_7(BadgeCategory.DAILY_STREAK, 7, "7-Day Streak", "Study for 7 consecutive days", BadgeTier.BRONZE),
    STREAK_31(BadgeCategory.DAILY_STREAK, 31, "31-Day Streak", "Study for 31 consecutive days", BadgeTier.SILVER),
    STREAK_365(BadgeCategory.DAILY_STREAK, 365, "1-Year Streak", "Study for 365 consecutive days", BadgeTier.GOLD),
    STREAK_730(BadgeCategory.DAILY_STREAK, 730, "2-Year Streak", "Study for 730 consecutive days", BadgeTier.DIAMOND),

    // 2. Longest streak of correct tests
    CORRECT_10(BadgeCategory.CORRECT_STREAK, 10, "10 In A Row", "10 consecutive correct answers", BadgeTier.BRONZE),
    CORRECT_20(BadgeCategory.CORRECT_STREAK, 20, "20 In A Row", "20 consecutive correct answers", BadgeTier.SILVER),
    CORRECT_50(BadgeCategory.CORRECT_STREAK, 50, "50 In A Row", "50 consecutive correct answers", BadgeTier.GOLD),
    CORRECT_100(BadgeCategory.CORRECT_STREAK, 100, "100 In A Row", "100 consecutive correct answers", BadgeTier.DIAMOND),

    // 3. Most tests in a day
    DAILY_TESTS_50(BadgeCategory.DAILY_TESTS, 50, "50 In One Day", "Complete 50 tests in a single day", BadgeTier.BRONZE),
    DAILY_TESTS_100(BadgeCategory.DAILY_TESTS, 100, "100 In One Day", "Complete 100 tests in a single day", BadgeTier.SILVER),
    DAILY_TESTS_200(BadgeCategory.DAILY_TESTS, 200, "200 In One Day", "Complete 200 tests in a single day", BadgeTier.GOLD),
    DAILY_TESTS_500(BadgeCategory.DAILY_TESTS, 500, "500 In One Day", "Complete 500 tests in a single day", BadgeTier.DIAMOND),

    // 4. HSK Level Mastery (all words reached Box 3+)
    HSK_1(BadgeCategory.HSK_LEVEL, 1, "HSK 1 Mastered", "Reach Box 3+ for all HSK 1 words", BadgeTier.BRONZE),
    HSK_2(BadgeCategory.HSK_LEVEL, 2, "HSK 2 Mastered", "Reach Box 3+ for all HSK 2 words", BadgeTier.BRONZE),
    HSK_3(BadgeCategory.HSK_LEVEL, 3, "HSK 3 Mastered", "Reach Box 3+ for all HSK 3 words", BadgeTier.SILVER),
    HSK_4(BadgeCategory.HSK_LEVEL, 4, "HSK 4 Mastered", "Reach Box 3+ for all HSK 4 words", BadgeTier.SILVER),
    HSK_5(BadgeCategory.HSK_LEVEL, 5, "HSK 5 Mastered", "Reach Box 3+ for all HSK 5 words", BadgeTier.GOLD),
    HSK_6(BadgeCategory.HSK_LEVEL, 6, "HSK 6 Mastered", "Reach Box 3+ for all HSK 6 words", BadgeTier.DIAMOND);

    companion object {
        fun fromId(id: String): BadgeId? = entries.firstOrNull { it.name == id }
        fun fromHskLevel(level: Int): BadgeId? = entries.firstOrNull { it.category == BadgeCategory.HSK_LEVEL && it.threshold == level }
    }
}

data class BadgeItem(
    val badge: BadgeId,
    val isUnlocked: Boolean,
    val unlockedAt: Long? = null,
    val currentProgress: Int,
    val target: Int
) {
    val progressFraction: Float
        get() = (currentProgress.toFloat() / target.toFloat()).coerceIn(0f, 1f)
}

data class BadgesSummary(
    val currentDailyStreak: Int,
    val longestDailyStreak: Int,
    val currentCorrectStreak: Int,
    val longestCorrectStreak: Int,
    val testsToday: Int,
    val mostTestsInADay: Int,
    val badges: List<BadgeItem>,
    val hskMasteredLevelsCount: Int = 0,
    val hskBox3PlusWordsCount: Int = 0
)

data class TestAnswerRecordResult(
    val newlyUnlockedBadges: List<BadgeId>,
    val isNewDailyStreakRecord: Boolean,
    val isNewCorrectStreakRecord: Boolean,
    val isNewDailyTestsRecord: Boolean,
    val currentDailyStreak: Int,
    val longestDailyStreak: Int,
    val currentCorrectStreak: Int,
    val longestCorrectStreak: Int,
    val testsToday: Int,
    val mostTestsInADay: Int
)
