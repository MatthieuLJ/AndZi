package com.matthieu.zi_app.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.matthieu.zi_app.data.preferences.UserPreferences
import com.matthieu.zi_app.data.repository.DatabaseBackupRepository
import com.matthieu.zi_app.data.repository.PodcastRepository
import com.matthieu.zi_app.data.repository.ProgressRepository
import com.matthieu.zi_app.data.repository.StudyRepository
import com.matthieu.zi_app.work.ProgressSyncWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val userPreferences: UserPreferences,
    private val studyRepository: StudyRepository,
    private val progressRepository: ProgressRepository,
    private val databaseBackupRepository: DatabaseBackupRepository,
    private val context: Context,
    private val podcastRepository: PodcastRepository? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        SettingsUiState(
            themeMode = userPreferences.themeMode.value,
            notificationsEnabled = userPreferences.notificationEnabled.value,
            writingQuizEnabled = userPreferences.writingQuizEnabled.value,
            audioStoriesEnabled = userPreferences.audioStoriesEnabled.value,
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
            userPreferences.notificationEnabled.collect { enabled ->
                _uiState.update { it.copy(notificationsEnabled = enabled) }
            }
        }
        viewModelScope.launch {
            userPreferences.writingQuizEnabled.collect { enabled ->
                _uiState.update { it.copy(writingQuizEnabled = enabled) }
            }
        }
        viewModelScope.launch {
            userPreferences.audioStoriesEnabled.collect { enabled ->
                _uiState.update { it.copy(audioStoriesEnabled = enabled) }
            }
        }
        if (podcastRepository != null) {
            viewModelScope.launch {
                podcastRepository.getAllChannels().collect { channels ->
                    _uiState.update { it.copy(podcastChannels = channels) }
                }
            }
        }
    }

    fun setThemeMode(mode: AppThemeMode) {
        userPreferences.setThemeMode(mode)
    }

    fun setNotificationEnabled(enabled: Boolean) {
        userPreferences.setNotificationEnabled(enabled)
        if (enabled) {
            ProgressSyncWorker.schedulePeriodicWork(context)
            ProgressSyncWorker.triggerImmediateCheck(context)
        } else {
            ProgressSyncWorker.cancelNotification(context)
        }
    }

    fun setWritingQuizEnabled(enabled: Boolean) {
        userPreferences.setWritingQuizEnabled(enabled)
    }

    fun setAudioStoriesEnabled(enabled: Boolean) {
        userPreferences.setAudioStoriesEnabled(enabled)
    }

    fun togglePodcastChannel(channelId: Int, isEnabled: Boolean) {
        val repo = podcastRepository ?: return
        viewModelScope.launch {
            val channel = repo.getChannelById(channelId) ?: return@launch
            repo.updateChannel(channel.copy(isEnabled = isEnabled))
            if (isEnabled && channelId == 3) {
                val count = repo.getEpisodeCountForChannel(3)
                if (count == 0) {
                    repo.seedMaoMiEpisodesFromAssets()
                    repo.ensureEpisodesGraded()
                }
            }
        }
    }

    fun showFeedbackMessage(message: String) {
        _uiState.update { it.copy(actionFeedbackMessage = message) }
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

    fun showExportStudyDialog(show: Boolean) {
        _uiState.update { it.copy(showExportStudyDialog = show) }
    }

    fun setExportIncludeScores(include: Boolean) {
        _uiState.update { it.copy(exportIncludeScores = include) }
    }

    fun showExportProgressDialog(show: Boolean) {
        _uiState.update { it.copy(showExportProgressDialog = show) }
    }

    fun cancelImportInspection() {
        val inspection = _uiState.value.pendingImportInspection
        if (inspection != null) {
            databaseBackupRepository.cleanupTempFile(inspection.tempFilePath)
        }
        _uiState.update { it.copy(pendingImportInspection = null) }
    }

    fun exportStudyDatabaseToUri(uri: Uri, context: Context) {
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessingBackup = true) }
            try {
                val outputStream = context.contentResolver.openOutputStream(uri)
                    ?: throw IllegalStateException("Cannot open destination file.")
                outputStream.use { stream ->
                    databaseBackupRepository.exportStudyDatabase(stream, _uiState.value.exportIncludeScores)
                }
                _uiState.update {
                    it.copy(
                        isProcessingBackup = false,
                        showExportStudyDialog = false,
                        actionFeedbackMessage = "Study database exported successfully."
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isProcessingBackup = false,
                        actionFeedbackMessage = "Export failed: ${e.localizedMessage ?: "Unknown error"}"
                    )
                }
            }
        }
    }

    fun shareStudyDatabase(context: Context) {
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessingBackup = true) }
            try {
                val shareFile = databaseBackupRepository.createStudyDatabaseShareFile(_uiState.value.exportIncludeScores)
                val shareUri = databaseBackupRepository.getShareUri(shareFile)

                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/octet-stream"
                    putExtra(Intent.EXTRA_STREAM, shareUri)
                    putExtra(Intent.EXTRA_SUBJECT, "AndZi Study Database Backup")
                    putExtra(Intent.EXTRA_TEXT, "AndZi Study Database Backup file (.azdb)")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                val chooser = Intent.createChooser(intent, "Share Study Database").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(chooser)

                _uiState.update {
                    it.copy(
                        isProcessingBackup = false,
                        showExportStudyDialog = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isProcessingBackup = false,
                        actionFeedbackMessage = "Sharing failed: ${e.localizedMessage ?: "Unknown error"}"
                    )
                }
            }
        }
    }

    fun exportProgressDatabaseToUri(uri: Uri, context: Context) {
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessingBackup = true) }
            try {
                val outputStream = context.contentResolver.openOutputStream(uri)
                    ?: throw IllegalStateException("Cannot open destination file.")
                outputStream.use { stream ->
                    databaseBackupRepository.exportProgressDatabase(stream)
                }
                _uiState.update {
                    it.copy(
                        isProcessingBackup = false,
                        showExportProgressDialog = false,
                        actionFeedbackMessage = "Progress history exported successfully."
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isProcessingBackup = false,
                        actionFeedbackMessage = "Export failed: ${e.localizedMessage ?: "Unknown error"}"
                    )
                }
            }
        }
    }

    fun shareProgressDatabase(context: Context) {
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessingBackup = true) }
            try {
                val shareFile = databaseBackupRepository.createProgressDatabaseShareFile()
                val shareUri = databaseBackupRepository.getShareUri(shareFile)

                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/octet-stream"
                    putExtra(Intent.EXTRA_STREAM, shareUri)
                    putExtra(Intent.EXTRA_SUBJECT, "AndZi Progress History Backup")
                    putExtra(Intent.EXTRA_TEXT, "AndZi Historical Analytics Database Backup (.azdb)")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                val chooser = Intent.createChooser(intent, "Share Progress History").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(chooser)

                _uiState.update {
                    it.copy(
                        isProcessingBackup = false,
                        showExportProgressDialog = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isProcessingBackup = false,
                        actionFeedbackMessage = "Sharing failed: ${e.localizedMessage ?: "Unknown error"}"
                    )
                }
            }
        }
    }

    fun onStudyDatabaseSelectedForImport(uri: Uri, context: Context) {
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessingBackup = true) }
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                    ?: throw IllegalStateException("Cannot open selected file.")

                val inspection = inputStream.use { stream ->
                    databaseBackupRepository.inspectIncomingStudyDatabase(stream)
                }

                _uiState.update {
                    it.copy(
                        isProcessingBackup = false,
                        pendingImportInspection = inspection
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isProcessingBackup = false,
                        actionFeedbackMessage = "Import inspection failed: ${e.localizedMessage ?: "Invalid file"}"
                    )
                }
            }
        }
    }

    fun confirmMergeStudyDatabase(overwriteScoresConflict: Boolean) {
        val inspection = _uiState.value.pendingImportInspection ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessingBackup = true, pendingImportInspection = null) }
            try {
                val result = databaseBackupRepository.mergeStudyDatabase(inspection, overwriteScoresConflict)
                val msg = buildString {
                    append("Import successful: ")
                    append("${result.wordsAdded} new words, ")
                    append("${result.translationsAdded} translations added")
                    if (result.scoresImported > 0 || result.scoresUpdated > 0) {
                        append(", ${result.scoresImported} scores imported")
                        if (result.scoresUpdated > 0) {
                            append(" and ${result.scoresUpdated} scores updated")
                        }
                    }
                    append(".")
                }
                _uiState.update {
                    it.copy(
                        isProcessingBackup = false,
                        actionFeedbackMessage = msg
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isProcessingBackup = false,
                        actionFeedbackMessage = "Merge failed: ${e.localizedMessage ?: "Unknown error"}"
                    )
                }
            }
        }
    }

    fun importProgressDatabaseFromUri(uri: Uri, context: Context) {
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessingBackup = true) }
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                    ?: throw IllegalStateException("Cannot open selected file.")

                val count = inputStream.use { stream ->
                    databaseBackupRepository.importProgressDatabase(stream)
                }
                _uiState.update {
                    it.copy(
                        isProcessingBackup = false,
                        actionFeedbackMessage = "Successfully imported $count progress snapshot records."
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isProcessingBackup = false,
                        actionFeedbackMessage = "Progress import failed: ${e.localizedMessage ?: "Invalid file"}"
                    )
                }
            }
        }
    }

    fun resetScores() {
        viewModelScope.launch {
            try {
                studyRepository.resetAllScores()
                userPreferences.resetStreak()
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
            databaseBackupRepository: DatabaseBackupRepository,
            context: Context,
            podcastRepository: PodcastRepository? = null
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return SettingsViewModel(
                    userPreferences,
                    studyRepository,
                    progressRepository,
                    databaseBackupRepository,
                    context.applicationContext,
                    podcastRepository
                ) as T
            }
        }
    }
}
