package com.caohaidang.edward.music.lyrics

import android.content.Context
import com.caohaidang.edward.lrclib.LrcLib
import com.caohaidang.edward.lrclib.models.Track

// Source: https://github.com/Malopieds/InnerTune
object LrcLibLyricsProvider : LyricsProvider {
    override val name = "LrcLib"

    private const val DURATION_TOLERANCE = 4
    private const val RELAXED_DURATION_TOLERANCE = 8

    override fun isEnabled(context: Context): Boolean =
        true // luôn bật: LrcLib là nguồn lời duy nhất

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
    ): Result<String> = runCatching {
        val cleanTitle = LyricsMatcher.cleanTitle(title)
        val fullArtist = LyricsMatcher.cleanArtist(artist)
        val mainArtist = LyricsMatcher.primaryArtist(artist)
        // Tiêu đề kiểu "Nghệ sĩ - Tên bài" thì thử thêm phần sau dấu gạch.
        val titles = listOfNotNull(cleanTitle, LyricsMatcher.titleAfterDash(title)).distinct()

        // Giữ nguyên chữ gốc (kể cả "(feat. B)", "(Live)") để nhận diện collab/phiên bản; thêm phần sau dấu gạch.
        val rawAfterDash = title.split(Regex("""\s+[-–—]\s+"""), limit = 2).getOrNull(1)?.takeIf { it.isNotBlank() }
        val wantedTitles = listOfNotNull(title, rawAfterDash).distinct()

        val pool = LinkedHashMap<Int, Track>()

        fun pick(relaxed: Boolean): String? = pool.values.mapNotNull { track ->
            if (track.syncedLyrics.isNullOrBlank()) return@mapNotNull null
            // Chấp nhận mọi cách đặt tên bài: "Bài", "Bài (feat. B)", "Nghệ sĩ - Bài".
            val score = wantedTitles.mapNotNull { t ->
                LyricsMatcher.evaluate(t, artist, track.trackName, track.artistName, duration, track.duration, relaxed)
            }.maxOrNull() ?: return@mapNotNull null
            track to score
        }.maxByOrNull { it.second }?.first?.syncedLyrics

        fun add(tracks: List<Track>) = tracks.forEach { pool.putIfAbsent(it.id, it) }

        for (t in titles) {
            // 1. /api/get chính xác theo (tên, nghệ sĩ, thời lượng)
            LrcLib.getExact(t, fullArtist, duration)?.let { add(listOf(it)) }
            if (mainArtist != fullArtist) LrcLib.getExact(t, mainArtist, duration)?.let { add(listOf(it)) }
            pick(relaxed = false)?.let { return@runCatching it }

            // 2. Tìm theo track_name + artist_name (đủ nghệ sĩ, rồi nghệ sĩ chính)
            add(LrcLib.searchTracks(t, fullArtist))
            if (mainArtist != fullArtist) add(LrcLib.searchTracks(t, mainArtist))
            pick(relaxed = false)?.let { return@runCatching it }

            // 3. Tìm tự do q="tên nghệ sĩ" (cách của SimpMusic)
            add(LrcLib.searchTracks("$t $fullArtist"))
            if (mainArtist != fullArtist) add(LrcLib.searchTracks("$t $mainArtist"))
            pick(relaxed = false)?.let { return@runCatching it }

            // 4. Chỉ theo tên bài
            add(LrcLib.searchTracks(t, ""))
            pick(relaxed = false)?.let { return@runCatching it }
        }

        // 5. Nới điều kiện: tên bài giống vừa phải, lệch thời lượng tối đa 8s
        pick(relaxed = true)?.let { return@runCatching it }
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
