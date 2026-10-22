package com.caohaidang.edward.music.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import android.widget.RemoteViews
import androidx.core.content.res.ResourcesCompat
import com.caohaidang.edward.music.MainActivity
import com.caohaidang.edward.music.R
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * "Disc" home-screen widget (4x2): a rounded card with a full, spinning CD (the cover art as the disc)
 * on the left and wave icon / artist / title / progress / time on the right.
 *
 * The whole widget face is drawn into ONE bitmap so it looks identical on every launcher
 * (custom fonts, clipping and shadows are not reliable inside RemoteViews).
 * Two invisible click areas on top of it: the disc (left) toggles play/pause, the text area (right) opens the app.
 */
object DiscWidgetRenderer {

    private const val DEFAULT_W_DP = 250
    private const val DEFAULT_H_DP = 110

    /** Font file in res/font (without extension). Falls back to the system monospace if missing. */
    private const val FONT_NAME = "jetbrains_mono"

    /** Resting angle of the cover (the reference shows it turned on its side). */
    private const val DISC_ROTATION_DEG = 90f

    /** Spin speed while playing, in degrees per second of playback (30 = one turn every 12 s). */
    private const val SPIN_DEG_PER_SEC = 30f

    /** Keeps the bitmap well below the RemoteViews memory limit on very big widgets. */
    private const val MAX_PIXELS = 1_300_000

    private class Palette(
        val bgTop: Int,
        val bgBottom: Int,
        val stroke: Int,
        val artist: Int,
        val title: Int,
        val dim: Int,
        val track: Int,
        val wave: Int,
    )

    private val LIGHT = Palette(
        bgTop = 0xFFEBEAE8.toInt(), bgBottom = 0xFFDEDDDB.toInt(), stroke = 0xFFCFCECC.toInt(),
        artist = 0xFF9C9C9C.toInt(), title = 0xFF1C1C1C.toInt(), dim = 0xFFA6A6A6.toInt(),
        track = 0xFFCDCDCB.toInt(), wave = 0xFF9C9C9C.toInt(),
    )
    private val DARK = Palette(
        bgTop = 0xFF2C2C2E.toInt(), bgBottom = 0xFF202022.toInt(), stroke = 0xFF3A3A3C.toInt(),
        artist = 0xFF8D8D92.toInt(), title = 0xFFF2F2F2.toInt(), dim = 0xFF8D8D92.toInt(),
        track = 0xFF4A4A4D.toInt(), wave = 0xFF8D8D92.toInt(),
    )

    private class SizeDp(val w: Int, val h: Int)

    private fun component(context: Context) = ComponentName(context, DiscWidgetProvider::class.java)

    fun hasWidgets(context: Context): Boolean =
        AppWidgetManager.getInstance(context).getAppWidgetIds(component(context)).isNotEmpty()

    /** Full redraw of all placed Disc widgets. */
    fun updateAll(context: Context, state: CdWidgetState) {
        val manager = AppWidgetManager.getInstance(context)
        updateWidgets(context, manager, manager.getAppWidgetIds(component(context)), state)
    }

    fun updateWidgets(context: Context, manager: AppWidgetManager, ids: IntArray, state: CdWidgetState) {
        for (id in ids) {
            runCatching { manager.updateAppWidget(id, buildViews(context, state, sizeDp(context, manager, id))) }
        }
    }

    /** Cheap update (only the image) used by the progress ticker. */
    fun updateProgress(context: Context, state: CdWidgetState) {
        val manager = AppWidgetManager.getInstance(context)
        for (id in manager.getAppWidgetIds(component(context))) {
            val views = RemoteViews(context.packageName, R.layout.widget_disc)
            views.setImageViewBitmap(R.id.dw_image, render(context, state, sizeDp(context, manager, id)))
            runCatching { manager.partiallyUpdateAppWidget(id, views) }
        }
    }

    private fun buildViews(context: Context, state: CdWidgetState, size: SizeDp): RemoteViews {
        val v = RemoteViews(context.packageName, R.layout.widget_disc)
        val artist = state.artist?.takeIf { it.isNotBlank() } ?: context.getString(R.string.dw_idle_artist)
        val title = state.title?.takeIf { it.isNotBlank() } ?: context.getString(R.string.dw_idle_track)

        v.setImageViewBitmap(R.id.dw_image, render(context, state, size))
        v.setContentDescription(R.id.dw_image, "$artist - $title")

        // Disc = play / pause (the base provider opens the app when nothing is loaded), text = open app
        v.setOnClickPendingIntent(R.id.dw_hit_disc, broadcast(context, CdPlayerWidgetProvider.ACTION_PLAY_PAUSE))
        v.setOnClickPendingIntent(R.id.dw_hit_info, openApp(context))
        return v
    }

    private fun broadcast(context: Context, action: String): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            action.hashCode() + 1,
            Intent(context, DiscWidgetProvider::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun openApp(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            1,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun sizeDp(context: Context, manager: AppWidgetManager, id: Int): SizeDp {
        val o = manager.getAppWidgetOptions(id)
        val landscape = context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val w = o.getInt(
            if (landscape) AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH else AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,
            DEFAULT_W_DP,
        )
        val h = o.getInt(
            if (landscape) AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT else AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT,
            DEFAULT_H_DP,
        )
        return SizeDp(if (w > 0) w else DEFAULT_W_DP, if (h > 0) h else DEFAULT_H_DP)
    }

    // ---------------------------------------------------------------------------------------
    //  Drawing
    // ---------------------------------------------------------------------------------------
    @Volatile
    private var cachedFont: Typeface? = null

    private fun font(context: Context): Typeface = cachedFont ?: run {
        val id = context.resources.getIdentifier(FONT_NAME, "font", context.packageName)
        (if (id != 0) runCatching { ResourcesCompat.getFont(context, id) }.getOrNull() else null)
            ?: Typeface.MONOSPACE
    }.also { cachedFont = it }

    private fun render(context: Context, state: CdWidgetState, size: SizeDp): Bitmap {
        val density = context.resources.displayMetrics.density
        var scale = density
        if (size.w * scale * size.h * scale > MAX_PIXELS) {
            scale *= sqrt(MAX_PIXELS / (size.w * scale * size.h * scale))
        }
        val w = max(1, (size.w * scale).toInt())
        val h = max(1, (size.h * scale).toInt())
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)

        val dark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        val pal = if (dark) DARK else LIGHT
        val u = h.toFloat()

        val corner = u * 0.24f
        val body = RectF(0f, 0f, w.toFloat(), h.toFloat())
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // card
        paint.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), pal.bgTop, pal.bgBottom, Shader.TileMode.CLAMP)
        c.drawRoundRect(body, corner, corner, paint)
        paint.shader = null

        // hairline border
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = max(1f, density * 0.75f)
        paint.color = pal.stroke
        val inset = paint.strokeWidth / 2f
        c.drawRoundRect(RectF(inset, inset, w - inset, h - inset), corner, corner, paint)

        // full disc on the left
        val margin = u * 0.15f
        val radius = u * 0.35f
        val cx = margin + radius
        val cy = h / 2f
        val angle = (DISC_ROTATION_DEG + state.positionMs / 1000f * SPIN_DEG_PER_SEC) % 360f
        drawDisc(c, state.artColor, angle, cx, cy, radius, u, pal)

        // text block on the right
        val x0 = cx + radius + u * 0.13f
        val x1 = w - margin
        drawInfo(context, c, state, x0, x1 - x0, h, u, pal)
        return bmp
    }

    private fun drawDisc(c: Canvas, art: Bitmap?, angle: Float, cx: Float, cy: Float, r: Float, u: Float, pal: Palette) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // soft shadow under the disc
        paint.color = pal.bgBottom
        paint.setShadowLayer(u * 0.05f, 0f, u * 0.02f, 0x44000000)
        c.drawCircle(cx, cy, r, paint)
        paint.clearShadowLayer()

        val circle = Path().apply { addCircle(cx, cy, r, Path.Direction.CW) }
        c.save()
        c.clipPath(circle)
        if (art != null) {
            c.translate(cx, cy)
            c.rotate(angle)
            c.drawBitmap(art, null, RectF(-r, -r, r, r), Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        } else {
            // placeholder: dark vinyl with fine grooves
            paint.color = 0xFF3A3A3C.toInt()
            c.drawCircle(cx, cy, r, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = max(1f, u * 0.006f)
            paint.color = 0x22FFFFFF
            var rr = r * 0.34f
            while (rr < r) {
                c.drawCircle(cx, cy, rr, paint)
                rr += u * 0.05f
            }
            paint.style = Paint.Style.FILL
        }
        c.restore()

        // disc edge
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = max(1f, u * 0.006f)
        paint.color = 0x22000000
        c.drawCircle(cx, cy, r, paint)
        paint.style = Paint.Style.FILL

        // centre hub: iridescent rim, lavender-grey rings, hole
        val hub = r * 0.28f
        paint.shader = SweepGradient(
            cx, cy,
            intArrayOf(0xFFE9C66B.toInt(), 0xFF8EA2DA.toInt(), 0xFFEFEFF6.toInt(), 0xFFD8B36A.toInt(), 0xFF7E91C9.toInt(), 0xFFE9C66B.toInt()),
            null,
        )
        c.drawCircle(cx, cy, hub, paint)
        paint.shader = null
        paint.color = 0xFF9FA0B5.toInt()
        c.drawCircle(cx, cy, hub * 0.88f, paint)
        paint.color = 0xFFD9D8E3.toInt()
        c.drawCircle(cx, cy, hub * 0.68f, paint)
        paint.color = 0xFFB3B3C6.toInt()
        c.drawCircle(cx, cy, hub * 0.46f, paint)
        paint.color = pal.bgTop
        c.drawCircle(cx, cy, hub * 0.40f, paint)
    }

    private fun drawInfo(
        context: Context,
        c: Canvas,
        state: CdWidgetState,
        x0: Float,
        infoW: Float,
        h: Int,
        u: Float,
        pal: Palette,
    ) {
        if (infoW <= 0f) return
        val textSize = u * 0.085f
        val regular = font(context)
        val bold = Typeface.create(regular, Typeface.BOLD)

        // sound-wave glyph (left aligned)
        val playing = state.playing
        val phase = state.positionMs / 1000f
        val base = floatArrayOf(0.35f, 0.6f, 1f, 0.75f, 0.9f, 0.55f, 0.3f)
        val barW = u * 0.012f
        val gap = u * 0.009f
        val maxH = u * 0.10f
        val wavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = pal.wave
            strokeCap = Paint.Cap.ROUND
            strokeWidth = barW
        }
        val waveY = h * 0.25f
        for (i in base.indices) {
            val k = if (playing) 0.55f + 0.45f * abs(sin(phase * 2.2f + i * 1.3f)) else 1f
            val bh = max(barW, maxH * base[i] * k)
            val x = x0 + barW / 2f + i * (barW + gap)
            c.drawLine(x, waveY - bh / 2f, x, waveY + bh / 2f, wavePaint)
        }

        // artist + title
        val artist = state.artist?.takeIf { it.isNotBlank() } ?: context.getString(R.string.dw_idle_artist)
        val title = state.title?.takeIf { it.isNotBlank() } ?: context.getString(R.string.dw_idle_track)
        drawLeft(c, artist, regular, pal.artist, textSize, infoW, x0, h * 0.415f)
        drawLeft(c, title, bold, pal.title, textSize, infoW, x0, h * 0.54f)

        // progress (full width of the text block)
        val barH = max(2f, u * 0.02f)
        val barY = h * 0.655f
        val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        val track = RectF(x0, barY - barH / 2f, x0 + infoW, barY + barH / 2f)
        barPaint.color = pal.track
        c.drawRoundRect(track, barH / 2f, barH / 2f, barPaint)
        val fill = RectF(track.left, track.top, track.left + max(barH, infoW * state.fraction), track.bottom)
        barPaint.color = pal.title
        c.drawRoundRect(fill, barH / 2f, barH / 2f, barPaint)

        // time "2:43 / 4:18"
        val cur = formatTime(state.positionMs)
        val rest = " / " + formatTime(state.durationMs)
        val tpCur = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = bold; this.textSize = textSize; color = pal.title }
        val tpRest = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = regular; this.textSize = textSize; color = pal.dim }
        val timeW = tpCur.measureText(cur) + tpRest.measureText(rest)
        if (timeW > infoW) {
            val k = infoW / timeW
            tpCur.textSize = textSize * k
            tpRest.textSize = textSize * k
        }
        val y = baseline(tpCur, h * 0.77f)
        c.drawText(cur, x0, y, tpCur)
        c.drawText(rest, x0 + tpCur.measureText(cur), y, tpRest)
    }

    private fun drawLeft(
        c: Canvas,
        text: String,
        face: Typeface,
        color: Int,
        size: Float,
        maxW: Float,
        x: Float,
        centerY: Float,
    ) {
        val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = face; textSize = size; this.color = color }
        // shrink a little for long names, then ellipsize
        val needed = tp.measureText(text)
        if (needed > maxW) tp.textSize = max(size * 0.78f, size * maxW / needed)
        val shown = TextUtils.ellipsize(text, tp, maxW, TextUtils.TruncateAt.END).toString()
        c.drawText(shown, x, baseline(tp, centerY), tp)
    }

    private fun baseline(tp: TextPaint, centerY: Float) = centerY - (tp.ascent() + tp.descent()) / 2f

    private fun formatTime(ms: Long): String {
        val s = (ms / 1000).coerceAtLeast(0)
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
    }
}
