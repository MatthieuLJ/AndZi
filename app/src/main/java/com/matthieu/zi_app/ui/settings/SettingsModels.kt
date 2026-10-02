package com.matthieu.zi_app.ui.settings

import com.matthieu.zi_app.data.repository.StudyBackupInspection

enum class AppThemeMode(val title: String) {
    SYSTEM("System Default"),
    LIGHT("Light Mode"),
    DARK("Dark Mode")
}

data class SettingsUiState(
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val coloredPinyin: Boolean = true,
    val notificationsEnabled: Boolean = true,
    val appVersion: String = "1.0",
    val showResetConfirmation: Boolean = false,
    val showClearConfirmation: Boolean = false,
    val showTrimProgressConfirmation: Boolean = false,
    val showExportStudyDialog: Boolean = false,
    val exportIncludeScores: Boolean = true,
    val showExportProgressDialog: Boolean = false,
    val pendingImportInspection: StudyBackupInspection? = null,
    val isProcessingBackup: Boolean = false,
    val actionFeedbackMessage: String? = null
)
