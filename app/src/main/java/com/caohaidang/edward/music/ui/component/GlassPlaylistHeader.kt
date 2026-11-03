package com.caohaidang.edward.music.ui.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.animation.animateColorAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.caohaidang.edward.music.R

private val GlassBase = Color(0xFF0D1620)

/**
 * Dark base with two soft glows derived from the cover tint.
 * Replaces the full-height gradient of [immersiveBackdrop] on the playlist screen.
 */
@Composable
fun Modifier.glassBackdrop(tint: Color?): Modifier {
    val glow by animateColorAsState(tint ?: Color(0xFF3A4CFF), label = "glassGlow")
    return this.drawBehind {
        drawRect(GlassBase)
        drawRect(
            Brush.radialGradient(
                colors = listOf(glow.copy(alpha = 0.55f), Color.Transparent),
                center = Offset(size.width * 0.1f, 0f),
                radius = size.width * 0.9f,
            )
        )
        drawRect(
            Brush.radialGradient(
                colors = listOf(lerp(glow, Color(0xFF3A4CFF), 0.5f).copy(alpha = 0.40f), Color.Transparent),
                center = Offset(size.width * 1.05f, size.height * 0.04f),
                radius = size.width * 0.8f,
            )
        )
    }
}

/**
 * Light "frosted" surface: translucent white tint plus a bright top-left edge.
 * No real backdrop blur, so it works on every API level and stays cheap in lists.
 */
fun Modifier.glassSurface(shape: Shape = CircleShape, strong: Boolean = false): Modifier = this
    .clip(shape)
    .background(Color.White.copy(alpha = if (strong) 0.16f else 0.08f))
    .border(
        BorderStroke(
            1.dp,
            Brush.linearGradient(
                listOf(Color.White.copy(alpha = 0.38f), Color.White.copy(alpha = 0.08f))
            )
        ),
        shape
    )

@Composable
fun GlassIconButton(
    onClick: () -> Unit,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Int = 44,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size.dp)
            .glassSurface(CircleShape)
            .clickable(role = Role.Button, onClickLabel = contentDescription, onClick = onClick),
        content = content
    )
}

@Composable
fun GlassIconButton(
    @DrawableRes icon: Int,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Int = 44,
) {
    GlassIconButton(
        onClick = onClick,
        contentDescription = contentDescription,
        modifier = modifier,
        size = size,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(22.dp)
        )
    }
}

/**
 * Compact playlist header: tappable cover on the left, title/meta on the right,
 * then one row with a round Play button and icon-only actions.
 */
@Composable
fun GlassPlaylistHeader(
    title: String,
    subtitle: String,
    tint: Color?,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
    onCoverClick: (() -> Unit)? = null,
    accentLine: (@Composable () -> Unit)? = null,
    cover: @Composable () -> Unit,
    actions: @Composable RowScope.() -> Unit,
) {
    val accent = lerp(tint ?: Color.White, Color.White, 0.75f)
    val coverShape = RoundedCornerShape(30.dp)
    val changeCover = stringResource(R.string.playlist_change_cover)

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(148.dp)
                    .shadow(18.dp, coverShape, clip = false)
                    .clip(coverShape)
                    .background(Color.White.copy(alpha = 0.2f))
                    .then(
                        if (onCoverClick != null)
                            Modifier.clickable(role = Role.Button, onClickLabel = changeCover, onClick = onCoverClick)
                        else Modifier
                    )
            ) {
                cover()
                if (onCoverClick != null) Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .size(34.dp)
                        .glassSurface(CircleShape, strong = true)
                        .background(Color.Black.copy(alpha = 0.25f))
                ) {
                    Icon(
                        painter = painterResource(R.drawable.image),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f).padding(bottom = 4.dp)) {
                Text(
                    text = stringResource(R.string.playlist_label).uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.7f),
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp)
                )
                if (accentLine != null) {
                    CompositionLocalProvider(LocalContentColor provides accent) { accentLine() }
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.75f),
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }

        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 8.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(accent)
                    .clickable(
                        role = Role.Button,
                        onClickLabel = stringResource(R.string.play),
                        onClick = onPlay
                    )
            ) {
                Icon(
                    painter = painterResource(R.drawable.play),
                    contentDescription = stringResource(R.string.play),
                    tint = Color(0xFF07231F),
                    modifier = Modifier.size(28.dp)
                )
            }
            actions()
        }
    }
}
