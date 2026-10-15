package com.caohaidang.edward.music.playback

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request as NpRequest
import org.schabi.newpipe.extractor.downloader.Response as NpResponse
import org.schabi.newpipe.extractor.localization.Localization
import java.util.concurrent.TimeUnit

private class OkHttpNewPipeDownloader : Downloader() {
    private val client =
        OkHttpClient.Builder()
            .connectTimeout(
                15,
                TimeUnit.SECONDS
            )
            .readTimeout(
                20,
                TimeUnit.SECONDS
            )
            .writeTimeout(
                15,
                TimeUnit.SECONDS
            )
            .callTimeout(
                30,
                TimeUnit.SECONDS
            )
            .build()

    override fun execute(
        request: NpRequest
    ): NpResponse {
        val builder =
            Request.Builder()
                .url(request.url())

        request.headers()
            .forEach { (key, values) ->

                values.forEach { value ->

                    builder.addHeader(
                        key,
                        value
                    )
                }
            }

        if (
            request.headers()["User-Agent"] == null
        ) {
            builder.header(
                "User-Agent",
                StreamFixer.USER_AGENT
            )
        }

        builder.header(
            "Cookie",
            "CONSENT=YES+cb.20210328-17-p0.en+FX+410"
        )

        val bodyBytes =
            request.dataToSend()

        val requestBody =
            if (bodyBytes != null) {
                bodyBytes.toRequestBody(null)
            } else if (
                request.httpMethod() == "POST"
            ) {
                ByteArray(0)
                    .toRequestBody(null)
            } else {
                null
            }

        builder.method(
            request.httpMethod(),
            requestBody
        )

        client
            .newCall(builder.build())
            .execute()
            .use { response ->

                val responseBody =
                    response.body?.string()
                        ?: ""

                return NpResponse(
                    response.code,
                    response.message,
                    response.headers.toMultimap(),
                    responseBody,
                    response.request.url.toString()
                )
            }
    }
}

object StreamFixer {
    private const val TAG =
        "StreamFixer"

    const val USER_AGENT =
        "com.google.ios.youtube/19.45.4 " +
                "(iPhone16,2; U; CPU iOS 18_1_0 like Mac OS X;)"

    private val json =
        Json {
            ignoreUnknownKeys = true
        }

    private val httpClient =
        HttpClient(OkHttp)

    @Volatile
    private var extractorInitialized =
        false

    @Synchronized
    private fun ensureExtractorInitialized() {
        if (extractorInitialized) {
            return
        }

        Log.d(
            TAG,
            "Initializing NewPipe..."
        )

        NewPipe.init(
            OkHttpNewPipeDownloader(),
            Localization(
                "vi",
                "VN"
            )
        )

        extractorInitialized =
            true

        Log.d(
            TAG,
            "NewPipe initialized successfully"
        )
    }

    suspend fun getAudioStreamUrl(
        videoId: String
    ): String? = withContext(
        Dispatchers.IO
    ) {
        Log.d(
            TAG,
            "================================"
        )

        Log.d(
            TAG,
            "Resolving videoId=$videoId"
        )

        Log.d(
            TAG,
            "================================"
        )

        try {
            Log.d(
                TAG,
                "========== NEWPIPE START =========="
            )

            ensureExtractorInitialized()

            val youtubeUrl =
                "https://www.youtube.com/watch?v=$videoId"

            Log.d(
                TAG,
                "NewPipe URL=$youtubeUrl"
            )

            val extractor =
                ServiceList.YouTube
                    .getStreamExtractor(
                        youtubeUrl
                    )

            Log.d(
                TAG,
                "NewPipe extractor created"
            )

            extractor.fetchPage()

            Log.d(
                TAG,
                "NewPipe fetchPage SUCCESS"
            )

            val streams =
                extractor.audioStreams

            Log.d(
                TAG,
                "NewPipe audio stream count=" +
                        (streams?.size ?: 0)
            )

            if (
                streams.isNullOrEmpty()
            ) {
                Log.e(
                    TAG,
                    "NewPipe returned ZERO audio streams"
                )
            } else {
                streams.forEachIndexed {
                        index,
                        stream ->

                    Log.d(
                        TAG,
                        "NewPipe stream[$index]: " +
                                "bitrate=" +
                                stream.averageBitrate +
                                ", format=" +
                                stream.format +
                                ", urlExists=" +
                                !stream.content
                                    .isNullOrBlank()
                    )
                }

                val bestStream =
                    streams
                        .asSequence()
                        .filter {
                            !it.content
                                .isNullOrBlank()
                        }
                        .maxByOrNull {
                            it.averageBitrate
                        }

                val url =
                    bestStream?.content

                if (
                    !url.isNullOrBlank()
                ) {
                    Log.d(
                        TAG,
                        "========== NEWPIPE SUCCESS =========="
                    )

                    Log.d(
                        TAG,
                        "NewPipe URL length=" +
                                url.length
                    )

                    return@withContext url
                }

                Log.e(
                    TAG,
                    "NewPipe streams exist " +
                            "but no usable URL was found"
                )
            }
        } catch (
            e: CancellationException
        ) {
            throw e
        } catch (e: Throwable) {
            Log.e(
                TAG,
                "========== NEWPIPE FAILED ==========",
                e
            )
        }

        try {
            Log.d(
                TAG,
                "========== IOS INNERTUBE START =========="
            )

            val url =
                fetchInnertubeStream(
                    videoId =
                        videoId,

                    clientName =
                        "IOS",

                    clientVersion =
                        "19.45.4",

                    userAgent =
                        USER_AGENT
                ) {
                    put(
                        "deviceMake",
                        "Apple"
                    )

                    put(
                        "deviceModel",
                        "iPhone16,2"
                    )

                    put(
                        "osName",
                        "iPhone"
                    )

                    put(
                        "osVersion",
                        "18.1.0.22B83"
                    )
                }

            if (
                !url.isNullOrBlank()
            ) {
                Log.d(
                    TAG,
                    "========== IOS INNERTUBE SUCCESS =========="
                )

                return@withContext url
            }

            Log.w(
                TAG,
                "iOS InnerTube returned no URL"
            )
        } catch (
            e: CancellationException
        ) {
            throw e
        } catch (e: Throwable) {
            Log.e(
                TAG,
                "========== IOS INNERTUBE FAILED ==========",
                e
            )
        }

        try {
            Log.d(
                TAG,
                "========== ANDROID VR START =========="
            )

            val url =
                fetchInnertubeStream(
                    videoId =
                        videoId,

                    clientName =
                        "ANDROID_VR",

                    clientVersion =
                        "1.60.19",

                    userAgent =
                        "com.google.android.apps.youtube.vr.oculus/1.60.19 " +
                                "(Linux; U; Android 14; en_US; Quest 3; " +
                                "Build/UP1A.231005.007.A1; Cronet/132.0.6808.3)"
                ) {
                    put(
                        "androidSdkVersion",
                        34
                    )

                    put(
                        "osName",
                        "Android"
                    )

                    put(
                        "osVersion",
                        "14"
                    )
                }

            if (
                !url.isNullOrBlank()
            ) {
                Log.d(
                    TAG,
                    "========== ANDROID VR SUCCESS =========="
                )

                return@withContext url
            }

            Log.w(
                TAG,
                "Android VR returned no URL"
            )
        } catch (
            e: CancellationException
        ) {
            throw e
        } catch (e: Throwable) {
            Log.e(
                TAG,
                "========== ANDROID VR FAILED ==========",
                e
            )
        }

        Log.e(
            TAG,
            "================================"
        )

        Log.e(
            TAG,
            "ALL STREAM RESOLVERS FAILED"
        )

        Log.e(
            TAG,
            "videoId=$videoId"
        )

        Log.e(
            TAG,
            "================================"
        )

        null
    }

    private suspend fun fetchInnertubeStream(
        videoId: String,
        clientName: String,
        clientVersion: String,
        userAgent: String,
        extraClientFields:
        JsonObjectBuilder.() -> Unit
    ): String? {
        return try {
            val body =
                buildJsonObject {
                    putJsonObject(
                        "context"
                    ) {
                        putJsonObject(
                            "client"
                        ) {
                            put(
                                "clientName",
                                clientName
                            )

                            put(
                                "clientVersion",
                                clientVersion
                            )

                            put(
                                "hl",
                                "vi"
                            )

                            put(
                                "gl",
                                "VN"
                            )

                            extraClientFields()
                        }
                    }

                    put(
                        "videoId",
                        videoId
                    )

                    put(
                        "contentCheckOk",
                        true
                    )

                    put(
                        "racyCheckOk",
                        true
                    )
                }

            Log.d(
                TAG,
                "Sending InnerTube request: " +
                        "client=$clientName " +
                        "version=$clientVersion"
            )

            val response: HttpResponse =
                httpClient.post(
                    "https://www.youtube.com/youtubei/v1/player" +
                            "?prettyPrint=false"
                ) {
                    contentType(
                        ContentType.Application.Json
                    )

                    header(
                        "User-Agent",
                        userAgent
                    )

                    header(
                        "X-YouTube-Client-Name",
                        when (clientName) {
                            "IOS" ->
                                "5"

                            "ANDROID_VR" ->
                                "28"

                            else ->
                                "3"
                        }
                    )

                    header(
                        "X-YouTube-Client-Version",
                        clientVersion
                    )

                    setBody(
                        body.toString()
                    )
                }

            Log.d(
                TAG,
                "InnerTube HTTP status=" +
                        response.status.value
            )

            if (
                response.status.value !in 200..299
            ) {
                Log.e(
                    TAG,
                    "InnerTube HTTP ERROR " +
                            response.status.value
                )

                return null
            }

            val responseText =
                response.bodyAsText()

            if (
                responseText.isBlank()
            ) {
                Log.e(
                    TAG,
                    "InnerTube returned empty response"
                )

                return null
            }

            val root =
                json
                    .parseToJsonElement(
                        responseText
                    )
                    .jsonObject

            val playabilityStatus =
                root[
                    "playabilityStatus"
                ]
                    ?.jsonObject

            val status =
                playabilityStatus
                    ?.get("status")
                    ?.jsonPrimitive
                    ?.contentOrNull

            val reason =
                playabilityStatus
                    ?.get("reason")
                    ?.jsonPrimitive
                    ?.contentOrNull

            Log.d(
                TAG,
                "InnerTube playabilityStatus=$status"
            )

            if (
                !reason.isNullOrBlank()
            ) {
                Log.d(
                    TAG,
                    "InnerTube reason=$reason"
                )
            }

            if (
                status != "OK"
            ) {
                Log.w(
                    TAG,
                    "InnerTube playback unavailable"
                )

                return null
            }

            val streamingData =
                root[
                    "streamingData"
                ]
                    ?.jsonObject

            if (
                streamingData == null
            ) {
                Log.w(
                    TAG,
                    "InnerTube has no streamingData"
                )

                return null
            }

            val formats =
                streamingData[
                    "adaptiveFormats"
                ]
                    ?.jsonArray

            if (
                formats == null ||
                formats.isEmpty()
            ) {
                Log.w(
                    TAG,
                    "InnerTube has no adaptiveFormats"
                )

                return null
            }

            Log.d(
                TAG,
                "InnerTube adaptiveFormats=" +
                        formats.size
            )

            val candidates =
                formats
                    .asSequence()
                    .map {
                        it.jsonObject
                    }
                    .filter { format ->

                        val mimeType =
                            format[
                                "mimeType"
                            ]
                                ?.jsonPrimitive
                                ?.contentOrNull

                        val url =
                            format[
                                "url"
                            ]
                                ?.jsonPrimitive
                                ?.contentOrNull

                        mimeType
                            ?.startsWith(
                                "audio/"
                            ) == true &&

                                !url
                                    .isNullOrBlank()
                    }
                    .sortedByDescending { format ->

                        format[
                            "bitrate"
                        ]
                            ?.jsonPrimitive
                            ?.intOrNull
                            ?: 0
                    }
                    .toList()

            Log.d(
                TAG,
                "InnerTube direct audio candidates=" +
                        candidates.size
            )

            if (
                candidates.isEmpty()
            ) {
                Log.w(
                    TAG,
                    "No direct audio URL found"
                )

                return null
            }

            val selected =
                candidates.first()

            val mimeType =
                selected[
                    "mimeType"
                ]
                    ?.jsonPrimitive
                    ?.contentOrNull

            val bitrate =
                selected[
                    "bitrate"
                ]
                    ?.jsonPrimitive
                    ?.intOrNull

            val itag =
                selected[
                    "itag"
                ]
                    ?.jsonPrimitive
                    ?.intOrNull

            val url =
                selected[
                    "url"
                ]
                    ?.jsonPrimitive
                    ?.contentOrNull

            Log.d(
                TAG,
                "Selected InnerTube format: " +
                        "itag=$itag " +
                        "mimeType=$mimeType " +
                        "bitrate=$bitrate"
            )

            if (
                url.isNullOrBlank()
            ) {
                Log.w(
                    TAG,
                    "Selected format URL is empty"
                )

                return null
            }

            url
        } catch (
            e: CancellationException
        ) {
            throw e
        } catch (e: Throwable) {
            Log.e(
                TAG,
                "fetchInnertubeStream failed",
                e
            )

            null
        }
    }
}
