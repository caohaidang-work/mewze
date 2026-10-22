package com.caohaidang.edward.music.sync

import android.content.Context
import android.util.Log
import com.caohaidang.edward.innertube.YouTube
import com.caohaidang.edward.innertube.models.PlaylistItem
import com.caohaidang.edward.innertube.models.SongItem
import com.caohaidang.edward.innertube.utils.parseCookieString
import com.caohaidang.edward.music.db.MusicDatabase
import com.caohaidang.edward.music.db.entities.AlbumEntity
import com.caohaidang.edward.music.db.entities.ArtistEntity
import com.caohaidang.edward.music.db.entities.PlaylistEntity
import com.caohaidang.edward.music.db.entities.PlaylistSongMap
import com.caohaidang.edward.music.models.toMediaMetadata
import com.caohaidang.edward.music.ui.utils.resize
import com.caohaidang.edward.music.utils.PlaylistCoverStorage
import com.caohaidang.edward.music.utils.isInternetAvailable
import com.caohaidang.edward.music.utils.reportException
import androidx.room.InvalidationTracker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class SyncSnapshot(
    val likedSongs: Set<String> = emptySet(),
    val albums: Set<String> = emptySet(),
    val artists: Set<String> = emptySet(),
    val playlists: Set<String> = emptySet(),

    val playlistStates: Map<String, PlaylistState> = emptyMap(),
    val syncedAt: Long = 0L,

    /**
     * Songs liked here and pushed to YouTube, but not yet seen in the YouTube "Liked music" list
     * (id -> time of the push). YouTube lists a new like with a delay, so these must NOT count as
     * "known on both sides" yet: if they did, a stale list would make the app drop the like locally.
     */
    val pendingLikes: Map<String, Long> = emptyMap(),

    /**
     * Items that were on both sides at the last sync but are missing from the latest YouTube list,
     * with how many syncs in a row that was the case. An item is only removed locally once it has
     * been missing for several syncs, so one truncated/stale response cannot wipe the library.
     * Keys are prefixed: "l:" liked song, "p:" playlist, "a:" album, "r:" artist.
     */
    val missing: Map<String, Int> = emptyMap(),
)

@Serializable
data class PlaylistState(
    val name: String,
    val songs: List<String>,
)

data class SyncSummary(
    val likedRemoteCount: Int = -1,
    val likedLocalCount: Int = -1,
    val likedPulled: Int = 0,
    val likedUnlikedLocally: Int = 0,
    val likedPushed: Int = 0,
    val likedUnlikedRemotely: Int = 0,
    val playlists: Int = 0,
    val albums: Int = 0,
    val artists: Int = 0,
    val playlistsPushed: Int = 0,
    val playlistsDeletedRemotely: Int = 0,
    val albumsPushed: Int = 0,
    val albumsRemoved: Int = 0,
    val warnings: List<String> = emptyList(),
)

sealed interface SyncState {
    data object Idle : SyncState
    data class Running(val step: String) : SyncState
    data class Done(val summary: SyncSummary) : SyncState
    data class Failed(val message: String) : SyncState
}

@Singleton
class LibrarySyncManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: MusicDatabase,
) {
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }
    private val snapshotFile get() = context.filesDir.resolve(SNAPSHOT_FILE)

    private val _state = MutableStateFlow<SyncState>(SyncState.Idle)
    val state: StateFlow<SyncState> = _state.asStateFlow()

    private val _lastSyncAt = MutableStateFlow(readSnapshot().syncedAt)
    val lastSyncAt: StateFlow<Long> = _lastSyncAt.asStateFlow()

    /** Emits once each time a failure streak starts (or a manual retry fails), so the UI can tell the user. */
    private val _failureEvents = MutableSharedFlow<String>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val failureEvents: SharedFlow<String> = _failureEvents.asSharedFlow()

    /** Earliest time an automatic (non-forced) sync may run again after failures; 0 = no backoff. */
    private val _retryAt = MutableStateFlow(0L)
    val retryAt: StateFlow<Long> = _retryAt.asStateFlow()

    @Volatile
    private var consecutiveFailures = 0

    val isLoggedIn: Boolean
        get() = YouTube.cookie?.let { "SAPISID" in parseCookieString(it) } == true

    /** Set when something asked for a forced sync while one was already running. */
    private val rerunRequested = AtomicBoolean(false)

    /** Fingerprint of the local playlists as they were when the last sync started. */
    @Volatile
    private var baselineFingerprint: Int? = null

    private val localChanges = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private var autoSyncStarted = false

    /**
     * Makes syncing fully automatic: it runs when the app opens (see MainActivity / App), shortly
     * after any local playlist change (create, rename, add/remove/reorder songs, delete), and
     * periodically while the app process is alive.
     */
    @OptIn(FlowPreview::class)
    @Synchronized
    fun startAutoSync(scope: CoroutineScope) {
        if (autoSyncStarted) return
        autoSyncStarted = true

        database.invalidationTracker.addObserver(
            object : InvalidationTracker.Observer("playlist", "playlist_song_map") {
                override fun onInvalidated(tables: Set<String>) {
                    localChanges.tryEmit(Unit)
                }
            }
        )

        scope.launch(Dispatchers.IO) {
            localChanges.debounce(CHANGE_DEBOUNCE_MS).collect {
                if (!isLoggedIn) return@collect
                val baseline = baselineFingerprint
                // Writes done by the sync itself end up in the baseline of the next run, so only a
                // real difference (made by the user) triggers another sync.
                if (baseline == null || playlistFingerprint() != baseline) syncAll(force = true)
            }
        }
        scope.launch {
            while (true) {
                delay(PERIODIC_INTERVAL_MS)
                syncAll()
            }
        }
    }

    /** User pressed "retry": ignores the backoff and runs right away. */
    suspend fun retryNow() {
        _retryAt.value = 0L
        syncAll(force = true, manual = true)
    }

    suspend fun syncAll(force: Boolean = false, manual: Boolean = false) {
        if (!isLoggedIn) return
        if (!isInternetAvailable(context)) {
            // Being offline is not a sync failure: no backoff, no error toast (except on manual retry).
            if (manual) {
                val msg = "Không có kết nối mạng"
                _state.value = SyncState.Failed(msg)
                _failureEvents.tryEmit(msg)
            }
            return
        }
        if (!force) {
            val now = System.currentTimeMillis()
            if (now < _retryAt.value) return // still backing off after a failure
            if (now - _lastSyncAt.value < MIN_INTERVAL_MS && consecutiveFailures == 0) return
        }
        if (!mutex.tryLock()) {
            if (force) rerunRequested.set(true)
            return
        }
        try {
            var runs = 0
            var failed = false
            do {
                rerunRequested.set(false)
                failed = withContext(Dispatchers.IO) { performSync() }
            } while (!failed && rerunRequested.get() && ++runs < MAX_RERUNS)
            onSyncFinished(failed, manual)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            reportException(e)
            val msg = e.message ?: "Đồng bộ thất bại"
            _state.value = SyncState.Failed(msg)
            onSyncFinished(failed = true, manual = manual, message = msg)
        } finally {
            mutex.unlock()
        }
    }

    /** Exponential backoff: 1, 2, 4 ... minutes, capped at [MAX_BACKOFF_MS]. Success resets it. */
    private fun onSyncFinished(failed: Boolean, manual: Boolean, message: String? = null) {
        if (!failed) {
            consecutiveFailures = 0
            _retryAt.value = 0L
            return
        }
        val first = consecutiveFailures == 0
        consecutiveFailures++
        val shift = (consecutiveFailures - 1).coerceAtMost(10)
        val delayMs = (BASE_BACKOFF_MS shl shift).coerceAtMost(MAX_BACKOFF_MS)
        _retryAt.value = System.currentTimeMillis() + delayMs
        if (first || manual) {
            _failureEvents.tryEmit(
                message ?: (_state.value as? SyncState.Done)?.summary?.warnings?.firstOrNull() ?: "Đồng bộ thất bại"
            )
        }
    }

    private fun playlistFingerprint(): Int {
        val sb = StringBuilder()
        (database.mirroredPlaylists() + database.localOnlyPlaylists())
            .sortedBy { it.id }
            .forEach { p ->
                sb.append(p.id).append('|').append(p.name).append('|').append(p.browseId).append('|')
                database.playlistSongIdList(p.id).forEach { sb.append(it).append(',') }
                sb.append('\n')
            }
        return sb.toString().hashCode()
    }

    fun reset() {
        snapshotFile.delete()
        baselineFingerprint = null
        _lastSyncAt.value = 0L
        consecutiveFailures = 0
        _retryAt.value = 0L
        _state.value = SyncState.Idle
    }

    /**
     * Last sync before signing out, so local changes (new playlists, likes...) reach YouTube Music
     * before the local data is wiped. Returns true only when everything was synced cleanly.
     */
    suspend fun syncBeforeLogout(): Boolean {
        if (!isLoggedIn) return true
        return withTimeoutOrNull(LOGOUT_SYNC_TIMEOUT_MS) {
            syncAll(force = true)
            // If a sync was already running, syncAll only queued a rerun: wait until it is really done.
            mutex.withLock { }
            (_state.value as? SyncState.Done)?.summary?.warnings?.isEmpty() == true
        } ?: false
    }

    /** Sign-out: waits for a running sync, then deletes all data of the previous account. */
    suspend fun wipeAccountData() = withContext(Dispatchers.IO + NonCancellable) {
        mutex.withLock {
            database.clearAllUserData()
            reset()
        }
    }

    /** @return true when at least one step failed. */
    private suspend fun performSync(): Boolean {
        baselineFingerprint = playlistFingerprint()
        val old = readSnapshot()
        var snap = old
        var summary = SyncSummary()
        val warnings = mutableListOf<String>()
        var stepFailed = false
        var networkDown = false
        var missing = old.missing

        /** Runs one step; once the network is down the remaining steps are skipped instead of timing out one by one. */
        suspend fun runStep(name: String, block: suspend () -> Unit) {
            if (networkDown) return
            step(name, block)?.let { err ->
                warnings += "$name: ${err.message}"
                stepFailed = true
                if (err.network) networkDown = true
            }
        }
        fun updateMissing(prefix: String, next: Map<String, Int>) {
            missing = missing.filterKeys { !it.startsWith(prefix) } + next
        }

        runStep("Bài hát đã thích") {
            val r = syncLikedSongs(old)
            snap = snap.copy(likedSongs = r.newSnapshotIds, pendingLikes = r.pending)
            updateMissing("l:", r.missing)
            summary = summary.copy(
                likedRemoteCount = r.remoteCount,
                likedLocalCount = r.localCount,
                likedPulled = r.pulled,
                likedUnlikedLocally = r.unlikedLocally,
                likedPushed = r.pushed,
                likedUnlikedRemotely = r.unlikedRemotely,
            )
            warnings += r.warnings
        }

        runStep("Playlist") {
            val r = syncPlaylists(old)
            snap = snap.copy(playlists = r.ids, playlistStates = r.states)
            updateMissing("p:", r.missing)
            summary = summary.copy(
                playlists = r.ids.size,
                playlistsPushed = r.pushed,
                playlistsDeletedRemotely = r.deletedRemotely,
            )
            warnings += r.warnings
        }

        runStep("Album") {
            val r = syncAlbums(old.albums, old.missing)
            snap = snap.copy(albums = r.ids)
            updateMissing("a:", r.missing)
            summary = summary.copy(albums = r.ids.size, albumsPushed = r.pushed, albumsRemoved = r.removed)
            warnings += r.warnings
        }

        runStep("Nghệ sĩ") {
            val r = syncArtists(old.artists, old.missing)
            snap = snap.copy(artists = r.ids)
            updateMissing("r:", r.missing)
            summary = summary.copy(artists = r.ids.size)
        }

        val now = System.currentTimeMillis()
        val syncedAt = if (stepFailed) old.syncedAt else now
        writeSnapshot(snap.copy(syncedAt = syncedAt, missing = missing))
        _lastSyncAt.value = syncedAt
        _state.value = SyncState.Done(summary.copy(warnings = warnings))
        return stepFailed
    }

    private data class StepError(val message: String, val network: Boolean)

    private suspend fun step(name: String, block: suspend () -> Unit): StepError? {
        _state.value = SyncState.Running(name)
        var attempt = 0
        while (true) {
            try {
                block()
                return null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Transient trouble (network, 429/5xx, a half-loaded response): retry with growing delay.
                if (isRetryable(e) && ++attempt <= STEP_RETRIES) {
                    delay(retryDelay(attempt))
                    continue
                }
                reportException(e)
                Log.w(TAG, "bước \"$name\" thất bại", e)
                return StepError(e.message ?: e.javaClass.simpleName, network = e.isNetworkError())
            }
        }
    }

    // ------------------------------------------------------------ retry helpers

    private fun Throwable.isNetworkError(): Boolean =
        this is java.io.IOException || cause?.isNetworkError() == true

    /** Everything except an explicit 4xx (other than 429 "slow down") is worth another try. */
    private fun isRetryable(e: Throwable): Boolean {
        if (e is CancellationException) return false
        if (e.isNetworkError()) return true
        val name = e.javaClass.name
        val msg = e.message.orEmpty()
        if (name.contains("ClientRequestException")) return "429" in msg
        if (name.contains("RedirectResponseException")) return false
        return true // ServerResponseException, parse hiccups, "playlist not available yet"...
    }

    /** 1.5 s, 3 s, 6 s ... plus jitter so parallel calls do not retry in lock-step. */
    private fun retryDelay(attempt: Int): Long =
        (STEP_RETRY_DELAY_MS shl (attempt - 1).coerceAtMost(4)) + (0..400).random()

    /** Calls a YouTube API that returns [Result], retrying transient failures. */
    private suspend fun <T> ytRetry(retries: Int = CALL_RETRIES, call: suspend () -> Result<T>): Result<T> {
        var attempt = 0
        while (true) {
            val r = call()
            val e = r.exceptionOrNull() ?: return r
            if (e is CancellationException) throw e
            if (!isRetryable(e) || ++attempt > retries) return r
            delay(retryDelay(attempt))
        }
    }

    /** Counts consecutive syncs in which an item was missing remotely; see [SyncSnapshot.missing]. */
    private class MissingTracker(private val old: Map<String, Int>, private val prefix: String) {
        val next = mutableMapOf<String, Int>()

        /** Records one more absence of [id]; true once it has been absent [needed] times in a row. */
        fun confirmed(id: String, needed: Int = MISSING_SYNCS_TO_REMOVE): Boolean {
            val n = (old[prefix + id] ?: 0) + 1
            next[prefix + id] = n
            return n >= needed
        }

        /** The remote list looked unreliable this time: keep earlier counts, add nothing. */
        fun hold(id: String) {
            old[prefix + id]?.let { next[prefix + id] = it }
        }
    }

    private data class LikedResult(
        val remoteCount: Int,
        val localCount: Int,
        val newSnapshotIds: Set<String>,
        val pending: Map<String, Long>,
        val missing: Map<String, Int>,
        val pulled: Int,
        val unlikedLocally: Int,
        val pushed: Int,
        val unlikedRemotely: Int,
        val warnings: List<String>,
    )

    /**
     * Two-way merge of the local likes and the YouTube Music "Liked music" list (playlist "LM"),
     * using the last synced state as the common ancestor.
     *
     * Why a like can never vanish because of a bad response:
     *  - a like pushed to YouTube is only "synced" after it has been seen in the remote list
     *    ([SyncSnapshot.pendingLikes]); YouTube lists new likes with a delay;
     *  - a like is only removed locally when it is missing from several consecutive, complete
     *    lists ([SyncSnapshot.missing]); a truncated list is detected and ignored.
     */
    private suspend fun syncLikedSongs(old: SyncSnapshot): LikedResult {
        val last = old.likedSongs
        val now = System.currentTimeMillis()
        val warnings = mutableListOf<String>()

        var fetched = fetchPlaylist("LM")
        if (fetched.looksIncomplete()) {
            // Probably a truncated response: ask once more and keep the longer list.
            delay(retryDelay(1))
            fetchPlaylist("LM").let { if (it.songs.size > fetched.songs.size) fetched = it }
        }
        val remoteSongs = fetched.songs
        val remoteIds = remoteSongs.map { it.id }.toSet()
        val localLiked = database.likedSongIds().toSet()

        var listTrusted = !fetched.looksIncomplete()
        if (remoteIds.isEmpty() && last.isNotEmpty()) {
            listTrusted = false
            warnings += "YouTube trả về danh sách thích rỗng; bỏ qua việc xoá lượt thích trên máy."
        } else if (!listTrusted) {
            Log.w(TAG, "danh sách thích không đầy đủ: ${remoteIds.size}/${fetched.expectedCount}")
        }

        // Pending likes: drop the ones that arrived (or that the user has since undone).
        val pending = old.pendingLikes.filterKeys { it in localLiked && it !in remoteIds }.toMutableMap()
        val undonePending = old.pendingLikes.keys.filter { it !in localLiked && it in remoteIds }

        val tracker = MissingTracker(old.missing, "l:")
        val addLocal = (remoteIds - localLiked) - last - old.pendingLikes.keys
        val unlikeRemote = ((remoteIds intersect last) - localLiked) + undonePending
        val likeRemote = ((localLiked - remoteIds) - last)
            .filter { id -> pending[id]?.let { now - it >= PENDING_REPUSH_MS } ?: true }

        // Liked on both sides at the last sync but missing now: unlike locally only when it is confirmed.
        val absent = (localLiked intersect last) - remoteIds
        val massRemoval = absent.size > maxOf(MASS_REMOVAL_MIN, last.size / 4)
        val unlikeLocal = mutableSetOf<String>()
        absent.forEach { id ->
            if (!listTrusted) tracker.hold(id)
            else if (tracker.confirmed(id, if (massRemoval) MISSING_SYNCS_MASS_REMOVE else MISSING_SYNCS_TO_REMOVE)) unlikeLocal += id
        }
        if (massRemoval && listTrusted && unlikeLocal.isEmpty()) {
            warnings += "${absent.size} bài thích biến mất khỏi YouTube cùng lúc; chờ xác nhận thêm trước khi xoá trên máy."
        }
        unlikeLocal.forEach { tracker.next.remove("l:$it") }

        val stamp = LocalDateTime.now()
        database.runInTransaction {
            remoteSongs.filter { it.id in addLocal }.forEach { song ->
                insert(song.toMediaMetadata())
                setSongLiked(song.id, true, stamp)
            }
            unlikeLocal.forEach { setSongLiked(it, false, stamp) }
        }
        applyRemoteLikedOrder(remoteSongs.map { it.id })

        val pushedOk = mutableSetOf<String>()
        val unlikedOk = mutableSetOf<String>()
        var pushFailed = 0
        var pushError: String? = null
        var streak = 0
        val onPushFailure: (Throwable) -> Unit = { e ->
            pushFailed++
            streak++
            if (pushError == null) pushError = e.message ?: e.javaClass.simpleName
            Log.w(TAG, "push like/unlike thất bại", e)
        }
        for (id in likeRemote.take(MAX_LIKE_PUSH_PER_SYNC)) {
            if (streak >= PUSH_FAIL_STREAK) break // YouTube is throttling or down: stop hammering it
            ytRetry(retries = 1) { YouTube.like(id) }
                .onSuccess { pushedOk += id; streak = 0 }.onFailure(onPushFailure)
            delay(PUSH_PACING_MS)
        }
        for (id in unlikeRemote.take(MAX_LIKE_PUSH_PER_SYNC)) {
            if (streak >= PUSH_FAIL_STREAK) break
            ytRetry(retries = 1) { YouTube.unlike(id) }
                .onSuccess { unlikedOk += id; streak = 0 }.onFailure(onPushFailure)
            delay(PUSH_PACING_MS)
        }
        if (pushFailed > 0) {
            warnings += "Không đẩy được $pushFailed thay đổi lượt thích lên YouTube: $pushError"
        }
        val backlog = likeRemote.size - pushedOk.size - pushFailed
        if (backlog > 0 || unlikeRemote.size > MAX_LIKE_PUSH_PER_SYNC) {
            if (streak < PUSH_FAIL_STREAK) rerunRequested.set(true) // keep going right away
            else warnings += "Còn thay đổi chưa đẩy lên YouTube; sẽ tiếp tục ở lần đồng bộ sau."
        }

        pushedOk.forEach { pending[it] = now }

        // "Synced" = seen on YouTube. Likes that are only locally unlike-pending stay in the snapshot
        // until confirmed missing, otherwise they would be mistaken for brand-new likes.
        val keptWhileAbsent = absent - unlikeLocal
        val newSnapshot = (remoteIds - unlikedOk) + keptWhileAbsent
        Log.d(TAG, "liked: remote=${remoteIds.size} local=${localLiked.size} last=${last.size} trusted=$listTrusted " +
                "addLocal=${addLocal.size} unlikeLocal=${unlikeLocal.size} likeRemote=${likeRemote.size} " +
                "unlikeRemote=${unlikeRemote.size} pending=${pending.size}")
        return LikedResult(
            remoteCount = remoteIds.size,
            localCount = localLiked.size,
            newSnapshotIds = newSnapshot,
            pending = pending,
            missing = tracker.next,
            pulled = addLocal.size,
            unlikedLocally = unlikeLocal.size,
            pushed = pushedOk.size,
            unlikedRemotely = unlikedOk.size,
            warnings = warnings,
        )
    }

    private data class PlaylistResult(
        val ids: Set<String>,
        val states: Map<String, PlaylistState>,
        val pushed: Int,
        val deletedRemotely: Int,
        val missing: Map<String, Int>,
        val warnings: List<String>,
    )

    private suspend fun syncPlaylists(old: SyncSnapshot): PlaylistResult {
        val last = old.playlists
        val lastStates = old.playlistStates
        val remote = ytRetry { YouTube.likedPlaylists() }.getOrThrow()
            .filter { it.id != "LM" && it.id != "SE" }
        val remoteIds = remote.map { it.id }.toSet()
        val warnings = mutableListOf<String>()
        val newStates = mutableMapOf<String, PlaylistState>()
        var pushed = 0
        var deletedRemotely = 0

        val mirrors = database.mirroredPlaylists().associateBy { it.browseId }

        val deletedLocally = remote.filter { it.id !in mirrors && it.id in lastStates }
        val prefetched = prefetchPlaylists(remote.filter { it.id in mirrors || it.id !in lastStates })
        val allowRemoteDelete = deletedLocally.size <= MAX_REMOTE_DELETES_PER_SYNC
        if (!allowRemoteDelete) {
            warnings += "Có ${deletedLocally.size} playlist biến mất khỏi máy cùng lúc; bỏ qua việc xoá trên YouTube để an toàn."
        }

        remote.forEach { item ->
            val mirror = mirrors[item.id]
            val lastState = lastStates[item.id]
            try {
                if (mirror == null && lastState != null) {
                    if (!allowRemoteDelete) {
                        newStates[item.id] = lastState
                    } else if (deletePlaylistOnYouTube(item.id)) {
                        deletedRemotely++
                    } else {
                        newStates[item.id] = lastState
                        warnings += "Không xoá được playlist \"${item.title}\" trên YouTube."
                    }
                } else {
                    val fetched = prefetched[item.id]!!.getOrThrow()
                    if (lastState != null && fetched.looksIncomplete()) {
                        // Never rewrite a playlist from a truncated download; try again next sync.
                        newStates[item.id] = lastState
                        warnings += "Playlist \"${item.title}\" tải chưa đủ bài, bỏ qua lần này."
                    } else {
                        val (state, didPush) = mergePlaylist(item, fetched, mirror, lastState, warnings)
                        newStates[item.id] = state
                        if (didPush) pushed++
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                reportException(e)
                lastState?.let { newStates[item.id] = it }
                warnings += "Không đồng bộ được playlist \"${item.title}\"."
            }
        }

        val createdIds = mutableSetOf<String>()
        database.localOnlyPlaylists().take(MAX_NEW_PLAYLISTS_PER_SYNC).forEach { local ->
            try {
                val songIds = database.playlistSongIdList(local.id).distinct().take(MAX_PLAYLIST_CREATE_SONGS)
                val newId = YouTube.createPlaylist(local.name, songIds).getOrThrow()
                database.setPlaylistBrowseId(local.id, newId)
                newStates[newId] = PlaylistState(local.name, songIds)
                createdIds += newId
                pushed++
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                reportException(e)
                warnings += "Không tạo được playlist \"${local.name}\" trên YouTube: ${e.message ?: e.javaClass.simpleName}"
            }
        }

        val tracker = MissingTracker(old.missing, "p:")
        if (remoteIds.isNotEmpty()) {
            // A playlist vanishing from the remote list is only believed after it stayed away for
            // several syncs; a single short or stale library page must not delete local playlists.
            val gone = database.mirroredPlaylists()
                .filter { it.browseId != null && it.browseId !in remoteIds && it.browseId in last }
                .filter { tracker.confirmed(it.browseId!!) }
            if (gone.isNotEmpty()) {
                database.runInTransaction {
                    gone.forEach {
                        delete(it)
                        PlaylistCoverStorage.deleteFor(context, it.id)
                        tracker.next.remove("p:${it.browseId}")
                    }
                }
            }
        } else if (last.isNotEmpty()) {
            warnings += "YouTube trả về danh sách playlist rỗng; giữ nguyên playlist trên máy."
        }

        applyRemotePlaylistOrder(remote.map { it.id })

        return PlaylistResult(
            ids = remoteIds + createdIds,
            states = newStates,
            pushed = pushed,
            deletedRemotely = deletedRemotely,
            missing = tracker.next,
            warnings = warnings,
        )
    }

    /** Downloads the playlists in parallel (a few at a time); each one keeps its own success/failure. */
    private suspend fun prefetchPlaylists(items: List<PlaylistItem>): Map<String, Result<FetchedPlaylist>> {
        val gate = Semaphore(PLAYLIST_PREFETCH_PARALLELISM)
        return coroutineScope {
            items.map { item ->
                async {
                    gate.withPermit {
                        item.id to try {
                            var f = fetchPlaylist(item.id)
                            if (f.looksIncomplete()) {
                                delay(retryDelay(1))
                                runCatching { fetchPlaylist(item.id) }.getOrNull()
                                    ?.takeIf { it.songs.size > f.songs.size }?.let { f = it }
                            }
                            Result.success(f)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Result.failure(e)
                        }
                    }
                }
            }.awaitAll().toMap()
        }
    }

    private suspend fun deletePlaylistOnYouTube(id: String): Boolean =
        ytRetry(retries = 1) { YouTube.deletePlaylist(id) }.isSuccess || ytRetry(retries = 1) { YouTube.unlikePlaylist(id) }.isSuccess

    private suspend fun mergePlaylist(
        item: PlaylistItem,
        fetched: FetchedPlaylist,
        mirror: PlaylistEntity?,
        last: PlaylistState?,
        warnings: MutableList<String>,
    ): Pair<PlaylistState, Boolean> {
        val remoteSongs = fetched.songs
        val remoteIds = remoteSongs.map { it.id }
        val remoteName = item.title

        if (mirror == null || last == null) {
            writeLocalPlaylist(item.id, mirror, remoteName, remoteSongs, remoteIds, item.thumbnail)
            return PlaylistState(remoteName, remoteIds) to false
        }

        val localIds = database.playlistSongIdList(mirror.id)
        val lastSet = last.songs.toSet()
        val localSet = localIds.toSet()
        val remoteSet = remoteIds.toSet()

        val addedLocal = localIds.filter { it !in lastSet && it !in remoteSet }.distinct()
        val removedLocal = lastSet - localSet
        val removeOnRemote = remoteSongs.filter { it.id in removedLocal }

        var didPush = false
        var failed = false

        // Order: if only the local side was reordered, replay the reorder on YouTube. The first
        // song decides the playlist cover there, so this also keeps covers in step.
        var reorderedIds: List<String>? = null
        run {
            val common = localSet intersect remoteSet intersect lastSet
            val orderLocal = localIds.filter { it in common }.distinct()
            val orderLast = last.songs.filter { it in common }.distinct()
            val orderRemote = remoteIds.filter { it in common }.distinct()
            if (orderLocal == orderLast || orderRemote != orderLast || orderLocal.size < 2) return@run

            val current = remoteSongs.map { it.id to it.setVideoId }.toMutableList()
            if (current.any { it.second == null }) return@run
            val slots = current.indices.filter { current[it].first in common }
            val setIdByVideo = current.associate { it.first to it.second }
            val target = current.toMutableList()
            slots.forEachIndexed { k, slot -> target[slot] = orderLocal[k] to setIdByVideo[orderLocal[k]] }

            val moves = mutableListOf<Pair<String, String>>()
            for (i in target.indices) {
                if (current[i].first == target[i].first) continue
                val j = (i + 1 until current.size).firstOrNull { current[it].first == target[i].first } ?: continue
                val moved = current.removeAt(j)
                moves += moved.second!! to current[i].second!!
                current.add(i, moved)
            }
            if (moves.isEmpty() || moves.size > MAX_REORDER_MOVES) return@run
            YouTube.reorderPlaylist(item.id, moves)
                .onSuccess { didPush = true; reorderedIds = target.map { it.first } }
                .onFailure { failed = true; reportException(it) }
        }

        val addedOk = mutableSetOf<String>()
        addedLocal.take(MAX_PUSH_PER_SYNC).let { batch ->
            if (batch.isNotEmpty()) {
                YouTube.addToPlaylist(item.id, batch)
                    .onSuccess { addedOk += batch; didPush = true }
                    .onFailure { failed = true; reportException(it) }
            }
        }

        val removeItems = removeOnRemote.mapNotNull { song -> song.setVideoId?.let { song.id to it } }
            .take(MAX_PUSH_PER_SYNC)
        val removedOk = mutableSetOf<String>()
        if (removeItems.isNotEmpty()) {
            YouTube.removeFromPlaylist(item.id, removeItems)
                .onSuccess { removedOk += removeItems.map { it.first }; didPush = true }
                .onFailure { failed = true; reportException(it) }
        }
        val removeStuck = removeOnRemote.map { it.id }.toSet() - removedOk

        var nameOk = true
        val remoteRenamed = remoteName != last.name
        if (mirror.name != last.name && !remoteRenamed) {
            ytRetry(retries = 1) { YouTube.renamePlaylist(item.id, mirror.name) }
                .onSuccess { didPush = true }
                .onFailure { nameOk = false; failed = true; reportException(it) }
        }
        val finalName = if (remoteRenamed) remoteName else mirror.name

        val finalIds = (reorderedIds ?: remoteIds).filter { it !in removedLocal } + addedLocal
        if (finalIds != localIds || finalName != mirror.name || coverFor(mirror, item.thumbnail) != mirror.customThumbnail) {
            writeLocalPlaylist(mirror.id, mirror, finalName, remoteSongs, finalIds, item.thumbnail)
        }

        if (failed) {
            warnings += "Không đẩy được thay đổi của playlist \"$finalName\" lên YouTube " +
                    "(playlist lưu từ người khác thì không sửa được)."
        }
        if (addedLocal.size > MAX_PUSH_PER_SYNC || removeOnRemote.size > MAX_PUSH_PER_SYNC) {
            warnings += "Playlist \"$finalName\" còn thay đổi chưa đẩy lên YouTube; sẽ tiếp tục ở lần sau."
        }

        val stateSongs = (finalIds - (addedLocal.toSet() - addedOk)) + removeStuck
        val stateName = if (nameOk) finalName else last.name
        return PlaylistState(stateName, stateSongs.distinct()) to didPush
    }

    private fun writeLocalPlaylist(
        browseId: String,
        mirror: PlaylistEntity?,
        name: String,
        remoteSongs: List<SongItem>,
        orderedIds: List<String>,
        remoteCover: String?,
    ) {
        database.runInTransaction {
            val existing = mirror ?: mirroredPlaylists().firstOrNull { it.browseId == browseId }
            val localId = existing?.id ?: PlaylistEntity.generatePlaylistId().also {
                insert(PlaylistEntity(id = it, name = name, browseId = browseId, customThumbnail = normalizeCover(remoteCover)))
            }
            if (existing != null) {
                val cover = coverFor(existing, remoteCover)
                if (existing.name != name || existing.customThumbnail != cover) {
                    update(existing.copy(name = name, customThumbnail = cover))
                }
            }
            remoteSongs.forEach { insert(it.toMediaMetadata()) }
            clearPlaylist(localId)
            orderedIds.forEachIndexed { index, songId ->
                insert(PlaylistSongMap(playlistId = localId, songId = songId, position = index))
            }
        }
    }

    /**
     * The cover a mirrored playlist should have after a sync. A picture the user chose on this
     * device always wins; otherwise the playlist follows its cover on YouTube Music (which changes
     * when the first song changes).
     */
    private fun coverFor(local: PlaylistEntity, remoteCover: String?): String? =
        if (PlaylistCoverStorage.isLocalFile(local.customThumbnail)) local.customThumbnail
        else normalizeCover(remoteCover) ?: local.customThumbnail

    private fun normalizeCover(url: String?): String? = url?.takeIf { it.isNotBlank() }?.resize(544, 544)

    private data class AlbumResult(
        val ids: Set<String>,
        val pushed: Int,
        val removed: Int,
        val missing: Map<String, Int>,
        val warnings: List<String>,
    )

    private suspend fun syncAlbums(last: Set<String>, oldMissing: Map<String, Int>): AlbumResult {
        val remote = ytRetry { YouTube.libraryAlbums() }.getOrThrow()
        val remoteById = remote.associateBy { it.browseId }
        val remoteIds = remoteById.keys
        val localBookmarked = database.bookmarkedAlbumIds().toSet()
        val warnings = mutableListOf<String>()

        val addLocal = (remoteIds - localBookmarked) - last
        val removeRemote = (remoteIds - localBookmarked) intersect last
        val absent = (localBookmarked - remoteIds) intersect last
        val addRemote = (localBookmarked - remoteIds) - last

        // Only trust "album is gone" after it stayed missing for several syncs in a row.
        val tracker = MissingTracker(oldMissing, "a:")
        val removeLocal = mutableSetOf<String>()
        if (remoteIds.isEmpty() && last.isNotEmpty()) {
            absent.forEach { tracker.hold(it) }
            warnings += "YouTube trả về danh sách album rỗng; giữ nguyên album trên máy."
        } else {
            absent.forEach { if (tracker.confirmed(it)) removeLocal += it }
            removeLocal.forEach { tracker.next.remove("a:$it") }
        }

        val now = LocalDateTime.now()
        database.runInTransaction {
            addLocal.mapNotNull { remoteById[it] }.forEach { item ->
                val existing = albumEntity(item.browseId)
                if (existing == null) {
                    insert(
                        AlbumEntity(
                            id = item.browseId,
                            title = item.title,
                            year = item.year,
                            thumbnailUrl = item.thumbnail,
                            songCount = 0,
                            duration = 0,
                            bookmarkedAt = now,
                        )
                    )
                } else {
                    setAlbumBookmarked(item.browseId, now)
                }
            }
            removeLocal.forEach { setAlbumBookmarked(it, null) }
        }
        applyRemoteAlbumOrder(remote.map { it.browseId })

        val addedOk = mutableSetOf<String>()
        val removedOk = mutableSetOf<String>()
        var failedCount = 0
        var firstError: String? = null
        val onFailure: (Throwable) -> Unit = { e ->
            failedCount++
            if (firstError == null) firstError = e.message ?: e.javaClass.simpleName
            Log.w(TAG, "push album thất bại", e)
        }
        addRemote.take(MAX_PUSH_PER_SYNC).forEach { browseId ->
            ytRetry(retries = 1) { YouTube.album(browseId, withSongs = false) }
                .mapCatching { ytRetry(retries = 1) { YouTube.likePlaylist(it.album.playlistId) }.getOrThrow() }
                .onSuccess { addedOk += browseId }
                .onFailure(onFailure)
        }
        removeRemote.take(MAX_PUSH_PER_SYNC).forEach { browseId ->
            val playlistId = remoteById[browseId]?.playlistId ?: return@forEach
            ytRetry(retries = 1) { YouTube.unlikePlaylist(playlistId) }
                .onSuccess { removedOk += browseId }
                .onFailure(onFailure)
        }
        if (failedCount > 0) {
            warnings += "Không đẩy được $failedCount thay đổi album lên YouTube: $firstError"
        }
        if (addRemote.size > MAX_PUSH_PER_SYNC || removeRemote.size > MAX_PUSH_PER_SYNC) {
            warnings += "Còn thay đổi album chưa đẩy lên YouTube; sẽ tiếp tục ở lần đồng bộ sau."
        }

        return AlbumResult(
            // Albums still waiting for removal stay in the snapshot, otherwise they would look brand new.
            ids = (remoteIds - removedOk) + addedOk + (absent - removeLocal),
            pushed = addedOk.size,
            removed = removedOk.size,
            missing = tracker.next,
            warnings = warnings,
        )
    }

    private data class ArtistResult(val ids: Set<String>, val missing: Map<String, Int>)

    private suspend fun syncArtists(last: Set<String>, oldMissing: Map<String, Int>): ArtistResult {
        val remote = ytRetry { YouTube.libraryArtists() }.getOrThrow()
        val remoteIds = remote.map { it.id }.toSet()
        val now = LocalDateTime.now()
        val tracker = MissingTracker(oldMissing, "r:")
        val stillListed = mutableSetOf<String>()
        database.runInTransaction {
            remote.forEach { item ->
                val existing = artistEntity(item.id)
                if (existing == null) {
                    insert(
                        ArtistEntity(
                            id = item.id,
                            name = item.title,
                            thumbnailUrl = item.thumbnail,
                            bookmarkedAt = now,
                        )
                    )
                } else if (existing.bookmarkedAt == null) {
                    setArtistBookmarked(item.id, now)
                }
            }
            if (remoteIds.isNotEmpty()) {
                (last - remoteIds).forEach { id ->
                    if (tracker.confirmed(id)) {
                        setArtistBookmarked(id, null)
                        tracker.next.remove("r:$id")
                    } else {
                        stillListed += id
                    }
                }
            } else {
                (last - remoteIds).forEach { tracker.hold(it); stillListed += it }
            }
        }
        return ArtistResult(remoteIds + stillListed, tracker.next)
    }

    /**
     * YouTube Music lists the library newest-first. The date shown/sorted in the app follows that
     * order, so after a sync the local timestamps are re-stamped to match it (only when the local
     * order differs, to avoid rewriting every row on each sync).
     */
    private fun stampFor(base: LocalDateTime, index: Int): LocalDateTime = base.minusNanos(index * 1_000_000L)

    private fun applyRemoteLikedOrder(remoteOrder: List<String>) {
        val liked = database.likedSongIds().toSet()
        val expected = remoteOrder.distinct().filter { it in liked }
        val remoteSet = expected.toSet()
        val current = database.likedSongIdsNewestFirst().filter { it in remoteSet }
        if (current == expected) return
        val base = LocalDateTime.now()
        database.runInTransaction {
            expected.forEachIndexed { index, id -> setSongInLibraryAt(id, stampFor(base, index)) }
        }
    }

    private fun applyRemoteAlbumOrder(remoteOrder: List<String>) {
        val saved = database.bookmarkedAlbumIds().toSet()
        val expected = remoteOrder.distinct().filter { it in saved }
        val remoteSet = expected.toSet()
        val current = database.bookmarkedAlbumIdsNewestFirst().filter { it in remoteSet }
        if (current == expected) return
        val base = LocalDateTime.now()
        database.runInTransaction {
            expected.forEachIndexed { index, id -> setAlbumBookmarked(id, stampFor(base, index)) }
        }
    }

    private fun applyRemotePlaylistOrder(remoteOrder: List<String>) {
        val localByBrowseId = database.mirroredPlaylists().associate { it.browseId to it.id }
        val expected = remoteOrder.distinct().mapNotNull { localByBrowseId[it] }
        val remoteSet = expected.toSet()
        val current = database.mirroredPlaylistIdsNewestFirst().filter { it in remoteSet }
        if (current == expected) return
        val base = LocalDateTime.now()
        database.runInTransaction {
            expected.forEachIndexed { index, id -> setPlaylistCreatedAt(id, stampFor(base, index)) }
        }
    }

    /** A playlist as downloaded; [expectedCount] is the song count YouTube shows in the header (if it could be read). */
    private class FetchedPlaylist(val songs: List<SongItem>, val expectedCount: Int?) {
        /** True when clearly fewer songs arrived than the header promises (truncated paging, stale page...). */
        fun looksIncomplete(): Boolean {
            val expected = expectedCount ?: return false
            if (expected - songs.size < INCOMPLETE_MIN_GAP) return false
            return songs.size < expected * INCOMPLETE_RATIO
        }
    }

    /** Downloads every page of a playlist; any page that fails (after retries) fails the whole call, never a partial list. */
    private suspend fun fetchPlaylist(playlistId: String): FetchedPlaylist {
        val page = ytRetry { YouTube.playlist(playlistId) }.getOrThrow()
        val songs = page.songs.toMutableList()
        val seenTokens = HashSet<String>()
        var continuation = page.songsContinuation
        var pages = 0
        while (continuation != null && pages++ < MAX_PAGES) {
            if (!seenTokens.add(continuation)) break // YouTube handed back the same token: avoid an endless loop
            val token = continuation
            val next = ytRetry { YouTube.playlistContinuation(token) }.getOrThrow()
            songs += next.songs
            continuation = next.continuation
        }
        return FetchedPlaylist(songs, parseCount(page.playlist.songCountText))
    }

    private fun parseCount(text: String?): Int? =
        text?.let { COUNT_REGEX.find(it) }?.value?.filter { it.isDigit() }?.toIntOrNull()

    private fun readSnapshot(): SyncSnapshot = try {
        if (snapshotFile.exists()) json.decodeFromString(SyncSnapshot.serializer(), snapshotFile.readText())
        else SyncSnapshot()
    } catch (e: Exception) {
        SyncSnapshot()
    }

    private fun writeSnapshot(snapshot: SyncSnapshot) {
        val tmp = snapshotFile.resolveSibling("$SNAPSHOT_FILE.tmp")
        tmp.writeText(json.encodeToString(SyncSnapshot.serializer(), snapshot))
        if (!tmp.renameTo(snapshotFile)) {
            snapshotFile.writeText(tmp.readText())
            tmp.delete()
        }
    }

    private companion object {
        const val TAG = "LibrarySync"
        const val SNAPSHOT_FILE = "sync_snapshot.json"
        const val MIN_INTERVAL_MS = 4 * 60 * 1000L
        const val PERIODIC_INTERVAL_MS = 5 * 60 * 1000L
        const val CHANGE_DEBOUNCE_MS = 3_000L
        const val MAX_RERUNS = 3
        const val BASE_BACKOFF_MS = 60_000L
        const val MAX_BACKOFF_MS = 60 * 60_000L
        const val STEP_RETRIES = 3
        const val STEP_RETRY_DELAY_MS = 1_500L
        const val CALL_RETRIES = 2
        const val PLAYLIST_PREFETCH_PARALLELISM = 3
        const val MISSING_SYNCS_TO_REMOVE = 2
        const val MISSING_SYNCS_MASS_REMOVE = 4
        const val MASS_REMOVAL_MIN = 10
        const val MAX_LIKE_PUSH_PER_SYNC = 250
        const val PUSH_FAIL_STREAK = 5
        const val PUSH_PACING_MS = 150L
        const val PENDING_REPUSH_MS = 6 * 60 * 60_000L
        const val INCOMPLETE_MIN_GAP = 3
        const val INCOMPLETE_RATIO = 0.8
        val COUNT_REGEX = Regex("""\d[\d.,\u00A0\u202F ]*""")
        const val MAX_REORDER_MOVES = 60
        const val MAX_PAGES = 100
        const val MAX_PUSH_PER_SYNC = 100
        const val MAX_NEW_PLAYLISTS_PER_SYNC = 10
        const val MAX_PLAYLIST_CREATE_SONGS = 200
        const val MAX_REMOTE_DELETES_PER_SYNC = 3
        const val LOGOUT_SYNC_TIMEOUT_MS = 30_000L
    }
}
