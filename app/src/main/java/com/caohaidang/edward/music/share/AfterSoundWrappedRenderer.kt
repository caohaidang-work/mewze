package com.caohaidang.edward.music.share

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import com.caohaidang.edward.music.R
import com.caohaidang.edward.music.models.MediaMetadata
import com.caohaidang.edward.music.share.ShareCardRenderer.Weight
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Chữ hiển thị trên 2 thẻ "The After Sound". */
data class AfterSoundTexts(
    /** Dòng tiêu đề trang bài hát, ví dụ "your week in music". */
    val header: String,
    /** Khoảng thời gian in trên miếng băng dính xanh. */
    val dateLabel: String,
    val minutes: (Long) -> String,
    val plays: (Int) -> String,
)

/**
 * Wrapped phong cách "The After Sound": khung điện thoại, sticker, sổ tay kẻ ô.
 *
 * Phần trang trí là ảnh nền có sẵn trong `res/drawable-nodpi` (đã xoá hết chữ/số/ảnh động);
 * renderer chỉ vẽ nội dung động lên trên. Mọi toạ độ bên dưới đo trên khung thiết kế
 * 942 x 1672, rồi canvas được scale lên 1080 x 1920 như các thẻ Wrapped khác.
 *
 * Ô ảnh #1 trong ảnh nền để trống (trong suốt): ảnh được vẽ TRƯỚC, ảnh nền vẽ SAU,
 * nhờ vậy ngôi sao dán đè lên góc khung vẫn nằm phía trước ảnh như thiết kế gốc.
 */
internal object AfterSoundRenderer {
    private const val W = 1080
    private const val H = 1920
    private const val DW = 942f
    private const val DH = 1672f

    private val INK = 0xFF121212.toInt()
    private val SUB = 0xFF4A4A52.toInt()
    private val TAPE_INK = 0xFF46505C.toInt()
    private val LABEL_INK = 0xFF19191F.toInt()
    private val PLACEHOLDER_BG = 0xFFE4DCEC.toInt()
    private val PLACEHOLDER_FG = 0xFF8A8296.toInt()

    // Ô ảnh #1 (bên trong khung viền đen)
    private val photoRect = RectF(172f, 555f, 437f, 820f)
    private const val PHOTO_RADIUS = 12f

    // 4 hàng của cuốn sổ (hạng 2..5). Sổ nghiêng ~ -8°.
    private const val PILL_ANGLE = -8.1f
    private val pillCenters = arrayOf(
        floatArrayOf(454.3f, 951.7f),
        floatArrayOf(470.3f, 1061.3f),
        floatArrayOf(488.8f, 1173.4f),
        floatArrayOf(504.9f, 1287.8f),
    )
    private const val THUMB_ANGLE = -7.5f
    private const val THUMB_HALF = 37f
    private val thumbCenters = arrayOf(
        floatArrayOf(206.3f, 984.0f),
        floatArrayOf(225.0f, 1096.7f),
        floatArrayOf(240.7f, 1213.7f),
        floatArrayOf(256.9f, 1326.6f),
    )

    /** Điểm bắt đầu (trái, giữa chiều cao chữ) của nhãn "N minutes / N plays" bên phải mỗi hàng. */
    private val labelAnchors = arrayOf(
        floatArrayOf(679.5f, 914.5f),
        floatArrayOf(696.8f, 1031.5f),
        floatArrayOf(710.5f, 1144.5f),
        floatArrayOf(728.2f, 1258.3f),
    )

    // ───────────────────────── Trang nghệ sĩ ─────────────────────────

    fun artists(
        context: Context,
        artists: List<Pair<String, Long>>,
        playCount: Int,
        texts: AfterSoundTexts,
        images: WrappedImages,
    ): Bitmap = page(context, R.drawable.wrapped_after_sound_artists, images.artistAvatars.firstOrNull()) { c ->
        dateTag(c, texts.dateLabel)

        val top = artists.firstOrNull()
        if (top != null) {
            text(c, top.first, 467f, 702f, 36f, INK, Weight.Bold, maxW = 333f)
            text(c, texts.minutes(top.second / 60_000), 538f, 763f, 36f, INK, Weight.Regular, maxW = 262f)
        }

        val rows = artists.drop(1).take(4)
        rows.forEachIndexed { i, (name, playTime) ->
            thumb(c, images.artistAvatars.getOrNull(i + 1), i)
            val p = pillPoint(i, -8f, 12.5f)
            text(c, name, p[0], p[1], 36f, INK, Weight.Bold, maxW = 290f, align = Paint.Align.CENTER, deg = PILL_ANGLE)
            label(c, i, texts.minutes(playTime / 60_000))
        }

        number(c, ShareCardRenderer.formatNumber(playCount.toLong()), 605.8f, 1526.2f, 3.2f, glyphH = 72f, maxW = 130f)
    }

    // ───────────────────────── Trang bài hát ─────────────────────────

    fun songs(
        context: Context,
        songs: List<Pair<MediaMetadata, Int>>,
        totalMinutes: Long,
        songCount: Int,
        texts: AfterSoundTexts,
        images: WrappedImages,
    ): Bitmap = page(context, R.drawable.wrapped_after_sound_songs, images.topCover ?: images.songCovers.firstOrNull()) { c ->
        // Tiêu đề căn giữa, chữ có chân như thiết kế gốc
        val head = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF23232A.toInt()
            textSize = 33f
            typeface = Typeface.SERIF
            textAlign = Paint.Align.CENTER
        }
        while (head.measureText(texts.header) > 440f && head.textSize > 18f) head.textSize -= 1f
        c.drawText(texts.header, 470.5f, 191f, head)

        dateTag(c, texts.dateLabel)

        val top = songs.firstOrNull()
        if (top != null) {
            val (song, plays) = top
            text(c, song.title, 494f, 688f, 36f, INK, Weight.Bold, maxW = 310f)
            text(c, song.artists.joinToString { it.name }, 496f, 717f, 22f, SUB, Weight.Regular, maxW = 300f)
            text(c, texts.plays(plays), 562f, 782f, 38f, INK, Weight.Regular, maxW = 240f)
        }

        songs.drop(1).take(4).forEachIndexed { i, (song, plays) ->
            thumb(c, images.songCovers.getOrNull(i + 1), i)
            val t = pillPoint(i, -155f, -4f)
            text(c, song.title, t[0], t[1], 29f, INK, Weight.Bold, maxW = 300f, deg = PILL_ANGLE)
            val a = pillPoint(i, -155f, 20f)
            text(
                c, song.artists.joinToString { it.name }, a[0], a[1], 20f, SUB, Weight.Regular,
                maxW = 300f, deg = PILL_ANGLE,
            )
            label(c, i, texts.plays(plays))
        }

        number(c, ShareCardRenderer.formatNumber(totalMinutes), 678.3f, 1452f, 9.5f, glyphH = 64f, maxW = 112f)
        number(c, ShareCardRenderer.formatNumber(songCount.toLong()), 484.9f, 1584f, 9.2f, glyphH = 64f, maxW = 112f)
    }

    // ───────────────────────── Khung chung ─────────────────────────

    private inline fun page(
        context: Context,
        templateRes: Int,
        photo: Bitmap?,
        draw: (Canvas) -> Unit,
    ): Bitmap {
        ShareCardRenderer.loadFonts(context)
        val bitmap = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bitmap)
        c.drawColor(Color.WHITE)
        c.scale(W / DW, H / DH)

        // 1) ảnh #1 nằm dưới, 2) ảnh nền (có lỗ trong suốt ở ô ảnh) nằm trên
        drawPhoto(c, photo)
        val opts = BitmapFactory.Options().apply { inScaled = false }
        val template = runCatching {
            BitmapFactory.decodeResource(context.applicationContext.resources, templateRes, opts)
        }.getOrNull()
        if (template != null) {
            c.drawBitmap(
                template, null, RectF(0f, 0f, DW, DH),
                Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            )
            template.recycle()
        }

        draw(c)
        return bitmap
    }

    private fun drawPhoto(c: Canvas, bmp: Bitmap?) {
        c.save()
        c.clipPath(Path().apply { addRoundRect(photoRect, PHOTO_RADIUS, PHOTO_RADIUS, Path.Direction.CW) })
        if (bmp != null) {
            c.drawBitmap(bmp, cropSrc(bmp), photoRect, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        } else {
            placeholder(c, photoRect)
        }
        c.restore()
    }

    private fun thumb(c: Canvas, bmp: Bitmap?, row: Int) {
        val (cx, cy) = thumbCenters[row].let { it[0] to it[1] }
        val rect = RectF(cx - THUMB_HALF, cy - THUMB_HALF, cx + THUMB_HALF, cy + THUMB_HALF)
        c.save()
        c.rotate(THUMB_ANGLE, cx, cy)
        c.clipPath(Path().apply { addRoundRect(rect, 3f, 3f, Path.Direction.CW) })
        if (bmp != null) {
            c.drawBitmap(bmp, cropSrc(bmp), rect, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        } else {
            placeholder(c, rect)
        }
        c.restore()
    }

    private fun placeholder(c: Canvas, rect: RectF) {
        c.drawRect(rect, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = PLACEHOLDER_BG })
        val note = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = PLACEHOLDER_FG
            textSize = rect.width() * 0.45f
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }
        c.drawText("♪", rect.centerX(), rect.centerY() + rect.width() * 0.16f, note)
    }

    private fun cropSrc(bmp: Bitmap): Rect {
        val side = min(bmp.width, bmp.height)
        return Rect((bmp.width - side) / 2, (bmp.height - side) / 2, (bmp.width + side) / 2, (bmp.height + side) / 2)
    }

    // ───────────────────────── Chữ ─────────────────────────

    /** Ngày trên miếng băng dính xanh, căn giữa tại x = 620. */
    private fun dateTag(c: Canvas, s: String) {
        text(c, s, 620f, 491f, 21f, TAPE_INK, Weight.Regular, maxW = 212f, align = Paint.Align.CENTER, minSize = 14f)
    }

    /** Nhãn "N minutes / N plays" kèm biểu tượng ♪ nằm bên phải pill, nghiêng theo cuốn sổ. */
    private fun label(c: Canvas, row: Int, s: String) {
        val a = labelAnchors[row]
        text(c, s, a[0], a[1] + 7f, 18.5f, LABEL_INK, Weight.Regular, maxW = 84f, deg = PILL_ANGLE, minSize = 13f)
    }

    /** Đổi toạ độ cục bộ của pill (trục x dọc theo pill, trục y hướng xuống) sang toạ độ thiết kế. */
    private fun pillPoint(row: Int, lx: Float, ly: Float): FloatArray {
        val r = Math.toRadians(PILL_ANGLE.toDouble())
        val cs = cos(r).toFloat()
        val sn = sin(r).toFloat()
        val p = pillCenters[row]
        return floatArrayOf(p[0] + cs * lx - sn * ly, p[1] + sn * lx + cs * ly)
    }

    /**
     * Vẽ chữ tại (x, baseline), tự thu nhỏ cho vừa [maxW] rồi mới cắt bớt bằng "…".
     * [deg]: góc xoay quanh (x, baseline), dương = theo chiều kim đồng hồ.
     */
    private fun text(
        c: Canvas,
        s: String,
        x: Float,
        baseline: Float,
        size: Float,
        color: Int,
        weight: Weight,
        maxW: Float = Float.MAX_VALUE,
        align: Paint.Align = Paint.Align.LEFT,
        deg: Float = 0f,
        minSize: Float = 18f,
    ) {
        if (s.isEmpty()) return
        val paint = ShareCardRenderer.paint(color, size, weight, align = align)
        while (paint.measureText(s) > maxW && paint.textSize > minSize) paint.textSize -= 1f
        val shown = if (paint.measureText(s) > maxW) ShareCardRenderer.ellipsize(s, paint, maxW) else s
        c.save()
        c.translate(x, baseline)
        if (deg != 0f) c.rotate(deg)
        c.drawText(shown, 0f, 0f, paint)
        c.restore()
    }

    /**
     * Số lớn trên tấm vé: chữ trắng viền đen, đổ bóng đen lệch xuống phải, hơi nén ngang.
     * (cx, cy) là tâm hộp chữ số, [glyphH] là chiều cao chữ số, [maxW] là bề ngang tối đa.
     */
    private fun number(c: Canvas, s: String, cx: Float, cy: Float, deg: Float, glyphH: Float, maxW: Float) {
        if (s.isEmpty()) return
        val size = glyphH / 0.74f // chữ số của Be Vietnam Pro cao ~0.74 em
        val base = ShareCardRenderer.paint(Color.WHITE, size, Weight.Bold, align = Paint.Align.CENTER)
        val natural = base.measureText(s)
        base.textScaleX = min(0.67f, maxW / natural)
        base.textSkewX = -0.08f

        val outline = TextPaint(base).apply {
            style = Paint.Style.STROKE
            strokeWidth = 8f
            strokeJoin = Paint.Join.ROUND
            color = Color.BLACK
        }
        val shadow = TextPaint(base).apply { color = Color.BLACK }
        val shadowStroke = TextPaint(outline)

        c.save()
        c.translate(cx, cy + glyphH / 2f)
        c.rotate(deg)
        c.drawText(s, 4f, 5f, shadow)
        c.drawText(s, 4f, 5f, shadowStroke)
        c.drawText(s, 0f, 0f, outline)
        c.drawText(s, 0f, 0f, base)
        c.restore()
    }
}
