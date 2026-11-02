package com.caohaidang.edward.music.ui.screens.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.caohaidang.edward.music.R
import com.caohaidang.edward.music.ui.component.PlaylistThumbnail
import com.caohaidang.edward.music.ui.component.liquidGlass

/** Pastel colours of the library tiles; every kind of item keeps its own hue. */
internal enum class BentoTone { SONG, PLAYLIST, ARTIST, ALBUM }

internal class TileColors(val container: Color, val content: Color)

@Composable
internal fun tileColors(tone: BentoTone): TileColors {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.surface.luminance() < 0.5f
    return when (tone) {
        BentoTone.SONG -> TileColors(scheme.primaryContainer, scheme.onPrimaryContainer)

        BentoTone.PLAYLIST ->
            if (dark) TileColors(Color(0xFF4A2B1D), Color(0xFFFFDCCB))
            else TileColors(Color(0xFFFFE2D4), Color(0xFF4A1F0D))

        BentoTone.ARTIST ->
            if (dark) TileColors(Color(0xFF1D4631), Color(0xFFC9F3DB))
            else TileColors(Color(0xFFD6F3E2), Color(0xFF0B3B25))

        BentoTone.ALBUM ->
            if (dark) TileColors(Color(0xFF342A5E), Color(0xFFE4DDFF))
            else TileColors(Color(0xFFE6DFFF), Color(0xFF2A1A5C))
    }
}

/** Rounded cover used in the stacked collages; falls back to a flat tone while the image is missing. */
@Composable
internal fun StackCover(url: String?, modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(18.dp)) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/** Up to three covers fanned out and slightly tilted, same look as the "Yêu thích" tile. */
@Composable
internal fun CoverFan(
    covers: List<String?>,
    coverSize: Dp,
    step: Dp,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(18.dp),
) {
    val tilts = listOf(-12f, -4f, 8f)
    val items = covers.take(3).ifEmpty { listOf(null, null, null) }
    Box(
        modifier = modifier.size(
            width = coverSize + step * (items.size - 1),
            height = coverSize + 12.dp
        )
    ) {
        items.forEachIndexed { i, url ->
            StackCover(
                url = url,
                shape = shape,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = step * i, y = ((i % 2) * 8).dp)
                    .size(coverSize)
                    .rotate(tilts[i % tilts.size])
            )
        }
    }
}

/** Round glass button; clickable when [onClick] is given, otherwise purely decorative. */
@Composable
internal fun GlassIconButton(
    @androidx.annotation.DrawableRes icon: Int,
    iconColor: Color,
    fill: Color,
    modifier: Modifier = Modifier,
    fillAlpha: Float = 0.5f,
    size: Dp = 40.dp,
    contentDescription: String? = null,
    onClick: (() -> Unit)? = null,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .liquidGlass(CircleShape, tint = fill, tintAlpha = fillAlpha, elevation = 0.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = contentDescription,
            tint = iconColor,
            modifier = Modifier.size(size * 0.5f)
        )
    }
}

/** List row on a glass card: cover on the left, text in the middle, optional [trailing] on the right. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun GlassRow(
    title: String,
    subtitle: String,
    colors: TileColors,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    typeLabel: String? = null,
    coverSize: Dp = 64.dp,
    highlighted: Boolean = false,
    tintAlpha: Float = 0.55f,
    activeTintAlpha: Float = 0.85f,
    trailing: @Composable () -> Unit = {},
    cover: @Composable () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp)
            .liquidGlass(
                shape = RoundedCornerShape(26.dp),
                tint = colors.container,
                tintAlpha = if (highlighted) activeTintAlpha else tintAlpha
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(coverSize)
                .clip(RoundedCornerShape((coverSize.value * 0.24f).dp))
                .background(colors.content.copy(alpha = 0.12f))
        ) {
            cover()
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            if (typeLabel != null) {
                Text(
                    text = typeLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.content.copy(alpha = 0.65f),
                    maxLines = 1
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.content.copy(alpha = 0.75f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        trailing()
    }
}

/** Grid cell: the cover fills a square glass card and the title sits in a glass strip over its bottom. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun GlassCoverCard(
    title: String,
    subtitle: String,
    colors: TileColors,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    tintAlpha: Float = 0.5f,
    captionAlpha: Float = 0.72f,
    activeCaptionAlpha: Float = 0.9f,
    cover: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .liquidGlass(RoundedCornerShape(26.dp), tint = colors.container, tintAlpha = tintAlpha)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        Box(modifier = Modifier.fillMaxSize()) { cover() }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(8.dp)
                .liquidGlass(
                    shape = RoundedCornerShape(20.dp),
                    tint = colors.container,
                    tintAlpha = if (highlighted) activeCaptionAlpha else captionAlpha,
                    elevation = 0.dp
                )
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = colors.content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.content.copy(alpha = 0.75f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        GlassIconButton(
            icon = R.drawable.more_vert,
            iconColor = colors.content,
            fill = colors.container,
            fillAlpha = 0.72f,
            size = 32.dp,
            onClick = onMore,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(10.dp)
        )
    }
}

/** Playlist cover that fills the card (collage of up to four covers when there is no custom one). */
@Composable
internal fun PlaylistCardCover(thumbnails: List<String>, colors: TileColors) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        PlaylistThumbnail(
            thumbnails = thumbnails,
            size = maxWidth,
            placeHolder = {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(maxWidth)) {
                    Icon(
                        painter = painterResource(R.drawable.queue_music),
                        contentDescription = null,
                        tint = colors.content,
                        modifier = Modifier.size(48.dp)
                    )
                }
            },
            shape = RoundedCornerShape(0.dp)
        )
    }
}

/** Pill-shaped filter chips on glass. */
@Composable
internal fun <T> GlassChipRow(
    options: List<Pair<T, String>>,
    selected: T,
    colors: TileColors,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        options.forEach { (value, label) ->
            val on = value == selected
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium,
                color = if (on) colors.content else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .liquidGlass(
                        shape = CircleShape,
                        tint = colors.container,
                        tintAlpha = if (on) 0.85f else 0.25f,
                        elevation = 0.dp
                    )
                    .clickable { onSelect(value) }
                    .padding(horizontal = 16.dp, vertical = 9.dp)
            )
        }
    }
}

/** "Tạo playlist" as a grid cell. */
@Composable
internal fun CreatePlaylistCard(colors: TileColors, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .liquidGlass(RoundedCornerShape(26.dp), tint = colors.container, tintAlpha = 0.35f)
            .clickable(onClick = onClick)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            GlassIconButton(
                icon = R.drawable.add,
                iconColor = colors.content,
                fill = colors.container,
                fillAlpha = 0.8f,
                size = 52.dp
            )
            Spacer(Modifier.size(10.dp))
            Text(
                text = "Tạo playlist",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = colors.content,
                textAlign = TextAlign.Center
            )
        }
    }
}

/** Square song cover; the playing song gets a dimmed overlay with a play / pause icon. */
@Composable
internal fun SongCover(url: String?, active: Boolean, playing: Boolean) {
    Box(modifier = Modifier.fillMaxSize()) {
        AsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        if (active) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.40f))
            ) {
                Icon(
                    painter = painterResource(if (playing) R.drawable.pause else R.drawable.play),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}
