package com.example.android_go_librespot.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.android_go_librespot.data.SettingsRepository
import com.example.android_go_librespot.service.SpotifyConnectService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Starting a foreground service from a BOOT_COMPLETED receiver is one of the exemptions to
 * Android's background-start restrictions (apps holding RECEIVE_BOOT_COMPLETED are allowed
 * to do this in direct response to the broadcast), so this stays a plain manifest receiver
 * rather than needing WorkManager.
 */
class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val autostart = SettingsRepository(context.applicationContext)
                    .appPreferences.first().autostartOnBoot
                if (autostart) {
                    SpotifyConnectService.start(context.applicationContext)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
