package com.caohaidang.edward.music.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Decodes [source], centre-crops to a square, scales to at most [size] px and stores it as a JPEG. */
internal suspend fun saveSquareImage(
    context: Context,
    source: Uri,
    dirName: String,
    filePrefix: String,
    size: Int,
    deleteOlder: Boolean = true,
): String? = withContext(Dispatchers.IO) {
    runCatching {
        val resolver = context.contentResolver

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(source)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= size && bounds.outHeight / (sample * 2) >= size) sample *= 2

        val decoded = resolver.openInputStream(source)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return@runCatching null

        val side = minOf(decoded.width, decoded.height)
        val square = Bitmap.createBitmap(decoded, (decoded.width - side) / 2, (decoded.height - side) / 2, side, side)
        val scaled = if (side > size) Bitmap.createScaledBitmap(square, size, size, true) else square

        val dir = File(context.filesDir, dirName).apply { mkdirs() }
        val file = File(dir, "${filePrefix}_${System.currentTimeMillis()}.jpg")
        file.outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        // Drop older versions of the same image, keep the new one.
        if (deleteOlder) {
            dir.listFiles { f -> f.name.startsWith("${filePrefix}_") && f.name != file.name }?.forEach { it.delete() }
        }
        file.absolutePath
    }.getOrNull()
}

object PlaylistCoverStorage {
    private const val SIZE = 800
    private const val DIR = "playlist_covers"

    /**
     * A cover is either a local file picked by the user (an absolute path) or the playlist's own
     * cover on YouTube Music (an https URL, kept up to date by the library sync).
     */
    fun isLocalFile(cover: String?): Boolean = cover != null && !cover.startsWith("http", ignoreCase = true)

    suspend fun save(context: Context, playlistId: String, source: Uri): String? =
        saveSquareImage(context, source, DIR, playlistId, SIZE)

    fun deleteFor(context: Context, playlistId: String) {
        File(context.filesDir, DIR).listFiles { f -> f.name.startsWith("${playlistId}_") }
            ?.forEach { it.delete() }
    }
}

/** The avatar of the in-app profile. Stored on this device only. */
object ProfileAvatarStorage {
    private const val SIZE = 512
    private const val DIR = "profile"
    private const val NAME = "avatar"

    /** Saves a candidate avatar without touching the current one; call [prune] once the user decides. */
    suspend fun save(context: Context, source: Uri): String? =
        saveSquareImage(context, source, DIR, NAME, SIZE, deleteOlder = false)

    /** Deletes every stored avatar file except [keep] (an absolute path, or blank to keep none). */
    fun prune(context: Context, keep: String?) {
        val keepName = keep?.takeIf { it.isNotBlank() }?.let { File(it).name }
        File(context.filesDir, DIR).listFiles { f -> f.name.startsWith("${NAME}_") && f.name != keepName }
            ?.forEach { it.delete() }
    }
}
