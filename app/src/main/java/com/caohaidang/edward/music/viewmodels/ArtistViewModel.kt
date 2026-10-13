package com.caohaidang.edward.music.viewmodels

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.caohaidang.edward.innertube.YouTube
import com.caohaidang.edward.innertube.pages.ArtistPage
import com.caohaidang.edward.music.constants.HideExplicitKey
import com.caohaidang.edward.music.db.MusicDatabase
import com.caohaidang.edward.music.utils.dataStore
import com.caohaidang.edward.music.utils.get
import com.caohaidang.edward.music.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.caohaidang.edward.music.utils.ArtistImageProvider
import javax.inject.Inject

@HiltViewModel
class ArtistViewModel @Inject constructor(
    @ApplicationContext context: Context,
    database: MusicDatabase,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    var freshImageUrl by mutableStateOf<String?>(null)
        private set

    val artistId = savedStateHandle.get<String>("artistId")!!
    var artistPage by mutableStateOf<ArtistPage?>(null)
    val libraryArtist = database.artist(artistId)
        .stateIn(viewModelScope, SharingStarted.Lazily, null)
    val librarySongs = database.artistSongsPreview(artistId)
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    init {
        viewModelScope.launch {
            YouTube.artist(artistId)
                .onSuccess {
                    artistPage = it.filterExplicit(context.dataStore.get(HideExplicitKey, false))
                    freshImageUrl = ArtistImageProvider.find(it.artist.title)
                }.onFailure {
                    reportException(it)
                }
        }
    }
}
