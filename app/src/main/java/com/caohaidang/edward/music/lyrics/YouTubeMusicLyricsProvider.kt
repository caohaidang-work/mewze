package com.caohaidang.edward.music.lyrics

import android.content.Context
import com.caohaidang.edward.innertube.YouTube
import com.caohaidang.edward.innertube.models.WatchEndpoint

/**
 * Lời của chính YouTube Music (do LyricFind / Musixmatch cung cấp), lấy THEO ID VIDEO của bài đang phát
 * nên chắc chắn đúng bài, đúng bản (không phải đoán theo tên). Chỉ có lời thường (không có mốc thời gian),
 * nên chỉ dùng làm dự phòng sau các nguồn có đồng bộ.
 */
object YouTubeMusicLyricsProvider : LyricsProvider {
    override val name = "YouTube Music"

    override fun isEnabled(context: Context): Boolean = true

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
    ): Result<String> = runCatching {
        val endpoint = YouTube.next(WatchEndpoint(videoId = id)).getOrThrow().lyricsEndpoint
            ?: throw IllegalStateException("No lyrics endpoint")
        val raw = YouTube.lyrics(endpoint).getOrThrow()
            ?: throw IllegalStateException("No lyrics")
        // Bỏ dòng ghi nguồn ("Source: LyricFind") ở cuối.
        val text = raw.lines()
            .filterNot { it.trim().startsWith("Source:", ignoreCase = true) }
            .joinToString("\n")
            .trim()
        // Lời thường không được bắt đầu bằng "[" vì app dùng ký tự đó để nhận biết lời đồng bộ.
        if (text.isBlank() || text.startsWith("[")) throw IllegalStateException("Lyrics unavailable")
        text
    }
}
