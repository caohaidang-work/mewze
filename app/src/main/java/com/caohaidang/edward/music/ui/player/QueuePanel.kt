package com.caohaidang.edward.music.ui.player

import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.caohaidang.edward.innertube.YouTube
import com.caohaidang.edward.music.LocalDatabase
import com.caohaidang.edward.music.db.entities.SongEntity
import com.caohaidang.edward.music.models.MediaMetadata
import com.caohaidang.edward.music.utils.reportException
import com.caohaidang.edward.music.ui.screens.Screens
import com.caohaidang.edward.music.constants.AutoPlayKey
import com.caohaidang.edward.music.constants.ListThumbnailSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import com.caohaidang.edward.music.ui.share.ShareSongDialog
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEachReversed
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.Timeline
import com.caohaidang.edward.music.extensions.toggleRepeatMode
import androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder
import androidx.navigation.NavController
import com.caohaidang.edward.music.LocalPlayerConnection
import com.caohaidang.edward.music.R
import com.caohaidang.edward.music.constants.ListItemHeight
import com.caohaidang.edward.music.constants.LockQueueKey
import com.caohaidang.edward.music.constants.ShowLyricsKey
import com.caohaidang.edward.music.extensions.metadata
import com.caohaidang.edward.music.extensions.move
import com.caohaidang.edward.music.extensions.togglePlayPause
import com.caohaidang.edward.music.ui.component.BottomSheet
import com.caohaidang.edward.music.ui.component.BottomSheetState
import com.caohaidang.edward.music.ui.component.LocalMenuState
import com.caohaidang.edward.music.ui.component.MediaMetadataListItem
import com.caohaidang.edward.music.ui.menu.MediaMetadataMenu
import com.caohaidang.edward.music.ui.menu.PlayerMenu
import com.caohaidang.edward.music.ui.menu.QueueSelectionMenu
import com.caohaidang.edward.music.utils.joinByBullet
import com.caohaidang.edward.music.utils.makeTimeString
import com.caohaidang.edward.music.utils.rememberPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import com.caohaidang.edward.music.ui.utils.fadingEdge
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.statusBarsPadding

/**
 * Hàng đợi nằm ngay trong màn hình phát (thay cho vùng ảnh bìa), các nút điều khiển
 * phát nhạc vẫn hiện ở phía dưới.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun QueuePanel(
    playerBottomSheetState: BottomSheetState,
    navController: NavController,
    modifier: Modifier = Modifier,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    val isPlaying by playerConnection.isPlaying.collectAsState()
    val currentWindowIndex by playerConnection.currentWindowIndex.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val shuffleModeEnabled by playerConnection.shuffleModeEnabled.collectAsState()
    val repeatMode by playerConnection.repeatMode.collectAsState()
    val queueWindows by playerConnection.queueWindows.collectAsState()

    var lockQueue by rememberPreference(LockQueueKey, defaultValue = false)
    var autoPlay by rememberPreference(AutoPlayKey, defaultValue = true)

    var showDetailsDialog by remember { mutableStateOf(false) }
    if (showDetailsDialog) {
        DetailsDialog(onDismiss = { showDetailsDialog = false })
    }

    val mutableQueueWindows = remember { mutableStateListOf<Timeline.Window>() }
    val lazyListState = rememberLazyListState()
    var dragInfo by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    val reorderableState = rememberReorderableLazyListState(
        lazyListState = lazyListState,
        scrollThresholdPadding = PaddingValues(vertical = ListItemHeight)
    ) { from, to ->
        val currentDragInfo = dragInfo
        dragInfo = if (currentDragInfo == null) {
            from.index to to.index
        } else {
            currentDragInfo.first to to.index
        }
        mutableQueueWindows.move(from.index, to.index)
    }

    LaunchedEffect(reorderableState.isAnyItemDragging) {
        if (!reorderableState.isAnyItemDragging) {
            dragInfo?.let { (from, to) ->
                if (!playerConnection.player.shuffleModeEnabled) {
                    playerConnection.player.moveMediaItem(from, to)
                } else {
                    playerConnection.player.setShuffleOrder(
                        DefaultShuffleOrder(
                            queueWindows.map { it.firstPeriodIndex }.toMutableList().move(from, to).toIntArray(),
                            System.currentTimeMillis()
                        )
                    )
                }
                dragInfo = null
            }
        }
    }

    LaunchedEffect(queueWindows) {
        mutableQueueWindows.apply {
            clear()
            addAll(queueWindows)
        }
    }

    LaunchedEffect(mutableQueueWindows) {
        if (currentWindowIndex != -1) {
            lazyListState.scrollToItem(currentWindowIndex)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(top = 20.dp)
    ) {
        QueueTopBar(
            mediaMetadata = mediaMetadata,
            onMoreClick = {
                menuState.show {
                    PlayerMenu(
                        mediaMetadata = mediaMetadata,
                        navController = navController,
                        bottomSheetState = playerBottomSheetState,
                        onShowDetailsDialog = { showDetailsDialog = true },
                        onDismiss = menuState::dismiss
                    )
                }
            }
        )

        Spacer(Modifier.height(16.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
        ) {
            QueuePill(
                icon = R.drawable.shuffle,
                active = shuffleModeEnabled,
                modifier = Modifier.weight(1f),
                onClick = {
                    coroutineScope.launch {
                        lazyListState.animateScrollToItem(
                            if (playerConnection.player.shuffleModeEnabled) playerConnection.player.currentMediaItemIndex else 0
                        )
                    }.invokeOnCompletion {
                        playerConnection.player.shuffleModeEnabled = !playerConnection.player.shuffleModeEnabled
                    }
                }
            )
            QueuePill(
                icon = if (repeatMode == androidx.media3.common.Player.REPEAT_MODE_ONE) R.drawable.repeat_one else R.drawable.repeat,
                active = repeatMode != androidx.media3.common.Player.REPEAT_MODE_OFF,
                modifier = Modifier.weight(1f),
                onClick = playerConnection.player::toggleRepeatMode
            )
            QueuePill(
                icon = R.drawable.all_inclusive,
                active = autoPlay,
                modifier = Modifier.weight(1f),
                onClick = { autoPlay = !autoPlay }
            )
            QueuePill(
                icon = if (lockQueue) R.drawable.lock else R.drawable.lock_open,
                active = lockQueue,
                modifier = Modifier.weight(1f),
                onClick = { lockQueue = !lockQueue }
            )
        }

        Spacer(Modifier.height(18.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
        ) {
            Text(
                text = stringResource(R.string.queue),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = stringResource(R.string.queue_clear),
                fontSize = 16.sp,
                color = Color.White.copy(alpha = 0.6f),
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        // Xóa các bài sắp phát, giữ lại bài đang phát.
                        val player = playerConnection.player
                        val next = player.currentMediaItemIndex + 1
                        if (next in 1 until player.mediaItemCount) {
                            player.removeMediaItems(next, player.mediaItemCount)
                        }
                    }
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            )
        }

        Spacer(Modifier.height(4.dp))

        LazyColumn(
            state = lazyListState,
            contentPadding = PaddingValues(bottom = 24.dp),
            modifier = Modifier
                .weight(1f)
                .fadingEdge(bottom = 56.dp)
                .nestedScroll(playerBottomSheetState.preUpPostDownNestedScrollConnection)
        ) {
            itemsIndexed(
                items = mutableQueueWindows,
                key = { _, item -> item.uid.hashCode() }
            ) { index, window ->
                ReorderableItem(
                    state = reorderableState,
                    key = window.uid.hashCode()
                ) {
                    val currentItem by rememberUpdatedState(window)
                    val dismissState = rememberSwipeToDismissBoxState(
                        positionalThreshold = { totalDistance -> totalDistance },
                        confirmValueChange = { dismissValue ->
                            if (dismissValue == SwipeToDismissBoxValue.StartToEnd || dismissValue == SwipeToDismissBoxValue.EndToStart) {
                                playerConnection.player.removeMediaItem(currentItem.firstPeriodIndex)
                            }
                            true
                        }
                    )
                    val metadata = window.mediaItem.metadata!!
                    val isActive = index == currentWindowIndex

                    val content = @Composable {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(ListItemHeight)
                                .combinedClickable(
                                    onClick = {
                                        coroutineScope.launch(Dispatchers.Main) {
                                            if (isActive) {
                                                playerConnection.player.togglePlayPause()
                                            } else {
                                                playerConnection.player.seekToDefaultPosition(window.firstPeriodIndex)
                                                playerConnection.player.playWhenReady = true
                                            }
                                        }
                                    },
                                    onLongClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        menuState.show {
                                            MediaMetadataMenu(
                                                mediaMetadata = metadata,
                                                navController = navController,
                                                bottomSheetState = playerBottomSheetState,
                                                onDismiss = menuState::dismiss,
                                            )
                                        }
                                    }
                                )
                                .padding(start = 20.dp, end = 8.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(ListThumbnailSize)
                                    .clip(RoundedCornerShape(6.dp))
                            ) {
                                AsyncImage(
                                    model = metadata.thumbnailUrl,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                if (isActive) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color.Black.copy(alpha = 0.45f))
                                    ) {
                                        Icon(
                                            painter = painterResource(if (isPlaying) R.drawable.pause else R.drawable.play),
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = metadata.title,
                                    fontSize = 17.sp,
                                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = metadata.artists.joinToString { it.name },
                                    fontSize = 14.sp,
                                    color = Color.White.copy(alpha = 0.6f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (!lockQueue) {
                                IconButton(
                                    onClick = { },
                                    modifier = Modifier.draggableHandle()
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.drag_handle),
                                        contentDescription = null,
                                        tint = Color.White.copy(alpha = 0.6f)
                                    )
                                }
                            }
                        }
                    }

                    if (!lockQueue) {
                        SwipeToDismissBox(
                            state = dismissState,
                            backgroundContent = {},
                            content = { content() }
                        )
                    } else {
                        content()
                    }
                }
            }

            item(key = "add_songs_to_queue") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(ListItemHeight)
                        .clickable {
                            navController.navigate(Screens.Search.route) { launchSingleTop = true }
                            playerBottomSheetState.collapseSoft()
                        }
                        .padding(horizontal = 20.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(ListThumbnailSize)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.White.copy(alpha = 0.14f))
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.add),
                            contentDescription = null,
                            tint = Color.White
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.add_songs_to_queue),
                        fontSize = 17.sp,
                        color = Color.White
                    )
                }
            }

            if (autoPlay) {
                item(key = "autoplay_section") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                painter = painterResource(R.drawable.all_inclusive),
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.autoplay),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                        Text(
                            text = stringResource(R.string.queue_autoplay_desc),
                            fontSize = 13.sp,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QueueTopBar(
    mediaMetadata: MediaMetadata?,
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val database = LocalDatabase.current
    val scope = rememberCoroutineScope()
    val currentSong by playerConnection.currentSong.collectAsState(initial = null)
    val liked = currentSong?.song?.liked == true

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 20.dp)
    ) {
        if (mediaMetadata == null) return@Row
        AsyncImage(
            model = mediaMetadata.thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(8.dp))
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = mediaMetadata.title,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = mediaMetadata.artists.joinToString { it.name },
                fontSize = 15.sp,
                color = Color.White.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(
            onClick = {
                val song = currentSong
                val wasLiked = song?.song?.liked == true
                database.query {
                    if (song == null) {
                        insert(mediaMetadata, SongEntity::toggleLike)
                    } else {
                        update(song.song.toggleLike())
                    }
                }
                scope.launch(Dispatchers.IO) {
                    if (YouTube.cookie != null) {
                        val result = if (!wasLiked) YouTube.like(mediaMetadata.id) else YouTube.unlike(mediaMetadata.id)
                        result.onFailure { reportException(it) }
                    }
                }
            }
        ) {
            Icon(
                painter = painterResource(if (liked) R.drawable.favorite else R.drawable.favorite_border),
                contentDescription = null,
                tint = if (liked) Color(0xFFFF4D6D) else Color.White
            )
        }
        IconButton(onClick = onMoreClick) {
            Icon(
                painter = painterResource(R.drawable.more_horiz),
                contentDescription = null,
                tint = Color.White
            )
        }
    }
}

@Composable
private fun QueuePill(
    icon: Int,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = if (active) 0.34f else 0.14f))
            .clickable(onClick = onClick)
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(22.dp)
        )
    }
}

