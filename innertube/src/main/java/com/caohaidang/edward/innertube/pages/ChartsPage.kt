package com.caohaidang.edward.innertube.pages

import com.caohaidang.edward.innertube.models.Artist
import com.caohaidang.edward.innertube.models.SongItem
import com.caohaidang.edward.innertube.models.WatchEndpoint
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Top tracks of a country (or worldwide when [country] is "ZZ"), in chart order.
 */
data class ChartsPage(
    val country: String,
    val songs: List<SongItem>,
) {
    companion object {
        const val GLOBAL = "ZZ"

        /** Titles (several languages) of the "Top songs" shelf of the YouTube Music charts page. */
        val TOP_SONGS_TITLES = listOf("top songs", "top tracks", "bài hát hàng đầu", "bài hát được nghe nhiều")

        /**
         * Shelves of the charts page that are not the song chart: video charts, artists, trending, ...
         * They are never used, even when nothing else can be read, so the Home screen shows nothing rather
         * than a list of trending videos.
         */
        private val NOT_SONG_TITLES = listOf(
            "video", "artist", "nghệ sĩ", "trending", "thịnh hành", "xu hướng", "đang hot", "mới", "new",
        )

        private val json = Json { ignoreUnknownKeys = true; isLenient = true }

        /**
         * Reads the "Top songs" chart from a `FEmusic_charts` response.
         *
         * The page layout differs between regions and changes over time, so this works on the raw JSON and
         * only looks for items that carry a video id instead of relying on typed models. Items that cannot
         * be read are skipped. Shelves are picked in this order:
         *  1. a shelf titled like "Top songs" (ranked rows, or cards when it has no rows);
         *  2. otherwise the first shelf made of ranked rows whose title is not a video / artist / trending one.
         * Video cards from any other shelf are ignored, so this returns null instead of a video list.
         */
        fun fromJson(country: String, body: String): ChartsPage? {
            val root = runCatching { json.parseToJsonElement(body) }.getOrNull() ?: return null
            val sections = findKey(root, "sectionListRenderer")?.obj()?.get("contents") as? JsonArray ?: return null

            class Candidate(val title: String, val rows: List<SongItem>, val cards: List<SongItem>)

            val candidates = sections.mapNotNull { section ->
                val sectionObj = section.obj() ?: return@mapNotNull null
                val shelf = sectionObj["musicCarouselShelfRenderer"]?.obj()
                    ?: sectionObj["musicImmersiveCarouselShelfRenderer"]?.obj()
                    ?: sectionObj["musicShelfRenderer"]?.obj()
                    ?: return@mapNotNull null
                val contents = (shelf["contents"] as? JsonArray).orEmpty().mapNotNull { it.obj() }
                Candidate(
                    title = shelfTitle(shelf),
                    rows = contents.mapNotNull { c -> c["musicResponsiveListItemRenderer"]?.obj()?.let { rowSong(it) } },
                    cards = contents.mapNotNull { c -> c["musicTwoRowItemRenderer"]?.obj()?.let { cardSong(it) } },
                )
            }

            fun Candidate.matches(words: List<String>) = words.any { title.contains(it, ignoreCase = true) }

            val songs = candidates.firstOrNull { it.matches(TOP_SONGS_TITLES) && maxOf(it.rows.size, it.cards.size) >= 3 }
                ?.let { if (it.rows.size >= 3) it.rows else it.cards }
                ?: candidates.firstOrNull { it.rows.size >= 3 && !it.matches(NOT_SONG_TITLES) }?.rows
                ?: return null

            return ChartsPage(country, songs.distinctBy { it.id })
        }

        private fun shelfTitle(shelf: JsonObject): String {
            val header = shelf["header"]?.obj()
                ?.get("musicCarouselShelfBasicHeaderRenderer")?.obj()
            val runs = (header?.get("title")?.obj() ?: shelf["title"]?.obj())?.get("runs") as? JsonArray
            return runs?.firstOrNull()?.obj()?.text().orEmpty()
        }

        /** Ranked list row (`musicResponsiveListItemRenderer`). */
        private fun rowSong(item: JsonObject): SongItem? {
            val columns = item["flexColumns"] as? JsonArray ?: return null
            fun column(i: Int): List<JsonObject> =
                (columns.getOrNull(i)?.obj()?.get("musicResponsiveListItemFlexColumnRenderer")?.obj()
                    ?.get("text")?.obj()?.get("runs") as? JsonArray).orEmpty().mapNotNull { it.obj() }

            val title = column(0).firstOrNull()?.text() ?: return null
            return buildSong(item, title, column(1))
        }

        /** Video / album style card (`musicTwoRowItemRenderer`). */
        private fun cardSong(item: JsonObject): SongItem? {
            val title = (item["title"]?.obj()?.get("runs") as? JsonArray)
                ?.firstOrNull()?.obj()?.text() ?: return null
            val subtitle = (item["subtitle"]?.obj()?.get("runs") as? JsonArray).orEmpty().mapNotNull { it.obj() }
            return buildSong(item, title, subtitle)
        }

        private fun buildSong(item: JsonObject, title: String, subtitleRuns: List<JsonObject>): SongItem? {
            val videoId = videoId(item) ?: return null
            val thumbnail = thumbnail(item) ?: return null

            val linked = subtitleRuns.mapNotNull { run ->
                val browse = run["navigationEndpoint"]?.obj()?.get("browseEndpoint")?.obj() ?: return@mapNotNull null
                Artist(name = run.text() ?: return@mapNotNull null, id = browse.str("browseId"))
            }
            val artists = linked.ifEmpty {
                subtitleRuns.firstOrNull()?.text()
                    ?.takeIf { it.isNotBlank() && it != "•" && it != " • " }
                    ?.let { listOf(Artist(name = it, id = null)) }
                    .orEmpty()
            }

            return SongItem(
                id = videoId,
                title = title,
                artists = artists,
                thumbnail = thumbnail,
                endpoint = WatchEndpoint(videoId = videoId),
            )
        }

        private fun videoId(item: JsonObject): String? =
            item["playlistItemData"]?.obj()?.str("videoId")
                ?: findKey(item["overlay"], "watchEndpoint")?.obj()?.str("videoId")
                ?: findKey(item["navigationEndpoint"], "watchEndpoint")?.obj()?.str("videoId")
                ?: findKey(item, "watchEndpoint")?.obj()?.str("videoId")

        private fun thumbnail(item: JsonObject): String? {
            val thumbnails = (findKey(item["thumbnail"], "thumbnails")
                ?: findKey(item["thumbnailRenderer"], "thumbnails")
                ?: findKey(item, "thumbnails")) as? JsonArray
            return thumbnails?.lastOrNull()?.obj()?.str("url")
        }

        /** First value stored under [key] anywhere inside [element] (depth first). */
        private fun findKey(element: JsonElement?, key: String): JsonElement? = when (element) {
            is JsonObject -> element[key] ?: element.values.firstNotNullOfOrNull { findKey(it, key) }
            is JsonArray -> element.firstNotNullOfOrNull { findKey(it, key) }
            else -> null
        }

        private fun JsonElement.obj(): JsonObject? = this as? JsonObject
        private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
        private fun JsonObject.text(): String? = str("text")
    }
}
