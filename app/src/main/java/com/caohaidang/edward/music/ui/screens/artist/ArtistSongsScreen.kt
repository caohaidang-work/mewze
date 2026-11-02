package com.caohaidang.edward.music.ui.screens.artist

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEachReversed
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.caohaidang.edward.music.LocalPlayerAwareWindowInsets
import com.caohaidang.edward.music.LocalPlayerConnection
import com.caohaidang.edward.music.R
import com.caohaidang.edward.music.constants.ArtistSongSortDescendingKey
import com.caohaidang.edward.music.constants.ArtistSongSortType
import com.caohaidang.edward.music.constants.ArtistSongSortTypeKey
import com.caohaidang.edward.music.constants.CONTENT_TYPE_HEADER
import com.caohaidang.edward.music.db.entities.Song
import com.caohaidang.edward.music.extensions.toMediaItem
import com.caohaidang.edward.music.extensions.togglePlayPause
import com.caohaidang.edward.music.playback.queues.ListQueue
import com.caohaidang.edward.music.ui.component.HideOnScrollFAB
import com.caohaidang.edward.music.ui.component.IconButton
import com.caohaidang.edward.music.ui.component.LocalMenuState
import com.caohaidang.edward.music.ui.component.liquidBackdrop
import com.caohaidang.edward.music.ui.screens.library.BentoTone
import com.caohaidang.edward.music.ui.screens.library.GlassIconButton
import com.caohaidang.edward.music.ui.screens.library.tileColors
import com.caohaidang.edward.music.utils.joinByBullet
import com.caohaidang.edward.music.utils.makeTimeString
import com.caohaidang.edward.music.ui.component.SortHeader
import com.caohaidang.edward.music.ui.menu.SongMenu
import com.caohaidang.edward.music.ui.menu.SongSelectionMenu
import com.caohaidang.edward.music.ui.utils.backToMain
import com.caohaidang.edward.music.utils.rememberEnumPreference
import com.caohaidang.edward.music.utils.rememberPreference
import com.caohaidang.edward.music.viewmodels.ArtistSongsViewModel

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ArtistSongsScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: ArtistSongsViewModel = hiltViewModel(),
) {
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val menuState = LocalMenuState.current
    val playerConnection = LocalPlayerConnection.current ?: return

    val isPlaying by playerConnection.isPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()

    val (sortType, onSortTypeChange) = rememberEnumPreference(ArtistSongSortTypeKey, ArtistSongSortType.CREATE_DATE)
    val (sortDescending, onSortDescendingChange) = rememberPreference(ArtistSongSortDescendingKey, true)

    val artist by viewModel.artist.collectAsState()
    val songs by viewModel.songs.collectAsState()
    val songIndex: Map<String, Song> by remember(songs) {
        derivedStateOf {
            songs.associateBy { it.id }
        }
    }

    val lazyListState = rememberLazyListState()

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

    LaunchedEffect(songIndex) {
        selection.fastForEachReversed { songId ->
            if (songIndex[songId] == null) {
                selection.remove(songId)
            }
        }
    }

    val colors = tileColors(BentoTone.SONG)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .liquidBackdrop()
    ) {
        LazyColumn(
            state = lazyListState,
            contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues()
        ) {
            item(
                key = "header",
                contentType = CONTENT_TYPE_HEADER
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    SortHeader(
                        sortType = sortType,
                        sortDescending = sortDescending,
                        onSortTypeChange = onSortTypeChange,
                        onSortDescendingChange = onSortDescendingChange,
                        sortTypeText = { sortType ->
                            when (sortType) {
                                ArtistSongSortType.CREATE_DATE -> R.string.sort_by_create_date
                                ArtistSongSortType.NAME -> R.string.sort_by_name
                                ArtistSongSortType.PLAY_TIME -> R.string.sort_by_play_time
                            }
                        }
                    )

                    Spacer(Modifier.weight(1f))

                    Text(
                        text = pluralStringResource(R.plurals.n_song, songs.size, songs.size),
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.content
                    )
                }
            }

            itemsIndexed(
                items = songs,
                key = { _, item -> item.id }
            ) { index, song ->
                val onCheckedChange: (Boolean) -> Unit = {
                    if (it) {
                        selection.add(song.id)
                    } else {
                        selection.remove(song.id)
                    }
                }

                val showMenu = {
                    menuState.show {
                        SongMenu(
                            originalSong = song,
                            navController = navController,
                            onDismiss = menuState::dismiss
                        )
                    }
                }

                GlassPageSongRow(
                    title = song.song.title,
                    subtitle = joinByBullet(
                        song.artists.joinToString { it.name },
                        makeTimeString(song.song.duration * 1000L)
                    ).orEmpty(),
                    thumbnailUrl = song.song.thumbnailUrl,
                    colors = colors,
                    active = song.id == mediaMetadata?.id,
                    playing = isPlaying,
                    selected = inSelectMode && song.id in selection,
                    onClick = {
                        if (inSelectMode) {
                            onCheckedChange(song.id !in selection)
                        } else if (song.id == mediaMetadata?.id) {
                            playerConnection.player.togglePlayPause()
                        } else {
                            playerConnection.playQueue(
                                ListQueue(
                                    title = context.getString(R.string.queue_all_songs),
                                    items = songs.map { it.toMediaItem() },
                                    startIndex = index
                                )
                            )
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
                                iconColor = colors.content,
                                fill = colors.content,
                                fillAlpha = 0.12f,
                                onClick = { showMenu() }
                            )
                        }
                    },
                    modifier = Modifier.animateItem()
                )
            }
        }

        TopAppBar(
            title = {
                if (inSelectMode) {
                    Text(pluralStringResource(R.plurals.n_selected, selection.size, selection.size))
                } else {
                    Text(artist?.artist?.name.orEmpty())
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
                        checked = selection.size == songs.size && selection.isNotEmpty(),
                        onCheckedChange = {
                            if (selection.size == songs.size) {
                                selection.clear()
                            } else {
                                selection.clear()
                                selection.addAll(songs.map { it.id })
                            }
                        }
                    )
                    IconButton(
                        enabled = selection.isNotEmpty(),
                        onClick = {
                            menuState.show {
                                SongSelectionMenu(
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

        HideOnScrollFAB(
            lazyListState = lazyListState,
            icon = R.drawable.shuffle,
            onClick = {
                playerConnection.playQueue(
                    ListQueue(
                        title = artist?.artist?.name,
                        items = songs.shuffled().map { it.toMediaItem() },
                    )
                )
            }
        )
    }
}
