package com.caohaidang.edward.music.ui.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.caohaidang.edward.music.R
import com.caohaidang.edward.music.ui.utils.resize
import com.caohaidang.edward.music.viewmodels.HeroSlide
import com.caohaidang.edward.music.viewmodels.HeroTarget
import kotlinx.coroutines.delay

/**
 * Full-bleed carousel at the top of Home: the cover fills the page (under the status bar), fades into the
 * background at the bottom, and the label, title and a Play pill sit over it.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeHeroPager(
    slides: List<HeroSlide>,
    onPlay: (HeroSlide) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (slides.isEmpty()) return

    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val heroHeight = (screenWidth * 1.12f).coerceAtMost(520.dp) + statusBarTop
    val background = MaterialTheme.colorScheme.background

    val pagerState = rememberPagerState(pageCount = { slides.size })
    val isDragged by pagerState.interactionSource.collectIsDraggedAsState()

    LaunchedEffect(pagerState.settledPage, isDragged, slides.size) {
        if (isDragged || slides.size < 2) return@LaunchedEffect
        delay(7000)
        pagerState.animateScrollToPage((pagerState.settledPage + 1) % slides.size)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(heroHeight)
    ) {
        HorizontalPager(
            state = pagerState,
            key = { slides[it].key },
            modifier = Modifier.fillMaxSize()
        ) { page ->
            HeroPage(slide = slides[page], onPlay = { onPlay(slides[page]) })
        }

        // Soft fade into the page background below the hero.
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(72.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, background)))
        )

        if (slides.size > 1) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
            ) {
                repeat(slides.size) { i ->
                    val selected = i == pagerState.currentPage
                    Box(
                        Modifier
                            .height(6.dp)
                            .width(if (selected) 18.dp else 6.dp)
                            .clip(CircleShape)
                            .background(
                                MaterialTheme.colorScheme.onSurface.copy(alpha = if (selected) 0.9f else 0.3f)
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroPage(
    slide: HeroSlide,
    onPlay: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        AsyncImage(
            model = slide.thumbnail?.resize(800, 800),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .blur(1.5.dp)
        )
        // Darkens the top (status bar icons) and the bottom (text) while keeping the middle of the cover clear.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.30f),
                        0.28f to Color.Transparent,
                        0.50f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.80f),
                    )
                )
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 24.dp, end = 24.dp, bottom = 40.dp)
        ) {
            Text(
                text = slide.label,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.5).sp,
                color = Color.White.copy(alpha = 0.72f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = slide.title,
                fontSize = 32.sp,
                lineHeight = 36.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-1).sp,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(16.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .height(48.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFD0D0D0).copy(alpha = 0.86f))
                    .clickable(onClick = onPlay)
                    .padding(horizontal = 22.dp)
            ) {
                val isArtist = slide.target is HeroTarget.ArtistPage
                if (!isArtist) {
                    Icon(
                        painter = painterResource(R.drawable.play),
                        contentDescription = null,
                        tint = Color(0xFF1B1B1B),
                        modifier = Modifier.size(22.dp)
                    )
                }
                Text(
                    text = if (isArtist) "Xem nghệ sĩ" else "Phát",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.5).sp,
                    color = Color(0xFF1B1B1B),
                )
            }
        }
    }
}

/** Bold section title with a chevron when the whole section can be opened. */
@Composable
fun HomeSectionTitle(
    title: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 24.dp, vertical = 10.dp)
    ) {
        Text(
            text = title,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.8).sp,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        if (onClick != null) {
            Icon(
                painter = painterResource(R.drawable.navigate_next),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(start = 4.dp)
                    .size(24.dp)
            )
        }
    }
}

/** Plain rounded cover with the title and subtitle underneath (no card around it). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeCover(
    title: String,
    subtitle: String?,
    thumbnailUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    isActive: Boolean = false,
    size: androidx.compose.ui.unit.Dp = 148.dp,
) {
    Column(
        modifier = modifier
            .width(size)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        AsyncImage(
            model = thumbnailUrl?.resize(400, 400),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(22.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = title,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.4).sp,
            color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = subtitle ?: " ",
            fontSize = 14.sp,
            letterSpacing = (-0.3).sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** One line of a chart: big rank number, cover, title and artists. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeChartRow(
    rank: Int,
    title: String,
    subtitle: String?,
    thumbnailUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    isActive: Boolean = false,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 24.dp, vertical = 6.dp)
    ) {
        Text(
            text = rank.toString(),
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.5).sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.width(40.dp)
        )
        AsyncImage(
            model = thumbnailUrl?.resize(200, 200),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(54.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
        )
        Column(
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .weight(1f)
                .padding(start = 14.dp)
        ) {
            Text(
                text = title,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.4).sp,
                color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    fontSize = 14.sp,
                    letterSpacing = (-0.3).sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Pill under a chart that expands it to the full list (and collapses it again). */
@Composable
fun HomeExpandButton(
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Start
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .height(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .clickable(onClick = onClick)
                .padding(horizontal = 18.dp)
        ) {
            Text(
                text = if (expanded) "Thu gọn" else "Xem tất cả",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Icon(
                painter = painterResource(if (expanded) R.drawable.expand_less else R.drawable.expand_more),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
