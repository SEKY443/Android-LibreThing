package com.example.android_go_librespot.service

import com.example.android_go_librespot.data.CredentialsType
import com.example.android_go_librespot.data.DeviceType
import com.example.android_go_librespot.data.GoLibrespotConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GoLibrespotConfigWriterTest {

    private fun render(config: GoLibrespotConfig) =
        GoLibrespotConfigWriter.renderYaml(config, pipePath = "/data/audio.pipe", cacheDirPath = "/data/cache")

    @Test
    fun `pipe backend and loopback server are always forced regardless of user config`() {
        val yaml = render(GoLibrespotConfig())

        assertTrue(yaml.contains("audio_backend: \"pipe\""))
        assertTrue(yaml.contains("audio_output_pipe: \"/data/audio.pipe\""))
        assertTrue(yaml.contains("audio_output_pipe_format: \"s16le\""))
        assertTrue(yaml.contains("audio_output_pipe_wait_for_reader: true"))
        assertTrue(yaml.contains("server:"))
        assertTrue(yaml.contains("enabled: true"))
        assertTrue(yaml.contains("address: \"127.0.0.1\""))
    }

    @Test
    fun `firewall-friendly access point ports are always preferred`() {
        // Mobile networks, NATs, and the Android emulator commonly block the access point's
        // default port 4070; this makes the daemon try 443/80 first instead of getting stuck.
        val yaml = render(GoLibrespotConfig())

        assertTrue(yaml.contains("prefer_firewall_friendly_ports: true"))
    }

    @Test
    fun `device name and type round-trip into yaml`() {
        val yaml = render(GoLibrespotConfig(deviceName = "Living Room", deviceType = DeviceType.AVR))

        assertTrue(yaml.contains("device_name: \"Living Room\""))
        assertTrue(yaml.contains("device_type: \"avr\""))
    }

    @Test
    fun `device name with quotes and backslashes is escaped, not left to break the yaml`() {
        val yaml = render(GoLibrespotConfig(deviceName = "Bob's \"Speaker\"\\thing"))

        val line = yaml.lines().first { it.startsWith("device_name:") }
        assertEquals("device_name: \"Bob's \\\"Speaker\\\"\\\\thing\"", line)
    }

    @Test
    fun `spotify token fields are only emitted when that credentials type is selected`() {
        val zeroconfYaml = render(GoLibrespotConfig(credentialsType = CredentialsType.ZEROCONF))
        assertFalse(zeroconfYaml.contains("spotify_token:"))

        val tokenYaml = render(
            GoLibrespotConfig(
                credentialsType = CredentialsType.SPOTIFY_TOKEN,
                spotifyTokenUsername = "someone",
                spotifyTokenAccessToken = "secret-token",
            )
        )
        assertTrue(tokenYaml.contains("spotify_token:"))
        assertTrue(tokenYaml.contains("username: \"someone\""))
        assertTrue(tokenYaml.contains("access_token: \"secret-token\""))
    }

    @Test
    fun `bitrate and normalisation values are emitted verbatim`() {
        val yaml = render(
            GoLibrespotConfig(
                bitrate = 320,
                normalisationDisabled = true,
                normalisationUseAlbumGain = true,
                normalisationPregain = -3.5f,
            )
        )

        assertTrue(yaml.contains("bitrate: 320"))
        assertTrue(yaml.contains("normalisation_disabled: true"))
        assertTrue(yaml.contains("normalisation_use_album_gain: true"))
        assertTrue(yaml.contains("normalisation_pregain: -3.5"))
    }
}
