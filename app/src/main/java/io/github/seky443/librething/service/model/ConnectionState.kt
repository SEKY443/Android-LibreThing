package io.github.seky443.librething.service.model

/** High-level state of the go-librespot daemon, surfaced on the Dashboard's status indicator. */
sealed interface ConnectionState {
    data object Idle : ConnectionState
    data object Starting : ConnectionState
    data object Discoverable : ConnectionState
    data object Playing : ConnectionState
    data object Paused : ConnectionState
    data class Error(val message: String) : ConnectionState
}

enum class LogLevel { DEBUG, INFO, WARN, ERROR }

data class LogEntry(
    val level: LogLevel,
    val message: String,
    val timestampMillis: Long = System.currentTimeMillis(),
)

data class TrackInfo(
    val uri: String,
    val name: String,
    val artistNames: List<String>,
    val albumName: String,
    val albumCoverUrl: String?,
    val positionMs: Long,
    val durationMs: Long,
)

data class PlayerStatus(
    val username: String,
    val deviceName: String,
    val stopped: Boolean,
    val paused: Boolean,
    val buffering: Boolean,
    val volume: Int,
    val volumeSteps: Int,
    val track: TrackInfo?,
)

/** OAuth 2.0 device authorization flow ("device_auth" credentials): the daemon logs this once
 * per login attempt, then blocks waiting for the user to approve on any device using
 * [verificationUri] and [userCode] -- unlike interactive login's browser-OAuth callback, that
 * approval doesn't have to happen on this device, so the code has to stay visible/copyable in
 * the app itself rather than just auto-launching a browser. */
data class DeviceAuthPrompt(
    val verificationUri: String,
    val userCode: String,
)
