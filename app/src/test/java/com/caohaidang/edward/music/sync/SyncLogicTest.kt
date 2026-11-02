package com.caohaidang.edward.music.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Regression tests for the "liked songs appear and disappear" bug described in CHANGES_SYNC.md.
 * Each test names the failure it protects against.
 */
class LikedSyncPlanTest {
    private val now = 1_000_000_000L
    private val hour = 60 * 60_000L

    private fun plan(
        last: Set<String> = emptySet(),
        local: Set<String> = emptySet(),
        remote: Set<String> = emptySet(),
        pending: Map<String, Long> = emptyMap(),
        missing: Map<String, Int> = emptyMap(),
        trusted: Boolean = true,
        at: Long = now,
    ) = planLikedSync(last, pending, missing, remote, local, trusted, at)

    // ---- plain two-way merge -------------------------------------------------------------------

    @Test
    fun `a like made on YouTube is pulled to the device`() {
        val p = plan(last = setOf("a"), local = setOf("a"), remote = setOf("a", "b"))
        assertEquals(setOf("b"), p.addLocal)
        assertTrue(p.likeRemote.isEmpty())
        assertTrue(p.unlikeLocal.isEmpty())
    }

    @Test
    fun `a like made on the device is pushed to YouTube`() {
        val p = plan(last = setOf("a"), local = setOf("a", "c"), remote = setOf("a"))
        assertEquals(listOf("c"), p.likeRemote)
        assertTrue(p.addLocal.isEmpty())
    }

    @Test
    fun `an unlike made on the device is pushed to YouTube and not undone by the pull`() {
        val p = plan(last = setOf("a", "b"), local = setOf("a"), remote = setOf("a", "b"))
        assertEquals(setOf("b"), p.unlikeRemote)
        assertFalse("b" in p.addLocal, "b was liked at the last sync, so it is not a new remote like")
    }

    @Test
    fun `first sync with no snapshot merges both sides instead of deleting either`() {
        val p = plan(last = emptySet(), local = setOf("x"), remote = setOf("y"))
        assertEquals(setOf("y"), p.addLocal)
        assertEquals(listOf("x"), p.likeRemote)
        assertTrue(p.unlikeLocal.isEmpty() && p.unlikeRemote.isEmpty())
    }

    // ---- pending likes: YouTube lists a new like late ------------------------------------------

    @Test
    fun `a like pushed a moment ago is not pushed again and not dropped while YouTube is slow`() {
        val p = plan(
            last = setOf("a"), local = setOf("a", "c"), remote = setOf("a"),
            pending = mapOf("c" to now - 1 * hour),
        )
        assertTrue(p.likeRemote.isEmpty(), "still within the re-push window")
        assertTrue(p.unlikeLocal.isEmpty())
        assertEquals(setOf("c"), p.pending.keys, "must keep waiting for it to show up")
    }

    @Test
    fun `a pending like that never shows up is pushed again after the re-push window`() {
        val p = plan(
            last = setOf("a"), local = setOf("a", "c"), remote = setOf("a"),
            pending = mapOf("c" to now - 7 * hour),
        )
        assertEquals(listOf("c"), p.likeRemote)
    }

    @Test
    fun `a pending like that finally appears on YouTube is settled and not pulled twice`() {
        val p = plan(
            last = setOf("a"), local = setOf("a", "c"), remote = setOf("a", "c"),
            pending = mapOf("c" to now - hour),
        )
        assertTrue(p.pending.isEmpty())
        assertFalse("c" in p.addLocal)
        assertTrue(p.unlikeLocal.isEmpty())
    }

    @Test
    fun `unliking a pending like on the device removes it from YouTube too`() {
        val p = plan(
            last = setOf("a"), local = setOf("a"), remote = setOf("a", "c"),
            pending = mapOf("c" to now - hour),
        )
        assertEquals(setOf("c"), p.unlikeRemote)
        assertFalse("c" in p.addLocal, "the user just undid it; it must not come back")
    }

    @Test
    fun `settling treats a pushed like as pending, never as synced`() {
        val remote = setOf("a")
        val p = plan(last = setOf("a"), local = setOf("a", "c"), remote = remote)
        val s = settleLiked(p, remote, pushedOk = setOf("c"), unlikedOk = emptySet(), now = now)
        assertEquals(setOf("a"), s.snapshotIds, "c is not in the snapshot until YouTube lists it")
        assertEquals(mapOf("c" to now), s.pending)
    }

    // ---- removals are confirmed, never immediate -----------------------------------------------

    @Test
    fun `a like missing from YouTube is only removed locally on the second sync in a row`() {
        val first = plan(last = setOf("a", "b"), local = setOf("a", "b"), remote = setOf("a"))
        assertTrue(first.unlikeLocal.isEmpty(), "one missing response must not delete anything")
        assertEquals(mapOf("l:b" to 1), first.missing)

        val second = plan(last = setOf("a", "b"), local = setOf("a", "b"), remote = setOf("a"), missing = first.missing)
        assertEquals(setOf("b"), second.unlikeLocal)
        assertFalse("l:b" in second.missing, "counter is cleared once removed")
    }

    @Test
    fun `an item that comes back resets its missing counter`() {
        val p = plan(
            last = setOf("a", "b"), local = setOf("a", "b"), remote = setOf("a", "b"),
            missing = mapOf("l:b" to 1),
        )
        assertTrue(p.missing.isEmpty())
        assertTrue(p.unlikeLocal.isEmpty())
    }

    @Test
    fun `a truncated or empty remote list never removes anything and never advances the counters`() {
        val last = setOf("a", "b", "c")
        val p = plan(last = last, local = last, remote = emptySet(), missing = mapOf("l:a" to 1), trusted = false)
        assertTrue(p.unlikeLocal.isEmpty())
        assertEquals(mapOf("l:a" to 1), p.missing, "held as it was: no increment, no new entries")
        assertEquals(last, p.absent)
    }

    @Test
    fun `mass disappearance needs four syncs instead of two`() {
        val last = (1..40).map { "s$it" }.toSet()
        val remote = (1..20).map { "s$it" }.toSet() // 20 vanish: more than max(10, 40/4)
        val gone = last - remote

        val early = plan(last = last, local = last, remote = remote, missing = gone.associate { "l:$it" to 1 })
        assertTrue(early.unlikeLocal.isEmpty(), "second sync is not enough for a mass removal")
        assertTrue(early.massRemovalHeld)

        val confirmed = plan(last = last, local = last, remote = remote, missing = gone.associate { "l:$it" to 3 })
        assertEquals(gone, confirmed.unlikeLocal)
        assertFalse(confirmed.massRemovalHeld)
    }

    @Test
    fun `exactly at the mass-removal threshold still counts as a normal removal`() {
        val last = (1..40).map { "s$it" }.toSet()
        val remote = (1..30).map { "s$it" }.toSet() // 10 vanish: not more than max(10, 10)
        val gone = last - remote
        val p = plan(last = last, local = last, remote = remote, missing = gone.associate { "l:$it" to 1 })
        assertEquals(gone, p.unlikeLocal)
        assertFalse(p.massRemovalHeld)
    }

    @Test
    fun `an unlike that failed to push stays in the snapshot so it is retried, not re-pulled`() {
        val remote = setOf("a", "b")
        val p = plan(last = setOf("a", "b"), local = setOf("a"), remote = remote)
        val failed = settleLiked(p, remote, pushedOk = emptySet(), unlikedOk = emptySet(), now = now)
        assertEquals(setOf("a", "b"), failed.snapshotIds)

        val ok = settleLiked(p, remote, pushedOk = emptySet(), unlikedOk = setOf("b"), now = now)
        assertEquals(setOf("a"), ok.snapshotIds)
    }

    @Test
    fun `a like waiting for removal confirmation stays in the snapshot`() {
        val remote = setOf("a")
        val p = plan(last = setOf("a", "b"), local = setOf("a", "b"), remote = remote)
        val s = settleLiked(p, remote, emptySet(), emptySet(), now)
        assertEquals(setOf("a", "b"), s.snapshotIds, "b is not forgotten before it is confirmed gone")
    }
}

class IncompleteListTest {
    @Test
    fun `unknown expected count is trusted`() = assertFalse(isIncompleteList(null, 0))

    @Test
    fun `small gaps are tolerated`() = assertFalse(isIncompleteList(expected = 5, received = 3))

    @Test
    fun `a full list is complete`() = assertFalse(isIncompleteList(100, 100))

    @Test
    fun `just under 80 percent is incomplete`() {
        assertTrue(isIncompleteList(100, 79))
        assertFalse(isIncompleteList(100, 80))
    }

    @Test
    fun `gap of exactly three needs to be under the ratio too`() {
        assertTrue(isIncompleteList(10, 7))
        assertFalse(isIncompleteList(10, 8))
    }

    @Test
    fun `receiving more than announced is fine`() = assertFalse(isIncompleteList(10, 12))
}

class MissingTrackerTest {
    @Test
    fun `counts consecutive absences and reports confirmation at the threshold`() {
        val t = MissingTracker(emptyMap(), "p:")
        assertFalse(t.confirmed("x"))
        assertEquals(mapOf("p:x" to 1), t.next)

        val t2 = MissingTracker(t.next, "p:")
        assertTrue(t2.confirmed("x"))
        assertEquals(mapOf("p:x" to 2), t2.next)
    }

    @Test
    fun `prefixes keep playlists, albums and artists apart`() {
        val t = MissingTracker(mapOf("a:x" to 5), "p:")
        assertFalse(t.confirmed("x"), "an album counter must not confirm a playlist")
    }

    @Test
    fun `hold keeps earlier counts and adds nothing new`() {
        val t = MissingTracker(mapOf("l:a" to 1), "l:")
        t.hold("a")
        t.hold("never-seen")
        assertEquals(mapOf("l:a" to 1), t.next)
    }
}
