package com.caohaidang.edward.music.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.caohaidang.edward.music.LocalPlayerConnection
import com.caohaidang.edward.music.constants.ShowLyricsKey
import com.caohaidang.edward.music.constants.ShowQueueKey
import com.caohaidang.edward.music.ui.component.Lyrics
import com.caohaidang.edward.music.utils.rememberPreference

@Composable
fun Thumbnail(
    sliderPositionProvider: () -> Long?,
    modifier: Modifier = Modifier,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val currentView = LocalView.current

    val error by playerConnection.error.collectAsState()
    val showQueue by rememberPreference(ShowQueueKey, false)
    val showLyrics = rememberPreference(ShowLyricsKey, false).value && !showQueue

    DisposableEffect(showLyrics) {
        currentView.keepScreenOn = showLyrics
        onDispose {
            currentView.keepScreenOn = false
        }
    }

    Box(modifier = modifier) {
        if (!showLyrics && error == null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = { offset ->
                                if (offset.x < size.width / 2) {
                                    playerConnection.player.seekBack()
                                } else {
                                    playerConnection.player.seekForward()
                                }
                            }
                        )
                    }
            )
        }

        AnimatedVisibility(
            visible = showLyrics && error == null,
            // vào chậm hơn một nhịp để không chồng lên danh sách chờ đang mờ dần
            enter = fadeIn(tween(200, delayMillis = 120)),
            exit = fadeOut(tween(120))
        ) {
            Lyrics(sliderPositionProvider = sliderPositionProvider)
        }

        AnimatedVisibility(
            visible = error != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .padding(32.dp)
                .align(Alignment.Center)
        ) {
            error?.let { error ->
                PlaybackError(
                    error = error,
                    retry = playerConnection.player::prepare
                )
            }
        }
    }
}
