package com.caohaidang.edward.music.playback.queues

import androidx.media3.common.MediaItem
import com.caohaidang.edward.music.db.MusicDatabase
import com.caohaidang.edward.music.models.MediaMetadata
import com.caohaidang.edward.music.playback.AutoPlayRecommender

class PersonalMixQueue(
    private val database: MusicDatabase,
    private val hideExplicit: Boolean = false,
    private val title: String? = null,
) : Queue {
    override val preloadItem: MediaMetadata? = null

    override suspend fun getInitialStatus(): Queue.Status {
        val items = AutoPlayRecommender(database).recommend(
            seed = null,
            exclude = emptySet(),
            count = 30,
            hideExplicit = hideExplicit,
        )
        return Queue.Status(title, items, 0)
    }

    override fun hasNextPage(): Boolean = false
    override suspend fun nextPage(): List<MediaItem> = throw UnsupportedOperationException()
}
