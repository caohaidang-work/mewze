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

/**
 * Picks the songs that keep playing once the queue runs out (autoplay / random mode).
 *
 * v2 changes:
 *  - seeds come from the song playing now *and* the last few songs of the session, so the
 *    queue drifts naturally instead of orbiting around a single track;
 *  - artists heard in the last few songs are penalised and no artist can take more than two
 *    slots in one batch, so you don't get the same singer five times in a row;
 *  - every candidate source is optional: if the network is down the picker falls back to the
 *    local library (quick picks, liked songs, most played) instead of returning nothing.
 */
class AutoPlayRecommender(
    private val database: MusicDatabase,
) {
    suspend fun recommend(
        seed: MediaMetadata?,
        exclude: Set<String>,
        count: Int = 12,
        hideExplicit: Boolean = false,
        recentSeeds: List<MediaMetadata> = emptyList(),
    ): List<MediaItem> = withContext(Dispatchers.IO) {
        val now = LocalDateTime.now()
        val from = now.minusDays(30).toDbMillis()
        val to = now.plusDays(1).toDbMillis()

        val artistRanks = runCatching { database.topArtistRanks(from, to, 15) }.getOrDefault(emptyList())
        val maxArtistTime = (artistRanks.maxOfOrNull { it.playTime } ?: 0L).coerceAtLeast(1L)
        val artistAffinity = artistRanks.associate { it.id to it.playTime.toFloat() / maxArtistTime }
        val topSongIds = runCatching { database.topSongRanks(from, to, 8).map { it.id } }.getOrDefault(emptyList())
        val recentIds = runCatching { database.recentSongIds(40).toSet() }.getOrDefault(emptySet())

        // Artists of the last few songs: used to avoid long runs of the same singer.
        val recentArtistIds = recentSeeds.takeLast(5)
            .flatMap { it.artists }
            .mapNotNull { it.id }
            .toSet()

        val earlierSeedIds = recentSeeds
            .map { it.id }
            .filter { it != seed?.id }
            .distinct()
            .takeLast(6)
            .shuffled()
            .take(2)

        val seedIds = buildList {
            seed?.id?.let(::add)
            addAll(earlierSeedIds)
            val used = toSet()
            addAll(
                topSongIds
                    .filter { it !in used }
                    .shuffled()
                    .take(if (seed == null) 3 else 1)
            )
        }.distinct()

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
            val liked = async {
                runCatching {
                    database.likedSongsByCreateDateAsc().first().shuffled().take(15).map { it.toMediaItem() }
                }.getOrDefault(emptyList())
            }
            radios.forEach { job ->
                val (list, bonus) = job.await()
                list.forEach { addCandidate(it, bonus) }
            }
            local.await().forEach { addCandidate(it, 0.8f) }
            liked.await().forEach { addCandidate(it, 0.4f) }
        }

        val ranked = items.values.map { item ->
            val id = item.mediaId
            var score = scores[id] ?: 0f
            val artists = item.metadata?.artists.orEmpty()
            val affinity = artists
                .mapNotNull { a -> a.id?.let { artistAffinity[it] } }
                .maxOrNull() ?: 0f
            score += affinity * 1.5f
            if (id in topSongIds) score += 0.3f
            if (id in recentIds) score -= 1.5f
            if (artists.any { it.id != null && it.id in recentArtistIds }) score -= 0.8f
            score += Random.nextFloat() * 0.4f
            item to score
        }.sortedByDescending { it.second }

        // Take the best candidates but never more than two songs from the same lead artist.
        val perArtist = HashMap<String, Int>()
        val picked = ArrayList<MediaItem>(count)
        val overflow = ArrayList<MediaItem>()
        for ((item, _) in ranked) {
            if (picked.size >= count) break
            val lead = item.metadata?.artists?.firstOrNull()?.let { it.id ?: it.name }
            if (lead == null) {
                picked += item
                continue
            }
            val used = perArtist[lead] ?: 0
            if (used >= 2) {
                overflow += item
            } else {
                perArtist[lead] = used + 1
                picked += item
            }
        }
        // Not enough variety available (small candidate pool): fill the rest anyway.
        if (picked.size < count) picked += overflow.take(count - picked.size)
        picked
    }
}
