package com.caohaidang.edward.music.ui.player

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import coil.compose.AsyncImage
import com.caohaidang.edward.music.share.ShareCardRenderer
import com.caohaidang.edward.music.ui.utils.resize
import com.google.material.color.score.Score
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

private const val SPEED_PLAYING = 1f
private const val SPEED_PAUSED = 0.2f

private class CoverColors(val base: Color, val blobs: List<Color>)

private val DEFAULT_COLORS = CoverColors(
    base = Color(0xFF1C2230),
    blobs = listOf(Color(0xFF34406B), Color(0xFF4B3A6B), Color(0xFF2E5A6B)),
)

@Composable
fun PlayerBackdrop(
    coverUrl: String?,
    isPlaying: Boolean,
    animate: Boolean,
    motion: Boolean,
    landscape: Boolean,
    showLyrics: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val target by produceState(initialValue = DEFAULT_COLORS, coverUrl) {
        value = coverUrl
            ?.let { ShareCardRenderer.loadBitmap(context, it, 96) }
            ?.let { runCatching { it.toCoverColors() }.getOrNull() }
            ?: DEFAULT_COLORS
    }
    val colorAnim = tween<Color>(1000)
    val base by animateColorAsState(target.base, colorAnim, label = "backdropBase")
    val b0 by animateColorAsState(target.blobs[0], colorAnim, label = "blob0")
    val b1 by animateColorAsState(target.blobs[1], colorAnim, label = "blob1")
    val b2 by animateColorAsState(target.blobs[2], colorAnim, label = "blob2")

    val speed by animateFloatAsState(if (isPlaying) SPEED_PLAYING else SPEED_PAUSED, tween(900), label = "motionSpeed")
    val speedNow by rememberUpdatedState(speed)
    val phase = remember { mutableFloatStateOf(0f) }
    if (motion && animate) {
        LaunchedEffect(Unit) {
            var last = 0L
            while (true) {
                withFrameNanos { t ->
                    if (last != 0L) phase.floatValue += (t - last) / 1_000_000_000f * speedNow
                    last = t
                }
            }
        }
    }

    val coverAlpha by animateFloatAsState(if (showLyrics) 0.18f else 1f, tween(500), label = "coverAlpha")

    BoxWithConstraints(modifier.background(base)) {
        val coverW = if (landscape) maxWidth / 2 else maxWidth
        val coverH = if (landscape) maxHeight else minOf(maxWidth * 4f / 3f, maxHeight * 0.64f)

        Box(
            Modifier
                .width(coverW)
                .height(coverH)
                .clipToBounds()
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = coverAlpha

                        val p = phase.floatValue
                        val s = 1.12f + 0.04f * sin(p * 0.15f)
                        scaleX = s
                        scaleY = s
                        translationX = sin(p * 0.11f) * size.width * 0.02f
                        translationY = cos(p * 0.09f) * size.height * 0.015f
                    }
            ) {
                Crossfade(targetState = coverUrl, animationSpec = tween(500), label = "coverCrossfade") { url ->
                    AsyncImage(
                        model = url?.resize(1080, 1080),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        alignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        if (landscape) Brush.horizontalGradient(
                            0.55f to base.copy(alpha = 0f), 1f to base
                        ) else Brush.verticalGradient(
                            0.45f to base.copy(alpha = 0f), 0.8f to base.copy(alpha = 0.85f), 1f to base
                        )
                    )
            )

            if (!landscape) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(96.dp)
                        .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.35f), Color.Transparent)))
                )
            }
        }

        if (motion) {
            Canvas(Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val p = phase.floatValue
                val cw = coverW.toPx()
                val ch = coverH.toPx()
                val radius = max(cw, ch) * 0.5f
                val colors = listOf(b0, b1, b2)

                val spec = arrayOf(
                    floatArrayOf(0.21f, 0.17f, 0.0f, 1.3f),
                    floatArrayOf(0.15f, 0.24f, 2.1f, 0.4f),
                    floatArrayOf(0.27f, 0.13f, 4.0f, 3.1f),
                )
                for (i in 0 until 3) {
                    val sp = spec[i]
                    val ox = sin(p * sp[0] + sp[2])
                    val oy = cos(p * sp[1] + sp[3])
                    val center = if (landscape) {
                        Offset(cw * (0.95f + 0.12f * ox), h * (0.2f + 0.3f * i + 0.12f * oy))
                    } else {
                        Offset(w * (0.2f + 0.3f * i + 0.15f * ox), ch * (0.93f + 0.08f * oy))
                    }
                    drawCircle(
                        brush = Brush.radialGradient(
                            0f to colors[i].copy(alpha = 0.55f),
                            1f to colors[i].copy(alpha = 0f),
                            center = center,
                            radius = radius,
                        ),
                        radius = radius,
                        center = center,
                    )
                }
            }
        }
    }
}

private fun android.graphics.Bitmap.toCoverColors(): CoverColors {
    val pal = Palette.from(this).maximumColorCount(16).generate()
    val ranked = Score.score(pal.swatches.associate { it.rgb to it.population }, 4)
    val main = ranked.firstOrNull() ?: pal.dominantSwatch?.rgb ?: return DEFAULT_COLORS

    val baseHsl = FloatArray(3).also { ColorUtils.colorToHSL(main, it) }
    baseHsl[1] = (baseHsl[1] * 0.55f).coerceIn(0.10f, 0.40f)
    baseHsl[2] = 0.17f
    val base = Color(ColorUtils.HSLToColor(baseHsl))

    val picks = buildList {
        listOf(pal.vibrantSwatch, pal.mutedSwatch, pal.lightVibrantSwatch).forEach { it?.rgb?.let(::add) }
        addAll(ranked)
    }.distinct()
    val lightness = floatArrayOf(0.40f, 0.34f, 0.44f)
    val blobs = List(3) { i ->
        var argb = picks.getOrNull(i) ?: main
        if (i >= picks.size) {
            val hsl = FloatArray(3).also { ColorUtils.colorToHSL(argb, it) }
            hsl[0] = (hsl[0] + 22f * (i - picks.size + 1)) % 360f
            argb = ColorUtils.HSLToColor(hsl)
        }
        val hsl = FloatArray(3).also { ColorUtils.colorToHSL(argb, it) }
        hsl[1] = hsl[1].coerceIn(0.35f, 0.8f)
        hsl[2] = lightness[i]
        Color(ColorUtils.HSLToColor(hsl))
    }
    return CoverColors(base, blobs)
}
