package com.example.android_go_librespot

import android.app.Application
import com.example.android_go_librespot.data.SettingsRepository

class GoLibrespotApplication : Application() {
    val settingsRepository: SettingsRepository by lazy { SettingsRepository(this) }
}
