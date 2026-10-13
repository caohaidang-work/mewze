package com.caohaidang.edward.innertube.pages

import com.caohaidang.edward.innertube.models.Album
import com.caohaidang.edward.innertube.models.AlbumItem
import com.caohaidang.edward.innertube.models.Artist
import com.caohaidang.edward.innertube.models.ArtistItem
import com.caohaidang.edward.innertube.models.MusicCardShelfRenderer
import com.caohaidang.edward.innertube.models.MusicResponsiveListItemRenderer
import com.caohaidang.edward.innertube.models.PlaylistItem
import com.caohaidang.edward.innertube.models.SongItem
import com.caohaidang.edward.innertube.models.YTItem
import com.caohaidang.edward.innertube.models.clean
import com.caohaidang.edward.innertube.models.filterExplicit
import com.caohaidang.edward.innertube.models.oddElements
import com.caohaidang.edward.innertube.models.splitBySeparator
import com.caohaidang.edward.innertube.utils.parseTime

data class SearchSummary(
    val title: String,
    val items: List<YTItem>,
)

data class SearchSummaryPage(
    val summaries: List<SearchSummary>,
) {
    fun filterExplicit(enabled: Boolean): SearchSummaryPage {
        if (!enabled) return this

        return SearchSummaryPage(
            summaries = summaries.mapNotNull { summary ->
                val filteredItems = summary.items.filterExplicit()
                if (filteredItems.isEmpty()) null
                else SearchSummary(title = summary.title, items = filteredItems)
            }
        )
    }

    companion object {
        private fun isExplicit(badges: List<com.caohaidang.edward.innertube.models.Badges>?): Boolean =
            badges?.any { it.musicInlineBadgeRenderer?.icon?.iconType == "MUSIC_EXPLICIT_BADGE" } ?: false

        fun fromMusicCardShelfRenderer(renderer: MusicCardShelfRenderer): YTItem? {
            val title = renderer.title?.runs?.firstOrNull()?.text ?: return null

            val subtitle = renderer.subtitle?.runs?.splitBySeparator() ?: emptyList()

            val thumbnail = renderer.thumbnail?.musicThumbnailRenderer?.getThumbnailUrl()
                ?: return null

            val buttons = renderer.buttons.orEmpty()

            return when {
                renderer.onTap?.watchEndpoint != null -> {
                    val videoId = renderer.onTap.watchEndpoint?.videoId ?: return null

                    val artists = subtitle.getOrNull(1)?.oddElements()?.map {
                        Artist(
                            name = it.text,
                            id = it.navigationEndpoint?.browseEndpoint?.browseId
                        )
                    } ?: return null

                    val album = subtitle.getOrNull(2)?.firstOrNull()?.let { run ->
                        val browseId = run.navigationEndpoint?.browseEndpoint?.browseId
                            ?: return@let null
                        Album(name = run.text, id = browseId)
                    }

                    SongItem(
                        id = videoId,
                        title = title,
                        artists = artists,
                        album = album,
                        duration = subtitle.lastOrNull()?.firstOrNull()?.text?.parseTime(),
                        thumbnail = thumbnail,
                        explicit = isExplicit(renderer.subtitleBadges)
                    )
                }

                renderer.onTap?.browseEndpoint?.isArtistEndpoint == true -> {
                    val browseId = renderer.onTap.browseEndpoint?.browseId ?: return null

                    val shuffleEndpoint = buttons
                        .find { it.buttonRenderer?.icon?.iconType == "MUSIC_SHUFFLE" }
                        ?.buttonRenderer?.command?.watchPlaylistEndpoint

                    val radioEndpoint = buttons
                        .find { it.buttonRenderer?.icon?.iconType == "MIX" }
                        ?.buttonRenderer?.command?.watchPlaylistEndpoint

                    ArtistItem(
                        id = browseId,
                        title = title,
                        thumbnail = thumbnail,
                        shuffleEndpoint = shuffleEndpoint,
                        radioEndpoint = radioEndpoint
                    )
                }

                renderer.onTap?.browseEndpoint?.isAlbumEndpoint == true -> {
                    val browseId = renderer.onTap.browseEndpoint?.browseId ?: return null

                    val playlistId = buttons.firstOrNull()
                        ?.buttonRenderer?.command?.anyWatchEndpoint?.playlistId
                        ?: return null

                    val artists = subtitle.getOrNull(1)?.oddElements()?.map {
                        Artist(
                            name = it.text,
                            id = it.navigationEndpoint?.browseEndpoint?.browseId
                        )
                    } ?: return null

                    AlbumItem(
                        browseId = browseId,
                        playlistId = playlistId,
                        title = title,
                        artists = artists,
                        year = null,
                        thumbnail = thumbnail,
                        explicit = isExplicit(renderer.subtitleBadges)
                    )
                }

                renderer.onTap?.browseEndpoint?.isPlaylistEndpoint == true -> {
                    val browseId = renderer.onTap.browseEndpoint?.browseId
                        ?.removePrefix("VL")
                        ?: return null

                    val playlistTitle = renderer.header
                        ?.musicCardShelfHeaderBasicRenderer
                        ?.title?.runs
                        ?.joinToString(separator = "") { it.text }
                        ?.takeIf { it.isNotBlank() }
                        ?: title

                    val authorName = renderer.subtitle?.runs
                        ?.joinToString(separator = "") { it.text }
                        ?.takeIf { it.isNotBlank() }
                        ?: return null

                    val playEndpoint = buttons
                        .find { it.buttonRenderer?.icon?.iconType == "PLAY_ARROW" }
                        ?.buttonRenderer?.command?.watchPlaylistEndpoint

                    val shuffleEndpoint = buttons
                        .find { it.buttonRenderer?.icon?.iconType == "MUSIC_SHUFFLE" }
                        ?.buttonRenderer?.command?.watchPlaylistEndpoint
                        ?: playEndpoint
                        ?: return null

                    PlaylistItem(
                        id = browseId,
                        title = playlistTitle,
                        author = Artist(id = null, name = authorName),
                        songCountText = null,
                        thumbnail = thumbnail,
                        playEndpoint = playEndpoint,
                        shuffleEndpoint = shuffleEndpoint,
                        radioEndpoint = null
                    )
                }

                else -> null
            }
        }

        fun fromMusicResponsiveListItemRenderer(
            renderer: MusicResponsiveListItemRenderer
        ): YTItem? {
            val secondaryLine = renderer.flexColumns
                .getOrNull(1)
                ?.musicResponsiveListItemFlexColumnRenderer
                ?.text?.runs?.splitBySeparator()
                ?: return null

            val thirdLine = renderer.flexColumns
                .getOrNull(2)
                ?.musicResponsiveListItemFlexColumnRenderer
                ?.text?.runs?.splitBySeparator()
                ?: emptyList()

            val listRun = (secondaryLine + thirdLine).clean()

            val title = renderer.flexColumns
                .firstOrNull()
                ?.musicResponsiveListItemFlexColumnRenderer
                ?.text?.runs?.firstOrNull()?.text
                ?: return null

            val thumbnail = renderer.thumbnail
                ?.musicThumbnailRenderer
                ?.getThumbnailUrl()
                ?: return null

            return when {
                renderer.isSong -> {
                    val videoId = renderer.playlistItemData?.videoId ?: return null

                    val artists = listRun.getOrNull(0)?.oddElements()?.map {
                        Artist(
                            name = it.text,
                            id = it.navigationEndpoint?.browseEndpoint?.browseId
                        )
                    } ?: return null

                    val album = listRun.getOrNull(1)?.firstOrNull()?.let { run ->
                        val browseId = run.navigationEndpoint?.browseEndpoint?.browseId
                            ?: return@let null
                        Album(name = run.text, id = browseId)
                    }

                    SongItem(
                        id = videoId,
                        title = title,
                        artists = artists,
                        album = album,
                        duration = secondaryLine.lastOrNull()?.firstOrNull()?.text?.parseTime(),
                        thumbnail = thumbnail,
                        explicit = isExplicit(renderer.badges)
                    )
                }

                renderer.isArtist -> {
                    val browseId = renderer.navigationEndpoint?.browseEndpoint?.browseId
                        ?: return null

                    val shuffleEndpoint = renderer.menu?.menuRenderer?.items
                        ?.find { it.menuNavigationItemRenderer?.icon?.iconType == "MUSIC_SHUFFLE" }
                        ?.menuNavigationItemRenderer?.navigationEndpoint?.watchPlaylistEndpoint

                    val radioEndpoint = renderer.menu?.menuRenderer?.items
                        ?.find { it.menuNavigationItemRenderer?.icon?.iconType == "MIX" }
                        ?.menuNavigationItemRenderer?.navigationEndpoint?.watchPlaylistEndpoint

                    ArtistItem(
                        id = browseId,
                        title = title,
                        thumbnail = thumbnail,
                        shuffleEndpoint = shuffleEndpoint,
                        radioEndpoint = radioEndpoint
                    )
                }

                renderer.isAlbum -> {
                    val browseId = renderer.navigationEndpoint?.browseEndpoint?.browseId
                        ?: return null

                    val playlistId = renderer.overlay
                        ?.musicItemThumbnailOverlayRenderer
                        ?.content
                        ?.musicPlayButtonRenderer
                        ?.playNavigationEndpoint
                        ?.anyWatchEndpoint
                        ?.playlistId
                        ?: return null

                    val artists = secondaryLine.getOrNull(1)?.oddElements()?.map {
                        Artist(
                            name = it.text,
                            id = it.navigationEndpoint?.browseEndpoint?.browseId
                        )
                    } ?: return null

                    AlbumItem(
                        browseId = browseId,
                        playlistId = playlistId,
                        title = title,
                        artists = artists,
                        year = secondaryLine.getOrNull(2)?.firstOrNull()?.text?.toIntOrNull(),
                        thumbnail = thumbnail,
                        explicit = isExplicit(renderer.badges)
                    )
                }

                renderer.isPlaylist -> {
                    val browseId = renderer.navigationEndpoint?.browseEndpoint?.browseId
                        ?.removePrefix("VL")
                        ?: return null

                    val author = secondaryLine.getOrNull(1)?.firstOrNull()?.let { run ->
                        Artist(
                            name = run.text,
                            id = run.navigationEndpoint?.browseEndpoint?.browseId
                        )
                    } ?: return null

                    val playEndpoint = renderer.overlay
                        ?.musicItemThumbnailOverlayRenderer
                        ?.content
                        ?.musicPlayButtonRenderer
                        ?.playNavigationEndpoint
                        ?.watchPlaylistEndpoint

                    val shuffleEndpoint = renderer.menu?.menuRenderer?.items
                        ?.find { it.menuNavigationItemRenderer?.icon?.iconType == "MUSIC_SHUFFLE" }
                        ?.menuNavigationItemRenderer?.navigationEndpoint?.watchPlaylistEndpoint
                        ?: playEndpoint
                        ?: return null

                    val radioEndpoint = renderer.menu?.menuRenderer?.items
                        ?.find { it.menuNavigationItemRenderer?.icon?.iconType == "MIX" }
                        ?.menuNavigationItemRenderer?.navigationEndpoint?.watchPlaylistEndpoint

                    PlaylistItem(
                        id = browseId,
                        title = title,
                        author = author,
                        songCountText = renderer.flexColumns
                            .getOrNull(1)
                            ?.musicResponsiveListItemFlexColumnRenderer
                            ?.text?.runs?.lastOrNull()?.text,
                        thumbnail = thumbnail,
                        playEndpoint = playEndpoint,
                        shuffleEndpoint = shuffleEndpoint,
                        radioEndpoint = radioEndpoint
                    )
                }

                else -> null
            }
        }
    }
}
