package com.caohaidang.edward.music.playback

import androidx.media3.common.MediaItem
import com.caohaidang.edward.innertube.YouTube
import com.caohaidang.edward.innertube.models.WatchEndpoint
import com.caohaidang.edward.music.constants.toDbMillis
import com.caohaidang.edward.music.db.MusicDatabase
import com.caohaidang.edward.music.extensions.metadata
import com.caohaidang.edward.music.extensions.toMediaItem
import com.caohaidang.edward.music.models.MediaMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import kotlin.random.Random

class AutoPlayRecommender(
    private val database: MusicDatabase,
) {
    suspend fun recommend(
        seed: MediaMetadata?,
        exclude: Set<String>,
        count: Int = 12,
        hideExplicit: Boolean = false,
    ): List<MediaItem> = withContext(Dispatchers.IO) {
        val now = LocalDateTime.now()
        val from = now.minusDays(30).toDbMillis()
        val to = now.plusDays(1).toDbMillis()

        val artistRanks = database.topArtistRanks(from, to, 15)
        val maxArtistTime = (artistRanks.maxOfOrNull { it.playTime } ?: 0L).coerceAtLeast(1L)
        val artistAffinity = artistRanks.associate { it.id to it.playTime.toFloat() / maxArtistTime }
        val topSongIds = database.topSongRanks(from, to, 8).map { it.id }
        val recentIds = database.recentSongIds(40).toSet()

        val seedIds = buildList {
            seed?.id?.let(::add)
            addAll(topSongIds.filter { it != seed?.id }.shuffled().take(if (seed == null) 3 else 2))
        }

        val scores = HashMap<String, Float>()
        val items = HashMap<String, MediaItem>()

        fun addCandidate(item: MediaItem, sourceBonus: Float) {
            val id = item.mediaId
            if (id.isBlank() || id in exclude) return
            val meta = item.metadata
            if (hideExplicit && meta?.explicit == true) return
            items.putIfAbsent(id, item)
            scores[id] = (scores[id] ?: 0f) + sourceBonus
        }

        coroutineScope {
            val radios = seedIds.mapIndexed { index, id ->
                async {
                    runCatching {
                        YouTube.next(WatchEndpoint(videoId = id)).getOrNull()?.items.orEmpty()
                            .filter { it.id != id }
                            .take(25)
                            .map { it.toMediaItem() }
                    }.getOrDefault(emptyList()) to (if (index == 0 && seed != null) 1.0f else 0.6f)
                }
            }
            val local = async {
                runCatching { database.quickPicks().first().take(20).map { it.toMediaItem() } }
                    .getOrDefault(emptyList())
            }
            radios.forEach { job ->
                val (list, bonus) = job.await()
                list.forEach { addCandidate(it, bonus) }
            }
            local.await().forEach { addCandidate(it, 0.8f) }
        }

        val ranked = items.values.map { item ->
            val id = item.mediaId
            var score = scores[id] ?: 0f
            val affinity = item.metadata?.artists
                ?.mapNotNull { a -> a.id?.let { artistAffinity[it] } }
                ?.maxOrNull() ?: 0f
            score += affinity * 1.5f
            if (id in topSongIds) score += 0.3f
            if (id in recentIds) score -= 1.5f
            score += Random.nextFloat() * 0.3f
            item to score
        }.sortedByDescending { it.second }

        ranked.take(count).map { it.first }
    }
}
