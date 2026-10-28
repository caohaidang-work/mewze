package com.caohaidang.edward.music.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.caohaidang.edward.innertube.YouTube
import com.caohaidang.edward.innertube.models.ArtistItem
import com.caohaidang.edward.innertube.models.PlaylistItem
import com.caohaidang.edward.innertube.models.SongItem
import com.caohaidang.edward.innertube.models.WatchEndpoint
import com.caohaidang.edward.innertube.models.YTItem
import com.caohaidang.edward.innertube.models.filterExplicit
import com.caohaidang.edward.innertube.pages.ChartsPage
import com.caohaidang.edward.innertube.pages.ExplorePage
import com.caohaidang.edward.innertube.pages.HomePage
import com.caohaidang.edward.music.constants.HideExplicitKey
import com.caohaidang.edward.music.db.MusicDatabase
import com.caohaidang.edward.music.db.entities.Album
import com.caohaidang.edward.music.db.entities.Artist
import com.caohaidang.edward.music.db.entities.LocalItem
import com.caohaidang.edward.music.db.entities.Playlist
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What a hero slide opens or plays when its button is pressed. */
sealed interface HeroTarget {
    data class LocalSong(val song: Song) : HeroTarget
    data class OnlineSong(val item: SongItem) : HeroTarget
    data class OnlineAlbum(val item: AlbumItem) : HeroTarget
    data class ArtistPage(val artistId: String) : HeroTarget
}

/** One page of the big carousel at the top of Home. */
data class HeroSlide(
    val key: String,
    /** Small line above the title, e.g. "Mới ra mắt". */
    val label: String,
    val title: String,
    val thumbnail: String?,
    val target: HeroTarget,
)

private const val HERO_PAGES = 7
private const val RECENT_LIMIT = 12
private const val CHART_LIMIT = 50

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

    val heroSlides = MutableStateFlow<List<HeroSlide>>(emptyList())

    /** Top songs of the country the app is set to, and worldwide. */
    val countryChart = MutableStateFlow<ChartsPage?>(null)
    val globalChart = MutableStateFlow<ChartsPage?>(null)

    // Candidates for the hero carousel, filled by load().
    @Volatile private var heroMostPlayed: List<Song> = emptyList()
    @Volatile private var heroLiked: List<Song> = emptyList()
    @Volatile private var heroArtists: List<Artist> = emptyList()

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

        heroMostPlayed = database.mostPlayedSongs(System.currentTimeMillis() - 86400000L * 30, limit = 8)
            .first().shuffled()
        heroLiked = database.likedSongsByCreateDateAsc().first().shuffled()
        heroArtists = (database.artistsBookmarkedByCreateDateAsc().first() +
                database.mostPlayedArtists(fromTimeStamp, limit = 8).first())
            .filter { it.artist.isYouTubeArtist && it.artist.thumbnailUrl != null }
            .distinctBy { it.id }
            .shuffled()

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

        updateHero()
        isLoading.value = false
    }

    private fun songTitle(title: String, artists: List<String>): String =
        if (artists.isEmpty()) title else "$title - ${artists.joinToString()}"

    /**
     * Builds the hero carousel: one slide per kind of suggestion (maybe-you-know, new release, long
     * time no hear, most played, favourite artist, liked song, trending), skipping the kinds there is
     * nothing for, then topped up with more suggestions up to [HERO_PAGES] pages.
     */
    private fun updateHero() {
        val used = HashSet<String>()
        val slides = ArrayList<HeroSlide>()

        fun addSong(label: String, song: Song?) {
            if (song == null || slides.size >= HERO_PAGES || !used.add(song.id)) return
            slides += HeroSlide(
                key = "song_${song.id}_$label",
                label = label,
                title = songTitle(song.title, song.artists.map { it.name }),
                thumbnail = song.thumbnailUrl,
                target = HeroTarget.LocalSong(song),
            )
        }

        fun addOnline(label: String, item: YTItem?) {
            if (item == null || slides.size >= HERO_PAGES || !used.add(item.id)) return
            when (item) {
                is SongItem -> slides += HeroSlide(
                    key = "yt_${item.id}_$label",
                    label = label,
                    title = songTitle(item.title, item.artists.map { it.name }),
                    thumbnail = item.thumbnail,
                    target = HeroTarget.OnlineSong(item),
                )

                is AlbumItem -> slides += HeroSlide(
                    key = "yt_${item.id}_$label",
                    label = label,
                    title = songTitle(item.title, item.artists.orEmpty().map { it.name }),
                    thumbnail = item.thumbnail,
                    target = HeroTarget.OnlineAlbum(item),
                )

                else -> used.remove(item.id)
            }
        }

        fun List<YTItem>.songsAndAlbums() = filter { it is SongItem || it is AlbumItem }

        val suggestions = (similarRecommendations.value.orEmpty().flatMap { it.items } +
                homePage.value?.sections.orEmpty().flatMap { it.items }).songsAndAlbums()

        // 1. Maybe you know
        addOnline("Có thể bạn biết", suggestions.firstOrNull())
        // 2. New release: the store's own new-release shelf (recent, for the app's country) is already
        // sorted with your favourite and known artists first; the wider "not heard yet" list is the fallback.
        addOnline("Mới ra mắt", explorePage.value?.newReleaseAlbums?.firstOrNull() ?: newForYou.value?.firstOrNull())
        // 3. Long time no hear
        addSong("Đã lâu không nghe", forgottenFavorites.value?.firstOrNull { it.id !in used })
        // 4. Most played
        addSong("Nghe nhiều", heroMostPlayed.firstOrNull { it.id !in used })
        // 5. Favourite artist
        heroArtists.firstOrNull()?.let { artist ->
            if (slides.size < HERO_PAGES && used.add(artist.id)) {
                slides += HeroSlide(
                    key = "artist_${artist.id}",
                    label = "Nghệ sĩ yêu thích",
                    title = artist.title,
                    thumbnail = artist.thumbnailUrl,
                    target = HeroTarget.ArtistPage(artist.id),
                )
            }
        }
        // 6. Liked song
        addSong("Bài hát yêu thích", heroLiked.firstOrNull { it.id !in used })
        // 7. Trending
        addOnline(
            "Đang thịnh hành",
            (countryChart.value?.songs ?: globalChart.value?.songs)?.firstOrNull { it.id !in used }
        )

        // Fill up to HERO_PAGES with more suggestions (e.g. a new account with no history yet).
        suggestions.drop(1).forEach { addOnline("Đề xuất cho bạn", it) }
        (explorePage.value?.newReleaseAlbums.orEmpty()).forEach { addOnline("Mới ra mắt", it) }

        heroSlides.value = slides
    }

    private suspend fun loadCharts() {
        val hideExplicit = context.dataStore.get(HideExplicitKey, false)
        val country = YouTube.locale.gl

        suspend fun fetch(code: String): ChartsPage? =
            YouTube.charts(code).onFailure { reportException(it) }.getOrNull()?.let { page ->
                page.copy(songs = page.songs.filterExplicit(hideExplicit).take(CHART_LIMIT))
                    .takeIf { it.songs.isNotEmpty() }
            }

        coroutineScope {
            val local = if (country.isNotBlank() && country != ChartsPage.GLOBAL) async { fetch(country) } else null
            val global = async { fetch(ChartsPage.GLOBAL) }
            countryChart.value = local?.await()
            globalChart.value = global.await()
        }
        updateHero()
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
            loadCharts()
            isRefreshing.value = false
        }
    }

    /** Interleaves recent songs, albums and playlists so one kind does not crowd out the others. */
    private fun mixRecent(songs: List<Song>, albums: List<Album>, playlists: List<Playlist>): List<LocalItem> {
        val songQueue = ArrayDeque<LocalItem>(songs)
        val albumQueue = ArrayDeque<LocalItem>(albums)
        val playlistQueue = ArrayDeque<LocalItem>(playlists)
        val pattern = listOf(songQueue, albumQueue, playlistQueue, songQueue)
        val result = ArrayList<LocalItem>()
        var i = 0
        while (result.size < RECENT_LIMIT) {
            val next = pattern[i++ % pattern.size].removeFirstOrNull()
                ?: listOf(songQueue, albumQueue, playlistQueue).firstOrNull { it.isNotEmpty() }?.removeFirst()
                ?: break
            result += next
        }
        return result
    }

    init {
        viewModelScope.launch(Dispatchers.IO) {
            load()
        }

        viewModelScope.launch(Dispatchers.IO) {
            loadCharts()
        }

        viewModelScope.launch(Dispatchers.IO) {
            combine(
                database.recentlyPlayedSongs(RECENT_LIMIT),
                database.recentlyPlayedAlbums(6),
                database.recentlyPlayedPlaylists(6),
            ) { songs, albums, playlists ->
                mixRecent(songs, albums.filter { it.album.thumbnailUrl != null }, playlists)
            }.collect { recentItems.value = it }
        }
    }
}
