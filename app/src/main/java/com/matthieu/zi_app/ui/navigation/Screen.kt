package com.matthieu.zi_app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoGraph
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.AutoGraph
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(
    val route: String,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    data object Home : Screen(
        route = "home",
        title = "Study",
        selectedIcon = Icons.Filled.School,
        unselectedIcon = Icons.Outlined.School
    )

    data object Dictionary : Screen(
        route = "dictionary",
        title = "Dictionary",
        selectedIcon = Icons.Filled.Book,
        unselectedIcon = Icons.Outlined.Book
    )

    data object Analytics : Screen(
        route = "analytics",
        title = "Analytics",
        selectedIcon = Icons.Filled.AutoGraph,
        unselectedIcon = Icons.Outlined.AutoGraph
    )

    data object Study : Screen(
        route = "study",
        title = "Study Session",
        selectedIcon = Icons.Filled.School,
        unselectedIcon = Icons.Outlined.School
    )

    data object Settings : Screen(
        route = "settings",
        title = "Settings",
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings
    )

    data object Vocabulary : Screen(
        route = "vocabulary?tab={tab}",
        title = "Active Cards",
        selectedIcon = Icons.Filled.School,
        unselectedIcon = Icons.Outlined.School
    ) {
        fun createRoute(tab: String = "words") = "vocabulary?tab=$tab"
    }

    data object HskBrowser : Screen(
        route = "hsk_browser?level={level}",
        title = "HSK Vocabulary",
        selectedIcon = Icons.Filled.School,
        unselectedIcon = Icons.Outlined.School
    ) {
        fun createRoute(level: Int = 1) = "hsk_browser?level=$level"
    }

    data object PodcastCatalog : Screen(
        route = "podcast_catalog",
        title = "Stories",
        selectedIcon = Icons.Filled.Headphones,
        unselectedIcon = Icons.Outlined.Headphones
    )

    data object PodcastPlayer : Screen(
        route = "podcast_player/{episodeId}",
        title = "Story Player",
        selectedIcon = Icons.Filled.Headphones,
        unselectedIcon = Icons.Outlined.Headphones
    ) {
        fun createRoute(episodeId: Int) = "podcast_player/$episodeId"
    }

    companion object {
        val bottomNavItems: List<Screen>
            get() = listOf(Home, Dictionary)
    }
}
