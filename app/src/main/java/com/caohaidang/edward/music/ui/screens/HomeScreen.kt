package com.caohaidang.edward.music.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults.Indicator
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.caohaidang.edward.innertube.models.AlbumItem
import com.caohaidang.edward.innertube.models.ArtistItem
import com.caohaidang.edward.innertube.models.PlaylistItem
import com.caohaidang.edward.innertube.models.SongItem
import com.caohaidang.edward.innertube.models.WatchEndpoint
import com.caohaidang.edward.innertube.models.YTItem
import com.caohaidang.edward.innertube.pages.ExplorePage
import com.caohaidang.edward.innertube.pages.HomePage
import com.caohaidang.edward.innertube.pages.MoodAndGenres
import com.caohaidang.edward.music.LocalPlayerAwareWindowInsets
import com.caohaidang.edward.music.LocalPlayerConnection
import com.caohaidang.edward.music.db.entities.Album
import com.caohaidang.edward.music.db.entities.Artist
import com.caohaidang.edward.music.db.entities.LocalItem
import com.caohaidang.edward.music.db.entities.Playlist
import com.caohaidang.edward.music.db.entities.Song
import com.caohaidang.edward.music.extensions.togglePlayPause
import com.caohaidang.edward.music.models.SimilarRecommendation
import com.caohaidang.edward.music.models.toMediaMetadata
import com.caohaidang.edward.music.playback.queues.YouTubeAlbumRadio
import com.caohaidang.edward.music.playback.queues.YouTubeQueue
import com.caohaidang.edward.music.ui.component.GlassSectionHeader
import com.caohaidang.edward.music.ui.component.HomeChartRow
import com.caohaidang.edward.music.ui.component.HomeCover
import com.caohaidang.edward.music.ui.component.HomeExpandButton
import com.caohaidang.edward.music.ui.component.HomeHeroPager
import com.caohaidang.edward.music.ui.component.HomeSectionTitle
import com.caohaidang.edward.music.ui.component.NewReleasePager
import com.caohaidang.edward.music.ui.component.GlassChip
import com.caohaidang.edward.music.ui.component.GlassSquareCard
import com.caohaidang.edward.music.ui.component.LikedMusicCover
import com.caohaidang.edward.music.ui.component.GlassTile
import com.caohaidang.edward.music.ui.component.GlassWideCard
import com.caohaidang.edward.music.ui.component.LocalMenuState
import com.caohaidang.edward.music.ui.component.shimmer.GridItemPlaceHolder
import com.caohaidang.edward.music.ui.component.shimmer.ShimmerHost
import com.caohaidang.edward.music.ui.component.shimmer.TextPlaceholder
import com.caohaidang.edward.music.ui.menu.AlbumMenu
import com.caohaidang.edward.music.ui.menu.PlaylistMenu
import com.caohaidang.edward.music.ui.menu.SongMenu
import com.caohaidang.edward.music.ui.menu.YouTubeAlbumMenu
import com.caohaidang.edward.music.ui.menu.YouTubeArtistMenu
import com.caohaidang.edward.music.ui.menu.YouTubePlaylistMenu
import com.caohaidang.edward.music.ui.menu.YouTubeSongMenu
import com.caohaidang.edward.music.viewmodels.HeroTarget
import com.caohaidang.edward.music.viewmodels.HomeViewModel
import java.util.Calendar
import java.util.Locale
import kotlin.math.min

private const val RECENT_TILE_COUNT = 12
private const val CHART_PREVIEW_COUNT = 5
private const val MAX_RECOMMEND_BLOCKS = 10

private data class HomeTile(
    val key: String,
    val title: String,
    val subtitle: String?,
    val thumbnail: String?,
    val isActive: Boolean,
    val onClick: () -> Unit,
    val onLongClick: () -> Unit,
)

private sealed interface RecBlock {
    val key: String

    class QuickPicks(val songs: List<Song>) : RecBlock {
        override val key = "quick"
    }

    class Local(
        override val key: String,
        val title: String,
        val label: String?,
        val items: List<LocalItem>,
    ) : RecBlock

    class NewReleases(val albums: List<AlbumItem>) : RecBlock {
        override val key = "new_releases"
    }

    class Moods(val items: List<MoodAndGenres.Item>) : RecBlock {
        override val key = "moods"
    }

    class Items(
        override val key: String,
        val title: String,
        val label: String?,
        val items: List<YTItem>,
        val wide: Boolean,
        val onTitleClick: (() -> Unit)? = null,
    ) : RecBlock
}

private fun YTItem.subtitleText(): String? = when (this) {
    is SongItem -> artists.joinToString { it.name }.ifEmpty { null }
    is AlbumItem -> listOfNotNull(
        artists?.joinToString { it.name }?.takeIf { it.isNotEmpty() },
        year?.toString()
    ).joinToString(" • ").ifEmpty { null }

    is ArtistItem -> "Nghệ sĩ"
    is PlaylistItem -> listOfNotNull(author?.name, songCountText).joinToString(" • ").ifEmpty { null }
}

private fun buildBlocks(
    quickPicks: List<Song>?,
    keepListening: List<LocalItem>?,
    forgotten: List<Song>?,
    similar: List<SimilarRecommendation>?,
    accountPlaylists: List<PlaylistItem>?,
    homePage: HomePage?,
    explorePage: ExplorePage?,
    newForYou: List<AlbumItem>?,
    navController: NavController,
): List<RecBlock> {
    val out = mutableListOf<RecBlock>()
    fun add(block: RecBlock) {
        if (out.size < MAX_RECOMMEND_BLOCKS) out += block
    }

    val similarQueue = ArrayDeque(similar.orEmpty())
    val sectionQueue = ArrayDeque(homePage?.sections.orEmpty().filter { it.items.isNotEmpty() })

    fun similarBlock(r: SimilarRecommendation, wide: Boolean) = RecBlock.Items(
        key = "similar_${r.title.id}",
        title = r.title.title,
        label = "Vì bạn đã nghe",
        items = r.items,
        wide = wide,
        onTitleClick = {
            when (val t = r.title) {
                is Song -> t.album?.id?.let { navController.navigate("album/$it") }
                is Album -> navController.navigate("album/${t.id}")
                is Artist -> navController.navigate("artist/${t.id}")
                is Playlist -> Unit
            }
        }
    )

    fun sectionBlock(index: Int, s: HomePage.Section, wide: Boolean) = RecBlock.Items(
        key = "section_${index}_${s.title}",
        title = s.title,
        label = s.label,
        items = s.items,
        wide = wide,
    )

    var sectionIndex = 0

    quickPicks?.takeIf { it.isNotEmpty() }?.let { add(RecBlock.QuickPicks(it.take(18))) }

    newForYou?.takeIf { it.isNotEmpty() }?.let { add(RecBlock.NewReleases(it)) }

    similarQueue.removeFirstOrNull()?.let { add(similarBlock(it, wide = false)) }

    if (!accountPlaylists.isNullOrEmpty()) {
        add(
            RecBlock.Items(
                key = "account",
                title = "Playlist của bạn",
                label = "YouTube Music",
                items = accountPlaylists,
                wide = false,
                onTitleClick = { navController.navigate("account") }
            )
        )
    } else {
        similarQueue.removeFirstOrNull()?.let { add(similarBlock(it, wide = true)) }
    }

    explorePage?.moodAndGenres?.takeIf { it.isNotEmpty() }?.let { add(RecBlock.Moods(it.take(16))) }

    keepListening?.takeIf { it.size >= 3 }?.let {
        add(RecBlock.Local("keep_listening", "Tiếp tục nghe", "Từ thư viện của bạn", it.take(15)))
    }

    sectionQueue.removeFirstOrNull()?.let { add(sectionBlock(sectionIndex++, it, wide = true)) }

    forgotten?.takeIf { it.size >= 3 }?.let {
        add(RecBlock.Local("forgotten", "Bài hát bạn đã quên", "Nghe lại nào", it.take(15)))
    }

    sectionQueue.removeFirstOrNull()?.let { add(sectionBlock(sectionIndex++, it, wide = false)) }

    while (out.size < MAX_RECOMMEND_BLOCKS) {
        val sim = similarQueue.removeFirstOrNull()
        if (sim != null) {
            add(similarBlock(sim, wide = out.size % 2 == 1))
            continue
        }
        val sec = sectionQueue.removeFirstOrNull() ?: break
        add(sectionBlock(sectionIndex++, sec, wide = out.size % 2 == 1))
    }
    return out
}

private fun LazyListScope.chartSection(
    keyPrefix: String,
    title: String,
    songs: List<SongItem>,
    expanded: Boolean,
    onToggle: () -> Unit,
    isActive: (SongItem) -> Boolean,
    onPlay: (SongItem) -> Unit,
    onMenu: (SongItem) -> Unit,
) {
    if (songs.isEmpty()) return
    item(key = "h_$keyPrefix") {
        HomeSectionTitle(title = title, modifier = Modifier.padding(top = 10.dp))
    }
    val shown = if (expanded) songs else songs.take(CHART_PREVIEW_COUNT)
    itemsIndexed(shown, key = { _, song -> "${keyPrefix}_${song.id}" }) { index, song ->
        HomeChartRow(
            rank = index + 1,
            title = song.title,
            subtitle = song.artists.joinToString { it.name }.ifEmpty { null },
            thumbnailUrl = song.thumbnail,
            isActive = isActive(song),
            onClick = { onPlay(song) },
            onLongClick = { onMenu(song) },
        )
    }
    if (songs.size > CHART_PREVIEW_COUNT) {
        item(key = "t_$keyPrefix") {
            HomeExpandButton(expanded = expanded, onClick = onToggle)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavController,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val menuState = LocalMenuState.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val haptic = LocalHapticFeedback.current

    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()

    val recentItems by viewModel.recentItems.collectAsState()
    val quickPicks by viewModel.quickPicks.collectAsState()
    val keepListening by viewModel.keepListening.collectAsState()
    val forgottenFavorites by viewModel.forgottenFavorites.collectAsState()
    val similarRecommendations by viewModel.similarRecommendations.collectAsState()
    val accountPlaylists by viewModel.accountPlaylists.collectAsState()
    val homePage by viewModel.homePage.collectAsState()
    val explorePage by viewModel.explorePage.collectAsState()
    val newForYou by viewModel.newForYou.collectAsState()
    val heroSlides by viewModel.heroSlides.collectAsState()
    val countryChart by viewModel.countryChart.collectAsState()
    val globalChart by viewModel.globalChart.collectAsState()

    var countryChartExpanded by rememberSaveable { mutableStateOf(false) }
    var globalChartExpanded by rememberSaveable { mutableStateOf(false) }

    val isLoading by viewModel.isLoading.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val pullRefreshState = rememberPullToRefreshState()

    val scope = rememberCoroutineScope()
    val lazylistState = rememberLazyListState()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val scrollToTop = backStackEntry?.savedStateHandle?.getStateFlow("scrollToTop", false)?.collectAsState()

    LaunchedEffect(scrollToTop?.value) {
        if (scrollToTop?.value == true) {
            lazylistState.animateScrollToItem(0)
            backStackEntry?.savedStateHandle?.set("scrollToTop", false)
        }
    }

    val greeting = remember {
        when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in 5..10 -> "Chào buổi sáng"
            in 11..17 -> "Chào buổi chiều"
            else -> "Chào buổi tối"
        }
    }

    val playSong: (Song) -> Unit = { song ->
        if (song.id == mediaMetadata?.id) {
            playerConnection.player.togglePlayPause()
        } else {
            playerConnection.playQueue(YouTubeQueue.radio(song.toMediaMetadata()))
        }
    }
    val showSongMenu: (Song) -> Unit = { song ->
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        menuState.show {
            SongMenu(
                originalSong = song,
                navController = navController,
                onDismiss = menuState::dismiss
            )
        }
    }
    val showAlbumMenu: (Album) -> Unit = { album ->
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        menuState.show {
            AlbumMenu(
                originalAlbum = album,
                navController = navController,
                onDismiss = menuState::dismiss
            )
        }
    }
    val showPlaylistMenu: (Playlist) -> Unit = { playlist ->
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        menuState.show {
            PlaylistMenu(
                playlist = playlist,
                coroutineScope = scope,
                onDismiss = menuState::dismiss
            )
        }
    }
    val onYtClick: (YTItem) -> Unit = { item ->
        when (item) {
            is SongItem -> playerConnection.playQueue(
                YouTubeQueue(item.endpoint ?: WatchEndpoint(videoId = item.id), item.toMediaMetadata())
            )

            is AlbumItem -> navController.navigate("album/${item.id}")
            is ArtistItem -> navController.navigate("artist/${item.id}")
            is PlaylistItem -> navController.navigate("online_playlist/${item.id}")
        }
    }
    val onYtLongClick: (YTItem) -> Unit = { item ->
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
                    coroutineScope = scope,
                    onDismiss = menuState::dismiss
                )
            }
        }
    }

    val recentTiles: List<HomeTile> = recentItems.orEmpty().mapNotNull { item ->
        when (item) {
            is Song -> HomeTile(
                key = "song_${item.id}",
                title = item.title,
                subtitle = item.artists.joinToString { it.name }.ifEmpty { null },
                thumbnail = item.thumbnailUrl,
                isActive = item.id == mediaMetadata?.id,
                onClick = { playSong(item) },
                onLongClick = { showSongMenu(item) }
            )

            is Album -> HomeTile(
                key = "album_${item.id}",
                title = item.title,
                subtitle = "Album",
                thumbnail = item.thumbnailUrl,
                isActive = item.id == mediaMetadata?.album?.id,
                onClick = { navController.navigate("album/${item.id}") },
                onLongClick = { showAlbumMenu(item) }
            )

            is Playlist -> HomeTile(
                key = "playlist_${item.id}",
                title = item.title,
                subtitle = "Playlist",
                thumbnail = item.displayThumbnails.firstOrNull(),
                isActive = false,
                onClick = { navController.navigate("local_playlist/${item.id}") },
                onLongClick = { showPlaylistMenu(item) }
            )

            else -> null
        }
    }.distinctBy { it.key }.take(RECENT_TILE_COUNT)

    val onHeroPlay: (com.caohaidang.edward.music.viewmodels.HeroSlide) -> Unit = { slide ->
        when (val target = slide.target) {
            is HeroTarget.LocalSong -> playSong(target.song)
            is HeroTarget.OnlineSong -> onYtClick(target.item)
            is HeroTarget.OnlineAlbum -> playerConnection.playQueue(YouTubeAlbumRadio(target.item.playlistId))
            is HeroTarget.ArtistPage -> navController.navigate("artist/${target.artistId}")
        }
    }

    val countryName = remember(countryChart?.country) {
        countryChart?.country?.let { Locale("", it).getDisplayCountry(Locale("vi")) }.orEmpty()
    }

    val blocks = remember(
        quickPicks, keepListening, forgottenFavorites,
        similarRecommendations, accountPlaylists, homePage, explorePage, newForYou
    ) {
        buildBlocks(
            quickPicks, keepListening, forgottenFavorites,
            similarRecommendations, accountPlaylists, homePage, explorePage, newForYou, navController
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pullToRefresh(
                state = pullRefreshState,
                isRefreshing = isRefreshing,
                onRefresh = viewModel::refresh
            ),
        contentAlignment = Alignment.TopStart
    ) {
        val screenWidth = LocalConfiguration.current.screenWidthDp.dp
        val quickItemWidth = if (screenWidth * 0.475f >= 300.dp) screenWidth * 0.475f else screenWidth * 0.86f

        LazyColumn(
            state = lazylistState,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(
                bottom = LocalPlayerAwareWindowInsets.current.asPaddingValues().calculateBottomPadding()
            )
        ) {
            if (heroSlides.isNotEmpty()) {
                item(key = "hero") {
                    HomeHeroPager(
                        slides = heroSlides,
                        onPlay = onHeroPlay,
                        modifier = Modifier.animateItem()
                    )
                }
            } else {
                // Nothing to suggest yet (loading / offline): keep the page clear of the status bar.
                item(key = "greeting") {
                    Text(
                        text = greeting,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-1).sp,
                        modifier = Modifier
                            .padding(
                                start = 24.dp,
                                end = 24.dp,
                                top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 16.dp,
                                bottom = 8.dp
                            )
                            .animateItem()
                    )
                }
            }

            if (recentTiles.isNotEmpty()) {
                item(key = "h_recent") {
                    HomeSectionTitle(
                        title = "Gần đây phát",
                        onClick = { navController.navigate("history") },
                        modifier = Modifier.animateItem()
                    )
                }
                item(key = "row_recent") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.animateItem()
                    ) {
                        items(recentTiles, key = { it.key }) { tile ->
                            HomeCover(
                                title = tile.title,
                                subtitle = tile.subtitle,
                                thumbnailUrl = tile.thumbnail,
                                isActive = tile.isActive,
                                onClick = tile.onClick,
                                onLongClick = tile.onLongClick,
                            )
                        }
                    }
                }
            }

            countryChart?.let { chart ->
                chartSection(
                    keyPrefix = "chart_country",
                    title = if (countryName.isNotEmpty()) "Thịnh hành tại $countryName" else "Thịnh hành trong nước",
                    songs = chart.songs,
                    expanded = countryChartExpanded,
                    onToggle = { countryChartExpanded = !countryChartExpanded },
                    isActive = { it.id == mediaMetadata?.id },
                    onPlay = { onYtClick(it) },
                    onMenu = { onYtLongClick(it) },
                )
            }
            globalChart?.let { chart ->
                chartSection(
                    keyPrefix = "chart_global",
                    title = "Thịnh hành quốc tế",
                    songs = chart.songs,
                    expanded = globalChartExpanded,
                    onToggle = { globalChartExpanded = !globalChartExpanded },
                    isActive = { it.id == mediaMetadata?.id },
                    onPlay = { onYtClick(it) },
                    onMenu = { onYtLongClick(it) },
                )
            }

            blocks.forEach { block ->
                when (block) {
                    is RecBlock.QuickPicks -> {
                        item(key = "h_${block.key}") {
                            GlassSectionHeader(
                                title = "Phát nhanh",
                                label = "Dành cho bạn",
                                modifier = Modifier
                                    .padding(top = 10.dp)
                                    .animateItem()
                            )
                        }
                        item(key = "row_${block.key}") {
                            val rows = min(3, block.songs.size)
                            LazyHorizontalGrid(
                                rows = GridCells.Fixed(rows),
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(64.dp * rows + 10.dp * (rows - 1))
                                    .animateItem()
                            ) {
                                items(block.songs, key = { it.id }) { song ->
                                    GlassTile(
                                        title = song.title,
                                        subtitle = song.artists.joinToString { it.name }.ifEmpty { null },
                                        thumbnailUrl = song.thumbnailUrl,
                                        isActive = song.id == mediaMetadata?.id,
                                        onClick = { playSong(song) },
                                        onLongClick = { showSongMenu(song) },
                                        modifier = Modifier.width(quickItemWidth)
                                    )
                                }
                            }
                        }
                    }

                    is RecBlock.Moods -> {
                        item(key = "h_${block.key}") {
                            GlassSectionHeader(
                                title = "Tâm trạng và thể loại",
                                label = "Khám phá",
                                onClick = { navController.navigate("mood_and_genres") },
                                modifier = Modifier
                                    .padding(top = 10.dp)
                                    .animateItem()
                            )
                        }
                        item(key = "row_${block.key}") {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.animateItem()
                            ) {
                                items(block.items) { mood ->
                                    GlassChip(
                                        text = mood.title,
                                        onClick = {
                                            navController.navigate(
                                                "youtube_browse/${mood.endpoint.browseId}?params=${mood.endpoint.params}"
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }

                    is RecBlock.Local -> {
                        item(key = "h_${block.key}") {
                            GlassSectionHeader(
                                title = block.title,
                                label = block.label,
                                modifier = Modifier
                                    .padding(top = 10.dp)
                                    .animateItem()
                            )
                        }
                        item(key = "row_${block.key}") {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.animateItem()
                            ) {
                                items(block.items) { item ->
                                    GlassSquareCard(
                                        title = item.title,
                                        subtitle = when (item) {
                                            is Song -> item.artists.joinToString { it.name }.ifEmpty { null }
                                            is Album -> "Album"
                                            is Artist -> "Nghệ sĩ"
                                            else -> null
                                        },
                                        thumbnailUrl = item.thumbnailUrl,
                                        circle = item is Artist,
                                        isActive = item.id in listOf(mediaMetadata?.album?.id, mediaMetadata?.id),
                                        onClick = {
                                            when (item) {
                                                is Song -> playSong(item)
                                                is Album -> navController.navigate("album/${item.id}")
                                                is Artist -> navController.navigate("artist/${item.id}")
                                                else -> Unit
                                            }
                                        },
                                        onLongClick = {
                                            when (item) {
                                                is Song -> showSongMenu(item)
                                                is Album -> showAlbumMenu(item)
                                                else -> Unit
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }

                    is RecBlock.NewReleases -> {
                        item(key = "h_${block.key}") {
                            GlassSectionHeader(
                                title = "Mới ra mắt",
                                label = "Chưa nghe, dành cho bạn",
                                onClick = { navController.navigate("new_release") },
                                modifier = Modifier
                                    .padding(top = 10.dp)
                                    .animateItem()
                            )
                        }
                        item(key = "row_${block.key}") {
                            NewReleasePager(
                                items = block.albums,
                                onClick = { onYtClick(it) },
                                onLongClick = { onYtLongClick(it) },
                                modifier = Modifier.animateItem()
                            )
                        }
                    }

                    is RecBlock.Items -> {
                        item(key = "h_${block.key}") {
                            GlassSectionHeader(
                                title = block.title,
                                label = block.label,
                                onClick = block.onTitleClick,
                                modifier = Modifier
                                    .padding(top = 10.dp)
                                    .animateItem()
                            )
                        }
                        item(key = "row_${block.key}") {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.animateItem()
                            ) {
                                items(block.items) { item ->
                                    val active = item.id in listOf(mediaMetadata?.album?.id, mediaMetadata?.id)
                                    if (block.wide && item !is ArtistItem) {
                                        GlassWideCard(
                                            title = item.title,
                                            subtitle = item.subtitleText(),
                                            thumbnailUrl = item.thumbnail,
                                            isActive = active,
                                            onClick = { onYtClick(item) },
                                            onLongClick = { onYtLongClick(item) }
                                        )
                                    } else {
                                        val isLiked = item is PlaylistItem && item.id == "LM"
                                        GlassSquareCard(
                                            title = if (isLiked) "Nhạc đã thích" else item.title,
                                            subtitle = if (isLiked) "Playlist tự động" else item.subtitleText(),
                                            thumbnailUrl = item.thumbnail,
                                            circle = item is ArtistItem,
                                            isActive = active,
                                            onClick = { onYtClick(item) },
                                            onLongClick = { onYtLongClick(item) },
                                            imageContent = if (isLiked) ({ LikedMusicCover() }) else null
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (isLoading) {
                item(key = "loading") {
                    ShimmerHost(
                        modifier = Modifier.animateItem()
                    ) {
                        TextPlaceholder(
                            height = 36.dp,
                            modifier = Modifier
                                .padding(12.dp)
                                .width(250.dp),
                        )
                        LazyRow {
                            items(4) {
                                GridItemPlaceHolder()
                            }
                        }
                    }
                }
            }
        }

        Indicator(
            isRefreshing = isRefreshing,
            state = pullRefreshState,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(LocalPlayerAwareWindowInsets.current.asPaddingValues()),
        )
    }
}
