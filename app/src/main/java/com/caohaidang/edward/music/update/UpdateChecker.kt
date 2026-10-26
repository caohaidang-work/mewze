package com.caohaidang.edward.music.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.caohaidang.edward.music.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Kiểm tra / tải / cài bản cập nhật từ GitHub Releases của repo.
 * Quy ước: mỗi release có tag dạng `v2.1.0` (khớp `versionName`) và đính kèm 1 file .apk đã ký.
 */
object UpdateChecker {
    const val REPO = "caohaidang-work/mewze"
    private const val LATEST_URL = "https://api.github.com/repos/$REPO/releases/latest"

    data class UpdateInfo(
        val versionName: String,
        val notes: String,
        val apkUrl: String,
        val apkSize: Long,
        val sha256: String?,
        val pageUrl: String,
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    /**
     * Trả về [UpdateInfo] nếu có bản mới hơn [currentVersion], `null` nếu đã là bản mới nhất
     * (hoặc chưa có release nào). Ném [IOException] nếu lỗi mạng / phản hồi không hợp lệ.
     */
    suspend fun check(currentVersion: String = BuildConfig.VERSION_NAME): UpdateInfo? =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(LATEST_URL)
                .header("Accept", "application/vnd.github+json")
                .build()
            client.newCall(request).execute().use { resp ->
                if (resp.code == 404) return@withContext null // chưa có release
                if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
                val json = JSONObject(resp.body?.string() ?: throw IOException("Empty body"))

                val tag = json.optString("tag_name")
                if (tag.isBlank() || !isNewer(tag, currentVersion)) return@withContext null

                val assets = json.optJSONArray("assets") ?: return@withContext null
                var chosen: JSONObject? = null
                for (i in 0 until assets.length()) {
                    val a = assets.getJSONObject(i)
                    if (!a.optString("name").endsWith(".apk", ignoreCase = true)) continue
                    // ưu tiên APK không phải debug
                    if (chosen == null || !a.optString("name").contains("debug", ignoreCase = true)) {
                        chosen = a
                        if (!a.optString("name").contains("debug", ignoreCase = true)) break
                    }
                }
                val apk = chosen ?: return@withContext null

                UpdateInfo(
                    versionName = tag.removePrefix("v").removePrefix("V"),
                    notes = json.optString("body").trim(),
                    apkUrl = apk.getString("browser_download_url"),
                    apkSize = apk.optLong("size", 0L),
                    sha256 = apk.optString("digest")
                        .takeIf { it.startsWith("sha256:") }
                        ?.removePrefix("sha256:"),
                    pageUrl = json.optString("html_url"),
                )
            }
        }

    /** So sánh các cụm số: "v2.1.0" > "2.0.9". */
    fun isNewer(remote: String, local: String): Boolean {
        fun parts(v: String) = Regex("\\d+").findAll(v.substringBefore('-'))
            .map { it.value.toIntOrNull() ?: 0 }.toList()
        val r = parts(remote)
        val l = parts(local)
        for (i in 0 until maxOf(r.size, l.size)) {
            val a = r.getOrElse(i) { 0 }
            val b = l.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }

    /** Tải APK về cache, báo tiến độ 0f..1f, kiểm tra SHA-256 nếu GitHub cung cấp. */
    suspend fun download(
        context: Context,
        info: UpdateInfo,
        onProgress: (Float) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val target = File(dir, "Mewze-${info.versionName}.apk")
        val part = File(dir, target.name + ".part")

        val digest = MessageDigest.getInstance("SHA-256")
        client.newCall(Request.Builder().url(info.apkUrl).build()).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            val body = resp.body ?: throw IOException("Empty body")
            val total = body.contentLength().takeIf { it > 0 } ?: info.apkSize
            var done = 0L
            var lastPercent = -1
            val buf = ByteArray(64 * 1024)
            body.byteStream().use { input ->
                part.outputStream().use { out ->
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = input.read(buf)
                        if (n == -1) break
                        out.write(buf, 0, n)
                        digest.update(buf, 0, n)
                        done += n
                        if (total > 0) {
                            val percent = (done * 100 / total).toInt()
                            if (percent != lastPercent) {
                                lastPercent = percent
                                onProgress(percent / 100f)
                            }
                        }
                    }
                }
            }
        }

        info.sha256?.let { expected ->
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            if (!actual.equals(expected, ignoreCase = true)) {
                part.delete()
                throw IOException("SHA-256 mismatch")
            }
        }
        if (!part.renameTo(target)) throw IOException("Cannot save APK")
        target
    }

    fun canInstall(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    /** Mở màn hình cấp quyền "Cài ứng dụng không rõ nguồn gốc" cho riêng Mewze. */
    fun openInstallPermissionSettings(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun install(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.FileProvider", apk)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
