package com.matthieu.zi_app.data.preferences

import android.content.Context
import android.content.SharedPreferences
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

    private val _coloredPinyin = MutableStateFlow(prefs.getBoolean(KEY_COLOR_PINYIN, true))
    val coloredPinyin: StateFlow<Boolean> = _coloredPinyin.asStateFlow()

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

    fun setColoredPinyin(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_COLOR_PINYIN, enabled).apply()
        _coloredPinyin.value = enabled
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

        if (lastDateStr == todayStr) {
            val validStreak = currentStreak.coerceAtLeast(1)
            if (currentStreak != validStreak) {
                prefs.edit().putInt(KEY_STREAK_DAYS, validStreak).apply()
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

        prefs.edit()
            .putInt(KEY_STREAK_DAYS, newStreak)
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

    fun resetStreak() {
        prefs.edit()
            .remove(KEY_STREAK_DAYS)
            .remove(KEY_LAST_STUDY_DATE)
            .apply()
        _streakDays.value = 0
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
        const val KEY_COLOR_PINYIN = "color_pinyin"
        const val KEY_NOTIFICATION_ENABLED = "notification_enabled"
        const val KEY_WRITING_QUIZ_ENABLED = "writing_quiz_enabled"
        const val KEY_AUDIO_STORIES_ENABLED = "audio_stories_enabled"
        const val KEY_STREAK_DAYS = "streak_days"
        const val KEY_LAST_STUDY_DATE = "last_study_date"
    }
}
