package com.caohaidang.edward.lrclib.models

import kotlinx.serialization.Serializable
import kotlin.math.abs

@Serializable
data class Track(
    val id: Int,
    val trackName: String,
    val artistName: String,
    val duration: Double,
    val plainLyrics: String? = null,
    val syncedLyrics: String? = null,
)

internal fun List<Track>.bestMatchingFor(duration: Int) = firstOrNull { abs(it.duration.toInt() - duration) <= 2 }
