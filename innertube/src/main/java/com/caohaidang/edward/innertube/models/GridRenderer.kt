package com.caohaidang.edward.innertube.models

import kotlinx.serialization.Serializable

@Serializable
data class GridRenderer(
    val header: Header?,
    val items: List<Item>,
    val continuations: List<Continuation>?,
) {
    @Serializable
    data class Header(
        val gridHeaderRenderer: GridHeaderRenderer,
    ) {
        @Serializable
        data class GridHeaderRenderer(
            val title: Runs,
        )
    }

    @Serializable
    data class Item(
        val musicNavigationButtonRenderer: MusicNavigationButtonRenderer?,
        val musicTwoRowItemRenderer: MusicTwoRowItemRenderer?,
        val continuationItemRenderer: ContinuationItemRenderer? = null,
    )
}

@JvmName("trailingContinuationOfGrid")
fun List<GridRenderer.Item>?.trailingContinuation(): String? =
    this?.lastOrNull { it.continuationItemRenderer != null }?.continuationItemRenderer?.token
