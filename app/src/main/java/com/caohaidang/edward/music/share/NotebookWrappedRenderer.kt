package com.caohaidang.edward.music.share

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.CornerPathEffect
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.TextPaint
import androidx.core.graphics.ColorUtils
import com.caohaidang.edward.music.models.MediaMetadata
import com.caohaidang.edward.music.share.ShareCardRenderer.Weight
import kotlin.math.cos
import kotlin.math.sin

internal object NotebookWrappedRenderer {
    private const val W = 1080
    private const val H = 1920
    private const val LEFT = 196f
    private const val RIGHT = W - 76f

    private enum class Pattern { RULED, GRID, DOTS }

    private class Palette(
        val paper: Int,
        val line: Int,
        val margin: Int,
        val ink: Int,
        val sub: Int,
        val accent: Int,
        val accent2: Int,
        val accent3: Int,
        val pattern: Pattern,
    )

    private val week = Palette(
        paper = 0xFFFBF7EA.toInt(), line = 0xFFC4DDEE.toInt(), margin = 0xFFF28B8B.toInt(),
        ink = 0xFF22304A.toInt(), sub = 0xFF5B6B86.toInt(),
        accent = 0xFF3FC1A5.toInt(), accent2 = 0xFFFFD84D.toInt(), accent3 = 0xFFFF8FB1.toInt(),
        pattern = Pattern.RULED,
    )
    private val month = Palette(
        paper = 0xFFFDF6F2.toInt(), line = 0xFFE6CCE9.toInt(), margin = 0xFFF28B8B.toInt(),
        ink = 0xFF2E2250.toInt(), sub = 0xFF6A5C8A.toInt(),
        accent = 0xFF9B5DE5.toInt(), accent2 = 0xFFFFC857.toInt(), accent3 = 0xFF5BC0EB.toInt(),
        pattern = Pattern.GRID,
    )
    private val year = Palette(
        paper = 0xFFFFF8E8.toInt(), line = 0xFFE3C79E.toInt(), margin = 0xFFF28B8B.toInt(),
        ink = 0xFF3B2416.toInt(), sub = 0xFF7D5A44.toInt(),
        accent = 0xFFFF7A3D.toInt(), accent2 = 0xFFFFD166.toInt(), accent3 = 0xFF06D6A0.toInt(),
        pattern = Pattern.DOTS,
    )

    private fun paletteFor(theme: CardTheme) = when (theme.name) {
        "wrapped_month" -> month
        "wrapped_year" -> year
        else -> week
    }

    fun overview(
        context: Context,
        totalMinutes: Long,
        topSong: MediaMetadata?,
        topSongPlays: Int,
        texts: WrappedTexts,
        images: WrappedImages,
        theme: CardTheme,
    ): Bitmap {
        ShareCardRenderer.loadFonts(context)
        val p = paletteFor(theme)
        val (bitmap, c) = newPage(p)

        brandLabel(c, texts.brand, 420f, 168f, 500f, 84f, 46f, 192f, p)
        text(c, texts.periodLabel, LEFT, 300f, 46f, p.ink, Weight.SemiBold)
        squiggle(c, LEFT, 328f, LEFT + 520f, 9f, p.accent3, 7f)

        star(c, 900f, 170f, 52f, p.accent2, 12f)
        sparkle(c, 985f, 258f, 30f, p.accent3)
        sparkle(c, 830f, 270f, 20f, p.accent)
        note(c, 960f, 380f, 54f, p.accent, -10f)

        val minutesText = ShareCardRenderer.formatNumber(totalMinutes)
        val big = if (totalMinutes >= 10_000) 240f else 300f
        val bigPaint = ShareCardRenderer.paint(p.ink, big, Weight.Bold)
        val bigW = bigPaint.measureText(minutesText)
        c.drawText(minutesText, LEFT, 660f, bigPaint)
        scribbleCircle(c, RectF(LEFT - 34f, 660f - big * 0.92f, LEFT + bigW + 34f, 696f), p.accent3, 9f)
        highlight(c, texts.minutesUnit, LEFT, 786f, 64f, p.ink, p.accent2, Weight.Bold, rotate = -1.5f)

        val stats = listOfNotNull(texts.songsCount, texts.busiestDay, texts.topArtistLine)
        val notes = intArrayOf(p.accent2, p.accent3, p.accent)
        val rots = floatArrayOf(-1.6f, 1.2f, -0.9f)
        var y = 872f
        stats.forEachIndexed { i, line ->
            val rect = RectF(LEFT, y, RIGHT - 24f, y + 116f)
            stickyNote(c, rect, tint(notes[i % 3], 0.55f), rots[i % 3], if (i == 1) p.accent else p.accent3)
            val tp = ShareCardRenderer.paint(p.ink, 42f, Weight.SemiBold)
            c.save()
            c.rotate(rots[i % 3], rect.centerX(), rect.centerY())
            c.drawText(ShareCardRenderer.ellipsize(line, tp, rect.width() - 150f), rect.left + 34f, rect.centerY() + 15f, tp)
            when (i % 3) {
                0 -> heart(c, rect.right - 54f, rect.centerY(), 22f, p.accent3)
                1 -> star(c, rect.right - 54f, rect.centerY(), 24f, p.accent2, 0f)
                else -> note(c, rect.right - 54f, rect.centerY() + 4f, 34f, p.ink, 0f)
            }
            c.restore()
            y += 140f
        }

        if (topSong != null) {
            val top = 1336f
            stamp(c, LEFT + 150f, top - 8f, texts.topSongLabel.uppercase(), p.accent3, p.ink)
            val size = 300f
            polaroid(c, images.topCover, LEFT + 20f, top + 36f, size, -4f, p)

            val tx = LEFT + 20f + size + 74f
            val tw = RIGHT - tx
            val titleLayout = ShareCardRenderer.buildLayout(
                topSong.title, ShareCardRenderer.paint(p.ink, 54f, Weight.Bold), tw.toInt(),
                Layout.Alignment.ALIGN_NORMAL, maxLines = 3
            )
            c.save(); c.translate(tx, top + 54f); titleLayout.draw(c); c.restore()
            val artistPaint = ShareCardRenderer.paint(p.sub, 40f, Weight.Regular)
            val ay = top + 54f + titleLayout.height + 52f
            c.drawText(
                ShareCardRenderer.ellipsize(topSong.artists.joinToString { it.name }, artistPaint, tw),
                tx, ay, artistPaint
            )
            highlight(c, texts.playsText(topSongPlays), tx, ay + 78f, 40f, p.ink, p.accent2, Weight.Bold, rotate = -1f)
        }

        return bitmap
    }

    fun songs(
        context: Context,
        songs: List<Pair<MediaMetadata, Int>>,
        texts: WrappedTexts,
        images: WrappedImages,
        theme: CardTheme,
    ): Bitmap {
        ShareCardRenderer.loadFonts(context)
        val p = paletteFor(theme)
        val (bitmap, c) = newPage(p)
        pageHeader(c, texts, p)

        highlight(c, texts.topSongsTitle, LEFT, 372f, 96f, p.ink, p.accent2, Weight.Bold, rotate = -1.2f)
        star(c, 930f, 330f, 46f, p.accent3, 14f)
        sparkle(c, 1000f, 400f, 24f, p.accent)

        songs.take(5).forEachIndexed { i, (song, plays) ->
            val top = 470f + i * 236f

            rankBadge(c, 252f, top + 100f, i + 1, p)

            val coverRect = RectF(340f, top + 6f, 340f + 188f, top + 6f + 188f)
            drawCrop(c, images.songCovers.getOrNull(i), coverRect, 26f, tint(p.accent, 0.4f), p.ink)
            c.drawRoundRect(coverRect, 26f, 26f, strokePaint(Color.WHITE, 7f))
            tape(c, coverRect.left + 18f, coverRect.top + 4f, 84f, 34f, -35f, if (i % 2 == 0) p.accent3 else p.accent2)

            val tx = coverRect.right + 40f
            val tw = RIGHT - tx
            val titlePaint = ShareCardRenderer.paint(p.ink, 46f, Weight.Bold)
            c.drawText(ShareCardRenderer.ellipsize(song.title, titlePaint, tw), tx, top + 72f, titlePaint)
            val artistPaint = ShareCardRenderer.paint(p.sub, 36f, Weight.Regular)
            c.drawText(ShareCardRenderer.ellipsize(song.artists.joinToString { it.name }, artistPaint, tw), tx, top + 124f, artistPaint)
            highlight(c, texts.playsText(plays), tx, top + 178f, 36f, p.ink, if (i == 0) p.accent2 else tint(p.accent, 0.55f), Weight.Bold, rotate = -1f)

            if (i < 4) dashedLine(c, LEFT, top + 218f, RIGHT, p.line.darker(0.82f))
        }

        heart(c, 1000f, 1650f, 26f, p.accent3)
        note(c, 120f, 1700f, 44f, p.accent, 8f)
        return bitmap
    }

    fun artists(
        context: Context,
        artists: List<Pair<String, Long>>,
        texts: WrappedTexts,
        images: WrappedImages,
        theme: CardTheme,
    ): Bitmap {
        ShareCardRenderer.loadFonts(context)
        val p = paletteFor(theme)
        val (bitmap, c) = newPage(p)
        pageHeader(c, texts, p)

        highlight(c, texts.topArtistsTitle, LEFT, 372f, 96f, p.ink, p.accent3, Weight.Bold, rotate = 1.2f)
        heart(c, 940f, 330f, 36f, p.accent)
        sparkle(c, 1000f, 410f, 26f, p.accent2)

        artists.take(5).forEachIndexed { i, (name, playTime) ->
            val top = 470f + i * 236f
            rankBadge(c, 252f, top + 100f, i + 1, p)

            val cx = 340f + 94f
            val cy = top + 100f
            val r = 92f
            drawCropCircle(c, images.artistAvatars.getOrNull(i), cx, cy, r, tint(p.accent, 0.4f), p.ink)
            c.drawCircle(cx, cy, r, strokePaint(Color.WHITE, 8f))
            c.drawCircle(cx, cy, r + 14f, strokePaint(if (i % 2 == 0) p.accent3 else p.accent, 4f).apply {
                pathEffect = DashPathEffect(floatArrayOf(14f, 12f), 0f)
            })

            val tx = cx + r + 56f
            val tw = RIGHT - tx
            val namePaint = ShareCardRenderer.paint(p.ink, 50f, Weight.Bold)
            c.drawText(ShareCardRenderer.ellipsize(name, namePaint, tw), tx, top + 88f, namePaint)
            highlight(c, texts.minutesText(playTime / 60_000), tx, top + 148f, 38f, p.ink, tint(p.accent2, 0.7f), Weight.SemiBold, rotate = -1f)

            val stars = (5 - i).coerceAtLeast(1)
            repeat(5) { s ->
                star(c, tx + 18f + s * 46f, top + 186f, 17f, if (s < stars) p.accent2 else p.line.darker(0.92f), 0f)
            }
            if (i < 4) dashedLine(c, LEFT, top + 218f, RIGHT, p.line.darker(0.82f))
        }

        sparkle(c, 140f, 1640f, 28f, p.accent3)
        star(c, 990f, 1700f, 30f, p.accent2, -10f)
        return bitmap
    }

    private fun brandLabel(
        c: Canvas, brand: String, cx: Float, cy: Float, w: Float, h: Float,
        size: Float, baseline: Float, p: Palette,
    ) {
        tape(c, cx, cy, w, h, -3f, p.accent)
        val paint = ShareCardRenderer.paint(p.ink, size, Weight.Bold)
        val logoR = size * 0.62f
        val gap = 14f
        val total = logoR * 2f + gap + paint.measureText(brand)
        val left = cx - total / 2f
        rotated(c, -3f, cx, baseline) {
            ShareCardRenderer.drawLogoMark(c, left + logoR, baseline - size * 0.34f, logoR, p.ink, p.accent)
            c.drawText(brand, left + logoR * 2f + gap, baseline, paint)
        }
    }

    private fun pageHeader(c: Canvas, texts: WrappedTexts, p: Palette) {
        brandLabel(c, texts.brand, 400f, 152f, 480f, 80f, 44f, 175f, p)
        text(c, texts.periodLabel, LEFT, 268f, 42f, p.sub, Weight.SemiBold)
        star(c, 900f, 150f, 40f, p.accent2, 10f)
    }

    private fun newPage(p: Palette): Pair<Bitmap, Canvas> {
        val bitmap = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bitmap)
        c.drawColor(p.paper)

        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = p.line; strokeWidth = 3f }
        when (p.pattern) {
            Pattern.RULED -> {
                var y = 120f + 40f
                while (y < H) { c.drawLine(0f, y, W.toFloat(), y, linePaint); y += 68f }
            }
            Pattern.GRID -> {
                var y = 120f
                while (y < H) { c.drawLine(0f, y, W.toFloat(), y, linePaint); y += 60f }
                var x = 36f
                while (x < W) { c.drawLine(x, 0f, x, H.toFloat(), linePaint); x += 60f }
            }
            Pattern.DOTS -> {
                var y = 120f
                while (y < H) {
                    var x = 36f
                    while (x < W) { c.drawCircle(x, y, 4.5f, linePaint); x += 56f }
                    y += 56f
                }
            }
        }

        val margin = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = p.margin; strokeWidth = 4f }
        c.drawLine(152f, 0f, 152f, H.toFloat(), margin)
        c.drawLine(164f, 0f, 164f, H.toFloat(), Paint(margin).apply { alpha = 120; strokeWidth = 2.5f })

        listOf(340f, 960f, 1580f).forEach { cy ->
            val hole = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ColorUtils.blendARGB(p.paper, Color.BLACK, 0.22f) }
            c.drawCircle(68f, cy, 30f, hole)
            c.drawCircle(68f, cy, 30f, strokePaint(ColorUtils.blendARGB(p.paper, Color.BLACK, 0.08f), 3f))
            c.drawCircle(60f, cy - 7f, 20f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ColorUtils.blendARGB(p.paper, Color.BLACK, 0.34f) })
        }

        val vignette = Paint().apply {
            shader = RadialGradient(
                W / 2f, H / 2f, H * 0.72f,
                Color.TRANSPARENT, ColorUtils.setAlphaComponent(0xFF8A6D3B.toInt(), 46), Shader.TileMode.CLAMP
            )
        }
        c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), vignette)
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

    private fun tint(color: Int, amount: Float) = ColorUtils.blendARGB(color, Color.WHITE, 1f - amount)

    private fun Int.darker(factor: Float): Int = Color.rgb(
        (Color.red(this) * factor).toInt(), (Color.green(this) * factor).toInt(), (Color.blue(this) * factor).toInt()
    )

    private inline fun rotated(c: Canvas, deg: Float, px: Float, py: Float, block: () -> Unit) {
        c.save(); c.rotate(deg, px, py); block(); c.restore()
    }

    private fun text(
        c: Canvas, s: String, x: Float, baseline: Float, size: Float, color: Int, weight: Weight,
        rotate: Float = 0f, align: Paint.Align = Paint.Align.LEFT,
    ) {
        val paint = ShareCardRenderer.paint(color, size, weight, align = align)
        rotated(c, rotate, x, baseline) { c.drawText(s, x, baseline, paint) }
    }

    private fun highlight(
        c: Canvas, s: String, x: Float, baseline: Float, size: Float, ink: Int, marker: Int, weight: Weight,
        rotate: Float = 0f,
    ) {
        val paint = ShareCardRenderer.paint(ink, size, weight)
        val w = paint.measureText(s)
        rotated(c, rotate, x, baseline) {
            val top = baseline - size * 0.82f
            val bottom = baseline + size * 0.26f
            val path = Path().apply {
                moveTo(x - 14f, top + 6f)
                lineTo(x + w + 16f, top - 2f)
                lineTo(x + w + 8f, bottom)
                lineTo(x - 10f, bottom + 5f)
                close()
            }
            c.drawPath(path, fill(ColorUtils.setAlphaComponent(marker, 190)).apply { pathEffect = CornerPathEffect(10f) })
            c.drawText(s, x, baseline, paint)
        }
    }

    private fun star(c: Canvas, cx: Float, cy: Float, r: Float, color: Int, deg: Float) {
        val path = Path()
        for (i in 0 until 10) {
            val rad = if (i % 2 == 0) r else r * 0.46f
            val a = Math.toRadians((-90 + i * 36).toDouble())
            val x = cx + (rad * cos(a)).toFloat()
            val y = cy + (rad * sin(a)).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        rotated(c, deg, cx, cy) {
            c.drawPath(path, fill(color).apply { pathEffect = CornerPathEffect(r * 0.14f) })
            c.drawPath(path, strokePaint(ColorUtils.blendARGB(color, Color.BLACK, 0.18f), 2.5f).apply { pathEffect = CornerPathEffect(r * 0.14f) })
        }
    }

    private fun sparkle(c: Canvas, cx: Float, cy: Float, r: Float, color: Int) {
        val path = Path().apply {
            moveTo(cx, cy - r)
            quadTo(cx, cy, cx + r, cy)
            quadTo(cx, cy, cx, cy + r)
            quadTo(cx, cy, cx - r, cy)
            quadTo(cx, cy, cx, cy - r)
            close()
        }
        c.drawPath(path, fill(color))
    }

    private fun heart(c: Canvas, cx: Float, cy: Float, r: Float, color: Int) {
        val path = Path().apply {
            moveTo(cx, cy + r * 0.9f)
            cubicTo(cx - r * 1.6f, cy - r * 0.2f, cx - r * 0.7f, cy - r * 1.3f, cx, cy - r * 0.4f)
            cubicTo(cx + r * 0.7f, cy - r * 1.3f, cx + r * 1.6f, cy - r * 0.2f, cx, cy + r * 0.9f)
            close()
        }
        c.drawPath(path, fill(color))
        c.drawPath(path, strokePaint(ColorUtils.blendARGB(color, Color.BLACK, 0.2f), 2.5f))
    }

    private fun note(c: Canvas, cx: Float, cy: Float, size: Float, color: Int, deg: Float) {
        rotated(c, deg, cx, cy) {
            val head = RectF(cx - size * 0.34f, cy + size * 0.1f, cx + size * 0.2f, cy + size * 0.42f)
            c.save(); c.rotate(-20f, head.centerX(), head.centerY()); c.drawOval(head, fill(color)); c.restore()
            val stemX = head.right - 3f
            c.drawLine(stemX, head.centerY(), stemX, cy - size * 0.5f, strokePaint(color, size * 0.09f))
            val flag = Path().apply {
                moveTo(stemX, cy - size * 0.5f)
                cubicTo(stemX + size * 0.4f, cy - size * 0.42f, stemX + size * 0.45f, cy - size * 0.1f, stemX + size * 0.22f, cy + size * 0.04f)
            }
            c.drawPath(flag, strokePaint(color, size * 0.09f))
        }
    }

    private fun squiggle(c: Canvas, x1: Float, y: Float, x2: Float, amp: Float, color: Int, stroke: Float) {
        val path = Path().apply {
            moveTo(x1, y)
            var x = x1
            var up = true
            val step = 26f
            while (x < x2) {
                quadTo(x + step / 2f, y + if (up) -amp else amp, x + step, y)
                x += step
                up = !up
            }
        }
        c.drawPath(path, strokePaint(color, stroke))
    }

    private fun scribbleCircle(c: Canvas, r: RectF, color: Int, stroke: Float) {
        val paint = strokePaint(color, stroke)
        c.drawArc(r, 196f, 334f, false, paint)
        val r2 = RectF(r).apply { inset(-12f, -9f); offset(6f, 4f) }
        c.drawArc(r2, 150f, 170f, false, paint)
    }

    private fun dashedLine(c: Canvas, x1: Float, y: Float, x2: Float, color: Int) {
        c.drawLine(x1, y, x2, y, strokePaint(color, 3f).apply { pathEffect = DashPathEffect(floatArrayOf(16f, 14f), 0f) })
    }

    private fun tape(c: Canvas, cx: Float, cy: Float, w: Float, h: Float, deg: Float, color: Int) {
        rotated(c, deg, cx, cy) {
            val l = cx - w / 2f
            val r = cx + w / 2f
            val t = cy - h / 2f
            val b = cy + h / 2f
            val teeth = 6
            val path = Path().apply {
                moveTo(l, t)
                lineTo(r, t)
                for (k in 0..teeth) lineTo(r + if (k % 2 == 0) 0f else 8f, t + (b - t) * k / teeth)
                lineTo(l, b)
                for (k in teeth downTo 0) lineTo(l - if (k % 2 == 0) 0f else 8f, t + (b - t) * k / teeth)
                close()
            }
            c.drawPath(path, fill(ColorUtils.setAlphaComponent(color, 205)))
            c.save()
            c.clipPath(path)
            val stripe = strokePaint(ColorUtils.setAlphaComponent(Color.WHITE, 90), 9f)
            var x = l - h
            while (x < r + h) { c.drawLine(x, b, x + h, t, stripe); x += 30f }
            c.restore()
        }
    }

    private fun softShadow(c: Canvas, rect: RectF, radius: Float, alpha: Int = 70) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(alpha, 40, 30, 10)
            maskFilter = BlurMaskFilter(18f, BlurMaskFilter.Blur.NORMAL)
        }
        c.drawRoundRect(RectF(rect).apply { offset(5f, 10f) }, radius, radius, paint)
    }

    private fun stickyNote(c: Canvas, rect: RectF, color: Int, deg: Float, tapeColor: Int) {
        rotated(c, deg, rect.centerX(), rect.centerY()) {
            softShadow(c, rect, 6f)
            c.drawRoundRect(rect, 6f, 6f, fill(color))
            val fold = Path().apply {
                moveTo(rect.right, rect.bottom - 30f)
                lineTo(rect.right - 30f, rect.bottom)
                lineTo(rect.right, rect.bottom)
                close()
            }
            c.drawPath(fold, fill(ColorUtils.blendARGB(color, Color.BLACK, 0.12f)))
            tape(c, rect.left + 70f, rect.top + 2f, 96f, 30f, -6f, tapeColor)
        }
    }

    private fun polaroid(c: Canvas, bmp: Bitmap?, left: Float, top: Float, size: Float, deg: Float, p: Palette) {
        val pad = 18f
        val frame = RectF(left, top, left + size + pad * 2, top + size + pad * 2 + 52f)
        rotated(c, deg, frame.centerX(), frame.centerY()) {
            softShadow(c, frame, 8f, 90)
            c.drawRoundRect(frame, 8f, 8f, fill(Color.WHITE))
            val img = RectF(frame.left + pad, frame.top + pad, frame.left + pad + size, frame.top + pad + size)
            drawCrop(c, bmp, img, 0f, tint(p.accent, 0.4f), p.ink)

            squiggle(c, frame.left + pad + 8f, frame.bottom - 28f, frame.left + pad + 150f, 5f, p.sub, 4f)
            tape(c, frame.left + 28f, frame.top + 6f, 110f, 38f, -38f, p.accent2)
            tape(c, frame.right - 28f, frame.top + 6f, 110f, 38f, 38f, p.accent3)
        }
    }

    private fun stamp(c: Canvas, cx: Float, cy: Float, label: String, color: Int, ink: Int) {
        val paint = ShareCardRenderer.paint(ink, 38f, Weight.Bold, letterSpacing = 0.12f, align = Paint.Align.CENTER)
        val w = paint.measureText(label) + 84f
        val rect = RectF(cx - w / 2f, cy - 40f, cx + w / 2f, cy + 40f)
        rotated(c, -4f, cx, cy) {
            c.drawRoundRect(rect, 40f, 40f, fill(ColorUtils.setAlphaComponent(color, 70)))
            c.drawRoundRect(rect, 40f, 40f, strokePaint(color.darker(0.85f), 5f))
            c.drawRoundRect(RectF(rect).apply { inset(9f, 9f) }, 32f, 32f, strokePaint(color.darker(0.85f), 2.5f).apply {
                pathEffect = DashPathEffect(floatArrayOf(10f, 8f), 0f)
            })
            c.drawText(label, cx, cy + 13f, paint)
        }
    }

    private fun rankBadge(c: Canvas, cx: Float, cy: Float, rank: Int, p: Palette) {
        val colors = intArrayOf(p.accent2, p.accent3, p.accent, tint(p.accent2, 0.8f), tint(p.accent3, 0.8f))
        val color = colors[(rank - 1).coerceIn(0, 4)]
        val r = 52f
        softShadow(c, RectF(cx - r, cy - r, cx + r, cy + r), r, 60)
        c.drawCircle(cx, cy, r, fill(color))
        c.drawCircle(cx, cy, r, strokePaint(p.ink, 4f))
        c.drawCircle(cx, cy, r - 10f, strokePaint(Color.WHITE, 3f).apply { pathEffect = DashPathEffect(floatArrayOf(8f, 7f), 0f) })
        val paint = ShareCardRenderer.paint(p.ink, 60f, Weight.Bold, align = Paint.Align.CENTER)
        c.drawText(rank.toString(), cx, cy + 21f, paint)
        if (rank == 1) star(c, cx + 36f, cy - 46f, 22f, p.accent2, 15f)
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
