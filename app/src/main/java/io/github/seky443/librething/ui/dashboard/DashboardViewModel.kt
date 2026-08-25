package io.github.seky443.librething.ui.dashboard

import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.seky443.librething.GoLibrespotApplication
import io.github.seky443.librething.data.DashboardBackgroundStyle
import io.github.seky443.librething.service.SpotifyConnectService
import io.github.seky443.librething.service.SpotifyConnectServiceState
import io.github.seky443.librething.service.model.ConnectionState
import io.github.seky443.librething.service.model.DeviceAuthPrompt
import io.github.seky443.librething.service.model.LogEntry
import io.github.seky443.librething.service.model.TrackInfo
import io.github.seky443.librething.util.UpdateChecker
import io.github.seky443.librething.util.UpdateInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch

class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepository = (application as GoLibrespotApplication).settingsRepository

    // On a natural end-of-track auto-advance, the daemon emits a "not_playing" event for the
    // finished track *before* it has anything loaded for the next one, which the app maps to
    // ConnectionState.Discoverable + nowPlaying = null -- briefly showing the idle screen until
    // the next track's own events arrive a moment later. A manual skip never emits that
    // intermediate event, going straight from the old track to the new one, so it never shows
    // this. Some people like the flash (it is honestly kind of neat); maskTrackTransitionFlashEnabled
    // lets the rest hide it instead of the daemon behavior being changed outright: while enabled,
    // a transition through Discoverable is held un-emitted for a grace window in case it's just
    // this gap, so the dashboard keeps showing the previous track until either the next one's
    // events arrive (no flash at all) or the window elapses, meaning it's a real stop.
    private val maskedConnectionAndTrack: StateFlow<Pair<ConnectionState, TrackInfo?>> = combine(
        SpotifyConnectServiceState.connectionState,
        SpotifyConnectServiceState.nowPlaying,
        settingsRepository.appPreferences.map { it.maskTrackTransitionFlashEnabled },
    ) { state, track, maskEnabled -> Triple(state, track, maskEnabled) }
        .transformLatest { (state, track, maskEnabled) ->
            if (maskEnabled && state == ConnectionState.Discoverable) {
                delay(TRACK_TRANSITION_MASK_WINDOW_MS)
            }
            emit(state to track)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ConnectionState.Idle to null)

    val deviceAuthPrompt: StateFlow<DeviceAuthPrompt?> = SpotifyConnectServiceState.deviceAuthPrompt
    val connectionState: StateFlow<ConnectionState> = maskedConnectionAndTrack
        .map { it.first }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ConnectionState.Idle)
    val nowPlaying: StateFlow<TrackInfo?> = maskedConnectionAndTrack
        .map { it.second }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val volume: StateFlow<Pair<Int, Int>> = SpotifyConnectServiceState.volume
    val localDeviceVolumeFraction: StateFlow<Float?> = SpotifyConnectServiceState.localDeviceVolumeFraction
    val isServiceRunning: StateFlow<Boolean> = SpotifyConnectServiceState.isServiceRunning
    val logs: StateFlow<List<LogEntry>> = SpotifyConnectServiceState.logs
    val volumeSliderEnabled: StateFlow<Boolean> = settingsRepository.appPreferences
        .map { it.volumeSliderEnabled }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val volumeSliderLandscapeOnly: StateFlow<Boolean> = settingsRepository.appPreferences
        .map { it.volumeSliderLandscapeOnly }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val landscapeLeftAlignTrackInfoEnabled: StateFlow<Boolean> = settingsRepository.appPreferences
        .map { it.landscapeLeftAlignTrackInfoEnabled }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val lastSessionEndAtMillis: StateFlow<Long?> = settingsRepository.lastSessionEndAtMillis
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val nerdModeEnabled: StateFlow<Boolean> = settingsRepository.appPreferences
        .map { it.nerdModeEnabled }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val dashboardBackgroundStyle: StateFlow<DashboardBackgroundStyle> = settingsRepository.appPreferences
        .map { it.dashboardBackgroundStyle }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardBackgroundStyle.TINTED)
    val fullscreenModeEnabled: StateFlow<Boolean> = settingsRepository.appPreferences
        .map { it.fullscreenModeEnabled }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val hideConsoleButtonEnabled: StateFlow<Boolean> = settingsRepository.appPreferences
        .map { it.hideConsoleButtonEnabled }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val hideFakeSleepButtonEnabled: StateFlow<Boolean> = settingsRepository.appPreferences
        .map { it.hideFakeSleepButtonEnabled }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val hideIconRowInLandscapeEnabled: StateFlow<Boolean> = settingsRepository.appPreferences
        .map { it.hideIconRowInLandscapeEnabled }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val hideLastSessionLabelInLandscapeEnabled: StateFlow<Boolean> = settingsRepository.appPreferences
        .map { it.hideLastSessionLabelInLandscapeEnabled }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val landscapeStretchTransportRowEnabled: StateFlow<Boolean> = settingsRepository.appPreferences
        .map { it.landscapeStretchTransportRowEnabled }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    // Eagerly, not WhileSubscribed: unlike the other settings-derived StateFlows in this
    // ViewModel, these two are only ever read via .value from fakeSleep() below, never
    // collected by a composable -- WhileSubscribed would never see an active collector, so
    // .value would stay stuck at its seed default (see fakeSleep()'s outdated behavior before
    // this) instead of ever picking up the real DataStore value.
    val fakeSleepSingleTapWakeEnabled: StateFlow<Boolean> = settingsRepository.appPreferences
        .map { it.fakeSleepSingleTapWakeEnabled }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val gestureControlsEnabled: StateFlow<Boolean> = settingsRepository.appPreferences
        .map { it.gestureControlsEnabled }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val gestureHapticIntensity: StateFlow<Float> = settingsRepository.appPreferences
        .map { it.gestureHapticIntensity }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 1f)
    val gestureTransitionShowConsoleEnabled: StateFlow<Boolean> = settingsRepository.appPreferences
        .map { it.gestureTransitionShowConsoleEnabled }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val gestureTransitionRoundedCoverEnabled: StateFlow<Boolean> = settingsRepository.appPreferences
        .map { it.gestureTransitionRoundedCoverEnabled }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val gestureControlsFullScreenEnabled: StateFlow<Boolean> = settingsRepository.appPreferences
        .map { it.gestureControlsFullScreenEnabled }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val oledPixelShiftEnabled: StateFlow<Boolean> = settingsRepository.appPreferences
        .map { it.oledPixelShiftEnabled }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val oledCheckerboardDimEnabled: StateFlow<Boolean> = settingsRepository.appPreferences
        .map { it.oledCheckerboardDimEnabled }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val grayscaleFilterEnabled: StateFlow<Boolean> = settingsRepository.appPreferences
        .map { it.grayscaleFilterEnabled }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val grayscaleFilterStartMinutes: StateFlow<Int> = settingsRepository.appPreferences
        .map { it.grayscaleFilterStartMinutes }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 22 * 60)
    val grayscaleFilterEndMinutes: StateFlow<Int> = settingsRepository.appPreferences
        .map { it.grayscaleFilterEndMinutes }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 6 * 60)
    val redLightFilterEnabled: StateFlow<Boolean> = settingsRepository.appPreferences
        .map { it.redLightFilterEnabled }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val redLightFilterStartMinutes: StateFlow<Int> = settingsRepository.appPreferences
        .map { it.redLightFilterStartMinutes }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 22 * 60)
    val redLightFilterEndMinutes: StateFlow<Int> = settingsRepository.appPreferences
        .map { it.redLightFilterEndMinutes }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 6 * 60)

    private val _updateAvailable = MutableStateFlow<UpdateInfo?>(null)
    val updateAvailable: StateFlow<UpdateInfo?> = _updateAvailable.asStateFlow()

    init {
        viewModelScope.launch { checkForUpdateIfDue() }
    }

    /** One-shot on every fresh process (this ViewModel doesn't survive process death, so there's
     * no separate "already checked this session" flag needed beyond the throttle below) -- see
     * [UpdateChecker]. Silently does nothing if the toggle is off, the last check was too
     * recent, the network call fails, or the latest release is one the user already dismissed. */
    private suspend fun checkForUpdateIfDue() {
        if (!settingsRepository.appPreferences.first().autoCheckForUpdatesEnabled) return

        val lastCheckAtMillis = settingsRepository.lastUpdateCheckAtMillis.first()
        val now = System.currentTimeMillis()
        if (lastCheckAtMillis != null && now - lastCheckAtMillis < UPDATE_CHECK_THROTTLE_MILLIS) return
        settingsRepository.setLastUpdateCheckAtMillis(now)

        val currentVersionName = currentVersionName() ?: return
        val info = UpdateChecker.checkForNewerRelease(currentVersionName) ?: return
        if (info.version == settingsRepository.lastDismissedUpdateVersion.first()) return
        _updateAvailable.value = info
    }

    private fun currentVersionName(): String? {
        val context = getApplication<Application>()
        val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0)
        }
        return packageInfo.versionName
    }

    /** Persists [info] as dismissed so [checkForUpdateIfDue] won't re-prompt for this same
     * release again, even on a later launch -- a genuinely newer release still will. */
    fun dismissUpdate(info: UpdateInfo) {
        _updateAvailable.value = null
        viewModelScope.launch { settingsRepository.setLastDismissedUpdateVersion(info.version) }
    }

    fun toggleService() {
        val context = getApplication<Application>()
        if (isServiceRunning.value) {
            SpotifyConnectService.stop(context)
        } else {
            SpotifyConnectService.start(context)
        }
    }

    // GoLibrespotApiClient's player commands are synchronous blocking calls (see its kdoc);
    // dispatched off Dispatchers.IO so they don't run on the Compose UI thread, which would
    // throw NetworkOnMainThreadException -- uncaught, since that's a RuntimeException, not
    // the IOException the client itself catches.
    fun playPause() = viewModelScope.launch(Dispatchers.IO) { SpotifyConnectServiceState.playPause() }
    fun next() = viewModelScope.launch(Dispatchers.IO) { SpotifyConnectServiceState.next() }
    fun previous() = viewModelScope.launch(Dispatchers.IO) { SpotifyConnectServiceState.previous() }
    fun setVolume(value: Int) = viewModelScope.launch(Dispatchers.IO) { SpotifyConnectServiceState.setVolumeCommand(value) }
    fun seek(positionMs: Long) = viewModelScope.launch(Dispatchers.IO) { SpotifyConnectServiceState.seek(positionMs) }
    fun clearLogs() = SpotifyConnectServiceState.clearLogs()

    /** Shows the black-screen overlay if permitted, otherwise sends the user to grant it. */
    fun fakeSleep() {
        val context = getApplication<Application>()
        if (BlackScreenOverlayController.canShow(context)) {
            BlackScreenOverlayController.show(
                context,
                wakeOnSingleTap = fakeSleepSingleTapWakeEnabled.value,
                gestureControlsEnabled = gestureControlsEnabled.value,
                gestureHapticIntensity = gestureHapticIntensity.value,
            )
        } else {
            BlackScreenOverlayController.requestPermission(context)
        }
    }

    private companion object {
        // Comfortably longer than a prefetched track swap typically takes, so the mask reliably
        // absorbs the auto-advance gap without regularly timing out and showing Discoverable
        // anyway; long enough on a real stop that it's a deliberate tradeoff, not a bug -- see
        // maskedConnectionAndTrack. 1500ms used to be enough, but testing auto-advance by
        // seeking right up to a track's end (rather than waiting through it normally) showed the
        // gap occasionally running past that -- bumped up for more headroom.
        const val TRACK_TRANSITION_MASK_WINDOW_MS = 3000L

        // Twice-daily at most, regardless of how often the app is launched -- comfortably
        // inside GitHub's unauthenticated rate limit (60/hour per IP) with room to spare, since
        // this is a background nicety, not something that needs to catch a new release the
        // moment it goes out.
        const val UPDATE_CHECK_THROTTLE_MILLIS = 12 * 60 * 60 * 1000L
    }
}
