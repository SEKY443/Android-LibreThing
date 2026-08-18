package com.example.android_go_librespot.service

import android.content.Context
import com.example.android_go_librespot.data.CredentialsType
import com.example.android_go_librespot.data.GoLibrespotConfig
import com.example.android_go_librespot.util.GoLibrespotPaths
import java.io.File

/**
 * Renders [GoLibrespotConfig] plus the fixed Android-side wiring (pipe audio output,
 * loopback API server, on-disk cache) into the YAML the daemon expects at
 * `<config_dir>/config.yml`. The key set mirrors cmd/daemon/cli_config.go's koanf tags
 * in SEKY443/go-librespot-termux -- see GoLibrespotConfig's kdoc for the source of truth.
 *
 * Values are always double-quoted and escaped rather than pulled through a general YAML
 * library: the document shape is fixed and every value originates from either this app's
 * own enums or a DataStore-persisted string, so a small dedicated writer is both simpler
 * and has less surface area than a full YAML parser/emitter dependency would.
 */
object GoLibrespotConfigWriter {

    fun write(context: Context, config: GoLibrespotConfig): File {
        val configDir = GoLibrespotPaths.configDir(context)
        val pipe = GoLibrespotPaths.audioPipe(context)
        val cacheDir = GoLibrespotPaths.cacheDir(context)

        val yaml = renderYaml(config, pipePath = pipe.absolutePath, cacheDirPath = cacheDir.absolutePath)

        val configFile = File(configDir, "config.yml")
        configFile.writeText(yaml)
        return configFile
    }

    /**
     * Pure YAML rendering, split out from [write] so it's unit-testable without an Android
     * [Context] -- everything Context-dependent (paths) is resolved by the caller first.
     */
    internal fun renderYaml(config: GoLibrespotConfig, pipePath: String, cacheDirPath: String): String {
        return buildString {
            // Always capture every level from the daemon; the Dashboard's log console
            // filters client-side so the user can change the visible level without
            // restarting the process.
            appendLine("log_level: \"debug\"")
            appendLine("log_disable_timestamp: false")
            appendLine()
            appendLine("device_name: ${quote(config.deviceName)}")
            appendLine("device_type: ${quote(config.deviceType.wireValue)}")
            appendLine("bitrate: ${config.bitrate}")
            appendLine()
            // Mobile/carrier networks and NATs (and Android emulator networking) commonly
            // block outbound 4070, the access point's default port; trying 443/80 first has
            // no downside on networks that do allow 4070.
            appendLine("prefer_firewall_friendly_ports: true")
            appendLine()
            appendLine("audio_backend: \"pipe\"")
            appendLine("audio_output_pipe: ${quote(pipePath)}")
            appendLine("audio_output_pipe_format: \"s16le\"")
            appendLine("audio_output_pipe_wait_for_reader: true")
            appendLine()
            appendLine("normalisation_disabled: ${config.normalisationDisabled}")
            appendLine("normalisation_use_album_gain: ${config.normalisationUseAlbumGain}")
            appendLine("normalisation_pregain: ${config.normalisationPregain}")
            appendLine("disable_autoplay: ${config.disableAutoplay}")
            appendLine("optimistic_playback_replies: ${config.optimisticPlaybackReplies}")
            appendLine()
            appendLine("zeroconf_enabled: ${config.zeroconfEnabled}")
            appendLine("zeroconf_port: ${config.zeroconfPort}")
            appendLine("zeroconf_backend: \"builtin\"")
            appendLine()
            appendLine("credentials:")
            appendLine("  type: ${quote(config.credentialsType.wireValue)}")
            if (config.credentialsType == CredentialsType.SPOTIFY_TOKEN) {
                appendLine("  spotify_token:")
                appendLine("    username: ${quote(config.spotifyTokenUsername)}")
                appendLine("    access_token: ${quote(config.spotifyTokenAccessToken)}")
            }
            appendLine("  zeroconf:")
            appendLine("    persist_credentials: true")
            appendLine()
            appendLine("server:")
            appendLine("  enabled: true")
            appendLine("  address: ${quote(GoLibrespotPaths.API_HOST)}")
            appendLine("  port: ${GoLibrespotPaths.API_PORT}")
            appendLine()
            appendLine("cache:")
            appendLine("  enabled: true")
            appendLine("  dir: ${quote(cacheDirPath)}")
            appendLine("  size_limit: \"512MB\"")
        }
    }

    /** Double-quoted YAML scalar with backslashes/quotes escaped; safe for any plain string value. */
    private fun quote(value: String): String {
        val escaped = value.replace("\\", "\\\\").replace("\"", "\\\"")
        return "\"$escaped\""
    }
}
