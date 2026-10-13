package com.caohaidang.edward.music.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.media3.exoplayer.offline.Download
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.caohaidang.edward.music.LocalDatabase
import com.caohaidang.edward.music.LocalDownloadUtil
import com.caohaidang.edward.music.LocalPlayerAwareWindowInsets
import com.caohaidang.edward.music.LocalPlayerConnection
import com.caohaidang.edward.music.R
import com.caohaidang.edward.music.db.entities.Song
import com.caohaidang.edward.music.extensions.togglePlayPause
import com.caohaidang.edward.music.extensions.toMediaItem
import com.caohaidang.edward.music.playback.queues.ListQueue
import com.caohaidang.edward.music.ui.component.EmptyPlaceholder
import com.caohaidang.edward.music.ui.component.GlassPillButton
import com.caohaidang.edward.music.ui.component.GlassSectionHeader
import com.caohaidang.edward.music.ui.component.GlassTile
import com.caohaidang.edward.music.ui.component.LocalMenuState
import com.caohaidang.edward.music.ui.component.glassCard
import com.caohaidang.edward.music.ui.menu.SongMenu

@Composable
fun DownloadedScreen(
    navController: NavController,
) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val menuState = LocalMenuState.current
    val downloadUtil = LocalDownloadUtil.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()

    val downloads by downloadUtil.downloads.collectAsState()
    val allSongs by remember(database) { database.allSongs() }.collectAsState(initial = emptyList())

    var query by rememberSaveable { mutableStateOf("") }

    val downloaded = remember(downloads, allSongs) {
        allSongs
            .filter { downloads[it.id]?.state == Download.STATE_COMPLETED }
            .sortedByDescending { downloads[it.id]?.updateTimeMs ?: 0L }
    }
    val inProgress = remember(downloads, allSongs) {
        allSongs.filter {
            when (downloads[it.id]?.state) {
                Download.STATE_QUEUED, Download.STATE_DOWNLOADING, Download.STATE_RESTARTING -> true
                else -> false
            }
        }
    }
    val shown = remember(downloaded, query) {
        val q = query.trim()
        if (q.isEmpty()) downloaded
        else downloaded.filter { song ->
            song.song.title.contains(q, ignoreCase = true) ||
                    song.artists.any { it.name.contains(q, ignoreCase = true) }
        }
    }

    val lazyListState = rememberLazyListState()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val scrollToTop = backStackEntry?.savedStateHandle?.getStateFlow("scrollToTop", false)?.collectAsState()
    LaunchedEffect(scrollToTop?.value) {
        if (scrollToTop?.value == true) {
            lazyListState.animateScrollToItem(0)
            backStackEntry?.savedStateHandle?.set("scrollToTop", false)
        }
    }

    val playSongs: (List<Song>, Int) -> Unit = { songs, startIndex ->
        playerConnection.playQueue(
            ListQueue(
                title = "Đã tải xuống",
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

    LazyColumn(
        state = lazyListState,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
        modifier = Modifier.fillMaxSize()
    ) {
        item(key = "title") {
            Text(
                text = "Đã tải xuống",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp)
            )
        }

        item(key = "summary") {
            val inProgressText = if (inProgress.isNotEmpty()) " • đang tải ${inProgress.size}" else ""
            Text(
                text = "${downloaded.size} bài hát nghe ngoại tuyến$inProgressText",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
        }

        if (downloaded.isNotEmpty()) {
            item(key = "actions") {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    GlassPillButton(
                        text = "Phát",
                        icon = R.drawable.play,
                        highlighted = true,
                        onClick = { playSongs(shown.ifEmpty { downloaded }, 0) }
                    )
                    GlassPillButton(
                        text = "Ngẫu nhiên",
                        icon = R.drawable.shuffle,
                        onClick = { playSongs(shown.ifEmpty { downloaded }.shuffled(), 0) }
                    )
                }
            }

            item(key = "search") {
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    placeholder = { Text("Tìm trong bài đã tải…") },
                    leadingIcon = {
                        Icon(painter = painterResource(R.drawable.search), contentDescription = null)
                    },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(painter = painterResource(R.drawable.close), contentDescription = null)
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    shape = RoundedCornerShape(28.dp),
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                        errorIndicatorColor = Color.Transparent,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .glassCard(RoundedCornerShape(28.dp), tintAlpha = 0.60f)
                )
            }
        }

        if (inProgress.isNotEmpty()) {
            item(key = "h_progress") { GlassSectionHeader(title = "Đang tải") }
            items(inProgress, key = { "progress_${it.id}" }) { song ->
                val percent = downloads[song.id]?.percentDownloaded ?: -1f
                GlassTile(
                    title = song.song.title,
                    subtitle = if (percent >= 0f) "Đang tải… ${percent.toInt()}%" else "Đang chờ…",
                    thumbnailUrl = song.song.thumbnailUrl,
                    onClick = {},
                    onLongClick = { showSongMenu(song) },
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth()
                )
            }
        }

        if (downloaded.isEmpty() && inProgress.isEmpty()) {
            item(key = "empty") {
                EmptyPlaceholder(
                    icon = R.drawable.download,
                    text = "Bài hát bạn tải xuống để nghe ngoại tuyến sẽ hiện ở đây.\nMở menu ⋮ của bài hát và chọn Tải xuống."
                )
            }
        } else {
            items(shown, key = { "downloaded_${it.id}" }) { song ->
                GlassTile(
                    title = song.song.title,
                    subtitle = song.artists.joinToString { it.name }.ifEmpty { null },
                    thumbnailUrl = song.song.thumbnailUrl,
                    isActive = song.id == mediaMetadata?.id,
                    onClick = {
                        if (song.id == mediaMetadata?.id) {
                            playerConnection.player.togglePlayPause()
                        } else {
                            playSongs(shown, shown.indexOf(song).coerceAtLeast(0))
                        }
                    },
                    onLongClick = { showSongMenu(song) },
                    trailingContent = {
                        IconButton(onClick = { showSongMenu(song) }) {
                            Icon(
                                painter = painterResource(R.drawable.more_vert),
                                contentDescription = null
                            )
                        }
                    },
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth()
                )
            }
        }
    }
}
