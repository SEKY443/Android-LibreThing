package io.github.seky443.librething.service

import android.content.Context
import android.net.ConnectivityManager
import io.github.seky443.librething.data.CredentialsType
import io.github.seky443.librething.data.GoLibrespotConfig
import io.github.seky443.librething.util.GoLibrespotPaths
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

    /** volume_steps is pinned to a fixed value (rather than left at the daemon's own default)
     * so callers can convert a 0f..1f fraction into [initialVolumeSteps] deterministically. */
    const val VOLUME_STEPS = 100

    fun write(context: Context, config: GoLibrespotConfig, initialVolumeSteps: Int): File {
        val configDir = GoLibrespotPaths.configDir(context)
        val pipe = GoLibrespotPaths.audioPipe(context)
        val cacheDir = GoLibrespotPaths.cacheDir(context)

        val yaml = renderYaml(
            config,
            pipePath = pipe.absolutePath,
            cacheDirPath = cacheDir.absolutePath,
            androidNetInterface = resolveAndroidNetInterface(context),
            initialVolumeSteps = initialVolumeSteps,
            externalVolume = !DeviceVolumeBridge.isVolumeFixed(context),
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
        initialVolumeSteps: Int = VOLUME_STEPS,
        externalVolume: Boolean = true,
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
            // Overrides the line above when set (daemon-side), so a network that intercepts 443
            // but passes 80 can say so. Ints only -- see GoLibrespotConfig.parsePorts.
            if (config.accesspointPorts.isNotEmpty()) {
                appendLine("accesspoint_ports: [${config.accesspointPorts.joinToString(", ")}]")
            }
            appendLine()
            appendLine("audio_backend: \"pipe\"")
            appendLine("audio_output_pipe: ${quote(pipePath)}")
            appendLine("audio_output_pipe_format: \"s16le\"")
            appendLine("audio_output_pipe_wait_for_reader: true")
            appendLine()
            // The phone's STREAM_MUSIC is the one and only gain stage: with external_volume the
            // daemon leaves the audio untouched (output/driver-pipe.go) and just reports every
            // volume change, which DeviceVolumeBridge mirrors onto the stream -- and vice versa,
            // so a Bluetooth speaker's buttons, the phone's keys, the in-app slider and other
            // Spotify clients all move the same value. Without it both scaled the same audio and
            // the two numbers only roughly tracked each other.
            //
            // An older fork build hung the daemon's event loop at "put connect state because
            // NEW_DEVICE" on reconnect under this setting, which is why it was once reverted;
            // upstream has since moved state pushes off that loop and fixed external-volume
            // sessions (go-librespot-termux 548e9d1). Not used when the output has fixed volume
            // (STREAM_MUSIC changes nothing there), where the daemon has to do the scaling itself.
            appendLine("external_volume: $externalVolume")
            appendLine("volume_steps: $VOLUME_STEPS")
            appendLine("initial_volume: ${initialVolumeSteps.coerceIn(0, VOLUME_STEPS)}")
            // daemon/player.go re-runs its NEW_DEVICE volume-init logic on *every* Connect
            // session, not just process startup -- switching this device away and back within
            // the same still-running daemon process creates a brand new session each time. With
            // ignore_last_volume true, every one of those reapplies initial_volume above, which
            // is a snapshot frozen at process launch -- so any volume change made mid-session
            // (a real one, or a stale/zero one from a previous run) gets replayed on every
            // reconnect for that process's whole lifetime, audibly resetting the volume each
            // time. Leaving this false lets the daemon prefer its own state.LastVolume instead,
            // which it keeps durably up to date on every real volume change -- initial_volume
            // above still applies as the fallback for a genuinely fresh install/state (see
            // GoLibrespotConfigWriter.write's kdoc).
            appendLine("ignore_last_volume: false")
            appendLine()
            appendLine("normalisation_disabled: ${config.normalisationDisabled}")
            appendLine("normalisation_use_album_gain: ${config.normalisationUseAlbumGain}")
            appendLine("normalisation_pregain: ${config.normalisationPregain}")
            appendLine("disable_autoplay: ${config.disableAutoplay}")
            appendLine("optimistic_playback_replies: ${config.optimisticPlaybackReplies}")
            appendLine("device_is_group: ${config.deviceIsGroup}")
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
            // Without this the daemon only knows a queue entry's URI, never its title or artist:
            // /player/queue's track metadata is filled from this cache, so the queue sheet would
            // sit on "Loading..." forever. Off, the daemon makes no metadata request that
            // playback itself doesn't need; on, it batch-fetches the tracks around the playback
            // position in the background.
            appendLine("metadata:")
            appendLine("  enabled: true")
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
