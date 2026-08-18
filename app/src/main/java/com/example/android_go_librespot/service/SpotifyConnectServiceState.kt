package com.example.android_go_librespot.service

import com.example.android_go_librespot.service.model.ConnectionState
import com.example.android_go_librespot.service.model.LogEntry
import com.example.android_go_librespot.service.model.TrackInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-wide, service-owned state that the UI observes and issues playback commands
 * through. [SpotifyConnectService] is the sole writer; everything else (ViewModels) only
 * reads the flows and calls the control functions, which are no-ops while the service
 * isn't running. This single-process singleton is simpler than binding to the service for
 * an app with exactly one UI process and one service.
 */
object SpotifyConnectServiceState {
    private const val MAX_LOG_LINES = 1000

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _nowPlaying = MutableStateFlow<TrackInfo?>(null)
    val nowPlaying: StateFlow<TrackInfo?> = _nowPlaying.asStateFlow()

    private val _volume = MutableStateFlow(0 to 65535)
    val volume: StateFlow<Pair<Int, Int>> = _volume.asStateFlow()

    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    private val _isServiceRunning = MutableStateFlow(false)
    val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

    @Volatile private var apiClient: GoLibrespotApiClient? = null

    internal fun attach(client: GoLibrespotApiClient?) {
        apiClient = client
        _isServiceRunning.value = client != null
        if (client == null) {
            _connectionState.value = ConnectionState.Idle
            _nowPlaying.value = null
        }
    }

    internal fun setConnectionState(state: ConnectionState) {
        _connectionState.value = state
    }

    internal fun setNowPlaying(track: TrackInfo?) {
        _nowPlaying.value = track
    }

    /** Updates the current track's position/duration in place, e.g. from a "seek" event that
     * (unlike "metadata") doesn't carry the rest of the track's fields. No-ops if nothing is
     * loaded, which can't happen in practice (seek events imply an active track) but is cheap
     * to make impossible to crash on regardless. */
    internal fun updatePosition(positionMs: Long, durationMs: Long) {
        _nowPlaying.update { it?.copy(positionMs = positionMs, durationMs = durationMs) }
    }

    internal fun setVolume(value: Int, max: Int) {
        _volume.value = value to max
    }

    internal fun appendLog(entry: LogEntry) {
        _logs.update { current ->
            val next = current + entry
            if (next.size > MAX_LOG_LINES) next.subList(next.size - MAX_LOG_LINES, next.size) else next
        }
    }

    internal fun clearLogs() {
        _logs.value = emptyList()
    }

    private inline fun <T> MutableStateFlow<T>.update(transform: (T) -> T) {
        value = transform(value)
    }

    fun playPause() = apiClient?.playPause()
    fun next() = apiClient?.next()
    fun previous() = apiClient?.previous()
    fun setVolumeCommand(value: Int) = apiClient?.setVolume(value)
    fun seek(positionMs: Long) = apiClient?.seek(positionMs)
}
