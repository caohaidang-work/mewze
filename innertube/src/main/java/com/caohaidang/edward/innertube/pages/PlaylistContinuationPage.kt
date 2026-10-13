package com.caohaidang.edward.innertube.pages

import com.caohaidang.edward.innertube.models.SongItem

data class PlaylistContinuationPage(
    val songs: List<SongItem>,
    val continuation: String?,
)
