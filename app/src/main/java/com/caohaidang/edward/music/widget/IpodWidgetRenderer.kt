package com.caohaidang.edward.music.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.TextPaint
import android.widget.RemoteViews
import androidx.core.content.res.ResourcesCompat
import com.caohaidang.edward.music.MainActivity
import com.caohaidang.edward.music.R
import kotlin.math.max
import kotlin.math.min

/**
 * "iPod" home-screen widget (4x2): a silver player with a black-bezel screen on the left
 * (cover art, title + artist, progress, like / queue on the blurred cover) and a white click wheel
 * on the right (MUSIC label, previous, next, play / pause, centre button).
 *
 * Launchers only report a widget's size roughly, so nothing here depends on knowing it exactly:
 *  - the body and the bezel are shape drawables (see res/drawable/ip_body.xml, ip_bezel.xml),
 *  - the screen background is the cover, blurred (centre-cropped, never stretched),
 *  - title, artist and times are real TextViews and the bar is a real ProgressBar (never distorted),
 *  - the cover and the click wheel are SQUARE bitmaps shown with `fitStart` / `fitCenter`, so they always
 *    stay square / round, whatever the real aspect ratio of the widget turns out to be.
 *
 * A transparent 3x3 grid of click areas sits on top of the wheel (see widget_ipod.xml):
 *  - left / right cell  -> previous / next track
 *  - bottom cell        -> play / pause
 *  - everything else    -> open the app
 */
object IpodWidgetRenderer {

    /** Side of the square cover / wheel bitmaps, in px. Small enough for the RemoteViews memory limit. */
    private const val COVER_PX = 360
    private const val WHEEL_PX = 420
    private const val BG_PX = 192
    private const val COVER_SHADOW_INSET = 0.07f   // x bitmap size, left free around the cover for its shadow
    private const val PROGRESS_MAX = 1000

    // ---- wheel proportions (x wheel diameter) ----------------------------------------------
    private const val CENTER_BTN = 0.37f        // centre button diameter
    private const val ICON_RING = 0.39f         // distance of prev / next / play / label from the centre

    // ---- colours ---------------------------------------------------------------------------
    private const val WHEEL_TOP = 0xFFFFFFFF.toInt()
    private const val WHEEL_BOTTOM = 0xFFF1F1F3.toInt()
    private const val WHEEL_EDGE = 0xFFD6D6DA.toInt()
    private const val BUTTON_TOP = 0xFFEDEDEF.toInt()
    private const val BUTTON_BOTTOM = 0xFFDCDCE0.toInt()
    private const val GLYPH = 0xFF98989E.toInt()
    private const val LIKE_OFF = 0xCCFFFFFF.toInt()

    // Screen colours used when there is no cover to take them from
    private val NEUTRAL_TOP = 0xFF5B6878.toInt()
    private val NEUTRAL_BOTTOM = 0xFF28303B.toInt()
    private val NEUTRAL_ACCENT = 0xFFB4B8C2.toInt()

    private fun component(context: Context) = ComponentName(context, IpodWidgetProvider::class.java)

    fun hasWidgets(context: Context): Boolean =
        AppWidgetManager.getInstance(context).getAppWidgetIds(component(context)).isNotEmpty()

    /** Full redraw of all placed iPod widgets. */
    fun updateAll(context: Context, state: CdWidgetState) {
        val manager = AppWidgetManager.getInstance(context)
        updateWidgets(context, manager, manager.getAppWidgetIds(component(context)), state)
    }

    fun updateWidgets(context: Context, manager: AppWidgetManager, ids: IntArray, state: CdWidgetState) {
        if (ids.isEmpty()) return
        // The bitmaps do not depend on the widget's size, so build the views once for all of them.
        val views = buildViews(context, state)
        for (id in ids) {
            runCatching { manager.updateAppWidget(id, views) }
        }
    }

    /** Cheap update used by the progress ticker: only the two times and the bar are touched. */
    fun updateProgress(context: Context, state: CdWidgetState) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(component(context))
        if (ids.isEmpty()) return
        val v = RemoteViews(context.packageName, R.layout.widget_ipod)
        applyProgress(v, state)
        for (id in ids) {
            runCatching { manager.partiallyUpdateAppWidget(id, v) }
        }
    }

    private fun applyProgress(v: RemoteViews, state: CdWidgetState) {
        v.setTextViewText(R.id.ip_time_now, formatTime(state.positionMs))
        v.setTextViewText(R.id.ip_time_total, formatTime(state.durationMs))
        v.setProgressBar(R.id.ip_progress, PROGRESS_MAX, (state.fraction * PROGRESS_MAX).toInt(), false)
    }

    private fun formatTime(ms: Long): String {
        val total = (ms.coerceAtLeast(0L) / 1000L).toInt()
        return "%d:%02d".format(total / 60, total % 60)
    }

    private fun buildViews(context: Context, state: CdWidgetState): RemoteViews {
        val v = RemoteViews(context.packageName, R.layout.widget_ipod)
        val title = state.title?.takeIf { it.isNotBlank() } ?: context.getString(R.string.ip_idle_title)
        val artist = state.artist?.takeIf { it.isNotBlank() } ?: context.getString(R.string.ip_idle_artist)

        val look = lookFor(state.artColor)
        v.setImageViewBitmap(R.id.ip_screen_bg, look.background)
        v.setImageViewBitmap(R.id.ip_cover, renderCover(state.artColor))
        v.setImageViewBitmap(R.id.ip_wheel, renderWheel(context))
        v.setContentDescription(R.id.ip_cover, "$title - $artist")

        v.setTextViewText(R.id.ip_title, title)
        v.setTextViewText(R.id.ip_artist, artist)
        v.setTextColor(R.id.ip_artist, look.accent)
        applyProgress(v, state)

        v.setImageViewResource(R.id.ip_like, if (state.liked) R.drawable.ip_ic_heart else R.drawable.ip_ic_heart_border)
        v.setInt(R.id.ip_like, "setColorFilter", if (state.liked) look.accent else LIKE_OFF)
        v.setOnClickPendingIntent(R.id.ip_like, broadcast(context, CdPlayerWidgetProvider.ACTION_LIKE))
        v.setOnClickPendingIntent(R.id.ip_queue, openApp(context))

        v.setOnClickPendingIntent(R.id.ip_hit_app, openApp(context))
        v.setOnClickPendingIntent(R.id.ip_hit_screen, openApp(context))
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

    // ---------------------------------------------------------------------------------------
    //  Fonts
    // ---------------------------------------------------------------------------------------
    @Volatile
    private var cachedMedium: Typeface? = null

    private fun medium(context: Context): Typeface = cachedMedium ?: run {
        runCatching { ResourcesCompat.getFont(context, R.font.be_vietnam_pro_medium) }.getOrNull()
            ?: Typeface.DEFAULT
    }.also { cachedMedium = it }

    // ---------------------------------------------------------------------------------------
    //  Screen colours taken from the cover
    // ---------------------------------------------------------------------------------------
    private class Look(val background: Bitmap, val accent: Int)

    private class LookCache(val art: Bitmap, val look: Look)

    @Volatile
    private var lookCache: LookCache? = null

    /** Blurred-cover background + a light accent of the cover's hue (artist name, liked heart). */
    private fun lookFor(art: Bitmap?): Look {
        if (art == null) return neutralLook()
        lookCache?.takeIf { it.art === art }?.let { return it.look }

        val look = runCatching {
            val small = Bitmap.createScaledBitmap(art, 12, 12, true)
            var r = 0L; var g = 0L; var b = 0L
            val px = IntArray(small.width * small.height)
            small.getPixels(px, 0, small.width, 0, 0, small.width, small.height)
            for (p in px) { r += Color.red(p); g += Color.green(p); b += Color.blue(p) }
            val n = max(1, px.size)
            val hsv = FloatArray(3)
            Color.RGBToHSV((r / n).toInt(), (g / n).toInt(), (b / n).toInt(), hsv)
            if (small !== art) small.recycle()

            val accent = if (hsv[1] < 0.10f) NEUTRAL_ACCENT
            else Color.HSVToColor(floatArrayOf(hsv[0], hsv[1].coerceIn(0.30f, 0.55f), 0.97f))
            Look(renderBlurredBackground(art), accent)
        }.getOrElse { neutralLook() }
        lookCache = LookCache(art, look)
        return look
    }

    private fun neutralLook(): Look {
        val bmp = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        val paint = Paint().apply {
            shader = LinearGradient(0f, 0f, 0f, 16f, NEUTRAL_TOP, NEUTRAL_BOTTOM, Shader.TileMode.CLAMP)
        }
        Canvas(bmp).drawRect(0f, 0f, 16f, 16f, paint)
        return Look(bmp, NEUTRAL_ACCENT)
    }

    // ---------------------------------------------------------------------------------------
    //  Bitmaps
    // ---------------------------------------------------------------------------------------

    /** Square, heavily blurred, slightly more saturated copy of the cover (shown with centerCrop). */
    private fun renderBlurredBackground(art: Bitmap): Bitmap {
        val side = min(art.width, art.height)
        var small = Bitmap.createBitmap(art, (art.width - side) / 2, (art.height - side) / 2, side, side)
        while (small.width > 12) {
            val next = max(12, small.width / 3)
            small = Bitmap.createScaledBitmap(small, next, next, true)
        }
        var big = small
        for (target in intArrayOf(48, BG_PX)) big = Bitmap.createScaledBitmap(big, target, target, true)

        val out = Bitmap.createBitmap(BG_PX, BG_PX, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(1.3f) })
        }
        Canvas(out).drawBitmap(big, null, RectF(0f, 0f, BG_PX.toFloat(), BG_PX.toFloat()), paint)
        return out
    }

    /** Square cover with rounded corners (or a music-note placeholder when there is no cover). */
    private fun renderCover(art: Bitmap?): Bitmap {
        val size = COVER_PX
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        // the cover sits a little inside the bitmap so its drop shadow has room to show
        val inset = size * COVER_SHADOW_INSET
        val r = RectF(inset, inset, size - inset, size - inset)
        val radius = r.width() * 0.06f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        if (art != null) {
            val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                setShadowLayer(size * 0.035f, 0f, size * 0.012f, 0x80000000.toInt())
            }
            c.drawRoundRect(r, radius, radius, shadow)
            c.clipPath(Path().apply { addRoundRect(r, radius, radius, Path.Direction.CW) })
            // centre-crop so a non-square cover is cropped, never squashed
            val side = min(art.width, art.height)
            val src = android.graphics.Rect(
                (art.width - side) / 2, (art.height - side) / 2,
                (art.width + side) / 2, (art.height + side) / 2,
            )
            c.drawBitmap(art, src, r, paint)
        } else {
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
        return bmp
    }

    @Volatile
    private var wheelCache: Bitmap? = null

    /** The click wheel never changes, so it is drawn once. */
    private fun renderWheel(context: Context): Bitmap {
        wheelCache?.takeIf { !it.isRecycled }?.let { return it }
        return drawWheel(context).also { wheelCache = it }
    }

    private fun drawWheel(context: Context): Bitmap {
        val size = WHEEL_PX
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val cx = size / 2f
        val cy = size / 2f
        val r = size * 0.46f            // leaves room around the wheel for its shadow
        val wheelD = r * 2f
        val hair = max(1f, size * 0.003f)

        // wheel with a soft drop shadow
        paint.color = WHEEL_BOTTOM
        paint.setShadowLayer(size * 0.025f, 0f, size * 0.01f, 0x40000000)
        c.drawCircle(cx, cy, r, paint)
        paint.clearShadowLayer()
        paint.shader = LinearGradient(0f, cy - r, 0f, cy + r, WHEEL_TOP, WHEEL_BOTTOM, Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, r, paint)
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = hair
        paint.color = WHEEL_EDGE
        c.drawCircle(cx, cy, r - hair / 2f, paint)
        paint.style = Paint.Style.FILL

        // centre button
        val br = wheelD * CENTER_BTN / 2f
        paint.color = BUTTON_BOTTOM
        paint.setShadowLayer(size * 0.018f, 0f, size * 0.007f, 0x33000000)
        c.drawCircle(cx, cy, br, paint)
        paint.clearShadowLayer()
        paint.shader = RadialGradient(cx - br * 0.25f, cy - br * 0.35f, br * 1.4f, BUTTON_TOP, BUTTON_BOTTOM, Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, br, paint)
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = hair
        paint.color = 0xFFCBCBD0.toInt()
        c.drawCircle(cx, cy, br - hair / 2f, paint)
        paint.style = Paint.Style.FILL

        val ring = wheelD * ICON_RING

        // MUSIC label
        val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = medium(context); textSize = wheelD * 0.085f; color = GLYPH
            letterSpacing = 0.14f
        }
        val label = context.getString(R.string.ip_wheel_label)
        c.drawText(label, cx - tp.measureText(label) / 2f, baseline(tp, cy - ring), tp)

        // transport glyphs
        val gh = wheelD * 0.105f          // glyph height
        val bar = max(1.5f, wheelD * 0.016f)
        paint.color = GLYPH
        // previous: bar + left-pointing triangle
        run {
            val x = cx - ring
            val w = wheelD * 0.085f
            val l = x - w / 2f
            c.drawRect(l, cy - gh / 2f, l + bar, cy + gh / 2f, paint)
            c.drawPath(triangle(l + bar, cy, l + w, cy - gh / 2f, l + w, cy + gh / 2f), paint)
        }
        // next: right-pointing triangle + bar
        run {
            val x = cx + ring
            val w = wheelD * 0.085f
            val rgt = x + w / 2f
            c.drawRect(rgt - bar, cy - gh / 2f, rgt, cy + gh / 2f, paint)
            c.drawPath(triangle(rgt - bar, cy, rgt - w, cy - gh / 2f, rgt - w, cy + gh / 2f), paint)
        }
        // play / pause: triangle followed by two bars
        run {
            val y = cy + ring
            val tri = wheelD * 0.065f
            val gap = wheelD * 0.018f
            val total = tri + gap + bar * 2f + bar
            val l = cx - total / 2f
            c.drawPath(triangle(l + tri, y, l, y - gh / 2f, l, y + gh / 2f), paint)
            val b1 = l + tri + gap
            c.drawRect(b1, y - gh / 2f, b1 + bar, y + gh / 2f, paint)
            c.drawRect(b1 + bar * 2f, y - gh / 2f, b1 + bar * 3f, y + gh / 2f, paint)
        }
        return bmp
    }

    /** Triangle with its tip at (tx, ty) and base corners at (ax, ay) / (bx, by). */
    private fun triangle(tx: Float, ty: Float, ax: Float, ay: Float, bx: Float, by: Float) = Path().apply {
        moveTo(tx, ty); lineTo(ax, ay); lineTo(bx, by); close()
    }

    private fun baseline(tp: TextPaint, centerY: Float) = centerY - (tp.ascent() + tp.descent()) / 2f
}