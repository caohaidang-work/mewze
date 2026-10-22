package com.caohaidang.edward.music.viewmodels

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.caohaidang.edward.innertube.YouTube
import com.caohaidang.edward.innertube.models.BrowseEndpoint
import com.caohaidang.edward.innertube.models.filterExplicit
import com.caohaidang.edward.music.constants.HideExplicitKey
import com.caohaidang.edward.music.models.ItemsPage
import com.caohaidang.edward.music.utils.dataStore
import com.caohaidang.edward.music.utils.get
import com.caohaidang.edward.music.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ArtistItemsViewModel @Inject constructor(
    @ApplicationContext val context: Context,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val browseId = savedStateHandle.get<String>("browseId").orEmpty()

    // "null"/blank used to be sent to YouTube as a literal params value, which made the
    // "see all" page of top songs / albums fail and stay on the loading shimmer forever.
    private val params = savedStateHandle.get<String>("params")
        ?.takeIf { it.isNotBlank() && it != "null" && it != "@null" }

    val title = MutableStateFlow("")
    val itemsPage = MutableStateFlow<ItemsPage?>(null)
    val error = MutableStateFlow(false)

    private var loadMoreJob: Job? = null

    init {
        load()
    }

    fun load() {
        error.value = false
        if (browseId.isBlank()) {
            error.value = true
            return
        }
        viewModelScope.launch {
            YouTube.artistItems(
                BrowseEndpoint(
                    browseId = browseId,
                    params = params
                )
            ).onSuccess { artistItemsPage ->
                title.value = artistItemsPage.title
                itemsPage.value = ItemsPage(
                    items = artistItemsPage.items
                        .distinctBy { it.id }
                        .filterExplicit(context.dataStore.get(HideExplicitKey, false)),
                    continuation = artistItemsPage.continuation
                )
            }.onFailure {
                error.value = true
                reportException(it)
            }
        }
    }

    fun loadMore() {
        if (loadMoreJob?.isActive == true) return
        loadMoreJob = viewModelScope.launch {
            val oldItemsPage = itemsPage.value ?: return@launch
            val continuation = oldItemsPage.continuation ?: return@launch
            YouTube.artistItemsContinuation(continuation)
                .onSuccess { artistItemsContinuationPage ->
                    // Read the latest value: the page may have changed while the request was running.
                    val current = itemsPage.value ?: oldItemsPage
                    itemsPage.value = ItemsPage(
                        items = (current.items + artistItemsContinuationPage.items)
                            .distinctBy { it.id }
                            .filterExplicit(context.dataStore.get(HideExplicitKey, false)),
                        continuation = artistItemsContinuationPage.continuation
                    )
                }.onFailure {
                    reportException(it)
                }
        }
    }
}
