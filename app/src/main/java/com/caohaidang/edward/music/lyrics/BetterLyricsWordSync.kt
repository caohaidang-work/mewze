package com.caohaidang.edward.music.lyrics

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Bổ sung mốc TỪNG TỪ từ BetterLyrics (API cộng đồng, trả TTML) cho lời đã được kiểm chứng từ nguồn khác.
 *
 * BetterLyrics không trả tên bài / nghệ sĩ nên KHÔNG THỂ tin nó là đúng bài. Vì vậy nó không bao giờ
 * thay lời: chỉ khi từng dòng của nó khớp mốc thời gian VÀ giống chữ với lời đã kiểm chứng
 * (cùng ngưỡng như Musixmatch, xem MusixmatchWordSync.applyWordTiming) thì mới lấy mốc từng từ.
 * Sai bài -> không khớp -> trả nguyên lời gốc. Mọi lỗi (mạng, JSON, quá 8 giây) -> trả nguyên lời gốc.
 */
object BetterLyricsWordSync {
    private const val ENDPOINT = "https://lyrics-api.boidu.dev/getLyrics"

    private val client = OkHttpClient.Builder()
        .callTimeout(6, TimeUnit.SECONDS)
        .build()

    private val P_REGEX = Regex("""<p\s[^>]*begin="([^"]+)"[^>]*end="([^"]+)"[^>]*>([\s\S]*?)</p>""")
    private val SPAN_REGEX = Regex("""<span\s[^>]*begin="([^"]+)"[^>]*end="([^"]+)"[^>]*>([\s\S]*?)</span>""")

    suspend fun enhance(lrc: String, title: String, artist: String, durationSec: Int): String =
        withContext(Dispatchers.IO) {
            runCatching {
                withTimeoutOrNull(8_000) {
                    val ttml = fetchTtml(title, artist, durationSec) ?: return@withTimeoutOrNull null
                    val lines = parseTtml(ttml)
                    if (lines.isEmpty()) null else MusixmatchWordSync.applyWordTiming(lrc, lines)
                }
            }.getOrNull() ?: lrc
        }

    private fun fetchTtml(title: String, artist: String, durationSec: Int): String? {
        val url = ENDPOINT.toHttpUrl().newBuilder()
            .addQueryParameter("s", LyricsMatcher.cleanTitle(title))
            .addQueryParameter("a", LyricsMatcher.cleanArtist(artist))
            .apply { if (durationSec > 0) addQueryParameter("d", durationSec.toString()) }
            .build()
        val req = Request.Builder().url(url).header("User-Agent", "Mozilla/5.0").build()
        return client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) null
            else JSONObject(resp.body?.string().orEmpty()).optString("ttml").takeIf { it.isNotBlank() }
        }
    }

    private fun parseTtml(ttml: String): List<MusixmatchWordSync.MxLine> {
        val lines = ArrayList<MusixmatchWordSync.MxLine>()
        for (p in P_REGEX.findAll(ttml)) {
            val start = parseTime(p.groupValues[1])
            val end = parseTime(p.groupValues[2])
            val segments = SPAN_REGEX.findAll(p.groupValues[3]).mapNotNull { span ->
                val text = decode(span.groupValues[3].replace(Regex("<[^>]*>"), "")).trim()
                if (text.isEmpty()) null else MusixmatchWordSync.MxSegment(text, parseTime(span.groupValues[1]))
            }.toList()
            // Dòng không có mốc từng từ thì vô ích cho việc bổ sung.
            if (segments.isNotEmpty()) lines += MusixmatchWordSync.MxLine(start, end, segments)
        }
        return lines
    }

    private fun decode(s: String) = s
        .replace("&amp;", "&").replace("&apos;", "'").replace("&#39;", "'")
        .replace("&quot;", "\"").replace("&lt;", "<").replace("&gt;", ">")

    /** "H:MM:SS.mmm", "M:SS.mmm" hoặc "SS.mmm" -> mili giây. */
    private fun parseTime(time: String): Long {
        val parts = time.trim().removeSuffix("s").split(":")
        var total = 0.0
        for (part in parts) total = total * 60 + (part.toDoubleOrNull() ?: 0.0)
        return (total * 1000).toLong()
    }
}
