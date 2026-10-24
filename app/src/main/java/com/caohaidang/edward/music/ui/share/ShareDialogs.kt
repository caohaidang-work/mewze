package com.caohaidang.edward.music.ui.share

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.caohaidang.edward.music.R
import com.caohaidang.edward.music.lyrics.LyricsUtils
import com.caohaidang.edward.music.models.MediaMetadata
import com.caohaidang.edward.music.share.CardTheme
import com.caohaidang.edward.music.share.ShareCardRenderer
import com.caohaidang.edward.music.share.ShareUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.absoluteValue

private const val MAX_LYRIC_LINES = 8

// The share sheet is always dark (like Spotify's), whatever the app theme is.
private val SheetColor = Color(0xFF262626)
private val CardPlaceholder = Color(0xFF1C1C1E)
private val ChipColor = Color(0xFF3A3A3C)

private const val TAB_SONG = 0
private const val TAB_LYRICS = 1

/** Share a song as an image card. Pass [lyrics] to also get the "Lyrics" tab. */
@Composable
fun ShareSongDialog(
    song: MediaMetadata,
    onDismiss: () -> Unit,
    lyrics: String? = null,
) {
    ShareSheet(song = song, lyrics = lyrics, initialTab = TAB_SONG, initialLine = null, onDismiss = onDismiss)
}

/** Share lyrics as an image card (opens on the "Lyrics" tab, [initialLine] pre-selected). */
@Composable
fun ShareLyricsDialog(
    song: MediaMetadata,
    lyrics: String,
    initialLine: String?,
    onDismiss: () -> Unit,
) {
    ShareSheet(song = song, lyrics = lyrics, initialTab = TAB_LYRICS, initialLine = initialLine, onDismiss = onDismiss)
}

private fun parseShareLines(lyrics: String): List<String> {
    val raw = if (lyrics.startsWith("[")) LyricsUtils.parseLyrics(lyrics).map { it.text } else lyrics.lines()
    return raw.map { it.trim() }.filter { it.isNotEmpty() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShareSheet(
    song: MediaMetadata,
    lyrics: String?,
    initialTab: Int,
    initialLine: String?,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val brand = stringResource(R.string.app_name)
    val link = remember(song.id) { ShareUtils.musicLink(song.id) }

    // ---- data -------------------------------------------------------------------------------
    val lines = remember(lyrics) { lyrics?.let { parseShareLines(it) }.orEmpty() }
    val hasLyrics = lines.isNotEmpty()
    var tab by rememberSaveable { mutableIntStateOf(if (initialTab == TAB_LYRICS && hasLyrics) TAB_LYRICS else TAB_SONG) }
    if (!hasLyrics && tab == TAB_LYRICS) tab = TAB_SONG

    var cover by remember { mutableStateOf<Bitmap?>(null) }
    var coverLoaded by remember { mutableStateOf(false) }
    LaunchedEffect(song.thumbnailUrl) {
        cover = ShareCardRenderer.loadBitmap(context, song.thumbnailUrl, 800)
        coverLoaded = true
    }

    val selected = remember(lines, initialLine) {
        mutableStateListOf<Int>().apply {
            val index = initialLine?.trim()?.let { lines.indexOf(it) } ?: -1
            if (index >= 0) add(index) else addAll(0 until minOf(3, lines.size))
        }
    }
    val selectedLines = selected.sorted().map { lines[it] }

    // blurred-artwork first (it is what the card looks like in the reference), then the rest
    val themes = remember(cover) {
        listOf(CardTheme.fromArtwork(cover), CardTheme.fromArtwork(cover, solidCard = true)) + CardTheme.presets
    }
    val pagerState = rememberPagerState(pageCount = { themes.size })

    val lyricsTab = tab == TAB_LYRICS
    val nothingToShare = lyricsTab && selectedLines.isEmpty()

    /** Renders the card of the given theme for the current tab (null = nothing to render). */
    suspend fun render(themeIndex: Int): Bitmap? = withContext(Dispatchers.Default) {
        val theme = themes[themeIndex.coerceIn(themes.indices)]
        if (lyricsTab) {
            if (selectedLines.isEmpty()) null
            else ShareCardRenderer.renderLyrics(context, song, selectedLines, theme, cover, brand)
        } else {
            ShareCardRenderer.renderSong(context, song, theme, cover, brand)
        }
    }

    /** Renders the visible card, saves it and hands the file to [action]. */
    fun shareCurrent(action: (android.net.Uri) -> Unit) {
        scope.launch {
            val bitmap = render(pagerState.currentPage) ?: return@launch
            val uri = ShareUtils.saveToCache(context, bitmap, (if (lyricsTab) "lyrics-" else "song-") + song.id)
            action(uri)
        }
    }

    // ---- layout sizes -----------------------------------------------------------------------
    val config = LocalConfiguration.current
    val screenW = config.screenWidthDp.dp
    val screenH = config.screenHeightDp.dp
    val chrome = 300.dp + (if (hasLyrics) 48.dp else 0.dp) + (if (lyricsTab) 150.dp else 0.dp)
    val cardH = minOf(screenW * 0.56f * 16f / 9f, screenH - chrome).coerceAtLeast(180.dp)
    val cardW = cardH * 9f / 16f

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = SheetColor,
        contentColor = Color.White,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.35f)) },
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            // ---- card carousel: swipe to change the look ------------------------------------
            HorizontalPager(
                state = pagerState,
                contentPadding = PaddingValues(horizontal = (screenW - cardW) / 2),
                pageSize = PageSize.Fixed(cardW),
                pageSpacing = 12.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(cardH)
            ) { page ->
                SharePageCard(
                    page = page,
                    pagerState = pagerState,
                    width = cardW,
                    height = cardH,
                    renderKey = listOf(tab, selectedLines, cover),
                    ready = coverLoaded,
                    showEmptyHint = nothingToShare,
                    render = ::render,
                )
            }

            // ---- tabs -----------------------------------------------------------------------
            if (hasLyrics) {
                Spacer(12.dp)
                ShareTabs(
                    labels = listOf(stringResource(R.string.share_tab_song), stringResource(R.string.share_tab_lyrics)),
                    selectedIndex = tab,
                    onSelect = { tab = it },
                )
            }

            // ---- lyric line picker (lyrics tab only) -----------------------------------------
            if (lyricsTab) {
                Spacer(8.dp)
                LyricsPicker(lines = lines, selected = selected)
            }

            // ---- look: tap to jump the carousel to that theme --------------------------------
            Spacer(12.dp)
            ThemeDots(
                themes = themes,
                cover = cover,
                selectedIndex = pagerState.currentPage,
                onSelect = { index -> scope.launch { pagerState.animateScrollToPage(index) } },
            )

            Spacer(12.dp)
            HorizontalDivider(color = Color.White.copy(alpha = 0.12f))
            Spacer(14.dp)

            // ---- where to send it ------------------------------------------------------------
            ShareTargets(
                enabled = !nothingToShare,
                onCopyLink = { ShareUtils.copyLink(context, link) },
                onStory = { shareCurrent { uri -> ShareUtils.shareToStory(context, uri, link) } },
                onTarget = { target -> shareCurrent { uri -> ShareUtils.shareImageTo(context, uri, link, target) } },
                onMore = { shareCurrent { uri -> ShareUtils.shareImage(context, uri, link) } },
            )
            Spacer(8.dp)
        }
    }
}

@Composable
private fun Spacer(height: Dp) {
    androidx.compose.foundation.layout.Spacer(Modifier.height(height))
}

// ---------------------------------------------------------------------------------------------
//  Carousel page
// ---------------------------------------------------------------------------------------------
@Composable
private fun SharePageCard(
    page: Int,
    pagerState: PagerState,
    width: Dp,
    height: Dp,
    renderKey: Any,
    ready: Boolean,
    showEmptyHint: Boolean,
    render: suspend (Int) -> Bitmap?,
) {
    // Keep showing the previous image while the new one renders (no flicker when picking lyric lines).
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(renderKey, ready) {
        if (ready) bitmap = render(page)
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(width, height)
            .graphicsLayer {
                // neighbours of the centred card are a little smaller and dimmer
                val distance = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction)
                    .absoluteValue.coerceIn(0f, 1f)
                val scale = 1f - 0.08f * distance
                scaleX = scale
                scaleY = scale
                alpha = 1f - 0.35f * distance
            }
            .clip(RoundedCornerShape(24.dp))
            .background(CardPlaceholder)
    ) {
        val current = bitmap
        when {
            current != null && !showEmptyHint -> {
                val image = remember(current) { current.asImageBitmap() }
                Image(
                    bitmap = image,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            showEmptyHint -> Text(
                stringResource(R.string.share_pick_lines_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(24.dp)
            )
            else -> CircularProgressIndicator(
                color = Color.White,
                strokeWidth = 2.dp,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
//  Tabs  [ Song | Lyrics ]
// ---------------------------------------------------------------------------------------------
@Composable
private fun ShareTabs(labels: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(CircleShape)
            .background(ChipColor)
    ) {
        labels.forEachIndexed { index, label ->
            val isSelected = index == selectedIndex
            Text(
                text = label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) Color.Black else Color.White.copy(alpha = 0.85f),
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (isSelected) Color.White else Color.Transparent)
                    .clickable { onSelect(index) }
                    .padding(horizontal = 22.dp, vertical = 11.dp)
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
//  Lyric line picker
// ---------------------------------------------------------------------------------------------
@Composable
private fun LyricsPicker(lines: List<String>, selected: MutableList<Int>) {
    Text(
        text = stringResource(R.string.share_pick_lines, MAX_LYRIC_LINES),
        style = MaterialTheme.typography.labelMedium,
        color = Color.White.copy(alpha = 0.65f),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 2.dp)
    )
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 112.dp)
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.08f))
    ) {
        itemsIndexed(lines) { index, line ->
            val isSelected = index in selected
            Text(
                text = line,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isSelected) Color.White else Color.White.copy(alpha = 0.72f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 1.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isSelected) Color.White.copy(alpha = 0.2f) else Color.Transparent)
                    .clickable {
                        if (isSelected) selected.remove(index)
                        else if (selected.size < MAX_LYRIC_LINES) selected.add(index)
                    }
                    .padding(horizontal = 12.dp, vertical = 7.dp)
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
//  Look dots
// ---------------------------------------------------------------------------------------------
@Composable
private fun ThemeDots(
    themes: List<CardTheme>,
    cover: Bitmap?,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        contentPadding = PaddingValues(horizontal = 16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        itemsIndexed(themes) { index, theme ->
            val isSelected = index == selectedIndex
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .border(2.dp, if (isSelected) Color.White else Color.Transparent, CircleShape)
                    .clickable { onSelect(index) }
                    .padding(5.dp)
                    .clip(CircleShape)
                    .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape)
            ) {
                if (theme.useBlur && !theme.solidCard && cover != null) {
                    val image = remember(cover) { cover.asImageBitmap() }
                    Image(
                        bitmap = image,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Brush.verticalGradient(listOf(Color(theme.top), Color(theme.bottom))))
                    ) {
                        if (theme.solidCard) {
                            Box(
                                Modifier
                                    .size(12.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color.White)
                            )
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
//  Share targets row:  [ copy link ] [ story ] [ app ] [ app ] ... [ more ]
// ---------------------------------------------------------------------------------------------
@Composable
private fun ShareTargets(
    enabled: Boolean,
    onCopyLink: () -> Unit,
    onStory: () -> Unit,
    onTarget: (ShareUtils.Target) -> Unit,
    onMore: () -> Unit,
) {
    val context = LocalContext.current
    val targets = remember { ShareUtils.imageTargets(context) }
    val instagramIcon = remember { ShareUtils.instagramIcon(context)?.toBitmap(160, 160)?.asImageBitmap() }

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        item(key = "copy_link") {
            ShareTargetItem(label = stringResource(R.string.share_copy_link), enabled = true, onClick = onCopyLink) {
                Box(Modifier.fillMaxSize().background(ChipColor), contentAlignment = Alignment.Center) {
                    Icon(
                        painterResource(R.drawable.share_ic_link),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }
        }
        if (instagramIcon != null) {
            item(key = "story") {
                ShareTargetItem(label = stringResource(R.string.share_target_story), enabled = enabled, onClick = onStory) {
                    Image(bitmap = instagramIcon, contentDescription = null, modifier = Modifier.fillMaxSize())
                }
            }
        }
        itemsIndexed(targets, key = { _, t -> t.packageName }) { _, target ->
            val icon = remember(target.packageName) { target.icon.toBitmap(160, 160).asImageBitmap() }
            ShareTargetItem(label = target.label, enabled = enabled, onClick = { onTarget(target) }) {
                Image(bitmap = icon, contentDescription = null, modifier = Modifier.fillMaxSize())
            }
        }
        item(key = "more") {
            ShareTargetItem(label = stringResource(R.string.share_target_more), enabled = enabled, onClick = onMore) {
                Box(Modifier.fillMaxSize().background(ChipColor), contentAlignment = Alignment.Center) {
                    Icon(
                        painterResource(R.drawable.share),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ShareTargetItem(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(76.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(CircleShape)
        ) { icon() }
        androidx.compose.foundation.layout.Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.9f),
            modifier = Modifier.padding(horizontal = 2.dp)
        )
    }
}