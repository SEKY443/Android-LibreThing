package io.github.seky443.librething.util

import android.content.Context
import java.io.File

/**
 * Filesystem/network layout shared by the process launcher, the config writer, and the
 * pipe audio player. The daemon binary is shipped as a per-ABI jniLibs entry named
 * `libgolibrespot.so` purely so Android's package installer extracts it next to the
 * real native libraries with the execute bit set (see app/build.gradle.kts packaging
 * block) -- it is a plain ELF executable, not a shared library.
 */
object GoLibrespotPaths {
    /** Fixed loopback port for the daemon's REST + WebSocket API (`server.port`). */
    const val API_PORT = 24_879
    const val API_HOST = "127.0.0.1"

    fun configDir(context: Context): File = File(context.filesDir, "golibrespot").apply { mkdirs() }

    fun cacheDir(context: Context): File = File(context.cacheDir, "golibrespot-audio-cache").apply { mkdirs() }

    /** Total size in bytes of everything currently in [cacheDir], walked recursively. */
    fun cacheDirSizeBytes(context: Context): Long =
        cacheDir(context).walkTopDown().filter { it.isFile }.sumOf { it.length() }

    /** Deletes everything under [cacheDir], leaving the directory itself in place (the daemon
     * expects it to already exist when it starts) -- see AppPreferences.autoClearCacheEnabled. */
    fun clearCacheDir(context: Context) {
        cacheDir(context).listFiles()?.forEach { it.deleteRecursively() }
    }

    fun audioPipe(context: Context): File = File(configDir(context), "audio.pipe")

    /** Rotating on-disk mirror of [io.github.seky443.librething.service.SpotifyConnectServiceState]'s
     * log flow -- see [io.github.seky443.librething.service.DaemonLogFile]. Survives across
     * restarts and process death, unlike that in-memory flow or logcat's own ring buffer, so an
     * intermittent crash or hang days apart is still diagnosable after the fact. */
    fun logFile(context: Context): File = File(configDir(context), "daemon.log")

    /** One rotated-out backup of [logFile], kept so a crash that happens right after rotation
     * isn't lost. */
    fun previousLogFile(context: Context): File = File(configDir(context), "daemon.log.1")

    fun daemonBinary(context: Context): File =
        File(context.applicationInfo.nativeLibraryDir, "libgolibrespot.so")
}
