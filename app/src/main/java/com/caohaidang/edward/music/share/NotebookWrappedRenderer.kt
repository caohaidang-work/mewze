package com.caohaidang.edward.music.share

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.CornerPathEffect
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.graphics.Typeface
import android.text.Layout
import android.text.TextPaint
import androidx.core.graphics.ColorUtils
import com.caohaidang.edward.music.models.MediaMetadata
import com.caohaidang.edward.music.share.ShareCardRenderer.Weight
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Wrapped theo phong cách "My Recent Favs": giấy nhăn, chữ pixel, đĩa than,
 * ảnh bìa xoay nghiêng quanh đĩa và bìa đĩa ghi danh sách bài.
 */
internal object NotebookWrappedRenderer {
    private const val W = 1080
    private const val H = 1920

    private val INK = 0xFF121212.toInt()
    private val PAPER = 0xFFF4F3EF.toInt()
    private val SLEEVE = 0xFFE9E9E7.toInt()
    private val GRAY = 0xFF6F6F6C.toInt()

    // Vị trí các ảnh bìa quanh mép đĩa (độ, hệ tọa độ canvas: 270 = hướng lên)
    private val slotAngles = floatArrayOf(200f, 232f, 270f, 308f, 340f)
    private val slotTilts = floatArrayOf(-26f, -13f, 2f, 13f, 22f)

    private fun slotsFor(n: Int): IntArray = when (n) {
        1 -> intArrayOf(2)
        2 -> intArrayOf(1, 3)
        3 -> intArrayOf(1, 2, 3)
        4 -> intArrayOf(0, 1, 3, 4)
        else -> intArrayOf(0, 1, 2, 3, 4)
    }

    private fun labelColor(theme: CardTheme) = when (theme.name) {
        "wrapped_month" -> 0xFFE3D3F5.toInt()
        "wrapped_year" -> 0xFFF8D9BF.toInt()
        else -> 0xFFF2EBD8.toInt()
    }

    // ───────────────────────── Trang 1: tổng quan ─────────────────────────

    fun overview(
        context: Context,
        totalMinutes: Long,
        topSong: MediaMetadata?,
        topSongPlays: Int,
        songs: List<Pair<MediaMetadata, Int>>,
        texts: WrappedTexts,
        images: WrappedImages,
        theme: CardTheme,
    ): Bitmap {
        ShareCardRenderer.loadFonts(context)
        val (bitmap, c) = newPage()

        title(c, "MY RECENT", "Favs", pixelBaseline = 215f, pixelSize = 150f, scriptSize = 190f)
        dateTag(c, texts.periodLabel, 432f)
        outlineStar(c, 130f, 130f, 20f)
        outlineStar(c, 950f, 300f, 17f)
        outlineStar(c, 170f, 470f, 14f)

        // Chỉ giữ lại tổng thời gian nghe, trong khung chọn kiểu trình chỉnh sửa
        selectionBox(
            c, RectF(340f, 505f, 740f, 655f),
            ShareCardRenderer.formatNumber(totalMinutes), texts.minutesUnit
        )

        val list = songs.take(5)
        val cx = 540f
        val cy = 1230f
        val radius = 470f
        vinyl(c, cx, cy, radius, labelColor(theme))
        arcPrints(c, cx, cy, list.size) { i, x, y, tilt ->
            poster(c, images.songCovers.getOrNull(i), x, y, 215f, tilt)
        }

        val top = 1410f
        sleeve(c, RectF(80f, top, 1000f, H + 60f))
        trackList(
            c,
            titles = list.map { it.first.title },
            subs = list.map { null },
            top = top + 78f,
        )
        wordmark(c, context, 1856f)
        return bitmap
    }

    // ───────────────────────── Trang 2: top bài hát ─────────────────────────

    fun songs(
        context: Context,
        songs: List<Pair<MediaMetadata, Int>>,
        texts: WrappedTexts,
        images: WrappedImages,
        theme: CardTheme,
    ): Bitmap {
        ShareCardRenderer.loadFonts(context)
        val (bitmap, c) = newPage()

        titleFrom(c, texts.topSongsTitle, pixelBaseline = 200f, pixelSize = 130f, scriptSize = 150f)
        dateTag(c, texts.periodLabel, 405f)
        outlineStar(c, 900f, 150f, 18f)
        outlineStar(c, 190f, 440f, 15f)

        vinyl(c, 540f, 860f, 360f, labelColor(theme))

        val sleeveTop = 660f
        sleeve(c, RectF(80f, sleeveTop, 1000f, 1830f), deg = -1.2f)

        rotated(c, -1.2f, 540f, 1245f) {
            songs.take(5).forEachIndexed { i, (song, plays) ->
                val rowTop = sleeveTop + 50f + i * 212f
                pixelText(c, "%02d".format(i + 1), 140f, rowTop + 100f, 54f, INK)

                poster(
                    c, images.songCovers.getOrNull(i),
                    340f, rowTop + 78f, 148f, if (i % 2 == 0) -4f else 3.5f
                )

                val tx = 462f
                val tw = 940f - tx
                val titlePaint = ShareCardRenderer.paint(INK, 40f, Weight.Bold)
                c.drawText(ShareCardRenderer.ellipsize(song.title, titlePaint, tw), tx, rowTop + 54f, titlePaint)
                val artistPaint = ShareCardRenderer.paint(GRAY, 31f, Weight.Regular)
                c.drawText(
                    ShareCardRenderer.ellipsize(song.artists.joinToString { it.name }, artistPaint, tw),
                    tx, rowTop + 100f, artistPaint
                )
                pixelText(c, texts.playsText(plays), tx, rowTop + 148f, 32f, INK)

                if (i < songs.take(5).size - 1) dashedLine(c, 130f, rowTop + 190f, 950f, 0xFFB9B9B5.toInt())
            }
        }
        wordmark(c, context, 1770f)
        return bitmap
    }

    // ───────────────────────── Trang 3: top nghệ sĩ ─────────────────────────

    fun artists(
        context: Context,
        artists: List<Pair<String, Long>>,
        texts: WrappedTexts,
        images: WrappedImages,
        theme: CardTheme,
    ): Bitmap {
        ShareCardRenderer.loadFonts(context)
        val (bitmap, c) = newPage()

        titleFrom(c, texts.topArtistsTitle, pixelBaseline = 215f, pixelSize = 150f, scriptSize = 190f)
        dateTag(c, texts.periodLabel, 440f)
        outlineStar(c, 292f, 70f, 20f)
        outlineStar(c, 900f, 540f, 17f)
        outlineStar(c, 200f, 560f, 15f)

        val list = artists.take(5)
        val cx = 540f
        val cy = 1230f
        vinyl(c, cx, cy, 470f, labelColor(theme))
        arcPrints(c, cx, cy, list.size) { i, x, y, _ ->
            avatar(c, images.artistAvatars.getOrNull(i), x, y, 108f)
        }

        val top = 1410f
        sleeve(c, RectF(80f, top, 1000f, H + 60f))
        trackList(
            c,
            titles = list.map { it.first },
            subs = list.map { texts.minutesText(it.second / 60_000) },
            top = top + 78f,
        )
        wordmark(c, context, 1856f)
        return bitmap
    }

    // ───────────────────────── Thành phần chung ─────────────────────────

    @Volatile
    private var wordmarkBmp: Bitmap? = null

    /** Logo wordmark căn giữa theo chiều ngang, cao ~54px, tâm tại cy. */
    private fun wordmark(c: Canvas, context: Context, cy: Float) {
        val img = wordmarkBmp ?: runCatching {
            android.graphics.BitmapFactory.decodeResource(
                context.applicationContext.resources,
                com.caohaidang.edward.music.R.drawable.logo_wordmark
            )
        }.getOrNull()?.also { wordmarkBmp = it } ?: return
        val h = 56f
        val w = img.width * (h / img.height)
        val dst = RectF(W / 2f - w / 2f, cy - h / 2f, W / 2f + w / 2f, cy + h / 2f)
        c.drawBitmap(img, null, dst, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
    }

    /** Tách "Top songs" thành chữ pixel "TOP" và chữ viết tay "Songs". */
    private fun titleFrom(c: Canvas, text: String, pixelBaseline: Float, pixelSize: Float, scriptSize: Float) {
        val parts = text.trim().split(" ", limit = 2)
        val script = parts.getOrNull(1).orEmpty().replaceFirstChar { it.uppercase() }
        title(c, parts[0].uppercase(), script, pixelBaseline, pixelSize, scriptSize)
    }

    /** Tiêu đề: dòng pixel đậm có bóng lệch, bên dưới là chữ viết tay nghiêng ôm sát. */
    private fun title(
        c: Canvas, pix: String, script: String,
        pixelBaseline: Float, pixelSize: Float, scriptSize: Float,
    ) {
        var size = pixelSize
        while (pixelWidth(pix, size) > 820f && size > 60f) size -= 6f
        pixelText(c, pix, W / 2f + 7f, pixelBaseline + 7f, size, Color.argb(46, 0, 0, 0), Paint.Align.CENTER)
        pixelText(c, pix, W / 2f, pixelBaseline, size, INK, Paint.Align.CENTER)

        if (script.isNotEmpty()) {
            scriptText(c, script, W / 2f + 30f, pixelBaseline + scriptSize * 0.66f, scriptSize, -3f)
        }
    }

    /** Nhãn khoảng thời gian dạng tem bo tròn. */
    private fun dateTag(c: Canvas, s: String, cy: Float) {
        if (s.isBlank()) return
        val (t, size) = fitPixel(s, 30f, 820f, 22f)
        val w = pixelWidth(t, size) + 64f
        val rect = RectF(W / 2f - w / 2f, cy - 30f, W / 2f + w / 2f, cy + 30f)
        rotated(c, -1.5f, W / 2f, cy) {
            softShadow(c, rect, 30f, 70)
            c.drawRoundRect(rect, 30f, 30f, fill(0xFFFAFAF8.toInt()))
            c.drawRoundRect(rect, 30f, 30f, strokePaint(INK, 2.5f))
            pixelText(c, t, W / 2f, cy + size * 0.36f, size, INK, Paint.Align.CENTER)
        }
    }

    private fun scriptText(c: Canvas, s: String, cx: Float, baseline: Float, size: Float, deg: Float) {
        val fill = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.SERIF, Typeface.ITALIC)
            textSize = size
            color = INK
            textAlign = Paint.Align.CENTER
            textSkewX = -0.14f
        }
        while (fill.measureText(s) > 820f && fill.textSize > 60f) fill.textSize -= 6f
        val outline = TextPaint(fill).apply {
            style = Paint.Style.STROKE
            strokeWidth = 18f
            strokeJoin = Paint.Join.ROUND
            color = PAPER
        }
        rotated(c, deg, cx, baseline) {
            c.drawText(s, cx, baseline, outline)
            c.drawText(s, cx, baseline, fill)
        }
    }

    /** Vẽ chữ kiểu pixel: render chữ nhỏ không khử răng cưa rồi phóng to không làm mượt. */
    private fun pixelText(
        c: Canvas, s: String, x: Float, baseline: Float, size: Float, color: Int,
        align: Paint.Align = Paint.Align.LEFT, rotate: Float = 0f,
    ) {
        if (s.isEmpty()) return
        val small = 20f
        val scale = size / small
        val tp = ShareCardRenderer.paint(Color.WHITE, small, Weight.Bold, letterSpacing = 0.06f).apply {
            isAntiAlias = false
        }
        val fm = tp.fontMetrics
        val w = Math.ceil(tp.measureText(s).toDouble()).toInt() + 4
        val h = Math.ceil((fm.descent - fm.ascent).toDouble()).toInt() + 4
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Canvas(bmp).drawText(s, 2f, 2f - fm.ascent, tp)

        val paint = Paint().apply {
            isFilterBitmap = false
            isAntiAlias = false
            colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
        }
        val dw = w * scale
        val dh = h * scale
        val left = when (align) {
            Paint.Align.LEFT -> x
            Paint.Align.CENTER -> x - dw / 2f
            Paint.Align.RIGHT -> x - dw
        }
        val top = baseline - (2f - fm.ascent) * scale
        rotated(c, rotate, x, baseline) {
            c.drawBitmap(bmp, null, RectF(left, top, left + dw, top + dh), paint)
        }
        bmp.recycle()
    }

    private fun pixelWidth(s: String, size: Float): Float {
        val tp = ShareCardRenderer.paint(Color.WHITE, 20f, Weight.Bold, letterSpacing = 0.06f)
        return (tp.measureText(s) + 4f) * (size / 20f)
    }

    /** Rút gọn chữ pixel cho vừa chiều rộng: thu nhỏ dần rồi mới cắt bớt. */
    private fun fitPixel(s: String, size: Float, maxW: Float, minSize: Float = 30f): Pair<String, Float> {
        var sz = size
        while (pixelWidth(s, sz) > maxW && sz > minSize) sz -= 2f
        if (pixelWidth(s, sz) <= maxW) return s to sz
        var t = s
        while (t.length > 1 && pixelWidth("$t…", sz) > maxW) t = t.dropLast(1)
        return "${t.trimEnd()}…" to sz
    }

    private fun trackList(c: Canvas, titles: List<String>, subs: List<String?>, top: Float) {
        titles.forEachIndexed { i, title ->
            val baseline = top + i * 76f
            pixelText(c, "%02d".format(i + 1), 150f, baseline, 44f, INK)
            val sub = subs.getOrNull(i)
            val maxW = if (sub != null) 480f else 640f
            val (t, sz) = fitPixel(title.lowercase(), 44f, maxW)
            pixelText(c, t, 930f, baseline, sz, INK, Paint.Align.RIGHT)
            if (sub != null) {
                val sp = ShareCardRenderer.paint(GRAY, 28f, Weight.Medium)
                c.drawText(ShareCardRenderer.ellipsize(sub, sp, 220f), 246f, baseline - 2f, sp)
            }
        }
    }

    // ───────────────────────── Đĩa than ─────────────────────────

    private fun vinyl(c: Canvas, cx: Float, cy: Float, r: Float, label: Int) {
        // bóng đổ
        c.drawCircle(cx + 8f, cy + 16f, r, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(90, 20, 20, 20)
            maskFilter = BlurMaskFilter(26f, BlurMaskFilter.Blur.NORMAL)
        })
        c.drawCircle(cx, cy, r, fill(0xFF0E0E0E.toInt()))

        // rãnh đĩa
        var g = r - 12f
        var k = 0
        while (g > r * 0.36f) {
            val col = if (k % 3 == 0) Color.argb(34, 255, 255, 255) else Color.argb(22, 0, 0, 0)
            c.drawCircle(cx, cy, g, strokePaint(col, 2f))
            g -= 7f
            k++
        }
        // vòng phân cách tối hơn giống đĩa thật
        c.drawCircle(cx, cy, r * 0.64f, strokePaint(0xFF2B2B2B.toInt(), 5f))
        c.drawCircle(cx, cy, r * 0.43f, strokePaint(0xFF242424.toInt(), 4f))

        // ánh phản quang
        val sheen = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = SweepGradient(
                cx, cy,
                intArrayOf(
                    Color.TRANSPARENT, Color.argb(46, 255, 255, 255), Color.TRANSPARENT,
                    Color.TRANSPARENT, Color.argb(36, 255, 255, 255), Color.TRANSPARENT, Color.TRANSPARENT
                ),
                floatArrayOf(0f, 0.09f, 0.2f, 0.5f, 0.59f, 0.7f, 1f)
            )
        }
        c.drawCircle(cx, cy, r - 6f, sheen)

        // nhãn giữa
        val lr = r * 0.30f
        c.drawCircle(cx, cy, lr, fill(label))
        c.drawCircle(cx, cy, lr, strokePaint(ColorUtils.blendARGB(label, Color.BLACK, 0.18f), 3f))
        c.drawCircle(cx, cy, lr * 0.62f, strokePaint(ColorUtils.blendARGB(label, Color.BLACK, 0.10f), 2f))
        c.drawCircle(cx, cy, 15f, fill(0xFF0E0E0E.toInt()))
        c.drawCircle(cx, cy, 9f, fill(PAPER))
    }

    /** Đặt các ảnh quanh cung phía trên đĩa kèm số thứ hạng nằm trong đĩa. */
    private fun arcPrints(
        c: Canvas, cx: Float, cy: Float, n: Int,
        draw: (index: Int, x: Float, y: Float, tilt: Float) -> Unit,
    ) {
        val slots = slotsFor(n)
        slots.forEachIndexed { i, slot ->
            val a = Math.toRadians(slotAngles[slot].toDouble())
            val px = cx + (420f * cos(a)).toFloat()
            val py = cy + (420f * sin(a)).toFloat()
            draw(i, px, py, slotTilts[slot])

            val nx = cx + (292f * cos(a)).toFloat()
            val ny = cy + (292f * sin(a)).toFloat()
            pixelText(c, (i + 1).toString(), nx, ny + 20f, 50f, Color.WHITE, Paint.Align.CENTER)
        }
    }

    // ───────────────────────── Bìa đĩa, ảnh, khung ─────────────────────────

    private fun sleeve(c: Canvas, rect: RectF, deg: Float = 0f) {
        rotated(c, deg, rect.centerX(), rect.centerY()) {
            c.drawRect(RectF(rect).apply { offset(0f, -6f) }, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(80, 20, 20, 20)
                maskFilter = BlurMaskFilter(22f, BlurMaskFilter.Blur.NORMAL)
            })
            c.drawRect(rect, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    0f, rect.top, 0f, minOf(rect.bottom, H.toFloat()),
                    0xFFEEEEEC.toInt(), 0xFFDCDCD9.toInt(), Shader.TileMode.CLAMP
                )
            })
            c.drawRect(rect, strokePaint(0xFFCDCDC9.toInt(), 2.5f))

            // vài nếp nhăn mờ trên bìa
            val rnd = Random(rect.top.toInt())
            repeat(10) {
                val x1 = rect.left + rnd.nextFloat() * rect.width()
                val y1 = rect.top + rnd.nextFloat() * 500f
                val x2 = x1 + (rnd.nextFloat() - 0.5f) * 320f
                val y2 = y1 + 80f + rnd.nextFloat() * 220f
                c.drawLine(x1, y1, x2, y2, strokePaint(Color.argb(16, 0, 0, 0), 2f))
                c.drawLine(x1 + 3f, y1, x2 + 3f, y2, strokePaint(Color.argb(60, 255, 255, 255), 2f))
            }
        }
    }

    private fun poster(c: Canvas, bmp: Bitmap?, cx: Float, cy: Float, size: Float, deg: Float) {
        val pad = 9f
        val frame = RectF(cx - size / 2 - pad, cy - size / 2 - pad, cx + size / 2 + pad, cy + size / 2 + pad)
        rotated(c, deg, cx, cy) {
            softShadow(c, frame, 4f, 110)
            c.drawRect(frame, fill(Color.WHITE))
            val img = RectF(cx - size / 2, cy - size / 2, cx + size / 2, cy + size / 2)
            drawCrop(c, bmp, img, 0f, 0xFFBDBDBA.toInt(), INK)
        }
    }

    private fun avatar(c: Canvas, bmp: Bitmap?, cx: Float, cy: Float, r: Float) {
        c.drawCircle(cx + 5f, cy + 10f, r + 9f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(110, 20, 20, 20)
            maskFilter = BlurMaskFilter(18f, BlurMaskFilter.Blur.NORMAL)
        })
        c.drawCircle(cx, cy, r + 9f, fill(Color.WHITE))
        drawCropCircle(c, bmp, cx, cy, r, 0xFFBDBDBA.toInt(), INK)
    }

    /** Khung chọn giống trình chỉnh sửa (có 8 điểm điều khiển và con trỏ chuột). */
    private fun selectionBox(c: Canvas, r: RectF, big: String, small: String) {
        c.drawRect(r, strokePaint(0xFF8A8A87.toInt(), 2.5f))
        val hs = 9f
        val xs = floatArrayOf(r.left, r.centerX(), r.right)
        val ys = floatArrayOf(r.top, r.centerY(), r.bottom)
        for (xi in 0..2) for (yi in 0..2) {
            if (xi == 1 && yi == 1) continue
            val hr = RectF(xs[xi] - hs, ys[yi] - hs, xs[xi] + hs, ys[yi] + hs)
            c.drawRect(hr, fill(Color.WHITE))
            c.drawRect(hr, strokePaint(0xFF8A8A87.toInt(), 2.5f))
        }
        val (bt, bs) = fitPixel(big, 100f, r.width() - 40f, 40f)
        pixelText(c, bt, r.centerX(), r.centerY() + 12f, bs, INK, Paint.Align.CENTER)
        val sp = ShareCardRenderer.paint(GRAY, 28f, Weight.Medium, align = Paint.Align.CENTER)
        c.drawText(ShareCardRenderer.ellipsize(small, sp, r.width() - 24f), r.centerX(), r.centerY() + 66f, sp)

        // con trỏ chuột
        val ox = r.right + 6f
        val oy = r.bottom + 10f
        val arrow = Path().apply {
            moveTo(ox, oy)
            lineTo(ox, oy + 34f)
            lineTo(ox + 9f, oy + 26f)
            lineTo(ox + 16f, oy + 42f)
            lineTo(ox + 23f, oy + 39f)
            lineTo(ox + 16f, oy + 24f)
            lineTo(ox + 28f, oy + 24f)
            close()
        }
        c.drawPath(arrow, fill(INK))
        c.drawPath(arrow, strokePaint(Color.WHITE, 3f))
    }

    // ───────────────────────── Giấy nhăn & họa tiết ─────────────────────────

    private fun newPage(): Pair<Bitmap, Canvas> {
        val bitmap = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bitmap)
        c.drawColor(PAPER)
        val rnd = Random(20261015)

        // vệt sáng/tối loang nhẹ như giấy bị vò
        repeat(16) { i ->
            val x = rnd.nextFloat() * W
            val y = rnd.nextFloat() * H
            val r = 260f + rnd.nextFloat() * 420f
            val light = i % 2 == 0
            val center = if (light) Color.argb(120, 255, 255, 255) else Color.argb(34, 120, 116, 104)
            c.drawCircle(x, y, r, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(x, y, r, center, Color.TRANSPARENT, Shader.TileMode.CLAMP)
            })
        }

        // nếp gấp: một đường tối kèm đường sáng sát bên cạnh
        repeat(80) {
            val x1 = rnd.nextFloat() * W
            val y1 = rnd.nextFloat() * H
            val len = 120f + rnd.nextFloat() * 520f
            val ang = rnd.nextFloat() * Math.PI.toFloat()
            val mx = x1 + cos(ang) * len * 0.5f + (rnd.nextFloat() - 0.5f) * 60f
            val my = y1 + sin(ang) * len * 0.5f + (rnd.nextFloat() - 0.5f) * 60f
            val x2 = x1 + cos(ang) * len
            val y2 = y1 + sin(ang) * len
            val path = Path().apply { moveTo(x1, y1); quadTo(mx, my, x2, y2) }
            c.drawPath(path, strokePaint(Color.argb(10 + rnd.nextInt(16), 70, 66, 56), 2f))
            c.save(); c.translate(2.5f, 2f)
            c.drawPath(path, strokePaint(Color.argb(80, 255, 255, 255), 2f))
            c.restore()
        }

        // hạt giấy li ti
        val speck = Paint(Paint.ANTI_ALIAS_FLAG)
        repeat(1400) {
            speck.color = Color.argb(6 + rnd.nextInt(12), 60, 56, 48)
            c.drawCircle(rnd.nextFloat() * W, rnd.nextFloat() * H, 0.8f + rnd.nextFloat() * 1.6f, speck)
        }

        // hơi tối dần ra mép
        c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), Paint().apply {
            shader = RadialGradient(
                W / 2f, H / 2f, H * 0.74f,
                Color.TRANSPARENT, Color.argb(34, 90, 84, 66), Shader.TileMode.CLAMP
            )
        })
        return bitmap to c
    }

    private fun fill(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }

    private fun strokePaint(color: Int, width: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.STROKE
        strokeWidth = width
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private inline fun rotated(c: Canvas, deg: Float, px: Float, py: Float, block: () -> Unit) {
        c.save(); c.rotate(deg, px, py); block(); c.restore()
    }

    private fun outlineStar(c: Canvas, cx: Float, cy: Float, r: Float) {
        val path = Path()
        for (i in 0 until 10) {
            val rad = if (i % 2 == 0) r else r * 0.44f
            val a = Math.toRadians((-90 + i * 36).toDouble())
            val x = cx + (rad * cos(a)).toFloat()
            val y = cy + (rad * sin(a)).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        c.drawPath(path, fill(0xFFFAFAF8.toInt()))
        c.drawPath(path, strokePaint(0xFF3A3A38.toInt(), 2f).apply { pathEffect = CornerPathEffect(r * 0.1f) })
    }

    private fun heart(c: Canvas, cx: Float, cy: Float, r: Float, color: Int) {
        val path = Path().apply {
            moveTo(cx, cy + r * 0.9f)
            cubicTo(cx - r * 1.6f, cy - r * 0.2f, cx - r * 0.7f, cy - r * 1.3f, cx, cy - r * 0.4f)
            cubicTo(cx + r * 0.7f, cy - r * 1.3f, cx + r * 1.6f, cy - r * 0.2f, cx, cy + r * 0.9f)
            close()
        }
        c.drawPath(path, fill(color))
    }

    private fun dashedLine(c: Canvas, x1: Float, y: Float, x2: Float, color: Int) {
        c.drawLine(x1, y, x2, y, strokePaint(color, 2.5f).apply { pathEffect = DashPathEffect(floatArrayOf(14f, 12f), 0f) })
    }

    private fun softShadow(c: Canvas, rect: RectF, radius: Float, alpha: Int = 70) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(alpha, 30, 24, 10)
            maskFilter = BlurMaskFilter(16f, BlurMaskFilter.Blur.NORMAL)
        }
        c.drawRoundRect(RectF(rect).apply { offset(4f, 9f) }, radius, radius, paint)
    }

    private fun cropSrc(bmp: Bitmap): Rect {
        val side = minOf(bmp.width, bmp.height)
        return Rect((bmp.width - side) / 2, (bmp.height - side) / 2, (bmp.width + side) / 2, (bmp.height + side) / 2)
    }

    private fun placeholder(c: Canvas, rect: RectF, bg: Int, fg: Int) {
        c.drawRect(rect, fill(bg))
        val notePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ColorUtils.setAlphaComponent(fg, 150)
            textSize = rect.width() * 0.45f
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }
        c.drawText("♪", rect.centerX(), rect.centerY() + rect.width() * 0.16f, notePaint)
    }

    private fun drawCrop(c: Canvas, bmp: Bitmap?, rect: RectF, radius: Float, bg: Int, fg: Int) {
        c.save()
        c.clipPath(Path().apply { addRoundRect(rect, radius, radius, Path.Direction.CW) })
        if (bmp != null) {
            c.drawBitmap(bmp, cropSrc(bmp), rect, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        } else placeholder(c, rect, bg, fg)
        c.restore()
    }

    private fun drawCropCircle(c: Canvas, bmp: Bitmap?, cx: Float, cy: Float, r: Float, bg: Int, fg: Int) {
        val rect = RectF(cx - r, cy - r, cx + r, cy + r)
        c.save()
        c.clipPath(Path().apply { addOval(rect, Path.Direction.CW) })
        if (bmp != null) {
            c.drawBitmap(bmp, cropSrc(bmp), rect, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        } else placeholder(c, rect, bg, fg)
        c.restore()
    }
}
