package io.github.seky443.librething.ui.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.seky443.librething.GoLibrespotApplication
import io.github.seky443.librething.data.DashboardBackgroundStyle
import io.github.seky443.librething.service.SpotifyConnectService
import io.github.seky443.librething.service.SpotifyConnectServiceState
import io.github.seky443.librething.service.model.ConnectionState
import io.github.seky443.librething.service.model.LogEntry
import io.github.seky443.librething.service.model.TrackInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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
    val fakeSleepSingleTapWakeEnabled: StateFlow<Boolean> = settingsRepository.appPreferences
        .map { it.fakeSleepSingleTapWakeEnabled }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

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
            BlackScreenOverlayController.show(context, wakeOnSingleTap = fakeSleepSingleTapWakeEnabled.value)
        } else {
            BlackScreenOverlayController.requestPermission(context)
        }
    }

    private companion object {
        // Comfortably longer than a prefetched track swap typically takes, so the mask reliably
        // absorbs the auto-advance gap without regularly timing out and showing Discoverable
        // anyway; long enough on a real stop that it's a deliberate tradeoff, not a bug -- see
        // maskedConnectionAndTrack.
        const val TRACK_TRANSITION_MASK_WINDOW_MS = 1500L
    }
}
