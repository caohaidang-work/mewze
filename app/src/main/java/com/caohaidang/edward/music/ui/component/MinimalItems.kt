package com.caohaidang.edward.music.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.exoplayer.offline.Download.STATE_COMPLETED
import androidx.media3.exoplayer.offline.Download.STATE_DOWNLOADING
import androidx.media3.exoplayer.offline.Download.STATE_QUEUED
import com.caohaidang.edward.innertube.models.SongItem
import com.caohaidang.edward.innertube.models.YTItem
import com.caohaidang.edward.music.LocalDatabase
import com.caohaidang.edward.music.LocalDownloadUtil
import com.caohaidang.edward.music.R
import com.caohaidang.edward.music.db.entities.Song

private val MinimalRowHeight = 64.dp
private val MinimalThumbSize = 52.dp
private val MinimalIndexWidth = 28.dp
private val MinimalStartPadding = 16.dp
private val MinimalGap = 14.dp

@Composable
fun MinimalListItem(
    title: String,
    subtitle: String?,
    isActive: Boolean,
    leadingWidth: Dp,
    leading: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    badges: @Composable RowScope.() -> Unit = {},
    trailingContent: @Composable RowScope.() -> Unit = {},
) {
    val divider = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .height(MinimalRowHeight)
            .drawBehind {
                val startX = (MinimalStartPadding + leadingWidth + MinimalGap).toPx()
                drawLine(
                    color = divider,
                    start = Offset(startX, size.height),
                    end = Offset(size.width, size.height),
                    strokeWidth = 1f
                )
            }
            .padding(start = MinimalStartPadding, end = 4.dp)
    ) {
        Box(Modifier.width(leadingWidth), contentAlignment = Alignment.Center) { leading() }
        Column(
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
            modifier = Modifier
                .weight(1f)
                .padding(start = MinimalGap, end = 8.dp)
        ) {
            Text(
                text = title,
                fontSize = 17.sp,
                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        badges()
        trailingContent()
    }
}

@Composable
private fun SmallBadge(res: Int, tint: androidx.compose.ui.graphics.Color? = null) {
    Icon(
        painter = painterResource(res),
        contentDescription = null,
        tint = tint ?: MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(16.dp)
    )
}

@Composable
private fun DownloadBadge(state: Int?) {
    when (state) {
        STATE_COMPLETED -> SmallBadge(R.drawable.offline)
        STATE_QUEUED, STATE_DOWNLOADING -> CircularProgressIndicator(
            strokeWidth = 2.dp,
            modifier = Modifier.size(14.dp)
        )
        else -> {}
    }
}

@Composable
fun MinimalSongListItem(
    song: Song,
    modifier: Modifier = Modifier,
    albumIndex: Int? = null,
    isActive: Boolean = false,
    isPlaying: Boolean = false,
    trailingContent: @Composable RowScope.() -> Unit = {},
) {
    val download by LocalDownloadUtil.current.getDownload(song.id).collectAsState(initial = null)
    MinimalListItem(
        title = song.song.title,

        subtitle = if (albumIndex != null) null else song.artists.joinToString { it.name },
        isActive = isActive,
        leadingWidth = if (albumIndex != null) MinimalIndexWidth else MinimalThumbSize,
        leading = {
            ItemThumbnail(
                thumbnailUrl = song.song.thumbnailUrl,
                albumIndex = albumIndex,
                isActive = isActive,
                isPlaying = isPlaying,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.size(if (albumIndex != null) MinimalIndexWidth else MinimalThumbSize)
            )
        },
        badges = {
            if (song.song.liked) SmallBadge(R.drawable.favorite, MaterialTheme.colorScheme.error)
            DownloadBadge(download?.state)
        },
        trailingContent = trailingContent,
        modifier = modifier
    )
}

@Composable
fun MinimalYouTubeListItem(
    item: YTItem,
    modifier: Modifier = Modifier,
    isActive: Boolean = false,
    isPlaying: Boolean = false,
    trailingContent: @Composable RowScope.() -> Unit = {},
) {
    val database = LocalDatabase.current
    val song by database.song(item.id).collectAsState(initial = null)
    val downloads by LocalDownloadUtil.current.downloads.collectAsState()
    MinimalListItem(
        title = item.title,
        subtitle = (item as? SongItem)?.artists?.joinToString { it.name },
        isActive = isActive,
        leadingWidth = MinimalThumbSize,
        leading = {
            ItemThumbnail(
                thumbnailUrl = item.thumbnail,
                isActive = isActive,
                isPlaying = isPlaying,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.size(MinimalThumbSize)
            )
        },
        badges = {
            if (item is SongItem && song?.song?.liked == true) {
                SmallBadge(R.drawable.favorite, MaterialTheme.colorScheme.error)
            }
            if (item.explicit) SmallBadge(R.drawable.explicit)
            if (item is SongItem) DownloadBadge(downloads[item.id]?.state)
        },
        trailingContent = trailingContent,
        modifier = modifier
    )
}
