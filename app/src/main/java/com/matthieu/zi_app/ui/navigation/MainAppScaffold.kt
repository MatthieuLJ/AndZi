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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.matthieu.zi_app.AndZiApplication
import com.matthieu.zi_app.ui.dictionary.DictionaryScreen
import com.matthieu.zi_app.ui.home.HomeScreen
import com.matthieu.zi_app.ui.home.HomeViewModel
import com.matthieu.zi_app.ui.podcast.catalog.PodcastCatalogScreen
import com.matthieu.zi_app.ui.podcast.catalog.PodcastCatalogViewModel
import com.matthieu.zi_app.ui.podcast.player.PodcastAudioPlayer
import com.matthieu.zi_app.ui.podcast.player.PodcastPlayerScreen
import com.matthieu.zi_app.ui.podcast.player.PodcastPlayerViewModel
import com.matthieu.zi_app.ui.settings.SettingsScreen
import com.matthieu.zi_app.ui.study.StudyScreen
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first

@Composable
fun MainAppScaffold(
    navController: NavHostController = rememberNavController(),
    startDestination: String = Screen.Home.route,
    startTest: Boolean = false,
    onTestStarted: () -> Unit = {}
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    LaunchedEffect(startTest) {
        if (startTest) {
            navController.currentBackStackEntryFlow.filterNotNull().first()
            if (navController.currentDestination?.route != Screen.Study.route) {
                navController.navigate(Screen.Study.route) {
                    launchSingleTop = true
                }
            }
            onTestStarted()
        }
    }

    val context = LocalContext.current
    val app = context.applicationContext as AndZiApplication
    val appContainer = app.appContainer

    val audioStoriesEnabled by appContainer.userPreferences.audioStoriesEnabled.collectAsStateWithLifecycle()
    val bottomNavItems = remember(audioStoriesEnabled) {
        if (audioStoriesEnabled) {
            listOf(Screen.Home, Screen.Dictionary, Screen.PodcastCatalog)
        } else {
            listOf(Screen.Home, Screen.Dictionary)
        }
    }

    val isBottomBarVisible = currentRoute in bottomNavItems.map { it.route }

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
                    bottomNavItems.forEach { screen ->
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
            startDestination = startDestination,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Home.route) {
                val homeViewModel: HomeViewModel = viewModel(
                    factory = HomeViewModel.provideFactory(
                        studyRepository = appContainer.studyRepository,
                        progressRepository = appContainer.progressRepository,
                        userPreferences = appContainer.userPreferences,
                        leitnerDao = appContainer.leitnerDao,
                        vocabularyRepository = appContainer.vocabularyRepository
                    )
                )
                HomeScreen(
                    viewModel = homeViewModel,
                    onStartStudy = {
                        navController.navigate(Screen.Study.route)
                    },
                    onOpenSettings = {
                        navController.navigate(Screen.Settings.route)
                    },
                    onOpenVocabulary = {
                        navController.navigate(Screen.Vocabulary.createRoute("words"))
                    },
                    onOpenWords = {
                        navController.navigate(Screen.Vocabulary.createRoute("words"))
                    },
                    onOpenCharacters = {
                        navController.navigate(Screen.Vocabulary.createRoute("characters"))
                    }
                )
            }

            composable(
                route = Screen.Vocabulary.route,
                arguments = listOf(
                    navArgument("tab") {
                        type = NavType.StringType
                        defaultValue = "words"
                    }
                )
            ) { backStackEntry ->
                val tabArg = backStackEntry.arguments?.getString("tab")
                val vocabViewModel: com.matthieu.zi_app.ui.vocabulary.VocabularyListViewModel = viewModel(
                    factory = com.matthieu.zi_app.ui.vocabulary.VocabularyListViewModel.provideFactory(
                        localVocabularyRepository = appContainer.vocabularyRepository,
                        dictionaryRepository = appContainer.dictionaryRepository,
                        studyRepository = appContainer.studyRepository
                    )
                )
                LaunchedEffect(tabArg) {
                    val targetTab = if (tabArg == "characters") {
                        com.matthieu.zi_app.ui.vocabulary.ActiveCardTab.CHARACTERS
                    } else {
                        com.matthieu.zi_app.ui.vocabulary.ActiveCardTab.WORDS
                    }
                    vocabViewModel.selectTab(targetTab)
                }
                com.matthieu.zi_app.ui.vocabulary.VocabularyListScreen(
                    viewModel = vocabViewModel,
                    onNavigateBack = {
                        if (!navController.popBackStack()) {
                            navController.navigate(Screen.Home.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    }
                )
            }

            composable(Screen.Dictionary.route) {
                val dictViewModel: com.matthieu.zi_app.ui.dictionary.DictionaryViewModel = viewModel(
                    factory = com.matthieu.zi_app.ui.dictionary.DictionaryViewModel.provideFactory(
                        dictionaryRepository = appContainer.dictionaryRepository,
                        localVocabularyRepository = appContainer.vocabularyRepository,
                        studyRepository = appContainer.studyRepository,
                        wordRecommendationEngine = appContainer.wordRecommendationEngine
                    )
                )
                DictionaryScreen(
                    viewModel = dictViewModel,
                    onOpenHskBrowser = { level ->
                        navController.navigate(Screen.HskBrowser.createRoute(level))
                    }
                )
            }

            composable(
                route = Screen.HskBrowser.route,
                arguments = listOf(
                    navArgument("level") {
                        type = NavType.IntType
                        defaultValue = 1
                    }
                )
            ) { backStackEntry ->
                val level = backStackEntry.arguments?.getInt("level") ?: 1
                val hskViewModel: com.matthieu.zi_app.ui.hsk.HskBrowserViewModel = viewModel(
                    factory = com.matthieu.zi_app.ui.hsk.HskBrowserViewModel.provideFactory(
                        dictionaryRepository = appContainer.dictionaryRepository,
                        localVocabularyRepository = appContainer.vocabularyRepository,
                        studyRepository = appContainer.studyRepository,
                        initialLevel = level
                    )
                )
                com.matthieu.zi_app.ui.hsk.HskBrowserScreen(
                    viewModel = hskViewModel,
                    onNavigateBack = {
                        if (!navController.popBackStack()) {
                            navController.navigate(Screen.Dictionary.route)
                        }
                    }
                )
            }

            composable(Screen.Study.route) {
                val studyViewModel: com.matthieu.zi_app.ui.study.StudyViewModel = viewModel(
                    factory = com.matthieu.zi_app.ui.study.StudyViewModel.provideFactory(
                        studyRepository = appContainer.studyRepository,
                        vocabularyRepository = appContainer.vocabularyRepository,
                        dictionaryRepository = appContainer.dictionaryRepository,
                        userPreferences = appContainer.userPreferences
                    )
                )
                StudyScreen(
                    viewModel = studyViewModel,
                    onNavigateBack = {
                        if (!navController.popBackStack()) {
                            navController.navigate(Screen.Home.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    },
                    onReturnHome = {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                inclusive = false
                            }
                            launchSingleTop = true
                        }
                    }
                )
            }

            composable(Screen.Settings.route) {
                val settingsViewModel: com.matthieu.zi_app.ui.settings.SettingsViewModel = viewModel(
                    factory = com.matthieu.zi_app.ui.settings.SettingsViewModel.provideFactory(
                        userPreferences = appContainer.userPreferences,
                        studyRepository = appContainer.studyRepository,
                        progressRepository = appContainer.progressRepository,
                        databaseBackupRepository = appContainer.databaseBackupRepository,
                        context = context
                    )
                )
                SettingsScreen(
                    viewModel = settingsViewModel,
                    onNavigateBack = {
                        if (!navController.popBackStack()) {
                            navController.navigate(Screen.Home.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    }
                )
            }

            composable(Screen.PodcastCatalog.route) {
                val catalogViewModel: PodcastCatalogViewModel = viewModel(
                    factory = PodcastCatalogViewModel.provideFactory(
                        podcastRepository = appContainer.podcastRepository,
                        studyRepository = appContainer.studyRepository
                    )
                )
                PodcastCatalogScreen(
                    viewModel = catalogViewModel,
                    onEpisodeClick = { episodeId ->
                        navController.navigate(Screen.PodcastPlayer.createRoute(episodeId))
                    }
                )
            }

            composable(
                route = Screen.PodcastPlayer.route,
                arguments = listOf(navArgument("episodeId") { type = NavType.IntType })
            ) { backStackEntry ->
                val epId = backStackEntry.arguments?.getInt("episodeId") ?: 1
                val coroutineScope = rememberCoroutineScope()
                val audioPlayer = remember(epId) { PodcastAudioPlayer(context, coroutineScope) }
                val playerViewModel: PodcastPlayerViewModel = viewModel(
                    key = "podcast_player_$epId",
                    factory = PodcastPlayerViewModel.provideFactory(
                        episodeId = epId,
                        podcastRepository = appContainer.podcastRepository,
                        localVocabularyRepository = appContainer.vocabularyRepository,
                        dictionaryRepository = appContainer.dictionaryRepository,
                        studyRepository = appContainer.studyRepository,
                        audioPlayer = audioPlayer
                    )
                )
                PodcastPlayerScreen(
                    viewModel = playerViewModel,
                    onBackClick = { navController.popBackStack() }
                )
            }
        }
    }
}
