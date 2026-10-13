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
import com.caohaidang.edward.music.utils.reportException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
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

    val isLoggedIn: Boolean
        get() = YouTube.cookie?.let { "SAPISID" in parseCookieString(it) } == true

    suspend fun syncAll(force: Boolean = false) {
        if (!isLoggedIn) return
        if (!force && System.currentTimeMillis() - _lastSyncAt.value < MIN_INTERVAL_MS) return
        if (!mutex.tryLock()) return
        try {
            withContext(Dispatchers.IO) { performSync() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            reportException(e)
            _state.value = SyncState.Failed(e.message ?: "Đồng bộ thất bại")
        } finally {
            mutex.unlock()
        }
    }

    fun reset() {
        snapshotFile.delete()
        _lastSyncAt.value = 0L
        _state.value = SyncState.Idle
    }

    private suspend fun performSync() {
        val old = readSnapshot()
        var snap = old
        var summary = SyncSummary()
        val warnings = mutableListOf<String>()
        var stepFailed = false

        step("Bài hát đã thích") {
            val r = syncLikedSongs(old.likedSongs)
            snap = snap.copy(likedSongs = r.newSnapshotIds)
            summary = summary.copy(
                likedRemoteCount = r.remoteCount,
                likedLocalCount = r.localCount,
                likedPulled = r.pulled,
                likedUnlikedLocally = r.unlikedLocally,
                likedPushed = r.pushed,
                likedUnlikedRemotely = r.unlikedRemotely,
            )
            warnings += r.warnings
        }?.let { warnings += "Bài hát đã thích: $it"; stepFailed = true }

        step("Playlist") {
            val r = syncPlaylists(old)
            snap = snap.copy(playlists = r.ids, playlistStates = r.states)
            summary = summary.copy(
                playlists = r.ids.size,
                playlistsPushed = r.pushed,
                playlistsDeletedRemotely = r.deletedRemotely,
            )
            warnings += r.warnings
        }?.let { warnings += "Playlist: $it"; stepFailed = true }

        step("Album") {
            val r = syncAlbums(old.albums)
            snap = snap.copy(albums = r.ids)
            summary = summary.copy(albums = r.ids.size, albumsPushed = r.pushed, albumsRemoved = r.removed)
            warnings += r.warnings
        }?.let { warnings += "Album: $it"; stepFailed = true }

        step("Nghệ sĩ") {
            val ids = syncArtists(old.artists)
            snap = snap.copy(artists = ids)
            summary = summary.copy(artists = ids.size)
        }?.let { warnings += "Nghệ sĩ: $it"; stepFailed = true }

        val now = System.currentTimeMillis()
        val syncedAt = if (stepFailed) old.syncedAt else now
        writeSnapshot(snap.copy(syncedAt = syncedAt))
        _lastSyncAt.value = syncedAt
        _state.value = SyncState.Done(summary.copy(warnings = warnings))
    }

    private suspend fun step(name: String, block: suspend () -> Unit): String? {
        _state.value = SyncState.Running(name)
        return try {
            block()
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            reportException(e)
            Log.w(TAG, "bước \"$name\" thất bại", e)
            e.message ?: e.javaClass.simpleName
        }
    }

    private data class LikedResult(
        val remoteCount: Int,
        val localCount: Int,
        val newSnapshotIds: Set<String>,
        val pulled: Int,
        val unlikedLocally: Int,
        val pushed: Int,
        val unlikedRemotely: Int,
        val warnings: List<String>,
    )

    private suspend fun syncLikedSongs(last: Set<String>): LikedResult {
        val remoteSongs = fetchPlaylistSongs("LM")
        val remoteIds = remoteSongs.map { it.id }.toSet()
        val localLiked = database.likedSongIds().toSet()
        val warnings = mutableListOf<String>()

        val addLocal = (remoteIds - localLiked) - last
        val unlikeRemote = (remoteIds intersect last) - localLiked
        var unlikeLocal = (localLiked intersect last) - remoteIds
        val likeRemote = (localLiked - remoteIds) - last

        if (remoteIds.isEmpty() && last.isNotEmpty()) {
            unlikeLocal = emptySet()
            warnings += "YouTube trả về danh sách thích rỗng; bỏ qua việc xoá lượt thích trên máy."
        }

        val now = LocalDateTime.now()
        database.runInTransaction {
            remoteSongs.filter { it.id in addLocal }.forEach { song ->
                insert(song.toMediaMetadata())
                setSongLiked(song.id, true, now)
            }
            unlikeLocal.forEach { setSongLiked(it, false, now) }
        }

        val pushedOk = mutableSetOf<String>()
        val unlikedOk = mutableSetOf<String>()
        var pushFailed = 0
        var pushError: String? = null
        val onPushFailure: (Throwable) -> Unit = { e ->
            pushFailed++
            if (pushError == null) pushError = e.message ?: e.javaClass.simpleName
            Log.w(TAG, "push like/unlike thất bại", e)
        }
        likeRemote.take(MAX_PUSH_PER_SYNC).forEach { id ->
            YouTube.like(id).onSuccess { pushedOk += id }.onFailure(onPushFailure)
        }
        unlikeRemote.take(MAX_PUSH_PER_SYNC).forEach { id ->
            YouTube.unlike(id).onSuccess { unlikedOk += id }.onFailure(onPushFailure)
        }
        if (pushFailed > 0) {
            warnings += "Không đẩy được $pushFailed thay đổi lượt thích lên YouTube: $pushError"
        }
        if (likeRemote.size > MAX_PUSH_PER_SYNC || unlikeRemote.size > MAX_PUSH_PER_SYNC) {
            warnings += "Còn thay đổi chưa đẩy lên YouTube; sẽ tiếp tục ở lần đồng bộ sau."
        }

        val newSnapshot = (remoteIds - unlikedOk) + pushedOk
        Log.d(TAG, "liked: remote=${remoteIds.size} local=${localLiked.size} last=${last.size} " +
                "addLocal=${addLocal.size} unlikeLocal=${unlikeLocal.size} likeRemote=${likeRemote.size} unlikeRemote=${unlikeRemote.size}")
        return LikedResult(
            remoteCount = remoteIds.size,
            localCount = localLiked.size,
            newSnapshotIds = newSnapshot,
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
        val warnings: List<String>,
    )

    private suspend fun syncPlaylists(old: SyncSnapshot): PlaylistResult {
        val last = old.playlists
        val lastStates = old.playlistStates
        val remote = YouTube.likedPlaylists().getOrThrow()
            .filter { it.id != "LM" && it.id != "SE" }
        val remoteIds = remote.map { it.id }.toSet()
        val warnings = mutableListOf<String>()
        val newStates = mutableMapOf<String, PlaylistState>()
        var pushed = 0
        var deletedRemotely = 0

        val mirrors = database.mirroredPlaylists().associateBy { it.browseId }

        val deletedLocally = remote.filter { it.id !in mirrors && it.id in lastStates }
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
                    val (state, didPush) = mergePlaylist(item, mirror, lastState, warnings)
                    newStates[item.id] = state
                    if (didPush) pushed++
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

        if (remoteIds.isNotEmpty()) {
            database.runInTransaction {
                mirroredPlaylists()
                    .filter { it.browseId != null && it.browseId !in remoteIds && it.browseId in last }
                    .forEach { delete(it) }
            }
        } else if (last.isNotEmpty()) {
            warnings += "YouTube trả về danh sách playlist rỗng; giữ nguyên playlist trên máy."
        }

        return PlaylistResult(
            ids = remoteIds + createdIds,
            states = newStates,
            pushed = pushed,
            deletedRemotely = deletedRemotely,
            warnings = warnings,
        )
    }

    private suspend fun deletePlaylistOnYouTube(id: String): Boolean =
        YouTube.deletePlaylist(id).isSuccess || YouTube.unlikePlaylist(id).isSuccess

    private suspend fun mergePlaylist(
        item: PlaylistItem,
        mirror: PlaylistEntity?,
        last: PlaylistState?,
        warnings: MutableList<String>,
    ): Pair<PlaylistState, Boolean> {
        val remoteSongs = fetchPlaylistSongs(item.id)
        val remoteIds = remoteSongs.map { it.id }
        val remoteName = item.title

        if (mirror == null || last == null) {
            writeLocalPlaylist(item.id, mirror, remoteName, remoteSongs, remoteIds)
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
            YouTube.renamePlaylist(item.id, mirror.name)
                .onSuccess { didPush = true }
                .onFailure { nameOk = false; failed = true; reportException(it) }
        }
        val finalName = if (remoteRenamed) remoteName else mirror.name

        val finalIds = remoteIds.filter { it !in removedLocal } + addedLocal
        if (finalIds != localIds || finalName != mirror.name) {
            writeLocalPlaylist(mirror.id, mirror, finalName, remoteSongs, finalIds)
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
    ) {
        database.runInTransaction {
            val existing = mirror ?: mirroredPlaylists().firstOrNull { it.browseId == browseId }
            val localId = existing?.id ?: PlaylistEntity.generatePlaylistId().also {
                insert(PlaylistEntity(id = it, name = name, browseId = browseId))
            }
            if (existing != null && existing.name != name) {
                update(existing.copy(name = name))
            }
            remoteSongs.forEach { insert(it.toMediaMetadata()) }
            clearPlaylist(localId)
            orderedIds.forEachIndexed { index, songId ->
                insert(PlaylistSongMap(playlistId = localId, songId = songId, position = index))
            }
        }
    }

    private data class AlbumResult(
        val ids: Set<String>,
        val pushed: Int,
        val removed: Int,
        val warnings: List<String>,
    )

    private suspend fun syncAlbums(last: Set<String>): AlbumResult {
        val remote = YouTube.libraryAlbums().getOrThrow()
        val remoteById = remote.associateBy { it.browseId }
        val remoteIds = remoteById.keys
        val localBookmarked = database.bookmarkedAlbumIds().toSet()
        val warnings = mutableListOf<String>()

        val addLocal = (remoteIds - localBookmarked) - last
        val removeRemote = (remoteIds - localBookmarked) intersect last
        var removeLocal = (localBookmarked - remoteIds) intersect last
        val addRemote = (localBookmarked - remoteIds) - last

        if (remoteIds.isEmpty() && last.isNotEmpty()) {
            removeLocal = emptySet()
            warnings += "YouTube trả về danh sách album rỗng; giữ nguyên album trên máy."
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
            YouTube.album(browseId, withSongs = false)
                .mapCatching { YouTube.likePlaylist(it.album.playlistId).getOrThrow() }
                .onSuccess { addedOk += browseId }
                .onFailure(onFailure)
        }
        removeRemote.take(MAX_PUSH_PER_SYNC).forEach { browseId ->
            val playlistId = remoteById[browseId]?.playlistId ?: return@forEach
            YouTube.unlikePlaylist(playlistId)
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
            ids = (remoteIds - removedOk) + addedOk,
            pushed = addedOk.size,
            removed = removedOk.size,
            warnings = warnings,
        )
    }

    private suspend fun syncArtists(last: Set<String>): Set<String> {
        val remote = YouTube.libraryArtists().getOrThrow()
        val remoteIds = remote.map { it.id }.toSet()
        val now = LocalDateTime.now()
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
                (last - remoteIds).forEach { setArtistBookmarked(it, null) }
            }
        }
        return remoteIds
    }

    private suspend fun fetchPlaylistSongs(playlistId: String): List<SongItem> {
        val page = YouTube.playlist(playlistId).getOrThrow()
        val songs = page.songs.toMutableList()
        var continuation = page.songsContinuation
        var pages = 0
        while (continuation != null && pages++ < MAX_PAGES) {
            val next = YouTube.playlistContinuation(continuation).getOrThrow()
            songs += next.songs
            continuation = next.continuation
        }
        return songs
    }

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
        const val MIN_INTERVAL_MS = 15 * 60 * 1000L
        const val MAX_PAGES = 100
        const val MAX_PUSH_PER_SYNC = 100
        const val MAX_NEW_PLAYLISTS_PER_SYNC = 10
        const val MAX_PLAYLIST_CREATE_SONGS = 200
        const val MAX_REMOTE_DELETES_PER_SYNC = 3
    }
}
