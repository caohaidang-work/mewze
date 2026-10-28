package com.caohaidang.edward.innertube.pages

import com.caohaidang.edward.innertube.models.Artist
import com.caohaidang.edward.innertube.models.MusicResponsiveListItemRenderer
import com.caohaidang.edward.innertube.models.SectionListRenderer
import com.caohaidang.edward.innertube.models.SongItem
import com.caohaidang.edward.innertube.models.WatchEndpoint

/**
 * Top tracks of a country (or worldwide when [country] is "ZZ"), in chart order.
 */
data class ChartsPage(
    val country: String,
    val songs: List<SongItem>,
) {
    companion object {
        const val GLOBAL = "ZZ"

        /**
         * Reads the first track list found in the charts response. The page layout changes between
         * regions and over time, so this only looks for rows that carry a video id instead of relying
         * on section titles. Rows that cannot be read are skipped.
         */
        fun fromSections(country: String, sections: List<SectionListRenderer.Content>): ChartsPage? {
            data class Candidate(val title: String, val songs: List<SongItem>)

            val candidates = sections.mapNotNull { content ->
                val carousel = content.musicCarouselShelfRenderer
                val shelf = content.musicShelfRenderer
                when {
                    carousel != null -> Candidate(
                        title = carousel.header?.musicCarouselShelfBasicHeaderRenderer?.title?.runs
                            ?.firstOrNull()?.text.orEmpty(),
                        songs = carousel.contents.mapNotNull { it.musicResponsiveListItemRenderer }
                            .mapNotNull { chartSong(it) }
                    )

                    shelf != null -> Candidate(
                        title = shelf.title?.runs?.firstOrNull()?.text.orEmpty(),
                        songs = shelf.contents.orEmpty().mapNotNull { it.musicResponsiveListItemRenderer }
                            .mapNotNull { chartSong(it) }
                    )

                    else -> null
                }
            }.filter { it.songs.size >= 3 }

            val best = candidates.firstOrNull { c ->
                c.title.contains("song", ignoreCase = true) || c.title.contains("bài hát", ignoreCase = true)
            } ?: candidates.firstOrNull() ?: return null

            return ChartsPage(country, best.songs.distinctBy { it.id })
        }

        private fun chartSong(renderer: MusicResponsiveListItemRenderer): SongItem? {
            val videoId = renderer.playlistItemData?.videoId
                ?: renderer.overlay?.musicItemThumbnailOverlayRenderer?.content?.musicPlayButtonRenderer
                    ?.playNavigationEndpoint?.watchEndpoint?.videoId
                ?: return null
            val title = renderer.flexColumns.firstOrNull()
                ?.musicResponsiveListItemFlexColumnRenderer?.text?.runs?.firstOrNull()?.text
                ?: return null
            val thumbnail = renderer.thumbnail?.musicThumbnailRenderer?.getThumbnailUrl() ?: return null

            val artistRuns = renderer.flexColumns.getOrNull(1)
                ?.musicResponsiveListItemFlexColumnRenderer?.text?.runs.orEmpty()
            val linked = artistRuns
                .filter { it.navigationEndpoint?.browseEndpoint != null }
                .map { Artist(name = it.text, id = it.navigationEndpoint?.browseEndpoint?.browseId) }
            val artists = linked.ifEmpty {
                artistRuns.firstOrNull()?.let { listOf(Artist(name = it.text, id = null)) }.orEmpty()
            }

            return SongItem(
                id = videoId,
                title = title,
                artists = artists,
                thumbnail = thumbnail,
                explicit = renderer.badges?.any {
                    it.musicInlineBadgeRenderer?.icon?.iconType == "MUSIC_EXPLICIT_BADGE"
                } == true,
                endpoint = renderer.overlay?.musicItemThumbnailOverlayRenderer?.content
                    ?.musicPlayButtonRenderer?.playNavigationEndpoint?.watchEndpoint
                    ?: WatchEndpoint(videoId = videoId),
            )
        }
    }
}
