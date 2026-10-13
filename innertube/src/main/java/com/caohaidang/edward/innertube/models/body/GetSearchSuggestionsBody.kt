package com.caohaidang.edward.innertube.models.body

import com.caohaidang.edward.innertube.models.Context
import kotlinx.serialization.Serializable

@Serializable
data class GetSearchSuggestionsBody(
    val context: Context,
    val input: String,
)
