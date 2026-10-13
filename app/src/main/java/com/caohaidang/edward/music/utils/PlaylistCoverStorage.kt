package com.caohaidang.edward.music.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object PlaylistCoverStorage {
    private const val SIZE = 800
    private const val DIR = "playlist_covers"

    suspend fun save(context: Context, playlistId: String, source: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(source)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= SIZE && bounds.outHeight / (sample * 2) >= SIZE) sample *= 2

            val decoded = resolver.openInputStream(source)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: return@runCatching null

            val side = minOf(decoded.width, decoded.height)
            val square = Bitmap.createBitmap(decoded, (decoded.width - side) / 2, (decoded.height - side) / 2, side, side)
            val scaled = if (side > SIZE) Bitmap.createScaledBitmap(square, SIZE, SIZE, true) else square

            val dir = File(context.filesDir, DIR).apply { mkdirs() }

            val file = File(dir, "${playlistId}_${System.currentTimeMillis()}.jpg")
            file.outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            deleteFor(context, playlistId, keep = file.name)
            file.absolutePath
        }.getOrNull()
    }

    fun deleteFor(context: Context, playlistId: String, keep: String? = null) {
        File(context.filesDir, DIR).listFiles { f -> f.name.startsWith("${playlistId}_") && f.name != keep }
            ?.forEach { it.delete() }
    }
}
