package com.matthieu.zi_app.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.matthieu.zi_app.AndZiApplication
import com.matthieu.zi_app.ui.analytics.AnalyticsScreen
import com.matthieu.zi_app.ui.dictionary.DictionaryScreen
import com.matthieu.zi_app.ui.home.HomeScreen
import com.matthieu.zi_app.ui.home.HomeViewModel
import com.matthieu.zi_app.ui.settings.SettingsScreen
import com.matthieu.zi_app.ui.study.StudyScreen

@Composable
fun MainAppScaffold(
    navController: NavHostController = rememberNavController()
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val isBottomBarVisible = currentRoute in Screen.bottomNavItems.map { it.route }

    val context = LocalContext.current
    val app = context.applicationContext as AndZiApplication
    val appContainer = app.appContainer

    Scaffold(
        bottomBar = {
            AnimatedVisibility(
                visible = isBottomBarVisible,
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it })
            ) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ) {
                    Screen.bottomNavItems.forEach { screen ->
                        val isSelected = currentRoute == screen.route
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = {
                                if (currentRoute != screen.route) {
                                    navController.navigate(screen.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = if (isSelected) screen.selectedIcon else screen.unselectedIcon,
                                    contentDescription = screen.title
                                )
                            },
                            label = {
                                Text(
                                    text = screen.title,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Home.route) {
                val homeViewModel: HomeViewModel = viewModel(
                    factory = HomeViewModel.provideFactory(
                        studyRepository = appContainer.studyRepository,
                        progressRepository = appContainer.progressRepository
                    )
                )
                HomeScreen(
                    viewModel = homeViewModel,
                    onStartStudy = {
                        navController.navigate(Screen.Study.route)
                    },
                    onOpenSettings = {
                        navController.navigate(Screen.Settings.route)
                    }
                )
            }

            composable(Screen.Dictionary.route) {
                val dictViewModel: com.matthieu.zi_app.ui.dictionary.DictionaryViewModel = viewModel(
                    factory = com.matthieu.zi_app.ui.dictionary.DictionaryViewModel.provideFactory(
                        dictionaryRepository = appContainer.dictionaryRepository,
                        localVocabularyRepository = appContainer.vocabularyRepository,
                        studyRepository = appContainer.studyRepository
                    )
                )
                DictionaryScreen(viewModel = dictViewModel)
            }

            composable(Screen.Analytics.route) {
                val analyticsViewModel: com.matthieu.zi_app.ui.analytics.AnalyticsViewModel = viewModel(
                    factory = com.matthieu.zi_app.ui.analytics.AnalyticsViewModel.provideFactory(
                        progressRepository = appContainer.progressRepository,
                        studyRepository = appContainer.studyRepository,
                        leitnerDao = appContainer.leitnerDao
                    )
                )
                AnalyticsScreen(viewModel = analyticsViewModel)
            }

            composable(Screen.Study.route) {
                val studyViewModel: com.matthieu.zi_app.ui.study.StudyViewModel = viewModel(
                    factory = com.matthieu.zi_app.ui.study.StudyViewModel.provideFactory(
                        studyRepository = appContainer.studyRepository,
                        vocabularyRepository = appContainer.vocabularyRepository,
                        dictionaryRepository = appContainer.dictionaryRepository
                    )
                )
                StudyScreen(
                    viewModel = studyViewModel,
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }

            composable(Screen.Settings.route) {
                val settingsViewModel: com.matthieu.zi_app.ui.settings.SettingsViewModel = viewModel(
                    factory = com.matthieu.zi_app.ui.settings.SettingsViewModel.provideFactory(
                        userPreferences = appContainer.userPreferences,
                        studyRepository = appContainer.studyRepository,
                        progressRepository = appContainer.progressRepository,
                        context = context
                    )
                )
                SettingsScreen(
                    viewModel = settingsViewModel,
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }
        }
    }
}
