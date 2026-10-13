package com.caohaidang.edward.music.ui.component

import android.graphics.drawable.BitmapDrawable
import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import com.caohaidang.edward.music.constants.AppBarHeight
import com.caohaidang.edward.music.ui.utils.fadingEdge
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.material3.MaterialTheme as M3
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.imageLoader
import coil.request.ImageRequest
import com.caohaidang.edward.music.R
import com.caohaidang.edward.music.ui.theme.extractThemeColor

fun Color.toImmersiveTint(): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(this.toArgb(), hsv)
    hsv[1] = hsv[1].coerceIn(0.55f, 0.95f)
    hsv[2] = hsv[2].coerceIn(0.38f, 0.62f)
    return Color(android.graphics.Color.HSVToColor(hsv))
}

@Composable
fun rememberImageTint(url: String?): Color? {
    val context = LocalContext.current
    var tint by remember(url) { mutableStateOf<Color?>(null) }
    LaunchedEffect(url) {
        if (url.isNullOrBlank()) return@LaunchedEffect
        val result = runCatching {
            context.imageLoader.execute(
                ImageRequest.Builder(context)
                    .data(url)
                    .size(128)
                    .allowHardware(false)
                    .build()
            )
        }.getOrNull()
        tint = (result?.drawable as? BitmapDrawable)?.bitmap?.extractThemeColor()?.toImmersiveTint()
    }
    return tint
}

@Composable
fun Modifier.immersiveBackdrop(tint: Color?): Modifier {
    val fallback = Color(0xFF3A3A44)
    val animated by animateColorAsState(tint ?: fallback, label = "immersiveTint")
    return this.drawBehind {
        drawRect(
            Brush.verticalGradient(
                colors = listOf(
                    animated,
                    lerp(animated, Color.Black, 0.35f),
                    lerp(animated, Color.Black, 0.70f),
                ),
            )
        )
    }
}

@Composable
fun ImmersiveTheme(content: @Composable () -> Unit) {
    val cs = M3.colorScheme
    M3(
        colorScheme = cs.copy(
            onBackground = Color.White,
            onSurface = Color.White,
            onSurfaceVariant = Color.White.copy(alpha = 0.70f),
            surfaceVariant = Color.White.copy(alpha = 0.12f),
        )
    ) {
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides Color.White,
            content = content
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)

@Composable
fun GlassCircleButton(
    @DrawableRes icon: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
    size: Int = 44,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = if (enabled) 0.20f else 0.10f))
            .combinedClickable(enabled = enabled, onClick = onClick, onLongClick = onLongClick)
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
fun ImmersiveActionRow(
    playText: String,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
    onShuffle: (() -> Unit)? = null,
    liked: Boolean = false,
    onToggleLike: (() -> Unit)? = null,
    @DrawableRes trailingIcon: Int? = null,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth()
    ) {
        if (onShuffle != null) {
            GlassCircleButton(icon = R.drawable.shuffle, onClick = onShuffle, size = 52)
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .size(width = 190.dp, height = 52.dp)
                .clip(CircleShape)
                .background(Color.White)
                .clickable(onClick = onPlay)
        ) {
            Icon(
                painter = painterResource(R.drawable.play),
                contentDescription = null,
                tint = Color.Black,
                modifier = Modifier.size(22.dp)
            )
            Text(
                text = playText,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.Black,
            )
        }

        if (onToggleLike != null) {
            GlassCircleButton(
                icon = trailingIcon ?: if (liked) R.drawable.favorite else R.drawable.favorite_border,
                tint = if (liked && trailingIcon == null) Color(0xFFFF5A6E) else Color.White,
                onClick = onToggleLike,
                size = 52,
            )
        }
    }
}

@Composable
fun ImmersiveCoverHeader(
    title: String,
    tint: Color?,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    accentLine: (@Composable () -> Unit)? = null,
    secondaryActions: (@Composable androidx.compose.foundation.layout.RowScope.() -> Unit)? = null,
    cover: @Composable () -> Unit,
    actions: @Composable () -> Unit,
) {
    val statusTop = androidx.compose.foundation.layout.WindowInsets.systemBars
        .asPaddingValues().calculateTopPadding()
    val accent = lerp(tint ?: Color.White, Color.White, 0.65f)

    androidx.compose.foundation.layout.Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .fadingEdge(top = statusTop + AppBarHeight, bottom = 96.dp)
        ) { cover() }

        androidx.compose.foundation.layout.Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .offset(y = (-40).dp)
                .padding(horizontal = 20.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            if (accentLine != null) {
                androidx.compose.runtime.CompositionLocalProvider(
                    androidx.compose.material3.LocalContentColor provides accent
                ) { accentLine() }
            }
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            androidx.compose.foundation.layout.Spacer(Modifier.height(20.dp))
            actions()
            if (secondaryActions != null) {
                androidx.compose.foundation.layout.Spacer(Modifier.height(14.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                    content = secondaryActions
                )
            }
        }
    }
}

@Composable
fun ImmersiveCircleActionRow(
    @DrawableRes leftIcon: Int,
    onLeft: (() -> Unit)?,
    onPlay: () -> Unit,
    @DrawableRes rightIcon: Int,
    onRight: () -> Unit,
    modifier: Modifier = Modifier,
    rightTint: Color = Color.White,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth()
    ) {
        GlassCircleButton(
            icon = leftIcon,
            onClick = { onLeft?.invoke() },
            enabled = onLeft != null,
            size = 56,
        )
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(76.dp)
                .clip(CircleShape)
                .background(Color.White)
                .clickable(onClick = onPlay)
        ) {
            Icon(
                painter = painterResource(R.drawable.play),
                contentDescription = null,
                tint = Color.Black,
                modifier = Modifier.size(38.dp)
            )
        }
        GlassCircleButton(icon = rightIcon, onClick = onRight, tint = rightTint, size = 56)
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun immersiveTopBarColors(tint: Color?, solid: Boolean): androidx.compose.material3.TopAppBarColors {
    val bar = if (solid) lerp(tint ?: Color(0xFF3A3A44), Color.Black, 0.55f) else Color.Transparent
    return androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
        containerColor = bar,
        scrolledContainerColor = bar,
        navigationIconContentColor = Color.White,
        titleContentColor = Color.White,
        actionIconContentColor = Color.White,
    )
}
