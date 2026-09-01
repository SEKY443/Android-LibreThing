package io.github.seky443.librething.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaStyleNotificationHelper
import io.github.seky443.librething.MainActivity
import io.github.seky443.librething.R
import io.github.seky443.librething.data.AppPreferences
import io.github.seky443.librething.data.SettingsRepository
import io.github.seky443.librething.service.model.ConnectionState
import io.github.seky443.librething.service.model.DeviceAuthPrompt
import io.github.seky443.librething.service.model.LogEntry
import io.github.seky443.librething.service.model.LogLevel
import io.github.seky443.librething.service.model.PlayerStatus
import io.github.seky443.librething.service.model.TrackInfo
import io.github.seky443.librething.util.GoLibrespotPaths
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Foreground service that owns the go-librespot child process, its FIFO audio player, and
 * the REST/WebSocket control client for its whole lifetime. Started/stopped from the
 * Dashboard's toggle (or on boot, see [io.github.seky443.librething.receiver.BootCompletedReceiver]),
 * never expected to be bound to directly -- UI reads/writes through [SpotifyConnectServiceState].
 */
class SpotifyConnectService : LifecycleService() {

    companion object {
        private const val CHANNEL_ID = "spotify_connect_service"
        private const val NOTIFICATION_ID = 1001
        const val ACTION_STOP = "io.github.seky443.librething.action.STOP"
        const val ACTION_PLAY_PAUSE = "io.github.seky443.librething.action.PLAY_PAUSE"
        const val ACTION_NEXT = "io.github.seky443.librething.action.NEXT"
        const val ACTION_PREVIOUS = "io.github.seky443.librething.action.PREVIOUS"
        private val AUTH_URL_REGEX = Regex("""https://accounts\.spotify\.com/authorize\S*""")

        // Matches session.go's DeviceAuthCredentials log line exactly (see
        // native/go-librespot-src/session/session.go): "to complete authentication visit
        // <url> and, if prompted, enter code <code>". Two capture groups, unlike the
        // interactive flow's single-URL AUTH_URL_REGEX, since the code has to be shown
        // separately -- it isn't embedded in the URL when Spotify doesn't return a
        // "complete" verification link.
        private val DEVICE_AUTH_REGEX =
            Regex("""to complete authentication visit (\S+) and, if prompted, enter code (\S+)""")

        // Caps a crash loop (e.g. a config that makes the daemon fail immediately every time)
        // from restarting forever; the counter resets once a launch actually reaches a ready
        // status (see pollStatusUntilReady), so a daemon that runs fine for days before one
        // real crash still gets a full fresh budget of retries.
        private const val MAX_CONSECUTIVE_RESTART_ATTEMPTS = 5
        private const val RESTART_DELAY_MILLIS = 2000L

        // A hung-but-still-alive daemon (a goroutine deadlock rather than a crash -- see the
        // two channel-send races already found and fixed in dealer/recv.go and
        // output/driver-pipe-unix.go; nothing guarantees those were the only ones) never fires
        // handleProcessExit, since the process never actually exits on its own. Polling /status
        // on this slow cadence once startup succeeds catches that case too.
        private const val HEALTH_CHECK_INTERVAL_MILLIS = 30_000L
        private const val HEALTH_CHECK_FAILURE_THRESHOLD = 3

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, SpotifyConnectService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, SpotifyConnectService::class.java))
        }
    }

    private lateinit var settingsRepository: SettingsRepository
    private var processController: GoProcessController? = null
    private var pipeAudioPlayer: PipeAudioPlayer? = null
    private var apiClient: GoLibrespotApiClient? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val authUrlOpened = AtomicBoolean(false)
    private var restartAttempts = 0
    private var healthCheckJob: kotlinx.coroutines.Job? = null

    // Backs hardware/Bluetooth media-button routing (play/pause/next/previous) into the same
    // GoLibrespotApiClient calls the Dashboard UI uses, and lets a paired speaker's own volume
    // controls reach the daemon -- see GoLibrespotPlayer and DeviceVolumeBridge.
    private var mediaSession: MediaSession? = null
    private var player: GoLibrespotPlayer? = null
    private var volumeBridge: DeviceVolumeBridge? = null

    override fun onCreate() {
        super.onCreate()
        settingsRepository = SettingsRepository(applicationContext)
        createNotificationChannel()
        setupMediaSession()
        startForeground(NOTIFICATION_ID, buildNotification(ConnectionState.Starting, null))
        volumeBridge = DeviceVolumeBridge(applicationContext, lifecycleScope, settingsRepository).also { it.start() }
        launchDaemon()
        observeStateForNotification()
    }

    private fun setupMediaSession() {
        val goPlayer = GoLibrespotPlayer(Looper.getMainLooper(), lifecycleScope)
        player = goPlayer
        val sessionActivity = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        mediaSession = MediaSession.Builder(this, goPlayer)
            .setSessionActivity(sessionActivity)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_PLAY_PAUSE -> lifecycleScope.launch(Dispatchers.IO) { SpotifyConnectServiceState.playPause() }
            ACTION_NEXT -> lifecycleScope.launch(Dispatchers.IO) { SpotifyConnectServiceState.next() }
            ACTION_PREVIOUS -> lifecycleScope.launch(Dispatchers.IO) { SpotifyConnectServiceState.previous() }
        }
        return START_STICKY
    }

    private fun launchDaemon() {
        lifecycleScope.launch(Dispatchers.IO) {
            val appPrefs = settingsRepository.appPreferences.first()
            if (appPrefs.holdWakeLock && wakeLock?.isHeld != true) acquireWakeLock()
            clearCacheIfOverLimit(appPrefs)

            val config = settingsRepository.goLibrespotConfig.first()
            val initialVolumeSteps = (settingsRepository.lastVolumeFraction.first() * GoLibrespotConfigWriter.VOLUME_STEPS)
                .roundToInt()

            // Reused across an auto-restart (see handleProcessExit) rather than recreated:
            // PipeAudioPlayer's reader loop already tolerates the daemon's write end closing
            // and reopening (that's the normal Connect-session-transfer case), so a crash
            // restart doesn't need a fresh AudioTrack either -- and releasing/recreating one
            // is a well-known trigger for Android to briefly tear down and re-establish the
            // active Bluetooth A2DP route, which shows up as the peripheral disconnecting and
            // immediately reconnecting on every crash restart.
            val player = pipeAudioPlayer ?: PipeAudioPlayer(GoLibrespotPaths.audioPipe(applicationContext)) { message ->
                SpotifyConnectServiceState.appendLog(LogEntry(LogLevel.WARN, message))
            }.also { pipeAudioPlayer = it }
            player.start()

            val client = GoLibrespotApiClient(
                onEvent = ::handlePlayerEvent,
                onLog = SpotifyConnectServiceState::appendLog,
            )
            apiClient = client
            SpotifyConnectServiceState.attach(client)
            SpotifyConnectServiceState.setConnectionState(ConnectionState.Starting)

            // onExit checks that `controller` is still the active processController before
            // acting: teardownDaemonProcess() only kills the process, it doesn't stop this
            // controller's own reader thread from eventually observing that exit and firing
            // onExit anyway -- for a real crash that's harmless (this exact callback already
            // ran once and this launch replaced it), but for a launchDaemon() triggered by
            // startHealthCheck (the process was still alive, just hung) the old reader thread's
            // delayed exit report would otherwise land on whatever new session has since
            // started and spuriously restart it too.
            lateinit var controller: GoProcessController
            controller = GoProcessController(
                context = applicationContext,
                onLog = ::handleDaemonLog,
                onExit = { exitCode -> if (processController === controller) handleProcessExit(exitCode) },
            )
            processController = controller
            controller.start(config, initialVolumeSteps)

            client.connectEvents(lifecycleScope)
            pollStatusUntilReady(client)
            startHealthCheck(client)
        }
    }

    /** Started once startup polling succeeds; see [HEALTH_CHECK_INTERVAL_MILLIS]'s kdoc. */
    private fun startHealthCheck(client: GoLibrespotApiClient) {
        healthCheckJob = lifecycleScope.launch(Dispatchers.IO) {
            var consecutiveFailures = 0
            while (true) {
                kotlinx.coroutines.delay(HEALTH_CHECK_INTERVAL_MILLIS)
                val reachable = runCatching { client.getStatus() }.getOrNull() != null
                if (reachable) {
                    consecutiveFailures = 0
                    continue
                }
                consecutiveFailures++
                if (consecutiveFailures < HEALTH_CHECK_FAILURE_THRESHOLD) continue
                SpotifyConnectServiceState.appendLog(
                    LogEntry(
                        LogLevel.ERROR,
                        "go-librespot stopped responding to $HEALTH_CHECK_FAILURE_THRESHOLD consecutive status checks -- forcing a restart",
                    )
                )
                SpotifyConnectServiceState.setConnectionState(ConnectionState.Error("go-librespot stopped responding"))
                scheduleRestart()
                return@launch
            }
        }
    }

    /** Checked before every daemon launch (including an auto-restart) rather than on a timer:
     * a plain recursive size sum over the cache directory is cheap next to everything else this
     * function already does, so there's no need for a separate schedule. Clearing wipes tracks
     * the daemon would otherwise reuse from cache, but only once actually over the configured
     * limit -- see AppPreferences.autoClearCacheEnabled/autoClearCacheMaxSizeMb. */
    private fun clearCacheIfOverLimit(appPrefs: AppPreferences) {
        if (!appPrefs.autoClearCacheEnabled) return
        val limitBytes = appPrefs.autoClearCacheMaxSizeMb.toLong() * 1024 * 1024
        if (GoLibrespotPaths.cacheDirSizeBytes(applicationContext) <= limitBytes) return
        GoLibrespotPaths.clearCacheDir(applicationContext)
        SpotifyConnectServiceState.appendLog(
            LogEntry(LogLevel.INFO, "Cleared audio cache (exceeded ${appPrefs.autoClearCacheMaxSizeMb}MB limit)")
        )
    }

    /** The daemon's API server needs a moment to bind after the process starts; poll briefly. */
    private suspend fun pollStatusUntilReady(client: GoLibrespotApiClient) {
        repeat(20) { attempt ->
            val status = client.getStatus()
            if (status != null) {
                restartAttempts = 0
                SpotifyConnectServiceState.setDeviceAuthPrompt(null)
                SpotifyConnectServiceState.setConnectionState(connectionStateFor(status))
                SpotifyConnectServiceState.setNowPlaying(status.track)
                SpotifyConnectServiceState.setVolume(status.volume, status.volumeSteps)
                return
            }
            if (attempt == 0) SpotifyConnectServiceState.setConnectionState(ConnectionState.Discoverable)
            kotlinx.coroutines.delay(1000)
        }
    }

    private fun connectionStateFor(status: PlayerStatus): ConnectionState = when {
        status.stopped -> ConnectionState.Discoverable
        status.paused -> ConnectionState.Paused
        else -> ConnectionState.Playing
    }

    /**
     * Interactive-login mode (session.go's runInteractive) logs a one-time
     * "to complete authentication visit the following link: https://accounts.spotify.com/..."
     * line with no other way to surface that URL to the user. Auto-launching it removes the
     * need to hunt for it in the log console and copy it out by hand.
     */
    private fun handleDaemonLog(entry: LogEntry) {
        SpotifyConnectServiceState.appendLog(entry)

        DEVICE_AUTH_REGEX.find(entry.message)?.let { match ->
            val (verificationUri, userCode) = match.destructured
            SpotifyConnectServiceState.setDeviceAuthPrompt(DeviceAuthPrompt(verificationUri, userCode))
            return
        }

        val url = AUTH_URL_REGEX.find(entry.message)?.value ?: return
        if (!authUrlOpened.compareAndSet(false, true)) return
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            applicationContext.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            SpotifyConnectServiceState.appendLog(LogEntry(LogLevel.WARN, "No browser available to open the login link: $e"))
        }
    }

    private fun handlePlayerEvent(event: PlayerEvent) {
        when (event) {
            is PlayerEvent.Metadata -> SpotifyConnectServiceState.setNowPlaying(event.track)
            PlayerEvent.Playing -> SpotifyConnectServiceState.setConnectionState(ConnectionState.Playing)
            PlayerEvent.Paused -> SpotifyConnectServiceState.setConnectionState(ConnectionState.Paused)
            PlayerEvent.Stopped, PlayerEvent.Inactive -> {
                SpotifyConnectServiceState.setConnectionState(ConnectionState.Discoverable)
                SpotifyConnectServiceState.setNowPlaying(null)
            }
            PlayerEvent.Active -> refreshConnectionStateFromStatus()
            is PlayerEvent.Volume -> SpotifyConnectServiceState.setVolume(event.value, event.max)
            is PlayerEvent.Seek -> SpotifyConnectServiceState.updatePosition(event.positionMs, event.durationMs)
        }
    }

    /**
     * "active" only means this device just became the Connect session's target -- it fires the
     * same way whether the incoming session is playing or paused, so treating it as "now
     * playing" outright (as this used to) caused a brief flash of real audio on connect when the
     * other client was actually paused, before a later event corrected it back. Fetch the real
     * status instead of assuming, so nothing audible happens unless go-librespot says it should.
     */
    private fun refreshConnectionStateFromStatus() {
        val client = apiClient ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            val status = client.getStatus() ?: return@launch
            SpotifyConnectServiceState.setConnectionState(connectionStateFor(status))
            SpotifyConnectServiceState.setNowPlaying(status.track)
        }
    }

    private fun handleProcessExit(exitCode: Int) {
        // Always log this, including a clean exit (0): the daemon exiting at all while the
        // service is still meant to be running is unexpected and otherwise silent.
        SpotifyConnectServiceState.appendLog(LogEntry(LogLevel.INFO, "go-librespot process exited (code $exitCode)"))
        SpotifyConnectServiceState.setDeviceAuthPrompt(null)
        if (exitCode == 0) {
            stopSelf()
            return
        }

        SpotifyConnectServiceState.setConnectionState(ConnectionState.Error("go-librespot exited (code $exitCode)"))
        scheduleRestart()
    }

    /**
     * Shared by a real crash ([handleProcessExit]) and a detected hang ([startHealthCheck]).
     * onExit fires from GoProcessController's own reader thread, not a coroutine -- hop onto
     * lifecycleScope both to read the auto-restart preference and, if it applies, to delay
     * before relaunching. lifecycleScope is cancelled as soon as the service actually starts
     * tearing down (e.g. the user hit Stop), so a restart already in flight cannot outlive it.
     */
    private fun scheduleRestart() {
        lifecycleScope.launch(Dispatchers.IO) {
            val autoRestartEnabled = settingsRepository.appPreferences.first().autoRestartOnCrashEnabled
            if (!autoRestartEnabled || restartAttempts >= MAX_CONSECUTIVE_RESTART_ATTEMPTS) {
                if (autoRestartEnabled) {
                    SpotifyConnectServiceState.appendLog(
                        LogEntry(LogLevel.ERROR, "Giving up auto-restart after $restartAttempts consecutive failures")
                    )
                }
                stopSelf()
                return@launch
            }

            restartAttempts++
            SpotifyConnectServiceState.appendLog(
                LogEntry(LogLevel.WARN, "Restarting go-librespot automatically (attempt $restartAttempts/$MAX_CONSECUTIVE_RESTART_ATTEMPTS)")
            )
            teardownDaemonProcess()
            kotlinx.coroutines.delay(RESTART_DELAY_MILLIS)
            launchDaemon()
        }
    }

    /** Tears down the daemon process and its API client, without touching the FIFO audio
     * player -- see [pipeAudioPlayer]'s reuse comment in [launchDaemon] for why a crash restart
     * deliberately keeps the same instance (and its [android.media.AudioTrack]) alive instead
     * of tearing it down here too. */
    private fun teardownDaemonProcess() {
        healthCheckJob?.cancel()
        healthCheckJob = null
        apiClient?.disconnectEvents()
        apiClient?.shutdown()
        processController?.stop()
    }

    /** Tears down everything tied to one daemon launch (the process, its FIFO player, and the
     * API client) -- the full version of [teardownDaemonProcess], used only for an actual
     * service shutdown ([onDestroy]), not a crash auto-restart. */
    private fun teardownDaemon() {
        teardownDaemonProcess()
        pipeAudioPlayer?.stop()
    }

    private fun observeStateForNotification() {
        lifecycleScope.launch {
            kotlinx.coroutines.flow.combine(
                SpotifyConnectServiceState.connectionState,
                SpotifyConnectServiceState.nowPlaying,
            ) { state, track -> state to track }.collectLatest { (state, track) ->
                val manager = getSystemService(NotificationManager::class.java)
                manager?.notify(NOTIFICATION_ID, buildNotification(state, track))
            }
        }
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "AndroidGoLibrespot::PlaybackWakeLock",
        ).apply { setReferenceCounted(false); acquire() }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.notification_channel_description)
        }
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    private fun buildNotification(state: ConnectionState, track: TrackInfo?): Notification {
        val contentText = when (state) {
            ConnectionState.Idle -> getString(R.string.status_idle)
            ConnectionState.Starting -> getString(R.string.status_starting)
            ConnectionState.Discoverable -> getString(R.string.status_discoverable)
            ConnectionState.Playing -> track?.let { "${it.name} — ${it.artistNames.joinToString(", ")}" }
                ?: getString(R.string.status_playing)
            ConnectionState.Paused -> getString(R.string.status_paused)
            is ConnectionState.Error -> state.message
        }

        val openAppIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        fun serviceAction(action: String) = PendingIntent.getService(
            this, 0,
            Intent(this, SpotifyConnectService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val isPlaying = state == ConnectionState.Playing

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_connect)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(contentText)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppIntent)
            .addAction(R.drawable.ic_media_previous, getString(R.string.action_previous), serviceAction(ACTION_PREVIOUS))
            .addAction(
                if (isPlaying) R.drawable.ic_media_pause else R.drawable.ic_media_play,
                getString(if (isPlaying) R.string.action_pause else R.string.action_play),
                serviceAction(ACTION_PLAY_PAUSE),
            )
            .addAction(R.drawable.ic_media_next, getString(R.string.action_next), serviceAction(ACTION_NEXT))
            .addAction(0, getString(R.string.action_stop), serviceAction(ACTION_STOP))

        mediaSession?.let { session ->
            builder.setStyle(MediaStyleNotificationHelper.MediaStyle(session).setShowActionsInCompactView(0, 1, 2))
        }

        return builder.build()
    }

    override fun onDestroy() {
        teardownDaemon()
        SpotifyConnectServiceState.attach(null)
        volumeBridge?.stop()
        mediaSession?.let { it.player.release(); it.release() }
        wakeLock?.let { if (it.isHeld) it.release() }
        // lifecycleScope is already cancelled by this point (tied to the same ON_DESTROY event
        // this override runs on); a standalone scope is the only way left to persist this.
        @OptIn(DelicateCoroutinesApi::class)
        GlobalScope.launch(Dispatchers.IO) { settingsRepository.setLastSessionEndAtMillis(System.currentTimeMillis()) }
        super.onDestroy()
    }
}
