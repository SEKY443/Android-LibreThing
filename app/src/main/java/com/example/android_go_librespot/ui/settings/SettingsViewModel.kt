package com.example.android_go_librespot.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.android_go_librespot.GoLibrespotApplication
import com.example.android_go_librespot.data.AppPreferences
import com.example.android_go_librespot.data.GoLibrespotConfig
import com.example.android_go_librespot.util.BatteryOptimizationHelper
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

    fun updateConfig(transform: (GoLibrespotConfig) -> GoLibrespotConfig) {
        viewModelScope.launch { settingsRepository.updateGoLibrespotConfig(transform) }
    }

    fun updateAppPreferences(transform: (AppPreferences) -> AppPreferences) {
        viewModelScope.launch { settingsRepository.updateAppPreferences(transform) }
    }
}
