package com.komsomol.rustream.ui.navigation

import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import android.content.Intent
import androidx.compose.ui.platform.LocalContext
import com.komsomol.rustream.player.PlayerActivity
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.komsomol.rustream.ui.screens.downloads.DownloadDetailScreen
import com.komsomol.rustream.ui.screens.downloads.DownloadsScreen
import com.komsomol.rustream.ui.screens.search.SearchScreen
import com.komsomol.rustream.ui.screens.settings.SettingsScreen
import com.komsomol.rustream.ui.screens.music.MusicScreen
import com.komsomol.rustream.ui.screens.video.VideoScreen
import com.komsomol.rustream.ui.screens.grab.GrabScreen
import com.komsomol.rustream.ui.screens.grab.PasteUrlScreen
import com.komsomol.rustream.ui.screens.music.ArtistDetailScreen
import java.net.URLEncoder
import java.net.URLDecoder

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

/**
 * Открыть только что скачанный файл: переключаемся на нужную вкладку и сразу
 * запускаем воспроизведение. Общая для экрана поиска и для "Скачать по ссылке" —
 * раньше это было только в первом, и на втором нажатие ничего не делало.
 */
private fun openReadyFile(
    navController: NavHostController,
    ctx: android.content.Context,
    path: String,
    isVideo: Boolean
) {
    if (isVideo) {
        navController.navigate(Screen.Video.route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
        ctx.startActivity(
            Intent(ctx, PlayerActivity::class.java)
                .putExtra(PlayerActivity.EXTRA_PATH, path)
        )
    } else {
        navController.navigate("music?open=" + URLEncoder.encode(path, "UTF-8")) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
        }
    }
}

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
                        icon  = { Icon(screen.icon, screen.label) },
                        label = { Text(screen.label) },
                        // Маршрут музыки теперь "music?open={open}" — сравниваем без
                        // строки запроса, иначе вкладка перестанет подсвечиваться
                        selected = currentDestination?.hierarchy?.any {
                            it.route?.substringBefore("?") == screen.route
                        } == true,
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
            navController    = navController,
            startDestination = Screen.Search.route,
            modifier         = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Search.route)    { SearchScreen(onOpenGrab = { navController.navigate("grab") }) }
            composable(Screen.Downloads.route) {
                DownloadsScreen(onOpen = { id ->
                    navController.navigate("download_detail/" + id)
                })
            }
            composable(
                "download_detail/{id}",
                arguments = listOf(navArgument("id") { type = NavType.StringType })
            ) {
                DownloadDetailScreen(onBack = { navController.popBackStack() })
            }
            composable("grab") {
                val ctx = LocalContext.current
                GrabScreen(
                    onBack = { navController.popBackStack() },
                    onOpenPaste = { navController.navigate("paste_url") },
                    onOpenReady = { path, isVideo ->
                        openReadyFile(navController, ctx, path, isVideo)
                    }
                )
            }
            composable("paste_url") {
                val ctx = LocalContext.current
                PasteUrlScreen(
                    onBack = { navController.popBackStack() },
                    onOpenReady = { path, isVideo ->
                        openReadyFile(navController, ctx, path, isVideo)
                    }
                )
            }
            composable(Screen.Video.route)     { VideoScreen() }
            composable(
                Screen.Music.route + "?open={open}",
                arguments = listOf(navArgument("open") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                })
            ) { entry ->
                val open = entry.arguments?.getString("open")
                    ?.let { URLDecoder.decode(it, "UTF-8") }
                MusicScreen(
                    onOpenArtist = { name ->
                        navController.navigate("artist/" + URLEncoder.encode(name, "UTF-8"))
                    },
                    openPath = open
                )
            }
            composable(
                "artist/{name}",
                arguments = listOf(navArgument("name") { type = NavType.StringType })
            ) { entry ->
                val name = URLDecoder.decode(entry.arguments?.getString("name") ?: "", "UTF-8")
                ArtistDetailScreen(artistName = name, onBack = { navController.popBackStack() })
            }
            composable(Screen.Settings.route)  { SettingsScreen() }
        }
    }
}
