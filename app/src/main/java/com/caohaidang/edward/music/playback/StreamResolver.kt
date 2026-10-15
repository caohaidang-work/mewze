package com.caohaidang.edward.music.playback

import android.net.Uri
import android.util.Log
import com.caohaidang.edward.innertube.YouTube
import com.caohaidang.edward.innertube.models.response.PlayerResponse
import com.caohaidang.edward.music.constants.AudioQuality
import com.caohaidang.edward.music.db.entities.FormatEntity
import kotlinx.coroutines.CancellationException

data class ResolvedStream(
    val url: String,
    val expiresAt: Long,
    val source: Source,
    val playerResponse: PlayerResponse? = null,
) {
    fun isValid(
        now: Long = System.currentTimeMillis()
    ): Boolean {
        return expiresAt >
                now +
                REFRESH_MARGIN_MS
    }

    enum class Source {
        STREAM_FIXER,
        INNERTUBE,
    }

    companion object {
        const val DEFAULT_STREAM_LIFETIME_MS =
            30 * 60 * 1000L

        const val REFRESH_MARGIN_MS =
            30_000L
    }
}

object StreamResolver {
    private const val TAG =
        "StreamResolver"

    suspend fun resolve(
        videoId: String,
        playedFormat: FormatEntity?,
        audioQuality: AudioQuality,
        isMetered: Boolean,
    ): ResolvedStream? {
        try {
            Log.d(
                TAG,
                "Trying StreamFixer for $videoId"
            )

            val fixedUrl =
                StreamFixer
                    .getAudioStreamUrl(videoId)

            if (!fixedUrl.isNullOrBlank()) {
                Log.d(
                    TAG,
                    "StreamFixer SUCCESS for $videoId"
                )

                return ResolvedStream(
                    url = fixedUrl,
                    expiresAt = extractExpiration(
                        fixedUrl
                    ),
                    source =
                        ResolvedStream.Source
                            .STREAM_FIXER
                )
            }

            Log.w(
                TAG,
                "StreamFixer returned null"
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Log.e(
                TAG,
                "StreamFixer exception",
                e
            )
        }

        try {
            Log.d(
                TAG,
                "Trying original InnerTune resolver"
            )

            val result =
                resolveWithInnerTune(
                    videoId = videoId,
                    playedFormat = playedFormat,
                    audioQuality = audioQuality,
                    isMetered = isMetered
                )

            if (result != null) {
                Log.d(
                    TAG,
                    "Original InnerTune resolver SUCCESS"
                )

                return result
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Log.e(
                TAG,
                "Original InnerTune resolver exception",
                e
            )
        }

        Log.e(
            TAG,
            "No playable stream found for $videoId"
        )

        return null
    }

    private suspend fun resolveWithInnerTune(
        videoId: String,
        playedFormat: FormatEntity?,
        audioQuality: AudioQuality,
        isMetered: Boolean,
    ): ResolvedStream? {
        return try {
            val response =
                YouTube
                    .player(videoId)
                    .getOrNull()

            if (response == null) {
                Log.w(
                    TAG,
                    "YouTube.player returned null"
                )

                return null
            }

            if (
                response
                    .playabilityStatus
                    .status != "OK"
            ) {
                Log.w(
                    TAG,
                    "Playability status = " +
                            response
                                .playabilityStatus
                                .status
                )

                return null
            }

            val adaptiveFormats =
                response
                    .streamingData
                    ?.adaptiveFormats

            if (
                adaptiveFormats.isNullOrEmpty()
            ) {
                Log.w(
                    TAG,
                    "No adaptive audio formats"
                )

                return null
            }

            val exactFormat =
                playedFormat
                    ?.let { stored ->

                        adaptiveFormats.find {
                            it.itag ==
                                    stored.itag &&

                                    it.isAudio &&

                                    !it.url
                                        .isNullOrBlank()
                        }
                    }

            val selectedFormat =
                exactFormat
                    ?: adaptiveFormats
                        .asSequence()
                        .filter {
                            it.isAudio &&
                                    !it.url
                                        .isNullOrBlank()
                        }
                        .maxByOrNull {
                            val bitrate =
                                it.bitrate
                                    .toLong()

                            val qualityMultiplier =
                                when (audioQuality) {
                                    AudioQuality.HIGH ->
                                        1L

                                    AudioQuality.LOW ->
                                        -1L

                                    AudioQuality.AUTO ->
                                        if (isMetered) {
                                            -1L
                                        } else {
                                            1L
                                        }
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

            if (selectedFormat == null) {
                Log.w(
                    TAG,
                    "No direct playable audio format"
                )

                return null
            }

            val url =
                selectedFormat.url

            if (url.isNullOrBlank()) {
                Log.w(
                    TAG,
                    "Selected format has no URL"
                )

                return null
            }

            val expiresInSeconds =
                response
                    .streamingData
                    ?.expiresInSeconds
                    ?.toLong()
                    ?: 1800L

            val expiresAt =
                System.currentTimeMillis() +
                        expiresInSeconds * 1000L

            Log.d(
                TAG,
                "InnerTune selected itag=" +
                        selectedFormat.itag +
                        " bitrate=" +
                        selectedFormat.bitrate
            )

            ResolvedStream(
                url = url,
                expiresAt = expiresAt,
                source =
                    ResolvedStream.Source
                        .INNERTUBE,
                playerResponse = response
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Log.e(
                TAG,
                "resolveWithInnerTune failed",
                e
            )

            null
        }
    }

    private fun extractExpiration(
        url: String
    ): Long {
        val expireSeconds =
            runCatching {
                Uri.parse(url)
                    .getQueryParameter("expire")
                    ?.toLongOrNull()
            }.getOrNull()

        return if (
            expireSeconds != null &&
            expireSeconds > 0L
        ) {
            expireSeconds * 1000L
        } else {
            System.currentTimeMillis() +
                    ResolvedStream
                        .DEFAULT_STREAM_LIFETIME_MS
        }
    }
}
