package com.caohaidang.edward.music.utils

import android.os.SystemClock
import android.util.LruCache
import com.caohaidang.edward.music.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Dịch lyrics bằng các API AI (chuẩn OpenAI chat-completions) có gói miễn phí,
 * thay cho endpoint Google Translate không chính thức vốn hay lỗi.
 * Hỗ trợ nhiều provider: một cái lỗi / hết hạn mức thì tự chuyển sang cái tiếp theo.
 * Gửi cả cụm dòng một lúc để model có ngữ cảnh bài hát -> bản dịch tự nhiên hơn.
 */
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

    // ---- Cấu hình nhà cung cấp AI ------------------------------------------------

    enum class Provider(val label: String, val baseUrl: String, val defaultModel: String, val keyUrl: String) {
        GEMINI(
            "Google Gemini",
            "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions",
            "gemini-flash-latest",
            "https://aistudio.google.com/apikey",
        ),
        GROQ(
            "Groq",
            "https://api.groq.com/openai/v1/chat/completions",
            "openai/gpt-oss-120b",
            "https://console.groq.com/keys",
        ),
        CEREBRAS(
            "Cerebras",
            "https://api.cerebras.ai/v1/chat/completions",
            "gpt-oss-120b",
            "https://cloud.cerebras.ai/platform",
        ),
        MISTRAL(
            "Mistral",
            "https://api.mistral.ai/v1/chat/completions",
            "mistral-small-latest",
            "https://console.mistral.ai/api-keys",
        ),
        OPENROUTER(
            "OpenRouter",
            "https://openrouter.ai/api/v1/chat/completions",
            "openai/gpt-oss-120b:free",
            "https://openrouter.ai/keys",
        ),
        CUSTOM("Custom (OpenAI-compatible)", "", "", ""),
    }

    data class AiConfig(
        val provider: Provider,
        val apiKey: String,
        val model: String,
        val customBaseUrl: String = "",
    ) {
        val baseUrl: String get() = if (provider == Provider.CUSTOM) customBaseUrl.trim() else provider.baseUrl
        val isUsable: Boolean get() = apiKey.isNotBlank() && model.isNotBlank() && baseUrl.startsWith("http")
    }

    /**
     * Danh sách cấu hình gắn sẵn trong app, nạp từ BuildConfig (xem app/build.gradle.kts).
     * Phần tử đầu là provider chính (AI_TRANSLATE_*), các phần tử sau là dự phòng
     * (AI_TRANSLATE_FALLBACKS, dạng `PROVIDER=key` hoặc `PROVIDER=key|model`, ngăn cách bằng dấu phẩy).
     * Khi provider đứng trước lỗi / hết hạn mức, app tự chuyển sang provider kế tiếp.
     */
    val defaultConfigs: List<AiConfig> by lazy {
        val provider = Provider.entries.firstOrNull { it.name.equals(BuildConfig.AI_TRANSLATE_PROVIDER, true) }
            ?.takeIf { it != Provider.CUSTOM } ?: Provider.GEMINI
        val primary = AiConfig(
            provider = provider,
            apiKey = BuildConfig.AI_TRANSLATE_KEY,
            model = BuildConfig.AI_TRANSLATE_MODEL.ifBlank { provider.defaultModel },
        )
        val fallbacks = BuildConfig.AI_TRANSLATE_FALLBACKS.split(',', ';', '\n').mapNotNull { entry ->
            val pair = entry.trim().split('=', limit = 2).takeIf { it.size == 2 } ?: return@mapNotNull null
            val fallbackProvider = Provider.entries.firstOrNull { it.name.equals(pair[0].trim(), true) }
                ?.takeIf { it != Provider.CUSTOM } ?: return@mapNotNull null
            val parts = pair[1].split('|', limit = 2)
            AiConfig(
                provider = fallbackProvider,
                apiKey = parts[0].trim(),
                model = parts.getOrNull(1)?.trim().orEmpty().ifBlank { fallbackProvider.defaultModel },
            )
        }
        (listOf(primary) + fallbacks).filter { it.isUsable }.ifEmpty { listOf(primary) }
    }

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

    private fun languageName(code: String): String = when (code) {
        "zh-CN" -> "Simplified Chinese"
        "zh-TW" -> "Traditional Chinese"
        "tl" -> "Filipino (Tagalog)"
        else -> Locale.forLanguageTag(code).getDisplayLanguage(Locale.ENGLISH).ifBlank { code }
    }

    // ---- Mạng --------------------------------------------------------------------

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    private val cache = LruCache<String, List<LineResult>>(6)
    private val requestPermits = Semaphore(2)

    // Cụm nhỏ để model không bỏ sót / cắt cụt đầu ra ở các dòng cuối (trước đây 150 dòng/request
    // khiến chỉ nửa đầu bài được dịch). Các cụm chạy song song (tối đa 2 request cùng lúc).
    private const val CHUNK_SIZE = 40
    private const val MAX_ATTEMPTS = 3

    // Số lần dịch bù riêng những dòng model trả thiếu.
    private const val MAX_FILL_PASSES = 2

    class AiTranslateException(message: String, val code: Int = 0) : Exception(message)

    /**
     * Dịch / phiên âm [lines]. Mỗi cụm xong sẽ gọi [onResult] ngay (trên Main) để UI hiện dần.
     * Ném [AiTranslateException] nếu có cụm thất bại sau khi retry, để UI báo lỗi cho người dùng.
     */
    suspend fun translateLines(
        songId: String,
        lines: List<String>,
        targetLang: String,
        wantTranslation: Boolean,
        wantRomanization: Boolean,
        configs: List<AiConfig>,
        onResult: (index: Int, result: LineResult) -> Unit,
    ) {
        val usableConfigs = configs.filter { it.isUsable }
        val script = if (wantRomanization) detectScript(lines) else Script.OTHER
        val cacheKey = "$songId|$targetLang|$wantTranslation|${script.name}"
        cache.get(cacheKey)?.let { cached ->
            cached.forEachIndexed { index, result -> onResult(index, result) }
            return
        }

        val results = arrayOfNulls<LineResult>(lines.size)

        // Dòng cần gửi lên AI: (index, text, cần phiên âm online?)
        class Pending(val index: Int, val text: String, val needRoman: Boolean, val offlineRoman: String?)

        val pending = ArrayList<Pending>()
        lines.forEachIndexed { index, line ->
            val text = line.trim()
            if (text.isEmpty() || !text.any { it.isLetterOrDigit() }) {
                results[index] = LineResult()
                return@forEachIndexed
            }
            val wantsRoman = needsRomanization(text, script)
            val offline = if (wantsRoman) Romanizer.romanize(text, korean = script == Script.KOREAN) else null
            val needOnlineRoman = usableConfigs.isNotEmpty() && wantsRoman && offline == null
            if (!wantTranslation && !needOnlineRoman) {
                val result = LineResult(null, offline)
                results[index] = result
                withContext(Dispatchers.Main.immediate) { onResult(index, result) }
                return@forEachIndexed
            }
            pending += Pending(index, text, needOnlineRoman, offline)
        }

        var failure: AiTranslateException? = null
        var missingFromModel = 0
        coroutineScope {
            pending.chunked(CHUNK_SIZE).map { chunk ->
                async(Dispatchers.IO) {
                    val parsed = try {
                        translateChunkWithRetry(
                            chunk.map { Triple(it.index, it.text, it.needRoman) },
                            targetLang, wantTranslation, script, usableConfigs,
                        )
                    } catch (e: AiTranslateException) {
                        failure = failure ?: e
                        emptyMap()
                    }
                    withContext(Dispatchers.Main.immediate) {
                        chunk.forEach { p ->
                            val net = parsed[p.index]
                            if (net == null) missingFromModel++
                            val translation = net?.translation?.trim()?.takeIf { it.isNotEmpty() && !sameText(it, p.text) }
                            val roman = p.offlineRoman
                                ?: net?.romanization?.trim()?.takeIf { it.isNotEmpty() && !sameText(it, p.text) }
                            val result = LineResult(translation, roman)
                            results[p.index] = result
                            onResult(p.index, result)
                        }
                    }
                }
            }.awaitAll()
        }

        failure?.let { throw it }
        // Chỉ cache khi model đã trả đủ mọi dòng, tránh lưu kết quả khuyết.
        if (missingFromModel == 0) cache.put(cacheKey, results.map { it ?: LineResult() })
    }

    /**
     * Dịch một cụm; nếu model trả thiếu dòng (hay gặp ở nửa sau bài) thì gửi lại riêng các dòng thiếu.
     */
    private suspend fun translateChunkWithRetry(
        chunk: List<Triple<Int, String, Boolean>>,
        targetLang: String,
        wantTranslation: Boolean,
        script: Script,
        configs: List<AiConfig>,
    ): Map<Int, LineResult> {
        val merged = HashMap<Int, LineResult>()
        var remaining = chunk
        repeat(1 + MAX_FILL_PASSES) { pass ->
            val parsed = try {
                translateChunkOnce(remaining, targetLang, wantTranslation, script, configs)
            } catch (e: AiTranslateException) {
                // Lần đầu thất bại thì báo lỗi; các lần bù thất bại thì giữ phần đã có.
                if (pass == 0) throw e else return merged
            }
            merged.putAll(parsed)
            remaining = chunk.filter { (index, _, needRoman) ->
                val r = merged[index]
                r == null ||
                    (wantTranslation && r.translation.isNullOrBlank()) ||
                    (needRoman && r.romanization.isNullOrBlank())
            }
            if (remaining.isEmpty()) return merged
        }
        return merged
    }

    // Provider đang "nghỉ" (hết hạn mức / key sai / lỗi liên tục) -> bỏ qua đến mốc này (elapsedRealtime).
    private val cooldownUntil = HashMap<String, Long>()

    private fun configId(config: AiConfig) = "${config.provider.name}|${config.baseUrl}|${config.model}"

    private fun isCoolingDown(config: AiConfig): Boolean = synchronized(cooldownUntil) {
        (cooldownUntil[configId(config)] ?: 0L) > SystemClock.elapsedRealtime()
    }

    private fun coolDown(config: AiConfig, e: AiTranslateException) {
        val message = e.message.orEmpty()
        val millis = when {
            // Key sai / không có quyền / model không tồn tại: đừng thử lại sớm.
            e.code == 400 || e.code == 401 || e.code == 403 || e.code == 404 -> 30 * 60_000L
            e.code == 429 && message.contains("per day", ignoreCase = true) -> 30 * 60_000L
            e.code == 429 -> 60_000L
            else -> 30_000L
        }
        synchronized(cooldownUntil) { cooldownUntil[configId(config)] = SystemClock.elapsedRealtime() + millis }
    }

    /**
     * Thử lần lượt từng provider; provider nào lỗi (hết hạn mức, key sai, mạng...) thì chuyển sang provider kế.
     * Ném lỗi của provider cuối cùng nếu tất cả đều thất bại.
     */
    private suspend fun translateChunkOnce(
        chunk: List<Triple<Int, String, Boolean>>,
        targetLang: String,
        wantTranslation: Boolean,
        script: Script,
        configs: List<AiConfig>,
    ): Map<Int, LineResult> {
        // Nếu tất cả đều đang nghỉ thì vẫn thử hết, tốt hơn là báo lỗi ngay.
        val candidates = configs.filterNot { isCoolingDown(it) }.ifEmpty { configs }
        var last: AiTranslateException? = null
        candidates.forEachIndexed { position, config ->
            val isLast = position == candidates.lastIndex
            try {
                // Còn provider dự phòng thì không cần retry lâu trên provider đang lỗi.
                return translateChunkWithAttempts(
                    chunk, targetLang, wantTranslation, script, config,
                    attempts = if (isLast) MAX_ATTEMPTS else 1,
                )
            } catch (e: AiTranslateException) {
                coolDown(config, e)
                last = AiTranslateException("${config.provider.label}: ${e.message.orEmpty()}", e.code)
            }
        }
        throw last ?: AiTranslateException("Translation failed")
    }

    private suspend fun translateChunkWithAttempts(
        chunk: List<Triple<Int, String, Boolean>>,
        targetLang: String,
        wantTranslation: Boolean,
        script: Script,
        config: AiConfig,
        attempts: Int,
    ): Map<Int, LineResult> {
        var last: AiTranslateException? = null
        repeat(attempts) { attempt ->
            try {
                return requestPermits.withPermit {
                    translateChunk(chunk, targetLang, wantTranslation, script, config)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: AiTranslateException) {
                last = e
                // Key sai / không có quyền: retry vô ích.
                if (e.code == 400 || e.code == 401 || e.code == 403 || e.code == 404) throw e
                // Hết hạn mức theo ngày: thử lại chỉ làm tốn thêm request.
                if (e.code == 429 && e.message.orEmpty().contains("per day", ignoreCase = true)) throw e
                delay(if (e.code == 429) 4000L * (attempt + 1) else 800L * (attempt + 1))
            } catch (e: Exception) {
                last = AiTranslateException(e.message ?: "Network error")
                delay(800L * (attempt + 1))
            }
        }
        throw last ?: AiTranslateException("Translation failed")
    }

    private fun translateChunk(
        chunk: List<Triple<Int, String, Boolean>>,
        targetLang: String,
        wantTranslation: Boolean,
        script: Script,
        config: AiConfig,
    ): Map<Int, LineResult> {
        val romanStyle = when (script) {
            Script.KOREAN -> "Revised Romanization of Korean"
            Script.JAPANESE -> "Hepburn romaji"
            Script.OTHER -> "standard Latin-script romanization"
        }
        val system = buildString {
            append("You translate song lyrics. The input is a JSON array of lyric lines from ONE song, in order, ")
            append("so use neighbouring lines for context (metaphors, slang, repeated hooks).\n")
            if (wantTranslation) {
                append("- Translate every line into ${languageName(targetLang)} as a gifted lyricist and literary poet would, ")
                append("not as a machine translator. The goal is a poetic, emotionally resonant rendering that reads like real song lyrics ")
                append("in ${languageName(targetLang)}, while staying faithful to the meaning.\n")
                append("  * Preserve and, where natural, enrich metaphors, imagery, symbolism and wordplay; choose evocative, ")
                append("lyrical vocabulary over plain everyday wording.\n")
                append("  * Capture the emotion, mood and subtext of each line (longing, bitterness, tenderness, defiance), ")
                append("using the surrounding lines to understand what the singer really means.\n")
                append("  * Favour smooth rhythm and a gentle poetic flow; adapt idioms and slang into equally expressive native phrasing ")
                append("instead of literal word-for-word translation. Keep repeated hooks consistent.\n")
                append("  * Never add explanations or notes, never invent new story content, never censor. ")
                append("Keep each translation to a single line. If a line is already in the target language, copy it unchanged.\n")
            }
            append("- For items with \"roman\": true also give a $romanStyle romanization of the original line in field \"r\".\n")
            append("Reply with ONLY a JSON object: {\"lines\":[{\"i\":<same i as input>,")
            if (wantTranslation) append("\"t\":\"<translation>\",")
            append("\"r\":\"<romanization or empty string>\"}]}. ")
            append("Return exactly one object per input item, same i values, no markdown fences.")
        }
        val userContent = buildJsonArray {
            chunk.forEach { (index, text, needRoman) ->
                add(buildJsonObject {
                    put("i", index)
                    put("text", text)
                    if (needRoman) put("roman", true)
                })
            }
        }.toString()

        val body = buildJsonObject {
            put("model", config.model)
            put("temperature", 0.7)
            put("response_format", buildJsonObject { put("type", "json_object") })
            put("messages", buildJsonArray {
                add(buildJsonObject { put("role", "system"); put("content", system) })
                add(buildJsonObject { put("role", "user"); put("content", userContent) })
            })
        }.toString()

        val request = Request.Builder()
            .url(config.baseUrl)
            .header("Authorization", "Bearer ${config.apiKey.trim()}")
            .post(body.toRequestBody(jsonMedia))
            .build()

        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw AiTranslateException(extractError(text) ?: "HTTP ${response.code}", response.code)
            }
            val content = json.parseToJsonElement(text).jsonObject["choices"]
                ?.jsonArray?.firstOrNull()?.jsonObject
                ?.get("message")?.jsonObject
                ?.get("content")?.jsonPrimitive?.contentOrNull
                ?: throw AiTranslateException("Empty response")
            return parseLines(content, wantTranslation)
        }
    }

    private fun extractError(body: String): String? = runCatching {
        val root = json.parseToJsonElement(body)
        val obj = if (root is JsonArray) root.firstOrNull()?.jsonObject else root.jsonObject
        obj?.get("error")?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull
    }.getOrNull()

    private fun parseLines(content: String, wantTranslation: Boolean): Map<Int, LineResult> {
        // Một số model vẫn bọc ```json ... ``` dù đã yêu cầu không.
        val start = content.indexOf('{')
        val end = content.lastIndexOf('}')
        if (start < 0 || end <= start) throw AiTranslateException("Unreadable response")
        val root = runCatching { json.parseToJsonElement(content.substring(start, end + 1)).jsonObject }
            .getOrElse { throw AiTranslateException("Unreadable response") }
        val array = root["lines"] as? JsonArray ?: throw AiTranslateException("Unreadable response")
        val out = HashMap<Int, LineResult>()
        array.forEach { element ->
            val obj = element as? JsonObject ?: return@forEach
            val index = obj["i"]?.jsonPrimitive?.intOrNull ?: return@forEach
            val t = if (wantTranslation) obj["t"]?.jsonPrimitive?.contentOrNull else null
            val r = obj["r"]?.jsonPrimitive?.contentOrNull
            out[index] = LineResult(t, r)
        }
        if (out.isEmpty()) throw AiTranslateException("Empty translation")
        return out
    }

    private fun sameText(a: String, b: String) =
        a.filter { it.isLetterOrDigit() }.equals(b.filter { it.isLetterOrDigit() }, ignoreCase = true)

    suspend fun clearModels() {
        cache.evictAll()
    }
}
