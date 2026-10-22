package com.caohaidang.edward.innertube.models

import kotlinx.serialization.Serializable

@Serializable
data class MusicShelfRenderer(
    val title: Runs?,
    val contents: List<Content>?,
    val bottomEndpoint: NavigationEndpoint?,
    val moreContentButton: Button?,
    val continuations: List<Continuation>?,
) {
    @Serializable
    data class Content(
        val musicResponsiveListItemRenderer: MusicResponsiveListItemRenderer? = null,
        /** Newer pagination: the token for the next page is carried by a trailing item instead of `continuations`. */
        val continuationItemRenderer: ContinuationItemRenderer? = null,
    )
}

/** Marker item at the end of a list that holds the token of the next page (newer YouTube Music responses). */
@Serializable
data class ContinuationItemRenderer(
    val continuationEndpoint: ContinuationEndpoint? = null,
) {
    @Serializable
    data class ContinuationEndpoint(val continuationCommand: ContinuationCommand? = null)

    @Serializable
    data class ContinuationCommand(val token: String? = null)

    val token: String? get() = continuationEndpoint?.continuationCommand?.token
}

@JvmName("trailingContinuationOfShelf")
fun List<MusicShelfRenderer.Content>?.trailingContinuation(): String? =
    this?.lastOrNull { it.continuationItemRenderer != null }?.continuationItemRenderer?.token

fun List<Continuation>.getContinuation() =
    firstOrNull()?.nextContinuationData?.continuation
