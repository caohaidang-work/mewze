package com.caohaidang.edward.music.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File

/** Everything the CD Player widget needs to draw itself. */
data class CdWidgetState(
    val title: String? = null,
    val artist: String? = null,
    val playing: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val liked: Boolean = false,
    val art: Bitmap? = null,
    /** Full-colour cover (used by the Disc widget). [art] is the black & white one for the CD Player. */
    val artColor: Bitmap? = null,
) {
    val fraction: Float
        get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
}

/**
 * Keeps the last state so the widget still shows the last track after the process
 * (and the player service) was killed, e.g. after a reboot.
 */
object CdWidgetStore {
    private const val PREFS = "cd_widget"
    private const val ART_FILE = "cd_widget_art.png"
    private const val ART_COLOR_FILE = "disc_widget_art.png"

    @Volatile
    private var memory: CdWidgetState? = null

    fun load(context: Context): CdWidgetState {
        memory?.let { return it }
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val art = runCatching {
            File(context.cacheDir, ART_FILE).takeIf { it.exists() }
                ?.let { BitmapFactory.decodeFile(it.absolutePath) }
        }.getOrNull()
        val artColor = runCatching {
            File(context.cacheDir, ART_COLOR_FILE).takeIf { it.exists() }
                ?.let { BitmapFactory.decodeFile(it.absolutePath) }
        }.getOrNull()
        return CdWidgetState(
            title = prefs.getString("title", null),
            artist = prefs.getString("artist", null),
            playing = false,
            durationMs = prefs.getLong("duration", 0L),
            art = art,
            artColor = artColor,
        )
    }

    fun save(context: Context, state: CdWidgetState) {
        val old = memory
        memory = state
        if (old?.title != state.title || old?.artist != state.artist) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString("title", state.title)
                .putString("artist", state.artist)
                .putLong("duration", state.durationMs)
                .apply()
        }
    }

    fun saveArt(context: Context, art: Bitmap?) {
        runCatching {
            val file = File(context.cacheDir, ART_FILE)
            if (art == null) file.delete()
            else file.outputStream().use { art.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    fun saveArtColor(context: Context, art: Bitmap?) {
        runCatching {
            val file = File(context.cacheDir, ART_COLOR_FILE)
            if (art == null) file.delete()
            else file.outputStream().use { art.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        }
    }
}
