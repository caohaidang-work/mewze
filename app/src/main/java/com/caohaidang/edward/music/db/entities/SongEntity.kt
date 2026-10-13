package com.caohaidang.edward.music.db.entities

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDateTime

@Immutable
@Entity(
    tableName = "song",
    indices = [
        Index(
            value = ["albumId"]
        )
    ]
)
data class SongEntity(
    @PrimaryKey val id: String,
    val title: String,
    val duration: Int = -1,
    val thumbnailUrl: String? = null,
    val albumId: String? = null,
    val albumName: String? = null,
    val liked: Boolean = false,
    val totalPlayTime: Long = 0,
    val inLibrary: LocalDateTime? = null,
) {
    fun toggleLike() = copy(
        liked = !liked,
        inLibrary = if (!liked) inLibrary ?: LocalDateTime.now() else inLibrary
    )

    fun toggleLibrary() = copy(inLibrary = if (inLibrary == null) LocalDateTime.now() else null)
}
