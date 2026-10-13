package com.caohaidang.edward.music.lyrics

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

object NetEaseLyricsProvider : LyricsProvider {
    override val name = "NetEase"
    override fun isEnabled(context: Context) = true

    private const val DURATION_TOLERANCE = 6
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build()
    }

    private val TIMESTAMP = Regex("""^\[\d{1,3}:\d{2}(?:[.:]\d{1,3})?]""")
    private val CREDIT_LINE = Regex(
        """^\[[^]]+]\s*(作词|作詞|作曲|编曲|編曲|制作人|製作人|混音|母带|词|曲|Lyrics|Lyricist|Composer|Arranger|Producer)\s*[:：].*$""",
        RegexOption.IGNORE_CASE
    )

    override suspend fun getLyrics(id: String, title: String, artist: String, duration: Int): Result<String> = runCatching {
        withContext(Dispatchers.IO) {
            val cleanTitle = LyricsMatcher.cleanTitle(title)
            val mainArtist = LyricsMatcher.primaryArtist(artist)
            val songId = findSong("$cleanTitle $mainArtist", title, artist, duration)
                ?: findSong(cleanTitle, title, artist, duration)
                ?: throw IllegalStateException("No matching song")
            fetchLyrics(songId) ?: throw IllegalStateException("Lyrics unavailable")
        }
    }

    private fun get(url: String): JsonObject? {
        val request = Request.Builder()
            .url(url.toHttpUrl())
            .header("Referer", "https://music.163.com/")
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36")
            .header("Cookie", "appver=2.0.2; os=pc")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.string() ?: return null
            return json.parseToJsonElement(body).jsonObject
        }
    }

    private fun findSong(query: String, title: String, artist: String, duration: Int): Long? {
        val url = "https://music.163.com/api/search/get/web".toHttpUrl().newBuilder()
            .addQueryParameter("s", query)
            .addQueryParameter("type", "1")
            .addQueryParameter("offset", "0")
            .addQueryParameter("limit", "10")
            .addQueryParameter("total", "true")
            .build()
        val songs = get(url.toString())?.get("result")?.jsonObject?.get("songs")?.jsonArray ?: return null

        return songs.mapNotNull { element ->
            val song = element.jsonObject
            val songId = song["id"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: return@mapNotNull null
            val name = song["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val artists = (song["artists"] ?: song["ar"]) as? JsonArray
            val artistName = artists?.joinToString(", ") {
                (it as? JsonObject)?.get("name")?.jsonPrimitive?.contentOrNull.orEmpty()
            }.orEmpty()
            val durationMs = (song["duration"] ?: song["dt"])?.jsonPrimitive?.contentOrNull?.toLongOrNull()
            val delta = LyricsMatcher.durationDelta(duration, durationMs?.div(1000))
            if (delta != null && delta > DURATION_TOLERANCE) return@mapNotNull null
            val titleSim = LyricsMatcher.titleSimilarity(title, name)
            val artistSim = LyricsMatcher.artistSimilarity(artist, artistName)
            if (!LyricsMatcher.isMatch(titleSim, artistSim, delta)) return@mapNotNull null
            songId to LyricsMatcher.score(titleSim, artistSim) - (delta ?: 0) * 0.05
        }.maxByOrNull { it.second }?.first
    }

    private fun fetchLyrics(songId: Long): String? {
        val url = "https://music.163.com/api/song/lyric".toHttpUrl().newBuilder()
            .addQueryParameter("id", songId.toString())
            .addQueryParameter("lv", "-1")
            .addQueryParameter("kv", "-1")
            .addQueryParameter("tv", "-1")
            .build()
        val raw = get(url.toString())?.get("lrc")?.jsonObject?.get("lyric")?.let { (it as? JsonPrimitive)?.contentOrNull }
            ?: return null
        val lines = raw.lines()
            .map { it.trim() }
            .filter { TIMESTAMP.containsMatchIn(it) && !CREDIT_LINE.matches(it) }

            .filter { it.replace(TIMESTAMP, "").isNotBlank() }

        if (lines.size < 4) return null
        return lines.joinToString("\n")
    }
}
