package com.caohaidang.edward.music.lyrics

import android.content.Context
import com.caohaidang.edward.lrclib.LrcLib
import com.caohaidang.edward.lrclib.models.Track
import com.caohaidang.edward.music.constants.EnableLrcLibKey
import com.caohaidang.edward.music.utils.dataStore
import com.caohaidang.edward.music.utils.get

// Source: https://github.com/Malopieds/InnerTune
object LrcLibLyricsProvider : LyricsProvider {
    override val name = "LrcLib"

    private const val DURATION_TOLERANCE = 4

    override fun isEnabled(context: Context): Boolean =
        context.dataStore[EnableLrcLibKey] ?: true

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
    ): Result<String> = runCatching {
        val cleanTitle = LyricsMatcher.cleanTitle(title)
        val mainArtist = LyricsMatcher.primaryArtist(artist)

        fun pick(tracks: List<Track>): String? {
            val matched = tracks.mapNotNull { track ->
                val delta = LyricsMatcher.durationDelta(duration, track.duration)
                if (delta != null && delta > DURATION_TOLERANCE) return@mapNotNull null
                val titleSim = LyricsMatcher.titleSimilarity(title, track.trackName)
                val artistSim = LyricsMatcher.artistSimilarity(artist, track.artistName)
                if (!LyricsMatcher.isMatch(titleSim, artistSim, delta)) return@mapNotNull null
                track to LyricsMatcher.score(titleSim, artistSim) - (delta ?: 0) * 0.05
            }.sortedByDescending { it.second }.map { it.first }
            return matched.firstNotNullOfOrNull { it.syncedLyrics?.takeIf(String::isNotBlank) }
        }

        LrcLib.getExact(cleanTitle, mainArtist, duration)?.let { pick(listOf(it)) }?.let { return@runCatching it }

        pick(LrcLib.searchTracks(cleanTitle, mainArtist))?.let { return@runCatching it }

        pick(LrcLib.searchTracks("$cleanTitle $mainArtist"))?.let { return@runCatching it }
        pick(LrcLib.searchTracks(cleanTitle))?.let { return@runCatching it }
        throw IllegalStateException("Lyrics unavailable")
    }

    override suspend fun getAllLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        callback: (String) -> Unit,
    ) {
        LrcLib.getAllLyrics(title, artist, duration, null, callback)
    }
}
