package com.caohaidang.edward.music.playback

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.database.SQLException
import android.media.audiofx.AudioEffect
import android.net.ConnectivityManager
import android.net.Network
import android.os.Binder
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import androidx.datastore.preferences.core.edit
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Player.EVENT_POSITION_DISCONTINUITY
import androidx.media3.common.Player.EVENT_TIMELINE_CHANGED
import androidx.media3.common.Player.REPEAT_MODE_ALL
import androidx.media3.common.Player.REPEAT_MODE_OFF
import androidx.media3.common.Player.REPEAT_MODE_ONE
import androidx.media3.common.Player.STATE_IDLE
import androidx.media3.common.Timeline
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.analytics.PlaybackStats
import androidx.media3.exoplayer.analytics.PlaybackStatsListener
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.SilenceSkippingAudioProcessor
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.mkv.MatroskaExtractor
import androidx.media3.extractor.mp4.FragmentedMp4Extractor
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaController
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.caohaidang.edward.innertube.YouTube
import com.caohaidang.edward.innertube.models.SongItem
import com.caohaidang.edward.innertube.models.WatchEndpoint
import com.caohaidang.edward.innertube.models.response.PlayerResponse
import com.caohaidang.edward.music.MainActivity
import com.caohaidang.edward.music.R
import com.caohaidang.edward.music.constants.AudioNormalizationKey
import com.caohaidang.edward.music.constants.AudioQuality
import com.caohaidang.edward.music.constants.AudioQualityKey
import com.caohaidang.edward.music.constants.AutoLoadMoreKey
import com.caohaidang.edward.music.constants.AutoPlayKey
import com.caohaidang.edward.music.constants.AutoSkipNextOnErrorKey
import com.caohaidang.edward.music.constants.StopMusicOnTaskClearKey
import com.caohaidang.edward.music.constants.HideExplicitKey
import com.caohaidang.edward.music.constants.MediaSessionConstants.CommandToggleLibrary
import com.caohaidang.edward.music.constants.MediaSessionConstants.CommandToggleLike
import com.caohaidang.edward.music.widget.CdPlayerWidgetUpdater
import com.caohaidang.edward.music.constants.MediaSessionConstants.CommandToggleRepeatMode
import com.caohaidang.edward.music.constants.MediaSessionConstants.CommandToggleShuffle
import com.caohaidang.edward.music.constants.PauseListenHistoryKey
import com.caohaidang.edward.music.constants.PersistentQueueKey
import com.caohaidang.edward.music.constants.PlayerVolumeKey
import com.caohaidang.edward.music.constants.RepeatModeKey
import com.caohaidang.edward.music.constants.ShowLyricsKey
import com.caohaidang.edward.music.constants.SkipSilenceKey
import com.caohaidang.edward.music.db.MusicDatabase
import com.caohaidang.edward.music.db.entities.Event
import com.caohaidang.edward.music.db.entities.FormatEntity
import com.caohaidang.edward.music.db.entities.LyricsEntity
import com.caohaidang.edward.music.db.entities.RelatedSongMap
import com.caohaidang.edward.music.di.DownloadCache
import com.caohaidang.edward.music.di.PlayerCache
import com.caohaidang.edward.music.extensions.SilentHandler
import com.caohaidang.edward.music.extensions.collect
import com.caohaidang.edward.music.extensions.collectLatest
import com.caohaidang.edward.music.extensions.currentMetadata
import com.caohaidang.edward.music.extensions.findNextMediaItemById
import com.caohaidang.edward.music.extensions.mediaItems
import com.caohaidang.edward.music.extensions.metadata
import com.caohaidang.edward.music.extensions.toMediaItem
import com.caohaidang.edward.music.lyrics.LyricsHelper
import com.caohaidang.edward.music.models.PersistQueue
import com.caohaidang.edward.music.models.toMediaMetadata
import com.caohaidang.edward.music.playback.queues.EmptyQueue
import com.caohaidang.edward.music.playback.queues.ListQueue
import com.caohaidang.edward.music.playback.queues.Queue
import com.caohaidang.edward.music.playback.queues.YouTubeQueue
import com.caohaidang.edward.music.playback.queues.filterExplicit
import com.caohaidang.edward.music.utils.CoilBitmapLoader
import com.caohaidang.edward.music.utils.dataStore
import com.caohaidang.edward.music.utils.enumPreference
import com.caohaidang.edward.music.utils.get
import com.caohaidang.edward.music.utils.isInternetAvailable
import com.caohaidang.edward.music.utils.reportException
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import java.time.LocalDateTime
import javax.inject.Inject
import kotlin.math.min
import kotlin.math.pow
import kotlin.time.Duration.Companion.seconds
import android.util.Log

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@AndroidEntryPoint
class MusicService : MediaLibraryService(),
    Player.Listener,
    PlaybackStatsListener.Callback {
    @Inject
    lateinit var database: MusicDatabase

    @Inject
    lateinit var lyricsHelper: LyricsHelper

    @Inject
    lateinit var mediaLibrarySessionCallback: MediaLibrarySessionCallback

    private var scope = CoroutineScope(Dispatchers.Main) + Job()
    private val binder = MusicBinder()

    private lateinit var connectivityManager: ConnectivityManager

    private val audioQuality by enumPreference(this, AudioQualityKey, AudioQuality.AUTO)

    private var currentQueue: Queue = EmptyQueue
    private var autoPlayJob: Job? = null
    private var retryJob: Job? = null
    private var retryMediaId: String? = null
    private var retryCount = 0
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var queueLoadCount = 0
    private val queueLoading get() = queueLoadCount > 0
    var queueTitle: String? = null

    val currentMediaMetadata = MutableStateFlow<com.caohaidang.edward.music.models.MediaMetadata?>(null)
    private val currentSong = currentMediaMetadata.flatMapLatest { mediaMetadata ->
        database.song(mediaMetadata?.id)
    }.stateIn(scope, SharingStarted.Lazily, null)
    private val currentFormat = currentMediaMetadata.flatMapLatest { mediaMetadata ->
        database.format(mediaMetadata?.id)
    }

    private val normalizeFactor = MutableStateFlow(1f)
    val playerVolume = MutableStateFlow(dataStore.get(PlayerVolumeKey, 1f).coerceIn(0f, 1f))

    lateinit var sleepTimer: SleepTimer

    @Inject
    @PlayerCache
    lateinit var playerCache: SimpleCache

    @Inject
    @DownloadCache
    lateinit var downloadCache: SimpleCache

    lateinit var player: ExoPlayer
    private lateinit var mediaSession: MediaLibrarySession
    private lateinit var widgetUpdater: CdPlayerWidgetUpdater

    private val streamUrlCache = ConcurrentHashMap<String, ResolvedStream>()
    private val streamRefreshInProgress = ConcurrentHashMap.newKeySet<String>()

    private var isAudioEffectSessionOpened = false

    override fun onCreate() {
        super.onCreate()
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider(this, { NOTIFICATION_ID }, CHANNEL_ID, R.string.music_player)
                .apply {
                    setSmallIcon(R.drawable.logo_notification)
                }
        )
        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(createMediaSourceFactory())
            .setRenderersFactory(createRenderersFactory())
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(), true
            )
            .setSeekBackIncrementMs(5000)
            .setSeekForwardIncrementMs(5000)
            .build()
            .apply {
                addListener(this@MusicService)
                sleepTimer = SleepTimer(scope, this)
                addListener(sleepTimer)
                addAnalyticsListener(PlaybackStatsListener(false, this@MusicService))
            }
        mediaLibrarySessionCallback.apply {
            toggleLike = ::toggleLike
            toggleLibrary = ::toggleLibrary
        }
        mediaSession = MediaLibrarySession.Builder(this, player, mediaLibrarySessionCallback)
            .setSessionActivity(
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE
                )
            )
            .setBitmapLoader(CoilBitmapLoader(this, scope))
            .build()
        player.repeatMode = dataStore.get(RepeatModeKey, REPEAT_MODE_OFF)

        // Home-screen "CD Player" widget: mirrors track / play state / liked flag.
        widgetUpdater = CdPlayerWidgetUpdater(this, scope, player) { currentSong.value?.song?.liked == true }
        widgetUpdater.start()
        scope.launch { currentSong.collect { widgetUpdater.refresh() } }

        val sessionToken = SessionToken(this, ComponentName(this, MusicService::class.java))
        val controllerFuture = MediaController.Builder(this, sessionToken).buildAsync()
        controllerFuture.addListener({ controllerFuture.get() }, MoreExecutors.directExecutor())

        connectivityManager = getSystemService()!!
        registerNetworkCallback()

        combine(playerVolume, normalizeFactor) { playerVolume, normalizeFactor ->
            playerVolume * normalizeFactor
        }.collectLatest(scope) {
            player.volume = it
        }

        playerVolume.debounce(1000).collect(scope) { volume ->
            dataStore.edit { settings ->
                settings[PlayerVolumeKey] = volume
            }
        }

        currentSong.debounce(1000).collect(scope) { song ->
            updateNotification()
        }

        combine(
            currentMediaMetadata.distinctUntilChangedBy { it?.id },
            dataStore.data.map { it[ShowLyricsKey] ?: false }.distinctUntilChanged()
        ) { mediaMetadata, showLyrics ->
            mediaMetadata to showLyrics
        }.collectLatest(scope) { (mediaMetadata, showLyrics) ->
            if (showLyrics && mediaMetadata != null && database.lyrics(mediaMetadata.id).first() == null) {
                val lyrics = lyricsHelper.getLyrics(mediaMetadata)
                database.query {
                    upsert(
                        LyricsEntity(
                            id = mediaMetadata.id,
                            lyrics = lyrics
                        )
                    )
                }
            }
        }

        dataStore.data
            .map { it[SkipSilenceKey] ?: false }
            .distinctUntilChanged()
            .collectLatest(scope) {
                player.skipSilenceEnabled = it
            }

        combine(
            currentFormat,
            dataStore.data
                .map { it[AudioNormalizationKey] ?: true }
                .distinctUntilChanged()
        ) { format, normalizeAudio ->
            format to normalizeAudio
        }.collectLatest(scope) { (format, normalizeAudio) ->
            normalizeFactor.value = if (normalizeAudio && format?.loudnessDb != null) {
                min(10f.pow(-format.loudnessDb.toFloat() / 20), 1f)
            } else {
                1f
            }
        }

        if (dataStore.get(PersistentQueueKey, true)) {
            runCatching {
                filesDir.resolve(PERSISTENT_QUEUE_FILE).inputStream().use { fis ->
                    ObjectInputStream(fis).use { oos ->
                        oos.readObject() as PersistQueue
                    }
                }
            }.onSuccess { queue ->
                playQueue(
                    queue = ListQueue(
                        title = queue.title,
                        items = queue.items.map { it.toMediaItem() },
                        startIndex = queue.mediaItemIndex,
                        position = queue.position
                    ),
                    playWhenReady = false
                )
            }
        }

        scope.launch {
            while (isActive) {
                delay(30.seconds)
                if (dataStore.get(PersistentQueueKey, true)) {
                    saveQueueToDisk()
                }
            }
        }
    }

    private fun updateNotification() {
        mediaSession.setCustomLayout(
            listOf(
                CommandButton.Builder()
                    .setDisplayName(getString(if (currentSong.value?.song?.inLibrary != null) R.string.remove_from_library else R.string.add_to_library))
                    .setIconResId(if (currentSong.value?.song?.inLibrary != null) R.drawable.library_add_check else R.drawable.library_add)
                    .setSessionCommand(CommandToggleLibrary)
                    .setEnabled(currentSong.value != null)
                    .build(),
                CommandButton.Builder()
                    .setDisplayName(getString(if (currentSong.value?.song?.liked == true) R.string.action_remove_like else R.string.action_like))
                    .setIconResId(if (currentSong.value?.song?.liked == true) R.drawable.favorite else R.drawable.favorite_border)
                    .setSessionCommand(CommandToggleLike)
                    .setEnabled(currentSong.value != null)
                    .build(),
                CommandButton.Builder()
                    .setDisplayName(getString(if (player.shuffleModeEnabled) R.string.action_shuffle_off else R.string.action_shuffle_on))
                    .setIconResId(if (player.shuffleModeEnabled) R.drawable.shuffle_on else R.drawable.shuffle)
                    .setSessionCommand(CommandToggleShuffle)
                    .build(),
                CommandButton.Builder()
                    .setDisplayName(
                        getString(
                            when (player.repeatMode) {
                                REPEAT_MODE_OFF -> R.string.repeat_mode_off
                                REPEAT_MODE_ONE -> R.string.repeat_mode_one
                                REPEAT_MODE_ALL -> R.string.repeat_mode_all
                                else -> throw IllegalStateException()
                            }
                        )
                    )
                    .setIconResId(
                        when (player.repeatMode) {
                            REPEAT_MODE_OFF -> R.drawable.repeat
                            REPEAT_MODE_ONE -> R.drawable.repeat_one_on
                            REPEAT_MODE_ALL -> R.drawable.repeat_on
                            else -> throw IllegalStateException()
                        }
                    )
                    .setSessionCommand(CommandToggleRepeatMode)
                    .build()
            )
        )
    }

    private suspend fun recoverSong(mediaId: String, playerResponse: PlayerResponse? = null) {
        val song = database.song(mediaId).first()
        val mediaMetadata = withContext(Dispatchers.Main) {
            player.findNextMediaItemById(mediaId)?.metadata
        } ?: return
        val duration = song?.song?.duration?.takeIf { it != -1 }
            ?: mediaMetadata.duration.takeIf { it != -1 }
            ?: (playerResponse ?: YouTube.player(mediaId).getOrNull())?.videoDetails?.lengthSeconds?.toInt()
            ?: -1
        database.query {
            if (song == null) insert(mediaMetadata.copy(duration = duration))
            else if (song.song.duration == -1) update(song.song.copy(duration = duration))
        }
        if (!database.hasRelatedSongs(mediaId)) {
            val relatedEndpoint = YouTube.next(WatchEndpoint(videoId = mediaId)).getOrNull()?.relatedEndpoint ?: return
            val relatedPage = YouTube.related(relatedEndpoint).getOrNull() ?: return
            database.query {
                relatedPage.songs
                    .map(SongItem::toMediaMetadata)
                    .onEach(::insert)
                    .map {
                        RelatedSongMap(
                            songId = mediaId,
                            relatedSongId = it.id
                        )
                    }
                    .forEach(::insert)
            }
        }
    }

    fun playQueue(queue: Queue, playWhenReady: Boolean = true) {
        if (!scope.isActive) {
            scope = CoroutineScope(Dispatchers.Main) + Job()
        }
        currentQueue = queue
        queueTitle = null
        player.shuffleModeEnabled = false
        if (queue.preloadItem != null) {
            player.setMediaItem(queue.preloadItem!!.toMediaItem())
            player.prepare()
            player.playWhenReady = playWhenReady
        }

        queueLoadCount++
        scope.launch(SilentHandler) {
            try {
                loadInitialQueue(queue, playWhenReady)
            } finally {
                queueLoadCount--
            }
            maybeAutoPlay()
        }
    }

    private suspend fun loadInitialQueue(queue: Queue, playWhenReady: Boolean) {
        run {
            val initialStatus = withContext(Dispatchers.IO) {
                queue.getInitialStatus().filterExplicit(dataStore.get(HideExplicitKey, false))
            }
            if (queue.preloadItem != null && player.playbackState == STATE_IDLE) return
            if (initialStatus.title != null) {
                queueTitle = initialStatus.title
            }
            if (initialStatus.items.isEmpty()) return
            if (queue.preloadItem != null) {
                player.addMediaItems(0, initialStatus.items.subList(0, initialStatus.mediaItemIndex))
                player.addMediaItems(initialStatus.items.subList(initialStatus.mediaItemIndex + 1, initialStatus.items.size))
            } else {
                player.setMediaItems(initialStatus.items, if (initialStatus.mediaItemIndex > 0) initialStatus.mediaItemIndex else 0, initialStatus.position)
                player.prepare()
                player.playWhenReady = playWhenReady
            }
        }
    }

    fun startRadioSeamlessly() {
        val currentMediaMetadata = player.currentMetadata ?: return
        if (player.currentMediaItemIndex > 0) player.removeMediaItems(0, player.currentMediaItemIndex)
        if (player.currentMediaItemIndex < player.mediaItemCount - 1) player.removeMediaItems(player.currentMediaItemIndex + 1, player.mediaItemCount)
        scope.launch(SilentHandler) {
            val radioQueue = YouTubeQueue(endpoint = WatchEndpoint(videoId = currentMediaMetadata.id))
            val initialStatus = radioQueue.getInitialStatus()
            if (initialStatus.title != null) {
                queueTitle = initialStatus.title
            }
            player.addMediaItems(initialStatus.items.drop(1))
            currentQueue = radioQueue
        }
    }

    fun playNext(items: List<MediaItem>) {
        player.addMediaItems(if (player.mediaItemCount == 0) 0 else player.currentMediaItemIndex + 1, items)
        player.prepare()
    }

    fun addToQueue(items: List<MediaItem>) {
        player.addMediaItems(items)
        player.prepare()
    }

    fun toggleLibrary() {
        database.query {
            currentSong.value?.let {
                update(it.song.toggleLibrary())
            }
        }
    }

    fun toggleLike() {
        database.query {
            currentSong.value?.let {
                update(it.song.toggleLike())
            }
        }
    }

    private fun openAudioEffectSession() {
        if (isAudioEffectSessionOpened) return
        isAudioEffectSessionOpened = true
        sendBroadcast(
            Intent(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION).apply {
                putExtra(AudioEffect.EXTRA_AUDIO_SESSION, player.audioSessionId)
                putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)
                putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
            }
        )
    }

    private fun closeAudioEffectSession() {
        if (!isAudioEffectSessionOpened) return
        isAudioEffectSessionOpened = false
        sendBroadcast(
            Intent(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION).apply {
                putExtra(AudioEffect.EXTRA_AUDIO_SESSION, player.audioSessionId)
                putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)
            }
        )
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        if (dataStore.get(AutoLoadMoreKey, true) &&
            reason != Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT &&
            songsLeftInPlayOrder(6) <= 5 &&
            currentQueue.hasNextPage()
        ) {
            scope.launch(SilentHandler) {
                val mediaItems = currentQueue.nextPage().filterExplicit(dataStore.get(HideExplicitKey, false))
                if (player.playbackState != STATE_IDLE) {
                    appendItems(mediaItems)
                }
            }
        }
        maybeAutoPlay()
    }

    /**
     * How many songs are still ahead of the current one *in play order* (up to [limit]).
     * Using the timeline keeps this correct in shuffle mode, where "index + 1" is meaningless.
     */
    private fun songsLeftInPlayOrder(limit: Int): Int {
        val timeline = player.currentTimeline
        if (timeline.isEmpty) return 0
        var index = player.currentMediaItemIndex
        var count = 0
        while (count < limit) {
            val next = timeline.getNextWindowIndex(index, REPEAT_MODE_OFF, player.shuffleModeEnabled)
            if (next == C.INDEX_UNSET) break
            index = next
            count++
        }
        return count
    }

    /** Adds songs to the end of the queue; in shuffle mode they also go to the end of the shuffle order. */
    private fun appendItems(items: List<MediaItem>) {
        if (items.isEmpty()) return
        val oldCount = player.mediaItemCount
        player.addMediaItems(items)
        if (player.shuffleModeEnabled && oldCount > 0) keepNewItemsLastInShuffle(oldCount)
    }

    private fun keepNewItemsLastInShuffle(oldCount: Int) {
        val timeline = player.currentTimeline
        if (timeline.isEmpty) return
        val order = ArrayList<Int>(timeline.windowCount)
        var index = timeline.getFirstWindowIndex(true)
        while (index != C.INDEX_UNSET) {
            order.add(index)
            index = timeline.getNextWindowIndex(index, REPEAT_MODE_OFF, true)
        }
        val reordered = order.filter { it < oldCount } + order.filter { it >= oldCount }
        if (reordered.size == player.mediaItemCount) {
            player.setShuffleOrder(DefaultShuffleOrder(reordered.toIntArray(), System.currentTimeMillis()))
        }
    }

    private suspend fun fetchAutoPlayItems(): List<MediaItem> {
        val seed = player.currentMetadata
        val queueSnapshot = (0 until player.mediaItemCount).map { player.getMediaItemAt(it) }
        val exclude = queueSnapshot.map { it.mediaId }.toSet()
        val recent = queueSnapshot
            .take(player.currentMediaItemIndex + 1)
            .mapNotNull { it.metadata }
            .takeLast(6)
        return AutoPlayRecommender(database).recommend(
            seed = seed,
            exclude = exclude,
            count = 12,
            hideExplicit = dataStore.get(HideExplicitKey, false),
            recentSeeds = recent,
        )
    }

    private fun maybeAutoPlay() {
        if (!dataStore.get(AutoPlayKey, true)) return
        if (queueLoading || autoPlayJob?.isActive == true) return
        if (player.mediaItemCount == 0 || player.playbackState == STATE_IDLE) return
        if (player.repeatMode != REPEAT_MODE_OFF) return
        if (currentQueue.hasNextPage()) return
        // Works in shuffle mode too: refill when two or fewer songs are left in play order.
        if (songsLeftInPlayOrder(3) > 2) return

        autoPlayJob = scope.launch(SilentHandler) {
            val recommended = fetchAutoPlayItems()
            if (recommended.isNotEmpty() && player.playbackState != STATE_IDLE) {
                appendItems(recommended)
            }
        }
    }

    /**
     * The last song finished before autoplay had anything queued (slow/offline fetch, or autoplay
     * was still running). Instead of silently stopping, get songs now and carry on.
     */
    private fun continueAfterQueueEnded() {
        if (!dataStore.get(AutoPlayKey, true)) return
        if (player.repeatMode != REPEAT_MODE_OFF || player.mediaItemCount == 0) return
        if (sleepTimer.pauseWhenSongEnd) {
            sleepTimer.clear()
            return
        }
        scope.launch(SilentHandler) {
            autoPlayJob?.join()
            if (player.playbackState != Player.STATE_ENDED) return@launch
            if (player.hasNextMediaItem()) {
                player.seekToNextMediaItem()
                player.playWhenReady = true
                return@launch
            }
            if (currentQueue.hasNextPage()) {
                val more = currentQueue.nextPage().filterExplicit(dataStore.get(HideExplicitKey, false))
                if (more.isNotEmpty() && player.playbackState == Player.STATE_ENDED) {
                    val first = player.mediaItemCount
                    appendItems(more)
                    player.seekTo(first, 0L)
                    player.playWhenReady = true
                    return@launch
                }
            }
            val recommended = fetchAutoPlayItems()
            if (recommended.isNotEmpty() && player.playbackState == Player.STATE_ENDED) {
                val first = player.mediaItemCount
                appendItems(recommended)
                player.seekTo(first, 0L)
                player.playWhenReady = true
            }
        }
    }

    override fun onPlaybackStateChanged(@Player.State playbackState: Int) {
        when (playbackState) {
            // A playback *error* also lands in IDLE. That must not wipe the queue state or switch
            // shuffle off (it used to, which is why random mode "forgot" itself after a hiccup).
            STATE_IDLE -> if (player.playerError == null) {
                currentQueue = EmptyQueue
                player.shuffleModeEnabled = false
                queueTitle = null
            }

            Player.STATE_READY -> {
                retryCount = 0
                retryMediaId = null
            }

            Player.STATE_ENDED -> continueAfterQueueEnded()
        }
    }

    override fun onEvents(player: Player, events: Player.Events) {
        if (events.containsAny(Player.EVENT_PLAYBACK_STATE_CHANGED, Player.EVENT_PLAY_WHEN_READY_CHANGED)) {
            val isBufferingOrReady = player.playbackState == Player.STATE_BUFFERING || player.playbackState == Player.STATE_READY
            if (isBufferingOrReady && player.playWhenReady) {
                openAudioEffectSession()
            } else {
                closeAudioEffectSession()
            }
        }
        if (events.containsAny(EVENT_TIMELINE_CHANGED, EVENT_POSITION_DISCONTINUITY)) {
            currentMediaMetadata.value = player.currentMetadata
        }
    }

    override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
        updateNotification()
        if (shuffleModeEnabled) {
            val shuffledIndices = IntArray(player.mediaItemCount) { it }
            shuffledIndices.shuffle()
            shuffledIndices[shuffledIndices.indexOf(player.currentMediaItemIndex)] = shuffledIndices[0]
            shuffledIndices[0] = player.currentMediaItemIndex
            player.setShuffleOrder(DefaultShuffleOrder(shuffledIndices, System.currentTimeMillis()))
        }
    }

    override fun onRepeatModeChanged(repeatMode: Int) {
        updateNotification()
        scope.launch {
            dataStore.edit { settings ->
                settings[RepeatModeKey] = repeatMode
            }
        }
    }

    override fun onPlayerError(
        error: PlaybackException
    ) {
        val mediaId = player.currentMediaItem?.mediaId?.takeIf { it.isNotBlank() }

        Log.e("MusicService", "Playback error: ${error.message} code=${error.errorCode}", error)

        if (mediaId != null) {
            val expiredLink = isLikelyStreamHttpError(error)
            val noStream = error.errorCode == ERROR_CODE_NO_STREAM
            if (expiredLink || noStream || isNetworkError(error)) {
                // The stream URL may have expired (typical after the app sat in the background).
                streamUrlCache.remove(mediaId)
                if (scheduleRetry(mediaId, immediate = expiredLink)) return
            }
        }

        if (
            dataStore.get(AutoSkipNextOnErrorKey, false) &&
            isInternetAvailable(this) &&
            player.hasNextMediaItem()
        ) {
            player.seekToNext()
            player.prepare()
            player.playWhenReady = true
        }
    }

    private fun isNetworkError(error: PlaybackException): Boolean = when (error.errorCode) {
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
        PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
        PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE,
        PlaybackException.ERROR_CODE_TIMEOUT -> true
        else -> false
    }

    /**
     * Re-prepares the current song with a growing delay instead of giving up after one try
     * (or hammering the network in a tight loop, as the old NO_STREAM branch could).
     * Returns false once the attempts for this song are used up.
     */
    private fun scheduleRetry(mediaId: String, immediate: Boolean): Boolean {
        if (retryMediaId != mediaId) {
            retryMediaId = mediaId
            retryCount = 0
        }
        if (retryCount >= MAX_PLAYBACK_RETRIES) return false
        val attempt = retryCount++
        retryJob?.cancel()
        retryJob = scope.launch {
            if (!(immediate && attempt == 0)) {
                delay((1_500L shl attempt.coerceAtMost(4)).coerceAtMost(15_000L))
            }
            if (player.currentMediaItem?.mediaId != mediaId) return@launch
            player.prepare()
        }
        return true
    }

    /** When connectivity comes back, resume a song that stopped because the network dropped. */
    private fun registerNetworkCallback() {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                scope.launch {
                    if (player.playerError != null && player.playWhenReady) {
                        retryCount = 0
                        retryJob?.cancel()
                        player.prepare()
                    }
                }
            }
        }
        networkCallback = callback
        runCatching { connectivityManager.registerDefaultNetworkCallback(callback) }
            .onFailure { networkCallback = null }
    }

    private fun isLikelyStreamHttpError(error: PlaybackException): Boolean {
        if (error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS) return true
        var cause: Throwable? = error.cause
        repeat(6) {
            if (cause == null) return@repeat
            if (cause is androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException) return true
            cause = cause?.cause
        }
        return false
    }

    private fun createCacheDataSource(): CacheDataSource.Factory =
        CacheDataSource.Factory()
            .setCache(downloadCache)
            .setUpstreamDataSourceFactory(
                CacheDataSource.Factory()
                    .setCache(playerCache)
                    .setUpstreamDataSourceFactory(
                        DefaultDataSource.Factory(
                            this,
                            OkHttpDataSource.Factory(
                                OkHttpClient.Builder()
                                    .proxy(YouTube.proxy)
                                    .build()
                            )
                        )
                    )
            )
            .setCacheWriteDataSinkFactory(null)
            .setFlags(FLAG_IGNORE_CACHE_ON_ERROR)

    private fun createDataSourceFactory(): DataSource.Factory {
        return ResolvingDataSource.Factory(
            createCacheDataSource()
        ) { dataSpec ->

            val mediaId =
                dataSpec.key
                    ?: error("No media id")

            if (
                downloadCache.isCached(
                    mediaId,
                    dataSpec.position,
                    if (dataSpec.length >= 0) {
                        dataSpec.length
                    } else {
                        1
                    }
                ) ||
                playerCache.isCached(
                    mediaId,
                    dataSpec.position,
                    CHUNK_LENGTH
                )
            ) {
                return@Factory dataSpec
            }

            streamUrlCache[mediaId]
                ?.takeIf {
                    it.isValid()
                }
                ?.let { cached ->

                    return@Factory dataSpec
                        .withUri(
                            cached.url.toUri()
                        )
                        .subrange(
                            dataSpec.uriPositionOffset,
                            CHUNK_LENGTH
                        )
                }

            val playedFormat =
                runBlocking(Dispatchers.IO) {
                    database
                        .format(mediaId)
                        .first()
                }

            val resolved =
                runBlocking(Dispatchers.IO) {
                    StreamResolver.resolve(
                        videoId = mediaId,
                        playedFormat = playedFormat,
                        audioQuality = audioQuality,
                        isMetered =
                            connectivityManager
                                .isActiveNetworkMetered
                    )
                }

            if (resolved == null) {
                throw PlaybackException(
                    getString(
                        R.string.error_no_stream
                    ),
                    null,
                    ERROR_CODE_NO_STREAM
                )
            }

            streamUrlCache[
                mediaId
            ] = resolved

            resolved
                .playerResponse
                ?.let { playerResponse ->

                    val adaptiveFormats =
                        playerResponse
                            .streamingData
                            ?.adaptiveFormats

                    val selectedFormat =
                        if (playedFormat != null) {
                            adaptiveFormats
                                ?.find {
                                    it.itag ==
                                            playedFormat.itag &&

                                            it.isAudio &&
                                            !it.url
                                                .isNullOrBlank()
                                }
                        } else {
                            adaptiveFormats
                                ?.asSequence()
                                ?.filter {
                                    it.isAudio &&
                                            !it.url
                                                .isNullOrBlank()
                                }
                                ?.maxByOrNull {
                                    val bitrate =
                                        it.bitrate.toLong()

                                    val qualityMultiplier =
                                        when (audioQuality) {
                                            AudioQuality.AUTO ->

                                                if (
                                                    connectivityManager
                                                        .isActiveNetworkMetered
                                                ) {
                                                    -1L
                                                } else {
                                                    1L
                                                }

                                            AudioQuality.HIGH ->
                                                1L

                                            AudioQuality.LOW ->
                                                -1L
                                        }

                                    val webmBonus =
                                        if (
                                            it.mimeType
                                                .startsWith(
                                                    "audio/webm"
                                                )
                                        ) {
                                            10_240L
                                        } else {
                                            0L
                                        }

                                    bitrate *
                                            qualityMultiplier +
                                            webmBonus
                                }
                        }

                    selectedFormat?.let { format ->

                        database.query {
                            upsert(
                                FormatEntity(
                                    id = mediaId,

                                    itag = format.itag,

                                    mimeType =
                                        format.mimeType
                                            .split(";")
                                            .first(),

                                    codecs =
                                        format.mimeType
                                            .split("codecs=")
                                            .getOrNull(1)
                                            ?.removeSurrounding(
                                                "\""
                                            )
                                            ?: "",

                                    bitrate =
                                        format.bitrate,

                                    sampleRate =
                                        format.audioSampleRate,

                                    contentLength =
                                        format.contentLength
                                            ?: 0L,

                                    loudnessDb =
                                        playerResponse
                                            .playerConfig
                                            ?.audioConfig
                                            ?.loudnessDb
                                )
                            )
                        }
                    }

                    scope.launch(
                        Dispatchers.IO
                    ) {
                        recoverSong(
                            mediaId,
                            playerResponse
                        )
                    }
                }

            return@Factory dataSpec
                .withUri(
                    resolved.url.toUri()
                )
                .subrange(
                    dataSpec.uriPositionOffset,
                    CHUNK_LENGTH
                )
        }
    }

    private fun createMediaSourceFactory() =
        DefaultMediaSourceFactory(
            createDataSourceFactory(),
            ExtractorsFactory {
                arrayOf(MatroskaExtractor(), FragmentedMp4Extractor())
            }
        )

    private fun createRenderersFactory() =
        object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean,
            ) = DefaultAudioSink.Builder(this@MusicService)
                .setEnableFloatOutput(enableFloatOutput)
                .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                .setAudioProcessorChain(
                    DefaultAudioSink.DefaultAudioProcessorChain(
                        emptyArray(),
                        SilenceSkippingAudioProcessor(2_000_000, 0.01f, 2_000_000, 0, 256),
                        SonicAudioProcessor()
                    )
                )
                .build()
        }

    override fun onPlaybackStatsReady(eventTime: AnalyticsListener.EventTime, playbackStats: PlaybackStats) {
        val mediaItem = eventTime.timeline.getWindow(eventTime.windowIndex, Timeline.Window()).mediaItem
        if (playbackStats.totalPlayTimeMs >= 30000 && !dataStore.get(PauseListenHistoryKey, false)) {
            database.query {
                incrementTotalPlayTime(mediaItem.mediaId, playbackStats.totalPlayTimeMs)
                try {
                    insert(
                        Event(
                            songId = mediaItem.mediaId,
                            timestamp = LocalDateTime.now(),
                            playTime = playbackStats.totalPlayTimeMs
                        )
                    )
                } catch (_: SQLException) {
                }
            }
        }
    }

    private fun saveQueueToDisk() {
        if (player.playbackState == STATE_IDLE) {
            filesDir.resolve(PERSISTENT_QUEUE_FILE).delete()
            return
        }
        val persistQueue = PersistQueue(
            title = queueTitle,
            items = player.mediaItems.mapNotNull { it.metadata },
            mediaItemIndex = player.currentMediaItemIndex,
            position = player.currentPosition
        )
        runCatching {
            filesDir.resolve(PERSISTENT_QUEUE_FILE).outputStream().use { fos ->
                ObjectOutputStream(fos).use { oos ->
                    oos.writeObject(persistQueue)
                }
            }
        }.onFailure {
            reportException(it)
        }
    }

    override fun onDestroy() {
        if (dataStore.get(PersistentQueueKey, true)) {
            saveQueueToDisk()
        }
        retryJob?.cancel()
        networkCallback?.let { runCatching { connectivityManager.unregisterNetworkCallback(it) } }
        networkCallback = null
        widgetUpdater.stop()
        mediaSession.release()
        player.removeListener(this)
        player.removeListener(sleepTimer)
        player.release()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = super.onBind(intent) ?: binder

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Swiping the app out of Recents used to call stopSelf() unconditionally, which killed
        // the music. Keep the foreground service alive while something is playing; only stop
        // when nothing is playing, or when the user chose "stop music when app is cleared".
        val playing = player.playWhenReady &&
            player.playbackState != STATE_IDLE &&
            player.playbackState != Player.STATE_ENDED
        if (dataStore.get(StopMusicOnTaskClearKey, false) || !playing) {
            super.onTaskRemoved(rootIntent)
            stopSelf()
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = mediaSession

    inner class MusicBinder : Binder() {
        val service: MusicService
            get() = this@MusicService
    }

    companion object {
        private const val MAX_PLAYBACK_RETRIES = 5

        const val ROOT = "root"
        const val SONG = "song"
        const val ARTIST = "artist"
        const val ALBUM = "album"
        const val PLAYLIST = "playlist"

        const val CHANNEL_ID = "music_channel_01"
        const val NOTIFICATION_ID = 888
        const val ERROR_CODE_NO_STREAM = 1000001
        const val CHUNK_LENGTH = 512 * 1024L
        const val PERSISTENT_QUEUE_FILE = "persistent_queue.data"
    }
}
