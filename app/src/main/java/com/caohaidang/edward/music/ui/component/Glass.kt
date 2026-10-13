package com.caohaidang.edward.music.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
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
