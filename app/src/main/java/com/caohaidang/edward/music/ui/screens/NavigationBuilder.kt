package com.caohaidang.edward.music.ui.screens

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.datastore.preferences.core.edit
import com.caohaidang.edward.music.constants.OnboardingDoneKey
import com.caohaidang.edward.music.utils.dataStore
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.caohaidang.edward.music.LocalPlayerAwareWindowInsets
import com.caohaidang.edward.music.ui.screens.artist.ArtistItemsScreen
import com.caohaidang.edward.music.ui.screens.artist.ArtistScreen
import com.caohaidang.edward.music.ui.screens.artist.ArtistSongsScreen
import com.caohaidang.edward.music.ui.screens.library.LibrarySection
import com.caohaidang.edward.music.ui.screens.library.LibrarySectionScreen
import com.caohaidang.edward.music.ui.screens.library.UnifiedLibraryScreen
import com.caohaidang.edward.music.ui.screens.playlist.LocalPlaylistScreen
import com.caohaidang.edward.music.ui.screens.playlist.OnlinePlaylistScreen
import com.caohaidang.edward.music.ui.screens.search.OnlineSearchResult
import com.caohaidang.edward.music.ui.screens.search.SearchTabScreen
import com.caohaidang.edward.music.ui.screens.settings.SettingsScreen

@OptIn(ExperimentalMaterial3Api::class)
fun NavGraphBuilder.navigationBuilder(
    navController: NavHostController,
    scrollBehavior: TopAppBarScrollBehavior,
    latestVersionName: String,
    openSearchOnStart: Boolean = false,
) {
    composable(Screens.Home.route) {
        MainTabInsets { HomeScreen(navController) }
    }
    composable(Screens.Search.route) {
        MainTabInsets { SearchTabScreen(navController = navController, autoFocus = openSearchOnStart) }
    }
    composable(Screens.Library.route) {
        MainTabInsets { UnifiedLibraryScreen(navController = navController) }
    }
    composable(Screens.Account.route) {
        MainTabInsets { AccountScreen(navController = navController, scrollBehavior = scrollBehavior) }
    }
    composable(Screens.Welcome.route) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        WelcomeScreen(
            onSignIn = { navController.navigate(Screens.Login.route) },
            onSkip = {
                scope.launch {
                    context.dataStore.edit { it[OnboardingDoneKey] = true }
                    navController.navigate(Screens.Home.route) {
                        popUpTo(Screens.Welcome.route) { inclusive = true }
                    }
                }
            }
        )
    }
    composable(Screens.Login.route) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        LoginScreen(
            navController = navController,
            onLoggedIn = {
                scope.launch {
                    // Save first, then navigate: leaving the screen cancels this scope.
                    context.dataStore.edit { it[OnboardingDoneKey] = true }
                    val cameFromWelcome =
                        runCatching { navController.getBackStackEntry(Screens.Welcome.route) }.isSuccess
                    if (cameFromWelcome) {
                        navController.navigate(Screens.Home.route) {
                            popUpTo(Screens.Welcome.route) { inclusive = true }
                        }
                    } else {
                        navController.navigateUp()
                    }
                }
            }
        )
    }
    composable(Screens.Songs.route) {
        MainTabInsets { LibrarySectionScreen(navController, LibrarySection.SONGS) }
    }
    composable(Screens.Artists.route) {
        MainTabInsets { LibrarySectionScreen(navController, LibrarySection.ARTISTS) }
    }
    composable(Screens.Albums.route) {
        MainTabInsets { LibrarySectionScreen(navController, LibrarySection.ALBUMS) }
    }
    composable(Screens.Playlists.route) {
        MainTabInsets { LibrarySectionScreen(navController, LibrarySection.PLAYLISTS) }
    }
    composable("history") {
        HistoryScreen(navController)
    }
    composable("stats") {
        StatsScreen(navController)
    }
    composable("mood_and_genres") {
        MoodAndGenresScreen(navController, scrollBehavior)
    }
    composable("new_release") {
        NewReleaseScreen(navController, scrollBehavior)
    }
    composable(
        route = "search/{query}",
        arguments = listOf(
            navArgument("query") {
                type = NavType.StringType
            }
        ),
        enterTransition = {
            fadeIn(tween(250))
        },
        exitTransition = {
            if (targetState.destination.route?.startsWith("search/") == true) {
                fadeOut(tween(200))
            } else {
                fadeOut(tween(200)) + slideOutHorizontally { -it / 2 }
            }
        },
        popEnterTransition = {
            if (initialState.destination.route?.startsWith("search/") == true) {
                fadeIn(tween(250))
            } else {
                fadeIn(tween(250)) + slideInHorizontally { -it / 2 }
            }
        },
        popExitTransition = {
            fadeOut(tween(200))
        }
    ) {
        OnlineSearchResult(navController)
    }
    composable(
        route = "album/{albumId}",
        arguments = listOf(
            navArgument("albumId") {
                type = NavType.StringType
            },
        )
    ) {
        AlbumScreen(navController, scrollBehavior)
    }
    composable(
        route = "artist/{artistId}",
        arguments = listOf(
            navArgument("artistId") {
                type = NavType.StringType
            }
        )
    ) { backStackEntry ->
        val artistId = backStackEntry.arguments?.getString("artistId")!!
        if (artistId.startsWith("LA")) {
            ArtistSongsScreen(navController, scrollBehavior)
        } else {
            ArtistScreen(navController, scrollBehavior)
        }
    }
    composable(
        route = "artist/{artistId}/songs",
        arguments = listOf(
            navArgument("artistId") {
                type = NavType.StringType
            }
        )
    ) {
        ArtistSongsScreen(navController, scrollBehavior)
    }
    composable(
        route = "artist/{artistId}/items?browseId={browseId}&params={params}",
        arguments = listOf(
            navArgument("artistId") {
                type = NavType.StringType
            },
            navArgument("browseId") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            },
            navArgument("params") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            }
        )
    ) {
        ArtistItemsScreen(navController, scrollBehavior)
    }
    composable(
        route = "online_playlist/{playlistId}",
        arguments = listOf(
            navArgument("playlistId") {
                type = NavType.StringType
            }
        )
    ) {
        OnlinePlaylistScreen(navController, scrollBehavior)
    }
    composable(
        route = "local_playlist/{playlistId}",
        arguments = listOf(
            navArgument("playlistId") {
                type = NavType.StringType
            }
        )
    ) {
        LocalPlaylistScreen(navController, scrollBehavior)
    }
    composable(
        route = "youtube_browse/{browseId}?params={params}",
        arguments = listOf(
            navArgument("browseId") {
                type = NavType.StringType
                nullable = true
            },
            navArgument("params") {
                type = NavType.StringType
                nullable = true
            }
        )
    ) {
        YouTubeBrowseScreen(navController, scrollBehavior)
    }
    composable("settings") {
        SettingsScreen(navController, scrollBehavior)
    }
}

@Composable
private fun MainTabInsets(content: @Composable () -> Unit) {
    val base = LocalPlayerAwareWindowInsets.current
    val density = LocalDensity.current
    val bottom = with(density) { base.getBottom(density).toDp() }
    val adjusted = WindowInsets.systemBars
        .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top)
        .add(WindowInsets(bottom = bottom))
    CompositionLocalProvider(LocalPlayerAwareWindowInsets provides adjusted) {
        content()
    }
}
