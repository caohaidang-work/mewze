package com.caohaidang.edward.music.lyrics

import android.content.Context
import com.caohaidang.edward.kugou.KuGou
import com.caohaidang.edward.music.constants.EnableKugouKey
import com.caohaidang.edward.music.utils.dataStore
import com.caohaidang.edward.music.utils.get

object KuGouLyricsProvider : LyricsProvider {
    override val name = "Kugou"
    override fun isEnabled(context: Context): Boolean =
        context.dataStore[EnableKugouKey] ?: true

    override suspend fun getLyrics(id: String, title: String, artist: String, duration: Int): Result<String> =
        KuGou.getLyrics(title, artist, duration) { song, singer ->
            LyricsMatcher.isMatch(title, artist, song, singer)
        }

    override suspend fun getAllLyrics(id: String, title: String, artist: String, duration: Int, callback: (String) -> Unit) {
        KuGou.getAllLyrics(title, artist, duration, callback)
    }
}
