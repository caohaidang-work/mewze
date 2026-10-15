package com.caohaidang.edward.music.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.caohaidang.edward.innertube.YouTube
import com.caohaidang.edward.innertube.models.ArtistItem
import com.caohaidang.edward.innertube.models.PlaylistItem
import com.caohaidang.edward.innertube.models.WatchEndpoint
import com.caohaidang.edward.innertube.models.YTItem
import com.caohaidang.edward.innertube.models.filterExplicit
import com.caohaidang.edward.innertube.pages.ExplorePage
import com.caohaidang.edward.innertube.pages.HomePage
import com.caohaidang.edward.music.constants.HideExplicitKey
import com.caohaidang.edward.music.db.MusicDatabase
import com.caohaidang.edward.music.db.entities.Album
import com.caohaidang.edward.music.db.entities.Artist
import com.caohaidang.edward.music.db.entities.LocalItem
import com.caohaidang.edward.music.db.entities.Song
import com.caohaidang.edward.music.models.SimilarRecommendation
import com.caohaidang.edward.music.utils.dataStore
import com.caohaidang.edward.music.utils.get
import com.caohaidang.edward.music.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import com.caohaidang.edward.innertube.models.AlbumItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext val context: Context,
    val database: MusicDatabase,
) : ViewModel() {
    val isRefreshing = MutableStateFlow(false)
    val isLoading = MutableStateFlow(false)

    val recentItems = MutableStateFlow<List<LocalItem>?>(null)

    val quickPicks = MutableStateFlow<List<Song>?>(null)
    val forgottenFavorites = MutableStateFlow<List<Song>?>(null)
    val keepListening = MutableStateFlow<List<LocalItem>?>(null)
    val similarRecommendations = MutableStateFlow<List<SimilarRecommendation>?>(null)
    val accountPlaylists = MutableStateFlow<List<PlaylistItem>?>(null)
    val homePage = MutableStateFlow<HomePage?>(null)
    val explorePage = MutableStateFlow<ExplorePage?>(null)

    val newForYou = MutableStateFlow<List<AlbumItem>?>(null)

    val allLocalItems = MutableStateFlow<List<LocalItem>>(emptyList())
    val allYtItems = MutableStateFlow<List<YTItem>>(emptyList())

    private suspend fun load() {
        isLoading.value = true

        val hideExplicit = context.dataStore.get(HideExplicitKey, false)

        quickPicks.value = database.quickPicks()
            .first().shuffled().take(20)

        forgottenFavorites.value = database.forgottenFavorites()
            .first().shuffled().take(20)

        val fromTimeStamp = System.currentTimeMillis() - 86400000 * 7 * 2
        val keepListeningSongs = database.mostPlayedSongs(fromTimeStamp, limit = 15, offset = 5)
            .first().shuffled().take(10)
        val keepListeningAlbums = database.mostPlayedAlbums(fromTimeStamp, limit = 8, offset = 2)
            .first().filter { it.album.thumbnailUrl != null }.shuffled().take(5)
        val keepListeningArtists = database.mostPlayedArtists(fromTimeStamp)
            .first().filter { it.artist.isYouTubeArtist && it.artist.thumbnailUrl != null }.shuffled().take(5)
        keepListening.value = (keepListeningSongs + keepListeningAlbums + keepListeningArtists).shuffled()

        allLocalItems.value = (quickPicks.value.orEmpty() + forgottenFavorites.value.orEmpty() + keepListening.value.orEmpty())
            .filter { it is Song || it is Album }

        if (YouTube.cookie != null) {
            YouTube.likedPlaylists().onSuccess {
                accountPlaylists.value = it
            }.onFailure {
                reportException(it)
            }
        }

        val artistRecommendations =
            database.mostPlayedArtists(fromTimeStamp, limit = 10).first()
                .filter { it.artist.isYouTubeArtist }
                .shuffled().take(3)
                .mapNotNull {
                    val items = mutableListOf<YTItem>()
                    YouTube.artist(it.id).onSuccess { page ->
                        items += page.sections.getOrNull(page.sections.size - 2)?.items.orEmpty()
                        items += page.sections.lastOrNull()?.items.orEmpty()
                    }
                    SimilarRecommendation(
                        title = it,
                        items = items
                            .filterExplicit(hideExplicit)
                            .shuffled()
                            .ifEmpty { return@mapNotNull null }
                    )
                }

        val songRecommendations =
            database.mostPlayedSongs(fromTimeStamp, limit = 10).first()
                .filter { it.album != null }
                .shuffled().take(2)
                .mapNotNull { song ->
                    val endpoint = YouTube.next(WatchEndpoint(videoId = song.id)).getOrNull()?.relatedEndpoint ?: return@mapNotNull null
                    val page = YouTube.related(endpoint).getOrNull() ?: return@mapNotNull null
                    SimilarRecommendation(
                        title = song,
                        items = (page.songs.shuffled().take(8) +
                                page.albums.shuffled().take(4) +
                                page.artists.shuffled().take(4) +
                                page.playlists.shuffled().take(4))
                            .filterExplicit(hideExplicit)
                            .shuffled()
                            .ifEmpty { return@mapNotNull null }
                    )
                }
        similarRecommendations.value = (artistRecommendations + songRecommendations).shuffled()

        YouTube.home().onSuccess { page ->
            homePage.value = page.filterExplicit(hideExplicit)
        }.onFailure {
            reportException(it)
        }

        YouTube.explore().onSuccess { page ->
            val artists: Set<String>
            val favouriteArtists: Set<String>
            database.artistsByCreateDateAsc().first().let { list ->
                artists = list.map(Artist::id).toHashSet()
                favouriteArtists = list
                    .filter { it.artist.bookmarkedAt != null }
                    .map { it.id }
                    .toHashSet()
            }
            explorePage.value = page.copy(
                newReleaseAlbums = page.newReleaseAlbums
                    .sortedBy { album ->
                        if (album.artists.orEmpty().any { it.id in favouriteArtists }) 0
                        else if (album.artists.orEmpty().any { it.id in artists }) 1
                        else 2
                    }
                    .filterExplicit(hideExplicit)
            )
        }.onFailure {
            reportException(it)
        }

        loadNewForYou(hideExplicit)

        allYtItems.value = similarRecommendations.value?.flatMap { it.items }.orEmpty() +
                homePage.value?.sections?.flatMap { it.items }.orEmpty() +
                explorePage.value?.newReleaseAlbums.orEmpty() +
                newForYou.value.orEmpty()

        isLoading.value = false
    }

    /**
     * "Not heard yet, for you": new releases from the artists you play most, plus artists similar to
     * them, minus anything you have already listened to, liked or saved.
     */
    private suspend fun loadNewForYou(hideExplicit: Boolean) {
        val knownIds = (database.allAlbumIds() + database.heardAlbumIds()).toHashSet()
        val heardTitles = database.heardTitles().map(::normTitle).filter { it.isNotEmpty() }.toHashSet()
        fun AlbumItem.alreadyHeard(): Boolean = id in knownIds || normTitle(title) in heardTitles

        val from = System.currentTimeMillis() - 86400000L * 90
        val topArtists = database.mostPlayedArtists(from, limit = 12).first()
            .filter { it.artist.isYouTubeArtist }
        val tasteIds = topArtists.map { it.id }.toHashSet()

        val today = LocalDate.now()
        val minYear = if (today.monthValue <= 2) today.year - 1 else today.year
        fun List<YTItem>.recentAlbums() = filterIsInstance<AlbumItem>().filter { (it.year ?: 0) >= minYear }

        // Lower rank = closer to your taste.
        val ranked = ArrayList<Pair<AlbumItem, Int>>()
        coroutineScope {
            val pages = topArtists.take(6).map { artist ->
                async { YouTube.artist(artist.id).getOrNull() }
            }.awaitAll().filterNotNull()

            pages.forEachIndexed { i, page ->
                page.sections.flatMap { it.items }.recentAlbums().forEach { ranked += it to i }
            }

            val similarArtists = pages.flatMap { page -> page.sections.flatMap { it.items }.filterIsInstance<ArtistItem>() }
                .filter { it.id !in tasteIds }
                .distinctBy { it.id }
                .take(8)
            similarArtists.mapIndexed { i, artist ->
                async {
                    YouTube.artist(artist.id).getOrNull()
                        ?.sections.orEmpty().flatMap { it.items }.recentAlbums()
                        .map { it to 100 + i }
                }
            }.awaitAll().forEach { ranked += it }
        }

        // Generic new releases only fill in when there is too little personal material.
        val seen = HashSet<String>()
        fun pick(list: List<Pair<AlbumItem, Int>>) = list
            .filter { !it.first.alreadyHeard() }
            .filter { seen.add(albumKey(it.first)) }
            .sortedWith(compareBy({ it.second }, { -(it.first.year ?: 0) }))
            .map { it.first }
        val personal = pick(ranked).filterExplicit(hideExplicit)
        val result = if (personal.size >= 6) personal
        else personal + pick(explorePage.value?.newReleaseAlbums.orEmpty().map { it to 1000 }).filterExplicit(hideExplicit)
        newForYou.value = result.take(12).ifEmpty { null }
    }

    private fun normTitle(title: String): String =
        title.lowercase().replace(Regex("""[(\[].*?[)\]]"""), "").replace(Regex("""[^\p{L}\p{N}]+"""), " ").trim()

    private fun albumKey(album: AlbumItem): String =
        (album.title + "|" + album.artists.orEmpty().joinToString { it.name }).lowercase()

    fun refresh() {
        if (isRefreshing.value) return
        viewModelScope.launch(Dispatchers.IO) {
            isRefreshing.value = true
            load()
            isRefreshing.value = false
        }
    }

    private fun mixRecent(songs: List<Song>, albums: List<Album>): List<LocalItem> {
        val songQueue = ArrayDeque<LocalItem>(songs.take(6))
        val albumQueue = ArrayDeque<LocalItem>(albums.take(3))
        val result = ArrayList<LocalItem>()
        while (result.size < 6 && (songQueue.isNotEmpty() || albumQueue.isNotEmpty())) {
            val preferAlbum = result.size % 3 == 2
            val next = when {
                preferAlbum && albumQueue.isNotEmpty() -> albumQueue.removeFirst()
                songQueue.isNotEmpty() -> songQueue.removeFirst()
                else -> albumQueue.removeFirst()
            }
            result += next
        }
        return result
    }

    init {
        viewModelScope.launch(Dispatchers.IO) {
            load()
        }

        viewModelScope.launch(Dispatchers.IO) {
            database.recentlyPlayedSongs(12).collect { recentSongs ->
                val topAlbums = database.mostPlayedAlbums(0L, limit = 6).first()
                    .filter { it.album.thumbnailUrl != null }
                recentItems.value = mixRecent(recentSongs, topAlbums)
            }
        }
    }
}
