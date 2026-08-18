package com.example.android_go_librespot.service

import android.content.Context
import android.net.ConnectivityManager
import com.example.android_go_librespot.data.CredentialsType
import com.example.android_go_librespot.data.GoLibrespotConfig
import com.example.android_go_librespot.util.GoLibrespotPaths
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * Interface identity for the daemon's zeroconf/mDNS registration, resolved on the Android
 * side and passed into the generated config -- see [resolveAndroidNetInterface]'s kdoc for
 * why the daemon can't resolve this itself on Android.
 */
internal data class AndroidNetInterface(
    val name: String,
    val index: Int,
    val ip: String,
    val prefixLength: Int,
)

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

        val yaml = renderYaml(
            config,
            pipePath = pipe.absolutePath,
            cacheDirPath = cacheDir.absolutePath,
            androidNetInterface = resolveAndroidNetInterface(context),
        )

        val configFile = File(configDir, "config.yml")
        configFile.writeText(yaml)
        return configFile
    }

    /**
     * Resolves the active network interface's name, OS interface index, and IPv4
     * address/prefix via `ConnectivityManager`/`java.net.NetworkInterface`.
     *
     * The daemon's own zeroconf code can't do this on-device: Go's `net.Interfaces()` /
     * `net.InterfaceByName()` are netlink-only on Android, and Android's SELinux policy
     * denies untrusted apps `bind()` on `netlink_route_socket`; the same is true of reading
     * `/sys/class/net` or files under `/proc/net` directly (also SELinux-denied for untrusted apps --
     * confirmed via `adb logcat`'s `avc: denied` entries and direct `permission denied`
     * errors on-device, not just documentation). `ConnectivityManager` and `NetworkInterface`
     * work fine in the exact same process/SELinux context, though, since they're serviced by
     * `system_server` over Binder rather than a raw netlink/sysfs read from app userspace --
     * confirmed on-device (Pixel 6 emulator, API 37): both APIs return real interface/address
     * data (`wlan0`, index 16, `10.0.2.16/24`) where the Go-side equivalents fail. So the
     * app resolves this once per daemon launch and hands it down via new `android_net_*`
     * config fields, instead of the daemon trying (and failing) to discover it itself.
     */
    internal fun resolveAndroidNetInterface(context: Context): AndroidNetInterface? {
        return try {
            val connectivityManager =
                context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return null
            val network = connectivityManager.activeNetwork ?: return null
            val linkProperties = connectivityManager.getLinkProperties(network) ?: return null
            val ifaceName = linkProperties.interfaceName ?: return null
            val ipv4Address = linkProperties.linkAddresses.firstOrNull { it.address is Inet4Address } ?: return null
            val ifaceIndex = NetworkInterface.getByName(ifaceName)?.index ?: return null
            val hostAddress = ipv4Address.address.hostAddress ?: return null

            AndroidNetInterface(
                name = ifaceName,
                index = ifaceIndex,
                ip = hostAddress,
                prefixLength = ipv4Address.prefixLength,
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Pure YAML rendering, split out from [write] so it's unit-testable without an Android
     * [Context] -- everything Context-dependent (paths, network interface lookup) is
     * resolved by the caller first.
     */
    internal fun renderYaml(
        config: GoLibrespotConfig,
        pipePath: String,
        cacheDirPath: String,
        androidNetInterface: AndroidNetInterface? = null,
    ): String {
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
            if (androidNetInterface != null) {
                appendLine("android_net_iface_name: ${quote(androidNetInterface.name)}")
                appendLine("android_net_iface_index: ${androidNetInterface.index}")
                appendLine("android_net_ip: ${quote(androidNetInterface.ip)}")
                appendLine("android_net_prefix_len: ${androidNetInterface.prefixLength}")
            }
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
