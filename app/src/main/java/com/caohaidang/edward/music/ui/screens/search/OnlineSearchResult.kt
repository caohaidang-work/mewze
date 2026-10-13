package com.caohaidang.edward.music.ui.screens.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.net.URLDecoder
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.caohaidang.edward.innertube.YouTube.SearchFilter.Companion.FILTER_ALBUM
import com.caohaidang.edward.innertube.YouTube.SearchFilter.Companion.FILTER_ARTIST
import com.caohaidang.edward.innertube.YouTube.SearchFilter.Companion.FILTER_COMMUNITY_PLAYLIST
import com.caohaidang.edward.innertube.YouTube.SearchFilter.Companion.FILTER_FEATURED_PLAYLIST
import com.caohaidang.edward.innertube.YouTube.SearchFilter.Companion.FILTER_SONG
import com.caohaidang.edward.innertube.YouTube.SearchFilter.Companion.FILTER_VIDEO
import com.caohaidang.edward.innertube.models.AlbumItem
import com.caohaidang.edward.innertube.models.ArtistItem
import com.caohaidang.edward.innertube.models.PlaylistItem
import com.caohaidang.edward.innertube.models.SongItem
import com.caohaidang.edward.innertube.models.YTItem
import com.caohaidang.edward.music.LocalPlayerAwareWindowInsets
import com.caohaidang.edward.music.LocalPlayerConnection
import com.caohaidang.edward.music.R
import com.caohaidang.edward.music.constants.AppBarHeight
import com.caohaidang.edward.music.constants.SearchFilterHeight
import com.caohaidang.edward.music.extensions.togglePlayPause
import com.caohaidang.edward.music.models.toMediaMetadata
import com.caohaidang.edward.music.playback.queues.YouTubeQueue
import com.caohaidang.edward.music.ui.component.ChipsRow
import com.caohaidang.edward.music.ui.component.EmptyPlaceholder
import com.caohaidang.edward.music.ui.component.LocalMenuState
import com.caohaidang.edward.music.ui.component.NavigationTitle
import com.caohaidang.edward.music.ui.component.YouTubeListItem
import com.caohaidang.edward.music.ui.component.shimmer.ListItemPlaceHolder
import com.caohaidang.edward.music.ui.component.shimmer.ShimmerHost
import com.caohaidang.edward.music.ui.menu.YouTubeAlbumMenu
import com.caohaidang.edward.music.ui.menu.YouTubeArtistMenu
import com.caohaidang.edward.music.ui.menu.YouTubePlaylistMenu
import com.caohaidang.edward.music.ui.menu.YouTubeSongMenu
import com.caohaidang.edward.music.viewmodels.OnlineSearchViewModel
import kotlinx.coroutines.launch
import android.util.Log

@Composable
fun OnlineSearchResult(
    navController: NavController,
    viewModel: OnlineSearchViewModel = hiltViewModel(),
) {
    val menuState = LocalMenuState.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()

    val coroutineScope = rememberCoroutineScope()
    val lazyListState = rememberLazyListState()

    val searchFilter by viewModel.filter.collectAsState()
    val searchSummary = viewModel.summaryPage
    val itemsPage by remember(searchFilter) {
        derivedStateOf {
            searchFilter?.value?.let {
                viewModel.viewStateMap[it]
            }
        }
    }

    LaunchedEffect(lazyListState) {
        snapshotFlow {
            lazyListState.layoutInfo.visibleItemsInfo.any { it.key == "loading" }
        }.collect { shouldLoadMore ->
            if (!shouldLoadMore) return@collect
            viewModel.loadMore()
        }
    }

    val ytItemContent: @Composable LazyItemScope.(YTItem) -> Unit = { item: YTItem ->
        YouTubeListItem(
            item = item,
            isActive = when (item) {
                is SongItem -> mediaMetadata?.id == item.id
                is AlbumItem -> mediaMetadata?.album?.id == item.id
                else -> false
            },
            isPlaying = isPlaying,
            trailingContent = {
                IconButton(
                    onClick = {
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
                ) {
                    Icon(
                        painter = painterResource(R.drawable.more_vert),
                        contentDescription = null
                    )
                }
            },
            modifier = Modifier
                .clickable {
                    when (item) {
                        is SongItem -> {
                            if (item.id == mediaMetadata?.id) {
                                playerConnection.player.togglePlayPause()
                            } else {
                                playerConnection.playQueue(YouTubeQueue.radio(item.toMediaMetadata()))
                            }
                        }

                        is AlbumItem -> navController.navigate("album/${item.id}")
                        is ArtistItem -> navController.navigate("artist/${item.id}")
                        is PlaylistItem -> navController.navigate("online_playlist/${item.id}")
                    }
                }
                .animateItem()
        )
    }

    LazyColumn(
        state = lazyListState,
        contentPadding = LocalPlayerAwareWindowInsets.current
            .add(WindowInsets(top = SearchFilterHeight))
            .asPaddingValues()
    ) {
        if (searchFilter == null) {
            searchSummary?.summaries?.forEach { summary ->
                item {
                    NavigationTitle(summary.title)
                }

                items(
                    items = summary.items,
                    key = { "${summary.title}/${it.id}" },
                    itemContent = ytItemContent
                )
            }

            if (searchSummary?.summaries?.isEmpty() == true) {
                item {
                    EmptyPlaceholder(
                        icon = R.drawable.search,
                        text = stringResource(R.string.no_results_found),
                        modifier = Modifier.animateItem()
                    )
                }
            }
        } else {
            items(
                items = itemsPage?.items.orEmpty(),
                key = { it.id },
                itemContent = ytItemContent
            )

            if (itemsPage?.continuation != null) {
                item(key = "loading") {
                    ShimmerHost {
                        repeat(3) {
                            ListItemPlaceHolder()
                        }
                    }
                }
            }

            if (itemsPage?.items?.isEmpty() == true) {
                item {
                    EmptyPlaceholder(
                        icon = R.drawable.search,
                        text = stringResource(R.string.no_results_found),
                        modifier = Modifier.animateItem()
                    )
                }
            }
        }

        if (
            (searchFilter == null && searchSummary == null) ||
            (searchFilter != null && itemsPage == null)
        ) {
            item {
                ShimmerHost {
                    repeat(8) {
                        ListItemPlaceHolder()
                    }
                }
            }
        }
    }

    ChipsRow(
        chips = listOf(
            null to stringResource(R.string.filter_all),
            FILTER_SONG to stringResource(R.string.filter_songs),
            FILTER_VIDEO to stringResource(R.string.filter_videos),
            FILTER_ALBUM to stringResource(R.string.filter_albums),
            FILTER_ARTIST to stringResource(R.string.filter_artists),
            FILTER_COMMUNITY_PLAYLIST to stringResource(R.string.filter_community_playlists),
            FILTER_FEATURED_PLAYLIST to stringResource(R.string.filter_featured_playlists)
        ),
        currentValue = searchFilter,
        onValueUpdate = {
            if (viewModel.filter.value != it) {
                viewModel.filter.value = it
            }
            coroutineScope.launch {
                lazyListState.animateScrollToItem(0)
            }
        },
        modifier = Modifier
            .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top))
            .padding(top = AppBarHeight)
    )

    val displayQuery = remember(viewModel.query) {
        runCatching { URLDecoder.decode(viewModel.query, "UTF-8") }.getOrDefault(viewModel.query)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top))
            .height(AppBarHeight)
            .padding(horizontal = 4.dp)
    ) {
        IconButton(onClick = { navController.navigateUp() }) {
            Icon(
                painter = painterResource(R.drawable.arrow_back),
                contentDescription = null
            )
        }
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier
                .weight(1f)
                .height(44.dp)
                .clickable { navController.navigateUp() }
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.search),
                    contentDescription = null,
                    modifier = Modifier.alpha(0.6f)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = displayQuery,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.width(12.dp))
    }
}
