package com.caohaidang.edward.music.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import android.widget.RemoteViews
import androidx.core.content.res.ResourcesCompat
import com.caohaidang.edward.music.MainActivity
import com.caohaidang.edward.music.R
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * "iPod" home-screen widget (4x2): a silver player with a black-bezel screen on the left
 * (cover art, title, artist, elapsed time / progress / duration, tinted with the cover's colour)
 * and a white click wheel on the right (MUSIC label, previous, next, play / pause, centre button).
 *
 * Like the Disc widget, the whole face is drawn into ONE bitmap so it looks the same on every
 * launcher. A transparent 3x3 grid of click areas sits on top of the wheel (see widget_ipod.xml):
 * the grid is padded at run time so it lines up with the wheel for the widget's current size.
 *  - left / right cell  -> previous / next track
 *  - bottom cell        -> play / pause
 *  - everything else    -> open the app
 */
object IpodWidgetRenderer {

    private const val DEFAULT_W_DP = 250
    private const val DEFAULT_H_DP = 110

    /** Keeps the bitmap well below the RemoteViews memory limit on very big widgets. */
    private const val MAX_PIXELS = 1_300_000

    // ---- layout, all as fractions so the design scales with the widget (see [Geo]) ----------
    private const val MARGIN_H = 0.10f          // outer margin around the screen, x height
    private const val SCREEN_RIGHT_W = 0.542f   // right edge of the screen, x width
    private const val WHEEL_H = 0.70f           // wheel diameter, x height
    private const val WHEEL_FILL_PANEL = 0.80f  // ... but at most this much of the space right of the screen
    private const val CENTER_BTN = 0.37f        // centre button diameter, x wheel diameter
    private const val ICON_RING = 0.39f         // distance of prev / next / play / label from the centre, x wheel diameter

    // ---- colours ---------------------------------------------------------------------------
    private const val BODY_TOP = 0xFFE6E6E7.toInt()
    private const val BODY_BOTTOM = 0xFFC8C8CB.toInt()
    private const val BODY_EDGE = 0xFFB4B4B8.toInt()
    private const val BEZEL = 0xFF050505.toInt()
    private const val WHEEL_TOP = 0xFFFFFFFF.toInt()
    private const val WHEEL_BOTTOM = 0xFFF1F1F3.toInt()
    private const val WHEEL_EDGE = 0xFFD6D6DA.toInt()
    private const val BUTTON_TOP = 0xFFEDEDEF.toInt()
    private const val BUTTON_BOTTOM = 0xFFDCDCE0.toInt()
    private const val GLYPH = 0xFF98989E.toInt()

    // Screen background used when there is no cover to take the colour from
    private val NEUTRAL_TOP = 0xFF9AA6B5.toInt()
    private val NEUTRAL_BOTTOM = 0xFF74808F.toInt()

    private class SizeDp(val w: Int, val h: Int)

    /** Positions of everything, in dp, for one widget size. Shared by drawing and click areas. */
    private class Geo(val w: Float, val h: Float) {
        val margin = h * MARGIN_H
        val bodyCorner = h * 0.17f

        // screen (bezel rectangle)
        val screen = RectF(margin, margin, w * SCREEN_RIGHT_W, h - margin)

        // click wheel
        val wheelD = min(h * WHEEL_H, (w - screen.right) * WHEEL_FILL_PANEL)
        val wheelCx = (screen.right + w) / 2f
        val wheelCy = h / 2f
        val wheelR = wheelD / 2f
        val ring = wheelD * ICON_RING
    }

    private fun component(context: Context) = ComponentName(context, IpodWidgetProvider::class.java)

    fun hasWidgets(context: Context): Boolean =
        AppWidgetManager.getInstance(context).getAppWidgetIds(component(context)).isNotEmpty()

    /** Full redraw of all placed iPod widgets. */
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
            val views = RemoteViews(context.packageName, R.layout.widget_ipod)
            views.setImageViewBitmap(R.id.ip_image, render(context, state, sizeDp(context, manager, id)))
            runCatching { manager.partiallyUpdateAppWidget(id, views) }
        }
    }

    private fun buildViews(context: Context, state: CdWidgetState, size: SizeDp): RemoteViews {
        val v = RemoteViews(context.packageName, R.layout.widget_ipod)
        val title = state.title?.takeIf { it.isNotBlank() } ?: context.getString(R.string.ip_idle_title)
        val artist = state.artist?.takeIf { it.isNotBlank() } ?: context.getString(R.string.ip_idle_artist)

        v.setImageViewBitmap(R.id.ip_image, render(context, state, size))
        v.setContentDescription(R.id.ip_image, "$title - $artist")

        // Line the click grid up with the wheel (padding is in px, the geometry in dp).
        val d = context.resources.displayMetrics.density
        val g = Geo(size.w.toFloat(), size.h.toFloat())
        val left = ((g.wheelCx - g.wheelR) * d).roundToInt()
        val top = ((g.wheelCy - g.wheelR) * d).roundToInt()
        val right = ((g.w - g.wheelCx - g.wheelR) * d).roundToInt()
        val bottom = ((g.h - g.wheelCy - g.wheelR) * d).roundToInt()
        v.setViewPadding(R.id.ip_grid, max(0, left), max(0, top), max(0, right), max(0, bottom))

        v.setOnClickPendingIntent(R.id.ip_hit_app, openApp(context))
        v.setOnClickPendingIntent(R.id.ip_hit_menu, openApp(context))
        v.setOnClickPendingIntent(R.id.ip_hit_center, openApp(context))
        v.setOnClickPendingIntent(R.id.ip_hit_prev, broadcast(context, CdPlayerWidgetProvider.ACTION_PREV))
        v.setOnClickPendingIntent(R.id.ip_hit_next, broadcast(context, CdPlayerWidgetProvider.ACTION_NEXT))
        v.setOnClickPendingIntent(R.id.ip_hit_play, broadcast(context, CdPlayerWidgetProvider.ACTION_PLAY_PAUSE))
        return v
    }

    private fun broadcast(context: Context, action: String): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            action.hashCode() + 2,
            Intent(context, IpodWidgetProvider::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun openApp(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            2,
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
    //  Fonts
    // ---------------------------------------------------------------------------------------
    @Volatile
    private var cachedBold: Typeface? = null

    @Volatile
    private var cachedMedium: Typeface? = null

    private fun bold(context: Context): Typeface = cachedBold ?: run {
        runCatching { ResourcesCompat.getFont(context, R.font.be_vietnam_pro_bold) }.getOrNull()
            ?: Typeface.DEFAULT_BOLD
    }.also { cachedBold = it }

    private fun medium(context: Context): Typeface = cachedMedium ?: run {
        runCatching { ResourcesCompat.getFont(context, R.font.be_vietnam_pro_medium) }.getOrNull()
            ?: Typeface.DEFAULT
    }.also { cachedMedium = it }

    // ---------------------------------------------------------------------------------------
    //  Screen colour taken from the cover
    // ---------------------------------------------------------------------------------------
    private class Tint(val art: Bitmap, val top: Int, val bottom: Int)

    @Volatile
    private var tintCache: Tint? = null

    /** Gradient (top, bottom) for the screen: the cover's average hue, kept mid-tone so white text reads well. */
    private fun tintFor(art: Bitmap?): Pair<Int, Int> {
        if (art == null) return NEUTRAL_TOP to NEUTRAL_BOTTOM
        tintCache?.takeIf { it.art === art }?.let { return it.top to it.bottom }

        val small = runCatching { Bitmap.createScaledBitmap(art, 12, 12, true) }.getOrNull()
            ?: return NEUTRAL_TOP to NEUTRAL_BOTTOM
        var r = 0L; var g = 0L; var b = 0L
        val px = IntArray(small.width * small.height)
        small.getPixels(px, 0, small.width, 0, 0, small.width, small.height)
        for (p in px) { r += Color.red(p); g += Color.green(p); b += Color.blue(p) }
        val n = max(1, px.size)
        val hsv = FloatArray(3)
        Color.RGBToHSV((r / n).toInt(), (g / n).toInt(), (b / n).toInt(), hsv)
        if (small !== art) small.recycle()

        val hue = hsv[0]
        // Greys stay grey; colourful covers get a lively but not garish screen.
        val sat = if (hsv[1] < 0.08f) hsv[1] else hsv[1].coerceIn(0.28f, 0.62f)
        val value = hsv[2].coerceIn(0.58f, 0.80f)
        val top = Color.HSVToColor(floatArrayOf(hue, sat * 0.80f, min(1f, value + 0.12f)))
        val bottom = Color.HSVToColor(floatArrayOf(hue, sat, max(0f, value - 0.10f)))
        tintCache = Tint(art, top, bottom)
        return top to bottom
    }

    // ---------------------------------------------------------------------------------------
    //  Drawing (done in dp, the canvas is scaled to pixels)
    // ---------------------------------------------------------------------------------------
    private fun render(context: Context, state: CdWidgetState, size: SizeDp): Bitmap {
        val density = context.resources.displayMetrics.density
        var scale = density
        if (size.w * scale * size.h * scale > MAX_PIXELS) {
            scale *= sqrt(MAX_PIXELS / (size.w * scale * size.h * scale))
        }
        val bmp = Bitmap.createBitmap(
            max(1, (size.w * scale).roundToInt()),
            max(1, (size.h * scale).roundToInt()),
            Bitmap.Config.ARGB_8888,
        )
        val c = Canvas(bmp)
        c.scale(bmp.width / size.w.toFloat(), bmp.height / size.h.toFloat())
        val g = Geo(size.w.toFloat(), size.h.toFloat())

        drawBody(c, g)
        drawScreen(context, c, g, state)
        drawWheel(context, c, g)
        return bmp
    }

    private fun drawBody(c: Canvas, g: Geo) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val body = RectF(0f, 0f, g.w, g.h)
        paint.shader = LinearGradient(0f, 0f, 0f, g.h, BODY_TOP, BODY_BOTTOM, Shader.TileMode.CLAMP)
        c.drawRoundRect(body, g.bodyCorner, g.bodyCorner, paint)
        paint.shader = null

        // hairline edge + a faint light line along the top for the brushed-metal look
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 0.6f
        paint.color = BODY_EDGE
        c.drawRoundRect(RectF(0.3f, 0.3f, g.w - 0.3f, g.h - 0.3f), g.bodyCorner, g.bodyCorner, paint)
        paint.color = 0x99FFFFFF.toInt()
        c.drawRoundRect(RectF(1f, 1f, g.w - 1f, g.h - 1f), g.bodyCorner - 0.7f, g.bodyCorner - 0.7f, paint)
    }

    private fun drawScreen(context: Context, c: Canvas, g: Geo, state: CdWidgetState) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // black bezel
        val bezelR = g.h * 0.045f
        paint.color = BEZEL
        c.drawRoundRect(g.screen, bezelR, bezelR, paint)

        val inset = max(1.6f, g.h * 0.02f)
        val disp = RectF(g.screen.left + inset, g.screen.top + inset, g.screen.right - inset, g.screen.bottom - inset)
        val dispR = g.h * 0.032f
        val dw = disp.width()
        val dh = disp.height()

        // tinted background + a soft gloss on the upper half
        val (top, bottom) = tintFor(state.artColor)
        c.save()
        c.clipPath(Path().apply { addRoundRect(disp, dispR, dispR, Path.Direction.CW) })
        paint.shader = LinearGradient(disp.left, disp.top, disp.right * 0.9f, disp.bottom, top, bottom, Shader.TileMode.CLAMP)
        c.drawRect(disp, paint)
        paint.shader = LinearGradient(0f, disp.top, 0f, disp.centerY(), 0x22FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP)
        c.drawRect(disp, paint)
        paint.shader = null

        // cover
        val pad = dh * 0.052f
        val coverSize = dh * 0.70f
        val cover = RectF(disp.left + pad, disp.top + dh * 0.087f, disp.left + pad + coverSize, disp.top + dh * 0.087f + coverSize)
        drawCover(c, cover, state.artColor, dh)

        // text block to the right of the cover
        val tx = cover.right + pad
        val tw = disp.right - pad - tx
        if (tw > 8f) drawTexts(context, c, state, tx, tw, cover, dh)

        // elapsed time / progress bar / duration
        drawProgress(context, c, state, disp, pad, dh)
        c.restore()
    }

    private fun drawCover(c: Canvas, r: RectF, art: Bitmap?, dh: Float) {
        val radius = dh * 0.04f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        if (art != null) {
            c.save()
            c.clipPath(Path().apply { addRoundRect(r, radius, radius, Path.Direction.CW) })
            c.drawBitmap(art, null, r, paint)
            c.restore()
        } else {
            // placeholder: translucent tile with a music note
            paint.color = 0x33FFFFFF
            c.drawRoundRect(r, radius, radius, paint)
            paint.color = 0xCCFFFFFF.toInt()
            val s = r.width()
            val headR = s * 0.11f
            val hx = r.left + s * 0.40f
            val hy = r.top + s * 0.68f
            c.drawOval(RectF(hx - headR * 1.25f, hy - headR, hx + headR * 1.25f, hy + headR), paint)
            val stemW = max(1f, s * 0.045f)
            val stemX = hx + headR * 1.15f
            c.drawRect(stemX - stemW, r.top + s * 0.26f, stemX, hy, paint)
            val flag = Path().apply {
                moveTo(stemX - stemW, r.top + s * 0.26f)
                cubicTo(stemX + s * 0.14f, r.top + s * 0.30f, stemX + s * 0.18f, r.top + s * 0.40f, stemX + s * 0.10f, r.top + s * 0.50f)
                cubicTo(stemX + s * 0.14f, r.top + s * 0.40f, stemX + s * 0.06f, r.top + s * 0.36f, stemX - stemW, r.top + s * 0.36f)
                close()
            }
            c.drawPath(flag, paint)
        }
    }

    private fun drawTexts(
        context: Context,
        c: Canvas,
        state: CdWidgetState,
        x: Float,
        maxW: Float,
        cover: RectF,
        dh: Float,
    ) {
        val title = state.title?.takeIf { it.isNotBlank() } ?: context.getString(R.string.ip_idle_title)
        val artist = (state.artist?.takeIf { it.isNotBlank() } ?: context.getString(R.string.ip_idle_artist))
            .uppercase()

        // title: one line, shrinks a little for long names, then ellipsizes
        val titleSize = dh * 0.118f
        val tpTitle = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = bold(context); textSize = titleSize; color = Color.WHITE
        }
        val needed = tpTitle.measureText(title)
        if (needed > maxW) tpTitle.textSize = max(titleSize * 0.78f, titleSize * maxW / needed)
        val shownTitle = TextUtils.ellipsize(title, tpTitle, maxW, TextUtils.TruncateAt.END).toString()
        c.drawText(shownTitle, x, baseline(tpTitle, cover.top + cover.height() * 0.33f), tpTitle)

        // artist: up to two lines, upper-case, a little transparent
        val tpArtist = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = medium(context); textSize = dh * 0.086f; color = Color.WHITE; alpha = 215
            letterSpacing = 0.02f
        }
        val lines = wrapLines(artist, tpArtist, maxW, 2)
        lines.forEachIndexed { i, line ->
            c.drawText(line, x, baseline(tpArtist, cover.top + cover.height() * (0.54f + 0.16f * i)), tpArtist)
        }
    }

    private fun drawProgress(context: Context, c: Canvas, state: CdWidgetState, disp: RectF, pad: Float, dh: Float) {
        val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = medium(context); textSize = dh * 0.082f; color = Color.WHITE; alpha = 235
        }
        val cy = disp.bottom - dh * 0.105f
        // both labels get the same fixed width so the bar does not jitter as digits change
        val labelW = tp.measureText("00:00")
        val left = formatTime(state.positionMs)
        val right = formatTime(state.durationMs)
        c.drawText(left, disp.left + pad, baseline(tp, cy), tp)
        c.drawText(right, disp.right - pad - tp.measureText(right), baseline(tp, cy), tp)

        val gap = pad * 0.6f
        val barL = disp.left + pad + labelW + gap
        val barR = disp.right - pad - labelW - gap
        if (barR - barL < 6f) return
        val barH = max(1.5f, dh * 0.05f)
        val track = RectF(barL, cy - barH / 2f, barR, cy + barH / 2f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = 0x59FFFFFF
        c.drawRoundRect(track, barH / 2f, barH / 2f, paint)
        val fillW = max(barH, (barR - barL) * state.fraction)
        paint.color = 0xE6FFFFFF.toInt()
        c.drawRoundRect(RectF(barL, track.top, barL + fillW, track.bottom), barH / 2f, barH / 2f, paint)
    }

    private fun drawWheel(context: Context, c: Canvas, g: Geo) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val cx = g.wheelCx
        val cy = g.wheelCy
        val r = g.wheelR

        // wheel with a soft drop shadow
        paint.color = WHEEL_BOTTOM
        paint.setShadowLayer(g.h * 0.03f, 0f, g.h * 0.012f, 0x40000000)
        c.drawCircle(cx, cy, r, paint)
        paint.clearShadowLayer()
        paint.shader = LinearGradient(0f, cy - r, 0f, cy + r, WHEEL_TOP, WHEEL_BOTTOM, Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, r, paint)
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 0.6f
        paint.color = WHEEL_EDGE
        c.drawCircle(cx, cy, r - 0.3f, paint)
        paint.style = Paint.Style.FILL

        // centre button
        val br = g.wheelD * CENTER_BTN / 2f
        paint.color = BUTTON_BOTTOM
        paint.setShadowLayer(g.h * 0.02f, 0f, g.h * 0.008f, 0x33000000)
        c.drawCircle(cx, cy, br, paint)
        paint.clearShadowLayer()
        paint.shader = RadialGradient(cx - br * 0.25f, cy - br * 0.35f, br * 1.4f, BUTTON_TOP, BUTTON_BOTTOM, Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, br, paint)
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 0.5f
        paint.color = 0xFFCBCBD0.toInt()
        c.drawCircle(cx, cy, br - 0.25f, paint)
        paint.style = Paint.Style.FILL

        // MUSIC label
        val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = medium(context); textSize = g.wheelD * 0.085f; color = GLYPH
            letterSpacing = 0.14f
        }
        val label = context.getString(R.string.ip_wheel_label)
        c.drawText(label, cx - tp.measureText(label) / 2f, baseline(tp, cy - g.ring), tp)

        // transport glyphs
        val gh = g.wheelD * 0.105f          // glyph height
        val bar = max(0.8f, g.wheelD * 0.016f)
        paint.color = GLYPH
        // previous: bar + left-pointing triangle
        run {
            val x = cx - g.ring
            val w = g.wheelD * 0.085f
            val l = x - w / 2f
            c.drawRect(l, cy - gh / 2f, l + bar, cy + gh / 2f, paint)
            c.drawPath(triangle(l + bar, cy, l + w, cy - gh / 2f, l + w, cy + gh / 2f), paint)
        }
        // next: right-pointing triangle + bar
        run {
            val x = cx + g.ring
            val w = g.wheelD * 0.085f
            val rgt = x + w / 2f
            c.drawRect(rgt - bar, cy - gh / 2f, rgt, cy + gh / 2f, paint)
            c.drawPath(triangle(rgt - bar, cy, rgt - w, cy - gh / 2f, rgt - w, cy + gh / 2f), paint)
        }
        // play / pause: triangle followed by two bars
        run {
            val y = cy + g.ring
            val tri = g.wheelD * 0.065f
            val gap = g.wheelD * 0.018f
            val total = tri + gap + bar * 2f + bar
            val l = cx - total / 2f
            c.drawPath(triangle(l + tri, y, l, y - gh / 2f, l, y + gh / 2f), paint)
            val b1 = l + tri + gap
            c.drawRect(b1, y - gh / 2f, b1 + bar, y + gh / 2f, paint)
            c.drawRect(b1 + bar * 2f, y - gh / 2f, b1 + bar * 3f, y + gh / 2f, paint)
        }
    }

    /** Triangle with its tip at (tx, ty) and base corners at (ax, ay) / (bx, by). */
    private fun triangle(tx: Float, ty: Float, ax: Float, ay: Float, bx: Float, by: Float) = Path().apply {
        moveTo(tx, ty); lineTo(ax, ay); lineTo(bx, by); close()
    }

    /** Splits [text] into at most [maxLines] lines that fit [maxW]; the last line is ellipsized. */
    private fun wrapLines(text: String, tp: TextPaint, maxW: Float, maxLines: Int): List<String> {
        val lines = ArrayList<String>()
        var rest = text.trim()
        while (rest.isNotEmpty() && lines.size < maxLines) {
            val lastLine = lines.size == maxLines - 1
            if (lastLine || tp.measureText(rest) <= maxW) {
                lines += TextUtils.ellipsize(rest, tp, maxW, TextUtils.TruncateAt.END).toString()
                break
            }
            // break at the last space that still fits
            var cut = -1
            var idx = rest.indexOf(' ')
            while (idx != -1 && tp.measureText(rest, 0, idx) <= maxW) {
                cut = idx
                idx = rest.indexOf(' ', idx + 1)
            }
            if (cut <= 0) { // a single word wider than the line
                lines += TextUtils.ellipsize(rest, tp, maxW, TextUtils.TruncateAt.END).toString()
                break
            }
            lines += rest.substring(0, cut)
            rest = rest.substring(cut + 1).trim()
        }
        return lines
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
