package com.caohaidang.edward.music.models

import com.caohaidang.edward.innertube.models.YTItem

data class ItemsPage(
    val items: List<YTItem>,
    val continuation: String?,
)
