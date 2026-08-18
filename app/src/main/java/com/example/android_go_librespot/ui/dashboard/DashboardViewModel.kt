package com.example.android_go_librespot.ui.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.example.android_go_librespot.service.SpotifyConnectService
import com.example.android_go_librespot.service.SpotifyConnectServiceState
import com.example.android_go_librespot.service.model.ConnectionState
import com.example.android_go_librespot.service.model.LogEntry
import com.example.android_go_librespot.service.model.TrackInfo
import kotlinx.coroutines.flow.StateFlow

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

    fun playPause() = SpotifyConnectServiceState.playPause()
    fun next() = SpotifyConnectServiceState.next()
    fun previous() = SpotifyConnectServiceState.previous()
    fun setVolume(value: Int) = SpotifyConnectServiceState.setVolumeCommand(value)
    fun clearLogs() = SpotifyConnectServiceState.clearLogs()
}
