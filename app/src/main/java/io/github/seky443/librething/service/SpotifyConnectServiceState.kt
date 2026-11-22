package io.github.seky443.librething.service

import io.github.seky443.librething.service.model.ConnectDevices
import io.github.seky443.librething.service.model.ConnectionState
import io.github.seky443.librething.service.model.PlayQueue
import io.github.seky443.librething.service.model.DeviceAuthPrompt
import io.github.seky443.librething.service.model.LogEntry
import io.github.seky443.librething.service.model.TrackInfo
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

    /**
     * A hardware/Bluetooth volume-key press's immediate effect on `STREAM_MUSIC`, as a
     * 0f..1f fraction -- set by [DeviceVolumeBridge] the instant the key press's broadcast
     * arrives, well before its debounced push to the daemon (and that push's own network
     * round trip) completes. The UI prefers this over [volume] whenever it's non-null, so a
     * key press feels instant instead of waiting on the daemon to confirm; null once the
     * daemon's own reported [volume] has caught up (or after a timeout), reverting display to
     * the normal (animated) daemon-driven value -- see [DeviceVolumeBridge].
     */
    private val _localDeviceVolumeFraction = MutableStateFlow<Float?>(null)
    val localDeviceVolumeFraction: StateFlow<Float?> = _localDeviceVolumeFraction.asStateFlow()

    internal fun setLocalDeviceVolumeFraction(fraction: Float?) {
        _localDeviceVolumeFraction.value = fraction
    }

    private val _queue = MutableStateFlow<PlayQueue?>(null)

    /** The play order, kept current only while something is watching it (see [queueObserved]);
     * null until first fetched, and again whenever the daemon goes away. */
    val queue: StateFlow<PlayQueue?> = _queue.asStateFlow()

    /** True while the queue sheet is open: the daemon raises a `queue` event on every change to
     * the play order, and re-fetching it when nobody's looking would be wasted work. */
    @Volatile internal var queueObserved = false

    internal fun setQueue(queue: PlayQueue?) {
        _queue.value = queue
    }

    private val _connectDevices = MutableStateFlow<ConnectDevices?>(null)

    /** The account's Connect cluster as last reported by the daemon, null while unknown. */
    val connectDevices: StateFlow<ConnectDevices?> = _connectDevices.asStateFlow()

    internal fun setConnectDevices(devices: ConnectDevices?) {
        _connectDevices.value = devices
    }

    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    /** Non-null while a "device_auth" login is waiting on the user to approve elsewhere -- see
     * [DeviceAuthPrompt]. Cleared once the daemon reports real status (login succeeded), on a
     * daemon exit/restart (a fresh attempt gets a fresh code), and when the service stops. */
    private val _deviceAuthPrompt = MutableStateFlow<DeviceAuthPrompt?>(null)
    val deviceAuthPrompt: StateFlow<DeviceAuthPrompt?> = _deviceAuthPrompt.asStateFlow()

    internal fun setDeviceAuthPrompt(prompt: DeviceAuthPrompt?) {
        _deviceAuthPrompt.value = prompt
    }

    private val _isServiceRunning = MutableStateFlow(false)
    val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

    @Volatile private var apiClient: GoLibrespotApiClient? = null

    /** Set by [DeviceVolumeBridge] while it's running; lets [setVolumeFromUi] mirror an
     * app-originated volume change onto `STREAM_MUSIC` without this object needing a
     * [android.content.Context] of its own. Null while no [SpotifyConnectService] instance is
     * alive, in which case [setVolumeFromUi] just skips the mirror -- there's no stream to sync
     * to anyway. */
    @Volatile internal var localVolumeSync: ((value: Int, max: Int) -> Unit)? = null

    internal fun attach(client: GoLibrespotApiClient?) {
        apiClient = client
        _isServiceRunning.value = client != null
        if (client == null) {
            _connectionState.value = ConnectionState.Idle
            _nowPlaying.value = null
            _deviceAuthPrompt.value = null
            _queue.value = null
            _connectDevices.value = null
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
    fun resume() = apiClient?.resume()
    fun pause() = apiClient?.pause()
    fun next() = apiClient?.next()
    /** Whether Previous may rewind a track that's played for a few seconds instead of always going
     * back a track; kept in sync with the "Previous always skips back" setting by the service. */
    @Volatile internal var previousAllowsSeeking = true

    fun previous() = apiClient?.previous(allowSeeking = previousAllowsSeeking)
    fun setVolumeCommand(value: Int) = apiClient?.setVolume(value)

    /**
     * Same as [setVolumeCommand], but also mirrors the change onto the device's own
     * `STREAM_MUSIC` volume -- for a change that originates from this app's own on-screen
     * slider. [DeviceVolumeBridge] already syncs the other direction (hardware/Bluetooth key
     * presses -> remote), but deliberately never mirrors a *remote*-driven change (another
     * Spotify Connect client, or the daemon's own reported volume) back onto `STREAM_MUSIC` --
     * see its kdoc for why (rounding-induced audible correction jumps on a round trip). This
     * app's own slider isn't a round trip, though: it's the same local user gesture, so mirroring
     * it here immediately has no such delay to introduce a jump. Without this, dragging the
     * in-app slider never touched `STREAM_MUSIC` at all, so it silently drifted out of sync with
     * whatever the system volume UI (or another app sharing `STREAM_MUSIC`) showed.
     */
    fun setVolumeFromUi(value: Int, max: Int) {
        apiClient?.setVolume(value)
        localVolumeSync?.invoke(value, max)
    }

    fun seek(positionMs: Long) = apiClient?.seek(positionMs)

    // Blocking like the rest of these -- call off the main thread.
    fun fetchQueue(): PlayQueue? = apiClient?.getQueue()
    fun fetchConnectDevices(): ConnectDevices? = apiClient?.getConnectDevices()
    fun transferTo(deviceId: String, paused: Boolean = false): Boolean = apiClient?.connectTransfer(deviceId, paused) ?: false
    fun commandDevice(deviceId: String, endpoint: String): Boolean = apiClient?.connectCommand(deviceId, endpoint) ?: false
    fun setDeviceVolume(deviceId: String, volume: Int): Boolean = apiClient?.connectSetVolume(deviceId, volume) ?: false
}
