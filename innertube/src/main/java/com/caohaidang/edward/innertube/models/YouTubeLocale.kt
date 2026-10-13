package com.caohaidang.edward.innertube.models

import kotlinx.serialization.Serializable

@Serializable
data class YouTubeLocale(
    val gl: String,
    val hl: String,
)
