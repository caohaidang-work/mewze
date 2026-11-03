package com.caohaidang.edward.music.ui.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.caohaidang.edward.music.R

/** Round glass icon button; colours follow the theme so it matches the library tab. */
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
            .liquidGlass(CircleShape, tint = MaterialTheme.colorScheme.onSurface, tintAlpha = 0.12f, elevation = 0.dp)
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
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(22.dp)
        )
    }
}

/**
 * Compact playlist header for the [liquidBackdrop] pages: optional tappable cover on the left,
 * title / meta on the right, then one row with a round Play button and icon-only actions.
 */
@Composable
fun GlassPlaylistHeader(
    title: String,
    subtitle: String?,
    playContainer: Color,
    playContent: Color,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
    onCoverClick: (() -> Unit)? = null,
    accentLine: (@Composable () -> Unit)? = null,
    cover: @Composable () -> Unit,
    actions: @Composable RowScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
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
                    .background(scheme.onSurface.copy(alpha = 0.10f))
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
                        .liquidGlass(CircleShape, tint = Color.Black, tintAlpha = 0.35f, elevation = 0.dp)
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
                    color = scheme.onSurfaceVariant,
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = scheme.onSurface,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp)
                )
                if (accentLine != null) {
                    CompositionLocalProvider(LocalContentColor provides scheme.primary) { accentLine() }
                }
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
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
                    .liquidGlass(CircleShape, tint = playContainer, tintAlpha = 0.85f, elevation = 0.dp)
                    .clickable(
                        role = Role.Button,
                        onClickLabel = stringResource(R.string.play),
                        onClick = onPlay
                    )
            ) {
                Icon(
                    painter = painterResource(R.drawable.play),
                    contentDescription = stringResource(R.string.play),
                    tint = playContent,
                    modifier = Modifier.size(28.dp)
                )
            }
            actions()
        }
    }
}
