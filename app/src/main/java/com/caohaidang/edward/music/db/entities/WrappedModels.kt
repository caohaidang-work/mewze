package com.caohaidang.edward.music.db.entities

data class PlayRank(
    val id: String,
    val playTime: Long,
)

data class PlayCountRank(
    val id: String,
    val playCount: Int,
    val playTime: Long,
)

data class PlayTotals(
    val totalPlayTime: Long,
    val songCount: Int,
    val playCount: Int,
)
