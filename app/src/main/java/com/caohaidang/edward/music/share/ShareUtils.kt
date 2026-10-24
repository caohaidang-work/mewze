package com.caohaidang.edward.music.share

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import com.caohaidang.edward.music.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object ShareUtils {
    private const val INSTAGRAM_PACKAGE = "com.instagram.android"
    private const val INSTAGRAM_STORY_ACTION = "com.instagram.share.ADD_TO_STORY"

    /** An app that can receive the shared image (shown in the share sheet's bottom row). */
    class Target(val packageName: String, val activityName: String, val label: String, val icon: Drawable)

    /** Apps that accept a PNG via ACTION_SEND, one per package, Instagram excluded (it has its own Story entry). */
    fun imageTargets(context: Context, limit: Int = 8): List<Target> {
        val pm = context.packageManager
        val probe = Intent(Intent.ACTION_SEND).apply { type = "image/png" }
        val infos = runCatching { pm.queryIntentActivities(probe, PackageManager.MATCH_DEFAULT_ONLY) }
            .getOrDefault(emptyList())
        return infos.asSequence()
            .map { it.activityInfo }
            .filter { it.packageName != context.packageName && it.packageName != INSTAGRAM_PACKAGE }
            .distinctBy { it.packageName }
            .map { Target(it.packageName, it.name, it.loadLabel(pm).toString(), it.loadIcon(pm)) }
            .take(limit)
            .toList()
    }

    fun isInstagramInstalled(context: Context): Boolean =
        runCatching { context.packageManager.getPackageInfo(INSTAGRAM_PACKAGE, 0) }.isSuccess

    fun instagramIcon(context: Context): Drawable? =
        runCatching { context.packageManager.getApplicationIcon(INSTAGRAM_PACKAGE) }.getOrNull()

    fun shareImageTo(context: Context, uri: Uri, link: String?, target: Target) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            setClassName(target.packageName, target.activityName)
            putExtra(Intent.EXTRA_STREAM, uri)
            if (link != null) putExtra(Intent.EXTRA_TEXT, link)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            clipData = ClipData.newRawUri(null, uri)
        }
        context.grantUriPermission(target.packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            shareImage(context, uri, link)
        }
    }

    fun musicLink(songId: String) = "https://music.youtube.com/watch?v=$songId"

    suspend fun saveToCache(context: Context, bitmap: Bitmap, name: String): Uri = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "share").apply { mkdirs() }

        dir.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 60 * 60 * 1000L }?.forEach { it.delete() }
        val file = File(dir, "$name-${System.currentTimeMillis()}.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        FileProvider.getUriForFile(context, "${context.packageName}.FileProvider", file)
    }

    fun shareImage(context: Context, uri: Uri, link: String?) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            if (link != null) putExtra(Intent.EXTRA_TEXT, link)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = ClipData.newRawUri(null, uri)
        }
        context.startActivity(Intent.createChooser(intent, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun shareToStory(context: Context, uri: Uri, link: String?) {
        if (link != null) copyLink(context, link, toast = false)
        val intent = Intent(INSTAGRAM_STORY_ACTION).apply {
            setPackage(INSTAGRAM_PACKAGE)
            setDataAndType(uri, "image/png")
            putExtra("source_application", context.packageName)
            if (link != null) putExtra("content_url", link)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.grantUriPermission(INSTAGRAM_PACKAGE, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            if (link != null) Toast.makeText(context, R.string.share_story_link_copied, Toast.LENGTH_LONG).show()
        } catch (_: Exception) {
            shareImage(context, uri, link)
        }
    }

    fun copyLink(context: Context, link: String, toast: Boolean = true) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("link", link))
        if (toast) Toast.makeText(context, R.string.link_copied, Toast.LENGTH_SHORT).show()
    }

    fun shareLink(context: Context, link: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, link)
        }
        context.startActivity(Intent.createChooser(intent, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}