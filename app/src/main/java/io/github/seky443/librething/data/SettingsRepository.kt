package io.github.seky443.librething.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

/** Persists [GoLibrespotConfig] and [AppPreferences] via Jetpack DataStore Preferences. */
class SettingsRepository(private val context: Context) {

    private object Keys {
        val DEVICE_NAME = stringPreferencesKey("device_name")
        val DEVICE_TYPE = stringPreferencesKey("device_type")
        val BITRATE = intPreferencesKey("bitrate")
        val NORMALISATION_DISABLED = booleanPreferencesKey("normalisation_disabled")
        val NORMALISATION_USE_ALBUM_GAIN = booleanPreferencesKey("normalisation_use_album_gain")
        val NORMALISATION_PREGAIN = floatPreferencesKey("normalisation_pregain")
        val DISABLE_AUTOPLAY = booleanPreferencesKey("disable_autoplay")
        val ZEROCONF_ENABLED = booleanPreferencesKey("zeroconf_enabled")
        val ZEROCONF_PORT = intPreferencesKey("zeroconf_port")
        val CREDENTIALS_TYPE = stringPreferencesKey("credentials_type")
        val SPOTIFY_TOKEN_USERNAME = stringPreferencesKey("spotify_token_username")
        val SPOTIFY_TOKEN_ACCESS_TOKEN = stringPreferencesKey("spotify_token_access_token")
        val OPTIMISTIC_PLAYBACK_REPLIES = booleanPreferencesKey("optimistic_playback_replies")

        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val HOLD_WAKE_LOCK = booleanPreferencesKey("hold_wake_lock")
        val AUTOSTART_ON_BOOT = booleanPreferencesKey("autostart_on_boot")
        val VOLUME_SLIDER_ENABLED = booleanPreferencesKey("volume_slider_enabled")
        val VOLUME_SLIDER_LANDSCAPE_ONLY = booleanPreferencesKey("volume_slider_landscape_only")
        val LANDSCAPE_LEFT_ALIGN_TRACK_INFO_ENABLED = booleanPreferencesKey("landscape_left_align_track_info_enabled")
        val NERD_MODE_ENABLED = booleanPreferencesKey("nerd_mode_enabled")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DASHBOARD_BACKGROUND_STYLE = stringPreferencesKey("dashboard_background_style")
        val FULLSCREEN_MODE_ENABLED = booleanPreferencesKey("fullscreen_mode_enabled")
        val AUTO_SLEEP_ON_IDLE_ENABLED = booleanPreferencesKey("auto_sleep_on_idle_enabled")
        val AUTO_SLEEP_IDLE_DELAY_SECONDS = intPreferencesKey("auto_sleep_idle_delay_seconds")
        val AUTO_SLEEP_NAP_MODE_ENABLED = booleanPreferencesKey("auto_sleep_nap_mode_enabled")
        val HIDE_CONSOLE_BUTTON_ENABLED = booleanPreferencesKey("hide_console_button_enabled")
        val HIDE_FAKE_SLEEP_BUTTON_ENABLED = booleanPreferencesKey("hide_fake_sleep_button_enabled")
        val HIDE_ICON_ROW_IN_LANDSCAPE_ENABLED = booleanPreferencesKey("hide_icon_row_in_landscape_enabled")
        val HIDE_LAST_SESSION_LABEL_IN_LANDSCAPE_ENABLED = booleanPreferencesKey("hide_last_session_label_in_landscape_enabled")
        val LANDSCAPE_STRETCH_TRANSPORT_ROW_ENABLED = booleanPreferencesKey("landscape_stretch_transport_row_enabled")
        val AUTO_RESTART_ON_CRASH_ENABLED = booleanPreferencesKey("auto_restart_on_crash_enabled")
        val FAKE_SLEEP_SINGLE_TAP_WAKE_ENABLED = booleanPreferencesKey("fake_sleep_single_tap_wake_enabled")
        val MASK_TRACK_TRANSITION_FLASH_ENABLED = booleanPreferencesKey("mask_track_transition_flash_enabled")
        val GESTURE_CONTROLS_ENABLED = booleanPreferencesKey("gesture_controls_enabled")
        val GESTURE_HAPTIC_INTENSITY = floatPreferencesKey("gesture_haptic_intensity")
        val GESTURE_TRANSITION_SHOW_CONSOLE_ENABLED = booleanPreferencesKey("gesture_transition_show_console_enabled")
        val GESTURE_TRANSITION_ROUNDED_COVER_ENABLED = booleanPreferencesKey("gesture_transition_rounded_cover_enabled")
        val GESTURE_CONTROLS_FULL_SCREEN_ENABLED = booleanPreferencesKey("gesture_controls_full_screen_enabled")
        val LAST_VOLUME_FRACTION = floatPreferencesKey("last_volume_fraction")
        val LAST_SESSION_END_AT_MILLIS = longPreferencesKey("last_session_end_at_millis")
    }

    /** Wall-clock time (epoch millis) the service last stopped, or null if it never has this
     * install. Shown as the simple dashboard's idle-state "last session" label. */
    val lastSessionEndAtMillis: Flow<Long?> = context.dataStore.data.map { prefs -> prefs[Keys.LAST_SESSION_END_AT_MILLIS] }

    suspend fun setLastSessionEndAtMillis(millis: Long) {
        context.dataStore.edit { prefs -> prefs[Keys.LAST_SESSION_END_AT_MILLIS] = millis }
    }

    /**
     * The last volume [DeviceVolumeBridge] observed (as a 0f..1f fraction of whatever scale was
     * in play, device or daemon side -- fraction is what survives a daemon restart, since its
     * `volume_steps` isn't otherwise pinned to a fixed value). Fed back in as the next launch's
     * `initial_volume` (see [GoLibrespotConfigWriter]): the daemon's own last-volume memory is
     * unreachable code while `external_volume: true` is set (see that key's kdoc), so without
     * this every reconnect would reset to the daemon's `initial_volume` default (100%).
     */
    val lastVolumeFraction: Flow<Float> = context.dataStore.data.map { prefs -> prefs[Keys.LAST_VOLUME_FRACTION] ?: 1f }

    suspend fun setLastVolumeFraction(fraction: Float) {
        context.dataStore.edit { prefs -> prefs[Keys.LAST_VOLUME_FRACTION] = fraction.coerceIn(0f, 1f) }
    }

    val goLibrespotConfig: Flow<GoLibrespotConfig> = context.dataStore.data.map { prefs ->
        val defaults = GoLibrespotConfig()
        GoLibrespotConfig(
            deviceName = prefs[Keys.DEVICE_NAME] ?: defaults.deviceName,
            deviceType = prefs[Keys.DEVICE_TYPE]?.let { DeviceType.fromWireValue(it) } ?: defaults.deviceType,
            bitrate = prefs[Keys.BITRATE] ?: defaults.bitrate,
            normalisationDisabled = prefs[Keys.NORMALISATION_DISABLED] ?: defaults.normalisationDisabled,
            normalisationUseAlbumGain = prefs[Keys.NORMALISATION_USE_ALBUM_GAIN] ?: defaults.normalisationUseAlbumGain,
            normalisationPregain = prefs[Keys.NORMALISATION_PREGAIN] ?: defaults.normalisationPregain,
            disableAutoplay = prefs[Keys.DISABLE_AUTOPLAY] ?: defaults.disableAutoplay,
            zeroconfEnabled = prefs[Keys.ZEROCONF_ENABLED] ?: defaults.zeroconfEnabled,
            zeroconfPort = prefs[Keys.ZEROCONF_PORT] ?: defaults.zeroconfPort,
            credentialsType = prefs[Keys.CREDENTIALS_TYPE]?.let { CredentialsType.fromWireValue(it) } ?: defaults.credentialsType,
            spotifyTokenUsername = prefs[Keys.SPOTIFY_TOKEN_USERNAME] ?: defaults.spotifyTokenUsername,
            spotifyTokenAccessToken = prefs[Keys.SPOTIFY_TOKEN_ACCESS_TOKEN] ?: defaults.spotifyTokenAccessToken,
            optimisticPlaybackReplies = prefs[Keys.OPTIMISTIC_PLAYBACK_REPLIES] ?: defaults.optimisticPlaybackReplies,
        )
    }

    val appPreferences: Flow<AppPreferences> = context.dataStore.data.map { prefs ->
        val defaults = AppPreferences()
        AppPreferences(
            keepScreenOn = prefs[Keys.KEEP_SCREEN_ON] ?: defaults.keepScreenOn,
            holdWakeLock = prefs[Keys.HOLD_WAKE_LOCK] ?: defaults.holdWakeLock,
            autostartOnBoot = prefs[Keys.AUTOSTART_ON_BOOT] ?: defaults.autostartOnBoot,
            volumeSliderEnabled = prefs[Keys.VOLUME_SLIDER_ENABLED] ?: defaults.volumeSliderEnabled,
            volumeSliderLandscapeOnly = prefs[Keys.VOLUME_SLIDER_LANDSCAPE_ONLY] ?: defaults.volumeSliderLandscapeOnly,
            landscapeLeftAlignTrackInfoEnabled = prefs[Keys.LANDSCAPE_LEFT_ALIGN_TRACK_INFO_ENABLED] ?: defaults.landscapeLeftAlignTrackInfoEnabled,
            nerdModeEnabled = prefs[Keys.NERD_MODE_ENABLED] ?: defaults.nerdModeEnabled,
            themeMode = prefs[Keys.THEME_MODE]?.let { ThemeMode.fromWireValue(it) } ?: defaults.themeMode,
            dashboardBackgroundStyle = prefs[Keys.DASHBOARD_BACKGROUND_STYLE]?.let { DashboardBackgroundStyle.fromWireValue(it) } ?: defaults.dashboardBackgroundStyle,
            fullscreenModeEnabled = prefs[Keys.FULLSCREEN_MODE_ENABLED] ?: defaults.fullscreenModeEnabled,
            autoSleepOnIdleEnabled = prefs[Keys.AUTO_SLEEP_ON_IDLE_ENABLED] ?: defaults.autoSleepOnIdleEnabled,
            autoSleepIdleDelaySeconds = prefs[Keys.AUTO_SLEEP_IDLE_DELAY_SECONDS] ?: defaults.autoSleepIdleDelaySeconds,
            autoSleepNapModeEnabled = prefs[Keys.AUTO_SLEEP_NAP_MODE_ENABLED] ?: defaults.autoSleepNapModeEnabled,
            hideConsoleButtonEnabled = prefs[Keys.HIDE_CONSOLE_BUTTON_ENABLED] ?: defaults.hideConsoleButtonEnabled,
            hideFakeSleepButtonEnabled = prefs[Keys.HIDE_FAKE_SLEEP_BUTTON_ENABLED] ?: defaults.hideFakeSleepButtonEnabled,
            hideIconRowInLandscapeEnabled = prefs[Keys.HIDE_ICON_ROW_IN_LANDSCAPE_ENABLED] ?: defaults.hideIconRowInLandscapeEnabled,
            hideLastSessionLabelInLandscapeEnabled = prefs[Keys.HIDE_LAST_SESSION_LABEL_IN_LANDSCAPE_ENABLED] ?: defaults.hideLastSessionLabelInLandscapeEnabled,
            landscapeStretchTransportRowEnabled = prefs[Keys.LANDSCAPE_STRETCH_TRANSPORT_ROW_ENABLED] ?: defaults.landscapeStretchTransportRowEnabled,
            autoRestartOnCrashEnabled = prefs[Keys.AUTO_RESTART_ON_CRASH_ENABLED] ?: defaults.autoRestartOnCrashEnabled,
            fakeSleepSingleTapWakeEnabled = prefs[Keys.FAKE_SLEEP_SINGLE_TAP_WAKE_ENABLED] ?: defaults.fakeSleepSingleTapWakeEnabled,
            maskTrackTransitionFlashEnabled = prefs[Keys.MASK_TRACK_TRANSITION_FLASH_ENABLED] ?: defaults.maskTrackTransitionFlashEnabled,
            gestureControlsEnabled = prefs[Keys.GESTURE_CONTROLS_ENABLED] ?: defaults.gestureControlsEnabled,
            gestureHapticIntensity = prefs[Keys.GESTURE_HAPTIC_INTENSITY] ?: defaults.gestureHapticIntensity,
            gestureTransitionShowConsoleEnabled = prefs[Keys.GESTURE_TRANSITION_SHOW_CONSOLE_ENABLED] ?: defaults.gestureTransitionShowConsoleEnabled,
            gestureTransitionRoundedCoverEnabled = prefs[Keys.GESTURE_TRANSITION_ROUNDED_COVER_ENABLED] ?: defaults.gestureTransitionRoundedCoverEnabled,
            gestureControlsFullScreenEnabled = prefs[Keys.GESTURE_CONTROLS_FULL_SCREEN_ENABLED] ?: defaults.gestureControlsFullScreenEnabled,
        )
    }

    suspend fun updateGoLibrespotConfig(transform: (GoLibrespotConfig) -> GoLibrespotConfig) {
        context.dataStore.edit { prefs ->
            val existing = GoLibrespotConfig(
                deviceName = prefs[Keys.DEVICE_NAME] ?: GoLibrespotConfig().deviceName,
                deviceType = prefs[Keys.DEVICE_TYPE]?.let { DeviceType.fromWireValue(it) } ?: GoLibrespotConfig().deviceType,
                bitrate = prefs[Keys.BITRATE] ?: GoLibrespotConfig().bitrate,
                normalisationDisabled = prefs[Keys.NORMALISATION_DISABLED] ?: GoLibrespotConfig().normalisationDisabled,
                normalisationUseAlbumGain = prefs[Keys.NORMALISATION_USE_ALBUM_GAIN] ?: GoLibrespotConfig().normalisationUseAlbumGain,
                normalisationPregain = prefs[Keys.NORMALISATION_PREGAIN] ?: GoLibrespotConfig().normalisationPregain,
                disableAutoplay = prefs[Keys.DISABLE_AUTOPLAY] ?: GoLibrespotConfig().disableAutoplay,
                zeroconfEnabled = prefs[Keys.ZEROCONF_ENABLED] ?: GoLibrespotConfig().zeroconfEnabled,
                zeroconfPort = prefs[Keys.ZEROCONF_PORT] ?: GoLibrespotConfig().zeroconfPort,
                credentialsType = prefs[Keys.CREDENTIALS_TYPE]?.let { CredentialsType.fromWireValue(it) } ?: GoLibrespotConfig().credentialsType,
                spotifyTokenUsername = prefs[Keys.SPOTIFY_TOKEN_USERNAME] ?: GoLibrespotConfig().spotifyTokenUsername,
                spotifyTokenAccessToken = prefs[Keys.SPOTIFY_TOKEN_ACCESS_TOKEN] ?: GoLibrespotConfig().spotifyTokenAccessToken,
                optimisticPlaybackReplies = prefs[Keys.OPTIMISTIC_PLAYBACK_REPLIES] ?: GoLibrespotConfig().optimisticPlaybackReplies,
            )
            val updated = transform(existing)
            prefs[Keys.DEVICE_NAME] = updated.deviceName
            prefs[Keys.DEVICE_TYPE] = updated.deviceType.wireValue
            prefs[Keys.BITRATE] = updated.bitrate
            prefs[Keys.NORMALISATION_DISABLED] = updated.normalisationDisabled
            prefs[Keys.NORMALISATION_USE_ALBUM_GAIN] = updated.normalisationUseAlbumGain
            prefs[Keys.NORMALISATION_PREGAIN] = updated.normalisationPregain
            prefs[Keys.DISABLE_AUTOPLAY] = updated.disableAutoplay
            prefs[Keys.ZEROCONF_ENABLED] = updated.zeroconfEnabled
            prefs[Keys.ZEROCONF_PORT] = updated.zeroconfPort
            prefs[Keys.CREDENTIALS_TYPE] = updated.credentialsType.wireValue
            prefs[Keys.SPOTIFY_TOKEN_USERNAME] = updated.spotifyTokenUsername
            prefs[Keys.SPOTIFY_TOKEN_ACCESS_TOKEN] = updated.spotifyTokenAccessToken
            prefs[Keys.OPTIMISTIC_PLAYBACK_REPLIES] = updated.optimisticPlaybackReplies
        }
    }

    suspend fun updateAppPreferences(transform: (AppPreferences) -> AppPreferences) {
        context.dataStore.edit { prefs ->
            val existing = AppPreferences(
                keepScreenOn = prefs[Keys.KEEP_SCREEN_ON] ?: AppPreferences().keepScreenOn,
                holdWakeLock = prefs[Keys.HOLD_WAKE_LOCK] ?: AppPreferences().holdWakeLock,
                autostartOnBoot = prefs[Keys.AUTOSTART_ON_BOOT] ?: AppPreferences().autostartOnBoot,
                volumeSliderEnabled = prefs[Keys.VOLUME_SLIDER_ENABLED] ?: AppPreferences().volumeSliderEnabled,
                volumeSliderLandscapeOnly = prefs[Keys.VOLUME_SLIDER_LANDSCAPE_ONLY] ?: AppPreferences().volumeSliderLandscapeOnly,
                landscapeLeftAlignTrackInfoEnabled = prefs[Keys.LANDSCAPE_LEFT_ALIGN_TRACK_INFO_ENABLED] ?: AppPreferences().landscapeLeftAlignTrackInfoEnabled,
                nerdModeEnabled = prefs[Keys.NERD_MODE_ENABLED] ?: AppPreferences().nerdModeEnabled,
                themeMode = prefs[Keys.THEME_MODE]?.let { ThemeMode.fromWireValue(it) } ?: AppPreferences().themeMode,
                dashboardBackgroundStyle = prefs[Keys.DASHBOARD_BACKGROUND_STYLE]?.let { DashboardBackgroundStyle.fromWireValue(it) } ?: AppPreferences().dashboardBackgroundStyle,
                fullscreenModeEnabled = prefs[Keys.FULLSCREEN_MODE_ENABLED] ?: AppPreferences().fullscreenModeEnabled,
                autoSleepOnIdleEnabled = prefs[Keys.AUTO_SLEEP_ON_IDLE_ENABLED] ?: AppPreferences().autoSleepOnIdleEnabled,
                autoSleepIdleDelaySeconds = prefs[Keys.AUTO_SLEEP_IDLE_DELAY_SECONDS] ?: AppPreferences().autoSleepIdleDelaySeconds,
                autoSleepNapModeEnabled = prefs[Keys.AUTO_SLEEP_NAP_MODE_ENABLED] ?: AppPreferences().autoSleepNapModeEnabled,
                hideConsoleButtonEnabled = prefs[Keys.HIDE_CONSOLE_BUTTON_ENABLED] ?: AppPreferences().hideConsoleButtonEnabled,
                hideFakeSleepButtonEnabled = prefs[Keys.HIDE_FAKE_SLEEP_BUTTON_ENABLED] ?: AppPreferences().hideFakeSleepButtonEnabled,
                hideIconRowInLandscapeEnabled = prefs[Keys.HIDE_ICON_ROW_IN_LANDSCAPE_ENABLED] ?: AppPreferences().hideIconRowInLandscapeEnabled,
                hideLastSessionLabelInLandscapeEnabled = prefs[Keys.HIDE_LAST_SESSION_LABEL_IN_LANDSCAPE_ENABLED] ?: AppPreferences().hideLastSessionLabelInLandscapeEnabled,
                landscapeStretchTransportRowEnabled = prefs[Keys.LANDSCAPE_STRETCH_TRANSPORT_ROW_ENABLED] ?: AppPreferences().landscapeStretchTransportRowEnabled,
                autoRestartOnCrashEnabled = prefs[Keys.AUTO_RESTART_ON_CRASH_ENABLED] ?: AppPreferences().autoRestartOnCrashEnabled,
                fakeSleepSingleTapWakeEnabled = prefs[Keys.FAKE_SLEEP_SINGLE_TAP_WAKE_ENABLED] ?: AppPreferences().fakeSleepSingleTapWakeEnabled,
                maskTrackTransitionFlashEnabled = prefs[Keys.MASK_TRACK_TRANSITION_FLASH_ENABLED] ?: AppPreferences().maskTrackTransitionFlashEnabled,
                gestureControlsEnabled = prefs[Keys.GESTURE_CONTROLS_ENABLED] ?: AppPreferences().gestureControlsEnabled,
                gestureHapticIntensity = prefs[Keys.GESTURE_HAPTIC_INTENSITY] ?: AppPreferences().gestureHapticIntensity,
                gestureTransitionShowConsoleEnabled = prefs[Keys.GESTURE_TRANSITION_SHOW_CONSOLE_ENABLED] ?: AppPreferences().gestureTransitionShowConsoleEnabled,
                gestureTransitionRoundedCoverEnabled = prefs[Keys.GESTURE_TRANSITION_ROUNDED_COVER_ENABLED] ?: AppPreferences().gestureTransitionRoundedCoverEnabled,
                gestureControlsFullScreenEnabled = prefs[Keys.GESTURE_CONTROLS_FULL_SCREEN_ENABLED] ?: AppPreferences().gestureControlsFullScreenEnabled,
            )
            val updated = transform(existing)
            prefs[Keys.KEEP_SCREEN_ON] = updated.keepScreenOn
            prefs[Keys.HOLD_WAKE_LOCK] = updated.holdWakeLock
            prefs[Keys.AUTOSTART_ON_BOOT] = updated.autostartOnBoot
            prefs[Keys.VOLUME_SLIDER_ENABLED] = updated.volumeSliderEnabled
            prefs[Keys.VOLUME_SLIDER_LANDSCAPE_ONLY] = updated.volumeSliderLandscapeOnly
            prefs[Keys.LANDSCAPE_LEFT_ALIGN_TRACK_INFO_ENABLED] = updated.landscapeLeftAlignTrackInfoEnabled
            prefs[Keys.NERD_MODE_ENABLED] = updated.nerdModeEnabled
            prefs[Keys.THEME_MODE] = updated.themeMode.wireValue
            prefs[Keys.DASHBOARD_BACKGROUND_STYLE] = updated.dashboardBackgroundStyle.wireValue
            prefs[Keys.FULLSCREEN_MODE_ENABLED] = updated.fullscreenModeEnabled
            prefs[Keys.AUTO_SLEEP_ON_IDLE_ENABLED] = updated.autoSleepOnIdleEnabled
            prefs[Keys.AUTO_SLEEP_IDLE_DELAY_SECONDS] = updated.autoSleepIdleDelaySeconds
            prefs[Keys.AUTO_SLEEP_NAP_MODE_ENABLED] = updated.autoSleepNapModeEnabled
            prefs[Keys.HIDE_CONSOLE_BUTTON_ENABLED] = updated.hideConsoleButtonEnabled
            prefs[Keys.HIDE_FAKE_SLEEP_BUTTON_ENABLED] = updated.hideFakeSleepButtonEnabled
            prefs[Keys.HIDE_ICON_ROW_IN_LANDSCAPE_ENABLED] = updated.hideIconRowInLandscapeEnabled
            prefs[Keys.HIDE_LAST_SESSION_LABEL_IN_LANDSCAPE_ENABLED] = updated.hideLastSessionLabelInLandscapeEnabled
            prefs[Keys.LANDSCAPE_STRETCH_TRANSPORT_ROW_ENABLED] = updated.landscapeStretchTransportRowEnabled
            prefs[Keys.AUTO_RESTART_ON_CRASH_ENABLED] = updated.autoRestartOnCrashEnabled
            prefs[Keys.FAKE_SLEEP_SINGLE_TAP_WAKE_ENABLED] = updated.fakeSleepSingleTapWakeEnabled
            prefs[Keys.MASK_TRACK_TRANSITION_FLASH_ENABLED] = updated.maskTrackTransitionFlashEnabled
            prefs[Keys.GESTURE_CONTROLS_ENABLED] = updated.gestureControlsEnabled
            prefs[Keys.GESTURE_HAPTIC_INTENSITY] = updated.gestureHapticIntensity
            prefs[Keys.GESTURE_TRANSITION_SHOW_CONSOLE_ENABLED] = updated.gestureTransitionShowConsoleEnabled
            prefs[Keys.GESTURE_TRANSITION_ROUNDED_COVER_ENABLED] = updated.gestureTransitionRoundedCoverEnabled
            prefs[Keys.GESTURE_CONTROLS_FULL_SCREEN_ENABLED] = updated.gestureControlsFullScreenEnabled
        }
    }
}
