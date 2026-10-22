package com.caohaidang.edward.music.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.caohaidang.edward.music.spotify.SpotifyAuth
import com.caohaidang.edward.music.spotify.SpotifyImporter
import com.caohaidang.edward.music.sync.LibrarySyncManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AccountViewModel @Inject constructor(
    private val syncManager: LibrarySyncManager,
    private val spotifyAuth: SpotifyAuth,
    private val spotifyImporter: SpotifyImporter,
) : ViewModel() {
    val syncState = syncManager.state
    val lastSyncAt = syncManager.lastSyncAt
    val failureEvents = syncManager.failureEvents

    fun retrySync() {
        viewModelScope.launch { syncManager.retryNow() }
    }

    fun autoSync() {
        viewModelScope.launch { syncManager.syncAll() }
    }

    /** Pushes pending local changes to YouTube Music; [onResult] gets true when all synced cleanly. */
    fun syncBeforeLogout(onResult: (Boolean) -> Unit) {
        viewModelScope.launch { onResult(syncManager.syncBeforeLogout()) }
    }

    /** Deletes all data of the account that just signed out. */
    fun onLoggedOut() {
        // The Spotify link belongs to the same person: forget it too, so nothing leaks to the next account.
        spotifyAuth.signOut()
        spotifyImporter.clearCache()
        viewModelScope.launch { syncManager.wipeAccountData() }
    }
}
