package com.caohaidang.edward.innertube.models

import kotlinx.serialization.Serializable

@Serializable
data class ItemSectionRenderer(
    val contents: List<Content>? = null,
    val trackingParams: String? = null,
) {
    @Serializable
    data class Content(
        val musicResponsiveListItemRenderer: MusicResponsiveListItemRenderer? = null,
    )
}
