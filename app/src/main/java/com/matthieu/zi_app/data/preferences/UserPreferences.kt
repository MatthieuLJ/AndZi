package com.matthieu.zi_app.data.preferences

import android.content.Context
import android.content.SharedPreferences
import com.matthieu.zi_app.ui.settings.AppThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class UserPreferences(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(getSavedThemeMode())
    val themeMode: StateFlow<AppThemeMode> = _themeMode.asStateFlow()

    private val _coloredPinyin = MutableStateFlow(prefs.getBoolean(KEY_COLOR_PINYIN, true))
    val coloredPinyin: StateFlow<Boolean> = _coloredPinyin.asStateFlow()

    private val _notificationEnabled = MutableStateFlow(prefs.getBoolean(KEY_NOTIFICATION_ENABLED, true))
    val notificationEnabled: StateFlow<Boolean> = _notificationEnabled.asStateFlow()

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
    }
}
