package com.caohaidang.edward.music.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.caohaidang.edward.music.spotify.SpotifyApi
import com.caohaidang.edward.music.spotify.SpotifyAuth
import com.caohaidang.edward.music.spotify.SpotifyAuthState
import com.caohaidang.edward.music.spotify.SpotifyImportRequest
import com.caohaidang.edward.music.spotify.SpotifyImporter
import com.caohaidang.edward.music.spotify.SpotifyPlaylist
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SpotifyLibraryUi(
    val loading: Boolean = false,
    val error: String? = null,
    val likedCount: Int = 0,
    val importLiked: Boolean = true,
    val playlists: List<SpotifyPlaylist> = emptyList(),
    val selectedPlaylistIds: Set<String> = emptySet(),
    /** Playlists the user only follows: the Spotify API does not let apps read their tracks. */
    val unreadablePlaylistIds: Set<String> = emptySet(),
)

@HiltViewModel
class SpotifyImportViewModel @Inject constructor(
    private val auth: SpotifyAuth,
    private val importer: SpotifyImporter,
) : ViewModel() {
    val authState = auth.state
    val importState = importer.state

    private val _library = MutableStateFlow(SpotifyLibraryUi())
    val library: StateFlow<SpotifyLibraryUi> = _library.asStateFlow()

    val isConfigured get() = auth.isConfigured
    val isClientIdFromBuild get() = auth.isClientIdFromBuild
    val clientId get() = auth.clientId

    fun saveClientId(id: String) {
        auth.clientId = id
    }

    fun buildLoginIntent() = auth.buildAuthorizeIntent()
    fun cancelPendingLogin() = auth.cancelPending()
    fun clearAuthError() = auth.clearError()

    fun signOut() {
        auth.signOut()
        _library.value = SpotifyLibraryUi()
        importer.resetState()
    }

    /** Loads liked-song count and playlists once signed in. */
    fun loadLibrary() {
        if (authState.value !is SpotifyAuthState.SignedIn) return
        if (_library.value.loading) return
        _library.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val api = SpotifyApi(auth)
                val me = (authState.value as? SpotifyAuthState.SignedIn)?.account?.id.orEmpty()
                val liked = api.likedCount()
                val playlists = api.playlists()
                val unreadable = playlists
                    .filter { me.isNotEmpty() && it.ownerId != me && !it.collaborative }
                    .map { it.id }.toSet()
                _library.update {
                    it.copy(
                        loading = false,
                        likedCount = liked,
                        playlists = playlists,
                        unreadablePlaylistIds = unreadable,
                        selectedPlaylistIds = playlists.map { p -> p.id }.filter { id -> id !in unreadable }.toSet(),
                    )
                }
            } catch (e: Exception) {
                _library.update { it.copy(loading = false, error = e.message ?: "Không tải được dữ liệu Spotify") }
            }
        }
    }

    fun setImportLiked(value: Boolean) = _library.update { it.copy(importLiked = value) }

    fun togglePlaylist(id: String) = _library.update {
        it.copy(selectedPlaylistIds = if (id in it.selectedPlaylistIds) it.selectedPlaylistIds - id else it.selectedPlaylistIds + id)
    }

    fun selectAllPlaylists(select: Boolean) = _library.update {
        it.copy(selectedPlaylistIds = if (select) it.playlists.map { p -> p.id }.filter { id -> id !in it.unreadablePlaylistIds }.toSet() else emptySet())
    }

    fun startImport() {
        val ui = _library.value
        importer.start(
            SpotifyImportRequest(
                importLiked = ui.importLiked,
                playlists = ui.playlists.filter { it.id in ui.selectedPlaylistIds },
            )
        )
    }

    fun dismissResult() = importer.resetState()
}
