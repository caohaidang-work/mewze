package com.caohaidang.edward.music.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.caohaidang.edward.music.MainActivity
import com.caohaidang.edward.music.constants.MediaSessionConstants
import com.caohaidang.edward.music.playback.MusicService

/**
 * Win98 "CD Player" home-screen widget.
 *
 * The provider only handles clicks: it briefly connects a Media3 [MediaController] to
 * [MusicService], sends the command and disconnects. Drawing is done by
 * [CdPlayerWidgetRenderer]; the state is pushed by [CdPlayerWidgetUpdater] from inside the service.
 */
open class CdPlayerWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        // Draw the last known state immediately, then ask the live service (if any) for fresh data.
        CdPlayerWidgetRenderer.updateWidgets(context, manager, ids, CdWidgetStore.load(context))
        CdPlayerWidgetUpdater.active?.refresh()
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, newOptions: Bundle) {
        CdPlayerWidgetRenderer.updateWidgets(context, manager, intArrayOf(id), CdWidgetStore.load(context))
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_PREV -> control(context) { it.seekToPrevious() }
            ACTION_NEXT -> control(context) { it.seekToNext() }
            ACTION_PLAY_PAUSE -> control(context) { c ->
                when {
                    c.mediaItemCount == 0 -> openApp(context)
                    c.playWhenReady && c.playbackState != Player.STATE_ENDED -> c.pause()
                    else -> {
                        if (c.playbackState == Player.STATE_ENDED) c.seekTo(0)
                        if (c.playbackState == Player.STATE_IDLE) c.prepare()
                        c.play()
                    }
                }
            }
            ACTION_STOP -> control(context) { c ->
                c.pause()
                c.seekTo(0)
            }
            ACTION_LIKE -> control(context) { c ->
                c.sendCustomCommand(MediaSessionConstants.CommandToggleLike, Bundle.EMPTY)
            }
            else -> super.onReceive(context, intent)
        }
    }

    private fun openApp(context: Context) {
        runCatching {
            context.startActivity(
                Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            )
        }
    }

    private fun control(context: Context, block: (MediaController) -> Unit) {
        val appContext = context.applicationContext
        val pending = goAsync()
        val token = SessionToken(appContext, ComponentName(appContext, MusicService::class.java))
        val future = MediaController.Builder(appContext, token).buildAsync()
        future.addListener({
            try {
                block(future.get())
            } catch (_: Throwable) {
                // service could not be reached - nothing to do
            } finally {
                // give the binder call a moment to be delivered before disconnecting
                Handler(Looper.getMainLooper()).postDelayed({
                    MediaController.releaseFuture(future)
                    pending.finish()
                }, 400)
            }
        }, { Handler(Looper.getMainLooper()).post(it) })
    }

    companion object {
        const val ACTION_PREV = "com.caohaidang.edward.music.widget.PREV"
        const val ACTION_NEXT = "com.caohaidang.edward.music.widget.NEXT"
        const val ACTION_PLAY_PAUSE = "com.caohaidang.edward.music.widget.PLAY_PAUSE"
        const val ACTION_STOP = "com.caohaidang.edward.music.widget.STOP"
        const val ACTION_LIKE = "com.caohaidang.edward.music.widget.LIKE"
    }
}
