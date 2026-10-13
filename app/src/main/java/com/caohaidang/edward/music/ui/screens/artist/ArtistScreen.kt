package com.caohaidang.edward.music.ui.screens.artist

import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import com.caohaidang.edward.music.ui.component.GlassCircleButton
import com.caohaidang.edward.music.ui.component.ImmersiveCircleActionRow
import com.caohaidang.edward.music.ui.component.MinimalSongListItem
import com.caohaidang.edward.music.ui.component.MinimalYouTubeListItem
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastForEach
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.valentinilk.shimmer.shimmer
import com.caohaidang.edward.innertube.models.AlbumItem
import com.caohaidang.edward.innertube.models.ArtistItem
import com.caohaidang.edward.innertube.models.PlaylistItem
import com.caohaidang.edward.innertube.models.SongItem
import com.caohaidang.edward.music.LocalDatabase
import com.caohaidang.edward.music.LocalPlayerAwareWindowInsets
import com.caohaidang.edward.music.LocalPlayerConnection
import com.caohaidang.edward.music.R
import com.caohaidang.edward.music.constants.AppBarHeight
import com.caohaidang.edward.music.db.entities.ArtistEntity
import com.caohaidang.edward.music.extensions.togglePlayPause
import com.caohaidang.edward.music.models.toMediaMetadata
import com.caohaidang.edward.music.playback.queues.YouTubeQueue
import com.caohaidang.edward.music.ui.component.AutoResizeText
import com.caohaidang.edward.music.ui.component.FontSizeRange
import com.caohaidang.edward.music.ui.component.IconButton
import com.caohaidang.edward.music.ui.component.ImmersiveActionRow
import com.caohaidang.edward.music.ui.component.ImmersiveTheme
import com.caohaidang.edward.music.ui.component.immersiveBackdrop
import com.caohaidang.edward.music.ui.component.immersiveTopBarColors
import com.caohaidang.edward.music.ui.component.rememberImageTint
import com.caohaidang.edward.music.ui.component.LocalMenuState
import com.caohaidang.edward.music.ui.component.NavigationTitle
import com.caohaidang.edward.music.ui.component.SongListItem
import com.caohaidang.edward.music.ui.component.YouTubeGridItem
import com.caohaidang.edward.music.ui.component.YouTubeListItem
import com.caohaidang.edward.music.ui.component.shimmer.ButtonPlaceholder
import com.caohaidang.edward.music.ui.component.shimmer.ListItemPlaceHolder
import com.caohaidang.edward.music.ui.component.shimmer.ShimmerHost
import com.caohaidang.edward.music.ui.component.shimmer.TextPlaceholder
import com.caohaidang.edward.music.ui.menu.SongMenu
import com.caohaidang.edward.music.ui.menu.YouTubeAlbumMenu
import com.caohaidang.edward.music.ui.menu.YouTubeArtistMenu
import com.caohaidang.edward.music.ui.menu.YouTubePlaylistMenu
import com.caohaidang.edward.music.ui.menu.YouTubeSongMenu
import com.caohaidang.edward.music.ui.utils.backToMain
import com.caohaidang.edward.music.ui.utils.fadingEdge
import com.caohaidang.edward.music.ui.utils.resize
import com.caohaidang.edward.music.viewmodels.ArtistViewModel
import java.time.LocalDateTime

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ArtistScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: ArtistViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()

    val artistPage = viewModel.artistPage
    val libraryArtist by viewModel.libraryArtist.collectAsState()
    val librarySongs by viewModel.librarySongs.collectAsState()

    val lazyListState = rememberLazyListState()

    val transparentAppBar by remember {
        derivedStateOf {
            lazyListState.firstVisibleItemIndex <= 1
        }
    }

    val heroUrl = viewModel.freshImageUrl ?: artistPage?.artist?.thumbnail?.resize(1200, 900)
    val tint = rememberImageTint(heroUrl)

    Box(Modifier.fillMaxSize().immersiveBackdrop(tint)) {
    ImmersiveTheme {
    LazyColumn(
        state = lazyListState,
        contentPadding = LocalPlayerAwareWindowInsets.current
            .add(WindowInsets(top = -WindowInsets.systemBars.asPaddingValues().calculateTopPadding() - AppBarHeight))
            .asPaddingValues()
    ) {
        artistPage.let {
            if (artistPage != null) {
                item(key = "header") {
                    Column {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                        ) {
                            AsyncImage(
                                model = heroUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .matchParentSize()
                                    .fadingEdge(
                                        top = WindowInsets.systemBars
                                            .asPaddingValues()
                                            .calculateTopPadding() + AppBarHeight,
                                        bottom = 150.dp
                                    )
                            )
                            AutoResizeText(
                                text = artistPage.artist.title,
                                style = MaterialTheme.typography.displayLarge,
                                fontSizeRange = FontSizeRange(34.sp, 52.sp),
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(horizontal = 32.dp)
                            )
                        }

                        val artistLiked = libraryArtist?.artist?.bookmarkedAt != null
                        ImmersiveCircleActionRow(
                            leftIcon = R.drawable.shuffle,
                            onLeft = artistPage.artist.shuffleEndpoint?.let { endpoint ->
                                { playerConnection.playQueue(YouTubeQueue(endpoint)) }
                            },
                            onPlay = {
                                (artistPage.artist.radioEndpoint ?: artistPage.artist.shuffleEndpoint)
                                    ?.let { playerConnection.playQueue(YouTubeQueue(it)) }
                            },
                            rightIcon = if (artistLiked) R.drawable.favorite else R.drawable.favorite_border,
                            rightTint = if (artistLiked) Color(0xFFFF5A6E) else Color.White,
                            onRight = {
                                database.transaction {
                                    val artist = libraryArtist?.artist
                                    if (artist != null) {
                                        update(artist.toggleLike())
                                    } else {
                                        artistPage.artist.let {
                                            insert(
                                                ArtistEntity(
                                                    id = it.id,
                                                    name = it.title,
                                                    thumbnailUrl = it.thumbnail,
                                                    bookmarkedAt = LocalDateTime.now()
                                                )
                                            )
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                        )

                        val latest = artistPage.sections
                            .flatMap { it.items }
                            .filterIsInstance<AlbumItem>()
                            .maxByOrNull { it.year ?: 0 }
                        if (latest != null) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(24.dp))
                                    .background(Color.White.copy(alpha = 0.14f))
                                    .clickable { navController.navigate("album/${latest.id}") }
                                    .padding(10.dp)
                            ) {
                                AsyncImage(
                                    model = latest.thumbnail.resize(400, 400),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(88.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                )
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 14.dp)
                                ) {
                                    Text(
                                        text = listOfNotNull(
                                            stringResource(R.string.latest_release),
                                            latest.year?.toString()
                                        ).joinToString(" · "),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = latest.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                if (librarySongs.isNotEmpty()) {
                    item {
                        NavigationTitle(
                            title = stringResource(R.string.from_your_library),
                            onClick = {
                                navController.navigate("artist/${viewModel.artistId}/songs")
                            }
                        )
                    }

                    items(
                        items = librarySongs,
                        key = { "local_${it.id}" }
                    ) { song ->
                        MinimalSongListItem(
                            song = song,
                            isActive = song.id == mediaMetadata?.id,
                            isPlaying = isPlaying,
                            trailingContent = {
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
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable {
                                    if (song.id == mediaMetadata?.id) {
                                        playerConnection.player.togglePlayPause()
                                    } else {
                                        playerConnection.playQueue(YouTubeQueue.radio(song.toMediaMetadata()))
                                    }
                                }
                                .animateItem()
                        )
                    }
                }

                artistPage.sections.fastForEach { section ->
                    item {
                        NavigationTitle(
                            title = section.title,
                            onClick = section.moreEndpoint?.let {
                                {
                                    navController.navigate("artist/${viewModel.artistId}/items?browseId=${it.browseId}?params=${it.params}")
                                }
                            }
                        )
                    }

                    if ((section.items.firstOrNull() as? SongItem)?.album != null) {
                        items(
                            items = section.items,
                            key = { it.id }
                        ) { song ->
                            MinimalYouTubeListItem(
                                item = song as SongItem,
                                isActive = mediaMetadata?.id == song.id,
                                isPlaying = isPlaying,
                                trailingContent = {
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
                                },
                                modifier = Modifier
                                    .clickable {
                                        if (song.id == mediaMetadata?.id) {
                                            playerConnection.player.togglePlayPause()
                                        } else {
                                            playerConnection.playQueue(YouTubeQueue.radio(song.toMediaMetadata()))
                                        }
                                    }
                                    .animateItem()
                            )
                        }
                    } else {
                        item {
                            LazyRow {
                                items(
                                    items = section.items,
                                    key = { it.id }
                                ) { item ->
                                    YouTubeGridItem(
                                        item = item,
                                        isActive = when (item) {
                                            is SongItem -> mediaMetadata?.id == item.id
                                            is AlbumItem -> mediaMetadata?.album?.id == item.id
                                            else -> false
                                        },
                                        isPlaying = isPlaying,
                                        coroutineScope = coroutineScope,
                                        modifier = Modifier
                                            .combinedClickable(
                                                onClick = {
                                                    when (item) {
                                                        is SongItem -> playerConnection.playQueue(YouTubeQueue.radio(item.toMediaMetadata()))
                                                        is AlbumItem -> navController.navigate("album/${item.id}")
                                                        is ArtistItem -> navController.navigate("artist/${item.id}")
                                                        is PlaylistItem -> navController.navigate("online_playlist/${item.id}")
                                                    }
                                                },
                                                onLongClick = {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    menuState.show {
                                                        when (item) {
                                                            is SongItem -> YouTubeSongMenu(
                                                                song = item,
                                                                navController = navController,
                                                                onDismiss = menuState::dismiss
                                                            )

                                                            is AlbumItem -> YouTubeAlbumMenu(
                                                                albumItem = item,
                                                                navController = navController,
                                                                onDismiss = menuState::dismiss
                                                            )

                                                            is ArtistItem -> YouTubeArtistMenu(
                                                                artist = item,
                                                                onDismiss = menuState::dismiss
                                                            )

                                                            is PlaylistItem -> YouTubePlaylistMenu(
                                                                playlist = item,
                                                                coroutineScope = coroutineScope,
                                                                onDismiss = menuState::dismiss
                                                            )
                                                        }
                                                    }
                                                }
                                            )
                                            .animateItem()
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                item(key = "shimmer") {
                    ShimmerHost {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(4f / 3)
                        ) {
                            Spacer(
                                modifier = Modifier
                                    .shimmer()
                                    .background(MaterialTheme.colorScheme.onSurface)
                                    .fadingEdge(
                                        top = WindowInsets.systemBars
                                            .asPaddingValues()
                                            .calculateTopPadding() + AppBarHeight,
                                        bottom = 108.dp
                                    )
                            )
                            TextPlaceholder(
                                height = 56.dp,
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(horizontal = 48.dp)
                            )
                        }

                        Row(
                            modifier = Modifier.padding(12.dp)
                        ) {
                            ButtonPlaceholder(Modifier.weight(1f))

                            Spacer(Modifier.width(12.dp))

                            ButtonPlaceholder(Modifier.weight(1f))
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
    }

    var showInfo by remember { mutableStateOf(false) }
    val description = artistPage?.description?.takeIf { it.isNotBlank() }
    if (showInfo && description != null) {
        AlertDialog(
            onDismissRequest = { showInfo = false },
            confirmButton = {
                TextButton(onClick = { showInfo = false }) { Text(stringResource(android.R.string.ok)) }
            },
            title = { Text(artistPage?.artist?.title.orEmpty()) },
            text = { Text(description, modifier = Modifier.verticalScroll(rememberScrollState())) }
        )
    }

    TopAppBar(
        title = { if (!transparentAppBar) Text(artistPage?.artist?.title.orEmpty()) },
        navigationIcon = {
            GlassCircleButton(
                icon = R.drawable.arrow_back,
                onClick = navController::navigateUp,
                onLongClick = navController::backToMain,
                modifier = Modifier.padding(start = 12.dp)
            )
        },
        actions = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(end = 12.dp)
                    .height(44.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.20f))
                    .padding(horizontal = 4.dp)
            ) {
                if (description != null) {
                    IconButton(onClick = { showInfo = true }) {
                        Icon(painterResource(R.drawable.info), contentDescription = null, tint = Color.White)
                    }
                }
                IconButton(
                    onClick = {
                        viewModel.artistPage?.artist?.shareLink?.let { link ->
                            val intent = Intent().apply {
                                action = Intent.ACTION_SEND
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, link)
                            }
                            context.startActivity(Intent.createChooser(intent, null))
                        }
                    }
                ) {
                    Icon(painterResource(R.drawable.share), contentDescription = null, tint = Color.White)
                }
            }
        },
        scrollBehavior = scrollBehavior,
        colors = immersiveTopBarColors(tint, solid = !transparentAppBar)
    )
}
