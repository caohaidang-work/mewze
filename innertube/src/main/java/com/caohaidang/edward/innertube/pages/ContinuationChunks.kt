package com.caohaidang.edward.innertube.pages

import com.caohaidang.edward.innertube.models.GridRenderer
import com.caohaidang.edward.innertube.models.MusicShelfRenderer
import com.caohaidang.edward.innertube.models.getContinuation
import com.caohaidang.edward.innertube.models.response.BrowseResponse
import com.caohaidang.edward.innertube.models.trailingContinuation
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * YouTube Music hands out "next page" data in two shapes, and which one a session gets can change:
 *  - classic: `continuationContents.<shelf>Continuation` + `continuations[].nextContinuationData`
 *  - newer:   `onResponseReceivedActions[].appendContinuationItemsAction.continuationItems`, with the
 *             next token carried by a trailing `continuationItemRenderer`.
 * Reading only the classic one silently cuts long lists (liked songs, playlists) short after the first
 * page, which made the library sync think songs had been removed. These helpers understand both.
 */
@OptIn(ExperimentalSerializationApi::class)
private val chunkJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
}

internal data class ShelfChunk(val items: List<MusicShelfRenderer.Content>, val continuation: String?)

internal data class GridChunk(val items: List<GridRenderer.Item>, val continuation: String?)

/** Next page of a song list / shelf, or null when the response holds neither shape. */
internal fun BrowseResponse.shelfChunk(): ShelfChunk? {
    val classic = continuationContents?.musicPlaylistShelfContinuation ?: continuationContents?.musicShelfContinuation
    if (classic != null) {
        return ShelfChunk(
            items = classic.contents,
            continuation = classic.continuations?.getContinuation() ?: classic.contents.trailingContinuation(),
        )
    }
    val appended = appendedItems()
    if (appended.isEmpty()) return null
    val items = appended.decodeAll(MusicShelfRenderer.Content.serializer())
    return ShelfChunk(items, items.trailingContinuation())
}

/** Next page of a grid (library albums / playlists), or null when the response holds neither shape. */
internal fun BrowseResponse.gridChunk(): GridChunk? {
    val classic = continuationContents?.gridContinuation
    if (classic != null) {
        return GridChunk(
            items = classic.items,
            continuation = classic.continuations?.getContinuation() ?: classic.items.trailingContinuation(),
        )
    }
    val appended = appendedItems()
    if (appended.isEmpty()) return null
    val items = appended.decodeAll(GridRenderer.Item.serializer())
    return GridChunk(items, items.trailingContinuation())
}

private fun BrowseResponse.appendedItems(): List<JsonElement> =
    onResponseReceivedActions.orEmpty().flatMap { it.appendContinuationItemsAction?.continuationItems.orEmpty() }

private fun <T> List<JsonElement>.decodeAll(serializer: KSerializer<T>): List<T> =
    mapNotNull { runCatching { chunkJson.decodeFromJsonElement(serializer, it) }.getOrNull() }
