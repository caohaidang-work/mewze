package com.caohaidang.edward.music.sync

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * sync_snapshot.json lives on users' devices across app updates. If it stops parsing, readSnapshot()
 * silently falls back to an empty snapshot and the next sync treats everything as "first sync".
 * These tests keep older/newer files readable.
 */
class SyncSnapshotTest {
    // Same configuration as LibrarySyncManager.
    private val json = Json { ignoreUnknownKeys = true }

    private fun decode(text: String) = json.decodeFromString(SyncSnapshot.serializer(), text)

    @Test
    fun `a snapshot written before pendingLikes and missing existed still loads`() {
        val s = decode("""{"likedSongs":["a","b"],"albums":["x"],"syncedAt":123}""")
        assertEquals(setOf("a", "b"), s.likedSongs)
        assertEquals(setOf("x"), s.albums)
        assertEquals(123L, s.syncedAt)
        assertEquals(emptyMap(), s.pendingLikes)
        assertEquals(emptyMap(), s.missing)
    }

    @Test
    fun `fields added by a future version are ignored instead of failing`() {
        val s = decode("""{"likedSongs":["a"],"someFutureField":{"x":1}}""")
        assertEquals(setOf("a"), s.likedSongs)
    }

    @Test
    fun `a full snapshot survives a round trip`() {
        val original = SyncSnapshot(
            likedSongs = setOf("a", "b"),
            playlists = setOf("p"),
            playlistStates = mapOf("p" to PlaylistState("Mix", listOf("a", "b"))),
            syncedAt = 42L,
            pendingLikes = mapOf("c" to 7L),
            missing = mapOf("l:b" to 1, "p:q" to 3),
        )
        val back = decode(json.encodeToString(SyncSnapshot.serializer(), original))
        assertEquals(original, back)
    }
}
