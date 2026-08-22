package io.github.seky443.librething.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.SystemClock
import androidx.core.content.ContextCompat
import io.github.seky443.librething.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * One-directional bridge from the device's `STREAM_MUSIC` volume to the daemon's reported
 * Spotify Connect volume (0..[io.github.seky443.librething.service.model.PlayerStatus.volumeSteps],
 * reported via [SpotifyConnectServiceState.volume]): a connected Bluetooth speaker's hardware
 * volume controls (routed by Android to `STREAM_MUSIC` via AVRCP) move the in-app volume too.
 *
 * Deliberately *not* bidirectional -- remote-driven changes (this app's own slider, or another
 * Spotify Connect client) used to be mirrored back onto `STREAM_MUSIC` as well, but
 * `STREAM_MUSIC` only has a handful of discrete steps (commonly ~15) against the daemon's own
 * 100, so converting a precise remote value down to the nearest device step and writing it
 * introduced a small but audible correction shortly after every remote-driven change -- and on a
 * Bluetooth speaker, where `STREAM_MUSIC` drives the actual analog gain via AVRCP, that rounding
 * step is a real, physical volume jump, not just an internal recalculation. Dropping that
 * direction trades away a speaker's own volume display/knob position tracking remote-driven
 * changes (it now only moves from its own physical buttons) for eliminating that jump entirely.
 *
 * Device-side changes are debounced: holding a hardware/Bluetooth volume key fires a burst of
 * `STREAM_MUSIC` broadcasts (one per step), and posting straight through -- one blocking HTTP
 * call per step -- was visibly janky. Only the value left after a short quiet period is actually
 * sent.
 *
 * Also persists the daemon's reported volume (as a 0f..1f fraction, portable across whatever
 * `volume_steps` is configured) via [settingsRepository], fed back in as `initial_volume` on the
 * next launch (with `ignore_last_volume: true`, so this is the one source of truth instead of
 * competing with the daemon's own separately-persisted last volume) -- see
 * [GoLibrespotConfigWriter]'s `initial_volume`/`ignore_last_volume` comments.
 */
internal class DeviceVolumeBridge(
    private val context: Context,
    private val scope: CoroutineScope,
    private val settingsRepository: SettingsRepository,
) {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private var remoteObserverJob: Job? = null
    private var deviceDebounceJob: Job? = null
    private var localOverrideTimeoutJob: Job? = null
    private var receiver: BroadcastReceiver? = null

    /** Latest STREAM_MUSIC value not yet pushed to the daemon; null means nothing pending. */
    private val pendingDeviceVolume = MutableStateFlow<Int?>(null)

    fun start() {
        if (audioManager == null) return

        // Off the main thread: AudioManager calls are Binder round-trips.
        remoteObserverJob = scope.launch(Dispatchers.Default) {
            SpotifyConnectServiceState.volume.collect { (value, max) ->
                if (max > 0) {
                    withContext(Dispatchers.IO) { settingsRepository.setLastVolumeFraction(value.toFloat() / max) }
                    // The daemon has caught up to what the instant local-feedback override
                    // already showed (see the receiver below) -- clear it so display reverts
                    // to the normal, animated daemon-driven value for whatever comes next.
                    val localFraction = SpotifyConnectServiceState.localDeviceVolumeFraction.value
                    if (localFraction != null && abs(value.toFloat() / max - localFraction) < CONVERGED_THRESHOLD) {
                        SpotifyConnectServiceState.setLocalDeviceVolumeFraction(null)
                    }
                }
            }
        }

        deviceDebounceJob = scope.launch(Dispatchers.Default) {
            pendingDeviceVolume.collectLatest { value ->
                if (value == null) return@collectLatest
                delay(DEBOUNCE_MS)
                withContext(Dispatchers.IO) { applyToRemote(value) }
            }
        }

        val filter = IntentFilter(VOLUME_CHANGED_ACTION)
        val streamReceiver = object : BroadcastReceiver() {
            override fun onReceive(receivedContext: Context, intent: Intent) {
                if (intent.getIntExtra(EXTRA_VOLUME_STREAM_TYPE, -1) != AudioManager.STREAM_MUSIC) return
                val deviceValue = intent.getIntExtra(EXTRA_VOLUME_STREAM_VALUE, -1)
                if (deviceValue < 0) return
                // Instant local feedback: shown right away, well before the debounced push
                // below (and that push's network round trip to the daemon) ever completes.
                val deviceMax = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                if (deviceMax > 0) {
                    SpotifyConnectServiceState.setLocalDeviceVolumeFraction(deviceValue.toFloat() / deviceMax)
                }
                // Safety net: if the daemon round trip never converges close enough to clear
                // it above (e.g. daemon unreachable, or its volume_steps rounds differently),
                // don't leave the UI stuck showing a stale instant value forever.
                localOverrideTimeoutJob?.cancel()
                localOverrideTimeoutJob = scope.launch(Dispatchers.Default) {
                    delay(LOCAL_OVERRIDE_TIMEOUT_MS)
                    SpotifyConnectServiceState.setLocalDeviceVolumeFraction(null)
                }
                pendingDeviceVolume.value = deviceValue
            }
        }
        receiver = streamReceiver
        ContextCompat.registerReceiver(context, streamReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    fun stop() {
        remoteObserverJob?.cancel()
        remoteObserverJob = null
        deviceDebounceJob?.cancel()
        deviceDebounceJob = null
        localOverrideTimeoutJob?.cancel()
        localOverrideTimeoutJob = null
        SpotifyConnectServiceState.setLocalDeviceVolumeFraction(null)
        receiver?.let { context.unregisterReceiver(it) }
        receiver = null
    }

    private fun applyToRemote(deviceValue: Int) {
        val manager = audioManager ?: return
        val deviceMax = manager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        if (deviceMax <= 0) return
        val (currentRemote, remoteMax) = SpotifyConnectServiceState.volume.value
        if (remoteMax <= 0) return
        val target = ((deviceValue.toDouble() / deviceMax) * remoteMax).roundToInt().coerceIn(0, remoteMax)
        if (target == currentRemote) return
        SpotifyConnectServiceState.setVolumeCommand(target)
    }

    private companion object {
        const val DEBOUNCE_MS = 200L
        const val CONVERGED_THRESHOLD = 0.02f
        const val LOCAL_OVERRIDE_TIMEOUT_MS = 3000L

        // No public AudioManager constant exists for this pre-MediaSession, protected system
        // broadcast; it's the long-standing stable way apps observe stream volume changes made
        // outside their own process (hardware keys, other apps, AVRCP from a paired speaker).
        const val VOLUME_CHANGED_ACTION = "android.media.VOLUME_CHANGED_ACTION"
        const val EXTRA_VOLUME_STREAM_TYPE = "android.media.EXTRA_VOLUME_STREAM_TYPE"
        const val EXTRA_VOLUME_STREAM_VALUE = "android.media.EXTRA_VOLUME_STREAM_VALUE"
    }
}
