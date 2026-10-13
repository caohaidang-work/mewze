package com.caohaidang.edward.music.ui.screens.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.caohaidang.edward.music.LocalDatabase
import com.caohaidang.edward.music.LocalPlayerAwareWindowInsets
import com.caohaidang.edward.music.LocalPlayerConnection
import com.caohaidang.edward.music.R
import com.caohaidang.edward.music.constants.AlbumSortType
import com.caohaidang.edward.music.constants.GridCellSize
import com.caohaidang.edward.music.constants.GridCellSizeKey
import com.caohaidang.edward.music.constants.GridThumbnailHeight
import com.caohaidang.edward.music.constants.LibraryTabViewTypeKey
import com.caohaidang.edward.music.constants.LibraryViewType
import com.caohaidang.edward.music.constants.ListThumbnailSize
import com.caohaidang.edward.music.constants.PlaylistSortType
import com.caohaidang.edward.music.constants.SmallGridThumbnailHeight
import com.caohaidang.edward.music.constants.SongSortType
import com.caohaidang.edward.music.constants.ThumbnailCornerRadius
import com.caohaidang.edward.music.db.entities.Album
import com.caohaidang.edward.music.db.entities.Playlist
import com.caohaidang.edward.music.db.entities.PlaylistEntity
import com.caohaidang.edward.music.db.entities.Song
import com.caohaidang.edward.music.extensions.toMediaItem
import com.caohaidang.edward.music.extensions.togglePlayPause
import com.caohaidang.edward.music.playback.queues.ListQueue
import com.caohaidang.edward.music.ui.component.AlbumGridItem
import com.caohaidang.edward.music.ui.component.AlbumListItem
import com.caohaidang.edward.music.ui.component.ChipsRow
import com.caohaidang.edward.music.ui.component.EmptyPlaceholder
import com.caohaidang.edward.music.ui.component.LocalMenuState
import com.caohaidang.edward.music.ui.component.PlaylistGridItem
import com.caohaidang.edward.music.ui.component.PlaylistListItem
import com.caohaidang.edward.music.ui.component.SongGridItem
import com.caohaidang.edward.music.ui.component.SongListItem
import com.caohaidang.edward.music.ui.component.TextFieldDialog
import com.caohaidang.edward.music.ui.menu.AlbumMenu
import com.caohaidang.edward.music.ui.menu.PlaylistMenu
import com.caohaidang.edward.music.ui.menu.SongMenu
import com.caohaidang.edward.music.utils.rememberEnumPreference

private enum class LibraryTab(val label: String) {
    ALBUMS("Album yêu thích"),
    SONGS("Bài hát yêu thích"),
    PLAYLISTS("Playlist"),
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun UnifiedLibraryScreen(
    navController: NavController,
) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()

    val likedAlbums by remember(database) { database.albumsLiked(AlbumSortType.CREATE_DATE, true) }
        .collectAsState(initial = emptyList())
    val likedSongs by remember(database) { database.likedSongs(SongSortType.CREATE_DATE, true) }
        .collectAsState(initial = emptyList())
    val playlists by remember(database) { database.playlists(PlaylistSortType.CREATE_DATE, true) }
        .collectAsState(initial = emptyList())

    var tab by rememberSaveable { mutableStateOf(LibraryTab.ALBUMS) }
    var viewType by rememberEnumPreference(LibraryTabViewTypeKey, LibraryViewType.GRID)
    val gridCellSize by rememberEnumPreference(GridCellSizeKey, GridCellSize.SMALL)
    val isGrid = viewType == LibraryViewType.GRID

    var showCreatePlaylistDialog by rememberSaveable { mutableStateOf(false) }
    val gridState = rememberLazyGridState()

    LaunchedEffect(tab) { gridState.scrollToItem(0) }

    if (showCreatePlaylistDialog) {
        TextFieldDialog(
            icon = { Icon(painter = painterResource(R.drawable.add), contentDescription = null) },
            title = { Text(text = stringResource(R.string.create_playlist)) },
            onDismiss = { showCreatePlaylistDialog = false },
            onDone = { playlistName ->
                database.query {
                    insert(PlaylistEntity(name = playlistName))
                }
            }
        )
    }

    val playSongs: (List<Song>, Int) -> Unit = { songs, startIndex ->
        playerConnection.playQueue(
            ListQueue(
                title = context.getString(R.string.queue_all_songs),
                items = songs.map { it.toMediaItem() },
                startIndex = startIndex
            )
        )
    }
    val showSongMenu: (Song) -> Unit = { song ->
        menuState.show {
            SongMenu(
                originalSong = song,
                navController = navController,
                onDismiss = menuState::dismiss
            )
        }
    }
    val showAlbumMenu: (Album) -> Unit = { album ->
        menuState.show {
            AlbumMenu(
                originalAlbum = album,
                navController = navController,
                onDismiss = menuState::dismiss
            )
        }
    }
    val showPlaylistMenu: (Playlist) -> Unit = { playlist ->
        menuState.show {
            PlaylistMenu(
                playlist = playlist,
                coroutineScope = coroutineScope,
                onDismiss = menuState::dismiss
            )
        }
    }

    val cellMinSize = when (gridCellSize) {
        GridCellSize.SMALL -> SmallGridThumbnailHeight
        GridCellSize.BIG -> GridThumbnailHeight
    } + 24.dp

    Box(modifier = Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            state = gridState,
            columns = if (isGrid) GridCells.Adaptive(minSize = cellMinSize) else GridCells.Fixed(1),
            contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
            modifier = Modifier.fillMaxSize()
        ) {
            item(key = "title", span = { GridItemSpan(maxLineSpan) }) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 8.dp, top = 8.dp)
                ) {
                    Text(
                        text = "Thư viện",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { viewType = viewType.toggle() }) {
                        Icon(
                            painter = painterResource(
                                if (isGrid) R.drawable.grid_view else R.drawable.list
                            ),
                            contentDescription = null
                        )
                    }
                }
            }

            item(key = "tabs", span = { GridItemSpan(maxLineSpan) }) {
                ChipsRow(
                    chips = LibraryTab.entries.map { it to it.label },
                    currentValue = tab,
                    onValueUpdate = { tab = it }
                )
            }

            item(key = "actions_${tab.name}", span = { GridItemSpan(maxLineSpan) }) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    val countText = when (tab) {
                        LibraryTab.ALBUMS -> pluralStringResource(R.plurals.n_album, likedAlbums.size, likedAlbums.size)
                        LibraryTab.SONGS -> pluralStringResource(R.plurals.n_song, likedSongs.size, likedSongs.size)
                        LibraryTab.PLAYLISTS -> pluralStringResource(R.plurals.n_playlist, playlists.size, playlists.size)
                    }
                    Text(
                        text = countText,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.weight(1f)
                    )

                    when (tab) {
                        LibraryTab.SONGS -> if (likedSongs.isNotEmpty()) {
                            FilledTonalButton(onClick = { playSongs(likedSongs, 0) }) {
                                Icon(painterResource(R.drawable.play), null, Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.play))
                            }
                            OutlinedButton(onClick = { playSongs(likedSongs.shuffled(), 0) }) {
                                Icon(painterResource(R.drawable.shuffle), null, Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.shuffle))
                            }
                        }

                        LibraryTab.PLAYLISTS -> FilledTonalButton(onClick = { showCreatePlaylistDialog = true }) {
                            Icon(painterResource(R.drawable.add), null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Tạo playlist")
                        }

                        LibraryTab.ALBUMS -> Unit
                    }
                }
            }

            when (tab) {
                LibraryTab.ALBUMS -> albumsContent(
                    albums = likedAlbums,
                    isGrid = isGrid,
                    activeAlbumId = mediaMetadata?.album?.id,
                    isPlaying = isPlaying,
                    coroutineScope = coroutineScope,
                    onOpen = { navController.navigate("album/${it.id}") },
                    onOptions = showAlbumMenu,
                    onLongPress = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        showAlbumMenu(it)
                    }
                )

                LibraryTab.SONGS -> songsContent(
                    songs = likedSongs,
                    isGrid = isGrid,
                    activeSongId = mediaMetadata?.id,
                    isPlaying = isPlaying,
                    onPlay = { song, index ->
                        if (song.id == mediaMetadata?.id) {
                            playerConnection.player.togglePlayPause()
                        } else {
                            playSongs(likedSongs, index)
                        }
                    },
                    onOptions = showSongMenu,
                    onLongPress = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        showSongMenu(it)
                    }
                )

                LibraryTab.PLAYLISTS -> playlistsContent(
                    playlists = playlists,
                    isGrid = isGrid,
                    onCreate = { showCreatePlaylistDialog = true },
                    onOpen = { navController.navigate("local_playlist/${it.id}") },
                    onOptions = showPlaylistMenu,
                    onLongPress = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        showPlaylistMenu(it)
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun LazyGridScope.albumsContent(
    albums: List<Album>,
    isGrid: Boolean,
    activeAlbumId: String?,
    isPlaying: Boolean,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    onOpen: (Album) -> Unit,
    onOptions: (Album) -> Unit,
    onLongPress: (Album) -> Unit,
) {
    if (albums.isEmpty()) {
        item(key = "empty_albums", span = { GridItemSpan(maxLineSpan) }) {
            EmptyPlaceholder(
                icon = R.drawable.album,
                text = "Album bạn thích sẽ hiện ở đây"
            )
        }
        return
    }

    items(items = albums, key = { "album_${it.id}" }) { album ->
        if (isGrid) {
            Box(modifier = Modifier.fillMaxWidth()) {
                AlbumGridItem(
                    album = album,
                    isActive = album.id == activeAlbumId,
                    isPlaying = isPlaying,
                    coroutineScope = coroutineScope,
                    fillMaxWidth = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = { onOpen(album) },
                            onLongClick = { onLongPress(album) }
                        )
                )
                GridOptionsButton(
                    onClick = { onOptions(album) },
                    modifier = Modifier.align(Alignment.TopEnd)
                )
            }
        } else {
            AlbumListItem(
                album = album,
                isActive = album.id == activeAlbumId,
                isPlaying = isPlaying,
                trailingContent = { ListOptionsButton { onOptions(album) } },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpen(album) }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun LazyGridScope.songsContent(
    songs: List<Song>,
    isGrid: Boolean,
    activeSongId: String?,
    isPlaying: Boolean,
    onPlay: (Song, Int) -> Unit,
    onOptions: (Song) -> Unit,
    onLongPress: (Song) -> Unit,
) {
    if (songs.isEmpty()) {
        item(key = "empty_songs", span = { GridItemSpan(maxLineSpan) }) {
            EmptyPlaceholder(
                icon = R.drawable.favorite_border,
                text = "Bài hát bạn thả tim sẽ hiện ở đây"
            )
        }
        return
    }

    items(
        count = songs.size,
        key = { "song_${songs[it].id}" }
    ) { index ->
        val song = songs[index]
        if (isGrid) {
            Box(modifier = Modifier.fillMaxWidth()) {
                SongGridItem(
                    song = song,
                    isActive = song.id == activeSongId,
                    isPlaying = isPlaying,
                    fillMaxWidth = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = { onPlay(song, index) },
                            onLongClick = { onLongPress(song) }
                        )
                )
                GridOptionsButton(
                    onClick = { onOptions(song) },
                    modifier = Modifier.align(Alignment.TopEnd)
                )
            }
        } else {
            SongListItem(
                song = song,
                isActive = song.id == activeSongId,
                isPlaying = isPlaying,
                trailingContent = { ListOptionsButton { onOptions(song) } },
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = { onPlay(song, index) },
                        onLongClick = { onLongPress(song) }
                    )
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun LazyGridScope.playlistsContent(
    playlists: List<Playlist>,
    isGrid: Boolean,
    onCreate: () -> Unit,
    onOpen: (Playlist) -> Unit,
    onOptions: (Playlist) -> Unit,
    onLongPress: (Playlist) -> Unit,
) {
    item(key = "create_playlist") {
        if (isGrid) CreatePlaylistGridCell(onCreate) else CreatePlaylistListRow(onCreate)
    }

    if (playlists.isEmpty()) {
        item(key = "empty_playlists", span = { GridItemSpan(maxLineSpan) }) {
            EmptyPlaceholder(
                icon = R.drawable.queue_music,
                text = stringResource(R.string.library_playlist_empty)
            )
        }
        return
    }

    items(items = playlists, key = { "playlist_${it.id}" }) { playlist ->
        if (isGrid) {
            Box(modifier = Modifier.fillMaxWidth()) {
                PlaylistGridItem(
                    playlist = playlist,
                    fillMaxWidth = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = { onOpen(playlist) },
                            onLongClick = { onLongPress(playlist) }
                        )
                )
                GridOptionsButton(
                    onClick = { onOptions(playlist) },
                    modifier = Modifier.align(Alignment.TopEnd)
                )
            }
        } else {
            PlaylistListItem(
                playlist = playlist,
                trailingContent = { ListOptionsButton { onOptions(playlist) } },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpen(playlist) }
            )
        }
    }
}

@Composable
private fun ListOptionsButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            painter = painterResource(R.drawable.more_vert),
            contentDescription = null
        )
    }
}

@Composable
private fun GridOptionsButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .padding(top = 16.dp, end = 16.dp)
            .size(30.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(onClick = onClick)
    ) {
        Icon(
            painter = painterResource(R.drawable.more_vert),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun CreatePlaylistGridCell(onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(ThumbnailCornerRadius))
                .background(MaterialTheme.colorScheme.secondaryContainer)
        ) {
            Icon(
                painter = painterResource(R.drawable.add),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(40.dp)
            )
        }
        Spacer(Modifier.size(6.dp))
        Text(
            text = "Tạo playlist",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

@Composable
private fun CreatePlaylistListRow(onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(ListThumbnailSize)
                .clip(RoundedCornerShape(ThumbnailCornerRadius))
                .background(MaterialTheme.colorScheme.secondaryContainer)
        ) {
            Icon(
                painter = painterResource(R.drawable.add),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = "Tạo playlist",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold
        )
    }
}
