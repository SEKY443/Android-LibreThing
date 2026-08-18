package com.example.android_go_librespot.ui.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.android_go_librespot.service.SpotifyConnectService
import com.example.android_go_librespot.service.SpotifyConnectServiceState
import com.example.android_go_librespot.service.model.ConnectionState
import com.example.android_go_librespot.service.model.LogEntry
import com.example.android_go_librespot.service.model.TrackInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    val connectionState: StateFlow<ConnectionState> = SpotifyConnectServiceState.connectionState
    val nowPlaying: StateFlow<TrackInfo?> = SpotifyConnectServiceState.nowPlaying
    val volume: StateFlow<Pair<Int, Int>> = SpotifyConnectServiceState.volume
    val isServiceRunning: StateFlow<Boolean> = SpotifyConnectServiceState.isServiceRunning
    val logs: StateFlow<List<LogEntry>> = SpotifyConnectServiceState.logs

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
    fun clearLogs() = SpotifyConnectServiceState.clearLogs()

    /** Shows the black-screen overlay if permitted, otherwise sends the user to grant it. */
    fun fakeSleep() {
        val context = getApplication<Application>()
        if (BlackScreenOverlayController.canShow(context)) {
            BlackScreenOverlayController.show(context)
        } else {
            BlackScreenOverlayController.requestPermission(context)
        }
    }
}
