package com.example.android_go_librespot.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.example.android_go_librespot.MainActivity
import com.example.android_go_librespot.R
import com.example.android_go_librespot.data.SettingsRepository
import com.example.android_go_librespot.service.model.ConnectionState
import com.example.android_go_librespot.service.model.LogEntry
import com.example.android_go_librespot.service.model.LogLevel
import com.example.android_go_librespot.service.model.TrackInfo
import com.example.android_go_librespot.util.GoLibrespotPaths
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Foreground service that owns the go-librespot child process, its FIFO audio player, and
 * the REST/WebSocket control client for its whole lifetime. Started/stopped from the
 * Dashboard's toggle (or on boot, see [com.example.android_go_librespot.receiver.BootCompletedReceiver]),
 * never expected to be bound to directly -- UI reads/writes through [SpotifyConnectServiceState].
 */
class SpotifyConnectService : LifecycleService() {

    companion object {
        private const val CHANNEL_ID = "spotify_connect_service"
        private const val NOTIFICATION_ID = 1001
        const val ACTION_STOP = "com.example.android_go_librespot.action.STOP"
        private val AUTH_URL_REGEX = Regex("""https://accounts\.spotify\.com/authorize\S*""")

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

    override fun onCreate() {
        super.onCreate()
        settingsRepository = SettingsRepository(applicationContext)
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification(ConnectionState.Starting, null))
        launchDaemon()
        observeStateForNotification()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    private fun launchDaemon() {
        lifecycleScope.launch(Dispatchers.IO) {
            val appPrefs = settingsRepository.appPreferences.first()
            if (appPrefs.holdWakeLock) acquireWakeLock()

            val config = settingsRepository.goLibrespotConfig.first()

            val player = PipeAudioPlayer(GoLibrespotPaths.audioPipe(applicationContext)) { message ->
                SpotifyConnectServiceState.appendLog(LogEntry(LogLevel.WARN, message))
            }
            pipeAudioPlayer = player
            player.start()

            val client = GoLibrespotApiClient(
                onEvent = ::handlePlayerEvent,
                onLog = SpotifyConnectServiceState::appendLog,
            )
            apiClient = client
            SpotifyConnectServiceState.attach(client)
            SpotifyConnectServiceState.setConnectionState(ConnectionState.Starting)

            val controller = GoProcessController(
                context = applicationContext,
                onLog = ::handleDaemonLog,
                onExit = ::handleProcessExit,
            )
            processController = controller
            controller.start(config)

            client.connectEvents(lifecycleScope)
            pollStatusUntilReady(client)
        }
    }

    /** The daemon's API server needs a moment to bind after the process starts; poll briefly. */
    private suspend fun pollStatusUntilReady(client: GoLibrespotApiClient) {
        repeat(20) { attempt ->
            val status = client.getStatus()
            if (status != null) {
                SpotifyConnectServiceState.setConnectionState(
                    when {
                        status.stopped -> ConnectionState.Discoverable
                        status.paused -> ConnectionState.Paused
                        else -> ConnectionState.Playing
                    }
                )
                SpotifyConnectServiceState.setNowPlaying(status.track)
                SpotifyConnectServiceState.setVolume(status.volume, status.volumeSteps)
                return
            }
            if (attempt == 0) SpotifyConnectServiceState.setConnectionState(ConnectionState.Discoverable)
            kotlinx.coroutines.delay(1000)
        }
    }

    /**
     * Interactive-login mode (session.go's runInteractive) logs a one-time
     * "to complete authentication visit the following link: https://accounts.spotify.com/..."
     * line with no other way to surface that URL to the user. Auto-launching it removes the
     * need to hunt for it in the log console and copy it out by hand.
     */
    private fun handleDaemonLog(entry: LogEntry) {
        SpotifyConnectServiceState.appendLog(entry)

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
            PlayerEvent.Active -> SpotifyConnectServiceState.setConnectionState(ConnectionState.Playing)
            is PlayerEvent.Volume -> SpotifyConnectServiceState.setVolume(event.value, event.max)
        }
    }

    private fun handleProcessExit(exitCode: Int) {
        // Always log this, including a clean exit (0): the daemon exiting at all while the
        // service is still meant to be running is unexpected and otherwise silent.
        SpotifyConnectServiceState.appendLog(LogEntry(LogLevel.INFO, "go-librespot process exited (code $exitCode)"))
        if (exitCode != 0) {
            SpotifyConnectServiceState.setConnectionState(ConnectionState.Error("go-librespot exited (code $exitCode)"))
        }
        stopSelf()
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
        val stopIntent = PendingIntent.getService(
            this, 0,
            Intent(this, SpotifyConnectService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_connect)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(contentText)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppIntent)
            .addAction(0, getString(R.string.action_stop), stopIntent)
            .build()
    }

    override fun onDestroy() {
        apiClient?.disconnectEvents()
        apiClient?.shutdown()
        SpotifyConnectServiceState.attach(null)
        processController?.stop()
        pipeAudioPlayer?.stop()
        wakeLock?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }
}
