package com.caohaidang.edward.music.ui.screens.playlist

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.layout.ContentScale
import com.caohaidang.edward.music.constants.AppBarHeight
import com.caohaidang.edward.music.ui.component.GlassCircleButton
import com.caohaidang.edward.music.ui.component.ImmersiveActionRow
import com.caohaidang.edward.music.ui.component.ImmersiveCoverHeader
import com.caohaidang.edward.music.ui.component.ImmersiveTheme
import com.caohaidang.edward.music.ui.component.immersiveBackdrop
import com.caohaidang.edward.music.ui.component.immersiveTopBarColors
import com.caohaidang.edward.music.ui.component.rememberImageTint
import com.caohaidang.edward.music.ui.component.MinimalYouTubeListItem
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
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
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastAny
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.caohaidang.edward.innertube.models.SongItem
import com.caohaidang.edward.innertube.models.WatchEndpoint
import com.caohaidang.edward.music.LocalDatabase
import com.caohaidang.edward.music.LocalPlayerAwareWindowInsets
import com.caohaidang.edward.music.LocalPlayerConnection
import com.caohaidang.edward.music.R
import com.caohaidang.edward.music.constants.AlbumThumbnailSize
import com.caohaidang.edward.music.constants.HideExplicitKey
import com.caohaidang.edward.music.constants.ThumbnailCornerRadius
import com.caohaidang.edward.music.db.entities.PlaylistEntity
import com.caohaidang.edward.music.db.entities.PlaylistSongMap
import com.caohaidang.edward.music.extensions.togglePlayPause
import com.caohaidang.edward.music.models.toMediaMetadata
import com.caohaidang.edward.music.playback.queues.YouTubeQueue
import com.caohaidang.edward.music.ui.component.AutoResizeText
import com.caohaidang.edward.music.ui.component.FontSizeRange
import com.caohaidang.edward.music.ui.component.IconButton
import com.caohaidang.edward.music.ui.component.LocalMenuState
import com.caohaidang.edward.music.ui.component.YouTubeListItem
import com.caohaidang.edward.music.ui.component.shimmer.ButtonPlaceholder
import com.caohaidang.edward.music.ui.component.shimmer.ListItemPlaceHolder
import com.caohaidang.edward.music.ui.component.shimmer.ShimmerHost
import com.caohaidang.edward.music.ui.component.shimmer.TextPlaceholder
import com.caohaidang.edward.music.ui.menu.YouTubePlaylistMenu
import com.caohaidang.edward.music.ui.menu.YouTubeSongMenu
import com.caohaidang.edward.music.ui.menu.YouTubeSongSelectionMenu
import com.caohaidang.edward.music.ui.utils.backToMain
import com.caohaidang.edward.music.utils.rememberPreference
import com.caohaidang.edward.music.viewmodels.OnlinePlaylistViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun OnlinePlaylistScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: OnlinePlaylistViewModel = hiltViewModel(),
) {
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val menuState = LocalMenuState.current
    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()

    val playlist by viewModel.playlist.collectAsState()
    val songs by viewModel.playlistSongs.collectAsState()

    val hideExplicit by rememberPreference(key = HideExplicitKey, defaultValue = false)

    val lazyListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var isSearching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue())
    }
    val filteredSongs = remember(songs, query) {
        if (query.text.isEmpty()) songs.mapIndexed { index, song -> index to song }
        else songs
            .mapIndexed { index, song -> index to song }
            .filter { (_, song) ->
                song.title.contains(query.text, ignoreCase = true) ||
                        song.artists.fastAny { it.name.contains(query.text, ignoreCase = true) }
            }
    }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(isSearching) {
        if (isSearching) {
            focusRequester.requestFocus()
        }
    }
    if (isSearching) {
        BackHandler {
            isSearching = false
            query = TextFieldValue()
        }
    }

    val showTopBarTitle by remember {
        derivedStateOf {
            lazyListState.firstVisibleItemIndex > 0
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

    val tint = rememberImageTint(playlist?.thumbnail)
    val immersiveHeader = !isSearching && playlist != null

    Box(
        modifier = Modifier.fillMaxSize().immersiveBackdrop(tint)
    ) {
        ImmersiveTheme {
        LazyColumn(
            state = lazyListState,
            contentPadding = LocalPlayerAwareWindowInsets.current.union(WindowInsets.ime).let {
                if (immersiveHeader) it.add(WindowInsets(top = -WindowInsets.systemBars.asPaddingValues().calculateTopPadding() - AppBarHeight)) else it
            }.asPaddingValues()
        ) {
            playlist.let { playlist ->
                if (playlist != null) {
                    if (!isSearching) {
                        item(key = "header") {
                            ImmersiveCoverHeader(
                                title = playlist.title,
                                tint = tint,
                                subtitle = playlist.songCountText,
                                accentLine = playlist.author?.let { artist ->
                                    {
                                        Text(
                                            text = buildAnnotatedString {
                                                if (artist.id != null) {
                                                    val link = LinkAnnotation.Clickable(artist.id!!) {
                                                        navController.navigate("artist/${artist.id!!}")
                                                    }
                                                    withLink(link) { append(artist.name) }
                                                } else {
                                                    append(artist.name)
                                                }
                                            },
                                            style = MaterialTheme.typography.titleLarge,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                },
                                cover = {
                                    AsyncImage(
                                        model = playlist.thumbnail,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                },
                                actions = {
                                    ImmersiveActionRow(
                                        playText = stringResource(R.string.play),
                                        onPlay = {
                                            playerConnection.playQueue(
                                                YouTubeQueue(playlist.playEndpoint ?: playlist.shuffleEndpoint)
                                            )
                                        },
                                        onShuffle = {
                                            playerConnection.playQueue(YouTubeQueue(playlist.shuffleEndpoint))
                                        },
                                        trailingIcon = R.drawable.input,
                                        onToggleLike = {
                                            database.transaction {
                                                val playlistEntity = PlaylistEntity(
                                                    name = playlist.title,
                                                    browseId = playlist.id
                                                )
                                                insert(playlistEntity)
                                                songs.map(SongItem::toMediaMetadata)
                                                    .onEach(::insert)
                                                    .mapIndexed { index, song ->
                                                        PlaylistSongMap(
                                                            songId = song.id,
                                                            playlistId = playlistEntity.id,
                                                            position = index
                                                        )
                                                    }
                                                    .forEach(::insert)
                                                coroutineScope.launch {
                                                    snackbarHostState.showSnackbar(context.getString(R.string.playlist_imported))
                                                }
                                            }
                                        },
                                    )
                                },
                                secondaryActions = playlist.radioEndpoint?.let { radioEndpoint ->
                                    {
                                        GlassCircleButton(
                                            icon = R.drawable.radio,
                                            onClick = { playerConnection.playQueue(YouTubeQueue(radioEndpoint)) },
                                        )
                                    }
                                },
                                modifier = Modifier.animateItem()
                            )
                        }
                    }

                    items(
                        items = filteredSongs,
                        key = { (index, _) -> index }
                    ) { (index, song) ->
                        val onCheckedChange: (Boolean) -> Unit = {
                            if (it) {
                                selection.add(index)
                            } else {
                                selection.remove(index)
                            }
                        }

                        MinimalYouTubeListItem(
                            item = song,
                            isActive = mediaMetadata?.id == song.id,
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
                                                YouTubeSongMenu(
                                                    song = song,
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
                                .combinedClickable(
                                    enabled = !hideExplicit || !song.explicit,
                                    onClick = {
                                        if (inSelectMode) {
                                            onCheckedChange(index !in selection)
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
                                    }
                                )
                                .alpha(if (hideExplicit && song.explicit) 0.3f else 1f)
                                .animateItem()
                        )
                    }
                } else {
                    item {
                        ShimmerHost {
                            Column(Modifier.padding(12.dp)) {
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
                } else if (isSearching) {
                    TextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = {
                            Text(
                                text = stringResource(R.string.search),
                                style = MaterialTheme.typography.titleLarge
                            )
                        },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.titleLarge,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                    )
                } else if (showTopBarTitle) {
                    Text(playlist?.title.orEmpty())
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
                        onClick = {
                            if (isSearching) {
                                isSearching = false
                                query = TextFieldValue()
                            } else {
                                navController.navigateUp()
                            }
                        },
                        onLongClick = {
                            if (!isSearching) {
                                navController.backToMain()
                            }
                        }
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
                        checked = selection.size == songs.size,
                        onCheckedChange = {
                            if (selection.size == songs.size) {
                                selection.clear()
                            } else {
                                selection.clear()
                                selection.addAll(songs.mapIndexedNotNull { index, song ->
                                    if (hideExplicit && song.explicit) null
                                    else index
                                })
                            }
                        }
                    )
                    IconButton(
                        enabled = selection.isNotEmpty(),
                        onClick = {
                            menuState.show {
                                YouTubeSongSelectionMenu(
                                    selection = selection.mapNotNull { songs.getOrNull(it) },
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
                } else if (!isSearching) {
                    IconButton(
                        onClick = { isSearching = true }
                    ) {
                        Icon(
                            painterResource(R.drawable.search),
                            contentDescription = null
                        )
                    }
                    playlist?.let { playlist ->
                        IconButton(
                            onClick = {
                                menuState.show {
                                    YouTubePlaylistMenu(
                                        playlist = playlist,
                                        songs = songs,
                                        coroutineScope = coroutineScope,
                                        onDismiss = menuState::dismiss
                                    )
                                }
                            }
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.more_vert),
                                contentDescription = null
                            )
                        }
                    }
                }
            },
            scrollBehavior = scrollBehavior,
            colors = if (isSearching || playlist == null) TopAppBarDefaults.topAppBarColors()
            else immersiveTopBarColors(tint, solid = showTopBarTitle || inSelectMode)
        )

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.union(WindowInsets.ime))
                .align(Alignment.BottomCenter)
        )
    }
}
