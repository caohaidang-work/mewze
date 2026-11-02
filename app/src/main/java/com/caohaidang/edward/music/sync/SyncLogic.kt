package com.caohaidang.edward.music.sync

/*
 * Pure decision logic of the YouTube Music <-> local library sync. It has no Android, Room or network
 * dependency on purpose, so it can be unit-tested on the plain JVM (see SyncLogicTest).
 * LibrarySyncManager does the I/O (fetching, DB writes, pushing likes) and delegates the "what should
 * change" decisions to the functions below.
 */

/** Tuning values the decisions depend on. LibrarySyncManager's companion re-exports them. */
internal object SyncLimits {
    /** Consecutive syncs an item must be absent from YouTube before it is removed locally. */
    const val MISSING_SYNCS_TO_REMOVE = 2

    /** Same, when many items vanish at once (more likely a bad response than a real mass delete). */
    const val MISSING_SYNCS_MASS_REMOVE = 4

    /** A removal is "mass" above max(this, a quarter of the previously synced items). */
    const val MASS_REMOVAL_MIN = 10

    /** A like that was pushed but never seen on YouTube is pushed again after this long. */
    const val PENDING_REPUSH_MS = 6 * 60 * 60_000L

    const val INCOMPLETE_MIN_GAP = 3
    const val INCOMPLETE_RATIO = 0.8
}

/** Counts consecutive syncs in which an item was missing remotely; see [SyncSnapshot.missing]. */
internal class MissingTracker(private val old: Map<String, Int>, private val prefix: String) {
    val next = mutableMapOf<String, Int>()

    /** Records one more absence of [id]; true once it has been absent [needed] times in a row. */
    fun confirmed(id: String, needed: Int = SyncLimits.MISSING_SYNCS_TO_REMOVE): Boolean {
        val n = (old[prefix + id] ?: 0) + 1
        next[prefix + id] = n
        return n >= needed
    }

    /** The remote list looked unreliable this time: keep earlier counts, add nothing. */
    fun hold(id: String) {
        old[prefix + id]?.let { next[prefix + id] = it }
    }
}

/**
 * True when clearly fewer items arrived than the header promised (truncated paging, stale page...).
 * [expected] is null when the count could not be read, in which case the list is trusted.
 */
internal fun isIncompleteList(expected: Int?, received: Int): Boolean {
    if (expected == null) return false
    if (expected - received < SyncLimits.INCOMPLETE_MIN_GAP) return false
    return received < expected * SyncLimits.INCOMPLETE_RATIO
}

/** What a liked-songs sync decided to do, before any I/O happens. */
internal data class LikedPlan(
    /** On YouTube only and new: add + like locally. */
    val addLocal: Set<String>,
    /** Liked on both sides last time, confirmed gone from YouTube: unlike locally. */
    val unlikeLocal: Set<String>,
    /** Liked locally only and new: push a like to YouTube. */
    val likeRemote: List<String>,
    /** Unliked locally since last time but still liked on YouTube: push an unlike. */
    val unlikeRemote: Set<String>,
    /** Liked on both sides last time but absent from the current YouTube list (confirmed or not). */
    val absent: Set<String>,
    /** Pushed-but-not-yet-seen likes that are still waiting to show up on YouTube. */
    val pending: Map<String, Long>,
    /** New "missing for N syncs in a row" counters (keys prefixed "l:"). */
    val missing: Map<String, Int>,
    /** Many likes vanished at once and none was removed yet: the UI should say it is waiting. */
    val massRemovalHeld: Boolean,
)

/**
 * Two-way merge of local likes and the YouTube "Liked music" list, using the last synced state
 * ([last]) as the common ancestor. See LibrarySyncManager.syncLikedSongs for the rationale.
 *
 * @param pendingLikes likes pushed earlier but not yet seen in the remote list (id -> push time)
 * @param missing counters from the previous snapshot
 * @param listTrusted false when the remote list looks truncated/empty: nothing is removed locally then
 */
internal fun planLikedSync(
    last: Set<String>,
    pendingLikes: Map<String, Long>,
    missing: Map<String, Int>,
    remoteIds: Set<String>,
    localLiked: Set<String>,
    listTrusted: Boolean,
    now: Long,
): LikedPlan {
    // Pending likes: drop the ones that arrived (or that the user has since undone).
    val pending = pendingLikes.filterKeys { it in localLiked && it !in remoteIds }
    val undonePending = pendingLikes.keys.filter { it !in localLiked && it in remoteIds }

    val tracker = MissingTracker(missing, "l:")
    val addLocal = (remoteIds - localLiked) - last - pendingLikes.keys
    val unlikeRemote = ((remoteIds intersect last) - localLiked) + undonePending
    val likeRemote = ((localLiked - remoteIds) - last)
        .filter { id -> pending[id]?.let { now - it >= SyncLimits.PENDING_REPUSH_MS } ?: true }

    // Liked on both sides at the last sync but missing now: unlike locally only when it is confirmed.
    val absent = (localLiked intersect last) - remoteIds
    val massRemoval = absent.size > maxOf(SyncLimits.MASS_REMOVAL_MIN, last.size / 4)
    val unlikeLocal = mutableSetOf<String>()
    absent.forEach { id ->
        if (!listTrusted) {
            tracker.hold(id)
        } else {
            val needed = if (massRemoval) SyncLimits.MISSING_SYNCS_MASS_REMOVE else SyncLimits.MISSING_SYNCS_TO_REMOVE
            if (tracker.confirmed(id, needed)) unlikeLocal += id
        }
    }
    unlikeLocal.forEach { tracker.next.remove("l:$it") }

    return LikedPlan(
        addLocal = addLocal,
        unlikeLocal = unlikeLocal,
        likeRemote = likeRemote,
        unlikeRemote = unlikeRemote,
        absent = absent,
        pending = pending,
        missing = tracker.next,
        massRemovalHeld = massRemoval && listTrusted && unlikeLocal.isEmpty(),
    )
}

/** The state to remember after the pushes of a liked-songs sync have been attempted. */
internal data class LikedSettlement(
    /** New [SyncSnapshot.likedSongs]. */
    val snapshotIds: Set<String>,
    /** New [SyncSnapshot.pendingLikes]. */
    val pending: Map<String, Long>,
)

/**
 * "Synced" means seen on YouTube: a like we just pushed ([pushedOk]) goes to `pending`, not into the
 * snapshot, until it shows up in the remote list. An unlike that failed to push ([unlikedOk] lacks it)
 * stays in the snapshot so it is retried instead of being mistaken for a new like.
 */
internal fun settleLiked(
    plan: LikedPlan,
    remoteIds: Set<String>,
    pushedOk: Set<String>,
    unlikedOk: Set<String>,
    now: Long,
): LikedSettlement {
    val pending = plan.pending.toMutableMap()
    pushedOk.forEach { pending[it] = now }
    // Likes that are only locally unlike-pending stay in the snapshot until confirmed missing.
    val keptWhileAbsent = plan.absent - plan.unlikeLocal
    return LikedSettlement(
        snapshotIds = (remoteIds - unlikedOk) + keptWhileAbsent,
        pending = pending,
    )
}
