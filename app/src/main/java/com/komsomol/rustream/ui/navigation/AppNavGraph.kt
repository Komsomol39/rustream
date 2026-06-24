package com.komsomol.rustream.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.komsomol.rustream.ui.screens.downloads.DownloadsScreen
import com.komsomol.rustream.ui.screens.music.MusicScreen
import com.komsomol.rustream.ui.screens.search.SearchScreen
import com.komsomol.rustream.ui.screens.settings.SettingsScreen
import com.komsomol.rustream.ui.screens.video.VideoScreen

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Search : Screen("search", "Поиск", Icons.Default.Search)
    object Downloads : Screen("downloads", "Загрузки", Icons.Default.Download)
    object Video : Screen("video", "Видео", Icons.Default.VideoLibrary)
    object Music : Screen("music", "Музыка", Icons.Default.MusicNote)
    object Settings : Screen("settings", "Настройки", Icons.Default.Settings)
}

val bottomNavItems = listOf(
    Screen.Search,
    Screen.Downloads,
    Screen.Video,
    Screen.Music,
    Screen.Settings
)

@Composable
fun AppNavGraph() {
    val navController = rememberNavController()
    val backstackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backstackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar {
                bottomNavItems.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = screen.label) },
                        label = { Text(screen.label) },
                        selected = currentRoute == screen.route,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
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
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Search.route) { SearchScreen() }
            composable(Screen.Downloads.route) { DownloadsScreen() }
            composable(Screen.Video.route) { VideoScreen() }
            composable(Screen.Music.route) { MusicScreen() }
            composable(Screen.Settings.route) { SettingsScreen() }
        }
    }
}
