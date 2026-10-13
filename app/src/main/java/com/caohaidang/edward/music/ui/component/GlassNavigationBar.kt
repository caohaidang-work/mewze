package com.caohaidang.edward.music.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.caohaidang.edward.music.constants.NavMiniPlayerGap
import com.caohaidang.edward.music.constants.NavigationBarHeight
import com.caohaidang.edward.music.ui.screens.Screens

@Composable
fun GlassNavigationBar(
    items: List<Screens>,
    isSelected: (Screens) -> Boolean,
    onItemClick: (Screens) -> Unit,
    bottomInset: Dp,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme

    Box(
        contentAlignment = Alignment.TopCenter,
        modifier = modifier
            .fillMaxWidth()
            .height(NavigationBarHeight + bottomInset)

            .pointerInput(Unit) {}
            .padding(horizontal = 20.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = NavMiniPlayerGap)
                .height(56.dp)
                .glassSurface(shape = CircleShape, shadowElevation = 16.dp, opaque = true)
                .padding(5.dp)
        ) {
            items.forEach { screen ->
                val selected = isSelected(screen)
                val contentColor by animateColorAsState(
                    targetValue = if (selected) scheme.primary else scheme.onSurface.copy(alpha = 0.68f),
                    label = "navContent"
                )
                val highlight by animateColorAsState(
                    targetValue = if (selected) scheme.onSurface.copy(alpha = 0.18f) else Color.Transparent,
                    label = "navHighlight"
                )

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(highlight)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onItemClick(screen) }
                        )
                ) {
                    Icon(
                        painter = painterResource(screen.iconId),
                        contentDescription = if (screen.titleId != 0) stringResource(screen.titleId).takeIf { it.isNotBlank() } else null,
                        tint = contentColor,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }
    }
}
