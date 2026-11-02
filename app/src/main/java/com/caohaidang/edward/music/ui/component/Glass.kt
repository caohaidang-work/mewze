package com.caohaidang.edward.music.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun Modifier.glassSurface(
    shape: Shape,
    tintAlpha: Float = 0.72f,
    shadowElevation: Dp = 12.dp,
    opaque: Boolean = false,
): Modifier {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.surface.luminance() < 0.5f

    val base = if (opaque) scheme.surfaceContainerHigh.copy(alpha = 0.97f)
    else scheme.surfaceContainerLowest.copy(alpha = (tintAlpha + 0.12f).coerceAtMost(0.94f))
    val colorWash = Brush.verticalGradient(
        listOf(
            Color.White.copy(alpha = if (dark) 0.04f else 0.0f),
            Color.Transparent,
        )
    )

    val sheen = Brush.verticalGradient(
        listOf(
            Color.White.copy(alpha = if (dark) 0.07f else 0.30f),
            Color.Transparent,
        )
    )

    val rim = Brush.verticalGradient(
        listOf(
            Color.White.copy(alpha = if (opaque) (if (dark) 0.26f else 0.9f) else (if (dark) 0.18f else 0.8f)),
            Color.White.copy(alpha = if (dark) 0.06f else 0.25f),
        )
    )

    return this
        .shadow(
            elevation = shadowElevation,
            shape = shape,
            clip = false,
            ambientColor = Color.Black.copy(alpha = if (opaque) 0.5f else 0.30f),
            spotColor = Color.Black.copy(alpha = if (opaque) 0.5f else 0.30f),
        )
        .clip(shape)
        .background(base)
        .background(colorWash)
        .background(sheen)
        .border(0.8.dp, rim, shape)
}

@Composable
fun Modifier.glassCard(
    shape: Shape,
    tintAlpha: Float = 0.50f,
): Modifier {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.surface.luminance() < 0.5f

    val tint = scheme.surfaceContainerHigh.copy(alpha = tintAlpha)
    val sheen = Brush.verticalGradient(
        listOf(
            Color.White.copy(alpha = if (dark) 0.10f else 0.40f),
            Color.White.copy(alpha = if (dark) 0.02f else 0.08f),
        )
    )
    val rim = Brush.linearGradient(
        listOf(
            Color.White.copy(alpha = if (dark) 0.30f else 0.85f),
            Color.White.copy(alpha = if (dark) 0.04f else 0.15f),
            Color.White.copy(alpha = if (dark) 0.16f else 0.55f),
        )
    )

    return this
        .clip(shape)
        .background(tint)
        .background(sheen)
        .border(1.dp, rim, shape)
}

@Composable
fun Modifier.glassAmbient(): Modifier {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.surface.luminance() < 0.5f
    val a = if (dark) 0.30f else 0.26f
    val c1 = scheme.primary
    val c2 = scheme.tertiary
    val c3 = scheme.secondary

    return this.drawBehind {
        val r = size.maxDimension
        drawRect(
            Brush.radialGradient(
                colors = listOf(c1.copy(alpha = a), Color.Transparent),
                center = Offset(size.width * 0.05f, size.height * 0.04f),
                radius = r * 0.62f
            )
        )
        drawRect(
            Brush.radialGradient(
                colors = listOf(c2.copy(alpha = a * 0.8f), Color.Transparent),
                center = Offset(size.width * 1.0f, size.height * 0.42f),
                radius = r * 0.55f
            )
        )
        drawRect(
            Brush.radialGradient(
                colors = listOf(c3.copy(alpha = a * 0.7f), Color.Transparent),
                center = Offset(size.width * 0.1f, size.height * 0.98f),
                radius = r * 0.6f
            )
        )
    }
}

/** Set to true on screens with a dark backdrop (e.g. the immersive artist page) regardless of the app theme. */
val LocalGlassDark = compositionLocalOf<Boolean?> { null }

/**
 * "Liquid glass" panel: a translucent tinted body with a soft specular highlight in the top-left corner,
 * light pooling along the bottom edge and a bright rim that fades through the middle and comes back
 * at the opposite corner. Looks best on top of [liquidBackdrop] or a cover image.
 */
@Composable
fun Modifier.liquidGlass(
    shape: Shape,
    tint: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    tintAlpha: Float = 0.45f,
    elevation: Dp = 8.dp,
): Modifier {
    val dark = LocalGlassDark.current ?: (MaterialTheme.colorScheme.surface.luminance() < 0.5f)
    val rim = Brush.linearGradient(
        0f to Color.White.copy(alpha = if (dark) 0.60f else 0.95f),
        0.38f to Color.White.copy(alpha = if (dark) 0.05f else 0.18f),
        0.62f to Color.White.copy(alpha = if (dark) 0.05f else 0.18f),
        1f to Color.White.copy(alpha = if (dark) 0.38f else 0.75f),
    )
    val highlight = Color.White.copy(alpha = if (dark) 0.22f else 0.55f)
    val pool = Color.White.copy(alpha = if (dark) 0.06f else 0.16f)

    return this
        .then(
            if (elevation > 0.dp) {
                Modifier.shadow(
                    elevation = elevation,
                    shape = shape,
                    clip = false,
                    ambientColor = tint.copy(alpha = 0.40f),
                    spotColor = Color.Black.copy(alpha = if (dark) 0.35f else 0.14f),
                )
            } else Modifier
        )
        .clip(shape)
        .background(tint.copy(alpha = tintAlpha))
        .drawBehind {
            drawRect(
                Brush.radialGradient(
                    colors = listOf(highlight, Color.Transparent),
                    center = Offset(size.width * 0.15f, 0f),
                    radius = size.maxDimension * 0.75f
                )
            )
            drawRect(
                Brush.verticalGradient(
                    colors = listOf(Color.Transparent, pool),
                    startY = size.height * 0.55f,
                    endY = size.height
                )
            )
        }
        .border(1.dp, rim, shape)
}

/** Soft colour blobs for a screen background, so the glass panels above it have something to refract. */
@Composable
fun Modifier.liquidBackdrop(): Modifier {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.surface.luminance() < 0.5f
    val a = if (dark) 0.34f else 0.55f
    val pink = scheme.primaryContainer
    val lavender = if (dark) Color(0xFF6E52C8) else Color(0xFFC9B8FF)
    val mint = if (dark) Color(0xFF2E8F69) else Color(0xFFA6E8C9)
    val peach = if (dark) Color(0xFFC77848) else Color(0xFFFFC9A8)

    return this.drawBehind {
        val r = size.maxDimension
        fun blob(color: Color, x: Float, y: Float, radius: Float) = drawRect(
            Brush.radialGradient(
                colors = listOf(color.copy(alpha = a), Color.Transparent),
                center = Offset(size.width * x, size.height * y),
                radius = r * radius
            )
        )
        blob(pink, 0.05f, 0.06f, 0.55f)
        blob(lavender, 1.00f, 0.30f, 0.50f)
        blob(mint, 0.00f, 0.62f, 0.50f)
        blob(peach, 1.00f, 0.92f, 0.55f)
    }
}
