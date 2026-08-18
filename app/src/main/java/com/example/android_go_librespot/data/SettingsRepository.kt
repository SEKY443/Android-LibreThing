package com.example.android_go_librespot.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
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
            )
            val updated = transform(existing)
            prefs[Keys.KEEP_SCREEN_ON] = updated.keepScreenOn
            prefs[Keys.HOLD_WAKE_LOCK] = updated.holdWakeLock
            prefs[Keys.AUTOSTART_ON_BOOT] = updated.autostartOnBoot
        }
    }
}
