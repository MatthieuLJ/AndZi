package com.matthieu.zi_app.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.matthieu.zi_app.Vocabulary
import com.matthieu.zi_app.data.local.AndZiDatabase
import com.matthieu.zi_app.data.local.ProgressDatabase
import com.matthieu.zi_app.data.preferences.UserPreferences
import com.matthieu.zi_app.data.repository.DatabaseBackupRepository
import com.matthieu.zi_app.data.repository.ProgressRepository
import com.matthieu.zi_app.data.repository.StudyRepository
import com.matthieu.zi_app.data.repository.VocabularyRepository
import com.matthieu.zi_app.ui.settings.AppThemeMode
import com.matthieu.zi_app.ui.settings.SettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SettingsViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var andZiDb: AndZiDatabase
    private lateinit var progressDb: ProgressDatabase
    private lateinit var vocRepo: VocabularyRepository
    private lateinit var studyRepo: StudyRepository
    private lateinit var progressRepo: ProgressRepository
    private lateinit var backupRepo: DatabaseBackupRepository
    private lateinit var userPrefs: UserPreferences
    private lateinit var context: Context

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext<Context>()
        andZiDb = AndZiDatabase.createInMemory(context)
        progressDb = ProgressDatabase.createInMemory(context)

        vocRepo = VocabularyRepository(andZiDb.vocabularyDao())
        studyRepo = StudyRepository(andZiDb.leitnerDao())
        progressRepo = ProgressRepository(progressDb.progressDao(), andZiDb.leitnerDao())
        backupRepo = DatabaseBackupRepository(context, andZiDb, progressDb)
        userPrefs = UserPreferences(context)
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
        andZiDb.close()
        progressDb.close()
    }

    @Test
    fun testSettingsThemeAndResetFlow() = runBlocking {
        val viewModel = SettingsViewModel(userPrefs, studyRepo, progressRepo, backupRepo, context)

        // Test theme toggling
        viewModel.setThemeMode(AppThemeMode.DARK)
        val darkState = withTimeout(3000) {
            viewModel.uiState.filter { it.themeMode == AppThemeMode.DARK }.first()
        }
        assertEquals(AppThemeMode.DARK, darkState.themeMode)

        viewModel.setThemeMode(AppThemeMode.LIGHT)
        val lightState = withTimeout(3000) {
            viewModel.uiState.filter { it.themeMode == AppThemeMode.LIGHT }.first()
        }
        assertEquals(AppThemeMode.LIGHT, lightState.themeMode)

        // Test reset study scores
        val voc = Vocabulary("书", "shu1", arrayListOf("book"))
        vocRepo.addVocabulary(voc)
        studyRepo.recordReviewResult("书", isCorrect = true)

        viewModel.resetScores()
        val resetState = withTimeout(3000) {
            viewModel.uiState.filter { it.actionFeedbackMessage != null }.first()
        }
        assertTrue(resetState.actionFeedbackMessage!!.contains("reset"))

        val allRecords = andZiDb.leitnerDao().getAllRecords()
        assertTrue(allRecords.isEmpty())
    }

    @Test
    fun testBackupDialogToggles() = runBlocking {
        val viewModel = SettingsViewModel(userPrefs, studyRepo, progressRepo, backupRepo, context)

        viewModel.showExportStudyDialog(true)
        assertTrue(viewModel.uiState.value.showExportStudyDialog)

        viewModel.setExportIncludeScores(false)
        assertFalse(viewModel.uiState.value.exportIncludeScores)

        viewModel.showExportStudyDialog(false)
        assertFalse(viewModel.uiState.value.showExportStudyDialog)

        viewModel.showExportProgressDialog(true)
        assertTrue(viewModel.uiState.value.showExportProgressDialog)

        viewModel.showExportProgressDialog(false)
        assertFalse(viewModel.uiState.value.showExportProgressDialog)
    }

    @Test
    fun testNotificationToggling() = runBlocking {
        val viewModel = SettingsViewModel(userPrefs, studyRepo, progressRepo, backupRepo, context)

        viewModel.setNotificationEnabled(false)
        val disabledState = withTimeout(3000) {
            viewModel.uiState.filter { !it.notificationsEnabled }.first()
        }
        assertFalse(disabledState.notificationsEnabled)
        assertFalse(userPrefs.notificationEnabled.value)

        viewModel.setNotificationEnabled(true)
        val enabledState = withTimeout(3000) {
            viewModel.uiState.filter { it.notificationsEnabled }.first()
        }
        assertTrue(enabledState.notificationsEnabled)
        assertTrue(userPrefs.notificationEnabled.value)
    }

    @Test
    fun testShowFeedbackMessage() = runBlocking {
        val viewModel = SettingsViewModel(userPrefs, studyRepo, progressRepo, backupRepo, context)

        viewModel.showFeedbackMessage("Test notification feedback")
        assertEquals("Test notification feedback", viewModel.uiState.value.actionFeedbackMessage)

        viewModel.clearFeedbackMessage()
        assertNull(viewModel.uiState.value.actionFeedbackMessage)
    }

    @Test
    fun testCancelImportInspection() = runBlocking {
        val viewModel = SettingsViewModel(userPrefs, studyRepo, progressRepo, backupRepo, context)

        viewModel.cancelImportInspection()
        assertNull(viewModel.uiState.value.pendingImportInspection)
    }
}
