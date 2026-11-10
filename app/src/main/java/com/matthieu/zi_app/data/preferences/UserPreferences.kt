package com.matthieu.zi_app.data.preferences

import android.content.Context
import android.content.SharedPreferences
import com.matthieu.zi_app.domain.badges.BadgeCategory
import com.matthieu.zi_app.domain.badges.BadgeId
import com.matthieu.zi_app.domain.badges.BadgeItem
import com.matthieu.zi_app.domain.badges.BadgesSummary
import com.matthieu.zi_app.domain.badges.HskBadgeTracker
import com.matthieu.zi_app.domain.badges.HskLevelProgressInfo
import com.matthieu.zi_app.domain.badges.TestAnswerRecordResult
import com.matthieu.zi_app.ui.settings.AppThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

class UserPreferences(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(getSavedThemeMode())
    val themeMode: StateFlow<AppThemeMode> = _themeMode.asStateFlow()

    private val _notificationEnabled = MutableStateFlow(prefs.getBoolean(KEY_NOTIFICATION_ENABLED, true))
    val notificationEnabled: StateFlow<Boolean> = _notificationEnabled.asStateFlow()

    private val _writingQuizEnabled = MutableStateFlow(prefs.getBoolean(KEY_WRITING_QUIZ_ENABLED, true))
    val writingQuizEnabled: StateFlow<Boolean> = _writingQuizEnabled.asStateFlow()

    private val _audioStoriesEnabled = MutableStateFlow(prefs.getBoolean(KEY_AUDIO_STORIES_ENABLED, false))
    val audioStoriesEnabled: StateFlow<Boolean> = _audioStoriesEnabled.asStateFlow()

    private val _streakDays = MutableStateFlow(getStreakDays())
    val streakDays: StateFlow<Int> = _streakDays.asStateFlow()

    fun setThemeMode(mode: AppThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
        _themeMode.value = mode
    }

    fun setNotificationEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_NOTIFICATION_ENABLED, enabled).apply()
        _notificationEnabled.value = enabled
    }

    fun setWritingQuizEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_WRITING_QUIZ_ENABLED, enabled).apply()
        _writingQuizEnabled.value = enabled
    }

    fun setAudioStoriesEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUDIO_STORIES_ENABLED, enabled).apply()
        _audioStoriesEnabled.value = enabled
    }

    fun hasNoRecordedStudy(): Boolean = !prefs.contains(KEY_LAST_STUDY_DATE)

    fun recordStudySession(now: Long = System.currentTimeMillis()): Int {
        val today = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate()
        val todayStr = today.toString()
        val lastDateStr = prefs.getString(KEY_LAST_STUDY_DATE, null)
        val currentStreak = prefs.getInt(KEY_STREAK_DAYS, 0)
        val currentLongest = prefs.getInt(KEY_LONGEST_DAILY_STREAK, 0)

        if (lastDateStr == todayStr) {
            val validStreak = currentStreak.coerceAtLeast(1)
            val newLongest = maxOf(currentLongest, validStreak)
            if (currentStreak != validStreak || currentLongest != newLongest) {
                prefs.edit()
                    .putInt(KEY_STREAK_DAYS, validStreak)
                    .putInt(KEY_LONGEST_DAILY_STREAK, newLongest)
                    .apply()
                _streakDays.value = validStreak
            }
            return validStreak
        }

        val newStreak = if (lastDateStr != null) {
            try {
                val lastDate = LocalDate.parse(lastDateStr)
                val daysBetween = ChronoUnit.DAYS.between(lastDate, today)
                if (daysBetween == 1L) {
                    currentStreak + 1
                } else if (daysBetween <= 0L) {
                    currentStreak.coerceAtLeast(1)
                } else {
                    1
                }
            } catch (e: Exception) {
                1
            }
        } else {
            1
        }

        val newLongest = maxOf(currentLongest, newStreak)
        prefs.edit()
            .putInt(KEY_STREAK_DAYS, newStreak)
            .putInt(KEY_LONGEST_DAILY_STREAK, newLongest)
            .putString(KEY_LAST_STUDY_DATE, todayStr)
            .apply()
        _streakDays.value = newStreak
        return newStreak
    }

    fun getStreakDays(now: Long = System.currentTimeMillis()): Int {
        val lastDateStr = prefs.getString(KEY_LAST_STUDY_DATE, null) ?: return 0
        val currentStreak = prefs.getInt(KEY_STREAK_DAYS, 0)
        return try {
            val today = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate()
            val lastDate = LocalDate.parse(lastDateStr)
            val daysBetween = ChronoUnit.DAYS.between(lastDate, today)
            when {
                daysBetween <= 1L -> currentStreak
                else -> 0
            }
        } catch (e: Exception) {
            0
        }
    }

    fun getLongestDailyStreak(): Int = prefs.getInt(KEY_LONGEST_DAILY_STREAK, getStreakDays())

    fun getCurrentCorrectStreak(): Int = prefs.getInt(KEY_CURRENT_CORRECT_STREAK, 0)

    fun getLongestCorrectStreak(): Int = prefs.getInt(KEY_LONGEST_CORRECT_STREAK, 0)

    fun getTestsToday(now: Long = System.currentTimeMillis()): Int {
        val todayStr = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate().toString()
        val savedDate = prefs.getString(KEY_TESTS_TODAY_DATE, null)
        return if (savedDate == todayStr) prefs.getInt(KEY_TESTS_TODAY_COUNT, 0) else 0
    }

    fun getMostTestsInADay(): Int = prefs.getInt(KEY_MOST_TESTS_IN_A_DAY, 0)

    fun recordTestAnswer(isCorrect: Boolean, now: Long = System.currentTimeMillis()): TestAnswerRecordResult {
        val today = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate()
        val todayStr = today.toString()

        // 1. Daily Streak
        val prevLongestDailyStreak = prefs.getInt(KEY_LONGEST_DAILY_STREAK, 0)
        val dailyStreak = recordStudySession(now)
        val longestDailyStreak = prefs.getInt(KEY_LONGEST_DAILY_STREAK, dailyStreak)
        val isNewDailyStreakRecord = longestDailyStreak > prevLongestDailyStreak

        // 2. Correct Streak
        val prevCurrentCorrect = prefs.getInt(KEY_CURRENT_CORRECT_STREAK, 0)
        val prevLongestCorrect = prefs.getInt(KEY_LONGEST_CORRECT_STREAK, 0)
        val newCurrentCorrect = if (isCorrect) prevCurrentCorrect + 1 else 0
        val newLongestCorrect = maxOf(prevLongestCorrect, newCurrentCorrect)
        val isNewCorrectRecord = newLongestCorrect > prevLongestCorrect

        // 3. Tests in a day
        val savedDate = prefs.getString(KEY_TESTS_TODAY_DATE, null)
        val testsTodayCount = if (savedDate == todayStr) prefs.getInt(KEY_TESTS_TODAY_COUNT, 0) + 1 else 1
        val prevMostTests = prefs.getInt(KEY_MOST_TESTS_IN_A_DAY, 0)
        val newMostTests = maxOf(prevMostTests, testsTodayCount)
        val isNewDailyTestsRecord = newMostTests > prevMostTests

        // 4. Badge checks
        val currentUnlocked = prefs.getStringSet(KEY_UNLOCKED_BADGES, emptySet())?.toMutableSet() ?: mutableSetOf()
        val newlyUnlocked = mutableListOf<BadgeId>()
        val editor = prefs.edit()

        for (badge in BadgeId.entries) {
            if (!currentUnlocked.contains(badge.name)) {
                val qualifies = when (badge.category) {
                    BadgeCategory.DAILY_STREAK -> longestDailyStreak >= badge.threshold
                    BadgeCategory.CORRECT_STREAK -> newLongestCorrect >= badge.threshold
                    BadgeCategory.DAILY_TESTS -> newMostTests >= badge.threshold
                    BadgeCategory.HSK_LEVEL -> false // HSK badges are tracked via HskBadgeTracker based on Leitner card boxes
                }
                if (qualifies) {
                    currentUnlocked.add(badge.name)
                    newlyUnlocked.add(badge)
                    editor.putLong(KEY_BADGE_UNLOCKED_AT_PREFIX + badge.name, now)
                }
            }
        }

        editor
            .putInt(KEY_CURRENT_CORRECT_STREAK, newCurrentCorrect)
            .putInt(KEY_LONGEST_CORRECT_STREAK, newLongestCorrect)
            .putString(KEY_TESTS_TODAY_DATE, todayStr)
            .putInt(KEY_TESTS_TODAY_COUNT, testsTodayCount)
            .putInt(KEY_MOST_TESTS_IN_A_DAY, newMostTests)
            .putStringSet(KEY_UNLOCKED_BADGES, currentUnlocked)
            .apply()

        return TestAnswerRecordResult(
            newlyUnlockedBadges = newlyUnlocked,
            isNewDailyStreakRecord = isNewDailyStreakRecord,
            isNewCorrectStreakRecord = isNewCorrectRecord,
            isNewDailyTestsRecord = isNewDailyTestsRecord,
            currentDailyStreak = dailyStreak,
            longestDailyStreak = longestDailyStreak,
            currentCorrectStreak = newCurrentCorrect,
            longestCorrectStreak = newLongestCorrect,
            testsToday = testsTodayCount,
            mostTestsInADay = newMostTests
        )
    }

    fun isBadgeUnlocked(badgeId: BadgeId): Boolean {
        val unlocked = prefs.getStringSet(KEY_UNLOCKED_BADGES, emptySet()) ?: emptySet()
        return unlocked.contains(badgeId.name)
    }

    fun unlockBadge(badgeId: BadgeId, now: Long = System.currentTimeMillis()): Boolean {
        val currentUnlocked = prefs.getStringSet(KEY_UNLOCKED_BADGES, emptySet())?.toMutableSet() ?: mutableSetOf()
        if (!currentUnlocked.contains(badgeId.name)) {
            currentUnlocked.add(badgeId.name)
            prefs.edit()
                .putStringSet(KEY_UNLOCKED_BADGES, currentUnlocked)
                .putLong(KEY_BADGE_UNLOCKED_AT_PREFIX + badgeId.name, now)
                .apply()
            return true
        }
        return false
    }

    fun getBadgesSummary(
        now: Long = System.currentTimeMillis(),
        hskProgress: Map<Int, HskLevelProgressInfo>? = null
    ): BadgesSummary {
        val currentDailyStreak = getStreakDays(now)
        val longestDailyStreak = getLongestDailyStreak()
        val currentCorrectStreak = getCurrentCorrectStreak()
        val longestCorrectStreak = getLongestCorrectStreak()
        val testsToday = getTestsToday(now)
        val mostTestsInADay = getMostTestsInADay()

        val unlockedSet = prefs.getStringSet(KEY_UNLOCKED_BADGES, emptySet()) ?: emptySet()
        val badgeItems = BadgeId.entries.map { badge ->
            val isUnlocked = unlockedSet.contains(badge.name)
            val unlockedAt = if (isUnlocked) prefs.getLong(KEY_BADGE_UNLOCKED_AT_PREFIX + badge.name, 0L).takeIf { it > 0 } else null
            val currentProgress: Int
            val target: Int
            if (badge.category == BadgeCategory.HSK_LEVEL) {
                val hskInfo = hskProgress?.get(badge.threshold)
                val totalWords = hskInfo?.totalWords ?: HskBadgeTracker.DEFAULT_HSK_COUNTS[badge.threshold] ?: 100
                target = totalWords
                currentProgress = if (isUnlocked) totalWords else (hskInfo?.box3PlusWords ?: 0)
            } else {
                target = badge.threshold
                currentProgress = when (badge.category) {
                    BadgeCategory.DAILY_STREAK -> longestDailyStreak
                    BadgeCategory.CORRECT_STREAK -> longestCorrectStreak
                    BadgeCategory.DAILY_TESTS -> mostTestsInADay
                    BadgeCategory.HSK_LEVEL -> 0
                }
            }
            BadgeItem(
                badge = badge,
                isUnlocked = isUnlocked,
                unlockedAt = unlockedAt,
                currentProgress = currentProgress,
                target = target
            )
        }

        val hskMasteredLevelsCount = (1..6).count { lvl ->
            val b = BadgeId.fromHskLevel(lvl)
            (b != null && unlockedSet.contains(b.name)) || (hskProgress?.get(lvl)?.isMastered == true)
        }
        val hskBox3PlusWordsCount = hskProgress?.values?.sumOf { it.box3PlusWords } ?: 0

        return BadgesSummary(
            currentDailyStreak = currentDailyStreak,
            longestDailyStreak = longestDailyStreak,
            currentCorrectStreak = currentCorrectStreak,
            longestCorrectStreak = longestCorrectStreak,
            testsToday = testsToday,
            mostTestsInADay = mostTestsInADay,
            badges = badgeItems,
            hskMasteredLevelsCount = hskMasteredLevelsCount,
            hskBox3PlusWordsCount = hskBox3PlusWordsCount
        )
    }

    fun resetStreak() {
        prefs.edit()
            .remove(KEY_STREAK_DAYS)
            .remove(KEY_LAST_STUDY_DATE)
            .remove(KEY_LONGEST_DAILY_STREAK)
            .remove(KEY_CURRENT_CORRECT_STREAK)
            .remove(KEY_LONGEST_CORRECT_STREAK)
            .remove(KEY_TESTS_TODAY_COUNT)
            .remove(KEY_TESTS_TODAY_DATE)
            .remove(KEY_MOST_TESTS_IN_A_DAY)
            .apply()
        _streakDays.value = 0
    }

    fun resetBadges() {
        val editor = prefs.edit().remove(KEY_UNLOCKED_BADGES)
        for (badge in BadgeId.entries) {
            editor.remove(KEY_BADGE_UNLOCKED_AT_PREFIX + badge.name)
        }
        editor.apply()
    }

    private fun getSavedThemeMode(): AppThemeMode {
        val saved = prefs.getString(KEY_THEME_MODE, AppThemeMode.SYSTEM.name)
        return try {
            AppThemeMode.valueOf(saved ?: AppThemeMode.SYSTEM.name)
        } catch (e: Exception) {
            AppThemeMode.SYSTEM
        }
    }

    companion object {
        const val PREFS_NAME = "andzi_user_preferences"
        const val KEY_THEME_MODE = "app_theme_mode"
        const val KEY_NOTIFICATION_ENABLED = "notification_enabled"
        const val KEY_WRITING_QUIZ_ENABLED = "writing_quiz_enabled"
        const val KEY_AUDIO_STORIES_ENABLED = "audio_stories_enabled"
        const val KEY_STREAK_DAYS = "streak_days"
        const val KEY_LAST_STUDY_DATE = "last_study_date"
        const val KEY_LONGEST_DAILY_STREAK = "longest_daily_streak"
        const val KEY_CURRENT_CORRECT_STREAK = "current_correct_streak"
        const val KEY_LONGEST_CORRECT_STREAK = "longest_correct_streak"
        const val KEY_TESTS_TODAY_COUNT = "tests_today_count"
        const val KEY_TESTS_TODAY_DATE = "tests_today_date"
        const val KEY_MOST_TESTS_IN_A_DAY = "most_tests_in_a_day"
        const val KEY_UNLOCKED_BADGES = "unlocked_badges"
        const val KEY_BADGE_UNLOCKED_AT_PREFIX = "badge_unlocked_at_"
    }
}
