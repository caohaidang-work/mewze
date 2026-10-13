package com.caohaidang.edward.music.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.caohaidang.edward.innertube.YouTube
import com.caohaidang.edward.innertube.pages.MoodAndGenres
import com.caohaidang.edward.music.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MoodAndGenresViewModel @Inject constructor() : ViewModel() {
    val moodAndGenres = MutableStateFlow<List<MoodAndGenres>?>(null)

    init {
        viewModelScope.launch {
            YouTube.moodAndGenres().onSuccess {
                moodAndGenres.value = it
            }.onFailure {
                reportException(it)
            }
        }
    }
}
