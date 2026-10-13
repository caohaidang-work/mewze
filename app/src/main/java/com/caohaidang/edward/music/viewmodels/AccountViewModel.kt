package com.caohaidang.edward.music.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.caohaidang.edward.music.sync.LibrarySyncManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AccountViewModel @Inject constructor(
    private val syncManager: LibrarySyncManager,
) : ViewModel() {
    val syncState = syncManager.state
    val lastSyncAt = syncManager.lastSyncAt

    fun autoSync() {
        viewModelScope.launch { syncManager.syncAll() }
    }

    fun onLoggedOut() = syncManager.reset()
}
