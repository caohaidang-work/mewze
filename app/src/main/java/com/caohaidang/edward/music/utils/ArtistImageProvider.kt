package com.caohaidang.edward.music.utils

import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object ArtistImageProvider {
    private val cache = LruCache<String, String>(64)
    private val misses = HashSet<String>()

    suspend fun find(name: String): String? {
        val key = name.trim().lowercase()
        if (key.isEmpty() || key in misses) return null
        cache.get(key)?.let { return it }

        val url = withContext(Dispatchers.IO) {
            runCatching {
                val q = URLEncoder.encode(name.trim(), "UTF-8")
                val conn = (URL("https://api.deezer.com/search/artist?q=$q&limit=5").openConnection() as HttpURLConnection).apply {
                    connectTimeout = 5000
                    readTimeout = 5000
                }
                try {
                    if (conn.responseCode != 200) return@runCatching null
                    val data = JSONObject(conn.inputStream.bufferedReader().readText()).optJSONArray("data")
                        ?: return@runCatching null
                    var best: JSONObject? = null
                    for (i in 0 until data.length()) {
                        val o = data.getJSONObject(i)
                        if (!o.optString("name").trim().equals(name.trim(), ignoreCase = true)) continue
                        if (best == null || o.optInt("nb_fan") > best.optInt("nb_fan")) best = o
                    }
                    best?.optString("picture_xl")?.takeIf {
                        it.isNotBlank() && !it.contains("/images/artist//")
                    }
                } finally {
                    conn.disconnect()
                }
            }.getOrNull()
        }

        if (url != null) cache.put(key, url) else synchronized(misses) { misses.add(key) }
        return url
    }
}
