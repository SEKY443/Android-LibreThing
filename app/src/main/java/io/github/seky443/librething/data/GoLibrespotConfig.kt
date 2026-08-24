package io.github.seky443.librething.data

import androidx.annotation.StringRes
import io.github.seky443.librething.R

/** Spotify Connect device type, mirrors the `device_type` enum in go-librespot's config_schema.json.
 * [labelRes] rather than a plain label string so the UI can resolve it through [stringResource]
 * -- keeps every user-visible label translatable via strings.xml instead of hardcoded here. */
enum class DeviceType(val wireValue: String, @param:StringRes val labelRes: Int) {
    COMPUTER("computer", R.string.device_type_computer),
    TABLET("tablet", R.string.device_type_tablet),
    SMARTPHONE("smartphone", R.string.device_type_smartphone),
    SPEAKER("speaker", R.string.device_type_speaker),
    TV("tv", R.string.device_type_tv),
    AVR("avr", R.string.device_type_avr),
    STB("stb", R.string.device_type_stb),
    AUDIO_DONGLE("audio_dongle", R.string.device_type_audio_dongle),
    GAME_CONSOLE("game_console", R.string.device_type_game_console),
    CAST_VIDEO("cast_video", R.string.device_type_cast_video),
    CAST_AUDIO("cast_audio", R.string.device_type_cast_audio),
    AUTOMOBILE("automobile", R.string.device_type_automobile),
    SMARTWATCH("smartwatch", R.string.device_type_smartwatch),
    CHROMEBOOK("chromebook", R.string.device_type_chromebook),
    CAR_THING("car_thing", R.string.device_type_car_thing),
    OBSERVER("observer", R.string.device_type_observer),
    HOME_THING("home_thing", R.string.device_type_home_thing);

    companion object {
        fun fromWireValue(value: String): DeviceType = entries.firstOrNull { it.wireValue == value } ?: SPEAKER
    }
}

/** How this daemon instance authenticates to Spotify, mirrors `credentials.type`. */
enum class CredentialsType(val wireValue: String, @param:StringRes val labelRes: Int) {
    ZEROCONF("zeroconf", R.string.credentials_type_zeroconf),
    INTERACTIVE("interactive", R.string.credentials_type_interactive),
    DEVICE_AUTH("device_auth", R.string.credentials_type_device_auth),
    SPOTIFY_TOKEN("spotify_token", R.string.credentials_type_spotify_token);

    companion object {
        fun fromWireValue(value: String): CredentialsType = entries.firstOrNull { it.wireValue == value } ?: ZEROCONF
    }
}

/**
 * The subset of go-librespot's config.yml that this app exposes. Field names track
 * the daemon's koanf keys (see cmd/daemon/cli_config.go in SEKY443/go-librespot-termux)
 * so [io.github.seky443.librething.service.GoLibrespotConfigWriter] can serialize
 * them 1:1. Audio backend, audio device, and mixer settings are deliberately absent:
 * the Android build always runs with `audio_backend: pipe` into a FIFO consumed by
 * [io.github.seky443.librething.service.PipeAudioPlayer] via AudioTrack.
 */
data class GoLibrespotConfig(
    val deviceName: String = "Android Speaker",
    val deviceType: DeviceType = DeviceType.SPEAKER,
    val bitrate: Int = 160,
    val normalisationDisabled: Boolean = false,
    val normalisationUseAlbumGain: Boolean = false,
    val normalisationPregain: Float = 0f,
    val disableAutoplay: Boolean = false,
    val zeroconfEnabled: Boolean = true,
    val zeroconfPort: Int = 0,
    val credentialsType: CredentialsType = CredentialsType.ZEROCONF,
    val spotifyTokenUsername: String = "",
    val spotifyTokenAccessToken: String = "",
    val optimisticPlaybackReplies: Boolean = false,
) {
    companion object {
        val BITRATE_OPTIONS = listOf(96, 160, 320)
    }
}
