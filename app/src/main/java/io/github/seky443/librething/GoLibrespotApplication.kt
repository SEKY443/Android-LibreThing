package io.github.seky443.librething

import android.app.Application
import io.github.seky443.librething.data.SettingsRepository

class GoLibrespotApplication : Application() {
    val settingsRepository: SettingsRepository by lazy { SettingsRepository(this) }
}
