package com.caohaidang.edward.music.share

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Region
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.graphics.ColorUtils
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.caohaidang.edward.music.R
import com.caohaidang.edward.music.models.MediaMetadata
import com.caohaidang.edward.music.ui.utils.resize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class WrappedTexts(
    val brand: String,
    val periodLabel: String,
    val minutesUnit: String,
    val songsCount: String,
    val busiestDay: String?,
    val topArtistLine: String?,
    val topSongLabel: String,
    val topSongsTitle: String,
    val topArtistsTitle: String,
    val minutesText: (Long) -> String,
    val playsText: (Int) -> String,
)

data class WrappedImages(
    val topCover: Bitmap?,
    val songCovers: List<Bitmap?>,
    val artistAvatars: List<Bitmap?>,
)

object ShareCardRenderer {
    const val W = 1080
    const val H = 1920
    private const val PAD = 96f

    internal enum class Weight(val res: Int, val fallbackStyle: Int) {
        Regular(R.font.be_vietnam_pro_regular, Typeface.NORMAL),
        Medium(R.font.be_vietnam_pro_medium, Typeface.NORMAL),
        SemiBold(R.font.be_vietnam_pro_semibold, Typeface.BOLD),
        Bold(R.font.be_vietnam_pro_bold, Typeface.BOLD),
    }

    @Volatile
    private var typefaces: Map<Weight, Typeface> = emptyMap()

    @Volatile
    private var logo: Bitmap? = null

    internal fun loadFonts(context: Context) {
        if (typefaces.isNotEmpty()) return
        val app = context.applicationContext
        if (logo == null) {
            logo = runCatching { BitmapFactory.decodeResource(app.resources, R.drawable.logo_share) }.getOrNull()
        }
        typefaces = Weight.values().associateWith { w ->
            runCatching { ResourcesCompat.getFont(app, w.res) }.getOrNull()
                ?: Typeface.create(Typeface.DEFAULT, w.fallbackStyle)
        }
    }

    suspend fun loadBitmap(context: Context, url: String?, size: Int = 640): Bitmap? {
        if (url.isNullOrBlank()) return null
        return withContext(Dispatchers.IO) {
            runCatching {
                val request = ImageRequest.Builder(context)
                    .data(url.resize(size, size))
                    .allowHardware(false)
                    .size(size)
                    .build()
                val result = context.imageLoader.execute(request)
                (result as? SuccessResult)?.drawable?.toBitmap()
            }.getOrNull()
        }
    }

    fun renderLyrics(
        context: Context,
        song: MediaMetadata,
        lines: List<String>,
        theme: CardTheme,
        cover: Bitmap?,
        brand: String,
    ): Bitmap {
        loadFonts(context)
        val (bitmap, canvas) = newCard(theme, cover)
        val width = (W - PAD * 2).toInt()

        drawBrand(canvas, PAD, 210f, brand, size = 40f, textColor = theme.text, markBg = theme.text, markFg = theme.bottom)

        val text = lines.joinToString("\n")
        val areaTop = 430f
        val areaBottom = 1340f
        var size = 84f
        var layout = buildLayout(text, paint(theme.text, size, Weight.Bold), width, Layout.Alignment.ALIGN_NORMAL, spacing = 1.18f)
        while (layout.height > areaBottom - areaTop && size > 40f) {
            size -= 4f
            layout = buildLayout(text, paint(theme.text, size, Weight.Bold), width, Layout.Alignment.ALIGN_NORMAL, spacing = 1.18f)
        }
        // Even at the smallest size a long selection can still be taller than the card area
        // (it used to run over the song panel). Cut it with an ellipsis instead of overflowing.
        if (layout.height > areaBottom - areaTop) {
            val lineHeight = (layout.height.toFloat() / layout.lineCount).coerceAtLeast(1f)
            val fit = ((areaBottom - areaTop) / lineHeight).toInt().coerceAtLeast(1)
            layout = buildLayout(text, paint(theme.text, size, Weight.Bold), width, Layout.Alignment.ALIGN_NORMAL, maxLines = fit, spacing = 1.18f)
        }

        val y = areaTop + ((areaBottom - areaTop) - layout.height).coerceAtLeast(0f) / 2f

        canvas.drawText("\u201C", PAD - 6f, y + 68f, paint(ColorUtils.setAlphaComponent(theme.accent, 230), 240f, Weight.Bold))

        canvas.save()
        canvas.translate(PAD, y)
        layout.draw(canvas)
        canvas.restore()

        val panel = RectF(PAD, 1440f, W - PAD, 1610f)
        drawPanel(canvas, panel, 48f, theme)
        val coverRect = RectF(panel.left + 28f, panel.top + 28f, panel.left + 28f + 114f, panel.top + 28f + 114f)
        drawRoundedBitmap(canvas, cover, coverRect, 20f, theme)

        val markR = 32f
        val markCx = panel.right - 36f - markR
        drawLogoMark(canvas, markCx, panel.centerY(), markR, cardText(theme), if (theme.solidCard) Color.WHITE else theme.bottom)

        val tx = coverRect.right + 28f
        val tw = markCx - markR - 28f - tx
        val titlePaint = paint(cardText(theme), 42f, Weight.Bold)
        val artistPaint = paint(cardSub(theme), 34f, Weight.Regular)
        canvas.drawText(ellipsize(song.title, titlePaint, tw), tx, panel.top + 76f, titlePaint)
        canvas.drawText(ellipsize(song.artists.joinToString { it.name }, artistPaint, tw), tx, panel.top + 122f, artistPaint)
        return bitmap
    }

    /**
     * "Card" template: a translucent glass card centred on the blurred artwork background — big bold lyrics on top,
     * and a separately tinted bar underneath with artwork + title + artist + app mark.
     */
    fun renderLyricsCard(
        context: Context,
        song: MediaMetadata,
        lines: List<String>,
        theme: CardTheme,
        cover: Bitmap?,
        brand: String,
    ): Bitmap {
        loadFonts(context)
        val (bitmap, canvas) = newCard(theme, cover)

        // White card + dark text for light/solid themes, vivid artwork-coloured card + white text otherwise.
        val lightCard = theme.solidCard || ColorUtils.calculateLuminance(theme.text) < 0.4
        val textColor = if (lightCard) 0xFF111114.toInt() else Color.WHITE
        val subColor = if (lightCard) 0xFF6E6E76.toInt() else ColorUtils.setAlphaComponent(Color.WHITE, 190)

        val cardLeft = 80f
        val cardW = W - cardLeft * 2f
        val pad = 60f
        val textW = (cardW - pad * 2f).toInt()
        val metaH = 120f      // artwork size in the bottom bar
        val barPad = 36f      // vertical padding of the bottom bar around the artwork
        val barH = metaH + barPad * 2f
        val maxTextH = 1560f - pad * 2f - barH

        val text = lines.joinToString("\n")
        var size = 76f
        var layout = buildLayout(text, paint(textColor, size, Weight.Bold), textW, Layout.Alignment.ALIGN_NORMAL, spacing = 1.16f)
        while (layout.height > maxTextH && size > 40f) {
            size -= 4f
            layout = buildLayout(text, paint(textColor, size, Weight.Bold), textW, Layout.Alignment.ALIGN_NORMAL, spacing = 1.16f)
        }
        if (layout.height > maxTextH) {
            val lineHeight = (layout.height.toFloat() / layout.lineCount).coerceAtLeast(1f)
            val fit = (maxTextH / lineHeight).toInt().coerceAtLeast(1)
            layout = buildLayout(text, paint(textColor, size, Weight.Bold), textW, Layout.Alignment.ALIGN_NORMAL, maxLines = fit, spacing = 1.16f)
        }

        val lyricsH = pad + layout.height + pad
        val cardH = lyricsH + barH
        val top = ((H - cardH) / 2f - 40f).coerceAtLeast(160f)
        val card = RectF(cardLeft, top, cardLeft + cardW, top + cardH)
        val radius = 56f
        val lyricsRect = RectF(card.left, card.top, card.right, card.top + lyricsH)
        val barRect = RectF(card.left, lyricsRect.bottom, card.right, card.bottom)

        // "Liquid glass" card: very translucent, luminous body (the blurred artwork shines through), a soft
        // specular highlight in the top-left, glowing light-catching rim and a separately tinted info bar.
        drawSoftShadow(canvas, card, radius, blur = 90f, dy = 34f, alpha = 60, clipOut = true)
        val cardPath = Path().apply { addRoundRect(card, radius, radius, Path.Direction.CW) }
        canvas.save()
        canvas.clipPath(cardPath)

        // Body: light, frosted tint that fades top -> bottom, over a faint dark/white base for text contrast.
        canvas.drawRect(
            card,
            Paint().apply {
                color = if (lightCard) ColorUtils.setAlphaComponent(Color.WHITE, 96)
                else ColorUtils.setAlphaComponent(Color.BLACK, 34)
            }
        )
        canvas.drawRect(
            card,
            Paint().apply {
                shader = LinearGradient(
                    card.left, card.top, card.left, card.bottom,
                    ColorUtils.setAlphaComponent(Color.WHITE, if (lightCard) 90 else 58),
                    ColorUtils.setAlphaComponent(Color.WHITE, if (lightCard) 30 else 14),
                    Shader.TileMode.CLAMP
                )
            }
        )
        // Info bar: a separate, slightly deeper tint so it reads as its own pane.
        canvas.drawRect(
            barRect,
            Paint().apply {
                color = if (lightCard) ColorUtils.setAlphaComponent(Color.BLACK, 18)
                else ColorUtils.setAlphaComponent(Color.BLACK, 46)
            }
        )
        // Divider that fades out toward both ends.
        canvas.drawRect(
            barRect.left, barRect.top - 1f, barRect.right, barRect.top + 1.5f,
            Paint().apply {
                val c = if (lightCard) Color.BLACK else Color.WHITE
                val a = if (lightCard) 36 else 70
                shader = LinearGradient(
                    barRect.left, 0f, barRect.right, 0f,
                    intArrayOf(
                        ColorUtils.setAlphaComponent(c, 0),
                        ColorUtils.setAlphaComponent(c, a),
                        ColorUtils.setAlphaComponent(c, 0)
                    ),
                    floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
                )
            }
        )
        // Specular highlight blooming from the top-left corner.
        canvas.drawRect(
            card,
            Paint().apply {
                shader = RadialGradient(
                    card.left + card.width() * 0.12f, card.top - card.height() * 0.02f, card.width() * 0.85f,
                    ColorUtils.setAlphaComponent(Color.WHITE, if (lightCard) 150 else 92),
                    Color.TRANSPARENT, Shader.TileMode.CLAMP
                )
            }
        )
        // Soft inner glow just inside the rim (blurred stroke, clipped to the card).
        canvas.drawRoundRect(
            card, radius, radius,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 22f
                maskFilter = BlurMaskFilter(14f, BlurMaskFilter.Blur.NORMAL)
                color = ColorUtils.setAlphaComponent(Color.WHITE, if (lightCard) 110 else 48)
            }
        )
        canvas.restore()

        // Light-catching rim: bright at top-left and bottom-right, faint in between.
        canvas.drawRoundRect(
            card, radius, radius,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 3f
                shader = LinearGradient(
                    card.left, card.top, card.right, card.bottom,
                    intArrayOf(
                        ColorUtils.setAlphaComponent(Color.WHITE, 235),
                        ColorUtils.setAlphaComponent(Color.WHITE, 38),
                        ColorUtils.setAlphaComponent(Color.WHITE, 38),
                        ColorUtils.setAlphaComponent(Color.WHITE, 150)
                    ),
                    floatArrayOf(0f, 0.35f, 0.65f, 1f), Shader.TileMode.CLAMP
                )
            }
        )

        canvas.save()
        canvas.translate(card.left + pad, card.top + pad)
        layout.draw(canvas)
        canvas.restore()

        val metaTop = barRect.top + barPad
        val coverRect = RectF(card.left + pad, metaTop, card.left + pad + metaH, metaTop + metaH)
        drawRoundedBitmap(canvas, cover, coverRect, 22f, theme)

        val markR = 30f
        val markCx = card.right - pad - markR
        drawLogoMark(canvas, markCx, metaTop + metaH / 2f, markR, textColor, if (lightCard) Color.WHITE else theme.bottom)

        val tx = coverRect.right + 28f
        val tw = markCx - markR - 24f - tx
        val titlePaint = paint(textColor, 40f, Weight.Bold)
        val artistPaint = paint(subColor, 32f, Weight.Regular)
        canvas.drawText(ellipsize(song.title, titlePaint, tw), tx, metaTop + 52f, titlePaint)
        canvas.drawText(ellipsize(song.artists.joinToString { it.name }, artistPaint, tw), tx, metaTop + 94f, artistPaint)
        return bitmap
    }

    fun renderSong(
        context: Context,
        song: MediaMetadata,
        theme: CardTheme,
        cover: Bitmap?,
        brand: String,
    ): Bitmap {
        loadFonts(context)
        val (bitmap, canvas) = newCard(theme, cover)

        val cardW = 600f
        val inner = 30f
        val coverSize = cardW - inner * 2
        val titleSize = 48f
        val artistSize = 38f
        val brandSize = 32f

        val titleBase = inner + coverSize + 34f + titleSize * 0.95f
        val artistBase = titleBase + 16f + artistSize * 0.95f
        val brandBase = artistBase + 42f + brandSize * 0.8f
        val cardH = brandBase + inner

        val top = (910f - cardH / 2f).coerceAtLeast(240f)
        val card = RectF((W - cardW) / 2f, top, (W + cardW) / 2f, top + cardH)

        drawPanel(canvas, card, 52f, theme)

        val coverRect = RectF(card.left + inner, card.top + inner, card.left + inner + coverSize, card.top + inner + coverSize)
        drawSoftShadow(canvas, coverRect, 28f, blur = 30f, dy = 14f, alpha = 80, clipOut = false)
        drawRoundedBitmap(canvas, cover, coverRect, 28f, theme)

        val textW = cardW - inner * 2
        val titlePaint = paint(cardText(theme), titleSize, Weight.Bold)
        val artistPaint = paint(cardSub(theme), artistSize, Weight.Regular)
        canvas.drawText(ellipsize(song.title, titlePaint, textW), card.left + inner, card.top + titleBase, titlePaint)
        canvas.drawText(
            ellipsize(song.artists.joinToString { it.name }, artistPaint, textW),
            card.left + inner, card.top + artistBase, artistPaint
        )

        drawBrand(
            canvas, card.left + inner, card.top + brandBase, brand, size = brandSize,
            textColor = cardSub(theme), markBg = cardSub(theme), markFg = if (theme.solidCard) Color.WHITE else theme.bottom
        )
        return bitmap
    }

    fun renderWrappedOverview(
        context: Context,
        totalMinutes: Long,
        topSong: MediaMetadata?,
        topSongPlays: Int,
        songs: List<Pair<MediaMetadata, Int>>,
        texts: WrappedTexts,
        images: WrappedImages,
        theme: CardTheme,
    ): Bitmap = NotebookWrappedRenderer.overview(context, totalMinutes, topSong, topSongPlays, songs, texts, images, theme)

    fun renderWrappedSongs(
        context: Context,
        songs: List<Pair<MediaMetadata, Int>>,
        texts: WrappedTexts,
        images: WrappedImages,
        theme: CardTheme,
    ): Bitmap = NotebookWrappedRenderer.songs(context, songs, texts, images, theme)

    fun renderWrappedArtists(
        context: Context,
        artists: List<Pair<String, Long>>,
        texts: WrappedTexts,
        images: WrappedImages,
        theme: CardTheme,
    ): Bitmap = NotebookWrappedRenderer.artists(context, artists, texts, images, theme)

    private fun header(canvas: Canvas, texts: WrappedTexts, theme: CardTheme) {
        drawBrand(canvas, PAD, 160f, texts.brand, size = 40f, textColor = theme.text, markBg = theme.text, markFg = theme.bottom)
        canvas.drawText(texts.periodLabel, PAD, 238f, paint(theme.subtleText, 44f))
    }

    private fun drawRankRow(
        canvas: Canvas,
        theme: CardTheme,
        rank: Int,
        top: Float,
        image: Bitmap?,
        circle: Boolean,
        title: String,
        subtitle: String,
        trailing: String?,
    ) {
        val rowHeight = 200f
        val rankPaint = paint(if (rank == 1) theme.accent else theme.subtleText, if (rank == 1) 96f else 80f, Weight.Bold)
        canvas.drawText(rank.toString(), PAD, top + rowHeight / 2f + 30f, rankPaint)

        val imgRect = RectF(PAD + 100f, top, PAD + 100f + rowHeight, top + rowHeight)
        if (circle) drawCircleBitmap(canvas, image, imgRect, theme)
        else drawRoundedBitmap(canvas, image, imgRect, 28f, theme)

        val tx = imgRect.right + 36f
        val tw = W - PAD - tx
        val titlePaint = paint(theme.text, 50f, weight = Weight.Bold)
        canvas.drawText(ellipsize(title, titlePaint, tw), tx, top + 84f, titlePaint)
        val subPaint = paint(theme.subtleText, 40f, Weight.Regular)
        canvas.drawText(ellipsize(subtitle, subPaint, tw), tx, top + 140f, subPaint)
        if (trailing != null) {
            canvas.drawText(trailing, tx, top + 188f, paint(theme.accent, 36f, weight = Weight.Bold))
        }
    }

    private fun cardText(theme: CardTheme): Int = if (theme.solidCard) 0xFF111114.toInt() else theme.text
    private fun cardSub(theme: CardTheme): Int =
        if (theme.solidCard) 0xFF6E6E76.toInt() else theme.subtleText

    private fun drawPanel(canvas: Canvas, rect: RectF, radius: Float, theme: CardTheme) {
        if (theme.solidCard) {
            drawSoftShadow(canvas, rect, radius, blur = 70f, dy = 30f, alpha = 110, clipOut = true)
            canvas.drawRoundRect(rect, radius, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
        } else {
            val fillAlpha = if (theme.useBlur) 52 else 30
            canvas.drawRoundRect(
                rect, radius, radius,
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ColorUtils.setAlphaComponent(theme.text, fillAlpha) }
            )
            canvas.drawRoundRect(
                rect, radius, radius,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = 2.5f
                    color = ColorUtils.setAlphaComponent(theme.text, 60)
                }
            )
        }
    }

    private fun drawSoftShadow(canvas: Canvas, rect: RectF, radius: Float, blur: Float, dy: Float, alpha: Int, clipOut: Boolean) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(alpha, 0, 0, 0)
            maskFilter = BlurMaskFilter(blur, BlurMaskFilter.Blur.NORMAL)
        }
        canvas.save()
        if (clipOut) {
            canvas.clipPath(Path().apply { addRoundRect(rect, radius, radius, Path.Direction.CW) }, Region.Op.DIFFERENCE)
        }
        canvas.drawRoundRect(RectF(rect).apply { offset(0f, dy) }, radius, radius, paint)
        canvas.restore()
    }

    private fun drawPlayTriangle(canvas: Canvas, cx: Float, cy: Float, size: Float, color: Int) {
        val path = Path().apply {
            moveTo(cx - size * 0.32f, cy - size * 0.5f)
            lineTo(cx - size * 0.32f, cy + size * 0.5f)
            lineTo(cx + size * 0.52f, cy)
            close()
        }
        canvas.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color })
    }

    internal fun drawLogoMark(canvas: Canvas, cx: Float, cy: Float, radius: Float, bg: Int, fg: Int) {
        val img = logo
        if (img != null) {
            val box = RectF(cx - radius, cy - radius, cx + radius, cy + radius)
            val scale = minOf(box.width() / img.width, box.height() / img.height)
            val w = img.width * scale
            val h = img.height * scale
            val dst = RectF(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f)
            canvas.drawBitmap(img, null, dst, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
            return
        }
        canvas.drawCircle(cx, cy, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bg })
        drawPlayTriangle(canvas, cx, cy, radius * 0.9f, fg)
    }

    private fun drawBrand(
        canvas: Canvas, x: Float, baseline: Float, brand: String, size: Float,
        textColor: Int, markBg: Int, markFg: Int,
    ) {
        val r = size * 0.56f
        drawLogoMark(canvas, x + r, baseline - size * 0.34f, r, markBg, markFg)
        canvas.drawText(brand, x + r * 2f + 16f, baseline, paint(textColor, size, Weight.SemiBold))
    }

    private fun newCard(theme: CardTheme, cover: Bitmap?): Pair<Bitmap, Canvas> {
        val bitmap = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val bg = Paint().apply {
            shader = LinearGradient(0f, 0f, 0f, H.toFloat(), theme.top, theme.bottom, Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, W.toFloat(), H.toFloat(), bg)

        if (theme.useBlur && cover != null) {
            drawBlurredCover(canvas, cover)

            val scrim = Paint().apply {
                shader = LinearGradient(
                    0f, 0f, 0f, H.toFloat(),
                    ColorUtils.setAlphaComponent(theme.bottom, 70),
                    ColorUtils.setAlphaComponent(theme.bottom, 190),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(0f, 0f, W.toFloat(), H.toFloat(), scrim)
        } else {
            val glow = Paint().apply {
                shader = RadialGradient(
                    W * 0.95f, H * 0.04f, W * 0.8f,
                    ColorUtils.setAlphaComponent(theme.accent, 70), Color.TRANSPARENT, Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(0f, 0f, W.toFloat(), H.toFloat(), glow)
        }
        return bitmap to canvas
    }

    private fun drawBlurredCover(canvas: Canvas, cover: Bitmap) {
        val side = minOf(cover.width, cover.height)
        var small = Bitmap.createBitmap(cover, (cover.width - side) / 2, (cover.height - side) / 2, side, side)
        while (small.width > 12) {
            val next = maxOf(12, small.width / 3)
            small = Bitmap.createScaledBitmap(small, next, next, true)
        }
        var big = small
        for (target in intArrayOf(48, 192, 480)) big = Bitmap.createScaledBitmap(big, target, target, true)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(1.5f) })
        }

        canvas.drawBitmap(big, null, RectF((W - H) / 2f, 0f, (W + H) / 2f, H.toFloat()), paint)
    }

    private fun drawRoundedBitmap(canvas: Canvas, bmp: Bitmap?, rect: RectF, radius: Float, theme: CardTheme) {
        val path = Path().apply { addRoundRect(rect, radius, radius, Path.Direction.CW) }
        clipDraw(canvas, bmp, rect, path, theme)
    }

    private fun drawCircleBitmap(canvas: Canvas, bmp: Bitmap?, rect: RectF, theme: CardTheme) {
        val path = Path().apply { addOval(rect, Path.Direction.CW) }
        clipDraw(canvas, bmp, rect, path, theme)
    }

    private fun clipDraw(canvas: Canvas, bmp: Bitmap?, rect: RectF, path: Path, theme: CardTheme) {
        canvas.save()
        canvas.clipPath(path)
        if (bmp != null) {
            val side = minOf(bmp.width, bmp.height)
            val src = Rect((bmp.width - side) / 2, (bmp.height - side) / 2, (bmp.width + side) / 2, (bmp.height + side) / 2)
            canvas.drawBitmap(bmp, src, rect, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        } else {
            canvas.drawRect(rect, Paint().apply { color = theme.panel })

            val note = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = theme.subtleText
                textSize = rect.width() * 0.4f
                typeface = Typeface.DEFAULT_BOLD
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("♪", rect.centerX(), rect.centerY() + rect.width() * 0.14f, note)
        }
        canvas.restore()
    }

    internal fun paint(
        color: Int,
        size: Float,
        weight: Weight = Weight.Regular,
        letterSpacing: Float = 0f,
        align: Paint.Align = Paint.Align.LEFT,
    ) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        textSize = size
        typeface = typefaces[weight] ?: Typeface.DEFAULT
        this.letterSpacing = letterSpacing
        textAlign = align
    }

    internal fun ellipsize(text: String, paint: TextPaint, maxWidth: Float): String =
        TextUtils.ellipsize(text, paint, maxWidth, TextUtils.TruncateAt.END).toString()

    internal fun buildLayout(
        text: String,
        paint: TextPaint,
        width: Int,
        alignment: Layout.Alignment,
        maxLines: Int = Int.MAX_VALUE,
        spacing: Float = 1.12f,
    ): StaticLayout {
        paint.textAlign = Paint.Align.LEFT
        return StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(alignment)
            .setLineSpacing(0f, spacing)
            .setIncludePad(false)
            .setMaxLines(maxLines)
            .setEllipsize(TextUtils.TruncateAt.END)
            .build()
    }

    internal fun formatNumber(value: Long): String = java.text.NumberFormat.getInstance().format(value)
}
