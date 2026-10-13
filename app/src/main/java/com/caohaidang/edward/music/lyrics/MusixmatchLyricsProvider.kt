package com.caohaidang.edward.music.lyrics

import android.content.Context
import com.caohaidang.edward.music.constants.EnableMusixmatchKey
import com.caohaidang.edward.music.utils.dataStore
import com.caohaidang.edward.music.utils.get
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit

object MusixmatchLyricsProvider : LyricsProvider {
    override val name = "Musixmatch"

    override fun isEnabled(context: Context): Boolean =
        context.dataStore[EnableMusixmatchKey] ?: true

    private const val BASE_URL = "https://apic-desktop.musixmatch.com/ws/1.1/"
    private const val APP_ID = "web-desktop-app-v1.0"
    private const val DURATION_TOLERANCE = 6
    private const val UNAUTHORIZED = 401

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    @Volatile
    private var userToken: String? = null
    private val tokenMutex = Mutex()

    private class UnauthorizedException : Exception("Musixmatch token rejected")

    override suspend fun getLyrics(id: String, title: String, artist: String, duration: Int): Result<String> =
        runCatching {
            withContext(Dispatchers.IO) {
                val cleanTitle = LyricsMatcher.cleanTitle(title)
                val mainArtist = LyricsMatcher.primaryArtist(artist)
                try {
                    fetch(cleanTitle, mainArtist, title, artist, duration)
                } catch (e: UnauthorizedException) {
                    userToken = null
                    fetch(cleanTitle, mainArtist, title, artist, duration)
                }
            }
        }

    private suspend fun fetch(
        cleanTitle: String,
        mainArtist: String,
        wantedTitle: String,
        wantedArtist: String,
        duration: Int,
    ): String {
        val token = ensureToken()
        val params = buildMap {
            put("format", "json")
            put("namespace", "lyrics_synched")
            put("part", "lyrics_crowd,user,lyrics_verified_by")
            put("subtitle_format", "lrc")
            put("tags", "nowplaying")
            put("q_track", cleanTitle)
            put("q_artist", mainArtist)
            put("q_album", "")
            if (duration > 0) {
                put("q_duration", duration.toString())
                put("f_subtitle_length", duration.toString())
            }
            put("usertoken", token)
        }
        val message = request("macro.subtitles.get", params)
        val calls = message.obj("body").obj("macro_calls")

        val track = calls.obj("matcher.track.get").obj("message").obj("body").obj("track")
        val foundTitle = track.string("track_name")
        val foundArtist = track.string("artist_name")
        if (foundTitle.isEmpty()) throw IllegalStateException("No matching song")
        val delta = LyricsMatcher.durationDelta(duration, track["track_length"]?.jsonPrimitive?.intOrNull)
        if (delta != null && delta > DURATION_TOLERANCE) throw IllegalStateException("Duration mismatch")
        val titleSim = LyricsMatcher.titleSimilarity(wantedTitle, foundTitle)
        val artistSim = LyricsMatcher.artistSimilarity(wantedArtist, foundArtist)
        if (!LyricsMatcher.isMatch(titleSim, artistSim, delta)) throw IllegalStateException("Song mismatch")
        if (track["instrumental"]?.jsonPrimitive?.intOrNull == 1) throw IllegalStateException("Instrumental")

        val subtitleBody = calls.obj("track.subtitles.get").obj("message").obj("body")
            .array("subtitle_list").firstOrNull().obj().obj("subtitle").string("subtitle_body")
        parseSynced(subtitleBody)?.let { return it }

        val plain = calls.obj("track.lyrics.get").obj("message").obj("body").obj("lyrics").string("lyrics_body")
        cleanPlain(plain)?.let { return it }

        throw IllegalStateException("Lyrics unavailable")
    }

    private fun parseSynced(body: String): String? {
        val text = body.trim()
        if (text.isEmpty()) return null
        if (text.startsWith("[") && !text.startsWith("[{")) {
            return text.takeIf { it.lines().count { l -> l.trimStart().startsWith("[") } >= 4 }
        }
        if (text.startsWith("[{")) {
            val lines = (runCatching { json.parseToJsonElement(text) }.getOrNull() as? JsonArray)
                ?.mapNotNull { element ->
                    val obj = element as? JsonObject ?: return@mapNotNull null
                    val line = obj.string("text")
                    val seconds = obj.obj("time")["total"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()
                        ?: return@mapNotNull null
                    if (line.isBlank()) null else formatLrc(seconds, line)
                }.orEmpty()
            return lines.takeIf { it.size >= 4 }?.joinToString("\n")
        }
        return null
    }

    private fun formatLrc(seconds: Double, line: String): String {
        val total = (seconds * 100).toLong()
        val min = total / 6000
        val sec = (total % 6000) / 100
        val hundredths = total % 100
        return String.format(Locale.US, "[%02d:%02d.%02d]%s", min, sec, hundredths, line)
    }

    private fun cleanPlain(body: String): String? {
        val cleaned = body.substringBefore("*******").trim()
        return cleaned.takeIf { it.lines().size >= 4 }
    }

    private suspend fun ensureToken(): String {
        userToken?.let { return it }
        return tokenMutex.withLock {
            userToken ?: requestToken().also { userToken = it }
        }
    }

    private fun requestToken(): String {
        val message = request(
            "token.get",
            mapOf("format" to "json", "guid" to UUID.randomUUID().toString().replace("-", "").take(16))
        )
        val token = message.obj("body").string("user_token")

        if (token.isEmpty() || token.startsWith("UpgradeOnly")) {
            throw IllegalStateException("Musixmatch token unavailable")
        }
        return token
    }

    private fun request(method: String, params: Map<String, String>): JsonObject {
        val url = (BASE_URL + method).toHttpUrl().newBuilder().apply {
            addQueryParameter("app_id", APP_ID)
            addQueryParameter("t", System.currentTimeMillis().toString())
            params.forEach { (k, v) -> addQueryParameter(k, v) }
        }.build()
        val request = Request.Builder()
            .url(url)
            .header("authority", "apic-desktop.musixmatch.com")
            .header("cookie", "AWSELBCORS=0; AWSELB=0")
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Musixmatch/0.19.4 Chrome/58.0.3029.110 Electron/1.7.6 Safari/537.36")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("Musixmatch HTTP ${response.code}")
            val body = response.body?.string() ?: throw IllegalStateException("Empty response")
            val message = (json.parseToJsonElement(body) as? JsonObject).obj("message")
            val status = message.obj("header")["status_code"]?.jsonPrimitive?.intOrNull
            if (status == UNAUTHORIZED) throw UnauthorizedException()
            return message
        }
    }

    private fun JsonElement?.obj(): JsonObject = this as? JsonObject ?: JsonObject(emptyMap())
    private fun JsonElement?.obj(key: String): JsonObject = (this as? JsonObject)?.get(key).obj()
    private fun JsonElement?.array(key: String): List<JsonElement> =
        ((this as? JsonObject)?.get(key) as? JsonArray)?.toList().orEmpty()

    private fun JsonElement?.string(key: String): String =
        ((this as? JsonObject)?.get(key) as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull.orEmpty()
}
