package com.caohaidang.edward.music.ui.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.caohaidang.edward.music.BuildConfig
import com.caohaidang.edward.music.LocalPlayerConnection
import com.caohaidang.edward.music.R
import com.caohaidang.edward.music.constants.PlayerTextAlignmentKey
import com.caohaidang.edward.music.constants.RomanizeLyricsKey
import com.caohaidang.edward.music.constants.TranslateLanguageKey
import com.caohaidang.edward.music.constants.TranslateLyricsKey
import com.caohaidang.edward.music.db.entities.LyricsEntity.Companion.LYRICS_NOT_FOUND
import com.caohaidang.edward.music.lyrics.LyricsEntry
import com.caohaidang.edward.music.lyrics.LyricsEntry.Companion.HEAD_LYRICS_ENTRY
import com.caohaidang.edward.music.lyrics.LyricsUtils.findCurrentLineIndex
import com.caohaidang.edward.music.lyrics.LyricsUtils.parseLyrics
import com.caohaidang.edward.music.ui.component.shimmer.ShimmerHost
import com.caohaidang.edward.music.ui.component.shimmer.TextPlaceholder
import com.caohaidang.edward.music.ui.menu.LyricsMenu
import com.caohaidang.edward.music.ui.share.ShareLyricsDialog
import com.caohaidang.edward.music.ui.screens.settings.PlayerTextAlignment
import com.caohaidang.edward.music.ui.utils.fadingEdge
import com.caohaidang.edward.music.utils.TranslationHelper
import com.caohaidang.edward.music.utils.rememberEnumPreference
import com.caohaidang.edward.music.utils.rememberPreference
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Lyrics(
    sliderPositionProvider: () -> Long?,
    modifier: Modifier = Modifier,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val menuState = LocalMenuState.current
    val density = LocalDensity.current

    val playerTextAlignment by rememberEnumPreference(PlayerTextAlignmentKey, PlayerTextAlignment.CENTER)
    var translationEnabled by rememberPreference(TranslateLyricsKey, true)
    var romanizationEnabled by rememberPreference(RomanizeLyricsKey, true)
    var targetLanguage by rememberPreference(TranslateLanguageKey, TranslationHelper.DEFAULT_LANGUAGE)
    var showTranslateDialog by remember { mutableStateOf(false) }

    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val lyricsEntity by playerConnection.currentLyrics.collectAsState(initial = null)
    val lyrics = lyricsEntity?.lyrics

    val lines = remember(lyrics) {
        if (lyrics == null || lyrics == LYRICS_NOT_FOUND) emptyList()
        else if (lyrics.startsWith("[")) listOf(HEAD_LYRICS_ENTRY) + parseLyrics(lyrics)
        else lyrics.lines().mapIndexed { index, line -> LyricsEntry(index * 100L, line) }
    }
    val isSynced = remember(lyrics) {
        !lyrics.isNullOrEmpty() && lyrics.startsWith("[")
    }

    var currentLineIndex by remember {
        mutableIntStateOf(-1)
    }

    var deferredCurrentLineIndex by rememberSaveable {
        mutableIntStateOf(0)
    }

    var lastPreviewTime by rememberSaveable {
        mutableLongStateOf(0L)
    }
    var isSeeking by remember {
        mutableStateOf(false)
    }

    var shareInitialLine by remember { mutableStateOf<String?>(null) }

    val extras = remember(lyrics) { mutableStateMapOf<Int, TranslationHelper.LineResult>() }
    val songId = mediaMetadata?.id
    LaunchedEffect(lyrics, songId, translationEnabled, romanizationEnabled, targetLanguage) {
        extras.clear()
        if (songId == null || lyrics.isNullOrEmpty() || lyrics == LYRICS_NOT_FOUND) return@LaunchedEffect
        if (!translationEnabled && !romanizationEnabled) return@LaunchedEffect
        TranslationHelper.translateLines(
            songId = songId,
            lines = lines.map { it.text },
            targetLang = targetLanguage,
            wantTranslation = translationEnabled,
            wantRomanization = romanizationEnabled,
            onResult = { index, result -> extras[index] = result }
        )
    }

    LaunchedEffect(lyrics) {
        if (lyrics.isNullOrEmpty() || !lyrics.startsWith("[")) {
            currentLineIndex = -1
            return@LaunchedEffect
        }
        while (isActive) {
            delay(50)
            val sliderPosition = sliderPositionProvider()
            isSeeking = sliderPosition != null
            currentLineIndex = findCurrentLineIndex(lines, sliderPosition ?: playerConnection.player.currentPosition)
        }
    }

    LaunchedEffect(isSeeking, lastPreviewTime) {
        if (isSeeking) {
            lastPreviewTime = 0L
        } else if (lastPreviewTime != 0L) {
            delay(LyricsPreviewTime)
            lastPreviewTime = 0L
        }
    }

    val lazyListState = rememberLazyListState()

    LaunchedEffect(currentLineIndex, lastPreviewTime) {
        if (!isSynced) return@LaunchedEffect
        if (currentLineIndex != -1) {
            deferredCurrentLineIndex = currentLineIndex
            if (lastPreviewTime == 0L) {
                if (isSeeking) {
                    lazyListState.scrollToItem(currentLineIndex, with(density) { 36.dp.toPx().toInt() })
                } else {
                    lazyListState.animateScrollToItem(currentLineIndex, with(density) { 36.dp.toPx().toInt() })
                }
            }
        }
    }

    BoxWithConstraints(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxSize()
            .padding(bottom = 12.dp)
    ) {
        LazyColumn(
            state = lazyListState,
            contentPadding = WindowInsets.systemBars
                .only(WindowInsetsSides.Top)
                .add(WindowInsets(top = maxHeight / 2, bottom = maxHeight / 2))
                .asPaddingValues(),
            modifier = Modifier
                .fadingEdge(vertical = 64.dp)
                .nestedScroll(remember {
                    object : NestedScrollConnection {
                        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                            lastPreviewTime = System.currentTimeMillis()
                            return super.onPostScroll(consumed, available, source)
                        }

                        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                            lastPreviewTime = System.currentTimeMillis()
                            return super.onPostFling(consumed, available)
                        }
                    }
                })
        ) {
            val displayedCurrentLineIndex = if (isSeeking) deferredCurrentLineIndex else currentLineIndex

            if (lyrics == null) {
                item {
                    ShimmerHost {
                        repeat(10) {
                            Box(
                                contentAlignment = when (playerTextAlignment) {
                                    PlayerTextAlignment.SIDED -> Alignment.CenterStart
                                    PlayerTextAlignment.CENTER -> Alignment.Center
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp, vertical = 4.dp)
                            ) {
                                TextPlaceholder()
                            }
                        }
                    }
                }
            } else {
                itemsIndexed(
                    items = lines
                ) { index, item ->
                    val extra = extras[index]
                    val textAlign = when (playerTextAlignment) {
                        PlayerTextAlignment.SIDED -> TextAlign.Start
                        PlayerTextAlignment.CENTER -> TextAlign.Center
                    }
                    Column(
                        horizontalAlignment = when (playerTextAlignment) {
                            PlayerTextAlignment.SIDED -> Alignment.Start
                            PlayerTextAlignment.CENTER -> Alignment.CenterHorizontally
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = {
                                    if (isSynced) {
                                        playerConnection.player.seekTo(item.time)
                                        lastPreviewTime = 0L
                                    }
                                },
                                onLongClick = {
                                    if (item.text.isNotBlank()) shareInitialLine = item.text.trim()
                                }
                            )
                            .padding(horizontal = 24.dp, vertical = 8.dp)
                            .alpha(if (!isSynced || index == displayedCurrentLineIndex) 1f else 0.5f)
                    ) {
                        Text(
                            text = item.text,
                            fontSize = 20.sp,
                            color = if (index == displayedCurrentLineIndex) androidx.compose.ui.graphics.Color.White else androidx.compose.ui.graphics.Color.White.copy(alpha = 0.5f),
                            textAlign = textAlign,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (romanizationEnabled && !extra?.romanization.isNullOrBlank()) {
                            Text(
                                text = extra?.romanization.orEmpty(),
                                fontSize = 15.sp,
                                fontStyle = FontStyle.Italic,
                                color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.8f),
                                textAlign = textAlign,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 2.dp)
                            )
                        }

                        if (translationEnabled && !extra?.translation.isNullOrBlank()) {
                            Text(
                                text = extra?.translation.orEmpty(),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.8f),
                                textAlign = textAlign,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 2.dp)
                            )
                        }
                    }
                }
            }
        }

        if (lyrics == LYRICS_NOT_FOUND) {
            Text(
                text = stringResource(R.string.lyrics_not_found),
                fontSize = 20.sp,
                color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.7f),
                textAlign = when (playerTextAlignment) {
                    PlayerTextAlignment.SIDED -> TextAlign.Start
                    PlayerTextAlignment.CENTER -> TextAlign.Center
                },
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp)
                    .alpha(0.5f)
            )
        }

        if (showTranslateDialog) {
            LyricsTranslateDialog(
                translationEnabled = translationEnabled,
                onTranslationEnabledChange = { translationEnabled = it },
                romanizationEnabled = romanizationEnabled,
                onRomanizationEnabledChange = { romanizationEnabled = it },
                targetLanguage = targetLanguage,
                onTargetLanguageChange = { targetLanguage = it },
                onDismiss = { showTranslateDialog = false }
            )
        }

        mediaMetadata?.let { mediaMetadata ->
            val shareLine = shareInitialLine
            if (shareLine != null && !lyrics.isNullOrEmpty() && lyrics != LYRICS_NOT_FOUND) {
                ShareLyricsDialog(
                    song = mediaMetadata,
                    lyrics = lyrics,
                    initialLine = shareLine.takeIf { it.isNotEmpty() },
                    onDismiss = { shareInitialLine = null }
                )
            }
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 12.dp)
            ) {
                if (!lyrics.isNullOrEmpty() && lyrics != LYRICS_NOT_FOUND) {
                    IconButton(
                        onClick = {
                            shareInitialLine = lines.getOrNull(currentLineIndex)?.text?.trim().orEmpty()
                        }
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.share),
                            contentDescription = stringResource(R.string.share_lyrics)
                        )
                    }
                }
                if (!lyrics.isNullOrEmpty() && lyrics != LYRICS_NOT_FOUND) {
                    IconButton(onClick = { showTranslateDialog = true }) {
                        Icon(
                            painter = painterResource(id = R.drawable.translate),
                            contentDescription = stringResource(R.string.lyrics_translation),
                            modifier = Modifier.alpha(if (translationEnabled || romanizationEnabled) 1f else 0.5f)
                        )
                    }
                }
                IconButton(
                    onClick = {
                        menuState.show {
                            LyricsMenu(
                                lyricsProvider = { lyricsEntity },
                                mediaMetadataProvider = { mediaMetadata },
                                onDismiss = menuState::dismiss
                            )
                        }
                    }
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.more_horiz),
                        contentDescription = null
                    )
                }
            }
        }
    }
}

const val animateScrollDuration = 300L
val LyricsPreviewTime = 4.seconds
