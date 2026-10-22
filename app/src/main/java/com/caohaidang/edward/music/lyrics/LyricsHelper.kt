package com.caohaidang.edward.music.lyrics

import android.content.Context
import android.util.LruCache
import com.caohaidang.edward.music.db.entities.LyricsEntity.Companion.LYRICS_NOT_FOUND
import com.caohaidang.edward.music.constants.EnableMusixmatchKey
import com.caohaidang.edward.music.models.MediaMetadata
import com.caohaidang.edward.music.utils.dataStore
import com.caohaidang.edward.music.utils.get
import com.caohaidang.edward.music.utils.reportException
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class LyricsHelper @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    // Thứ tự ưu tiên: LrcLib -> SimpMusic (tra theo ID video) -> KuGou (đều có lời đồng bộ, đã kiểm chứng đúng bài);
    // sau đó Musixmatch (xem getLyrics); cuối cùng là lời thường của YouTube Music theo đúng ID video.
    private val lyricsProviders = listOf<LyricsProvider>(
        LrcLibLyricsProvider,
        SimpMusicLyricsProvider,
        KuGouLyricsProvider,
        YouTubeMusicLyricsProvider,
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
            if (lyrics.trimStart().startsWith("[")) {
                val lrc = lyrics.trimStart()
                // Chốt chặn cuối: mốc thời gian của lời phải hợp lý với độ dài bài (lời bản solo ngắn hơn bản collab,
                // hoặc lời của bài khác dài hơn bài này, sẽ lộ ra ở đây). Không hợp lý -> thử nguồn kế tiếp.
                if (!timingPlausible(lrc, mediaMetadata.duration)) continue
                // Lời + mốc dòng luôn lấy từ LrcLib; Musixmatch chỉ bổ sung mốc từng từ
                // và chỉ khi đã được kiểm chứng là CÙNG bài, CÙNG lời (xem MusixmatchWordSync).
                val artist = mediaMetadata.artists.joinToString { it.name }
                var enhanced = lrc
                if (context.dataStore.get(EnableMusixmatchKey, true)) {
                    enhanced = MusixmatchWordSync.enhance(lrc, mediaMetadata.title, artist, mediaMetadata.duration)
                }
                // Musixmatch không bổ sung được mốc từng từ -> thử BetterLyrics (cũng chỉ nhận khi khớp từng dòng với lời gốc).
                if (enhanced == lrc) {
                    enhanced = BetterLyricsWordSync.enhance(lrc, mediaMetadata.title, artist, mediaMetadata.duration)
                }
                return enhanced
            }
            if (plainFallback == null) plainFallback = lyrics
        }

        // LrcLib không có lời đồng bộ -> mới tới Musixmatch (nếu bật). Lời đồng bộ của Musixmatch
        // được ưu tiên hơn lời thường của LrcLib; không có thì giữ lời thường.
        if (context.dataStore.get(EnableMusixmatchKey, true)) {
            MusixmatchWordSync.fetchLyrics(
                title = mediaMetadata.title,
                artist = mediaMetadata.artists.joinToString { it.name },
                durationSec = mediaMetadata.duration,
            )?.let { return it }
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

    /** Dòng cuối không được vượt quá độ dài bài (+3s), và lời phải phủ ít nhất nửa bài. Không biết độ dài thì bỏ qua. */
    private fun timingPlausible(lrc: String, durationSec: Int): Boolean {
        if (durationSec <= 0) return true
        val last = LyricsUtils.parseLyrics(lrc).lastOrNull()?.time ?: return false
        val songMs = durationSec * 1000L
        return last <= songMs + 3_000 && last >= songMs / 2
    }

    companion object {
        private const val MAX_CACHE_SIZE = 3
    }
}

data class LyricsResult(
    val providerName: String,
    val lyrics: String,
)