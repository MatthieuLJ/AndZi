package com.matthieu.zi_app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.matthieu.zi_app.ui.navigation.MainAppScaffold
import com.matthieu.zi_app.ui.navigation.Screen
import com.matthieu.zi_app.ui.settings.AppThemeMode
import com.matthieu.zi_app.ui.theme.AndZiTheme
import com.matthieu.zi_app.work.ProgressSyncWorker

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Clear notification if app was opened
        ProgressSyncWorker.cancelNotification(this)

        val app = application as AndZiApplication
        val userPreferences = app.appContainer.userPreferences

        val startTest = intent?.getBooleanExtra("start_test", false) ?: false

        setContent {
            val themeMode by userPreferences.themeMode.collectAsStateWithLifecycle()
            val isSystemDark = isSystemInDarkTheme()
            val isDarkTheme = when (themeMode) {
                AppThemeMode.SYSTEM -> isSystemDark
                AppThemeMode.LIGHT -> false
                AppThemeMode.DARK -> true
            }

            AndZiTheme(darkTheme = isDarkTheme) {
                MainAppScaffold(
                    startDestination = if (startTest) Screen.Study.route else Screen.Home.route
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        ProgressSyncWorker.cancelNotification(this)
    }
}
