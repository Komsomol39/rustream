package com.komsomol.rustream.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.komsomol.rustream.ui.screens.downloads.DownloadsScreen
import com.komsomol.rustream.ui.screens.search.SearchScreen
import com.komsomol.rustream.ui.screens.settings.SettingsScreen

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Search    : Screen("search",    "Поиск",     Icons.Default.Search)
    object Downloads : Screen("downloads", "Загрузки",  Icons.Default.Download)
    object Video     : Screen("video",     "Видео",     Icons.Default.PlayCircle)
    object Music     : Screen("music",     "Музыка",    Icons.Default.MusicNote)
    object Settings  : Screen("settings",  "Настройки", Icons.Default.Settings)
}

val bottomNavItems = listOf(
    Screen.Search, Screen.Downloads, Screen.Video, Screen.Music, Screen.Settings
)

@Composable
fun AppNavGraph() {
    val navController = rememberNavController()

    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination
                bottomNavItems.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = screen.label) },
                        label = { Text(screen.label) },
                        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Search.route,
            modifier = androidx.compose.ui.Modifier.padding(innerPadding = innerPadding)
        ) {
            composable(Screen.Search.route)    { SearchScreen() }
            composable(Screen.Downloads.route) { DownloadsScreen() }
            composable(Screen.Video.route)     { PlaceholderScreen("Видео") }
            composable(Screen.Music.route)     { PlaceholderScreen("Музыка") }
            composable(Screen.Settings.route)  { SettingsScreen() }
        }
    }
}

@Composable
private fun PlaceholderScreen(name: String) {
    androidx.compose.foundation.layout.Box(
        modifier = androidx.compose.ui.Modifier.fillMaxSize(),
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        Text(name, style = androidx.compose.material3.MaterialTheme.typography.headlineMedium)
    }
}
