package io.github.seky443.librething.service.model

/** One entry of the play order (`GET /player/queue`). [name]/[artistNames] are empty until the
 * daemon has the metadata cached (it fetches it in the background) or for entries with none. */
data class QueueEntry(
    val uri: String,
    val name: String?,
    val artistNames: List<String>,
    /** "queue", "context" or "autoplay" -- where the entry comes from. */
    val provider: String,
)

/** The play order around the current track: [previous] farthest first, [next] nearest first. */
data class PlayQueue(
    val previous: List<QueueEntry> = emptyList(),
    val current: QueueEntry? = null,
    val next: List<QueueEntry> = emptyList(),
)

/** A device of the account's Spotify Connect cluster (`GET /connect/devices`). */
data class ConnectDevice(
    val id: String,
    val name: String,
    /** Lowercase device type from Spotify, e.g. "computer", "smartphone", "speaker". */
    val type: String,
    /** 0..65535. */
    val volume: Int,
    val isSelf: Boolean,
    val isActive: Boolean,
    val isOffline: Boolean,
    val canTransfer: Boolean,
)

data class ConnectDevices(
    val activeDeviceId: String = "",
    val devices: List<ConnectDevice> = emptyList(),
    /** Whether the cluster's active device is playing (not paused); false when unknown. */
    val isPlaying: Boolean = false,
)
