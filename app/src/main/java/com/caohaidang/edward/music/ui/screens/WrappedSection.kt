package com.caohaidang.edward.music.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import com.caohaidang.edward.music.ui.component.glassCard
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.caohaidang.edward.music.R
import com.caohaidang.edward.music.constants.WrappedPeriod
import com.caohaidang.edward.music.share.CardTheme
import com.caohaidang.edward.music.share.ShareCardRenderer
import com.caohaidang.edward.music.share.ShareUtils
import com.caohaidang.edward.music.share.WrappedImages
import com.caohaidang.edward.music.share.WrappedTexts
import com.caohaidang.edward.music.ui.component.ChipsRow
import com.caohaidang.edward.music.viewmodels.WrappedData
import com.caohaidang.edward.music.viewmodels.WrappedViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val SLIDE_COUNT = 3

/** Toàn bộ mục Wrapped (giao diện + ảnh chia sẻ) luôn hiển thị bằng tiếng Anh. */
@Composable
fun WrappedSection(
    navController: NavController,
    modifier: Modifier = Modifier,
    viewModel: WrappedViewModel = hiltViewModel(),
) {
    val base = LocalContext.current
    val english = remember(base) {
        val cfg = android.content.res.Configuration(base.resources.configuration)
        cfg.setLocale(Locale.ENGLISH)
        base.createConfigurationContext(cfg)
    }
    androidx.compose.runtime.CompositionLocalProvider(
        LocalContext provides english,
        androidx.compose.ui.platform.LocalConfiguration provides english.resources.configuration,
    ) {
        WrappedSectionContent(navController, modifier, viewModel)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WrappedSectionContent(
    navController: NavController,
    modifier: Modifier = Modifier,
    viewModel: WrappedViewModel,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val period by viewModel.period.collectAsState()
    val offset by viewModel.offset.collectAsState()
    val data by viewModel.data.collectAsState()
    val loading by viewModel.loading.collectAsState()

    LaunchedEffect(Unit) { viewModel.refresh() }

    val current = data?.takeIf { it.period == period && it.offset == offset }

    // Ảnh chia sẻ Wrapped luôn dùng tiếng Anh
    val enContext = remember(context) {
        val cfg = android.content.res.Configuration(context.resources.configuration)
        cfg.setLocale(Locale.ENGLISH)
        context.createConfigurationContext(cfg)
    }
    val brand = enContext.getString(R.string.wrapped_brand)
    val minutesUnit = enContext.getString(R.string.wrapped_minutes_unit)
    val topSongLabel = enContext.getString(R.string.wrapped_top_song)
    val topSongsTitle = enContext.getString(R.string.wrapped_top_songs)
    val topArtistsTitle = enContext.getString(R.string.wrapped_top_artists)
    val minutesFmt = R.string.wrapped_minutes_fmt
    val playsFmt = R.string.wrapped_plays_fmt
    val periodLabel = current?.let { periodLabel(it) }.orEmpty()
    val songsCount = current?.let { stringResource(R.string.wrapped_songs_count, it.songCount) }.orEmpty()
    val busiestDay = current?.busiestDay?.let {
        stringResource(R.string.wrapped_busiest_day, DateTimeFormatter.ofPattern("EEEE, dd/MM", Locale.ENGLISH).format(it))
    }
    val topArtistLine = current?.topArtists?.firstOrNull()?.let { stringResource(R.string.wrapped_top_artist_line, it.name) }

    var slides by remember { mutableStateOf<List<Bitmap>?>(null) }
    LaunchedEffect(current, periodLabel, songsCount, busiestDay, topArtistLine, brand) {
        val d = current
        if (d == null || d.isEmpty) {
            slides = null
            return@LaunchedEffect
        }
        slides = null
        val texts = WrappedTexts(
            brand = brand,
            periodLabel = rangeLabel(d),
            minutesUnit = minutesUnit,
            songsCount = songsCount,
            busiestDay = busiestDay,
            topArtistLine = topArtistLine,
            topSongLabel = topSongLabel,
            topSongsTitle = topSongsTitle,
            topArtistsTitle = topArtistsTitle,
            minutesText = { m -> enContext.getString(minutesFmt, m) },
            playsText = { n -> enContext.getString(playsFmt, n) },
        )
        val songCovers = d.topSongs.map { ShareCardRenderer.loadBitmap(context, it.song.thumbnailUrl, 320) }
        val artistAvatars = d.topArtists.map { artist ->
            ShareCardRenderer.loadBitmap(context, artist.thumbnailUrl, 320)
        }
        val images = WrappedImages(
            topCover = d.topSongs.firstOrNull()?.song?.thumbnailUrl?.let { ShareCardRenderer.loadBitmap(context, it, 600) },
            songCovers = songCovers,
            artistAvatars = artistAvatars,
        )
        val theme = when (d.period) {
            WrappedPeriod.WEEK -> CardTheme.WrappedWeek
            WrappedPeriod.MONTH -> CardTheme.WrappedMonth
            WrappedPeriod.YEAR -> CardTheme.WrappedYear
        }
        slides = withContext(Dispatchers.Default) {
            listOf(
                ShareCardRenderer.renderWrappedOverview(
                    context, d.totalMillis / 60_000, d.topSongs.firstOrNull()?.song, d.topSongs.firstOrNull()?.playCount ?: 0,
                    d.topSongs.map { it.song to it.playCount }, texts, images, theme
                ),
                ShareCardRenderer.renderWrappedSongs(context, d.topSongs.map { it.song to it.playCount }, texts, images, theme),
                ShareCardRenderer.renderWrappedArtists(context, d.topArtists.map { it.name to it.playTime }, texts, images, theme),
            )
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 12.dp)
        ) {
            Text(
                stringResource(R.string.wrapped),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = { navController.navigate("stats") }, shape = CircleShape) {
                Text(stringResource(R.string.wrapped_details))
            }
        }

        Spacer(Modifier.height(4.dp))
        ChipsRow(
            chips = listOf(
                WrappedPeriod.WEEK to stringResource(R.string.wrapped_week),
                WrappedPeriod.MONTH to stringResource(R.string.wrapped_month),
                WrappedPeriod.YEAR to stringResource(R.string.wrapped_year),
            ),
            currentValue = period,
            onValueUpdate = viewModel::setPeriod
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .glassCard(CircleShape)
        ) {
            IconButton(onClick = viewModel::previous) {
                Icon(painterResource(R.drawable.arrow_back), contentDescription = null)
            }
            Text(
                text = current?.let { periodLabel(it) } ?: "…",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = viewModel::next, enabled = offset < 0) {
                Icon(painterResource(R.drawable.arrow_forward), contentDescription = null)
            }
        }

        val bitmaps = slides
        when {
            bitmaps != null -> {
                val pagerState = rememberPagerState(pageCount = { SLIDE_COUNT })
                HorizontalPager(
                    state = pagerState,
                    contentPadding = PaddingValues(horizontal = 72.dp),
                    pageSpacing = 12.dp,
                    modifier = Modifier.fillMaxWidth()
                ) { page ->
                    Image(
                        bitmap = bitmaps[page].asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(9f / 16f)
                            .clip(RoundedCornerShape(28.dp))
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp)
                ) {
                    repeat(SLIDE_COUNT) { i ->
                        Box(
                            Modifier
                                .size(if (i == pagerState.currentPage) 8.dp else 6.dp)
                                .clip(CircleShape)
                                .background(
                                    if (i == pagerState.currentPage) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outlineVariant
                                )
                        )
                    }
                }

                val link = if (pagerState.currentPage == 1) {
                    current?.topSongs?.firstOrNull()?.song?.id?.let(ShareUtils::musicLink)
                } else null
                val fileName = "wrapped-${period.name.lowercase()}-${pagerState.currentPage + 1}"

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Button(
                        onClick = {
                            scope.launch {
                                val uri = ShareUtils.saveToCache(context, bitmaps[pagerState.currentPage], fileName)
                                ShareUtils.shareToStory(context, uri, link)
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text(stringResource(R.string.share_to_story), maxLines = 1) }
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                val uri = ShareUtils.saveToCache(context, bitmaps[pagerState.currentPage], fileName)
                                ShareUtils.shareImage(context, uri, link)
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text(stringResource(R.string.share_as_image), maxLines = 1) }
                }
            }

            loading || (current != null && !current.isEmpty) -> {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                ) { CircularProgressIndicator() }
            }

            else -> {
                Text(
                    text = stringResource(R.string.wrapped_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 32.dp)
                )
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun periodLabel(data: WrappedData): String {
    val last = data.end.minusDays(1).toLocalDate()
    val first = data.start.toLocalDate()
    val locale = Locale.ENGLISH
    val range = when (data.period) {
        WrappedPeriod.WEEK -> {
            val f = DateTimeFormatter.ofPattern("dd/MM", locale)
            "${f.format(first)} – ${f.format(last)}"
        }
        WrappedPeriod.MONTH -> DateTimeFormatter.ofPattern("LLLL yyyy", locale).format(first)
        WrappedPeriod.YEAR -> first.year.toString()
    }
    if (data.offset != 0) return range
    val prefix = when (data.period) {
        WrappedPeriod.WEEK -> stringResource(R.string.wrapped_this_week)
        WrappedPeriod.MONTH -> stringResource(R.string.wrapped_this_month)
        WrappedPeriod.YEAR -> stringResource(R.string.wrapped_this_year)
    }
    return "$prefix · $range"
}


/** Khoảng thời gian đầy đủ "dd/MM/yyyy – dd/MM/yyyy" dùng cho ảnh chia sẻ. */
private fun rangeLabel(data: WrappedData): String {
    val f = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
    val first = data.start.toLocalDate()
    val last = data.end.minusDays(1).toLocalDate()
    return "${f.format(first)} – ${f.format(last)}"
}
