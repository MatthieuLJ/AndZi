package com.matthieu.zi_app.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.matthieu.zi_app.data.preferences.UserPreferences
import com.matthieu.zi_app.data.repository.ProgressRepository
import com.matthieu.zi_app.data.repository.StudyRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val userPreferences: UserPreferences,
    private val studyRepository: StudyRepository,
    private val progressRepository: ProgressRepository,
    context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        SettingsUiState(
            themeMode = userPreferences.themeMode.value,
            coloredPinyin = userPreferences.coloredPinyin.value,
            notificationsEnabled = userPreferences.notificationEnabled.value,
            appVersion = getAppVersion(context)
        )
    )
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            userPreferences.themeMode.collect { mode ->
                _uiState.update { it.copy(themeMode = mode) }
            }
        }
        viewModelScope.launch {
            userPreferences.coloredPinyin.collect { colored ->
                _uiState.update { it.copy(coloredPinyin = colored) }
            }
        }
        viewModelScope.launch {
            userPreferences.notificationEnabled.collect { enabled ->
                _uiState.update { it.copy(notificationsEnabled = enabled) }
            }
        }
    }

    fun setThemeMode(mode: AppThemeMode) {
        userPreferences.setThemeMode(mode)
    }

    fun setColoredPinyin(enabled: Boolean) {
        userPreferences.setColoredPinyin(enabled)
    }

    fun setNotificationEnabled(enabled: Boolean) {
        userPreferences.setNotificationEnabled(enabled)
    }

    fun showResetConfirmation(show: Boolean) {
        _uiState.update { it.copy(showResetConfirmation = show) }
    }

    fun showClearConfirmation(show: Boolean) {
        _uiState.update { it.copy(showClearConfirmation = show) }
    }

    fun showTrimProgressConfirmation(show: Boolean) {
        _uiState.update { it.copy(showTrimProgressConfirmation = show) }
    }

    fun resetScores() {
        viewModelScope.launch {
            try {
                studyRepository.resetAllScores()
                _uiState.update {
                    it.copy(
                        showResetConfirmation = false,
                        actionFeedbackMessage = "SRS study scores reset to Box 0 successfully."
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        showResetConfirmation = false,
                        actionFeedbackMessage = "Failed to reset scores: ${e.localizedMessage}"
                    )
                }
            }
        }
    }

    fun clearHistoricalProgress() {
        viewModelScope.launch {
            try {
                progressRepository.clearAll()
                _uiState.update {
                    it.copy(
                        showTrimProgressConfirmation = false,
                        actionFeedbackMessage = "Historical progress statistics cleared."
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        showTrimProgressConfirmation = false,
                        actionFeedbackMessage = "Failed to clear progress: ${e.localizedMessage}"
                    )
                }
            }
        }
    }

    fun clearFeedbackMessage() {
        _uiState.update { it.copy(actionFeedbackMessage = null) }
    }

    private fun getAppVersion(context: Context): String {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName ?: "1.0"
        } catch (e: Exception) {
            "1.0"
        }
    }

    companion object {
        fun provideFactory(
            userPreferences: UserPreferences,
            studyRepository: StudyRepository,
            progressRepository: ProgressRepository,
            context: Context
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return SettingsViewModel(
                    userPreferences,
                    studyRepository,
                    progressRepository,
                    context.applicationContext
                ) as T
            }
        }
    }
}
