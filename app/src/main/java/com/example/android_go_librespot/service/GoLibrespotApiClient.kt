package com.example.android_go_librespot.service

import com.example.android_go_librespot.service.model.LogEntry
import com.example.android_go_librespot.service.model.LogLevel
import com.example.android_go_librespot.service.model.PlayerStatus
import com.example.android_go_librespot.service.model.TrackInfo
import com.example.android_go_librespot.util.GoLibrespotPaths
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.decodeFromJsonElement
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.io.IOException
import java.util.concurrent.TimeUnit

private val JSON_MEDIA_TYPE = "application/json".toMediaType()

/** Player events pushed over the daemon's `/events` WebSocket, see API.md. */
sealed interface PlayerEvent {
    data object Active : PlayerEvent
    data object Inactive : PlayerEvent
    data class Metadata(val track: TrackInfo) : PlayerEvent
    data object Playing : PlayerEvent
    data object Paused : PlayerEvent
    data object Stopped : PlayerEvent
    data class Volume(val value: Int, val max: Int) : PlayerEvent
}

/**
 * REST + WebSocket client for go-librespot's local API server (`server.enabled: true` in
 * [GoLibrespotConfigWriter]), bound to loopback only. See API.md / api-spec.yml in
 * SEKY443/go-librespot-termux for the wire format.
 *
 * [getStatus] and the player command methods ([resume], [pause], [playPause], [next],
 * [previous], [setVolume]) all block on a synchronous OkHttp call -- callers must not invoke
 * them from the main thread (Android throws `NetworkOnMainThreadException` for main-thread
 * network I/O regardless of the destination being loopback, and that's a `RuntimeException`,
 * not the `IOException` these methods catch internally).
 */
class GoLibrespotApiClient(
    private val onEvent: (PlayerEvent) -> Unit,
    private val onLog: (LogEntry) -> Unit,
) {
    private val baseHttpUrl = "http://${GoLibrespotPaths.API_HOST}:${GoLibrespotPaths.API_PORT}"
    private val json = Json { ignoreUnknownKeys = true }
    private val emptyJsonBody = "{}".toRequestBody(JSON_MEDIA_TYPE)

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(2, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // needed to keep the /events socket open indefinitely
        .callTimeout(5, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null
    @Volatile private var wantConnected = false

    fun connectEvents(scope: CoroutineScope) {
        wantConnected = true
        openSocket(scope)
    }

    fun disconnectEvents() {
        wantConnected = false
        webSocket?.close(1000, "service stopped")
        webSocket = null
    }

    private fun openSocket(scope: CoroutineScope) {
        val request = Request.Builder().url("$baseHttpUrl/events").build()
        webSocket = httpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                handleEventMessage(text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                onLog(LogEntry(LogLevel.WARN, "Events socket disconnected: ${t.message}"))
                scheduleReconnect(scope)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (wantConnected) scheduleReconnect(scope)
            }
        })
    }

    private fun scheduleReconnect(scope: CoroutineScope) {
        if (!wantConnected) return
        scope.launch(Dispatchers.IO) {
            kotlinx.coroutines.delay(1500)
            if (wantConnected) openSocket(scope)
        }
    }

    private fun handleEventMessage(text: String) {
        val envelope = runCatching { json.decodeFromString<WsEnvelope>(text) }.getOrNull() ?: return
        val event = when (envelope.type) {
            "active" -> PlayerEvent.Active
            "inactive" -> PlayerEvent.Inactive
            "playing" -> PlayerEvent.Playing
            "paused" -> PlayerEvent.Paused
            "stopped" -> PlayerEvent.Stopped
            "not_playing" -> PlayerEvent.Stopped
            "metadata" -> runCatching {
                val dto = json.decodeFromJsonElement<MetadataDto>(envelope.data)
                PlayerEvent.Metadata(dto.toTrackInfo())
            }.getOrNull()
            "volume" -> runCatching {
                val dto = json.decodeFromJsonElement<VolumeDto>(envelope.data)
                PlayerEvent.Volume(dto.value, dto.max)
            }.getOrNull()
            else -> null
        } ?: return
        onEvent(event)
    }

    /** Returns null both when there's no active session (204) and when the daemon's API
     * server isn't up yet -- callers poll this while the process is still starting. */
    fun getStatus(): PlayerStatus? {
        val request = Request.Builder().url("$baseHttpUrl/status").get().build()
        return try {
            httpClient.newCall(request).execute().use { response ->
                if (response.code == 204 || !response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                val dto = runCatching { json.decodeFromString<StatusDto>(body) }.getOrNull() ?: return null
                dto.toPlayerStatus()
            }
        } catch (e: IOException) {
            null
        }
    }

    fun resume() = post("/player/resume")
    fun pause() = post("/player/pause")
    fun playPause() = post("/player/playpause")
    fun next() = post("/player/next")
    fun previous() = post("/player/prev")
    fun setVolume(value: Int) = post("/player/volume", """{"volume":$value}""")

    private fun post(path: String, jsonBody: String? = null) {
        val body = jsonBody?.toRequestBody(JSON_MEDIA_TYPE) ?: emptyJsonBody
        val request = Request.Builder().url("$baseHttpUrl$path").post(body).build()
        try {
            httpClient.newCall(request).execute().close()
        } catch (e: IOException) {
            onLog(LogEntry(LogLevel.WARN, "API call to $path failed: ${e.message}"))
        }
    }

    fun shutdown() {
        disconnectEvents()
        httpClient.dispatcher.executorService.shutdown()
        httpClient.connectionPool.evictAll()
    }
}

@Serializable
private data class WsEnvelope(val type: String, val data: JsonElement = JsonNull)

@Serializable
private data class MetadataDto(
    val uri: String = "",
    val name: String = "",
    val artist_names: List<String> = emptyList(),
    val album_name: String = "",
    val album_cover_url: String? = null,
    val position: Long = 0,
    val duration: Long = 0,
) {
    fun toTrackInfo() = TrackInfo(
        uri = uri,
        name = name,
        artistNames = artist_names,
        albumName = album_name,
        albumCoverUrl = album_cover_url,
        positionMs = position,
        durationMs = duration,
    )
}

@Serializable
private data class VolumeDto(val value: Int = 0, val max: Int = 65535)

@Serializable
private data class ApiTrackDto(
    val uri: String = "",
    val name: String = "",
    val artist_names: List<String> = emptyList(),
    val album_name: String = "",
    val album_cover_url: String? = null,
    val position: Long = 0,
    val duration: Long = 0,
)

@Serializable
private data class StatusDto(
    val username: String = "",
    val device_name: String = "",
    val stopped: Boolean = true,
    val paused: Boolean = false,
    val buffering: Boolean = false,
    val volume: Int = 0,
    val volume_steps: Int = 65535,
    val track: ApiTrackDto? = null,
) {
    fun toPlayerStatus() = PlayerStatus(
        username = username,
        deviceName = device_name,
        stopped = stopped,
        paused = paused,
        buffering = buffering,
        volume = volume,
        volumeSteps = volume_steps,
        track = track?.let {
            TrackInfo(
                uri = it.uri,
                name = it.name,
                artistNames = it.artist_names,
                albumName = it.album_name,
                albumCoverUrl = it.album_cover_url,
                positionMs = it.position,
                durationMs = it.duration,
            )
        },
    )
}
