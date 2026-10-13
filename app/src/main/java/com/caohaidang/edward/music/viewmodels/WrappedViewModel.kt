package com.caohaidang.edward.music.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.caohaidang.edward.music.constants.WrappedPeriod
import com.caohaidang.edward.music.constants.toDbMillis
import com.caohaidang.edward.music.db.MusicDatabase
import com.caohaidang.edward.music.models.MediaMetadata
import com.caohaidang.edward.music.models.toMediaMetadata
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject

data class WrappedSong(val song: MediaMetadata, val playTime: Long, val playCount: Int)

data class WrappedArtist(val name: String, val thumbnailUrl: String?, val playTime: Long)

data class WrappedData(
    val period: WrappedPeriod,
    val offset: Int,
    val start: LocalDateTime,

    val end: LocalDateTime,
    val totalMillis: Long,
    val songCount: Int,
    val playCount: Int,
    val topSongs: List<WrappedSong>,
    val topArtists: List<WrappedArtist>,
    val busiestDay: LocalDate?,
) {
    val isEmpty: Boolean get() = totalMillis <= 0L || topSongs.isEmpty()
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class WrappedViewModel @Inject constructor(
    private val database: MusicDatabase,
) : ViewModel() {
    val period = MutableStateFlow(WrappedPeriod.WEEK)

    val offset = MutableStateFlow(0)
    private val refreshTick = MutableStateFlow(0)

    val loading = MutableStateFlow(true)

    val data: StateFlow<WrappedData?> =
        combine(period, offset, refreshTick) { p, o, _ -> p to o }
            .mapLatest { (p, o) ->
                loading.value = true
                try {
                    load(p, o)
                } finally {
                    loading.value = false
                }
            }
            .stateIn(viewModelScope, SharingStarted.Lazily, null)

    fun setPeriod(value: WrappedPeriod) {
        if (period.value == value) return
        offset.value = 0
        period.value = value
    }

    fun previous() {
        offset.value -= 1
    }

    fun next() {
        if (offset.value < 0) offset.value += 1
    }

    fun refresh() {
        refreshTick.value += 1
    }

    private suspend fun load(period: WrappedPeriod, offset: Int): WrappedData = withContext(Dispatchers.IO) {
        val (start, end) = period.range(offset)
        val from = start.toDbMillis()
        val to = end.toDbMillis()

        val totals = database.playTotals(from, to)
        val songs = database.topSongPlayCounts(from, to, 5).mapNotNull { rank ->
            database.song(rank.id).first()?.let { WrappedSong(it.toMediaMetadata(), rank.playTime, rank.playCount) }
        }
        val artists = database.topArtistRanks(from, to, 5).mapNotNull { rank ->
            database.artist(rank.id).first()?.let {
                WrappedArtist(it.artist.name, it.artist.thumbnailUrl, rank.playTime)
            }
        }
        val day = database.busiestDay(from, to)?.let { runCatching { LocalDate.parse(it.id) }.getOrNull() }

        WrappedData(
            period = period,
            offset = offset,
            start = start,
            end = end,
            totalMillis = totals.totalPlayTime,
            songCount = totals.songCount,
            playCount = totals.playCount,
            topSongs = songs,
            topArtists = artists,
            busiestDay = day,
        )
    }
}
