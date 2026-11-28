package io.github.seky443.librething.service

import kotlin.math.roundToInt

/**
 * Conversions between the daemon's volume scale (0..[REMOTE_STEPS], always the `volume_steps` this
 * app writes into the daemon's config) and the device's `STREAM_MUSIC` index (0..`deviceMax`, which
 * differs per phone and per output: 15 on many devices, 25 on a Pixel, up to 150 on some Samsungs).
 *
 * The remote scale is a constant on purpose: the daemon clamps `POST /player/volume` to its
 * configured step count, so anything computed against another max (an unset 65535 default, say)
 * would pin Spotify to 100%.
 */
internal object VolumeMath {
    const val REMOTE_STEPS = GoLibrespotConfigWriter.VOLUME_STEPS

    fun remoteToDevice(remote: Int, deviceMax: Int): Int =
        ((remote.toDouble() / REMOTE_STEPS) * deviceMax).roundToInt().coerceIn(0, deviceMax)

    fun deviceToRemote(device: Int, deviceMax: Int): Int =
        ((device.toDouble() / deviceMax) * REMOTE_STEPS).roundToInt().coerceIn(0, REMOTE_STEPS)

    /**
     * True when [device] and [remote] already describe the same volume, in *either* direction.
     * Comparing in the coarser device domain stops an echo of our own write from being mistaken for
     * a fresh change (and re-pushed, drifting the volume by a rounding step each time); checking the
     * other direction too keeps that stable above 100 device steps, where one direction alone
     * doesn't round-trip.
     */
    fun isInSync(device: Int, remote: Int, deviceMax: Int): Boolean =
        remoteToDevice(remote, deviceMax) == device || deviceToRemote(device, deviceMax) == remote
}
