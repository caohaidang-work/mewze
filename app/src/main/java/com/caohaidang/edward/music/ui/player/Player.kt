package com.caohaidang.edward.music.ui.player

import android.content.Context
import android.content.res.Configuration
import android.media.AudioManager
import android.os.Build
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastForEachIndexed
import androidx.media3.common.C
import androidx.media3.common.Player.REPEAT_MODE_ALL
import androidx.media3.common.Player.REPEAT_MODE_OFF
import androidx.media3.common.Player.REPEAT_MODE_ONE
import androidx.media3.common.Player.STATE_ENDED
import androidx.media3.common.Player.STATE_READY
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.caohaidang.edward.innertube.YouTube
import com.caohaidang.edward.music.LocalDatabase
import com.caohaidang.edward.music.LocalPlayerConnection
import com.caohaidang.edward.music.R
import com.caohaidang.edward.music.constants.DarkModeKey
import com.caohaidang.edward.music.constants.FlowingArtworkKey
import com.caohaidang.edward.music.constants.ShowLyricsKey
import com.caohaidang.edward.music.constants.PlayerHorizontalPadding
import com.caohaidang.edward.music.constants.PureBlackKey
import com.caohaidang.edward.music.constants.QueuePeekHeight
import com.caohaidang.edward.music.constants.SliderStyle
import com.caohaidang.edward.music.constants.SliderStyleKey
import com.caohaidang.edward.music.db.entities.SongEntity
import com.caohaidang.edward.music.extensions.togglePlayPause
import com.caohaidang.edward.music.extensions.toggleRepeatMode
import com.caohaidang.edward.music.models.MediaMetadata
import com.caohaidang.edward.music.ui.component.BottomSheet
import com.caohaidang.edward.music.ui.component.BottomSheetState
import com.caohaidang.edward.music.ui.component.ResizableIconButton
import com.caohaidang.edward.music.ui.component.rememberBottomSheetState
import com.caohaidang.edward.music.ui.screens.settings.DarkMode
import com.caohaidang.edward.music.utils.makeTimeString
import com.caohaidang.edward.music.utils.rememberEnumPreference
import com.caohaidang.edward.music.utils.rememberPreference
import com.caohaidang.edward.music.utils.reportException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import me.saket.squiggles.SquigglySlider
import kotlin.math.roundToInt

private val PlayerSecondaryText = Color.White.copy(alpha = 0.65f)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BottomSheetPlayer(
    state: BottomSheetState,
    navController: NavController,
    modifier: Modifier = Modifier,
) {
    val playerConnection = LocalPlayerConnection.current ?: return

    val isSystemInDarkTheme = isSystemInDarkTheme()
    val darkTheme by rememberEnumPreference(DarkModeKey, defaultValue = DarkMode.AUTO)
    val pureBlack by rememberPreference(PureBlackKey, defaultValue = false)
    val useBlackBackground = remember(isSystemInDarkTheme, darkTheme, pureBlack) {
        val useDarkTheme = if (darkTheme == DarkMode.AUTO) isSystemInDarkTheme else darkTheme == DarkMode.ON
        useDarkTheme && pureBlack
    }
    val backgroundColor = if (useBlackBackground && state.value > state.collapsedBound) {
        lerp(MaterialTheme.colorScheme.surfaceContainer, Color.Black, state.progress)
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }

    val sliderStyle by rememberEnumPreference(SliderStyleKey, SliderStyle.DEFAULT)

    val playbackState by playerConnection.playbackState.collectAsState()
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val repeatMode by playerConnection.repeatMode.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val currentSong by playerConnection.currentSong.collectAsState(initial = null)

    val canSkipPrevious by playerConnection.canSkipPrevious.collectAsState()
    val canSkipNext by playerConnection.canSkipNext.collectAsState()

    var position by rememberSaveable(playbackState) {
        mutableLongStateOf(playerConnection.player.currentPosition)
    }
    var duration by rememberSaveable(playbackState) {
        mutableLongStateOf(playerConnection.player.duration)
    }
    var sliderPosition by remember {
        mutableStateOf<Long?>(null)
    }

    LaunchedEffect(playbackState) {
        if (playbackState == STATE_READY) {
            while (isActive) {
                delay(100)
                position = playerConnection.player.currentPosition
                duration = playerConnection.player.duration
            }
        }
    }

    val flowingArtwork by rememberPreference(FlowingArtworkKey, defaultValue = true)
    val showLyrics by rememberPreference(ShowLyricsKey, false)

    val queueSheetState = rememberBottomSheetState(
        dismissedBound = QueuePeekHeight + WindowInsets.systemBars.asPaddingValues().calculateBottomPadding(),
        expandedBound = state.expandedBound,
    )

    BottomSheet(
        state = state,
        modifier = modifier,
        backgroundColor = backgroundColor,
        fadeBackgroundIn = true,
        onDismiss = {
            playerConnection.player.stop()
            playerConnection.player.clearMediaItems()
        },
        collapsedContent = {
            MiniPlayer(
                position = position,
                duration = duration,
            )
        }
    ) {
        PlayerBackdrop(
            coverUrl = mediaMetadata?.thumbnailUrl,
            isPlaying = isPlaying,
            animate = !state.isCollapsed,
            motion = flowingArtwork,
            landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE,
            showLyrics = showLyrics,
            modifier = Modifier.fillMaxSize(),
        )

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 6.dp)
                .size(width = 40.dp, height = 5.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.45f))
        )

        val controlsContent: @Composable ColumnScope.(MediaMetadata) -> Unit = { mediaMetadata ->
            val scope = rememberCoroutineScope()
            val database = LocalDatabase.current

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PlayerHorizontalPadding)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = mediaMetadata.title,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .basicMarquee()
                            .clickable(enabled = mediaMetadata.album != null) {
                                navController.navigate("album/${mediaMetadata.album!!.id}")
                                state.collapseSoft()
                            }
                    )

                    Spacer(Modifier.height(2.dp))

                    Row {
                        mediaMetadata.artists.fastForEachIndexed { index, artist ->
                            Text(
                                text = artist.name,
                                fontSize = 18.sp,
                                color = PlayerSecondaryText,
                                maxLines = 1,
                                modifier = Modifier
                                    .clickable(enabled = artist.id != null) {
                                        navController.navigate("artist/${artist.id}")
                                        state.collapseSoft()
                                    }
                            )

                            if (index != mediaMetadata.artists.lastIndex) {
                                Text(
                                    text = ", ",
                                    fontSize = 18.sp,
                                    color = PlayerSecondaryText
                                )
                            }
                        }
                    }

                    mediaMetadata.album?.let { album ->
                        Text(
                            text = album.title,
                            fontSize = 14.sp,
                            color = PlayerSecondaryText.copy(alpha = 0.75f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .padding(top = 2.dp)
                                .clickable {
                                    navController.navigate("album/${album.id}")
                                    state.collapseSoft()
                                }
                        )
                    }
                }

                Spacer(Modifier.width(12.dp))

                val liked = currentSong?.song?.liked == true
                val heartScale by animateFloatAsState(
                    targetValue = if (liked) 1.15f else 1f,
                    animationSpec = spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessMedium),
                    label = "heartScale"
                )
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .clickable {
                            val song = currentSong
                            val isCurrentlyLiked = song?.song?.liked == true

                            database.query {
                                if (song == null) {
                                    insert(mediaMetadata, SongEntity::toggleLike)
                                } else {
                                    update(song.song.toggleLike())
                                }
                            }

                            scope.launch(Dispatchers.IO) {
                                if (YouTube.cookie != null) {
                                    val result = if (!isCurrentlyLiked) YouTube.like(mediaMetadata.id) else YouTube.unlike(mediaMetadata.id)
                                    result.onFailure { reportException(it) }
                                }
                            }
                        }
                ) {
                    Icon(
                        painter = painterResource(if (liked) R.drawable.favorite else R.drawable.favorite_border),
                        contentDescription = null,
                        tint = if (liked) Color(0xFFFF4D6D) else Color.White,
                        modifier = Modifier
                            .size(28.dp)
                            .graphicsLayer {
                                scaleX = heartScale
                                scaleY = heartScale
                            }
                    )
                }

                Spacer(Modifier.width(8.dp))

                PlayerCircleButton(
                    icon = when (repeatMode) {
                        REPEAT_MODE_OFF, REPEAT_MODE_ALL -> R.drawable.repeat
                        REPEAT_MODE_ONE -> R.drawable.repeat_one
                        else -> throw IllegalStateException()
                    },
                    tint = Color.White.copy(alpha = if (repeatMode == REPEAT_MODE_OFF) 0.6f else 1f),
                    onClick = playerConnection.player::toggleRepeatMode
                )
            }

            Spacer(Modifier.height(18.dp))

            val seekMax = if (duration == C.TIME_UNSET || duration < 0) 0f else duration.toFloat()
            val applySeek = {
                sliderPosition?.let {
                    playerConnection.player.seekTo(it)
                    position = it
                }
                sliderPosition = null
            }
            when (sliderStyle) {
                SliderStyle.DEFAULT -> {
                    PlayerSeekBar(
                        value = (sliderPosition ?: position).toFloat(),
                        maxValue = seekMax,
                        onValueChange = { sliderPosition = it.toLong() },
                        onValueChangeFinished = { applySeek() },
                        modifier = Modifier.padding(horizontal = PlayerHorizontalPadding)
                    )
                }

                SliderStyle.SQUIGGLY -> {
                    SquigglySlider(
                        value = (sliderPosition ?: position).toFloat(),
                        valueRange = 0f..seekMax,
                        onValueChange = {
                            sliderPosition = it.toLong()
                        },
                        onValueChangeFinished = { applySeek() },
                        squigglesSpec = SquigglySlider.SquigglesSpec(
                            amplitude = if (isPlaying) 2.dp else 0.dp,
                            strokeWidth = 4.dp,
                        ),
                        modifier = Modifier.padding(horizontal = PlayerHorizontalPadding),
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PlayerHorizontalPadding)
            ) {
                val current = sliderPosition ?: position
                Text(
                    text = makeTimeString(current),
                    style = MaterialTheme.typography.labelMedium,
                    color = PlayerSecondaryText,
                    maxLines = 1,
                )

                Text(
                    text = if (duration != C.TIME_UNSET && duration > 0) "-" + makeTimeString((duration - current).coerceAtLeast(0L)) else "",
                    style = MaterialTheme.typography.labelMedium,
                    color = PlayerSecondaryText,
                    maxLines = 1,
                )
            }

            Spacer(Modifier.height(8.dp))

            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PlayerHorizontalPadding)
            ) {
                ResizableIconButton(
                    icon = R.drawable.skip_previous,
                    color = Color.White,
                    enabled = canSkipPrevious,
                    modifier = Modifier.size(48.dp),
                    onClick = playerConnection::seekToPrevious
                )

                ResizableIconButton(
                    icon = if (playbackState == STATE_ENDED) R.drawable.replay else if (isPlaying) R.drawable.pause else R.drawable.play,
                    color = Color.White,
                    modifier = Modifier.size(72.dp),
                    onClick = {
                        if (playbackState == STATE_ENDED) {
                            playerConnection.player.seekTo(0, 0)
                            playerConnection.player.playWhenReady = true
                        } else {
                            playerConnection.player.togglePlayPause()
                        }
                    }
                )

                ResizableIconButton(
                    icon = R.drawable.skip_next,
                    color = Color.White,
                    enabled = canSkipNext,
                    modifier = Modifier.size(48.dp),
                    onClick = playerConnection::seekToNext
                )
            }

            Spacer(Modifier.height(12.dp))

            VolumeRow(modifier = Modifier.padding(horizontal = PlayerHorizontalPadding))
        }

        CompositionLocalProvider(LocalContentColor provides Color.White) {
            when (LocalConfiguration.current.orientation) {
                Configuration.ORIENTATION_LANDSCAPE -> {
                    Row(
                        modifier = Modifier
                            .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal))
                            .padding(bottom = queueSheetState.collapsedBound)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.weight(1f)
                        ) {
                            Thumbnail(
                                sliderPositionProvider = { sliderPosition },
                                modifier = Modifier.nestedScroll(state.preUpPostDownNestedScrollConnection)
                            )
                        }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .weight(1f)
                                .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top))
                        ) {
                            Spacer(Modifier.weight(1f))

                            mediaMetadata?.let {
                                controlsContent(it)
                            }

                            Spacer(Modifier.weight(1f))
                        }
                    }
                }

                else -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal))
                            .padding(bottom = queueSheetState.collapsedBound)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.weight(1f)
                        ) {
                            Thumbnail(
                                sliderPositionProvider = { sliderPosition },
                                modifier = Modifier.nestedScroll(state.preUpPostDownNestedScrollConnection)
                            )
                        }

                        mediaMetadata?.let {
                            controlsContent(it)
                        }

                        Spacer(Modifier.height(16.dp))
                    }
                }
            }
        }

        Queue(
            state = queueSheetState,
            playerBottomSheetState = state,
            backgroundColor = backgroundColor,
            navController = navController
        )
    }
}

@Composable
private fun PlayerCircleButton(
    icon: Int,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.18f))
            .clickable(onClick = onClick)
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun VolumeRow(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    var volume by remember {
        mutableFloatStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVolume)
    }
    var isDragging by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (isActive) {
            if (!isDragging) {
                volume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVolume
            }
            delay(500)
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth()
    ) {
        Icon(
            painter = painterResource(R.drawable.volume_down),
            contentDescription = null,
            tint = PlayerSecondaryText,
            modifier = Modifier.size(20.dp)
        )

        PlayerSeekBar(
            value = volume,
            maxValue = 1f,
            onValueChange = {
                isDragging = true
                volume = it
                audioManager.setStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    (it * maxVolume).roundToInt().coerceIn(0, maxVolume),
                    0
                )
            },
            onValueChangeFinished = { isDragging = false },
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 10.dp)
        )

        Icon(
            painter = painterResource(R.drawable.volume_up),
            contentDescription = null,
            tint = PlayerSecondaryText,
            modifier = Modifier.size(20.dp)
        )
    }
}
