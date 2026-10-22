package com.caohaidang.edward.music.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.os.PowerManager
import androidx.media3.common.C
import androidx.media3.common.Player
import coil.imageLoader
import coil.request.ImageRequest
import com.caohaidang.edward.music.extensions.currentMetadata
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Lives inside MusicService. Listens to the player and keeps the CD Player, Disc and iPod widgets in sync.
 *
 *  - full redraw on track / play-state / like changes
 *  - light redraw of the slider every [TICK_MS] while playing and the screen is on
 */
class CdPlayerWidgetUpdater(
    context: Context,
    private val scope: CoroutineScope,
    private val player: Player,
    private val isLiked: () -> Boolean,
) : Player.Listener {

    private val context = context.applicationContext
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager

    private var refreshJob: Job? = null
    private var tickJob: Job? = null
    private var artUrl: String? = null
    private var art: Bitmap? = null
    private var artColor: Bitmap? = null

    fun start() {
        active = this
        player.addListener(this)
        refresh()
    }

    fun stop() {
        player.removeListener(this)
        tickJob?.cancel()
        refreshJob?.cancel()
        if (active === this) active = null
        // leave the widget in a "stopped" state
        val idle = CdWidgetStore.load(context).copy(playing = false)
        CdWidgetStore.save(context, idle)
        if (CdPlayerWidgetRenderer.hasWidgets(context)) CdPlayerWidgetRenderer.updateAll(context, idle)
        if (DiscWidgetRenderer.hasWidgets(context)) DiscWidgetRenderer.updateAll(context, idle)
        if (IpodWidgetRenderer.hasWidgets(context)) IpodWidgetRenderer.updateAll(context, idle)
    }

    override fun onEvents(player: Player, events: Player.Events) {
        if (events.containsAny(
                Player.EVENT_MEDIA_ITEM_TRANSITION,
                Player.EVENT_MEDIA_METADATA_CHANGED,
                Player.EVENT_PLAY_WHEN_READY_CHANGED,
                Player.EVENT_PLAYBACK_STATE_CHANGED,
                Player.EVENT_IS_PLAYING_CHANGED,
                Player.EVENT_TIMELINE_CHANGED,
                Player.EVENT_POSITION_DISCONTINUITY,
            )
        ) refresh()
        updateTicker()
    }

    /** Call whenever something the widget shows may have changed (also used for the "liked" flag). */
    fun refresh() {
        if (!hasAnyWidget()) {
            CdWidgetStore.save(context, snapshot(art, artColor))
            return
        }
        refreshJob?.cancel()
        refreshJob = scope.launch {
            val url = player.currentMetadata?.thumbnailUrl
            if (url == artUrl) {
                push(snapshot(art, artColor))
            } else {
                push(snapshot(null, null)) // show text + placeholder disc right away
                val color = loadArt(url)
                val gray = color?.let { toGray(it) }
                artUrl = url
                art = gray
                artColor = color
                CdWidgetStore.saveArt(context, gray)
                CdWidgetStore.saveArtColor(context, color)
                push(snapshot(gray, color))
            }
        }
        updateTicker()
    }

    private fun push(state: CdWidgetState) {
        CdWidgetStore.save(context, state)
        CdPlayerWidgetRenderer.updateAll(context, state)
        DiscWidgetRenderer.updateAll(context, state)
        IpodWidgetRenderer.updateAll(context, state)
    }

    private fun hasAnyWidget() = CdPlayerWidgetRenderer.hasWidgets(context) ||
        DiscWidgetRenderer.hasWidgets(context) || IpodWidgetRenderer.hasWidgets(context)

    private fun snapshot(art: Bitmap?, artColor: Bitmap?): CdWidgetState {
        val meta = player.currentMetadata
        val duration = player.duration.takeIf { it != C.TIME_UNSET && it > 0 }
            ?: ((meta?.duration ?: 0) * 1000L).coerceAtLeast(0L)
        return CdWidgetState(
            title = meta?.title,
            artist = meta?.artists?.joinToString { it.name },
            playing = player.playWhenReady && player.playbackState != Player.STATE_ENDED &&
                player.playbackState != Player.STATE_IDLE,
            positionMs = player.currentPosition.coerceAtLeast(0L),
            durationMs = duration,
            liked = isLiked(),
            art = art,
            artColor = artColor,
        )
    }

    private fun updateTicker() {
        val shouldTick = player.isPlaying && hasAnyWidget()
        if (shouldTick) {
            if (tickJob?.isActive != true) {
                tickJob = scope.launch {
                    while (isActive) {
                        // the Disc widget spins, so it needs a faster tick than the progress bar alone
                        delay(if (DiscWidgetRenderer.hasWidgets(context)) DISC_TICK_MS else TICK_MS)
                        if (powerManager.isInteractive) {
                            val state = snapshot(art, artColor)
                            CdPlayerWidgetRenderer.updateProgress(context, state)
                            DiscWidgetRenderer.updateProgress(context, state)
                            IpodWidgetRenderer.updateProgress(context, state)
                        }
                    }
                }
            }
        } else {
            tickJob?.cancel()
            tickJob = null
        }
    }

    /** Full-colour, centre-cropped square cover. */
    private suspend fun loadArt(url: String?): Bitmap? {
        if (url.isNullOrBlank()) return null
        return runCatching {
            val request = ImageRequest.Builder(context)
                .data(url)
                .size(ART_COLOR_SIZE)
                .allowHardware(false)
                .build()
            val source = (context.imageLoader.execute(request).drawable as? BitmapDrawable)?.bitmap
                ?: return null
            withContext(Dispatchers.Default) { cropSquare(source, ART_COLOR_SIZE, false) }
        }.getOrNull()
    }

    /** Small black & white copy for the CD Player widget (like the reference), or colour if disabled. */
    private fun toGray(color: Bitmap): Bitmap = cropSquare(color, ART_SIZE, GRAYSCALE_ART)

    private fun cropSquare(src: Bitmap, size: Int, gray: Boolean): Bitmap {
        val side = minOf(src.width, src.height)
        val x = (src.width - side) / 2
        val y = (src.height - side) / 2
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
            if (gray) colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
        }
        Canvas(out).drawBitmap(
            src,
            android.graphics.Rect(x, y, x + side, y + side),
            android.graphics.Rect(0, 0, size, size),
            paint,
        )
        return out
    }

    companion object {
        /** Set to false if you want full-colour cover art. */
        const val GRAYSCALE_ART = true
        private const val ART_SIZE = 200
        private const val ART_COLOR_SIZE = 480
        private const val TICK_MS = 2000L
        private const val DISC_TICK_MS = 1000L

        @Volatile
        var active: CdPlayerWidgetUpdater? = null
            private set
    }
}
