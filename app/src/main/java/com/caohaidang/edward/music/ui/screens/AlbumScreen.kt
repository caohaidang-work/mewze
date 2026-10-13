package com.caohaidang.edward.music.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.graphics.lerp
import com.caohaidang.edward.music.ui.component.ImmersiveTheme
import com.caohaidang.edward.music.ui.utils.fadingEdge
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import com.caohaidang.edward.music.constants.AppBarHeight
import com.caohaidang.edward.music.ui.component.ImmersiveActionRow
import com.caohaidang.edward.music.ui.component.immersiveBackdrop
import com.caohaidang.edward.music.ui.component.immersiveTopBarColors
import com.caohaidang.edward.music.ui.component.rememberImageTint
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import com.caohaidang.edward.music.ui.component.MinimalSongListItem
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastForEachIndexed
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.caohaidang.edward.music.LocalDatabase
import com.caohaidang.edward.music.LocalDownloadUtil
import com.caohaidang.edward.music.LocalPlayerAwareWindowInsets
import com.caohaidang.edward.music.LocalPlayerConnection
import com.caohaidang.edward.music.R
import com.caohaidang.edward.music.constants.AlbumThumbnailSize
import com.caohaidang.edward.music.constants.ThumbnailCornerRadius
import com.caohaidang.edward.music.db.entities.Album
import com.caohaidang.edward.music.extensions.togglePlayPause
import com.caohaidang.edward.music.playback.ExoDownloadService
import com.caohaidang.edward.music.playback.queues.LocalAlbumRadio
import com.caohaidang.edward.music.ui.component.AutoResizeText
import com.caohaidang.edward.music.ui.component.FontSizeRange
import com.caohaidang.edward.music.ui.component.IconButton
import com.caohaidang.edward.music.ui.component.LocalMenuState
import com.caohaidang.edward.music.ui.component.NavigationTitle
import com.caohaidang.edward.music.ui.component.SongListItem
import com.caohaidang.edward.music.ui.component.YouTubeGridItem
import com.caohaidang.edward.music.ui.component.shimmer.ButtonPlaceholder
import com.caohaidang.edward.music.ui.component.shimmer.ListItemPlaceHolder
import com.caohaidang.edward.music.ui.component.shimmer.ShimmerHost
import com.caohaidang.edward.music.ui.component.shimmer.TextPlaceholder
import com.caohaidang.edward.music.ui.menu.AlbumMenu
import com.caohaidang.edward.music.ui.menu.SongMenu
import com.caohaidang.edward.music.ui.menu.SongSelectionMenu
import com.caohaidang.edward.music.ui.menu.YouTubeAlbumMenu
import com.caohaidang.edward.music.ui.utils.backToMain
import com.caohaidang.edward.music.viewmodels.AlbumViewModel

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AlbumScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: AlbumViewModel = hiltViewModel(),
) {
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val menuState = LocalMenuState.current
    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current ?: return

    val scope = rememberCoroutineScope()

    val isPlaying by playerConnection.isPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()

    val albumWithSongs by viewModel.albumWithSongs.collectAsState()
    val otherVersions by viewModel.otherVersions.collectAsState()

    val downloadUtil = LocalDownloadUtil.current
    var downloadState by remember {
        mutableIntStateOf(Download.STATE_STOPPED)
    }

    val state = rememberLazyListState()
    val showTopBarTitle by remember {
        derivedStateOf {
            state.firstVisibleItemIndex > 0
        }
    }

    var inSelectMode by rememberSaveable { mutableStateOf(false) }
    val selection = rememberSaveable(
        saver = listSaver<MutableList<Int>, Int>(
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

    LaunchedEffect(albumWithSongs) {
        val songs = albumWithSongs?.songs?.map { it.id }
        if (songs.isNullOrEmpty()) return@LaunchedEffect
        downloadUtil.downloads.collect { downloads ->
            downloadState =
                if (songs.all { downloads[it]?.state == Download.STATE_COMPLETED })
                    Download.STATE_COMPLETED
                else if (songs.all {
                        downloads[it]?.state == Download.STATE_QUEUED
                                || downloads[it]?.state == Download.STATE_DOWNLOADING
                                || downloads[it]?.state == Download.STATE_COMPLETED
                    })
                    Download.STATE_DOWNLOADING
                else
                    Download.STATE_STOPPED
        }
    }

    val tint = rememberImageTint(albumWithSongs?.album?.thumbnailUrl)

    Box(Modifier.fillMaxSize().immersiveBackdrop(tint)) {
    ImmersiveTheme {
    LazyColumn(
        state = state,
        contentPadding = LocalPlayerAwareWindowInsets.current
            .add(WindowInsets(top = -WindowInsets.systemBars.asPaddingValues().calculateTopPadding() - AppBarHeight))
            .asPaddingValues()
    ) {
        val albumWithSongs = albumWithSongs
        if (albumWithSongs != null && albumWithSongs.songs.isNotEmpty()) {
            item(key = "header") {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val statusTop = WindowInsets.systemBars.asPaddingValues().calculateTopPadding()
                    AsyncImage(
                        model = albumWithSongs.album.thumbnailUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .fadingEdge(top = statusTop + AppBarHeight, bottom = 96.dp)
                    )

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .offset(y = (-40).dp)
                            .padding(horizontal = 20.dp)
                    ) {
                    Text(
                        text = albumWithSongs.album.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )

                    Text(
                        text = buildAnnotatedString {
                            albumWithSongs.artists.fastForEachIndexed { index, artist ->
                                val link = LinkAnnotation.Clickable(artist.id) {
                                    navController.navigate("artist/${artist.id}")
                                }
                                withLink(link) { append(artist.name) }
                                if (index != albumWithSongs.artists.lastIndex) append(", ")
                            }
                        },
                        style = MaterialTheme.typography.titleLarge.copy(
                            color = lerp(tint ?: Color.White, Color.White, 0.65f),
                            fontWeight = FontWeight.Normal,
                            textAlign = TextAlign.Center,
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )

                    val subtitle = listOfNotNull(
                        albumWithSongs.album.year?.toString(),
                        pluralStringResource(
                            R.plurals.n_song,
                            albumWithSongs.songs.size,
                            albumWithSongs.songs.size
                        ),
                    ).joinToString(" · ")
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.padding(top = 4.dp)
                    )

                    Spacer(Modifier.height(20.dp))

                    ImmersiveActionRow(
                        playText = stringResource(R.string.play),
                        onPlay = { playerConnection.playQueue(LocalAlbumRadio(albumWithSongs)) },
                        onShuffle = {
                            playerConnection.playQueue(
                                LocalAlbumRadio(albumWithSongs.copy(songs = albumWithSongs.songs.shuffled()))
                            )
                        },
                        liked = albumWithSongs.album.bookmarkedAt != null,
                        onToggleLike = {
                            database.query { update(albumWithSongs.album.toggleLike()) }
                        },
                    )
                    }
                }
            }

            itemsIndexed(
                items = albumWithSongs.songs,
                key = { _, song -> song.id }
            ) { index, song ->
                val onCheckedChange: (Boolean) -> Unit = {
                    if (it) {
                        selection.add(index)
                    } else {
                        selection.remove(index)
                    }
                }

                MinimalSongListItem(
                    song = song,
                    albumIndex = index + 1,
                    isActive = song.id == mediaMetadata?.id,
                    isPlaying = isPlaying,
                    trailingContent = {
                        if (inSelectMode) {
                            Checkbox(
                                checked = index in selection,
                                onCheckedChange = onCheckedChange
                            )
                        } else {
                            IconButton(
                                onClick = {
                                    menuState.show {
                                        SongMenu(
                                            originalSong = song,
                                            navController = navController,
                                            onDismiss = menuState::dismiss
                                        )
                                    }
                                }
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.more_horiz),
                                    contentDescription = null
                                )
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = {
                                if (inSelectMode) {
                                    onCheckedChange(index !in selection)
                                } else if (song.id == mediaMetadata?.id) {
                                    playerConnection.player.togglePlayPause()
                                } else {
                                    playerConnection.playQueue(
                                        LocalAlbumRadio(albumWithSongs, startIndex = index)
                                    )
                                }
                            },
                            onLongClick = {
                                if (!inSelectMode) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    inSelectMode = true
                                    onCheckedChange(true)
                                }
                            }
                        )
                )
            }

            if (otherVersions.isNotEmpty()) {
                item {
                    NavigationTitle(
                        title = stringResource(R.string.other_versions),
                    )
                }
                item {
                    LazyRow {
                        items(
                            items = otherVersions,
                            key = { it.id },
                        ) { item ->
                            YouTubeGridItem(
                                item = item,
                                isActive = mediaMetadata?.album?.id == item.id,
                                isPlaying = isPlaying,
                                coroutineScope = scope,
                                modifier =
                                Modifier
                                    .combinedClickable(
                                        onClick = { navController.navigate("album/${item.id}") },
                                        onLongClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            menuState.show {
                                                YouTubeAlbumMenu(
                                                    albumItem = item,
                                                    navController = navController,
                                                    onDismiss = menuState::dismiss,
                                                )
                                            }
                                        },
                                    )
                                    .animateItem(),
                            )
                        }
                    }
                }
            }
        } else {
            item {
                ShimmerHost {
                    Column(Modifier.padding(12.dp).padding(top = WindowInsets.systemBars.asPaddingValues().calculateTopPadding() + AppBarHeight)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Spacer(
                                modifier = Modifier
                                    .size(AlbumThumbnailSize)
                                    .clip(RoundedCornerShape(ThumbnailCornerRadius))
                                    .background(MaterialTheme.colorScheme.onSurface)
                            )

                            Spacer(Modifier.width(16.dp))

                            Column(
                                verticalArrangement = Arrangement.Center,
                            ) {
                                TextPlaceholder()
                                TextPlaceholder()
                                TextPlaceholder()
                            }
                        }

                        Spacer(Modifier.padding(8.dp))

                        Row {
                            ButtonPlaceholder(Modifier.weight(1f))

                            Spacer(Modifier.width(12.dp))

                            ButtonPlaceholder(Modifier.weight(1f))
                        }
                    }

                    repeat(6) {
                        ListItemPlaceHolder()
                    }
                }
            }
        }
    }
    }
    }

    TopAppBar(
        title = {
            if (inSelectMode) {
                Text(pluralStringResource(R.plurals.n_selected, selection.size, selection.size))
            } else if (showTopBarTitle) {
                Text(albumWithSongs?.album?.title.orEmpty())
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
            val album = albumWithSongs
            if (!inSelectMode && album != null && album.songs.isNotEmpty()) {
                when (downloadState) {
                    Download.STATE_COMPLETED -> IconButton(
                        onClick = {
                            album.songs.forEach { song ->
                                DownloadService.sendRemoveDownload(
                                    context, ExoDownloadService::class.java, song.id, false
                                )
                            }
                        }
                    ) {
                        Icon(painter = painterResource(R.drawable.offline), contentDescription = null)
                    }

                    Download.STATE_DOWNLOADING -> IconButton(
                        onClick = {
                            album.songs.forEach { song ->
                                DownloadService.sendRemoveDownload(
                                    context, ExoDownloadService::class.java, song.id, false
                                )
                            }
                        }
                    ) {
                        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                    }

                    else -> IconButton(
                        onClick = {
                            album.songs.forEach { song ->
                                val downloadRequest = DownloadRequest.Builder(song.id, song.id.toUri())
                                    .setCustomCacheKey(song.id)
                                    .setData(song.song.title.toByteArray())
                                    .build()
                                DownloadService.sendAddDownload(
                                    context, ExoDownloadService::class.java, downloadRequest, false
                                )
                            }
                        }
                    ) {
                        Icon(painter = painterResource(R.drawable.download), contentDescription = null)
                    }
                }

                IconButton(
                    onClick = {
                        menuState.show {
                            AlbumMenu(
                                originalAlbum = Album(album.album, album.artists),
                                navController = navController,
                                onDismiss = menuState::dismiss
                            )
                        }
                    }
                ) {
                    Icon(painter = painterResource(R.drawable.more_vert), contentDescription = null)
                }
            }
            if (inSelectMode) {
                Checkbox(
                    checked = selection.size == albumWithSongs?.songs?.size,
                    onCheckedChange = {
                        albumWithSongs?.let { albumWithSongs ->
                            if (selection.size == albumWithSongs.songs.size) {
                                selection.clear()
                            } else {
                                selection.clear()
                                selection.addAll(albumWithSongs.songs.indices)
                            }
                        }
                    }
                )
                IconButton(
                    enabled = selection.isNotEmpty(),
                    onClick = {
                        menuState.show {
                            SongSelectionMenu(
                                selection = selection.mapNotNull { index ->
                                    albumWithSongs?.songs?.getOrNull(index)
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
        scrollBehavior = scrollBehavior,
        colors = immersiveTopBarColors(tint, solid = showTopBarTitle || inSelectMode)
    )
}
