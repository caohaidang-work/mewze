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
import android.graphics.Paint
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.content.res.ResourcesCompat
import android.widget.RemoteViews
import com.caohaidang.edward.music.MainActivity
import com.caohaidang.edward.music.R
import kotlin.math.max
import kotlin.math.roundToInt

/** Turns a [CdWidgetState] into RemoteViews and pushes them to every placed widget. */
object CdPlayerWidgetRenderer {

    // Layout constants (dp) - keep in sync with res/layout/widget_cd_player.xml
    private const val SLIDER_HEIGHT_DP = 14
    private const val THUMB_WIDTH_DP = 9
    private const val SLIDER_OUTER_DP = 3 + 3 + 5 + 5 + 76 + 8 // frame + body padding + art + gap
    private const val TITLE_OUTER_DP = 6 + 4 + 14 + 4 + 50          // frame + padding + icon + gap + 3 buttons
    private const val FIELD_OUTER_DP = SLIDER_OUTER_DP + 36 + 4 + 10 + 12 + 1 // + label + field padding + tag + arrow
    private const val DEFAULT_WIDTH_DP = 250
    private const val TEXT_SIZE_DP = 10f
    private const val TITLE_SIZE_DP = 11f

    /** Name of the font file in res/font (without extension). Falls back to monospace if missing. */
    private const val FONT_NAME = "ms_sans"

    /** false = crisp hard pixels (best for pixel fonts), true = smooth edges. */
    private const val TEXT_ANTIALIAS = true

    private fun component(context: Context) = ComponentName(context, CdPlayerWidgetProvider::class.java)

    fun hasWidgets(context: Context): Boolean =
        AppWidgetManager.getInstance(context).getAppWidgetIds(component(context)).isNotEmpty()

    /** Full redraw of all widgets. */
    fun updateAll(context: Context, state: CdWidgetState) {
        val manager = AppWidgetManager.getInstance(context)
        updateWidgets(context, manager, manager.getAppWidgetIds(component(context)), state)
    }

    fun updateWidgets(context: Context, manager: AppWidgetManager, ids: IntArray, state: CdWidgetState) {
        for (id in ids) {
            runCatching { manager.updateAppWidget(id, buildViews(context, state, widthDp(context, manager, id))) }
        }
    }

    /** Cheap update that only redraws the progress slider (called every couple of seconds). */
    fun updateProgress(context: Context, state: CdWidgetState) {
        val manager = AppWidgetManager.getInstance(context)
        for (id in manager.getAppWidgetIds(component(context))) {
            val views = RemoteViews(context.packageName, R.layout.widget_cd_player)
            views.setImageViewBitmap(R.id.cdw_slider, sliderBitmap(context, widthDp(context, manager, id), state.fraction))
            runCatching { manager.partiallyUpdateAppWidget(id, views) }
        }
    }

    private fun buildViews(context: Context, state: CdWidgetState, widthDp: Int): RemoteViews {
        val v = RemoteViews(context.packageName, R.layout.widget_cd_player)

        // Text is drawn into bitmaps so the custom font looks identical on every launcher
        // (Xiaomi / HyperOS ignores custom fonts inside RemoteViews TextViews).
        val artist = state.artist?.takeIf { it.isNotBlank() } ?: context.getString(R.string.cdw_idle_artist)
        val track = state.title?.takeIf { it.isNotBlank() } ?: context.getString(R.string.cdw_idle_track)
        val fieldW = widthDp - FIELD_OUTER_DP
        v.setImageViewBitmap(R.id.cdw_title, textBitmap(context, context.getString(R.string.cdw_window_title), widthDp - TITLE_OUTER_DP, 14, Color.WHITE, bold = true))
        v.setImageViewBitmap(R.id.cdw_label_artist, textBitmap(context, context.getString(R.string.cdw_label_artist), 36, 14, Color.BLACK, bold = false, alignEnd = true, padEndDp = 3))
        v.setImageViewBitmap(R.id.cdw_label_track, textBitmap(context, context.getString(R.string.cdw_label_track), 36, 14, Color.BLACK, bold = false, alignEnd = true, padEndDp = 3))
        v.setImageViewBitmap(R.id.cdw_artist, textBitmap(context, artist, fieldW, 14, Color.BLACK, bold = true, padStartDp = 2))
        v.setImageViewBitmap(R.id.cdw_track, textBitmap(context, track, fieldW, 14, Color.BLACK, bold = true, padStartDp = 2))
        v.setContentDescription(R.id.cdw_artist, artist)
        v.setContentDescription(R.id.cdw_track, track)

        if (state.art != null) v.setImageViewBitmap(R.id.cdw_art, state.art)
        else v.setImageViewResource(R.id.cdw_art, R.drawable.cdw_disc)

        v.setImageViewBitmap(R.id.cdw_slider, sliderBitmap(context, widthDp, state.fraction))

        v.setImageViewResource(R.id.cdw_btn_play, if (state.playing) R.drawable.cdw_ic_pause else R.drawable.cdw_ic_play)
        v.setImageViewResource(R.id.cdw_btn_rec, if (state.liked) R.drawable.cdw_ic_record_on else R.drawable.cdw_ic_record)

        // Transport buttons -> broadcast to CdPlayerWidgetProvider
        v.setOnClickPendingIntent(R.id.cdw_btn_prev, action(context, CdPlayerWidgetProvider.ACTION_PREV))
        v.setOnClickPendingIntent(R.id.cdw_btn_next, action(context, CdPlayerWidgetProvider.ACTION_NEXT))
        v.setOnClickPendingIntent(R.id.cdw_btn_play, action(context, CdPlayerWidgetProvider.ACTION_PLAY_PAUSE))
        v.setOnClickPendingIntent(R.id.cdw_btn_stop, action(context, CdPlayerWidgetProvider.ACTION_STOP))
        v.setOnClickPendingIntent(R.id.cdw_btn_rec, action(context, CdPlayerWidgetProvider.ACTION_LIKE))

        // Title bar, cover art and the two text fields open the app
        val open = openApp(context)
        v.setOnClickPendingIntent(R.id.cdw_titlebar, open)
        v.setOnClickPendingIntent(R.id.cdw_art_frame, open)
        v.setOnClickPendingIntent(R.id.cdw_artist_field, open)
        v.setOnClickPendingIntent(R.id.cdw_track_field, open)
        return v
    }

    private fun action(context: Context, action: String): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            action.hashCode(),
            Intent(context, CdPlayerWidgetProvider::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun openApp(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun widthDp(context: Context, manager: AppWidgetManager, id: Int): Int {
        val options = manager.getAppWidgetOptions(id)
        val landscape = context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val key = if (landscape) AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH else AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH
        return options.getInt(key, DEFAULT_WIDTH_DP).takeIf { it > 0 } ?: DEFAULT_WIDTH_DP
    }

    // ---------------------------------------------------------------------------------------
    //  Text drawn as bitmap with the custom font
    // ---------------------------------------------------------------------------------------
    @Volatile
    private var cachedFont: Typeface? = null

    private fun font(context: Context): Typeface = cachedFont ?: run {
        val id = context.resources.getIdentifier(FONT_NAME, "font", context.packageName)
        (if (id != 0) runCatching { ResourcesCompat.getFont(context, id) }.getOrNull() else null)
            ?: Typeface.MONOSPACE
    }.also { cachedFont = it }

    private fun textBitmap(
        context: Context,
        text: String,
        widthDp: Int,
        heightDp: Int,
        color: Int,
        bold: Boolean,
        alignEnd: Boolean = false,
        padStartDp: Int = 0,
        padEndDp: Int = 0,
    ): Bitmap {
        val d = context.resources.displayMetrics.density
        val w = (max(8, widthDp) * d).roundToInt()
        val h = (heightDp * d).roundToInt()
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val tp = TextPaint().apply {
            isAntiAlias = TEXT_ANTIALIAS
            typeface = font(context)
            textSize = (if (bold && color == Color.WHITE) TITLE_SIZE_DP else TEXT_SIZE_DP) * d
            this.color = color
            isFakeBoldText = bold
        }
        val avail = (w - (padStartDp + padEndDp) * d).coerceAtLeast(1f)
        val shown = TextUtils.ellipsize(text, tp, avail, TextUtils.TruncateAt.END).toString()
        val x = if (alignEnd) w - padEndDp * d - tp.measureText(shown) else padStartDp * d
        val y = h / 2f - (tp.ascent() + tp.descent()) / 2f
        Canvas(bmp).drawText(shown, x, y, tp)
        return bmp
    }

    // ---------------------------------------------------------------------------------------
    //  Win98 slider drawn pixel-perfect (no anti-aliasing) because RemoteViews has no SeekBar
    // ---------------------------------------------------------------------------------------
    private val paint = Paint().apply { isAntiAlias = false; isFilterBitmap = false; style = Paint.Style.FILL }

    private fun fill(c: Canvas, l: Int, t: Int, r: Int, b: Int, color: Int) {
        paint.color = color
        c.drawRect(l.toFloat(), t.toFloat(), r.toFloat(), b.toFloat(), paint)
    }

    private fun sliderBitmap(context: Context, widgetWidthDp: Int, fraction: Float): Bitmap {
        val density = context.resources.displayMetrics.density
        val u = max(1, density.roundToInt()) // 1 "pixel" of the bevel
        val w = (max(60, widgetWidthDp - SLIDER_OUTER_DP) * density).roundToInt()
        val h = (SLIDER_HEIGHT_DP * density).roundToInt()
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)

        // groove
        val cy = h / 2
        fill(c, 0, cy - u, w, cy, Color.BLACK)
        fill(c, 0, cy, w, cy + u, Color.WHITE)

        // thumb (raised bevel)
        val tw = (THUMB_WIDTH_DP * density).roundToInt()
        val x = ((w - tw) * fraction.coerceIn(0f, 1f)).roundToInt()
        val l = x; val t = 0; val r = x + tw; val b = h
        fill(c, l, t, r, b, 0xFF000000.toInt())
        fill(c, l, t, r - u, b - u, 0xFFFFFFFF.toInt())
        fill(c, l + u, t + u, r - u, b - u, 0xFF808080.toInt())
        fill(c, l + u, t + u, r - 2 * u, b - 2 * u, 0xFFDFDFDF.toInt())
        fill(c, l + 2 * u, t + 2 * u, r - 2 * u, b - 2 * u, 0xFFC0C0C0.toInt())
        return bmp
    }
}