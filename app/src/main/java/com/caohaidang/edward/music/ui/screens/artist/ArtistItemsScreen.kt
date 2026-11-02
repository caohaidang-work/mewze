package com.caohaidang.edward.music.ui.screens.artist

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import com.caohaidang.edward.music.ui.screens.library.GlassRow
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.caohaidang.edward.innertube.models.AlbumItem
import com.caohaidang.edward.innertube.models.ArtistItem
import com.caohaidang.edward.innertube.models.PlaylistItem
import com.caohaidang.edward.innertube.models.SongItem
import com.caohaidang.edward.innertube.models.WatchEndpoint
import com.caohaidang.edward.music.LocalPlayerAwareWindowInsets
import com.caohaidang.edward.music.LocalPlayerConnection
import com.caohaidang.edward.music.R
import com.caohaidang.edward.music.constants.GridThumbnailHeight
import com.caohaidang.edward.music.extensions.togglePlayPause
import com.caohaidang.edward.music.models.toMediaMetadata
import com.caohaidang.edward.music.playback.queues.YouTubeQueue
import com.caohaidang.edward.music.ui.component.IconButton
import com.caohaidang.edward.music.ui.component.LocalMenuState
import com.caohaidang.edward.music.ui.component.liquidBackdrop
import com.caohaidang.edward.music.ui.screens.library.BentoTone
import com.caohaidang.edward.music.ui.screens.library.GlassCoverCard
import com.caohaidang.edward.music.ui.screens.library.GlassIconButton
import com.caohaidang.edward.music.ui.screens.library.SongCover
import com.caohaidang.edward.music.ui.screens.library.tileColors
import com.caohaidang.edward.music.ui.utils.resize
import com.caohaidang.edward.music.utils.joinByBullet
import com.caohaidang.edward.music.utils.makeTimeString
import com.caohaidang.edward.music.ui.component.shimmer.GridItemPlaceHolder
import com.caohaidang.edward.music.ui.component.shimmer.ListItemPlaceHolder
import com.caohaidang.edward.music.ui.component.shimmer.ShimmerHost
import com.caohaidang.edward.music.ui.menu.YouTubeAlbumMenu
import com.caohaidang.edward.music.ui.menu.YouTubeArtistMenu
import com.caohaidang.edward.music.ui.menu.YouTubePlaylistMenu
import com.caohaidang.edward.music.ui.menu.YouTubeSongMenu
import com.caohaidang.edward.music.ui.menu.YouTubeSongSelectionMenu
import com.caohaidang.edward.music.ui.utils.backToMain
import com.caohaidang.edward.music.viewmodels.ArtistItemsViewModel

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ArtistItemsScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: ArtistItemsViewModel = hiltViewModel(),
) {
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()

    val lazyListState = rememberLazyListState()
    val lazyGridState = rememberLazyGridState()
    val coroutineScope = rememberCoroutineScope()

    val title by viewModel.title.collectAsState()
    val itemsPage by viewModel.itemsPage.collectAsState()
    val loadFailed by viewModel.error.collectAsState()
    val songIndex: Map<String, SongItem> by remember(itemsPage) {
        derivedStateOf {
            itemsPage?.items
                ?.filterIsInstance<SongItem>()
                ?.associateBy { it.id }
                .orEmpty()
        }
    }

    var inSelectMode by rememberSaveable { mutableStateOf(false) }
    val selection = rememberSaveable(
        saver = listSaver<MutableList<String>, String>(
            save = { it.toList() },
            restore = { it.toMutableStateList() }
        )
    ) { mutableStateListOf() }
    val onExitSelectionMode = {
        inSelectMode = false
        selection.clear()
    }
    if (inSelectMode) {
        BackHandler(onBack = onExitSelectionMode)
    }

    // Keyed on the continuation token: when a page arrives while the "loading" row is still on
    // screen (short pages), the flow restarts and asks for the next page instead of stalling.
    val continuation = itemsPage?.continuation
    LaunchedEffect(lazyListState, continuation) {
        snapshotFlow {
            lazyListState.layoutInfo.visibleItemsInfo.any { it.key == "loading" }
        }.collect { shouldLoadMore ->
            if (!shouldLoadMore) return@collect
            viewModel.loadMore()
        }
    }

    LaunchedEffect(lazyGridState, continuation) {
        snapshotFlow {
            lazyGridState.layoutInfo.visibleItemsInfo.any { it.key == "loading" }
        }.collect { shouldLoadMore ->
            if (!shouldLoadMore) return@collect
            viewModel.loadMore()
        }
    }

    val songColors = tileColors(BentoTone.SONG)
    val albumColors = tileColors(BentoTone.ALBUM)

    fun showItemMenu(item: com.caohaidang.edward.innertube.models.YTItem) {
        menuState.show {
            when (item) {
                is SongItem -> YouTubeSongMenu(song = item, navController = navController, onDismiss = menuState::dismiss)
                is AlbumItem -> YouTubeAlbumMenu(albumItem = item, navController = navController, onDismiss = menuState::dismiss)
                is ArtistItem -> YouTubeArtistMenu(artist = item, onDismiss = menuState::dismiss)
                is PlaylistItem -> YouTubePlaylistMenu(playlist = item, coroutineScope = coroutineScope, onDismiss = menuState::dismiss)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().liquidBackdrop()) {

    if (itemsPage == null && !loadFailed) {
        ShimmerHost(
            modifier = Modifier.windowInsetsPadding(LocalPlayerAwareWindowInsets.current)
        ) {
            repeat(8) {
                ListItemPlaceHolder()
            }
        }
    }

    // Failed or empty: show a message with a retry button instead of a blank/endless-loading screen.
    if ((itemsPage == null && loadFailed) || (itemsPage != null && itemsPage?.items.isNullOrEmpty())) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(LocalPlayerAwareWindowInsets.current)
        ) {
            Text(
                text = stringResource(if (loadFailed) R.string.error_unknown else R.string.no_results_found),
                style = MaterialTheme.typography.bodyLarge
            )
            if (loadFailed) {
                TextButton(onClick = viewModel::load) {
                    Text(stringResource(R.string.retry))
                }
            }
        }
    }

    if (itemsPage?.items?.firstOrNull() is SongItem) {
        LazyColumn(
            state = lazyListState,
            contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues()
        ) {
            items(
                items = itemsPage?.items?.filterIsInstance<SongItem>().orEmpty(),
                key = { it.id }
            ) { song ->
                val onCheckedChange: (Boolean) -> Unit = {
                    if (it) {
                        selection.add(song.id)
                    } else {
                        selection.remove(song.id)
                    }
                }

                GlassRow(
                    title = song.title,
                    subtitle = joinByBullet(
                        song.artists.joinToString { it.name },
                        makeTimeString(song.duration?.times(1000L))
                    ),
                    colors = songColors,
                    coverSize = 60.dp,
                    highlighted = mediaMetadata?.id == song.id || (inSelectMode && song.id in selection),
                    onClick = {
                        if (inSelectMode) {
                            onCheckedChange(song.id !in selection)
                        } else if (song.id == mediaMetadata?.id) {
                            playerConnection.player.togglePlayPause()
                        } else {
                            playerConnection.playQueue(YouTubeQueue(song.endpoint ?: WatchEndpoint(videoId = song.id), song.toMediaMetadata()))
                        }
                    },
                    onLongClick = {
                        if (!inSelectMode) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            inSelectMode = true
                            onCheckedChange(true)
                        }
                    },
                    trailing = {
                        if (inSelectMode) {
                            Checkbox(
                                checked = song.id in selection,
                                onCheckedChange = onCheckedChange
                            )
                        } else {
                            GlassIconButton(
                                icon = R.drawable.more_vert,
                                iconColor = songColors.content,
                                fill = songColors.content,
                                fillAlpha = 0.12f,
                                onClick = { showItemMenu(song) }
                            )
                        }
                    },
                    cover = {
                        SongCover(
                            url = song.thumbnail.resize(544, 544),
                            active = mediaMetadata?.id == song.id,
                            playing = isPlaying
                        )
                    },
                    modifier = Modifier.animateItem()
                )
            }

            if (itemsPage?.continuation != null) {
                item(key = "loading") {
                    ShimmerHost(Modifier.animateItem()) {
                        repeat(3) {
                            ListItemPlaceHolder()
                        }
                    }
                }
            }
        }
    } else {
        LazyVerticalGrid(
            state = lazyGridState,
            columns = GridCells.Fixed(2),
            contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
            modifier = Modifier.fillMaxSize()
        ) {
            items(
                items = itemsPage?.items.orEmpty(),
                key = { it.id }
            ) { item ->
                val active = when (item) {
                    is SongItem -> mediaMetadata?.id == item.id
                    is AlbumItem -> mediaMetadata?.album?.id == item.id
                    else -> false
                }
                val subtitle = when (item) {
                    is SongItem -> joinByBullet(item.artists.joinToString { it.name }, makeTimeString(item.duration?.times(1000L)))
                    is AlbumItem -> joinByBullet(item.artists?.joinToString { it.name }, item.year?.toString())
                    is PlaylistItem -> joinByBullet(item.author?.name, item.songCountText)
                    is ArtistItem -> ""
                }
                GlassCoverCard(
                    title = item.title,
                    subtitle = subtitle,
                    colors = albumColors,
                    highlighted = active,
                    onClick = {
                        when (item) {
                            is SongItem -> playerConnection.playQueue(YouTubeQueue(item.endpoint ?: WatchEndpoint(videoId = item.id), item.toMediaMetadata()))
                            is AlbumItem -> navController.navigate("album/${item.id}")
                            is ArtistItem -> navController.navigate("artist/${item.id}")
                            is PlaylistItem -> navController.navigate("online_playlist/${item.id}")
                        }
                    },
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        showItemMenu(item)
                    },
                    onMore = { showItemMenu(item) },
                    cover = {
                        SongCover(
                            url = item.thumbnail.resize(544, 544),
                            active = active && item is SongItem,
                            playing = isPlaying
                        )
                    },
                    modifier = Modifier
                        .padding(8.dp)
                        .animateItem()
                )
            }

            if (itemsPage?.continuation != null) {
                item(key = "loading") {
                    ShimmerHost(Modifier.animateItem()) {
                        GridItemPlaceHolder(fillMaxWidth = true)
                    }
                }
            }
        }
    }

    TopAppBar(
        title = {
            if (inSelectMode) {
                Text(pluralStringResource(R.plurals.n_selected, selection.size, selection.size))
            } else {
                Text(title)
            }
        },
        navigationIcon = {
            if (inSelectMode) {
                IconButton(onClick = onExitSelectionMode) {
                    Icon(
                        painter = painterResource(R.drawable.close),
                        contentDescription = null,
                    )
                }
            } else {
                IconButton(
                    onClick = navController::navigateUp,
                    onLongClick = navController::backToMain
                ) {
                    Icon(
                        painterResource(R.drawable.arrow_back),
                        contentDescription = null
                    )
                }
            }
        },
        actions = {
            if (inSelectMode) {
                Checkbox(
                    checked = selection.size == itemsPage?.items?.size && selection.isNotEmpty(),
                    onCheckedChange = {
                        if (selection.size == itemsPage?.items?.size) {
                            selection.clear()
                        } else {
                            selection.clear()
                            selection.addAll(itemsPage?.items?.map { it.id }.orEmpty())
                        }
                    }
                )
                IconButton(
                    enabled = selection.isNotEmpty(),
                    onClick = {
                        menuState.show {
                            YouTubeSongSelectionMenu(
                                selection = selection.mapNotNull { songId ->
                                    songIndex[songId]
                                },
                                onDismiss = menuState::dismiss,
                                onExitSelectionMode = onExitSelectionMode
                            )
                        }
                    }
                ) {
                    Icon(
                        painterResource(R.drawable.more_vert),
                        contentDescription = null
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            scrolledContainerColor = Color.Transparent
        ),
        scrollBehavior = scrollBehavior
    )
    }
}
