package com.caohaidang.edward.music.spotify

import android.content.Context
import com.caohaidang.edward.innertube.YouTube
import com.caohaidang.edward.innertube.models.SongItem
import com.caohaidang.edward.music.db.MusicDatabase
import com.caohaidang.edward.music.db.entities.PlaylistEntity
import com.caohaidang.edward.music.db.entities.PlaylistSongMap
import com.caohaidang.edward.music.models.MediaMetadata
import com.caohaidang.edward.music.models.toMediaMetadata
import com.caohaidang.edward.music.sync.LibrarySyncManager
import com.caohaidang.edward.music.utils.reportException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.text.Normalizer
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/** What the user picked on the import screen. */
data class SpotifyImportRequest(
    val importLiked: Boolean,
    val playlists: List<SpotifyPlaylist>,
)

data class UnmatchedTrack(val title: String, val artists: String, val source: String)

data class SpotifyImportResult(
    val likedImported: Int = 0,
    val playlistsImported: Int = 0,
    val songsInPlaylists: Int = 0,
    val unmatched: List<UnmatchedTrack> = emptyList(),
    val errors: List<String> = emptyList(),
)

sealed interface SpotifyImportState {
    data object Idle : SpotifyImportState
    data class Running(val step: String, val done: Int, val total: Int) : SpotifyImportState
    data class Done(val result: SpotifyImportResult) : SpotifyImportState
    data class Failed(val message: String) : SpotifyImportState
}

/**
 * Copies a Spotify library into the local one. Spotify has no audio we can play, so every track is
 * matched to the same song on YouTube Music (title + artist + duration); matched songs are stored
 * like any other song, so they play, download and sync with the rest of the app.
 *
 * Safe to run repeatedly: matches are cached, liked songs are only added, and a playlist imported
 * before is topped up with missing songs instead of being duplicated or overwritten.
 */
@Singleton
class SpotifyImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: MusicDatabase,
    private val auth: SpotifyAuth,
    private val syncManager: LibrarySyncManager,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val cacheFile get() = context.filesDir.resolve("spotify_import.json")
    private val running = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow<SpotifyImportState>(SpotifyImportState.Idle)
    val state: StateFlow<SpotifyImportState> = _state.asStateFlow()

    @Serializable
    private data class CachedMatch(
        val ytId: String,
        val title: String,
        val artists: List<CachedArtist>,
        val durationSec: Int,
        val thumbnail: String? = null,
        val albumId: String? = null,
        val albumTitle: String? = null,
        val explicit: Boolean = false,
    )

    @Serializable
    private data class CachedArtist(val id: String? = null, val name: String)

    @Serializable
    private data class Cache(
        val matches: MutableMap<String, CachedMatch> = mutableMapOf(), // spotify track id -> YT Music song
        val playlists: MutableMap<String, String> = mutableMapOf(),    // spotify playlist id -> local playlist id
    )

    private fun loadCache(): Cache = runCatching {
        json.decodeFromString(Cache.serializer(), cacheFile.readText())
    }.getOrDefault(Cache())

    private fun saveCache(cache: Cache) {
        runCatching { cacheFile.writeText(json.encodeToString(Cache.serializer(), cache)) }
    }

    fun clearCache() {
        cacheFile.delete()
    }

    fun resetState() {
        if (!running.isLocked) _state.value = SpotifyImportState.Idle
    }

    /** Runs in the importer's own scope, so leaving the screen does not cancel an import in progress. */
    fun start(request: SpotifyImportRequest) {
        scope.launch { doImport(request) }
    }

    private suspend fun doImport(request: SpotifyImportRequest) {
        if (!running.tryLock()) return
        try {
            _state.value = SpotifyImportState.Running("Đang chuẩn bị…", 0, 0)
            _state.value = SpotifyImportState.Done(withContext(Dispatchers.IO) { performImport(request) })
            // Push the new likes/playlists to YouTube Music right away instead of waiting for the next periodic sync.
            scope.launch { runCatching { syncManager.syncAll(force = true) } }
        } catch (e: CancellationException) {
            _state.value = SpotifyImportState.Idle
            throw e
        } catch (e: Throwable) {
            reportException(e)
            _state.value = SpotifyImportState.Failed(e.message ?: "Nhập từ Spotify thất bại")
        } finally {
            running.unlock()
        }
    }

    // ---------------------------------------------------------------- main flow

    private suspend fun performImport(request: SpotifyImportRequest): SpotifyImportResult {
        val api = SpotifyApi(auth)
        val cache = loadCache()
        val unmatched = mutableListOf<UnmatchedTrack>()
        val errors = mutableListOf<String>()
        var likedImported = 0
        var playlistsImported = 0
        var songsInPlaylists = 0

        // 1) Download everything from Spotify first, so a problem there aborts before touching the library.
        _state.value = SpotifyImportState.Running("Đang đọc dữ liệu từ Spotify…", 0, 0)
        val liked: List<SpotifyTrack> =
            if (request.importLiked) api.likedTracks { n, total ->
                _state.value = SpotifyImportState.Running("Đang đọc Bài hát đã thích…", n, total)
            } else emptyList()

        val playlistTracks = LinkedHashMap<SpotifyPlaylist, List<SpotifyTrack>>()
        request.playlists.forEachIndexed { i, pl ->
            _state.value = SpotifyImportState.Running("Đang đọc playlist “${pl.name}”…", i, request.playlists.size)
            try {
                playlistTracks[pl] = api.playlistTracks(pl.id)
            } catch (e: SpotifyApiException) {
                if (e.code == 401) throw e
                errors += "“${pl.name}”: ${if (e.code == 403 || e.code == 404) "Spotify không cho đọc playlist này" else e.message}"
            }
        }

        // 2) Match every distinct track to YouTube Music.
        val all = LinkedHashMap<String, SpotifyTrack>()
        (liked + playlistTracks.values.flatten()).forEach { all.putIfAbsent(it.id, it) }
        val toMatch = all.values.filter { it.id !in cache.matches }
        val doneCount = AtomicInteger(0)
        _state.value = SpotifyImportState.Running("Đang tìm bài trên YouTube Music…", 0, toMatch.size)

        // Songs whose search failed (network, rate limit) used to be silently left out, so the
        // imported library differed from run to run. They are now searched again, more gently,
        // before the library is written.
        var failedTracks = matchAll(toMatch, cache, SEARCH_PARALLELISM) {
            _state.value = SpotifyImportState.Running("Đang tìm bài trên YouTube Music…", doneCount.incrementAndGet(), toMatch.size)
        }
        saveCache(cache)
        var pass = 0
        while (failedTracks.isNotEmpty() && pass++ < RETRY_PASSES) {
            _state.value = SpotifyImportState.Running("Đang thử lại ${failedTracks.size} bài bị lỗi…", 0, failedTracks.size)
            delay(RETRY_PASS_DELAY_MS * pass)
            val done = AtomicInteger(0)
            val batch = failedTracks
            failedTracks = matchAll(batch, cache, RETRY_PARALLELISM) {
                _state.value = SpotifyImportState.Running("Đang thử lại ${batch.size} bài bị lỗi…", done.incrementAndGet(), batch.size)
            }
            saveCache(cache)
        }
        if (failedTracks.isNotEmpty()) {
            errors += "${failedTracks.size} bài không tìm được do lỗi mạng/YouTube, chạy lại để thử tiếp"
        }

        // 3) Write to the local library.
        _state.value = SpotifyImportState.Running("Đang lưu vào thư viện…", 0, 0)

        if (request.importLiked) {
            val already = database.likedSongIds().toHashSet()
            val now = LocalDateTime.now()
            var added = 0
            database.runInTransaction {
                // Spotify returns newest first; keep that order via descending "inLibrary" timestamps.
                liked.forEachIndexed { index, t ->
                    val m = cache.matches[t.id]
                    if (m == null) {
                        unmatched += UnmatchedTrack(t.title, t.artists.joinToString(", "), "Bài hát đã thích")
                        return@forEachIndexed
                    }
                    insert(m.toMediaMetadata())
                    if (m.ytId !in already) {
                        setSongLiked(m.ytId, true, now)
                        setSongInLibraryAt(m.ytId, now.minusSeconds(index.toLong()))
                        already += m.ytId
                        added++
                    }
                }
            }
            likedImported = added
        }

        playlistTracks.forEach { (pl, tracks) ->
            val matched = tracks.mapNotNull { t ->
                val m = cache.matches[t.id]
                if (m == null) unmatched += UnmatchedTrack(t.title, t.artists.joinToString(", "), pl.name)
                m
            }
            database.runInTransaction {
                val existing = cache.playlists[pl.id]?.takeIf { playlistEntityById(it) != null }
                val localId = existing ?: PlaylistEntity.generatePlaylistId().also { newId ->
                    insert(PlaylistEntity(id = newId, name = pl.name))
                    cache.playlists[pl.id] = newId
                }
                val present = playlistSongIdList(localId).toHashSet()
                var position = present.size
                matched.forEach { m ->
                    insert(m.toMediaMetadata())
                    if (present.add(m.ytId)) {
                        insert(PlaylistSongMap(playlistId = localId, songId = m.ytId, position = position++))
                        songsInPlaylists++
                    }
                }
            }
            playlistsImported++
        }
        saveCache(cache)

        return SpotifyImportResult(
            likedImported = likedImported,
            playlistsImported = playlistsImported,
            songsInPlaylists = songsInPlaylists,
            unmatched = unmatched,
            errors = errors,
        )
    }

    /** Matches [tracks] and stores hits in [cache]; returns the tracks whose search failed (as opposed to "not found"). */
    private suspend fun matchAll(
        tracks: List<SpotifyTrack>,
        cache: Cache,
        parallelism: Int,
        onEach: () -> Unit,
    ): List<SpotifyTrack> {
        val failed = java.util.Collections.synchronizedList(ArrayList<SpotifyTrack>())
        val gate = Semaphore(parallelism)
        coroutineScope {
            tracks.map { track ->
                async {
                    gate.withPermit {
                        when (val r = match(track)) {
                            is MatchResult.Found -> synchronized(cache) { cache.matches[track.id] = r.match }
                            is MatchResult.NotFound -> Unit
                            is MatchResult.Failed -> failed += track
                        }
                        onEach()
                    }
                }
            }.awaitAll()
        }
        return failed.toList()
    }

    private fun CachedMatch.toMediaMetadata() = MediaMetadata(
        id = ytId,
        title = title,
        artists = artists.map { MediaMetadata.Artist(it.id, it.name) },
        duration = durationSec,
        thumbnailUrl = thumbnail,
        album = albumId?.let { MediaMetadata.Album(it, albumTitle.orEmpty()) },
        explicit = explicit,
    )

    // ---------------------------------------------------------------- matching

    private sealed interface MatchResult {
        data class Found(val match: CachedMatch) : MatchResult
        data object NotFound : MatchResult
        data object Failed : MatchResult
    }

    private suspend fun match(track: SpotifyTrack): MatchResult {
        val firstArtist = track.artists.firstOrNull().orEmpty()
        val queries = listOf(
            "${track.title} $firstArtist",
            "${cleanTitle(track.title)} ${track.artists.joinToString(" ")}",
        ).map { it.trim() }.distinct()

        var failed = false
        for (q in queries) {
            val items = searchWithRetry(q)?.items?.filterIsInstance<SongItem>()
            if (items == null) {
                failed = true
                continue
            }
            val best = items
                .map { it to score(track, it) }
                .maxByOrNull { it.second }
                ?.takeIf { it.second >= ACCEPT_SCORE }
                ?.first
            if (best != null) return MatchResult.Found(best.toCached())
        }
        return if (failed) MatchResult.Failed else MatchResult.NotFound
    }

    /** One search, retried a couple of times with growing delay; null when it kept failing. */
    private suspend fun searchWithRetry(query: String): com.caohaidang.edward.innertube.pages.SearchResult? = run {
        var result: com.caohaidang.edward.innertube.pages.SearchResult? = null
        for (attempt in 0..SEARCH_RETRIES) {
            val r = YouTube.search(query, YouTube.SearchFilter.FILTER_SONG)
            result = r.getOrNull()
            if (result != null) break
            if (r.exceptionOrNull() is CancellationException) throw r.exceptionOrNull()!!
            if (attempt < SEARCH_RETRIES) delay(600L * (attempt + 1) + (0..250).random())
        }
        result
    }

    private fun SongItem.toCached() = CachedMatch(
        ytId = id,
        title = title,
        artists = artists.map { CachedArtist(it.id, it.name) },
        durationSec = duration ?: -1,
        thumbnail = toMediaMetadata().thumbnailUrl,
        albumId = album?.id,
        albumTitle = album?.name,
        explicit = explicit,
    )

    /** 0..1: title similarity (50%), artist overlap (30%), duration closeness (20%, can go negative). */
    private fun score(t: SpotifyTrack, c: SongItem): Double {
        val titleScore = titleSimilarity(t.title, c.title)
        if (titleScore < 0.5) return 0.0

        val candArtists = c.artists.map { norm(it.name) }
        val wanted = t.artists.map { norm(it) }.filter { it.isNotEmpty() }
        val artistScore = when {
            wanted.isEmpty() -> 0.5
            else -> wanted.count { w -> candArtists.any { it.contains(w) || w.contains(it) } || norm(c.title).contains(w) }
                .toDouble() / wanted.size
        }

        val durScore = if (t.durationMs > 0 && (c.duration ?: -1) > 0) {
            val diff = abs(t.durationMs / 1000.0 - c.duration!!)
            when {
                diff <= 3 -> 0.2
                diff <= 8 -> 0.1
                diff <= 15 -> 0.0
                else -> -0.25
            }
        } else 0.0

        return 0.5 * titleScore + 0.3 * artistScore + durScore
    }

    private fun titleSimilarity(a: String, b: String): Double {
        val na = norm(cleanTitle(a))
        val nb = norm(cleanTitle(b))
        if (na.isEmpty() || nb.isEmpty()) return 0.0
        if (na == nb) return 1.0
        if (na.contains(nb) || nb.contains(na)) return 0.85
        val ta = na.split(' ').toSet()
        val tb = nb.split(' ').toSet()
        return ta.intersect(tb).size.toDouble() / ta.union(tb).size
    }

    private companion object {
        const val SEARCH_PARALLELISM = 4
        const val RETRY_PARALLELISM = 2
        const val RETRY_PASSES = 2
        const val RETRY_PASS_DELAY_MS = 3_000L
        const val SEARCH_RETRIES = 2
        const val ACCEPT_SCORE = 0.62

        private val BRACKETS = Regex("""[\(\[][^)\]]*[\)\]]""")
        private val SUFFIX = Regex("""\s+-\s+(remaster(ed)?|\d{4}.*|live|single version|radio edit|mono|stereo|feat\..*|ft\..*).*$""", RegexOption.IGNORE_CASE)
        private val DIACRITICS = Regex("\\p{InCombiningDiacriticalMarks}+")
        private val NON_ALNUM = Regex("[^\\p{L}\\p{N}]+")

        /** Drops "(feat. X)", "[Remastered]", "- 2011 Remaster"… that differ between catalogues. */
        fun cleanTitle(s: String) = s.replace(BRACKETS, " ").replace(SUFFIX, "").trim()

        fun norm(s: String): String =
            Normalizer.normalize(s.lowercase().replace('đ', 'd'), Normalizer.Form.NFD)
                .replace(DIACRITICS, "")
                .replace(NON_ALNUM, " ")
                .trim()
    }
}
