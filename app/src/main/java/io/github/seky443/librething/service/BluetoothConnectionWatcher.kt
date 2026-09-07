package io.github.seky443.librething.service

import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import io.github.seky443.librething.service.model.LogEntry
import io.github.seky443.librething.service.model.LogLevel

/**
 * Logs A2DP profile connection-state changes into [SpotifyConnectServiceState]'s log flow, so
 * the Dashboard's log console shows them alongside the daemon's own output -- the daemon has no
 * visibility into Bluetooth at all, since audio routing happens entirely on the Android side
 * (see [PipeAudioPlayer]).
 *
 * Deliberately only reads the connection-state int extras, not the [android.bluetooth.BluetoothDevice]
 * itself (name/address) -- that needs `BLUETOOTH_CONNECT` on API 31+, a runtime permission this
 * app has no other reason to request. The state transition alone (with a timestamp, correlatable
 * against the daemon's own log lines from the same moment) is enough to tell a real A2DP drop
 * apart from, say, the network blips already visible in the daemon log.
 */
internal class BluetoothConnectionWatcher(private val context: Context) {
    private var receiver: BroadcastReceiver? = null

    fun start() {
        val filter = IntentFilter(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
        val stateReceiver = object : BroadcastReceiver() {
            override fun onReceive(receivedContext: Context, intent: Intent) {
                val state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1)
                val previousState = intent.getIntExtra(BluetoothProfile.EXTRA_PREVIOUS_STATE, -1)
                SpotifyConnectServiceState.appendLog(
                    LogEntry(
                        LogLevel.INFO,
                        "Bluetooth A2DP state changed: ${stateName(previousState)} -> ${stateName(state)}",
                    )
                )
            }
        }
        receiver = stateReceiver
        ContextCompat.registerReceiver(context, stateReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    fun stop() {
        receiver?.let { context.unregisterReceiver(it) }
        receiver = null
    }

    private fun stateName(state: Int): String = when (state) {
        BluetoothProfile.STATE_DISCONNECTED -> "DISCONNECTED"
        BluetoothProfile.STATE_CONNECTING -> "CONNECTING"
        BluetoothProfile.STATE_CONNECTED -> "CONNECTED"
        BluetoothProfile.STATE_DISCONNECTING -> "DISCONNECTING"
        else -> "UNKNOWN($state)"
    }
}
