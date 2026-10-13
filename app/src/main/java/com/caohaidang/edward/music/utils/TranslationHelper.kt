package com.caohaidang.edward.music.utils

import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

object TranslationHelper {
    data class Language(val code: String, val name: String)

    const val DEFAULT_LANGUAGE = "vi"

    val languages = listOf(
        Language("vi", "Tiếng Việt"),
        Language("en", "English"),
        Language("zh-CN", "中文 (简体)"),
        Language("zh-TW", "中文 (繁體)"),
        Language("ja", "日本語"),
        Language("ko", "한국어"),
        Language("th", "ภาษาไทย"),
        Language("id", "Bahasa Indonesia"),
        Language("ms", "Bahasa Melayu"),
        Language("tl", "Filipino"),
        Language("es", "Español"),
        Language("pt", "Português"),
        Language("fr", "Français"),
        Language("de", "Deutsch"),
        Language("it", "Italiano"),
        Language("ru", "Русский"),
        Language("tr", "Türkçe"),
        Language("ar", "العربية"),
        Language("hi", "हिन्दी"),
    )

    private val latinTargets = setOf("vi", "en", "id", "ms", "tl", "es", "pt", "fr", "de", "it", "tr")

    data class LineResult(
        val translation: String? = null,
        val romanization: String? = null,
    )

    enum class Script { KOREAN, JAPANESE, OTHER }

    private val HANGUL = Regex("""[ᄀ-ᇿ㄰-㆏가-힯]""")
    private val KANA = Regex("""[぀-ヿㇰ-ㇿ]""")
    private val HAN = Regex("""[一-鿿]""")

    fun detectScript(lines: List<String>): Script {
        val text = lines.joinToString("\n")
        val hangul = HANGUL.findAll(text).count()
        val kana = KANA.findAll(text).count()
        return when {
            hangul > kana && hangul >= 3 -> Script.KOREAN
            kana >= 3 -> Script.JAPANESE
            else -> Script.OTHER
        }
    }

    private fun needsRomanization(line: String, script: Script) = when (script) {
        Script.KOREAN -> HANGUL.containsMatchIn(line)
        Script.JAPANESE -> KANA.containsMatchIn(line) || HAN.containsMatchIn(line)
        Script.OTHER -> false
    }

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val cache = LruCache<String, List<LineResult>>(6)
    private val requestPermits = Semaphore(3)

    suspend fun translateLines(
        songId: String,
        lines: List<String>,
        targetLang: String,
        wantTranslation: Boolean,
        wantRomanization: Boolean,
        onResult: (index: Int, result: LineResult) -> Unit,
    ) {
        val script = if (wantRomanization) detectScript(lines) else Script.OTHER
        val cacheKey = "$songId|$targetLang|$wantTranslation|${script.name}"
        cache.get(cacheKey)?.let { cached ->
            cached.forEachIndexed { index, result -> onResult(index, result) }
            return
        }

        val results = arrayOfNulls<LineResult>(lines.size)

        val memo = HashMap<String, kotlinx.coroutines.Deferred<LineResult>>()
        coroutineScope {
            lines.forEachIndexed { index, line ->
                val text = line.trim()
                if (text.isEmpty() || !text.any { it.isLetterOrDigit() }) {
                    results[index] = LineResult()
                    return@forEachIndexed
                }
                val wantsRoman = needsRomanization(text, script)

                val offline = if (wantsRoman) Romanizer.romanize(text, korean = script == Script.KOREAN) else null

                val needOnlineRoman = wantsRoman && offline == null
                if (!wantTranslation && !needOnlineRoman) {
                    val result = LineResult(null, offline)
                    results[index] = result
                    onResult(index, result)
                    return@forEachIndexed
                }
                val deferred = memo.getOrPut("$needOnlineRoman|$text") {
                    async(Dispatchers.IO) {
                        translateWithRetry(text, targetLang, wantTranslation, needOnlineRoman)
                    }
                }
                async {
                    val net = deferred.await()
                    val result = LineResult(net.translation, offline ?: net.romanization)
                    results[index] = result
                    withContext(Dispatchers.Main.immediate) { onResult(index, result) }
                }
            }
        }
        val finished = results.map { it ?: LineResult() }

        val attempted = finished.count { it.translation != null || it.romanization != null }
        if (attempted >= lines.count { it.isNotBlank() } * 9 / 10) cache.put(cacheKey, finished)
    }

    private suspend fun translateWithRetry(text: String, targetLang: String, wantTranslation: Boolean, romanize: Boolean): LineResult {
        repeat(MAX_ATTEMPTS) { attempt ->
            val result = runCatching {
                requestPermits.withPermit {
                    delay(60)
                    translateOne(text, targetLang, wantTranslation, romanize)
                }
            }
            result.getOrNull()?.let { return it }
            if (result.exceptionOrNull() is kotlin.coroutines.cancellation.CancellationException) throw result.exceptionOrNull()!!
            delay(500L * (attempt + 1))
        }
        return LineResult()
    }

    private const val MAX_ATTEMPTS = 4

    private fun translateOne(text: String, targetLang: String, wantTranslation: Boolean, romanize: Boolean): LineResult {
        var translation: String? = null
        var romanization: String? = null

        val root = request(text, targetLang, withRomanization = romanize, sourceLang = if (romanize) "ja" else "auto")
        if (wantTranslation) translation = extractTranslation(root)
        if (romanize && targetLang in latinTargets) romanization = extractRomanization(root)
        if (romanize && romanization == null && targetLang !in latinTargets) {
            romanization = extractRomanization(request(text, "en", withRomanization = true, sourceLang = "ja"))
        }
        val cleanTranslation = translation?.trim()?.takeIf { it.isNotEmpty() && !sameText(it, text) }
        return LineResult(cleanTranslation, romanization?.trim()?.takeIf { it.isNotEmpty() && !sameText(it, text) })
    }

    private fun sameText(a: String, b: String) =
        a.filter { it.isLetterOrDigit() }.equals(b.filter { it.isLetterOrDigit() }, ignoreCase = true)

    private fun request(text: String, targetLang: String, withRomanization: Boolean, sourceLang: String = "auto"): JsonArray {
        val url = "https://translate.googleapis.com/translate_a/single".toHttpUrl().newBuilder()
            .addQueryParameter("client", "gtx")
            .addQueryParameter("sl", sourceLang)
            .addQueryParameter("tl", targetLang)
            .addQueryParameter("dt", "t")
            .apply { if (withRomanization) addQueryParameter("dt", "rm") }
            .addQueryParameter("q", text)
            .build()
        val request = Request.Builder().url(url).header("User-Agent", "Mozilla/5.0").build()
        client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "Translate HTTP ${response.code}" }
            val body = checkNotNull(response.body?.string()) { "Empty translate response" }
            return json.parseToJsonElement(body).jsonArray
        }
    }

    private fun segments(root: JsonArray): List<JsonArray> =
        (root.getOrNull(0) as? JsonArray)?.mapNotNull { it as? JsonArray }.orEmpty()

    private fun extractTranslation(root: JsonArray): String? =
        segments(root).mapNotNull { (it.getOrNull(0) as? JsonPrimitive)?.takeIf { p -> p !is JsonNull }?.contentOrNull }
            .joinToString("")
            .takeIf { it.isNotBlank() }

    private fun extractRomanization(root: JsonArray): String? =
        segments(root)
            .filter { it.getOrNull(0) == null || it.getOrNull(0) is JsonNull }
            .mapNotNull { (it.getOrNull(2) as? JsonPrimitive)?.takeIf { p -> p !is JsonNull }?.contentOrNull }
            .joinToString(" ")
            .takeIf { it.isNotBlank() }

    suspend fun clearModels() {
        cache.evictAll()
    }
}
