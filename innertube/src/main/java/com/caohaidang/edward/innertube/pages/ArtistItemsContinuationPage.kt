package com.caohaidang.edward.innertube.pages

import com.caohaidang.edward.innertube.models.YTItem

data class ArtistItemsContinuationPage(
    val items: List<YTItem>,
    val continuation: String?,
)
