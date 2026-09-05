package io.github.seky443.librething.service

import android.content.Context
import io.github.seky443.librething.service.model.LogEntry
import io.github.seky443.librething.util.GoLibrespotPaths
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.io.Writer
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Persists every [LogEntry] (daemon output plus this service's own restart/crash/health-check
 * markers) to a small rotating file on disk. [SpotifyConnectServiceState]'s in-memory log flow
 * only keeps the last 1000 lines, and logcat's own ring buffer typically rotates within minutes
 * on a busy device -- neither survives long enough to diagnose a crash or a Bluetooth disconnect
 * that only happens once every few days. This file does, and can be pulled with `adb pull`
 * afterward (see [GoLibrespotPaths.logFile]).
 *
 * Single-rotation rather than a numbered series: this is meant to catch an intermittent bug days
 * apart, not to be a full audit trail, so one backup is enough headroom for whatever was
 * happening right before the file most recently rotated. Flushed after every line (not just
 * buffered) since the whole point is surviving the exact crash that would otherwise take an
 * in-flight write down with it.
 */
internal class DaemonLogFile(context: Context) {
    private val logFile = GoLibrespotPaths.logFile(context)
    private val previousLogFile = GoLibrespotPaths.previousLogFile(context)
    private val dateFormat = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)
    private var writer: Writer? = null

    @Synchronized
    fun append(entry: LogEntry) {
        if (logFile.length() > MAX_FILE_BYTES) rotate()
        val line = "${dateFormat.format(entry.timestampMillis)} ${entry.level} ${entry.message}\n"
        runCatching {
            val out = writer ?: OutputStreamWriter(FileOutputStream(logFile, true)).also { writer = it }
            out.write(line)
            out.flush()
        }.onFailure {
            // The open writer (if any) may now be pointed at a file that no longer exists or is
            // in a bad state -- drop it so the next append reopens fresh instead of failing forever.
            runCatching { writer?.close() }
            writer = null
        }
    }

    @Synchronized
    fun close() {
        runCatching { writer?.close() }
        writer = null
    }

    private fun rotate() {
        runCatching { writer?.close() }
        writer = null
        runCatching {
            previousLogFile.delete()
            logFile.renameTo(previousLogFile)
        }
    }

    private companion object {
        const val MAX_FILE_BYTES = 5L * 1024 * 1024
    }
}
