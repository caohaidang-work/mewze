package com.caohaidang.edward.music.ui.share

import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.caohaidang.edward.music.R
import com.caohaidang.edward.music.lyrics.LyricsUtils
import com.caohaidang.edward.music.models.MediaMetadata
import com.caohaidang.edward.music.share.CardTheme
import com.caohaidang.edward.music.share.ShareCardRenderer
import com.caohaidang.edward.music.share.ShareUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val MAX_LYRIC_LINES = 8

@Composable
fun ShareSongDialog(
    song: MediaMetadata,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var cover by remember { mutableStateOf<Bitmap?>(null) }
    var coverLoaded by remember { mutableStateOf(false) }
    LaunchedEffect(song.thumbnailUrl) {
        cover = ShareCardRenderer.loadBitmap(context, song.thumbnailUrl, 800)
        coverLoaded = true
    }

    val themes = remember(cover) {
        listOf(CardTheme.fromArtwork(cover, solidCard = true), CardTheme.fromArtwork(cover)) + CardTheme.presets
    }
    var themeIndex by rememberSaveable { mutableIntStateOf(0) }
    val brand = stringResource(R.string.app_name)

    var preview by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(coverLoaded, cover, themeIndex) {
        if (!coverLoaded) return@LaunchedEffect
        preview = withContext(Dispatchers.Default) {
            ShareCardRenderer.renderSong(context, song, themes[themeIndex.coerceIn(themes.indices)], cover, brand)
        }
    }

    ShareScaffold(
        title = stringResource(R.string.share_song),
        preview = preview,
        themes = themes,
        themeIndex = themeIndex,
        onThemeIndexChange = { themeIndex = it },
        link = ShareUtils.musicLink(song.id),
        fileName = "song-${song.id}",
        onDismiss = onDismiss,
    )
}

@Composable
fun ShareLyricsDialog(
    song: MediaMetadata,
    lyrics: String,
    initialLine: String?,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val lines = remember(lyrics) { parseShareLines(lyrics) }

    var cover by remember { mutableStateOf<Bitmap?>(null) }
    var coverLoaded by remember { mutableStateOf(false) }
    LaunchedEffect(song.thumbnailUrl) {
        cover = ShareCardRenderer.loadBitmap(context, song.thumbnailUrl, 400)
        coverLoaded = true
    }

    val selected = remember(lines, initialLine) {
        mutableStateListOf<Int>().apply {
            val index = initialLine?.trim()?.let { lines.indexOf(it) } ?: -1

            if (index >= 0) add(index) else addAll(0 until minOf(3, lines.size))
        }
    }

    val themes = remember(cover) {
        listOf(CardTheme.fromArtwork(cover, solidCard = true), CardTheme.fromArtwork(cover)) + CardTheme.presets
    }
    var themeIndex by rememberSaveable { mutableIntStateOf(0) }
    val brand = stringResource(R.string.app_name)

    var preview by remember { mutableStateOf<Bitmap?>(null) }
    val selectedLines = selected.sorted().map { lines[it] }
    LaunchedEffect(coverLoaded, cover, themeIndex, selectedLines) {
        if (!coverLoaded || selectedLines.isEmpty()) {
            preview = null
            return@LaunchedEffect
        }
        preview = withContext(Dispatchers.Default) {
            ShareCardRenderer.renderLyrics(context, song, selectedLines, themes[themeIndex.coerceIn(themes.indices)], cover, brand)
        }
    }

    ShareScaffold(
        title = stringResource(R.string.share_lyrics),
        preview = preview,
        previewEmpty = selectedLines.isEmpty(),
        themes = themes,
        themeIndex = themeIndex,
        onThemeIndexChange = { themeIndex = it },
        link = ShareUtils.musicLink(song.id),
        fileName = "lyrics-${song.id}",
        onDismiss = onDismiss,
    ) { listMaxHeight ->
        val content = LocalContentColor.current
        Text(
            text = stringResource(R.string.share_pick_lines, MAX_LYRIC_LINES),
            style = MaterialTheme.typography.labelMedium,
            color = content.copy(alpha = 0.7f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 2.dp)
        )
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = listMaxHeight)
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(content.copy(alpha = 0.08f))
        ) {
            itemsIndexed(lines) { index, line ->
                val isSelected = index in selected
                Text(
                    text = line,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isSelected) content else content.copy(alpha = 0.72f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 1.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) content.copy(alpha = 0.2f) else Color.Transparent)
                        .clickable {
                            if (isSelected) selected.remove(index)
                            else if (selected.size < MAX_LYRIC_LINES) selected.add(index)
                        }
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                )
            }
        }
    }
}

private fun parseShareLines(lyrics: String): List<String> {
    val raw = if (lyrics.startsWith("[")) LyricsUtils.parseLyrics(lyrics).map { it.text } else lyrics.lines()
    return raw.map { it.trim() }.filter { it.isNotEmpty() }
}

@Composable
private fun ShareScaffold(
    title: String,
    preview: Bitmap?,
    themes: List<CardTheme>,
    themeIndex: Int,
    onThemeIndexChange: (Int) -> Unit,
    link: String?,
    fileName: String,
    onDismiss: () -> Unit,
    previewEmpty: Boolean = false,
    extra: @Composable (listMaxHeight: Dp) -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val current = themes[themeIndex.coerceIn(themes.indices)]
    val topColor by animateColorAsState(Color(current.top), label = "shareTop")
    val bottomColor by animateColorAsState(Color(current.bottom), label = "shareBottom")
    val contentColor by animateColorAsState(Color(current.text), label = "shareContent")

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(topColor, bottomColor)))
        ) {
            // Everything below the preview has a bounded height, so the preview is the only
            // part that shrinks on short screens and the action buttons are never pushed off.
            val pickerMaxHeight = (maxHeight * 0.2f).coerceIn(84.dp, 168.dp)

            CompositionLocalProvider(LocalContentColor provides contentColor) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.systemBars)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        IconButton(onClick = onDismiss) {
                            Icon(painterResource(R.drawable.close), contentDescription = null)
                        }
                        Text(
                            title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    BoxWithConstraints(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 8.dp)
                    ) {
                        if (preview != null) {
                            val cardHeight = minOf(maxHeight, maxWidth * 16f / 9f).coerceAtLeast(0.dp)
                            val cardWidth = cardHeight * 9f / 16f
                            val radius = minOf(24.dp, cardWidth / 10f)
                            Image(
                                bitmap = preview.asImageBitmap(),
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .size(cardWidth, cardHeight)
                                    .shadow(16.dp, RoundedCornerShape(radius))
                                    .clip(RoundedCornerShape(radius))
                            )
                        } else if (previewEmpty) {
                            Text(
                                stringResource(R.string.share_pick_lines_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = contentColor.copy(alpha = 0.7f)
                            )
                        } else {
                            CircularProgressIndicator(color = contentColor)
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 24.dp, vertical = 6.dp)
                    ) {
                        themes.forEachIndexed { index, theme ->
                            val selectedTheme = index == themeIndex
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(if (selectedTheme) 36.dp else 30.dp)
                                    .clip(CircleShape)
                                    .background(Brush.verticalGradient(listOf(Color(theme.top), Color(theme.bottom))))
                                    .border(
                                        width = if (selectedTheme) 3.dp else 1.dp,
                                        color = if (selectedTheme) contentColor else contentColor.copy(alpha = 0.35f),
                                        shape = CircleShape
                                    )
                                    .clickable { onThemeIndexChange(index) }
                            ) {
                                if (theme.solidCard) {
                                    Box(
                                        modifier = Modifier
                                            .size(13.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(Color.White)
                                    )
                                }
                            }
                        }
                    }

                    extra(pickerMaxHeight)

                    val canShare = preview != null
                    val pillColors = ButtonDefaults.buttonColors(
                        containerColor = contentColor,
                        contentColor = bottomColor,
                    )
                    val buttonPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 10.dp)
                    ) {
                        Button(
                            enabled = canShare,
                            colors = pillColors,
                            shape = CircleShape,
                            contentPadding = buttonPadding,
                            onClick = {
                                val bitmap = preview ?: return@Button
                                scope.launch {
                                    val uri = ShareUtils.saveToCache(context, bitmap, fileName)
                                    ShareUtils.shareToStory(context, uri, link)
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                        ) {
                            Text(
                                stringResource(R.string.share_to_story),
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val outlineColors = ButtonDefaults.outlinedButtonColors(contentColor = contentColor)
                            val outline = BorderStroke(1.dp, contentColor.copy(alpha = 0.45f))
                            OutlinedButton(
                                enabled = canShare,
                                colors = outlineColors,
                                border = outline,
                                shape = CircleShape,
                                contentPadding = buttonPadding,
                                onClick = {
                                    val bitmap = preview ?: return@OutlinedButton
                                    scope.launch {
                                        val uri = ShareUtils.saveToCache(context, bitmap, fileName)
                                        ShareUtils.shareImage(context, uri, link)
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                            ) { Text(stringResource(R.string.share_as_image), maxLines = 1, overflow = TextOverflow.Ellipsis) }

                            if (link != null) {
                                OutlinedButton(
                                    colors = outlineColors,
                                    border = outline,
                                    shape = CircleShape,
                                    contentPadding = buttonPadding,
                                    onClick = { ShareUtils.shareLink(context, link) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp)
                                ) { Text(stringResource(R.string.share_link), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                            }
                        }
                    }
                }
            }
        }
    }
}
