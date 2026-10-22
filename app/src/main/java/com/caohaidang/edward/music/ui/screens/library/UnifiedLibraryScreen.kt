package com.caohaidang.edward.music.ui.screens.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.caohaidang.edward.music.constants.ArtistFilter
import com.caohaidang.edward.music.constants.ArtistFilterKey
import com.caohaidang.edward.music.ui.component.glassCard
import com.caohaidang.edward.music.ui.menu.ArtistMenu
import com.caohaidang.edward.music.ui.screens.Screens
import com.caohaidang.edward.music.viewmodels.LibraryArtistsViewModel
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
import com.caohaidang.edward.music.constants.LibraryAlbumsSortKey
import com.caohaidang.edward.music.constants.LibraryPlaylistsSortKey
import com.caohaidang.edward.music.constants.LibrarySongsSortKey
import com.caohaidang.edward.music.constants.LibrarySortOption
import java.text.Collator

enum class LibrarySection(val title: String) {
    ALBUMS("Album yêu thích"),
    SONGS("Bài hát yêu thích"),
    PLAYLISTS("Playlist"),
    ARTISTS("Nghệ sĩ"),
}


private fun LibrarySortOption.label(): String = when (this) {
    LibrarySortOption.NEWEST -> "Mới nhất"
    LibrarySortOption.OLDEST -> "Cũ nhất"
    LibrarySortOption.NAME_AZ -> "A-Z"
    LibrarySortOption.NAME_ZA -> "Z-A"
}

/**
 * The lists coming from the database are already newest-first, and "newest" follows the order of the
 * YouTube Music library (the sync stamps items in that order), so date sorting only flips the list
 * and never re-sorts by anything else.
 */
private fun <T> List<T>.sortedFor(option: LibrarySortOption, name: (T) -> String): List<T> = when (option) {
    LibrarySortOption.NEWEST -> this
    LibrarySortOption.OLDEST -> asReversed()
    LibrarySortOption.NAME_AZ -> {
        val collator = Collator.getInstance()
        sortedWith { a, b -> collator.compare(name(a), name(b)) }
    }
    LibrarySortOption.NAME_ZA -> {
        val collator = Collator.getInstance()
        sortedWith { a, b -> collator.compare(name(b), name(a)) }
    }
}

/** Small "Mới nhất ▾" chip that opens the sort choices. */
@Composable
private fun SortMenuButton(
    selected: LibrarySortOption,
    onSelected: (LibrarySortOption) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(CircleShape)
                .clickable { expanded = true }
                .padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp)
        ) {
            Text(
                text = selected.label(),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.secondary
            )
            Icon(
                painter = painterResource(R.drawable.expand_more),
                contentDescription = "Sắp xếp",
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(20.dp)
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            LibrarySortOption.values().forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = option.label(),
                            fontWeight = if (option == selected) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    onClick = {
                        expanded = false
                        onSelected(option)
                    }
                )
            }
        }
    }
}

/** Small rounded "glass" capsule used for the header buttons (back, view toggle, more). */
@Composable
private fun GlassPill(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
            .height(38.dp)
            .glassCard(CircleShape)
            .clip(CircleShape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp),
        content = content
    )
}

@Composable
private fun PillIconButton(@androidx.annotation.DrawableRes icon: Int, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
    ) {
        Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(20.dp))
    }
}

/** "‹ Thư viện" on the left, a capsule with actions on the right, then the large title. */
@Composable
private fun SectionHeader(
    navController: NavController,
    title: String,
    actions: @Composable RowScope.() -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 8.dp)
        ) {
            GlassPill(onClick = navController::navigateUp) {
                Icon(painterResource(R.drawable.arrow_back), null, Modifier.size(18.dp))
                Text("Thư viện", style = MaterialTheme.typography.labelLarge)
            }
            GlassPill(content = actions)
        }
        Text(
            text = title,
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 10.dp)
        )
    }
}

@Composable
private fun HubRow(@androidx.annotation.DrawableRes icon: Int, label: String, onClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 20.dp, end = 16.dp, top = 14.dp, bottom = 14.dp)
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(16.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            Icon(
                painter = painterResource(R.drawable.navigate_next),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        HorizontalDivider(
            modifier = Modifier.padding(start = 60.dp),
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant
        )
    }
}

/** Library home: a plain list of sections followed by what was added recently. */
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

    var showCreatePlaylistDialog by rememberSaveable { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }

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

    // A few of each kind, interleaved, so the grid reads as "recently added".
    val recent: List<Any> = remember(likedAlbums, likedSongs, playlists) {
        buildList {
            val p = playlists.take(3)
            val a = likedAlbums.take(3)
            val s = likedSongs.take(3)
            for (i in 0 until 3) {
                p.getOrNull(i)?.let(::add)
                a.getOrNull(i)?.let(::add)
                s.getOrNull(i)?.let(::add)
            }
        }
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
        modifier = Modifier.fillMaxSize()
    ) {
        item(key = "title", span = { GridItemSpan(maxLineSpan) }) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 6.dp)
            ) {
                Text(
                    text = "Thư viện",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Box {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(40.dp)
                            .glassCard(CircleShape)
                            .clip(CircleShape)
                            .clickable { showMoreMenu = true }
                    ) {
                        Icon(painterResource(R.drawable.more_horiz), contentDescription = null)
                    }
                    DropdownMenu(expanded = showMoreMenu, onDismissRequest = { showMoreMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Tạo playlist") },
                            leadingIcon = { Icon(painterResource(R.drawable.add), null) },
                            onClick = {
                                showMoreMenu = false
                                showCreatePlaylistDialog = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Lịch sử nghe") },
                            leadingIcon = { Icon(painterResource(R.drawable.history), null) },
                            onClick = {
                                showMoreMenu = false
                                navController.navigate("history")
                            }
                        )
                    }
                }
            }
        }

        item(key = "hub", span = { GridItemSpan(maxLineSpan) }) {
            Column {
                HubRow(R.drawable.queue_music, "Playlist") { navController.navigate(Screens.Playlists.route) }
                HubRow(R.drawable.artist, "Nghệ sĩ") { navController.navigate(Screens.Artists.route) }
                HubRow(R.drawable.music_note, "Bài hát") { navController.navigate(Screens.Songs.route) }
                HubRow(R.drawable.album, "Album") { navController.navigate(Screens.Albums.route) }
            }
        }

        if (recent.isNotEmpty()) {
            item(key = "recent_title", span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    text = "Mới thêm gần đây",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp)
                )
            }
        }

        items(count = recent.size, key = { i ->
            when (val item = recent[i]) {
                is Playlist -> "recent_playlist_${item.id}"
                is Album -> "recent_album_${item.id}"
                is Song -> "recent_song_${item.id}"
                else -> "recent_$i"
            }
        }) { index ->
            when (val item = recent[index]) {
                is Playlist -> PlaylistGridItem(
                    playlist = item,
                    fillMaxWidth = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = { navController.navigate("local_playlist/${item.id}") },
                            onLongClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                menuState.show {
                                    PlaylistMenu(
                                        playlist = item,
                                        coroutineScope = coroutineScope,
                                        onDismiss = menuState::dismiss
                                    )
                                }
                            }
                        )
                )

                is Album -> AlbumGridItem(
                    album = item,
                    isActive = item.id == mediaMetadata?.album?.id,
                    isPlaying = isPlaying,
                    coroutineScope = coroutineScope,
                    fillMaxWidth = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = { navController.navigate("album/${item.id}") },
                            onLongClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                menuState.show {
                                    AlbumMenu(
                                        originalAlbum = item,
                                        navController = navController,
                                        onDismiss = menuState::dismiss
                                    )
                                }
                            }
                        )
                )

                is Song -> SongGridItem(
                    song = item,
                    isActive = item.id == mediaMetadata?.id,
                    isPlaying = isPlaying,
                    fillMaxWidth = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = {
                                if (item.id == mediaMetadata?.id) {
                                    playerConnection.player.togglePlayPause()
                                } else {
                                    playSongs(likedSongs, likedSongs.indexOf(item).coerceAtLeast(0))
                                }
                            },
                            onLongClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                menuState.show {
                                    SongMenu(
                                        originalSong = item,
                                        navController = navController,
                                        onDismiss = menuState::dismiss
                                    )
                                }
                            }
                        )
                )
            }
        }
    }
}

/** One page of the library (liked albums, liked songs, playlists), styled like the library home. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibrarySectionScreen(
    navController: NavController,
    section: LibrarySection,
) {
    if (section == LibrarySection.ARTISTS) {
        LibraryArtistsSection(navController)
        return
    }

    val context = LocalContext.current
    val database = LocalDatabase.current
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()

    val likedAlbumsByDate by remember(database) { database.albumsLiked(AlbumSortType.CREATE_DATE, true) }
        .collectAsState(initial = emptyList())
    val likedSongsByDate by remember(database) { database.likedSongs(SongSortType.CREATE_DATE, true) }
        .collectAsState(initial = emptyList())
    val playlistsByDate by remember(database) { database.playlists(PlaylistSortType.CREATE_DATE, true) }
        .collectAsState(initial = emptyList())

    var songsSort by rememberEnumPreference(LibrarySongsSortKey, LibrarySortOption.NEWEST)
    var albumsSort by rememberEnumPreference(LibraryAlbumsSortKey, LibrarySortOption.NEWEST)
    var playlistsSort by rememberEnumPreference(LibraryPlaylistsSortKey, LibrarySortOption.NEWEST)

    val likedSongs = remember(likedSongsByDate, songsSort) { likedSongsByDate.sortedFor(songsSort) { it.song.title } }
    val likedAlbums = remember(likedAlbumsByDate, albumsSort) { likedAlbumsByDate.sortedFor(albumsSort) { it.album.title } }
    val playlists = remember(playlistsByDate, playlistsSort) { playlistsByDate.sortedFor(playlistsSort) { it.playlist.name } }

    // List / grid choice is remembered (shared by albums, songs and playlists).
    var viewType by rememberEnumPreference(LibraryTabViewTypeKey, LibraryViewType.GRID)
    val gridCellSize by rememberEnumPreference(GridCellSizeKey, GridCellSize.SMALL)
    val isGrid = viewType == LibraryViewType.GRID

    var showCreatePlaylistDialog by rememberSaveable { mutableStateOf(false) }
    val gridState = rememberLazyGridState()

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
            item(key = "header", span = { GridItemSpan(maxLineSpan) }) {
                SectionHeader(navController = navController, title = section.title) {
                    PillIconButton(
                        icon = if (isGrid) R.drawable.grid_view else R.drawable.list,
                        onClick = { viewType = viewType.toggle() }
                    )
                    if (section == LibrarySection.PLAYLISTS) {
                        PillIconButton(icon = R.drawable.add, onClick = { showCreatePlaylistDialog = true })
                    }
                }
            }

            item(key = "actions_${section.name}", span = { GridItemSpan(maxLineSpan) }) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    when (section) {
                        LibrarySection.SONGS -> {
                            if (likedSongs.isNotEmpty()) {
                                FilledTonalButton(
                                    onClick = { playSongs(likedSongs, 0) },
                                    contentPadding = PaddingValues(0.dp),
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.play),
                                        contentDescription = stringResource(R.string.play),
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                FilledTonalButton(
                                    onClick = { playSongs(likedSongs.shuffled(), 0) },
                                    contentPadding = PaddingValues(0.dp),
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.shuffle),
                                        contentDescription = stringResource(R.string.shuffle),
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.weight(1f))
                            SortMenuButton(selected = songsSort, onSelected = { songsSort = it })
                        }

                        else -> {
                            val countText = when (section) {
                                LibrarySection.ALBUMS -> pluralStringResource(R.plurals.n_album, likedAlbums.size, likedAlbums.size)
                                else -> pluralStringResource(R.plurals.n_playlist, playlists.size, playlists.size)
                            }
                            Text(
                                text = countText,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.weight(1f)
                            )
                            if (section == LibrarySection.ALBUMS) {
                                SortMenuButton(selected = albumsSort, onSelected = { albumsSort = it })
                            } else {
                                SortMenuButton(selected = playlistsSort, onSelected = { playlistsSort = it })
                            }
                        }
                    }
                }
            }

            when (section) {
                LibrarySection.ALBUMS -> albumsContent(
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

                LibrarySection.SONGS -> songsContent(
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

                LibrarySection.PLAYLISTS -> playlistsContent(
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

                LibrarySection.ARTISTS -> Unit
            }
        }
    }
}

/** Artists page: round avatars in a plain list with chevrons, keeping the library/liked filter. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibraryArtistsSection(
    navController: NavController,
    viewModel: LibraryArtistsViewModel = hiltViewModel(),
) {
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    var filter by rememberEnumPreference(ArtistFilterKey, ArtistFilter.LIBRARY)
    val artists by viewModel.allArtists.collectAsState()

    LazyColumn(
        contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
        modifier = Modifier.fillMaxSize()
    ) {
        item(key = "header") {
            SectionHeader(navController = navController, title = LibrarySection.ARTISTS.title) {}
        }
        item(key = "filter") {
            ChipsRow(
                chips = listOf(
                    ArtistFilter.LIBRARY to stringResource(R.string.filter_library),
                    ArtistFilter.LIKED to stringResource(R.string.filter_liked)
                ),
                currentValue = filter,
                onValueUpdate = { filter = it }
            )
        }
        artists?.let { list ->
            if (list.isEmpty()) {
                item(key = "empty") {
                    EmptyPlaceholder(
                        icon = R.drawable.artist,
                        text = stringResource(R.string.library_artist_empty)
                    )
                }
            }
            items(items = list, key = { it.id }) { artist ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = { navController.navigate("artist/${artist.id}") },
                            onLongClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                menuState.show {
                                    ArtistMenu(
                                        originalArtist = artist,
                                        coroutineScope = coroutineScope,
                                        onDismiss = menuState::dismiss
                                    )
                                }
                            }
                        )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)
                    ) {
                        AsyncImage(
                            model = artist.artist.thumbnailUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        )
                        Spacer(Modifier.width(14.dp))
                        Text(
                            text = artist.artist.name,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            painter = painterResource(R.drawable.navigate_next),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 74.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                }
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
