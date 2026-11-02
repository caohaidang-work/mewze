package com.caohaidang.edward.music.ui.screens.artist

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.caohaidang.edward.innertube.models.AlbumItem
import com.caohaidang.edward.innertube.models.ArtistItem
import com.caohaidang.edward.innertube.models.PlaylistItem
import com.caohaidang.edward.innertube.models.SongItem
import com.caohaidang.edward.innertube.models.YTItem
import com.caohaidang.edward.music.R
import com.caohaidang.edward.music.ui.component.liquidGlass
import com.caohaidang.edward.music.ui.screens.library.GlassCoverCard
import com.caohaidang.edward.music.ui.screens.library.GlassIconButton
import com.caohaidang.edward.music.ui.screens.library.GlassRow
import com.caohaidang.edward.music.ui.screens.library.SongCover
import com.caohaidang.edward.music.ui.screens.library.TileColors
import com.caohaidang.edward.music.ui.utils.resize
import com.caohaidang.edward.music.utils.joinByBullet
import com.caohaidang.edward.music.utils.makeTimeString

/** The artist page sits on a dark, image-tinted backdrop, so its glass is smoked: black body, white text. */
private val ArtistGlassColors = TileColors(container = Color.Black, content = Color.White)

private val ArtistRowCover = 60.dp
private val ArtistCardWidth = 156.dp

/** Song row on smoked glass: cover on the left, title / artists, "more" button on the right. */
@Composable
internal fun ArtistGlassSongRow(
    title: String,
    subtitle: String,
    thumbnailUrl: String?,
    active: Boolean,
    playing: Boolean,
    onClick: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassRow(
        title = title,
        subtitle = subtitle,
        colors = ArtistGlassColors,
        onClick = onClick,
        onLongClick = onMore,
        modifier = modifier,
        coverSize = ArtistRowCover,
        highlighted = active,
        tintAlpha = 0.28f,
        activeTintAlpha = 0.50f,
        trailing = {
            GlassIconButton(
                icon = R.drawable.more_horiz,
                iconColor = Color.White,
                fill = Color.White,
                fillAlpha = 0.14f,
                onClick = onMore
            )
        },
        cover = { SongCover(url = thumbnailUrl, active = active, playing = playing) }
    )
}

/**
 * Card for the horizontally scrolling rows (albums, singles, videos, playlists, related artists).
 * Artists get a round avatar on a glass ring, everything else a square cover card with a glass caption.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ArtistGlassCard(
    item: YTItem,
    active: Boolean,
    playing: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (item is ArtistItem) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = modifier
                .width(ArtistCardWidth * 0.72f)
                .clip(RoundedCornerShape(20.dp))
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .padding(4.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .liquidGlass(CircleShape, tint = Color.White, tintAlpha = 0.18f, elevation = 0.dp)
                    .padding(4.dp)
            ) {
                AsyncImage(
                    model = item.thumbnail.resize(400, 400),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                )
            }
            Spacer(Modifier.size(8.dp))
            Text(
                text = item.title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        return
    }

    val subtitle = when (item) {
        is SongItem -> joinByBullet(
            item.artists.joinToString { it.name },
            makeTimeString(item.duration?.times(1000L))
        )
        is AlbumItem -> joinByBullet(item.artists?.joinToString { it.name }, item.year?.toString())
        is PlaylistItem -> joinByBullet(item.author?.name, item.songCountText)
        is ArtistItem -> null
    }.orEmpty()

    GlassCoverCard(
        title = item.title,
        subtitle = subtitle,
        colors = ArtistGlassColors,
        onClick = onClick,
        onLongClick = onLongClick,
        onMore = onMore,
        modifier = modifier.width(ArtistCardWidth),
        highlighted = active,
        tintAlpha = 0.22f,
        captionAlpha = 0.50f,
        activeCaptionAlpha = 0.70f,
        cover = {
            SongCover(
                url = item.thumbnail.resize(544, 544),
                active = active && item is SongItem,
                playing = playing
            )
        }
    )
}
