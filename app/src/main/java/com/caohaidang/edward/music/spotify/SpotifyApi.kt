package com.caohaidang.edward.music.spotify

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

data class SpotifyTrack(
    val id: String,
    val title: String,
    val artists: List<String>,
    val album: String?,
    val durationMs: Int,
)

data class SpotifyPlaylist(
    val id: String,
    val name: String,
    val trackCount: Int,
    val ownerId: String,
    val collaborative: Boolean,
    val imageUrl: String?,
)

class SpotifyApiException(message: String, val code: Int = -1) : Exception(message)

/** Thin client for the few Spotify Web API endpoints needed to read a user's library. */
class SpotifyApi(private val auth: SpotifyAuth) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun me(): SpotifyAccount {
        val o = get("$BASE/me").jsonObject
        val id = o.str("id").orEmpty()
        return SpotifyAccount(id = id, displayName = o.str("display_name") ?: id)
    }

    /** Liked Songs, newest first. [onProgress] gets (loaded, total). */
    suspend fun likedTracks(onProgress: (Int, Int) -> Unit = { _, _ -> }): List<SpotifyTrack> {
        // Paging by offset while the library changes (or a page comes back short) can silently skip
        // songs, which made the imported Liked Songs differ from run to run. Compare what arrived
        // with the total Spotify reports and fetch again when entries are missing.
        var best: List<SpotifyTrack> = emptyList()
        var bestEntries = -1
        repeat(LIKED_FETCH_ATTEMPTS) { attempt ->
            val out = LinkedHashMap<String, SpotifyTrack>()
            var entries = 0
            var total = 0
            var url: String? = "$BASE/me/tracks?limit=50"
            while (url != null) {
                val page = get(url).jsonObject
                val items = page.arr("items")
                entries += items.size
                items.forEach { entry ->
                    val o = entry as? JsonObject ?: return@forEach
                    parseTrack(o["track"] ?: o["item"])?.let { out.putIfAbsent(it.id, it) }
                }
                total = page["total"]?.jsonPrimitive?.intOrNull ?: entries
                onProgress(out.size, total)
                url = page.str("next")
            }
            if (entries > bestEntries) {
                best = out.values.toList()
                bestEntries = entries
            }
            if (entries >= total) return best
            if (attempt < LIKED_FETCH_ATTEMPTS - 1) delay(1500)
        }
        return best
    }

    suspend fun likedCount(): Int =
        get("$BASE/me/tracks?limit=1").jsonObject["total"]?.jsonPrimitive?.intOrNull ?: 0

    suspend fun playlists(): List<SpotifyPlaylist> {
        val out = ArrayList<SpotifyPlaylist>()
        var url: String? = "$BASE/me/playlists?limit=50"
        while (url != null) {
            val page = get(url).jsonObject
            page.arr("items").forEach { el ->
                val o = (el as? JsonObject) ?: return@forEach
                val id = o.str("id") ?: return@forEach
                // Feb-2026 API renamed the "tracks" summary to "items".
                val total = (o["items"] as? JsonObject ?: o["tracks"] as? JsonObject)
                    ?.get("total")?.jsonPrimitive?.intOrNull ?: 0
                out += SpotifyPlaylist(
                    id = id,
                    name = o.str("name").orEmpty().ifBlank { "Playlist Spotify" },
                    trackCount = total,
                    ownerId = (o["owner"] as? JsonObject)?.str("id").orEmpty(),
                    collaborative = o["collaborative"]?.jsonPrimitive?.booleanOrNull == true,
                    imageUrl = (o["images"] as? JsonArray)?.firstOrNull()?.let { (it as? JsonObject)?.str("url") },
                )
            }
            url = page.str("next")
        }
        return out
    }

    /**
     * Tracks of a playlist, in playlist order. Podcast episodes and local files are skipped.
     * Uses `/items` (current API); throws [SpotifyApiException] with code 403/404 when the playlist
     * is not readable (e.g. a playlist the user only follows).
     */
    suspend fun playlistTracks(playlistId: String): List<SpotifyTrack> {
        val out = ArrayList<SpotifyTrack>()
        var url: String? = "$BASE/playlists/$playlistId/items?limit=50&additional_types=track"
        while (url != null) {
            val page = get(url).jsonObject
            page.arr("items").forEach { entry ->
                val o = entry as? JsonObject ?: return@forEach
                parseTrack(o["item"] ?: o["track"])?.let(out::add)
            }
            url = page.str("next")
        }
        return out
    }

    // ------------------------------------------------------------------ internals

    private fun parseTrack(el: JsonElement?): SpotifyTrack? {
        val o = el as? JsonObject ?: return null
        if (o["is_local"]?.jsonPrimitive?.booleanOrNull == true) return null
        val type = o.str("type")
        if (type != null && type != "track") return null
        val id = o.str("id") ?: return null
        val title = o.str("name")?.takeIf { it.isNotBlank() } ?: return null
        return SpotifyTrack(
            id = id,
            title = title,
            artists = o.arr("artists").mapNotNull { (it as? JsonObject)?.str("name") },
            album = (o["album"] as? JsonObject)?.str("name"),
            durationMs = o["duration_ms"]?.jsonPrimitive?.intOrNull ?: -1,
        )
    }

    private suspend fun get(url: String): JsonElement {
        var refreshed = false
        var attempt = 0
        var netAttempt = 0
        while (true) {
            val token = auth.accessToken()
            val result = try {
                withContext(Dispatchers.IO) {
                    http.newCall(Request.Builder().url(url).header("Authorization", "Bearer $token").build())
                        .execute().use { Triple(it.code, it.body?.string().orEmpty(), it.header("Retry-After")) }
                }
            } catch (e: IOException) {
                // Dropped connection / timeout in the middle of a long library download: retry the page, not the whole import.
                if (++netAttempt > NET_RETRIES) throw SpotifyApiException("Mất kết nối tới Spotify, thử lại sau", -1)
                delay(1000L * netAttempt)
                continue
            }
            val (code, body, retryAfter) = result
            when {
                code in 200..299 -> return try {
                    json.parseToJsonElement(body)
                } catch (e: Exception) {
                    if (++netAttempt > NET_RETRIES) throw SpotifyApiException("Spotify trả dữ liệu không hợp lệ", code)
                    delay(1000L * netAttempt)
                    continue
                }
                code in 500..599 && ++netAttempt <= NET_RETRIES -> delay(1000L * netAttempt)
                code == 401 && !refreshed -> {
                    refreshed = true
                    auth.accessToken(forceRefresh = true)
                }
                code == 429 && attempt < 5 -> {
                    attempt++
                    delay(((retryAfter?.toLongOrNull() ?: 2L).coerceIn(1L, 30L)) * 1000)
                }
                code == 401 -> throw SpotifyApiException("Phiên Spotify đã hết hạn, hãy đăng nhập lại", code)
                code == 403 -> throw SpotifyApiException(
                    "Spotify từ chối truy cập (403). Nếu app ở Development mode, hãy thêm tài khoản của bạn " +
                        "vào User Management trong Spotify Developer Dashboard.", code
                )
                else -> throw SpotifyApiException("Spotify lỗi $code", code)
            }
        }
    }

    private fun JsonObject.str(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull

    private fun JsonObject.arr(key: String): JsonArray = (this[key] as? JsonArray) ?: JsonArray(emptyList())

    private companion object {
        const val BASE = "https://api.spotify.com/v1"
        const val NET_RETRIES = 3
        const val LIKED_FETCH_ATTEMPTS = 3
    }
}
