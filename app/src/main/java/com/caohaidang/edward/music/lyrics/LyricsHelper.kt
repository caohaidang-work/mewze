package com.caohaidang.edward.music.lyrics

import android.content.Context
import android.util.LruCache
import com.caohaidang.edward.music.db.entities.LyricsEntity.Companion.LYRICS_NOT_FOUND
import com.caohaidang.edward.music.models.MediaMetadata
import com.caohaidang.edward.music.utils.reportException
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class LyricsHelper @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val lyricsProviders = listOf(
        LrcLibLyricsProvider,
        MusixmatchLyricsProvider,
        KuGouLyricsProvider,
        NetEaseLyricsProvider,
        YouTubeLyricsProvider,
        YouTubeSubtitleLyricsProvider,
    )
    private val cache = LruCache<String, List<LyricsResult>>(MAX_CACHE_SIZE)

    suspend fun getLyrics(mediaMetadata: MediaMetadata): String {
        var plainFallback: String? = null
        for (provider in lyricsProviders) {
            if (!provider.isEnabled(context)) continue
            val result = provider.getLyrics(
                mediaMetadata.id,
                mediaMetadata.title,
                mediaMetadata.artists.joinToString { it.name },
                mediaMetadata.duration
            )
            val lyrics = result.getOrNull()
            if (lyrics.isNullOrBlank() || lyrics == LYRICS_NOT_FOUND) {
                result.exceptionOrNull()?.let { reportException(it) }
                continue
            }
            if (lyrics.trimStart().startsWith("[")) return lyrics.trimStart()
            if (plainFallback == null) plainFallback = lyrics
        }
        return plainFallback ?: LYRICS_NOT_FOUND
    }

    suspend fun getAllLyrics(
        mediaId: String,
        songTitle: String,
        songArtists: String,
        duration: Int,
        callback: (LyricsResult) -> Unit,
    ) {
        val cacheKey = "$songArtists-$songTitle".replace(" ", "")
        cache.get(cacheKey)?.let { results ->
            results.forEach {
                callback(it)
            }
            return
        }
        val allResult = mutableListOf<LyricsResult>()
        lyricsProviders.forEach { provider ->
            if (provider.isEnabled(context)) {
                provider.getAllLyrics(mediaId, songTitle, songArtists, duration) { lyrics ->
                    val result = LyricsResult(provider.name, lyrics)
                    allResult += result
                    callback(result)
                }
            }
        }
        cache.put(cacheKey, allResult)
    }

    companion object {
        private const val MAX_CACHE_SIZE = 3
    }
}

data class LyricsResult(
    val providerName: String,
    val lyrics: String,
)
