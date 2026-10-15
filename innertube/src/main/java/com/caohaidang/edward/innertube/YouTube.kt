package com.caohaidang.edward.innertube

import com.caohaidang.edward.innertube.models.AccountInfo
import com.caohaidang.edward.innertube.models.AlbumItem
import com.caohaidang.edward.innertube.models.Artist
import com.caohaidang.edward.innertube.models.ArtistItem
import com.caohaidang.edward.innertube.models.BrowseEndpoint
import com.caohaidang.edward.innertube.models.GridRenderer
import com.caohaidang.edward.innertube.models.MusicCarouselShelfRenderer
import com.caohaidang.edward.innertube.models.PlaylistItem
import com.caohaidang.edward.innertube.models.SearchSuggestions
import com.caohaidang.edward.innertube.models.SongItem
import com.caohaidang.edward.innertube.models.YTItem
import com.caohaidang.edward.innertube.models.WatchEndpoint
import com.caohaidang.edward.innertube.models.WatchEndpoint.WatchEndpointMusicSupportedConfigs.WatchEndpointMusicConfig.Companion.MUSIC_VIDEO_TYPE_ATV
import com.caohaidang.edward.innertube.models.YouTubeClient.Companion.ANDROID_MUSIC
import com.caohaidang.edward.innertube.models.YouTubeClient.Companion.IOS
import com.caohaidang.edward.innertube.models.YouTubeClient.Companion.TVHTML5
import com.caohaidang.edward.innertube.models.YouTubeClient.Companion.WEB
import com.caohaidang.edward.innertube.models.YouTubeClient.Companion.WEB_REMIX
import com.caohaidang.edward.innertube.models.YouTubeLocale
import com.caohaidang.edward.innertube.models.getContinuation
import com.caohaidang.edward.innertube.models.oddElements
import com.caohaidang.edward.innertube.models.body.CreatePlaylistResponse
import com.caohaidang.edward.innertube.models.body.EditPlaylistBody
import com.caohaidang.edward.innertube.models.response.AccountMenuResponse
import com.caohaidang.edward.innertube.models.response.BrowseResponse
import com.caohaidang.edward.innertube.models.response.GetQueueResponse
import com.caohaidang.edward.innertube.models.response.GetSearchSuggestionsResponse
import com.caohaidang.edward.innertube.models.response.GetTranscriptResponse
import com.caohaidang.edward.innertube.models.response.NextResponse
import com.caohaidang.edward.innertube.models.response.PipedResponse
import com.caohaidang.edward.innertube.models.response.PlayerResponse
import com.caohaidang.edward.innertube.models.response.SearchResponse
import com.caohaidang.edward.innertube.pages.AlbumPage
import com.caohaidang.edward.innertube.pages.ArtistItemsContinuationPage
import com.caohaidang.edward.innertube.pages.ArtistItemsPage
import com.caohaidang.edward.innertube.pages.ArtistPage
import com.caohaidang.edward.innertube.pages.BrowseResult
import com.caohaidang.edward.innertube.pages.ExplorePage
import com.caohaidang.edward.innertube.pages.HomePage
import com.caohaidang.edward.innertube.pages.MoodAndGenres
import com.caohaidang.edward.innertube.pages.NewReleaseAlbumPage
import com.caohaidang.edward.innertube.pages.NextPage
import com.caohaidang.edward.innertube.pages.NextResult
import com.caohaidang.edward.innertube.pages.PlaylistContinuationPage
import com.caohaidang.edward.innertube.pages.PlaylistPage
import com.caohaidang.edward.innertube.pages.RelatedPage
import com.caohaidang.edward.innertube.pages.SearchPage
import com.caohaidang.edward.innertube.pages.SearchResult
import com.caohaidang.edward.innertube.pages.SearchSuggestionPage
import com.caohaidang.edward.innertube.pages.SearchSummary
import com.caohaidang.edward.innertube.pages.SearchSummaryPage
import io.ktor.client.call.body
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import java.net.Proxy

private fun JsonElement.jsonObjectOrNull(): JsonObject? =
    this as? JsonObject

private fun JsonElement.jsonArrayOrNull(): JsonArray? =
    this as? JsonArray

object YouTube {
    private val innerTube = InnerTube()

    var locale: YouTubeLocale
        get() = innerTube.locale
        set(value) {
            innerTube.locale = value
        }

    var visitorData: String
        get() = innerTube.visitorData
        set(value) {
            innerTube.visitorData = value
        }

    var cookie: String?
        get() = innerTube.cookie
        set(value) {
            innerTube.cookie = value
        }

    var proxy: Proxy?
        get() = innerTube.proxy
        set(value) {
            innerTube.proxy = value
        }

    var useLoginForBrowse: Boolean
        get() = innerTube.useLoginForBrowse
        set(value) {
            innerTube.useLoginForBrowse = value
        }

    suspend fun searchSuggestions(
        query: String
    ): Result<SearchSuggestions> = runCatching {
        val response = innerTube
            .getSearchSuggestions(WEB_REMIX, query)
            .body<GetSearchSuggestionsResponse>()

        SearchSuggestions(
            queries = response.contents
                ?.getOrNull(0)
                ?.searchSuggestionsSectionRenderer
                ?.contents
                ?.mapNotNull { content ->
                    content.searchSuggestionRenderer
                        ?.suggestion
                        ?.runs
                        ?.joinToString(separator = "") { it.text }
                }
                .orEmpty(),

            recommendedItems = response.contents
                ?.getOrNull(1)
                ?.searchSuggestionsSectionRenderer
                ?.contents
                ?.mapNotNull {
                    it.musicResponsiveListItemRenderer?.let { renderer ->
                        SearchSuggestionPage
                            .fromMusicResponsiveListItemRenderer(renderer)
                    }
                }
                .orEmpty()
        )
    }

    suspend fun searchSummary(
        query: String
    ): Result<SearchSummaryPage> = runCatching {
        val response = innerTube
            .search(
                client = WEB_REMIX,
                query = query
            )
            .body<SearchResponse>()

        val sections = response
            .contents
            ?.tabbedSearchResultsRenderer
            ?.tabs
            ?.firstOrNull()
            ?.tabRenderer
            ?.content
            ?.sectionListRenderer
            ?.contents
            .orEmpty()

        val artists = mutableListOf<YTItem>()
        val songs = mutableListOf<YTItem>()
        val albums = mutableListOf<YTItem>()
        val videos = mutableListOf<YTItem>()
        val playlists = mutableListOf<YTItem>()
        val topResultItems = mutableListOf<YTItem>()

        fun addToGroup(
            renderer: com.caohaidang.edward.innertube.models.MusicResponsiveListItemRenderer
        ) {
            val item = SearchSummaryPage.fromMusicResponsiveListItemRenderer(renderer) ?: return

            when {
                renderer.isArtist -> artists += item
                renderer.isAlbum -> albums += item
                renderer.isPlaylist -> playlists += item
                renderer.isSong -> {
                    val musicVideoType =
                        renderer.overlay
                            ?.musicItemThumbnailOverlayRenderer
                            ?.content
                            ?.musicPlayButtonRenderer
                            ?.playNavigationEndpoint
                            ?.watchEndpoint
                            ?.watchEndpointMusicSupportedConfigs
                            ?.watchEndpointMusicConfig
                            ?.musicVideoType

                    if (musicVideoType == null || musicVideoType == MUSIC_VIDEO_TYPE_ATV) {
                        songs += item
                    } else {
                        videos += item
                    }
                }
            }
        }

        sections.forEachIndexed { _, content ->
            content.musicCardShelfRenderer?.let { shelf ->
                SearchSummaryPage.fromMusicCardShelfRenderer(shelf)?.let { item ->
                    topResultItems += item
                }

                shelf.contents.orEmpty()
                    .mapNotNull { it.musicResponsiveListItemRenderer }
                    .forEach { renderer ->
                        SearchSummaryPage.fromMusicResponsiveListItemRenderer(renderer)?.let { item ->
                            topResultItems += item
                        }
                    }
            }

            content.musicShelfRenderer?.let { shelf ->
                shelf.contents.orEmpty()
                    .mapNotNull { it.musicResponsiveListItemRenderer }
                    .forEach { renderer -> addToGroup(renderer) }
            }

            content.itemSectionRenderer?.let { section ->
                section.contents.orEmpty()
                    .mapNotNull { it.musicResponsiveListItemRenderer }
                    .forEach { renderer -> addToGroup(renderer) }
            }
        }

        fun distinctItems(items: List<YTItem>): List<YTItem> {
            return items.distinctBy { it.id }
        }

        val distinctTopResults = distinctItems(topResultItems)
        val distinctArtists = distinctItems(artists)
        val distinctSongs = distinctItems(songs)
        val distinctAlbums = distinctItems(albums)
        val distinctVideos = distinctItems(videos)
        val distinctPlaylists = distinctItems(playlists)

        val summaries = buildList {
            if (distinctTopResults.isNotEmpty()) add(SearchSummary(title = "Top result", items = distinctTopResults))
            if (distinctArtists.isNotEmpty()) add(SearchSummary(title = "Artists", items = distinctArtists))
            if (distinctSongs.isNotEmpty()) add(SearchSummary(title = "Songs", items = distinctSongs))
            if (distinctAlbums.isNotEmpty()) add(SearchSummary(title = "Albums", items = distinctAlbums))
            if (distinctVideos.isNotEmpty()) add(SearchSummary(title = "Videos", items = distinctVideos))
            if (distinctPlaylists.isNotEmpty()) add(SearchSummary(title = "Playlists", items = distinctPlaylists))
        }

        SearchSummaryPage(summaries = summaries)
    }

    suspend fun search(
        query: String,
        filter: SearchFilter
    ): Result<SearchResult> = runCatching {
        val response = innerTube
            .search(
                client = WEB_REMIX,
                query = query,
                params = filter.value
            )
            .body<SearchResponse>()

        val sections = response
            .contents
            ?.tabbedSearchResultsRenderer
            ?.tabs
            ?.firstOrNull()
            ?.tabRenderer
            ?.content
            ?.sectionListRenderer
            ?.contents
            .orEmpty()

        val musicShelves = sections.mapNotNull { it.musicShelfRenderer }

        val items = musicShelves.flatMap { shelf ->
            shelf.contents.orEmpty().mapNotNull { content ->
                SearchPage.toYTItem(content.musicResponsiveListItemRenderer)
            }
        }.distinctBy { it.id }

        val continuation = musicShelves.firstNotNullOfOrNull { shelf ->
            shelf.continuations?.getContinuation()
        }

        SearchResult(items = items, continuation = continuation)
    }

    suspend fun searchContinuation(
        continuation: String
    ): Result<SearchResult> = runCatching {
        val response = innerTube
            .search(
                client = WEB_REMIX,
                continuation = continuation
            )
            .body<SearchResponse>()

        val continuationContents = response.continuationContents?.musicShelfContinuation

        val items = continuationContents?.contents.orEmpty().mapNotNull { content ->
            SearchPage.toYTItem(content.musicResponsiveListItemRenderer)
        }

        val nextContinuation = continuationContents?.continuations?.getContinuation()

        SearchResult(items = items, continuation = nextContinuation)
    }

    suspend fun album(
        browseId: String,
        withSongs: Boolean = true
    ): Result<AlbumPage> = runCatching {
        val response = innerTube.browse(WEB_REMIX, browseId).body<BrowseResponse>()

        val playlistId = response.microformat
            ?.microformatDataRenderer
            ?.urlCanonical
            ?.substringAfterLast('=') ?: browseId

        AlbumPage(
            album = AlbumItem(
                browseId = browseId,
                playlistId = playlistId,
                title = response
                    .contents
                    ?.twoColumnBrowseResultsRenderer
                    ?.tabs
                    ?.firstOrNull()
                    ?.tabRenderer
                    ?.content
                    ?.sectionListRenderer
                    ?.contents
                    ?.firstOrNull()
                    ?.musicResponsiveHeaderRenderer
                    ?.title
                    ?.runs
                    ?.firstOrNull()
                    ?.text ?: "Unknown Album",
                artists = response
                    .contents
                    ?.twoColumnBrowseResultsRenderer
                    ?.tabs
                    ?.firstOrNull()
                    ?.tabRenderer
                    ?.content
                    ?.sectionListRenderer
                    ?.contents
                    ?.firstOrNull()
                    ?.musicResponsiveHeaderRenderer
                    ?.straplineTextOne
                    ?.runs
                    ?.oddElements()
                    ?.map {
                        Artist(
                            name = it.text,
                            id = it.navigationEndpoint?.browseEndpoint?.browseId
                        )
                    }.orEmpty(),
                year = response
                    .contents
                    ?.twoColumnBrowseResultsRenderer
                    ?.tabs
                    ?.firstOrNull()
                    ?.tabRenderer
                    ?.content
                    ?.sectionListRenderer
                    ?.contents
                    ?.firstOrNull()
                    ?.musicResponsiveHeaderRenderer
                    ?.subtitle
                    ?.runs
                    ?.lastOrNull()
                    ?.text
                    ?.toIntOrNull(),
                thumbnail = response
                    .contents
                    ?.twoColumnBrowseResultsRenderer
                    ?.tabs
                    ?.firstOrNull()
                    ?.tabRenderer
                    ?.content
                    ?.sectionListRenderer
                    ?.contents
                    ?.firstOrNull()
                    ?.musicResponsiveHeaderRenderer
                    ?.thumbnail
                    ?.musicThumbnailRenderer
                    ?.thumbnail
                    ?.thumbnails
                    ?.lastOrNull()
                    ?.url ?: ""
            ),
            songs = if (withSongs) {
                albumSongs(playlistId).getOrDefault(emptyList())
            } else {
                emptyList()
            },
            otherVersions = response
                .contents
                ?.twoColumnBrowseResultsRenderer
                ?.secondaryContents
                ?.sectionListRenderer
                ?.contents
                ?.getOrNull(1)
                ?.musicCarouselShelfRenderer
                ?.contents
                ?.mapNotNull { it.musicTwoRowItemRenderer }
                ?.mapNotNull(NewReleaseAlbumPage::fromMusicTwoRowItemRenderer)
                .orEmpty()
        )
    }

    suspend fun albumSongs(
        playlistId: String
    ): Result<List<SongItem>> = runCatching {
        var response = innerTube.browse(WEB_REMIX, "VL$playlistId").body<BrowseResponse>()

        val songs = response
            .contents
            ?.twoColumnBrowseResultsRenderer
            ?.secondaryContents
            ?.sectionListRenderer
            ?.contents
            ?.firstOrNull()
            ?.musicPlaylistShelfRenderer
            ?.contents
            ?.mapNotNull {
                AlbumPage.fromMusicResponsiveListItemRenderer(it.musicResponsiveListItemRenderer)
            }.orEmpty().toMutableList()

        var continuation = response
            .contents
            ?.twoColumnBrowseResultsRenderer
            ?.secondaryContents
            ?.sectionListRenderer
            ?.contents
            ?.firstOrNull()
            ?.musicPlaylistShelfRenderer
            ?.continuations
            ?.getContinuation()

        while (continuation != null) {
            response = innerTube.browse(client = WEB_REMIX, continuation = continuation).body<BrowseResponse>()

            songs += response
                .continuationContents
                ?.musicPlaylistShelfContinuation
                ?.contents
                ?.mapNotNull {
                    AlbumPage.fromMusicResponsiveListItemRenderer(it.musicResponsiveListItemRenderer)
                }.orEmpty()

            continuation = response
                .continuationContents
                ?.musicPlaylistShelfContinuation
                ?.continuations
                ?.getContinuation()
        }
        songs
    }

    suspend fun artist(
        browseId: String
    ): Result<ArtistPage> = runCatching {
        val response = innerTube.browse(WEB_REMIX, browseId).body<BrowseResponse>()

        ArtistPage(
            artist = ArtistItem(
                id = browseId,
                title = response.header?.musicImmersiveHeaderRenderer?.title?.runs?.firstOrNull()?.text
                    ?: response.header?.musicVisualHeaderRenderer?.title?.runs?.firstOrNull()?.text ?: "Unknown Artist",
                thumbnail = response.header?.musicImmersiveHeaderRenderer?.thumbnail?.musicThumbnailRenderer?.getThumbnailUrl()
                    ?: response.header?.musicVisualHeaderRenderer?.foregroundThumbnail?.musicThumbnailRenderer?.getThumbnailUrl() ?: "",
                shuffleEndpoint = response.header?.musicImmersiveHeaderRenderer?.playButton?.buttonRenderer?.navigationEndpoint?.watchEndpoint,
                radioEndpoint = response.header?.musicImmersiveHeaderRenderer?.startRadioButton?.buttonRenderer?.navigationEndpoint?.watchEndpoint
            ),
            sections = response.contents?.singleColumnBrowseResultsRenderer?.tabs?.firstOrNull()?.tabRenderer?.content?.sectionListRenderer?.contents?.mapNotNull(
                ArtistPage::fromSectionListRendererContent
            ).orEmpty(),
            description = response.header?.musicImmersiveHeaderRenderer?.description?.runs?.firstOrNull()?.text
        )
    }

    suspend fun artistItems(
        endpoint: BrowseEndpoint
    ): Result<ArtistItemsPage> = runCatching {
        val response = innerTube.browse(WEB_REMIX, endpoint.browseId, endpoint.params).body<BrowseResponse>()

        val gridRenderer = response.contents?.singleColumnBrowseResultsRenderer?.tabs?.firstOrNull()?.tabRenderer?.content?.sectionListRenderer?.contents?.firstOrNull()?.gridRenderer

        if (gridRenderer != null) {
            ArtistItemsPage(
                title = gridRenderer.header?.gridHeaderRenderer?.title?.runs?.firstOrNull()?.text.orEmpty(),
                items = gridRenderer.items.mapNotNull { it.musicTwoRowItemRenderer }.mapNotNull { renderer ->
                    ArtistItemsPage.fromMusicTwoRowItemRenderer(renderer)
                },
                continuation = gridRenderer.continuations?.getContinuation()
            )
        } else {
            ArtistItemsPage(
                title = response.header?.musicHeaderRenderer?.title?.runs?.firstOrNull()?.text ?: "",
                items = response.contents?.singleColumnBrowseResultsRenderer?.tabs?.firstOrNull()?.tabRenderer?.content?.sectionListRenderer?.contents?.firstOrNull()?.musicPlaylistShelfRenderer?.contents?.mapNotNull {
                    ArtistItemsPage.fromMusicResponsiveListItemRenderer(it.musicResponsiveListItemRenderer)
                }.orEmpty(),
                continuation = response.contents?.singleColumnBrowseResultsRenderer?.tabs?.firstOrNull()?.tabRenderer?.content?.sectionListRenderer?.contents?.firstOrNull()?.musicPlaylistShelfRenderer?.continuations?.getContinuation()
            )
        }
    }

    suspend fun artistItemsContinuation(
        continuation: String
    ): Result<ArtistItemsContinuationPage> = runCatching {
        val response = innerTube.browse(WEB_REMIX, continuation = continuation).body<BrowseResponse>()

        val gridContinuation = response.continuationContents?.gridContinuation

        if (gridContinuation != null) {
            ArtistItemsContinuationPage(
                items = gridContinuation.items.mapNotNull { it.musicTwoRowItemRenderer }.mapNotNull { renderer ->
                    ArtistItemsPage.fromMusicTwoRowItemRenderer(renderer)
                },
                continuation = gridContinuation.continuations?.getContinuation()
            )
        } else {
            ArtistItemsContinuationPage(
                items = response.continuationContents?.musicPlaylistShelfContinuation?.contents?.mapNotNull {
                    ArtistItemsPage.fromMusicResponsiveListItemRenderer(it.musicResponsiveListItemRenderer)
                }.orEmpty(),
                continuation = response.continuationContents?.musicPlaylistShelfContinuation?.continuations?.getContinuation()
            )
        }
    }

    suspend fun playlist(
        playlistId: String
    ): Result<PlaylistPage> = runCatching {
        val response = innerTube.browse(client = WEB_REMIX, browseId = "VL$playlistId", setLogin = true).body<BrowseResponse>()

        val base = response.contents?.twoColumnBrowseResultsRenderer?.tabs?.firstOrNull()?.tabRenderer?.content?.sectionListRenderer?.contents?.firstOrNull()
        val header = base?.musicResponsiveHeaderRenderer ?: base?.musicEditablePlaylistDetailHeaderRenderer?.header?.musicResponsiveHeaderRenderer

        if (header == null) {
            throw IllegalStateException("Playlist không tồn tại hoặc tài khoản chưa khởi tạo YouTube Music.")
        }

        PlaylistPage(
            playlist = PlaylistItem(
                id = playlistId,
                title = header.title?.runs?.firstOrNull()?.text ?: "Unknown Playlist",
                author = header.straplineTextOne?.runs?.firstOrNull()?.let {
                    Artist(name = it.text, id = it.navigationEndpoint?.browseEndpoint?.browseId)
                },
                songCountText = header.secondSubtitle?.runs?.firstOrNull()?.text,
                thumbnail = header.thumbnail?.musicThumbnailRenderer?.thumbnail?.thumbnails?.lastOrNull()?.url ?: "",
                playEndpoint = null,
                shuffleEndpoint = header.buttons?.lastOrNull()?.menuRenderer?.items?.firstOrNull()?.menuNavigationItemRenderer?.navigationEndpoint?.watchPlaylistEndpoint ?: WatchEndpoint(playlistId = playlistId),
                radioEndpoint = header.buttons?.lastOrNull()?.menuRenderer?.items?.find { it.menuNavigationItemRenderer?.icon?.iconType == "MIX" }?.menuNavigationItemRenderer?.navigationEndpoint?.watchPlaylistEndpoint ?: WatchEndpoint(playlistId = "RDAMPL$playlistId")
            ),
            songs = response.contents?.twoColumnBrowseResultsRenderer?.secondaryContents?.sectionListRenderer?.contents?.firstOrNull()?.musicPlaylistShelfRenderer?.contents?.mapNotNull {
                PlaylistPage.fromMusicResponsiveListItemRenderer(it.musicResponsiveListItemRenderer)
            }.orEmpty(),
            songsContinuation = response.contents?.twoColumnBrowseResultsRenderer?.secondaryContents?.sectionListRenderer?.contents?.firstOrNull()?.musicPlaylistShelfRenderer?.continuations?.getContinuation(),
            continuation = response.contents?.twoColumnBrowseResultsRenderer?.secondaryContents?.sectionListRenderer?.continuations?.getContinuation()
        )
    }

    suspend fun playlistContinuation(
        continuation: String
    ) = runCatching {
        val response = innerTube.browse(client = WEB_REMIX, continuation = continuation, setLogin = true).body<BrowseResponse>()

        PlaylistContinuationPage(
            songs = response.continuationContents?.musicPlaylistShelfContinuation?.contents?.mapNotNull {
                PlaylistPage.fromMusicResponsiveListItemRenderer(it.musicResponsiveListItemRenderer)
            }.orEmpty(),
            continuation = response.continuationContents?.musicPlaylistShelfContinuation?.continuations?.getContinuation()
        )
    }

    suspend fun home(): Result<HomePage> = runCatching {
        var response = innerTube.browse(WEB_REMIX, browseId = "FEmusic_home").body<BrowseResponse>()

        var continuation = response.contents?.singleColumnBrowseResultsRenderer?.tabs?.firstOrNull()?.tabRenderer?.content?.sectionListRenderer?.continuations?.getContinuation()

        val sections = response.contents?.singleColumnBrowseResultsRenderer?.tabs?.firstOrNull()?.tabRenderer?.content?.sectionListRenderer?.contents.orEmpty()
            .mapNotNull { it.musicCarouselShelfRenderer }
            .mapNotNull { HomePage.Section.fromMusicCarouselShelfRenderer(it) }
            .toMutableList()

        while (continuation != null) {
            response = innerTube.browse(WEB_REMIX, continuation = continuation).body<BrowseResponse>()

            continuation = response.continuationContents?.sectionListContinuation?.continuations?.getContinuation()

            sections += response.continuationContents?.sectionListContinuation?.contents
                ?.mapNotNull { it.musicCarouselShelfRenderer }
                ?.mapNotNull { HomePage.Section.fromMusicCarouselShelfRenderer(it) }
                .orEmpty()
        }

        HomePage(sections)
    }

    suspend fun explore(): Result<ExplorePage> = runCatching {
        val response = innerTube.browse(WEB_REMIX, browseId = "FEmusic_explore").body<BrowseResponse>()

        ExplorePage(
            newReleaseAlbums = response.contents?.singleColumnBrowseResultsRenderer?.tabs?.firstOrNull()?.tabRenderer?.content?.sectionListRenderer?.contents?.find {
                it.musicCarouselShelfRenderer?.header?.musicCarouselShelfBasicHeaderRenderer?.moreContentButton?.buttonRenderer?.navigationEndpoint?.browseEndpoint?.browseId == "FEmusic_new_releases_albums"
            }?.musicCarouselShelfRenderer?.contents?.mapNotNull { it.musicTwoRowItemRenderer }?.mapNotNull(NewReleaseAlbumPage::fromMusicTwoRowItemRenderer).orEmpty(),
            moodAndGenres = response.contents?.singleColumnBrowseResultsRenderer?.tabs?.firstOrNull()?.tabRenderer?.content?.sectionListRenderer?.contents?.find {
                it.musicCarouselShelfRenderer?.header?.musicCarouselShelfBasicHeaderRenderer?.moreContentButton?.buttonRenderer?.navigationEndpoint?.browseEndpoint?.browseId == "FEmusic_moods_and_genres"
            }?.musicCarouselShelfRenderer?.contents?.mapNotNull { it.musicNavigationButtonRenderer }?.mapNotNull(MoodAndGenres.Companion::fromMusicNavigationButtonRenderer).orEmpty()
        )
    }

    suspend fun newReleaseAlbums(): Result<List<AlbumItem>> = runCatching {
        val response = innerTube.browse(WEB_REMIX, browseId = "FEmusic_new_releases_albums").body<BrowseResponse>()
        response.contents?.singleColumnBrowseResultsRenderer?.tabs?.firstOrNull()?.tabRenderer?.content?.sectionListRenderer?.contents?.firstOrNull()?.gridRenderer?.items?.mapNotNull {
            it.musicTwoRowItemRenderer
        }?.mapNotNull(NewReleaseAlbumPage::fromMusicTwoRowItemRenderer).orEmpty()
    }

    suspend fun moodAndGenres(): Result<List<MoodAndGenres>> = runCatching {
        val response = innerTube.browse(WEB_REMIX, browseId = "FEmusic_moods_and_genres").body<BrowseResponse>()
        response.contents?.singleColumnBrowseResultsRenderer?.tabs?.firstOrNull()?.tabRenderer?.content?.sectionListRenderer?.contents.orEmpty().mapNotNull(MoodAndGenres.Companion::fromSectionListRendererContent)
    }

    suspend fun browse(
        browseId: String,
        params: String?
    ): Result<BrowseResult> = runCatching {
        val response = innerTube.browse(WEB_REMIX, browseId = browseId, params = params).body<BrowseResponse>()

        BrowseResult(
            title = response.header?.musicHeaderRenderer?.title?.runs?.firstOrNull()?.text,
            items = response.contents?.singleColumnBrowseResultsRenderer?.tabs?.firstOrNull()?.tabRenderer?.content?.sectionListRenderer?.contents?.mapNotNull { content ->
                when {
                    content.gridRenderer != null -> {
                        BrowseResult.Item(
                            title = content.gridRenderer.header?.gridHeaderRenderer?.title?.runs?.firstOrNull()?.text,
                            items = content.gridRenderer.items.mapNotNull(GridRenderer.Item::musicTwoRowItemRenderer).mapNotNull(RelatedPage.Companion::fromMusicTwoRowItemRenderer)
                        )
                    }
                    content.musicCarouselShelfRenderer != null -> {
                        BrowseResult.Item(
                            title = content.musicCarouselShelfRenderer.header?.musicCarouselShelfBasicHeaderRenderer?.title?.runs?.firstOrNull()?.text,
                            items = content.musicCarouselShelfRenderer.contents.mapNotNull(MusicCarouselShelfRenderer.Content::musicTwoRowItemRenderer).mapNotNull(RelatedPage.Companion::fromMusicTwoRowItemRenderer)
                        )
                    }
                    else -> null
                }
            }.orEmpty()
        )
    }

    suspend fun likedPlaylists(): Result<List<PlaylistItem>> = runCatching {
        val response = innerTube.browse(client = WEB_REMIX, browseId = "FEmusic_liked_playlists", setLogin = true).body<BrowseResponse>()
        val grid = response.contents?.singleColumnBrowseResultsRenderer?.tabs?.firstOrNull()
            ?.tabRenderer?.content?.sectionListRenderer?.contents
            ?.firstNotNullOfOrNull { it.gridRenderer }

        // The first tile is the "New playlist" button.
        val items = grid?.items.orEmpty().drop(1).toMutableList()
        var continuation = grid?.continuations?.getContinuation()
        var pages = 0
        while (continuation != null && pages++ < MAX_LIBRARY_PAGES) {
            val next = innerTube.browse(client = WEB_REMIX, continuation = continuation, setLogin = true)
                .body<BrowseResponse>().continuationContents?.gridContinuation ?: break
            items += next.items
            continuation = next.continuations?.getContinuation()
        }

        items.mapNotNull(GridRenderer.Item::musicTwoRowItemRenderer)
            .mapNotNull { ArtistItemsPage.fromMusicTwoRowItemRenderer(it) as? PlaylistItem }
    }

    private const val MAX_LIBRARY_PAGES = 30

    suspend fun libraryAlbums(): Result<List<AlbumItem>> = runCatching {
        val response = innerTube.browse(client = WEB_REMIX, browseId = "FEmusic_liked_albums", setLogin = true).body<BrowseResponse>()
        val grid = response.contents?.singleColumnBrowseResultsRenderer?.tabs?.firstOrNull()
            ?.tabRenderer?.content?.sectionListRenderer?.contents
            ?.firstNotNullOfOrNull { it.gridRenderer }

        val items = grid?.items.orEmpty().toMutableList()
        var continuation = grid?.continuations?.getContinuation()
        var pages = 0
        while (continuation != null && pages++ < MAX_LIBRARY_PAGES) {
            val next = innerTube.browse(client = WEB_REMIX, continuation = continuation, setLogin = true)
                .body<BrowseResponse>().continuationContents?.gridContinuation ?: break
            items += next.items
            continuation = next.continuations?.getContinuation()
        }

        items.mapNotNull(GridRenderer.Item::musicTwoRowItemRenderer)
            .mapNotNull { ArtistItemsPage.fromMusicTwoRowItemRenderer(it) as? AlbumItem }
    }

    suspend fun libraryArtists(): Result<List<ArtistItem>> = runCatching {
        val response = innerTube.browse(client = WEB_REMIX, browseId = "FEmusic_library_corpus_artists", setLogin = true).body<BrowseResponse>()
        val shelf = response.contents?.singleColumnBrowseResultsRenderer?.tabs?.firstOrNull()
            ?.tabRenderer?.content?.sectionListRenderer?.contents
            ?.firstNotNullOfOrNull { it.musicShelfRenderer }

        val rows = shelf?.contents.orEmpty().toMutableList()
        var continuation = shelf?.continuations?.getContinuation()
        var pages = 0
        while (continuation != null && pages++ < MAX_LIBRARY_PAGES) {
            val next = innerTube.browse(client = WEB_REMIX, continuation = continuation, setLogin = true)
                .body<BrowseResponse>().continuationContents?.musicShelfContinuation ?: break
            rows += next.contents
            continuation = next.continuations?.getContinuation()
        }

        rows.mapNotNull { SearchPage.toYTItem(it.musicResponsiveListItemRenderer) as? ArtistItem }
    }

    suspend fun player(
        videoId: String,
        playlistId: String? = null
    ): Result<PlayerResponse> = runCatching {
        var playerResponse: PlayerResponse

        if (this.cookie != null) {
            playerResponse = innerTube.player(ANDROID_MUSIC, videoId, playlistId).body<PlayerResponse>()
            if (playerResponse.playabilityStatus.status == "OK") {
                return@runCatching playerResponse
            }
        }

        playerResponse = innerTube.player(IOS, videoId, playlistId).body<PlayerResponse>()

        if (playerResponse.playabilityStatus.status == "OK") {
            return@runCatching playerResponse
        }

        val safePlayerResponse = innerTube.player(TVHTML5, videoId, playlistId).body<PlayerResponse>()

        if (safePlayerResponse.playabilityStatus.status != "OK") {
            return@runCatching playerResponse
        }

        val audioStreams = innerTube.pipedStreams(videoId).body<PipedResponse>().audioStreams

        safePlayerResponse.copy(
            streamingData = safePlayerResponse.streamingData?.copy(
                adaptiveFormats = safePlayerResponse.streamingData.adaptiveFormats.mapNotNull { adaptiveFormat ->
                    audioStreams.find { it.bitrate == adaptiveFormat.bitrate }?.let {
                        adaptiveFormat.copy(url = it.url)
                    }
                }
            )
        )
    }

    suspend fun next(
        endpoint: WatchEndpoint,
        continuation: String? = null
    ): Result<NextResult> = runCatching {
        val response = innerTube.next(WEB_REMIX, endpoint.videoId, endpoint.playlistId, endpoint.playlistSetVideoId, endpoint.index, endpoint.params, continuation).body<NextResponse>()

        val title = response.contents.singleColumnMusicWatchNextResultsRenderer.tabbedRenderer.watchNextTabbedResultsRenderer.tabs[0].tabRenderer.content?.musicQueueRenderer?.header?.musicQueueHeaderRenderer?.subtitle?.runs?.firstOrNull()?.text

        val playlistPanelRenderer = response.continuationContents?.playlistPanelContinuation ?: response.contents.singleColumnMusicWatchNextResultsRenderer.tabbedRenderer.watchNextTabbedResultsRenderer.tabs[0].tabRenderer.content?.musicQueueRenderer?.content?.playlistPanelRenderer

        if (playlistPanelRenderer == null) {
            throw Exception("PlaylistPanelRenderer is null")
        }

        val items = playlistPanelRenderer.contents.mapNotNull { content ->
            content.playlistPanelVideoRenderer?.let(NextPage::fromPlaylistPanelVideoRenderer)?.let {
                it to content.playlistPanelVideoRenderer.selected
            }
        }

        val songs = items.map { it.first }

        val currentIndex = items.indexOfFirst { it.second }.takeIf { it != -1 }

        playlistPanelRenderer.contents.lastOrNull()?.automixPreviewVideoRenderer?.content?.automixPlaylistVideoRenderer?.navigationEndpoint?.watchPlaylistEndpoint?.let { watchPlaylistEndpoint ->
            return@runCatching next(watchPlaylistEndpoint).getOrThrow().let { result ->
                result.copy(
                    title = title,
                    items = songs + result.items,
                    lyricsEndpoint = response.contents.singleColumnMusicWatchNextResultsRenderer.tabbedRenderer.watchNextTabbedResultsRenderer.tabs.getOrNull(1)?.tabRenderer?.endpoint?.browseEndpoint,
                    relatedEndpoint = response.contents.singleColumnMusicWatchNextResultsRenderer.tabbedRenderer.watchNextTabbedResultsRenderer.tabs.getOrNull(2)?.tabRenderer?.endpoint?.browseEndpoint,
                    currentIndex = currentIndex,
                    endpoint = watchPlaylistEndpoint
                )
            }
        }

        NextResult(
            title = title,
            items = songs,
            currentIndex = currentIndex,
            lyricsEndpoint = response.contents.singleColumnMusicWatchNextResultsRenderer.tabbedRenderer.watchNextTabbedResultsRenderer.tabs.getOrNull(1)?.tabRenderer?.endpoint?.browseEndpoint,
            relatedEndpoint = response.contents.singleColumnMusicWatchNextResultsRenderer.tabbedRenderer.watchNextTabbedResultsRenderer.tabs.getOrNull(2)?.tabRenderer?.endpoint?.browseEndpoint,
            continuation = playlistPanelRenderer.continuations?.getContinuation(),
            endpoint = endpoint
        )
    }

    suspend fun lyrics(
        endpoint: BrowseEndpoint
    ): Result<String?> = runCatching {
        val response = innerTube.browse(WEB_REMIX, endpoint.browseId, endpoint.params).body<BrowseResponse>()
        response.contents?.sectionListRenderer?.contents?.firstOrNull()?.musicDescriptionShelfRenderer?.description?.runs?.firstOrNull()?.text
    }

    suspend fun related(
        endpoint: BrowseEndpoint
    ): Result<RelatedPage> = runCatching {
        val response = innerTube.browse(WEB_REMIX, endpoint.browseId).body<BrowseResponse>()

        val songs = mutableListOf<SongItem>()
        val albums = mutableListOf<AlbumItem>()
        val artists = mutableListOf<ArtistItem>()
        val playlists = mutableListOf<PlaylistItem>()

        response.contents?.sectionListRenderer?.contents?.forEach { sectionContent ->
            sectionContent.musicCarouselShelfRenderer?.contents?.forEach { content ->
                when (
                    val item = content.musicResponsiveListItemRenderer?.let(RelatedPage.Companion::fromMusicResponsiveListItemRenderer) ?: content.musicTwoRowItemRenderer?.let(RelatedPage.Companion::fromMusicTwoRowItemRenderer)
                ) {
                    is SongItem -> if (content.musicResponsiveListItemRenderer?.overlay?.musicItemThumbnailOverlayRenderer?.content?.musicPlayButtonRenderer?.playNavigationEndpoint?.watchEndpoint?.watchEndpointMusicSupportedConfigs?.watchEndpointMusicConfig?.musicVideoType == MUSIC_VIDEO_TYPE_ATV) {
                        songs.add(item)
                    }
                    is AlbumItem -> albums.add(item)
                    is ArtistItem -> artists.add(item)
                    is PlaylistItem -> playlists.add(item)
                    null -> {}
                }
            }
        }

        RelatedPage(songs, albums, artists, playlists)
    }

    suspend fun queue(
        videoIds: List<String>? = null,
        playlistId: String? = null
    ): Result<List<SongItem>> = runCatching {
        if (videoIds != null) {
            assert(videoIds.size <= MAX_GET_QUEUE_SIZE)
        }

        innerTube.getQueue(WEB_REMIX, videoIds, playlistId).body<GetQueueResponse>().queueDatas.mapNotNull {
            it.content.playlistPanelVideoRenderer?.let(NextPage::fromPlaylistPanelVideoRenderer)
        }
    }

    suspend fun transcript(
        videoId: String
    ): Result<String> = runCatching {
        val response = innerTube.getTranscript(WEB, videoId).body<GetTranscriptResponse>()

        response.actions?.firstOrNull()?.updateEngagementPanelAction?.content?.transcriptRenderer?.body?.transcriptBodyRenderer?.cueGroups?.joinToString(separator = "\n") { group ->
            val time = group.transcriptCueGroupRenderer.cues[0].transcriptCueRenderer.startOffsetMs
            val text = group.transcriptCueGroupRenderer.cues[0].transcriptCueRenderer.cue.simpleText.trim('♪').trim(' ')
            "[%02d:%02d.%03d]$text".format(time / 60000, (time / 1000) % 60, time % 1000)
        } ?: ""
    }

    suspend fun visitorData(): Result<String> = runCatching {
        Json.parseToJsonElement(innerTube.getSwJsData().bodyAsText().substring(5)).jsonArray[0].jsonArray[2].jsonArray.first {
            (it as? JsonPrimitive)?.content?.startsWith(VISITOR_DATA_PREFIX) == true
        }.jsonPrimitive.content
    }

    suspend fun accountInfo(): Result<AccountInfo> = runCatching {
        innerTube.accountMenu(WEB_REMIX).body<AccountMenuResponse>().actions[0].openPopupAction.popup.multiPageMenuRenderer.header?.activeAccountHeaderRenderer?.toAccountInfo() ?: throw Exception("Không lấy được thông tin tài khoản")
    }

    @JvmInline
    value class SearchFilter(val value: String) {
        companion object {
            val FILTER_SONG = SearchFilter("EgWKAQIIAWoKEAkQBRAKEAMQBA%3D%3D")
            val FILTER_VIDEO = SearchFilter("EgWKAQIQAWoKEAkQChAFEAMQBA%3D%3D")
            val FILTER_ALBUM = SearchFilter("EgWKAQIYAWoKEAkQChAFEAMQBA%3D%3D")
            val FILTER_ARTIST = SearchFilter("EgWKAQIgAWoKEAkQChAFEAMQBA%3D%3D")
            val FILTER_FEATURED_PLAYLIST = SearchFilter("EgeKAQQoADgBagwQDhAKEAMQBRAJEAQ%3D")
            val FILTER_COMMUNITY_PLAYLIST = SearchFilter("EgeKAQQoAEABagoQAxAEEAoQCRAF")
        }
    }

    suspend fun like(videoId: String): Result<Boolean> = runCatching {
        innerTube.likeEndpoint(WEB_REMIX, videoId)
        true
    }

    suspend fun unlike(videoId: String): Result<Boolean> = runCatching {
        innerTube.unlikeEndpoint(WEB_REMIX, videoId)
        true
    }

    suspend fun likePlaylist(playlistId: String): Result<Boolean> = runCatching {
        innerTube.likePlaylistEndpoint(WEB_REMIX, playlistId)
        true
    }

    suspend fun unlikePlaylist(playlistId: String): Result<Boolean> = runCatching {
        innerTube.unlikePlaylistEndpoint(WEB_REMIX, playlistId)
        true
    }

    suspend fun createPlaylist(title: String, videoIds: List<String>): Result<String> = runCatching {
        innerTube.createPlaylist(WEB_REMIX, title, videoIds)
            .body<CreatePlaylistResponse>().playlistId
            ?: throw IllegalStateException("YouTube không trả về id playlist mới")
    }

    suspend fun addToPlaylist(playlistId: String, videoIds: List<String>): Result<Boolean> = runCatching {
        innerTube.editPlaylist(WEB_REMIX, playlistId, videoIds.map { EditPlaylistBody.Action.add(it) })
        true
    }

    suspend fun removeFromPlaylist(playlistId: String, items: List<Pair<String, String>>): Result<Boolean> = runCatching {
        innerTube.editPlaylist(
            WEB_REMIX, playlistId,
            items.map { (videoId, setVideoId) -> EditPlaylistBody.Action.remove(videoId, setVideoId) }
        )
        true
    }

    /** Applies [moves] (setVideoId to successorSetVideoId) in order, in a single request. */
    suspend fun reorderPlaylist(playlistId: String, moves: List<Pair<String, String>>): Result<Boolean> = runCatching {
        innerTube.editPlaylist(
            WEB_REMIX, playlistId,
            moves.map { (setVideoId, successor) -> EditPlaylistBody.Action.moveBefore(setVideoId, successor) }
        )
        true
    }

    suspend fun renamePlaylist(playlistId: String, name: String): Result<Boolean> = runCatching {
        innerTube.editPlaylist(WEB_REMIX, playlistId, listOf(EditPlaylistBody.Action.rename(name)))
        true
    }

    suspend fun deletePlaylist(playlistId: String): Result<Boolean> = runCatching {
        innerTube.deletePlaylist(WEB_REMIX, playlistId)
        true
    }

    const val MAX_GET_QUEUE_SIZE = 1000
    private const val VISITOR_DATA_PREFIX = "Cgt"
    const val DEFAULT_VISITOR_DATA = "CgtsZG1ySnZiQWtSbyiMjuGSBg%3D%3D"
}
