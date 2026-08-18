package com.example.android_go_librespot.data

/** Spotify Connect device type, mirrors the `device_type` enum in go-librespot's config_schema.json. */
enum class DeviceType(val wireValue: String, val label: String) {
    COMPUTER("computer", "Computer"),
    TABLET("tablet", "Tablet"),
    SMARTPHONE("smartphone", "Smartphone"),
    SPEAKER("speaker", "Speaker"),
    TV("tv", "TV"),
    AVR("avr", "AVR"),
    STB("stb", "Set-top box"),
    AUDIO_DONGLE("audio_dongle", "Audio dongle"),
    GAME_CONSOLE("game_console", "Game console"),
    CAST_VIDEO("cast_video", "Chromecast (video)"),
    CAST_AUDIO("cast_audio", "Chromecast (audio)"),
    AUTOMOBILE("automobile", "Automobile"),
    SMARTWATCH("smartwatch", "Smartwatch"),
    CHROMEBOOK("chromebook", "Chromebook"),
    CAR_THING("car_thing", "Car Thing"),
    OBSERVER("observer", "Observer"),
    HOME_THING("home_thing", "Home thing");

    companion object {
        fun fromWireValue(value: String): DeviceType = entries.firstOrNull { it.wireValue == value } ?: SPEAKER
    }
}

/** How this daemon instance authenticates to Spotify, mirrors `credentials.type`. */
enum class CredentialsType(val wireValue: String, val label: String) {
    ZEROCONF("zeroconf", "Zeroconf (discover from Spotify app)"),
    INTERACTIVE("interactive", "Interactive login (browser OAuth)"),
    SPOTIFY_TOKEN("spotify_token", "Cached Spotify access token");

    companion object {
        fun fromWireValue(value: String): CredentialsType = entries.firstOrNull { it.wireValue == value } ?: ZEROCONF
    }
}

/**
 * The subset of go-librespot's config.yml that this app exposes. Field names track
 * the daemon's koanf keys (see cmd/daemon/cli_config.go in SEKY443/go-librespot-termux)
 * so [com.example.android_go_librespot.service.GoLibrespotConfigWriter] can serialize
 * them 1:1. Audio backend, audio device, and mixer settings are deliberately absent:
 * the Android build always runs with `audio_backend: pipe` into a FIFO consumed by
 * [com.example.android_go_librespot.service.PipeAudioPlayer] via AudioTrack.
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
