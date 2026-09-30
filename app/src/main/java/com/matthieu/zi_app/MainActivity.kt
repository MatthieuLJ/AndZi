package com.matthieu.zi_app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.matthieu.zi_app.ui.navigation.MainAppScaffold
import com.matthieu.zi_app.ui.settings.AppThemeMode
import com.matthieu.zi_app.ui.theme.AndZiTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Clear any old notification
        ProgressService.clear_notification(applicationContext)

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

            AndZiTheme(darkTheme = isDarkTheme) {
                MainAppScaffold()
            }
        }
    }
}
