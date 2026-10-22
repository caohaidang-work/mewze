package com.caohaidang.edward.music.lyrics

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Kho lời cộng đồng của SimpMusic, tra THEO ID VIDEO YouTube (không đoán theo tên) nên đúng bài, đúng bản
 * (bản live, remix, bản gốc... mỗi bản có ID riêng).
 *
 * Kiểm chứng thêm trước khi nhận:
 *  - tên bài, tập nghệ sĩ (solo khác collab), phiên bản và thời lượng của bản ghi phải khớp bài đang phát (LyricsMatcher.evaluate);
 *  - bản ghi có vote âm bị bỏ; nhiều bản ghi thì lấy bản nhiều vote nhất.
 * Ưu tiên: lời từng từ (richSync) -> lời theo dòng (syncedLyrics) -> lời thường.
 */
object SimpMusicLyricsProvider : LyricsProvider {
    override val name = "SimpMusic"

    private const val BASE = "https://api-lyrics.simpmusic.org/v1/"
    private const val DURATION_TOLERANCE = 4

    private val client = OkHttpClient.Builder()
        .callTimeout(8, TimeUnit.SECONDS)
        .build()

    private val VOICE_MARKER = Regex("""(?m)^(\[[^\]]*\])\s*v\d+:""")

    override fun isEnabled(context: Context): Boolean = true

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
    ): Result<String> = runCatching {
        val records = fetch(id)
        val best = (0 until records.length())
            .map { records.getJSONObject(it) }
            .filter { it.optInt("vote", 0) >= 0 }
            .filter { rec ->
                // Bản ghi cộng đồng có thể gắn lời bản solo vào video bản collab: đối chiếu cả tên bài + tập nghệ sĩ.
                val recTitle = rec.optString("songTitle")
                val recArtist = rec.optString("artistName")
                if (recTitle.isBlank() || recArtist.isBlank()) return@filter true
                LyricsMatcher.evaluate(title, artist, recTitle, recArtist, duration, rec.optInt("durationSeconds").takeIf { it > 0 }) != null
            }
            .filter { rec ->
                val delta = LyricsMatcher.durationDelta(duration, rec.optInt("durationSeconds").takeIf { it > 0 })
                delta == null || delta <= DURATION_TOLERANCE
            }
            .sortedByDescending { it.optInt("vote", 0) }

        for (rec in best) {
            val rich = rec.optString("richSyncLyrics").cleanRich()
            if (rich.startsWith("[") && LyricsUtils.parseLyrics(rich).size >= 3) return@runCatching rich
            val synced = rec.optString("syncedLyrics").trim()
            if (synced.startsWith("[") && LyricsUtils.parseLyrics(synced).size >= 3) return@runCatching synced
        }
        // Không có bản đồng bộ: dùng lời thường (không được bắt đầu bằng "[" vì app dùng ký tự đó nhận biết lời đồng bộ).
        best.firstNotNullOfOrNull { rec ->
            rec.optString("plainLyric").trim().takeIf { it.isNotBlank() && !it.startsWith("[") }
        } ?: throw IllegalStateException("Lyrics unavailable")
    }

    private fun String.cleanRich(): String =
        trim().removeSurrounding("\"")
            .replace("\\n", "\n").replace("\\r", "")
            .replace(VOICE_MARKER, "$1")
            .trim()

    private fun fetch(videoId: String): JSONArray {
        val req = Request.Builder().url(BASE + videoId)
            .header("Accept", "application/json")
            .header("User-Agent", "SimpMusicLyrics/1.0")
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code}")
            val root = JSONObject(resp.body?.string().orEmpty())
            if (!root.optBoolean("success", false)) throw IllegalStateException("No lyrics")
            return root.optJSONArray("data") ?: throw IllegalStateException("No lyrics")
        }
    }
}
