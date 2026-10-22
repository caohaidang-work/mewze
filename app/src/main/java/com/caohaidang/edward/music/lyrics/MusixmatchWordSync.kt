package com.caohaidang.edward.music.lyrics

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * Bổ sung mốc thời gian TỪNG TỪ (richsync của Musixmatch) cho lời đã lấy từ LrcLib.
 *
 * Nguyên tắc an toàn để không bao giờ sai bài / sai lời:
 *  1. Lời hiển thị và mốc dòng luôn là của LrcLib — Musixmatch không bao giờ thay lời.
 *  2. Bài Musixmatch trả về phải khớp tên + nghệ sĩ + thời lượng (LyricsMatcher).
 *  3. Từng dòng LrcLib phải có dòng Musixmatch gần mốc thời gian VÀ giống chữ (>= 0.8);
 *     tổng số dòng khớp phải >= 70%. Không đạt -> trả nguyên lời LrcLib.
 *  4. Mọi lỗi (mạng, token, JSON, quá 8 giây) -> trả nguyên lời LrcLib.
 * Dòng nào không khớp vẫn chạy karaoke kiểu ước lượng như bình thường.
 */
object MusixmatchWordSync {
    private const val BASE = "https://apic-desktop.musixmatch.com/ws/1.1/"
    private const val APP_ID = "web-desktop-app-v1.0"
    private const val MAX_LINE_OFFSET_MS = 2000L
    private const val MIN_LINE_SIMILARITY = 0.8
    private const val MIN_MATCHED_RATIO = 0.7

    private val client = OkHttpClient.Builder()
        .callTimeout(6, TimeUnit.SECONDS)
        .build()

    @Volatile private var token: String? = null

    internal class MxSegment(val text: String, val time: Long)
    internal class MxLine(val start: Long, val end: Long, val segments: List<MxSegment>) {
        val text get() = segments.joinToString("") { it.text }
    }

    suspend fun enhance(lrc: String, title: String, artist: String, durationSec: Int): String =
        withContext(Dispatchers.IO) {
            runCatching {
                withTimeoutOrNull(8_000) { doEnhance(lrc, title, artist, durationSec) }
            }.getOrNull() ?: lrc
        }

    private fun doEnhance(lrc: String, title: String, artist: String, durationSec: Int): String {
        val entries = LyricsUtils.parseLyrics(lrc)
        if (entries.size < 3 || entries.any { it.words != null }) return lrc

        val mxLines = fetchRichsync(title, artist, durationSec) ?: return lrc
        return applyWordTiming(lrc, mxLines)
    }

    /**
     * Căn mốc từng từ từ [mxLines] (nguồn bên ngoài, đã kiểm chứng riêng) lên lời [lrc].
     * Chỉ áp dụng khi >= 70% dòng khớp cả về thời gian lẫn chữ; không đạt -> trả nguyên [lrc].
     * Dùng chung cho Musixmatch và BetterLyrics.
     */
    internal fun applyWordTiming(lrc: String, mxLines: List<MxLine>): String {
        val entries = LyricsUtils.parseLyrics(lrc)
        if (entries.size < 3 || entries.any { it.words != null } || mxLines.isEmpty()) return lrc

        var matched = 0
        val out = StringBuilder()
        for (entry in entries) {
            val words = alignWords(entry, mxLines)
            if (words != null) matched++
            out.append(formatTime('[', entry.time, ']'))
            if (words == null) out.append(entry.text)
            else words.forEach { out.append(formatTime('<', it.time, '>')).append(it.text) }
            out.append('\n')
        }
        val nonBlank = entries.count { it.text.isNotBlank() }
        if (nonBlank == 0 || matched < nonBlank * MIN_MATCHED_RATIO) return lrc
        return out.toString().trimEnd()
    }

    // ── Căn mốc từng từ lên lời LrcLib ───────────────────────────────────────────

    private fun alignWords(entry: LyricsEntry, mxLines: List<MxLine>): List<LyricsWord>? {
        if (entry.text.isBlank()) return null
        val mx = mxLines.minByOrNull { abs(it.start - entry.time) } ?: return null
        if (abs(mx.start - entry.time) > MAX_LINE_OFFSET_MS) return null
        if (LyricsMatcher.textSimilarity(entry.text, mx.text) < MIN_LINE_SIMILARITY) return null

        // Trục thời gian của Musixmatch theo "trọng số chữ" (số ký tự chữ/số)
        val segWeights = mx.segments.map { LyricsMatcher.normalize(it.text).length }
        val total = segWeights.sum()
        if (total == 0) return null

        val tokens = tokenize(entry.text)
        val tokenWeights = tokens.map { LyricsMatcher.normalize(it).length }
        val lineWeight = tokenWeights.sum()
        if (lineWeight == 0) return null

        var cum = 0
        var last = Long.MIN_VALUE
        return tokens.mapIndexed { i, tok ->
            val target = cum.toFloat() / lineWeight * total
            cum += tokenWeights[i]
            val t = maxOf(timeAt(mx, segWeights, target), last)
            last = t
            LyricsWord(t, tok)
        }
    }

    private fun timeAt(line: MxLine, weights: List<Int>, target: Float): Long {
        var acc = 0
        for (i in line.segments.indices) {
            val w = weights[i]
            if (w == 0) continue
            if (target < acc + w) {
                val next = line.segments.drop(i + 1).firstOrNull { it.time > line.segments[i].time }?.time ?: line.end
                val frac = (target - acc) / w
                return line.segments[i].time + ((next - line.segments[i].time) * frac).toLong()
            }
            acc += w
        }
        return line.segments.last().time
    }

    /** Tách theo từ (giữ khoảng trắng đuôi). Ngôn ngữ không có khoảng trắng (CJK) tách từng ký tự. */
    private fun tokenize(text: String): List<String> {
        val byWord = Regex("""\S+\s*""").findAll(text).map { it.value }.toList()
        return if (byWord.size <= 1 && text.trim().length > 1) text.map { it.toString() } else byWord
    }

    private fun formatTime(open: Char, ms: Long, close: Char): String {
        val cs = (ms.coerceAtLeast(0) + 5) / 10
        return "%c%02d:%02d.%02d%c".format(open, cs / 6000, (cs / 100) % 60, cs % 100, close)
    }

    // ── Gọi Musixmatch (API desktop không chính thức) ────────────────────────────

    private fun fetchRichsync(title: String, artist: String, durationSec: Int): List<MxLine>? {
        val calls = fetchMacroCalls(title, artist, durationSec) ?: return null
        return richsyncLines(calls)
    }

    private fun richsyncLines(calls: JSONObject): List<MxLine>? {
        val body = calls.optJSONObject("track.richsync.get")?.optJSONObject("message")
            ?.optJSONObject("body")?.optJSONObject("richsync")?.optString("richsync_body")
        if (body.isNullOrBlank()) return null
        return runCatching { parseRichsync(body) }.getOrNull()
    }

    /** Dòng đồng bộ theo câu (không có mốc từng từ) từ subtitle của Musixmatch. */
    private fun subtitleLines(calls: JSONObject): List<Pair<Long, String>>? {
        // subtitle_list là một mảng: [{ subtitle: { subtitle_body } }]
        val list = calls.optJSONObject("track.subtitles.get")?.optJSONObject("message")
            ?.optJSONObject("body")?.optJSONArray("subtitle_list")
        val raw = list?.optJSONObject(0)?.optJSONObject("subtitle")?.optString("subtitle_body")
        if (raw.isNullOrBlank()) return null
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.getJSONObject(i)
                val text = o.optString("text")
                val ms = (o.getJSONObject("time").getDouble("total") * 1000).toLong()
                if (text.isBlank()) null else ms to text
            }
        }.getOrNull()?.takeIf { it.isNotEmpty() }
    }

    /**
     * Lời dự phòng khi LrcLib không có: trả về LRC (có mốc từng từ nếu Musixmatch có richsync,
     * không thì mốc theo dòng). Chỉ nhận khi Musixmatch trả đúng bài (tên + nghệ sĩ + thời lượng).
     * Mọi lỗi -> null.
     */
    suspend fun fetchLyrics(title: String, artist: String, durationSec: Int): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                withTimeoutOrNull(8_000) {
                    val calls = fetchMacroCalls(title, artist, durationSec) ?: return@withTimeoutOrNull null
                    val rich = richsyncLines(calls)
                    if (!rich.isNullOrEmpty()) {
                        buildString {
                            rich.forEach { line ->
                                append(formatTime('[', line.start, ']'))
                                line.segments.forEach { append(formatTime('<', it.time, '>')).append(it.text) }
                                append('\n')
                            }
                        }.trimEnd()
                    } else {
                        subtitleLines(calls)?.joinToString("\n") { (t, text) -> formatTime('[', t, ']') + text }
                    }
                }
            }.getOrNull()?.takeIf { it.isNotBlank() }
        }

    /** Gọi macro.subtitles.get và chỉ trả về `macro_calls` khi đúng bài. */
    private fun fetchMacroCalls(title: String, artist: String, durationSec: Int): JSONObject? {
        val cleanTitle = LyricsMatcher.cleanTitle(title)
        val cleanArtist = LyricsMatcher.cleanArtist(artist)
        repeat(2) {
            val tk = token ?: fetchToken() ?: return null
            val url = (BASE + "macro.subtitles.get").toHttpUrl().newBuilder()
                .addQueryParameter("format", "json")
                .addQueryParameter("namespace", "lyrics_richsynched")
                .addQueryParameter("subtitle_format", "mxm")
                .addQueryParameter("app_id", APP_ID)
                .addQueryParameter("q_track", cleanTitle)
                .addQueryParameter("q_artist", cleanArtist)
                .apply {
                    if (durationSec > 0) {
                        addQueryParameter("q_duration", durationSec.toString())
                        addQueryParameter("f_subtitle_length", durationSec.toString())
                    }
                }
                .addQueryParameter("usertoken", tk)
                .build()
            val root = getJson(url.toString()) ?: return null
            val header = root.optJSONObject("message")?.optJSONObject("header")
            if (header?.optInt("status_code") == 401) { token = null; return@repeat }

            val calls = root.optJSONObject("message")?.optJSONObject("body")
                ?.optJSONObject("macro_calls") ?: return null

            // Kiểm chứng đúng bài trước khi tin dữ liệu
            val track = calls.optJSONObject("matcher.track.get")?.optJSONObject("message")
                ?.optJSONObject("body")?.optJSONObject("track") ?: return null
            val found = track.optInt("track_length").takeIf { it > 0 }
            // Đúng bài, đúng phiên bản, đúng tập nghệ sĩ (solo khác collab) và thời lượng sát.
            LyricsMatcher.evaluate(
                title, artist, track.optString("track_name"), track.optString("artist_name"), durationSec, found
            ) ?: return null
            return calls
        }
        return null
    }

    private fun parseRichsync(body: String): List<MxLine> {
        val arr = JSONArray(body)
        val lines = ArrayList<MxLine>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val ts = (o.getDouble("ts") * 1000).toLong()
            val te = (o.getDouble("te") * 1000).toLong()
            val l = o.getJSONArray("l")
            val segs = ArrayList<MxSegment>(l.length())
            for (j in 0 until l.length()) {
                val w = l.getJSONObject(j)
                val c = w.optString("c")
                if (c.isNotEmpty()) segs += MxSegment(c, ts + (w.optDouble("o", 0.0) * 1000).toLong())
            }
            if (segs.isNotEmpty()) lines += MxLine(ts, te, segs)
        }
        return lines
    }

    private fun fetchToken(): String? {
        val url = (BASE + "token.get").toHttpUrl().newBuilder()
            .addQueryParameter("format", "json")
            .addQueryParameter("app_id", APP_ID)
            .build()
        val t = getJson(url.toString())?.optJSONObject("message")?.optJSONObject("body")
            ?.optString("user_token")
        // Musixmatch trả token giả khi bị giới hạn tốc độ
        if (t.isNullOrBlank() || t.startsWith("UpgradeOnly")) return null
        token = t
        return t
    }

    private fun getJson(url: String): JSONObject? {
        val req = Request.Builder().url(url)
            .header("Cookie", "AWSELB=0; AWSELBCORS=0")
            .header("User-Agent", "Mozilla/5.0")
            .build()
        return client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) null else JSONObject(resp.body?.string().orEmpty())
        }
    }
}