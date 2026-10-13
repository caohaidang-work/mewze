package com.caohaidang.edward.innertube.models.body

import com.caohaidang.edward.innertube.models.Context
import kotlinx.serialization.Serializable

@Serializable
data class SearchBody(
    val context: Context,
    val query: String?,
    val params: String?,
)
