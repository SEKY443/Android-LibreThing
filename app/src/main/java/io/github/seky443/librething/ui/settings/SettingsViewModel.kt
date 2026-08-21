package io.github.seky443.librething.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.seky443.librething.GoLibrespotApplication
import io.github.seky443.librething.data.AppPreferences
import io.github.seky443.librething.data.GoLibrespotConfig
import io.github.seky443.librething.ui.dashboard.BlackScreenOverlayController
import io.github.seky443.librething.util.BatteryOptimizationHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepository = (application as GoLibrespotApplication).settingsRepository

    val goLibrespotConfig: StateFlow<GoLibrespotConfig> = settingsRepository.goLibrespotConfig
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), GoLibrespotConfig())

    val appPreferences: StateFlow<AppPreferences> = settingsRepository.appPreferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppPreferences())

    private val _isBatteryOptimizationExempt = MutableStateFlow(false)
    val isBatteryOptimizationExempt: StateFlow<Boolean> = _isBatteryOptimizationExempt.asStateFlow()

    fun refreshBatteryOptimizationStatus() {
        _isBatteryOptimizationExempt.value =
            BatteryOptimizationHelper.isIgnoringBatteryOptimizations(getApplication())
    }

    // Fake sleep (both the manual button and the automatic idle/nap-mode triggers in
    // MainActivity) draws a SYSTEM_ALERT_WINDOW overlay; without that permission it just
    // silently never appears, with nothing on screen to explain why -- surfaced here instead so
    // Settings can show a persistent notice pointing at the fix.
    private val _isOverlayPermissionGranted = MutableStateFlow(false)
    val isOverlayPermissionGranted: StateFlow<Boolean> = _isOverlayPermissionGranted.asStateFlow()

    fun refreshOverlayPermissionStatus() {
        _isOverlayPermissionGranted.value = BlackScreenOverlayController.canShow(getApplication())
    }

    fun updateConfig(transform: (GoLibrespotConfig) -> GoLibrespotConfig) {
        viewModelScope.launch { settingsRepository.updateGoLibrespotConfig(transform) }
    }

    fun updateAppPreferences(transform: (AppPreferences) -> AppPreferences) {
        viewModelScope.launch { settingsRepository.updateAppPreferences(transform) }
    }
}
