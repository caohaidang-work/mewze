package com.caohaidang.edward.music.ui.component

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import coil.compose.AsyncImage
import com.caohaidang.edward.innertube.YouTube
import com.caohaidang.edward.music.LocalDatabase
import com.caohaidang.edward.music.db.entities.SongEntity
import com.caohaidang.edward.music.models.MediaMetadata
import com.caohaidang.edward.music.utils.reportException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.abs
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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.platform.LocalContext
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
import com.caohaidang.edward.music.constants.AiTranslateApiKeyKey
import com.caohaidang.edward.music.constants.AiTranslateCustomUrlKey
import com.caohaidang.edward.music.constants.AiTranslateModelKey
import com.caohaidang.edward.music.constants.AiTranslateProviderKey
import com.caohaidang.edward.music.constants.PlayerTextAlignmentKey
import com.caohaidang.edward.music.constants.RomanizeLyricsKey
import com.caohaidang.edward.music.constants.TranslateLanguageKey
import com.caohaidang.edward.music.constants.TranslateLyricsKey
import com.caohaidang.edward.music.db.entities.LyricsEntity.Companion.LYRICS_NOT_FOUND
import com.caohaidang.edward.music.lyrics.LyricsEntry
import com.caohaidang.edward.music.lyrics.LyricsEntry.Companion.HEAD_LYRICS_ENTRY
import com.caohaidang.edward.music.lyrics.LyricsUtils
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
import android.widget.Toast
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

    val playerTextAlignment by rememberEnumPreference(PlayerTextAlignmentKey, PlayerTextAlignment.SIDED)
    var translationEnabled by rememberPreference(TranslateLyricsKey, true)
    var romanizationEnabled by rememberPreference(RomanizeLyricsKey, true)
    var targetLanguage by rememberPreference(TranslateLanguageKey, TranslationHelper.DEFAULT_LANGUAGE)
    var aiProviderName by rememberPreference(AiTranslateProviderKey, TranslationHelper.Provider.GEMINI.name)
    var aiApiKey by rememberPreference(AiTranslateApiKeyKey, "")
    var aiModel by rememberPreference(AiTranslateModelKey, TranslationHelper.Provider.GEMINI.defaultModel)
    var aiCustomUrl by rememberPreference(AiTranslateCustomUrlKey, "")
    val aiConfig = TranslationHelper.AiConfig(
        provider = TranslationHelper.Provider.entries.firstOrNull { it.name == aiProviderName }
            ?: TranslationHelper.Provider.GEMINI,
        apiKey = aiApiKey,
        model = aiModel,
        customBaseUrl = aiCustomUrl,
    )
    var showTranslateDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

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
    LaunchedEffect(
        lyrics, songId, translationEnabled, romanizationEnabled, targetLanguage,
        aiProviderName, aiApiKey, aiModel, aiCustomUrl
    ) {
        extras.clear()
        if (songId == null || lyrics.isNullOrEmpty() || lyrics == LYRICS_NOT_FOUND) return@LaunchedEffect
        if (!translationEnabled && !romanizationEnabled) return@LaunchedEffect
        // Gõ dở key/model thì chờ một chút, tránh bắn request cho từng ký tự.
        delay(600)
        try {
            TranslationHelper.translateLines(
                songId = songId,
                lines = lines.map { it.text },
                targetLang = targetLanguage,
                wantTranslation = translationEnabled && aiConfig.isUsable,
                wantRomanization = romanizationEnabled,
                config = aiConfig,
                onResult = { index, result -> extras[index] = result }
            )
        } catch (e: TranslationHelper.AiTranslateException) {
            Toast.makeText(
                context,
                context.getString(R.string.ai_translate_failed, e.message.orEmpty()),
                Toast.LENGTH_LONG
            ).show()
        }
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

    // Vị trí phát cập nhật theo từng khung hình — chỉ dòng đang hát đọc giá trị này
    // nên chỉ dòng đó recompose (hiệu ứng karaoke chạy theo giọng hát).
    val karaokePosition = remember { mutableLongStateOf(0L) }
    LaunchedEffect(lyrics) {
        if (lyrics.isNullOrEmpty() || !lyrics.startsWith("[")) return@LaunchedEffect
        while (isActive) {
            withFrameNanos { }
            karaokePosition.longValue = sliderPositionProvider() ?: playerConnection.player.currentPosition
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
                .fadingEdge(
                    top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 190.dp,
                    bottom = 64.dp,
                    topStart = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 70.dp
                )
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
                    val centered = playerTextAlignment == PlayerTextAlignment.CENTER
                    val textAlign = if (centered) TextAlign.Center else TextAlign.Start
                    val isActive = !isSynced || index == displayedCurrentLineIndex
                    val distance = if (!isSynced || displayedCurrentLineIndex < 0) 0
                    else abs(index - displayedCurrentLineIndex)

                    // Đang lướt để đọc lời (lastPreviewTime != 0): bỏ hiệu ứng mờ để đọc rõ toàn bộ.
                    // Sau LyricsPreviewTime không chạm nữa thì tự mờ lại như bình thường.
                    val isReading = lastPreviewTime != 0L

                    val lineAlpha by animateFloatAsState(
                        targetValue = when {
                            !isSynced || isReading || distance == 0 -> 1f
                            distance == 1 -> 0.55f
                            distance == 2 -> 0.42f
                            else -> 0.3f
                        },
                        animationSpec = tween(350),
                        label = "lyricAlpha"
                    )
                    val lineScale by animateFloatAsState(
                        targetValue = if (isActive || isReading) 1f else 0.94f,
                        animationSpec = tween(350),
                        label = "lyricScale"
                    )
                    // Càng xa dòng đang hát càng mờ (blur chỉ có hiệu lực trên Android 12+).
                    val lineBlur by animateDpAsState(
                        targetValue = if (isActive || isReading) 0.dp else minOf(distance * 2.5f, 10f).dp,
                        animationSpec = tween(350),
                        label = "lyricBlur"
                    )

                    Column(
                        horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
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
                            .padding(horizontal = 24.dp, vertical = 12.dp)
                            .graphicsLayer {
                                alpha = lineAlpha
                                scaleX = lineScale
                                scaleY = lineScale
                                transformOrigin = TransformOrigin(if (centered) 0.5f else 0f, 0.5f)
                            }
                            .blur(lineBlur, BlurredEdgeTreatment.Unbounded)
                    ) {
                        val karaoke = isSynced && index == displayedCurrentLineIndex &&
                            index > 0 && item.text.isNotBlank()
                        if (karaoke) {
                            val endTime = lines.getOrNull(index + 1)?.time ?: (item.time + 5000L)
                            val sung = LyricsUtils.sungChars(item, endTime, karaokePosition.longValue)
                            val dim = 0.4f
                            val annotated = buildAnnotatedString {
                                append(item.text)
                                val n = item.text.length
                                val full = sung.toInt().coerceIn(0, n)
                                if (full > 0) addStyle(SpanStyle(color = Color.White), 0, full)
                                if (full < n) {
                                    val frac = (sung - full).coerceIn(0f, 1f)
                                    addStyle(
                                        SpanStyle(color = Color.White.copy(alpha = dim + (1f - dim) * frac)),
                                        full, full + 1
                                    )
                                }
                            }
                            Text(
                                text = annotated,
                                fontSize = 30.sp,
                                lineHeight = 36.sp,
                                color = Color.White.copy(alpha = dim),
                                textAlign = textAlign,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            Text(
                                text = item.text,
                                fontSize = if (isSynced) 30.sp else 24.sp,
                                lineHeight = if (isSynced) 36.sp else 30.sp,
                                color = Color.White,
                                textAlign = textAlign,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        if (romanizationEnabled && !extra?.romanization.isNullOrBlank()) {
                            Text(
                                text = extra?.romanization.orEmpty(),
                                fontSize = 18.sp,
                                lineHeight = 23.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.92f),
                                textAlign = textAlign,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp)
                            )
                        }

                        if (translationEnabled && !extra?.translation.isNullOrBlank()) {
                            Text(
                                text = extra?.translation.orEmpty(),
                                fontSize = 16.sp,
                                lineHeight = 21.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White.copy(alpha = 0.72f),
                                textAlign = textAlign,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp)
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
                aiConfig = aiConfig,
                onAiConfigChange = {
                    aiProviderName = it.provider.name
                    aiApiKey = it.apiKey
                    aiModel = it.model
                    aiCustomUrl = it.customBaseUrl
                },
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
            LyricsHeader(
                mediaMetadata = mediaMetadata,
                onMoreClick = {
                    menuState.show {
                        LyricsMenu(
                            lyricsProvider = { lyricsEntity },
                            mediaMetadataProvider = { mediaMetadata },
                            onDismiss = menuState::dismiss
                        )
                    }
                },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 20.dp)
            )

            if (!lyrics.isNullOrEmpty() && lyrics != LYRICS_NOT_FOUND) {
                LyricsCircleButton(
                    icon = R.drawable.translate,
                    contentDescription = stringResource(R.string.lyrics_translation),
                    active = translationEnabled || romanizationEnabled,
                    onClick = { showTranslateDialog = true },
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 24.dp)
                        .offset(y = 14.dp)
                )
            }
        }
    }
}

/** Đầu trang lyrics: ảnh bìa nhỏ, tên bài, nghệ sĩ, nút yêu thích và menu. */
@Composable
private fun LyricsHeader(
    mediaMetadata: MediaMetadata,
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
            .padding(horizontal = 24.dp)
    ) {
        AsyncImage(
            model = mediaMetadata.thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(8.dp))
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = mediaMetadata.title,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = mediaMetadata.artists.joinToString { it.name },
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.72f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(8.dp))
        LyricsCircleButton(
            icon = if (liked) R.drawable.favorite else R.drawable.favorite_border,
            contentDescription = null,
            active = true,
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
        )
        Spacer(Modifier.width(8.dp))
        LyricsCircleButton(
            icon = R.drawable.more_horiz,
            contentDescription = null,
            active = true,
            onClick = onMoreClick
        )
    }
}

@Composable
private fun LyricsCircleButton(
    icon: Int,
    contentDescription: String?,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.2f))
            .clickable(onClick = onClick)
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier
                .size(20.dp)
                .alpha(if (active) 1f else 0.5f)
        )
    }
}

const val animateScrollDuration = 300L
val LyricsPreviewTime = 4.seconds
