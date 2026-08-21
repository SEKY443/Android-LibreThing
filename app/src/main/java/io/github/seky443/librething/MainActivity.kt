package io.github.seky443.librething

import android.Manifest
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.github.seky443.librething.data.AppPreferences
import io.github.seky443.librething.data.ThemeMode
import io.github.seky443.librething.service.SpotifyConnectServiceState
import io.github.seky443.librething.service.model.ConnectionState
import io.github.seky443.librething.ui.dashboard.BlackScreenOverlayController
import io.github.seky443.librething.ui.navigation.AppNavHost
import io.github.seky443.librething.ui.theme.AndroidGoLibrespotTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op either way */ }

    /** Bumped on every touch/key event dispatched to this activity's window (see
     * [onUserInteraction]) -- feeds the auto-sleep-when-idle timer below so actually using the
     * app (e.g. sitting on Settings with nothing playing) keeps deferring sleep the same way
     * playback does, instead of going black out from under an attentive user. */
    private val lastInteractionAtMillis = MutableStateFlow(System.currentTimeMillis())

    override fun onUserInteraction() {
        super.onUserInteraction()
        lastInteractionAtMillis.value = System.currentTimeMillis()
    }

    /** True while the simplified Dashboard's own volume slider is on screen -- see
     * [onKeyDown]/[onKeyUp]. A plain volatile field rather than Compose state: it's only ever
     * read from those two imperative callbacks, never composed. */
    @Volatile private var suppressSystemVolumePanel = false

    /**
     * The simplified Dashboard already shows its own volume slider (see
     * [io.github.seky443.librething.ui.dashboard.SimpleDashboardScreen]), so the system's
     * volume overlay on top of it is redundant chrome -- adjusting the stream directly with
     * `flags = 0` (no `FLAG_SHOW_UI`) instead of deferring to the default key handling gets the
     * same volume change without it. [DeviceVolumeBridge] still picks this up the same way it
     * does any other externally-triggered `STREAM_MUSIC` change (Bluetooth AVRCP, another app).
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (suppressSystemVolumePanel && (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)) {
            val audioManager = getSystemService(AudioManager::class.java)
            val direction = if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
            audioManager?.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, 0)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (suppressSystemVolumePanel && (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)) {
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        val settingsRepository = (application as GoLibrespotApplication).settingsRepository
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                settingsRepository.appPreferences.map { it.keepScreenOn }.distinctUntilChanged().collect { keepScreenOn ->
                    if (keepScreenOn) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                }
            }
        }

        // Auto sleep when idle: watches actual playback state, fading to black after a
        // configurable quiet period. Two modes:
        // - Normal (napModeEnabled off): stays awake continuously the whole time something's
        //   playing, sleeping only once genuinely idle (not Playing) for the delay. Any resume
        //   wakes it, and so does pausing (pressing pause is a deliberate action, never the
        //   ambiguous "was this just an automatic resume" case worth gating).
        // - Nap mode (napModeEnabled on): never exempts Playing from the countdown -- it always
        //   re-sleeps after the delay, even mid-playback, and only wakes again on an actual
        //   event (connect, track change, play, pause).
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Tracked across collectLatest invocations (same enclosing scope, not reset per
                // emission) so each mode can tell a genuinely fresh event from this same signal
                // just getting redelivered by an unrelated re-emission (a touch, a settings
                // change) by comparing against what was last seen.
                var lastNonPlayingState: ConnectionState? = null
                var lastNapState: ConnectionState? = null
                var lastNapTrackUri: String? = null
                // kotlinx.coroutines' typed combine() overloads top out at 5 flows -- nested one
                // level so the settings themselves (4 flows) fold into one AutoSleepSignal
                // before joining connectionState/nowPlaying, rather than reaching for the
                // untyped vararg overload for just one extra flow.
                val settingsSignalFlow = combine(
                    settingsRepository.appPreferences.map { it.autoSleepOnIdleEnabled }.distinctUntilChanged(),
                    settingsRepository.appPreferences.map { it.autoSleepIdleDelaySeconds }.distinctUntilChanged(),
                    settingsRepository.appPreferences.map { it.autoSleepNapModeEnabled }.distinctUntilChanged(),
                    settingsRepository.appPreferences.map { it.fakeSleepSingleTapWakeEnabled }.distinctUntilChanged(),
                ) { enabled, delaySeconds, napMode, singleTapWake ->
                    AutoSleepSettings(enabled, delaySeconds, napMode, singleTapWake)
                }
                val signalFlow = combine(
                    settingsSignalFlow,
                    SpotifyConnectServiceState.connectionState,
                    SpotifyConnectServiceState.nowPlaying,
                ) { settings, state, track ->
                    AutoSleepSignal(settings.enabled, settings.delaySeconds, settings.napModeEnabled, settings.singleTapWakeEnabled, state, track?.uri)
                }
                // lastInteractionAtMillis isn't folded into the signal itself -- combining it in
                // here only needs to restart the idle countdown below (collectLatest cancels the
                // pending delay on any new emission, touch included), not change what the signal
                // says.
                combine(signalFlow, lastInteractionAtMillis) { signal, _ -> signal }
                    .collectLatest { signal ->
                        if (!signal.enabled) return@collectLatest
                        if (signal.napModeEnabled) {
                            val isFreshEvent = signal.connectionState != lastNapState ||
                                (signal.trackUri != null && signal.trackUri != lastNapTrackUri)
                            if (isFreshEvent && BlackScreenOverlayController.isShowing) {
                                BlackScreenOverlayController.hide(animate = true)
                            }
                            lastNapState = signal.connectionState
                            lastNapTrackUri = signal.trackUri
                            delay(signal.delaySeconds * 1000L)
                            if (!BlackScreenOverlayController.isShowing && BlackScreenOverlayController.canShow(this@MainActivity)) {
                                BlackScreenOverlayController.show(this@MainActivity, animate = true, wakeOnSingleTap = signal.singleTapWakeEnabled)
                            }
                            return@collectLatest
                        }
                        if (signal.connectionState == ConnectionState.Playing) {
                            if (BlackScreenOverlayController.isShowing) {
                                BlackScreenOverlayController.hide(animate = true)
                            }
                            return@collectLatest
                        }
                        if (signal.connectionState == ConnectionState.Paused &&
                            lastNonPlayingState != ConnectionState.Paused &&
                            BlackScreenOverlayController.isShowing
                        ) {
                            BlackScreenOverlayController.hide(animate = true)
                        }
                        lastNonPlayingState = signal.connectionState
                        delay(signal.delaySeconds * 1000L)
                        if (!BlackScreenOverlayController.isShowing && BlackScreenOverlayController.canShow(this@MainActivity)) {
                            BlackScreenOverlayController.show(this@MainActivity, animate = true, wakeOnSingleTap = signal.singleTapWakeEnabled)
                        }
                    }
            }
        }

        setContent {
            val appPrefs by settingsRepository.appPreferences.collectAsState(initial = AppPreferences())
            val systemDarkTheme = isSystemInDarkTheme()
            val darkTheme = when (appPrefs.themeMode) {
                ThemeMode.SYSTEM -> systemDarkTheme
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            AndroidGoLibrespotTheme(darkTheme = darkTheme) {
                AppNavHost(onSuppressSystemVolumePanelChange = { suppressSystemVolumePanel = it })
            }
        }
    }

    private data class AutoSleepSettings(
        val enabled: Boolean,
        val delaySeconds: Int,
        val napModeEnabled: Boolean,
        val singleTapWakeEnabled: Boolean,
    )

    private data class AutoSleepSignal(
        val enabled: Boolean,
        val delaySeconds: Int,
        val napModeEnabled: Boolean,
        val singleTapWakeEnabled: Boolean,
        val connectionState: ConnectionState,
        val trackUri: String?,
    )
}
