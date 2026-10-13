package com.caohaidang.edward.innertube.models.body

import com.caohaidang.edward.innertube.models.Context
import kotlinx.serialization.Serializable

@Serializable
data class LikeBody(
    val context: Context,
    val target: Target,
) {
    @Serializable
    data class Target(
        val videoId: String,
    )
}
