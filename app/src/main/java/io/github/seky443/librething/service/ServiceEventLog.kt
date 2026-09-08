package io.github.seky443.librething.service

import android.content.Context
import io.github.seky443.librething.util.GoLibrespotPaths
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.io.Writer
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * A tiny, on-disk log of just [SpotifyConnectService]'s own lifecycle/restart events (service
 * start/stop, daemon process exit, restart attempts, giving up on auto-restart) -- NOT the
 * daemon's own chatty debug-level output (that was tried once as a `DaemonLogFile` mirroring
 * every log line and removed before release: writing the daemon's full debug log continuously
 * has no place in a release build). This is orders of magnitude smaller and exists for one
 * purpose: proving which recovery path (if any) actually fired the next time the daemon dies but
 * the service is somehow left showing stale state instead of either restarting or surfacing an
 * error -- neither logcat's ring buffer nor [SpotifyConnectServiceState]'s in-memory log survive
 * long enough to catch that after the fact.
 */
internal class ServiceEventLog(context: Context) {
    private val logFile = GoLibrespotPaths.serviceEventLogFile(context)
    private val dateFormat = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)
    private var writer: Writer? = null

    @Synchronized
    fun append(message: String) {
        if (logFile.length() > MAX_FILE_BYTES) truncate()
        val line = "${dateFormat.format(System.currentTimeMillis())} $message\n"
        runCatching {
            val out = writer ?: OutputStreamWriter(FileOutputStream(logFile, true)).also { writer = it }
            out.write(line)
            out.flush()
        }.onFailure {
            // The open writer (if any) may now be pointed at a file in a bad state -- drop it so
            // the next append reopens fresh instead of failing forever.
            runCatching { writer?.close() }
            writer = null
        }
    }

    @Synchronized
    fun close() {
        runCatching { writer?.close() }
        writer = null
    }

    private fun truncate() {
        runCatching { writer?.close() }
        writer = null
        runCatching { logFile.delete() }
    }

    private companion object {
        // Lifecycle-only lines are rare -- even a bad crash loop produces at most a few per
        // minute -- so 1MB (tens of thousands of lines) is a safety net against a truly
        // pathological loop, not a limit meant to ever be hit from ordinary use over many days.
        // A single truncate-and-restart is enough here, unlike a full debug log's
        // rotation-with-one-backup: losing lifecycle history only matters if this cap is
        // actually reached, which itself would mean something is already very wrong.
        const val MAX_FILE_BYTES = 1L * 1024 * 1024
    }
}
