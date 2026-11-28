package io.github.seky443.librething.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import io.github.seky443.librething.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Keeps the device's `STREAM_MUSIC` volume and the daemon's volume in step *both ways*, so that
 * the phone's own volume keys, a Bluetooth speaker's buttons (AVRCP absolute volume), this app's
 * slider and any other Spotify Connect client all move the same single value.
 *
 * That only works because the daemon runs with `external_volume: true` (see
 * [GoLibrespotConfigWriter]): it no longer scales the audio itself, so `STREAM_MUSIC` is the one
 * and only gain stage -- before, both scaled the same audio and the two numbers merely resembled
 * each other. The daemon still reports every volume change a Spotify client makes (as a `volume`
 * event, fed in through [onRemoteVolume]) and accepts volume writes at any time.
 *
 * Two-way sync is where an earlier version of this class went wrong (the volume drifted by a
 * rounding step after every change, and snapped back while a key was held), so the rules are:
 * - everything runs on one worker, one event at a time -- no two callbacks ever interleave;
 * - "did this really change?" is always answered by [VolumeMath.isInSync] against the *last value
 *   either side is known to hold* (not a timer), so echoes of our own writes are recognised by
 *   what they say rather than by when they arrive;
 * - the broadcast's own value is never trusted -- a late broadcast would describe a state that is
 *   already gone -- the current stream index is read instead;
 * - while the user is pressing keys the device is authoritative, and a remote change that arrives
 *   in that window is parked and reconciled afterwards rather than dropped;
 * - nothing ever writes `STREAM_MUSIC` from a daemon value until [onDaemonReady] has first pushed
 *   the phone's *current* level to the daemon, so a stale or zero saved daemon volume can never
 *   mute the phone or blast it.
 *
 * A change of output (a Bluetooth device connecting, headphones unplugged) changes the effective
 * index without any volume broadcast, since Android keeps a separate index per output; it's
 * detected through an [AudioDeviceCallback] and pushed to the daemon the same way.
 *
 * Also persists the daemon's reported volume as a 0f..1f fraction via [settingsRepository], fed
 * back in as `initial_volume` on the next launch (a fallback only: [onDaemonReady] overrides it).
 */
internal class DeviceVolumeBridge(
    private val context: Context,
    private val scope: CoroutineScope,
    private val settingsRepository: SettingsRepository,
) {
    private sealed interface Event {
        data object DeviceChanged : Event
        data class Flush(val token: Long) : Event
        data class RemoteVolume(val remote: Int) : Event
        data object Reconcile : Event
        data object OutputChanged : Event
        data class Bootstrap(val attempt: Int) : Event
        data object Reset : Event
    }

    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val events = Channel<Event>(Channel.UNLIMITED)
    private var workerJob: Job? = null
    private var localOverrideTimeoutJob: Job? = null
    private var receiver: BroadcastReceiver? = null
    private var deviceCallback: AudioDeviceCallback? = null

    // Everything below is only ever touched from the worker.
    private var lastKnownRemote: Int? = null
    private var latestRemote: Int? = null
    private var bootstrapped = false
    private var guardUntilMs = 0L
    private var flushToken = 0L
    private var reconcileScheduled = false

    fun start() {
        val manager = audioManager ?: return
        // A fixed-volume output (some HDMI/TV paths) ignores STREAM_MUSIC entirely; the config
        // writer runs the daemon with its own scaling in that case, and there's nothing to sync.
        if (isVolumeFixed(context)) return

        workerJob = scope.launch(Dispatchers.IO) {
            for (event in events) handle(event)
        }

        val streamReceiver = object : BroadcastReceiver() {
            override fun onReceive(receivedContext: Context, intent: Intent) {
                if (intent.getIntExtra(EXTRA_VOLUME_STREAM_TYPE, -1) != AudioManager.STREAM_MUSIC) return
                events.trySend(Event.DeviceChanged)
            }
        }
        receiver = streamReceiver
        ContextCompat.registerReceiver(
            context, streamReceiver, IntentFilter(VOLUME_CHANGED_ACTION), ContextCompat.RECEIVER_NOT_EXPORTED,
        )

        // Android keeps one volume index per output (speaker, A2DP, wired...), and switching the
        // output changes the effective index without a volume broadcast.
        val callback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) = outputChanged()
            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) = outputChanged()
        }
        deviceCallback = callback
        manager.registerAudioDeviceCallback(callback, Handler(Looper.getMainLooper()))
    }

    fun stop() {
        deviceCallback?.let { audioManager?.unregisterAudioDeviceCallback(it) }
        deviceCallback = null
        receiver?.let { context.unregisterReceiver(it) }
        receiver = null
        workerJob?.cancel()
        workerJob = null
        localOverrideTimeoutJob?.cancel()
        localOverrideTimeoutJob = null
        SpotifyConnectServiceState.setLocalDeviceVolumeFraction(null)
    }

    /** The daemon's API just started answering: push the phone's current level to it before
     * anything is allowed to flow the other way. Call on every (re)launch. */
    fun onDaemonReady() {
        events.trySend(Event.Bootstrap(attempt = 0))
    }

    /** The daemon process is being torn down; it must be bootstrapped again once it's back. */
    fun onDaemonStopped() {
        events.trySend(Event.Reset)
    }

    /** A `volume` event from the daemon (a Spotify client, this app's slider, or our own write
     * echoed back). [max] is the daemon's step count. */
    fun onRemoteVolume(value: Int, max: Int) {
        if (max <= 0) return
        val remote = Math.round(value.toDouble() / max * VolumeMath.REMOTE_STEPS).toInt()
            .coerceIn(0, VolumeMath.REMOTE_STEPS)
        events.trySend(Event.RemoteVolume(remote))
    }

    private fun outputChanged() {
        scope.launch(Dispatchers.Default) {
            // The new output's index settles a moment after the callback.
            delay(OUTPUT_SETTLE_MS)
            events.trySend(Event.OutputChanged)
        }
    }

    private suspend fun handle(event: Event) {
        when (event) {
            Event.Reset -> bootstrapped = false
            is Event.Bootstrap -> bootstrap(event.attempt)
            Event.DeviceChanged -> onDeviceChanged()
            is Event.Flush -> if (event.token == flushToken) {
                pushDeviceLevel()
                scheduleReconcile()
            }
            is Event.RemoteVolume -> onRemote(event.remote)
            Event.Reconcile -> {
                reconcileScheduled = false
                if (SystemClock.elapsedRealtime() < guardUntilMs) {
                    scheduleReconcile()
                } else {
                    latestRemote?.let(::applyRemote)
                }
            }
            Event.OutputChanged -> if (bootstrapped) pushDeviceLevel()
        }
    }

    private suspend fun bootstrap(attempt: Int) {
        bootstrapped = false
        val (index, max) = readStream() ?: return
        val remote = VolumeMath.deviceToRemote(index, max)
        if (SpotifyConnectServiceState.setVolumeCommand(remote)) {
            lastKnownRemote = remote
            latestRemote = remote
            bootstrapped = true
        } else if (attempt < MAX_BOOTSTRAP_ATTEMPTS) {
            // The API said it was up a moment ago; give it a beat rather than giving up for good.
            scope.launch(Dispatchers.Default) {
                delay(BOOTSTRAP_RETRY_MS)
                events.trySend(Event.Bootstrap(attempt + 1))
            }
        }
    }

    private fun onDeviceChanged() {
        val (index, max) = readStream() ?: return
        val known = lastKnownRemote
        // An echo of a write we made (or a change that doesn't actually move anything).
        if (known != null && VolumeMath.isInSync(index, known, max)) return

        guardUntilMs = SystemClock.elapsedRealtime() + GUARD_MS
        // Instant local feedback: shown right away, well before the debounced push below (and its
        // round trip to the daemon) completes.
        SpotifyConnectServiceState.setLocalDeviceVolumeFraction(index.toFloat() / max)
        // Safety net so the UI never stays stuck on this if the daemon never converges on it.
        localOverrideTimeoutJob?.cancel()
        localOverrideTimeoutJob = scope.launch(Dispatchers.Default) {
            delay(LOCAL_OVERRIDE_TIMEOUT_MS)
            SpotifyConnectServiceState.setLocalDeviceVolumeFraction(null)
        }
        // Holding a key fires a burst of broadcasts; only the value left after a quiet period is sent.
        flushToken++
        val token = flushToken
        scope.launch(Dispatchers.Default) {
            delay(DEBOUNCE_MS)
            events.trySend(Event.Flush(token))
        }
    }

    private fun pushDeviceLevel() {
        val (index, max) = readStream() ?: return
        val known = lastKnownRemote
        if (known != null && VolumeMath.isInSync(index, known, max)) return
        val remote = VolumeMath.deviceToRemote(index, max)
        // On failure leave lastKnownRemote alone, so the next change (or output change) retries.
        if (SpotifyConnectServiceState.setVolumeCommand(remote)) lastKnownRemote = remote
    }

    private fun onRemote(remote: Int) {
        lastKnownRemote = remote
        latestRemote = remote
        scope.launch(Dispatchers.IO) {
            settingsRepository.setLastVolumeFraction(remote.toFloat() / VolumeMath.REMOTE_STEPS)
        }
        // The daemon has caught up to what the instant local-feedback override already showed --
        // clear it so display reverts to the normal, animated daemon-driven value.
        val local = SpotifyConnectServiceState.localDeviceVolumeFraction.value
        if (local != null && abs(remote.toFloat() / VolumeMath.REMOTE_STEPS - local) < CONVERGED_THRESHOLD) {
            SpotifyConnectServiceState.setLocalDeviceVolumeFraction(null)
        }

        if (!bootstrapped) return
        if (SystemClock.elapsedRealtime() < guardUntilMs) {
            // The user is pressing keys right now: the device wins for the moment, but this change
            // is parked, not dropped -- it's reconciled once the window ends.
            scheduleReconcile()
            return
        }
        applyRemote(remote)
    }

    private fun scheduleReconcile() {
        if (reconcileScheduled) return
        reconcileScheduled = true
        val wait = (guardUntilMs - SystemClock.elapsedRealtime()).coerceAtLeast(0) + RECONCILE_SLACK_MS
        scope.launch(Dispatchers.Default) {
            delay(wait)
            events.trySend(Event.Reconcile)
        }
    }

    /** Moves `STREAM_MUSIC` to what the daemon says, unless it's already there. */
    private fun applyRemote(remote: Int) {
        val manager = audioManager ?: return
        val (current, max) = readStream() ?: return
        if (VolumeMath.isInSync(current, remote, max)) return
        val target = VolumeMath.remoteToDevice(remote, max)
        try {
            manager.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
        } catch (_: SecurityException) {
            // Some OEMs restrict STREAM_MUSIC changes under active Do Not Disturb policies.
            return
        }
        // The system may have applied less than asked (safe-media-volume on headphones, OEM caps)
        // without any broadcast -- if so, tell the daemon the truth rather than letting the two
        // sides disagree for good.
        val actual = manager.getStreamVolume(AudioManager.STREAM_MUSIC)
        if (actual != target) {
            val corrected = VolumeMath.deviceToRemote(actual, max)
            if (SpotifyConnectServiceState.setVolumeCommand(corrected)) {
                lastKnownRemote = corrected
                latestRemote = corrected
            }
        }
    }

    /** (current index, max index), or null when the stream can't be read. */
    private fun readStream(): Pair<Int, Int>? {
        val manager = audioManager ?: return null
        val max = manager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        if (max <= 0) return null
        return manager.getStreamVolume(AudioManager.STREAM_MUSIC) to max
    }

    internal companion object {
        const val DEBOUNCE_MS = 200L

        // How long after a real device-side change the device stays authoritative.
        const val GUARD_MS = 1500L
        const val RECONCILE_SLACK_MS = 50L
        const val CONVERGED_THRESHOLD = 0.02f
        const val LOCAL_OVERRIDE_TIMEOUT_MS = 3000L
        const val OUTPUT_SETTLE_MS = 600L
        const val BOOTSTRAP_RETRY_MS = 2000L
        const val MAX_BOOTSTRAP_ATTEMPTS = 5

        /** True for outputs whose gain Android doesn't control (see [start]). */
        fun isVolumeFixed(context: Context): Boolean =
            context.getSystemService(AudioManager::class.java)?.isVolumeFixed == true

        // No public AudioManager constant exists for this pre-MediaSession, protected system
        // broadcast; it's the long-standing stable way apps observe stream volume changes made
        // outside their own process (hardware keys, other apps, AVRCP from a paired speaker).
        const val VOLUME_CHANGED_ACTION = "android.media.VOLUME_CHANGED_ACTION"
        const val EXTRA_VOLUME_STREAM_TYPE = "android.media.EXTRA_VOLUME_STREAM_TYPE"
    }
}
