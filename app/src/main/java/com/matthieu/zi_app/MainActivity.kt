package com.matthieu.zi_app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.matthieu.zi_app.ui.navigation.MainAppScaffold
import com.matthieu.zi_app.ui.settings.AppThemeMode
import com.matthieu.zi_app.ui.theme.AndZiTheme
import com.matthieu.zi_app.work.ProgressSyncWorker

class MainActivity : ComponentActivity() {

    private val startTestState = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Clear notification if app was opened
        ProgressSyncWorker.cancelNotification(this)

        if (savedInstanceState == null && intent?.getBooleanExtra("start_test", false) == true) {
            startTestState.value = true
            intent?.removeExtra("start_test")
        }

        val app = application as AndZiApplication
        val userPreferences = app.appContainer.userPreferences

        setContent {
            val themeMode by userPreferences.themeMode.collectAsStateWithLifecycle()
            val isSystemDark = isSystemInDarkTheme()
            val isDarkTheme = when (themeMode) {
                AppThemeMode.SYSTEM -> isSystemDark
                AppThemeMode.LIGHT -> false
                AppThemeMode.DARK -> true
            }
            val startTest by startTestState

            AndZiTheme(darkTheme = isDarkTheme) {
                MainAppScaffold(
                    startTest = startTest,
                    onTestStarted = { startTestState.value = false }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        ProgressSyncWorker.cancelNotification(this)
        if (intent.getBooleanExtra("start_test", false)) {
            startTestState.value = true
            intent.removeExtra("start_test")
        }
    }
}
