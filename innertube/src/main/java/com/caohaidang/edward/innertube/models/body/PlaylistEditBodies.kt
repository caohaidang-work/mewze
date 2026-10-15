package com.caohaidang.edward.innertube.models.body

import com.caohaidang.edward.innertube.models.Context
import kotlinx.serialization.Serializable

@Serializable
data class LikePlaylistBody(
    val context: Context,
    val target: Target,
) {
    @Serializable
    data class Target(val playlistId: String)
}

@Serializable
data class CreatePlaylistBody(
    val context: Context,
    val title: String,
    val privacyStatus: String = "PRIVATE",
    val videoIds: List<String> = emptyList(),
)

@Serializable
data class CreatePlaylistResponse(
    val playlistId: String? = null,
)

@Serializable
data class EditPlaylistBody(
    val context: Context,
    val playlistId: String,
    val actions: List<Action>,
) {
    @Serializable
    data class Action(
        val action: String,
        val addedVideoId: String? = null,
        val removedVideoId: String? = null,
        val setVideoId: String? = null,
        val playlistName: String? = null,
        val movedSetVideoIdSuccessor: String? = null,
    ) {
        companion object {
            fun add(videoId: String) = Action("ACTION_ADD_VIDEO", addedVideoId = videoId)
            fun remove(videoId: String, setVideoId: String) =
                Action("ACTION_REMOVE_VIDEO", removedVideoId = videoId, setVideoId = setVideoId)

            /** Moves the item [setVideoId] so that it sits right before [successorSetVideoId]. */
            fun moveBefore(setVideoId: String, successorSetVideoId: String) =
                Action("ACTION_MOVE_VIDEO_BEFORE", setVideoId = setVideoId, movedSetVideoIdSuccessor = successorSetVideoId)

            fun rename(name: String) = Action("ACTION_SET_PLAYLIST_NAME", playlistName = name)
        }
    }
}

@Serializable
data class DeletePlaylistBody(
    val context: Context,
    val playlistId: String,
)
