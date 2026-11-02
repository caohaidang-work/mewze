package com.caohaidang.edward.music.ui.screens.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.caohaidang.edward.music.constants.ArtistFilter
import com.caohaidang.edward.music.constants.ArtistFilterKey
import com.caohaidang.edward.music.ui.component.liquidBackdrop
import com.caohaidang.edward.music.ui.component.liquidGlass
import com.caohaidang.edward.music.db.entities.Artist
import androidx.compose.ui.text.style.TextAlign
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.rotate
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import com.caohaidang.edward.music.constants.ArtistSortType
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
import com.caohaidang.edward.music.constants.LibraryTabViewTypeKey
import com.caohaidang.edward.music.constants.LibraryViewType
import com.caohaidang.edward.music.constants.PlaylistSortType
import com.caohaidang.edward.music.constants.SongSortType
import com.caohaidang.edward.music.db.entities.Album
import com.caohaidang.edward.music.db.entities.Playlist
import com.caohaidang.edward.music.db.entities.PlaylistEntity
import com.caohaidang.edward.music.db.entities.Song
import com.caohaidang.edward.music.extensions.toMediaItem
import com.caohaidang.edward.music.extensions.togglePlayPause
import com.caohaidang.edward.music.playback.queues.ListQueue
import com.caohaidang.edward.music.ui.component.EmptyPlaceholder
import com.caohaidang.edward.music.ui.component.LocalMenuState
import com.caohaidang.edward.music.ui.component.PlaylistThumbnail
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
            .liquidGlass(CircleShape, elevation = 6.dp)
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
    actions: (@Composable RowScope.() -> Unit)? = null,
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
            if (actions != null) GlassPill(content = actions)
        }
        Text(
            text = title,
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 10.dp)
        )
    }
}

/** Big "Yêu thích" tile: count, play / shuffle buttons and a tilted stack of covers. */
@Composable
private fun LibraryHero(
    count: Int,
    covers: List<String?>,
    onClick: () -> Unit,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp)
            .liquidGlass(RoundedCornerShape(28.dp), tint = scheme.primaryContainer, tintAlpha = 0.7f, elevation = 10.dp)
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(start = 20.dp, top = 18.dp)) {
            Text(
                text = "Yêu thích",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = scheme.onPrimaryContainer
            )
            Text(
                text = "$count bài hát",
                style = MaterialTheme.typography.labelLarge,
                color = scheme.onPrimaryContainer
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 20.dp, bottom = 16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .height(44.dp)
                    .clip(CircleShape)
                    .background(scheme.onPrimaryContainer)
                    .clickable(onClick = onPlay)
                    .padding(start = 14.dp, end = 18.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.play),
                    contentDescription = null,
                    tint = scheme.primaryContainer,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Phát",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.primaryContainer
                )
            }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(scheme.onPrimaryContainer.copy(alpha = 0.14f))
                    .clickable(onClick = onShuffle)
            ) {
                Icon(
                    painter = painterResource(R.drawable.shuffle),
                    contentDescription = "Phát ngẫu nhiên",
                    tint = scheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        CoverFan(
            covers = covers,
            coverSize = 84.dp,
            step = 30.dp,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 20.dp)
        )
    }
}

/** Collection tile (Playlist / Nghệ sĩ / Album): own colour, title at the bottom, tilted covers in the corner. */
@Composable
private fun CollectionCard(
    title: String,
    subtitle: String,
    covers: List<String?>,
    circle: Boolean,
    tone: BentoTone,
    coverSize: Dp,
    coverStep: Dp,
    coverAlignment: Alignment,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = tileColors(tone)
    Box(
        modifier = modifier
            .liquidGlass(RoundedCornerShape(28.dp), tint = colors.container, tintAlpha = 0.62f, elevation = 10.dp)
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 18.dp, bottom = 16.dp, end = 12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = colors.content
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelMedium,
                color = colors.content.copy(alpha = 0.75f)
            )
        }
        CoverFan(
            covers = covers,
            coverSize = coverSize,
            step = coverStep,
            shape = if (circle) CircleShape else RoundedCornerShape(16.dp),
            modifier = Modifier
                .align(coverAlignment)
                .padding(14.dp)
        )
    }
}

private val RecentCoverSize = 76.dp

/** Row of "Mới thêm gần đây": a glass card with a big cover on the left. */
@Composable
private fun RecentRow(
    typeLabel: String,
    title: String,
    subtitle: String,
    colors: TileColors,
    @androidx.annotation.DrawableRes trailingIcon: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    cover: @Composable () -> Unit,
) {
    GlassRow(
        title = title,
        subtitle = subtitle,
        colors = colors,
        onClick = onClick,
        onLongClick = onLongClick,
        typeLabel = typeLabel,
        coverSize = RecentCoverSize,
        trailing = {
            GlassIconButton(
                icon = trailingIcon,
                iconColor = colors.content,
                fill = colors.content,
                fillAlpha = 0.12f
            )
        },
        cover = cover
    )
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
    val artists by remember(database) { database.artists(ArtistSortType.CREATE_DATE, true) }
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

    val playlistColors = tileColors(BentoTone.PLAYLIST)
    val albumColors = tileColors(BentoTone.ALBUM)
    val songColors = tileColors(BentoTone.SONG)

    // A few of each kind, interleaved, so the list reads as "recently added".
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

    Box(modifier = Modifier.fillMaxSize().liquidBackdrop()) {
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
                            .liquidGlass(CircleShape, elevation = 6.dp)
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
                            text = { Text("Đã tải xuống") },
                            leadingIcon = { Icon(painterResource(R.drawable.download), null) },
                            onClick = {
                                showMoreMenu = false
                                navController.navigate("downloaded")
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

        item(key = "bento", span = { GridItemSpan(maxLineSpan) }) {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                LibraryHero(
                    count = likedSongs.size,
                    covers = likedSongs.map { it.thumbnailUrl },
                    onClick = { navController.navigate(Screens.Songs.route) },
                    onPlay = { if (likedSongs.isNotEmpty()) playSongs(likedSongs, 0) },
                    onShuffle = { if (likedSongs.isNotEmpty()) playSongs(likedSongs.shuffled(), 0) }
                )
            }
        }

        item(key = "collections_title", span = { GridItemSpan(maxLineSpan) }) {
            Text(
                text = "Bộ sưu tập",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 6.dp)
            )
        }

        item(key = "collections", span = { GridItemSpan(maxLineSpan) }) {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CollectionCard(
                        title = "Playlist",
                        subtitle = "${playlists.size} playlist",
                        covers = playlists.mapNotNull { it.displayThumbnails.firstOrNull() },
                        circle = false,
                        tone = BentoTone.PLAYLIST,
                        coverSize = 60.dp,
                        coverStep = 22.dp,
                        coverAlignment = Alignment.TopEnd,
                        modifier = Modifier.weight(1f).height(156.dp),
                        onClick = { navController.navigate(Screens.Playlists.route) }
                    )
                    CollectionCard(
                        title = "Nghệ sĩ",
                        subtitle = "${artists.size} nghệ sĩ",
                        covers = artists.mapNotNull { it.artist.thumbnailUrl },
                        circle = true,
                        tone = BentoTone.ARTIST,
                        coverSize = 60.dp,
                        coverStep = 22.dp,
                        coverAlignment = Alignment.TopEnd,
                        modifier = Modifier.weight(1f).height(156.dp),
                        onClick = { navController.navigate(Screens.Artists.route) }
                    )
                }
                CollectionCard(
                    title = "Album",
                    subtitle = "${likedAlbums.size} album",
                    covers = likedAlbums.mapNotNull { it.thumbnailUrl },
                    circle = false,
                    tone = BentoTone.ALBUM,
                    coverSize = 68.dp,
                    coverStep = 38.dp,
                    coverAlignment = Alignment.CenterEnd,
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                    onClick = { navController.navigate(Screens.Albums.route) }
                )
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
        }, span = { GridItemSpan(maxLineSpan) }) { index ->
            when (val item = recent[index]) {
                is Playlist -> RecentRow(
                    typeLabel = "Playlist",
                    title = item.playlist.name,
                    subtitle = pluralStringResource(R.plurals.n_song, item.songCount, item.songCount),
                    colors = playlistColors,
                    trailingIcon = R.drawable.navigate_next,
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
                    },
                    cover = {
                        PlaylistThumbnail(
                            thumbnails = item.displayThumbnails,
                            size = RecentCoverSize,
                            placeHolder = {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(RecentCoverSize)) {
                                    Icon(
                                        painter = painterResource(R.drawable.queue_music),
                                        contentDescription = null,
                                        tint = playlistColors.content,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                            },
                            shape = RoundedCornerShape(18.dp)
                        )
                    }
                )

                is Album -> RecentRow(
                    typeLabel = "Album",
                    title = item.album.title,
                    subtitle = item.artists.joinToString { it.name },
                    colors = albumColors,
                    trailingIcon = R.drawable.navigate_next,
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
                    },
                    cover = {
                        AsyncImage(
                            model = item.thumbnailUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                )

                is Song -> RecentRow(
                    typeLabel = "Bài hát",
                    title = item.song.title,
                    subtitle = item.artists.joinToString { it.name },
                    colors = songColors,
                    trailingIcon = if (item.id == mediaMetadata?.id && isPlaying) R.drawable.pause else R.drawable.play,
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
                    },
                    cover = {
                        AsyncImage(
                            model = item.thumbnailUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                )
            }
        }
    }
    }
}

/** One page of the library (liked songs, albums, playlists) as liquid-glass rows and cards. */
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
    val isGrid = viewType == LibraryViewType.GRID

    val colors = tileColors(
        when (section) {
            LibrarySection.SONGS -> BentoTone.SONG
            LibrarySection.ALBUMS -> BentoTone.ALBUM
            else -> BentoTone.PLAYLIST
        }
    )

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

    Box(modifier = Modifier.fillMaxSize().liquidBackdrop()) {
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(if (isGrid) 2 else 1),
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
                                GlassIconButton(
                                    icon = R.drawable.play,
                                    iconColor = colors.content,
                                    fill = colors.container,
                                    fillAlpha = 0.8f,
                                    size = 48.dp,
                                    contentDescription = stringResource(R.string.play),
                                    onClick = { playSongs(likedSongs, 0) }
                                )
                                GlassIconButton(
                                    icon = R.drawable.shuffle,
                                    iconColor = colors.content,
                                    fill = colors.container,
                                    fillAlpha = 0.8f,
                                    size = 48.dp,
                                    contentDescription = stringResource(R.string.shuffle),
                                    onClick = { playSongs(likedSongs.shuffled(), 0) }
                                )
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
                    colors = colors,
                    activeAlbumId = mediaMetadata?.album?.id,
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
                    colors = colors,
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
                    colors = colors,
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

/** Artists page: round glass avatars in a three-column grid, keeping the library/liked filter. */
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
    val colors = tileColors(BentoTone.ARTIST)

    Box(modifier = Modifier.fillMaxSize().liquidBackdrop()) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
            modifier = Modifier.fillMaxSize()
        ) {
            item(key = "header", span = { GridItemSpan(maxLineSpan) }) {
                SectionHeader(navController = navController, title = LibrarySection.ARTISTS.title)
            }
            item(key = "filter", span = { GridItemSpan(maxLineSpan) }) {
                GlassChipRow(
                    options = listOf(
                        ArtistFilter.LIBRARY to stringResource(R.string.filter_library),
                        ArtistFilter.LIKED to stringResource(R.string.filter_liked)
                    ),
                    selected = filter,
                    colors = colors,
                    onSelect = { filter = it }
                )
            }
            artists?.let { list ->
                if (list.isEmpty()) {
                    item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                        EmptyPlaceholder(
                            icon = R.drawable.artist,
                            text = stringResource(R.string.library_artist_empty)
                        )
                    }
                }
                items(items = list, key = { it.id }) { artist ->
                    ArtistBubble(
                        artist = artist,
                        colors = colors,
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
                }
            }
        }
    }
}

/** Round avatar on a glass ring with the name underneath. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ArtistBubble(
    artist: Artist,
    colors: TileColors,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .padding(horizontal = 8.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(20.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .liquidGlass(CircleShape, tint = colors.container, tintAlpha = 0.55f, elevation = 0.dp)
                .padding(4.dp)
        ) {
            AsyncImage(
                model = artist.artist.thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
            )
        }
        Spacer(Modifier.size(8.dp))
        Text(
            text = artist.artist.name,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (artist.songCount > 0) {
            Text(
                text = "${artist.songCount} bài",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

/** Left cell gets a wider left margin, right cell a wider right one, so the grid lines up with the 16dp page margin. */
private fun cellPadding(cell: Int) =
    if (cell % 2 == 0) PaddingValues(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp)
    else PaddingValues(start = 6.dp, end = 16.dp, top = 6.dp, bottom = 6.dp)

private val SectionCoverSize = 64.dp

@OptIn(ExperimentalFoundationApi::class)
private fun LazyGridScope.albumsContent(
    albums: List<Album>,
    isGrid: Boolean,
    colors: TileColors,
    activeAlbumId: String?,
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

    items(count = albums.size, key = { "album_${albums[it].id}" }) { index ->
        val album = albums[index]
        val artistNames = album.artists.joinToString { it.name }
        val subtitle = listOfNotNull(artistNames.ifEmpty { null }, album.album.year?.toString())
            .joinToString(" · ")
        val active = album.id == activeAlbumId
        val cover: @Composable () -> Unit = {
            AsyncImage(
                model = album.thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        if (isGrid) {
            GlassCoverCard(
                title = album.album.title,
                subtitle = subtitle,
                colors = colors,
                highlighted = active,
                onClick = { onOpen(album) },
                onLongClick = { onLongPress(album) },
                onMore = { onOptions(album) },
                modifier = Modifier.padding(cellPadding(index)),
                cover = cover
            )
        } else {
            GlassRow(
                title = album.album.title,
                subtitle = subtitle,
                colors = colors,
                coverSize = SectionCoverSize,
                highlighted = active,
                onClick = { onOpen(album) },
                onLongClick = { onLongPress(album) },
                trailing = {
                    GlassIconButton(
                        icon = R.drawable.more_vert,
                        iconColor = colors.content,
                        fill = colors.content,
                        fillAlpha = 0.12f,
                        onClick = { onOptions(album) }
                    )
                },
                cover = cover
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun LazyGridScope.songsContent(
    songs: List<Song>,
    isGrid: Boolean,
    colors: TileColors,
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

    items(count = songs.size, key = { "song_${songs[it].id}" }) { index ->
        val song = songs[index]
        val active = song.id == activeSongId
        val subtitle = song.artists.joinToString { it.name }
        val cover: @Composable () -> Unit = { SongCover(song.thumbnailUrl, active, isPlaying) }
        if (isGrid) {
            GlassCoverCard(
                title = song.song.title,
                subtitle = subtitle,
                colors = colors,
                highlighted = active,
                onClick = { onPlay(song, index) },
                onLongClick = { onLongPress(song) },
                onMore = { onOptions(song) },
                modifier = Modifier.padding(cellPadding(index)),
                cover = cover
            )
        } else {
            GlassRow(
                title = song.song.title,
                subtitle = subtitle,
                colors = colors,
                coverSize = SectionCoverSize,
                highlighted = active,
                onClick = { onPlay(song, index) },
                onLongClick = { onLongPress(song) },
                trailing = {
                    GlassIconButton(
                        icon = R.drawable.more_vert,
                        iconColor = colors.content,
                        fill = colors.content,
                        fillAlpha = 0.12f,
                        onClick = { onOptions(song) }
                    )
                },
                cover = cover
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun LazyGridScope.playlistsContent(
    playlists: List<Playlist>,
    isGrid: Boolean,
    colors: TileColors,
    onCreate: () -> Unit,
    onOpen: (Playlist) -> Unit,
    onOptions: (Playlist) -> Unit,
    onLongPress: (Playlist) -> Unit,
) {
    item(key = "create_playlist") {
        if (isGrid) {
            CreatePlaylistCard(
                colors = colors,
                onClick = onCreate,
                modifier = Modifier.padding(cellPadding(0))
            )
        } else {
            GlassRow(
                title = "Tạo playlist",
                subtitle = "Tạo danh sách phát mới",
                colors = colors,
                coverSize = SectionCoverSize,
                onClick = onCreate,
                onLongClick = {},
                cover = {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            painter = painterResource(R.drawable.add),
                            contentDescription = null,
                            tint = colors.content,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            )
        }
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

    items(count = playlists.size, key = { "playlist_${playlists[it].id}" }) { index ->
        val playlist = playlists[index]
        val subtitle = pluralStringResource(R.plurals.n_song, playlist.songCount, playlist.songCount)
        if (isGrid) {
            GlassCoverCard(
                title = playlist.playlist.name,
                subtitle = subtitle,
                colors = colors,
                onClick = { onOpen(playlist) },
                onLongClick = { onLongPress(playlist) },
                onMore = { onOptions(playlist) },
                // the "create" card takes the first cell, so playlists start one cell later
                modifier = Modifier.padding(cellPadding(index + 1)),
                cover = { PlaylistCardCover(playlist.displayThumbnails, colors) }
            )
        } else {
            GlassRow(
                title = playlist.playlist.name,
                subtitle = subtitle,
                colors = colors,
                coverSize = SectionCoverSize,
                onClick = { onOpen(playlist) },
                onLongClick = { onLongPress(playlist) },
                trailing = {
                    GlassIconButton(
                        icon = R.drawable.more_vert,
                        iconColor = colors.content,
                        fill = colors.content,
                        fillAlpha = 0.12f,
                        onClick = { onOptions(playlist) }
                    )
                },
                cover = {
                    PlaylistThumbnail(
                        thumbnails = playlist.displayThumbnails,
                        size = SectionCoverSize,
                        placeHolder = {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(SectionCoverSize)) {
                                Icon(
                                    painter = painterResource(R.drawable.queue_music),
                                    contentDescription = null,
                                    tint = colors.content,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        },
                        shape = RoundedCornerShape(0.dp)
                    )
                }
            )
        }
    }
}
