package com.caohaidang.edward.music.share

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
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
