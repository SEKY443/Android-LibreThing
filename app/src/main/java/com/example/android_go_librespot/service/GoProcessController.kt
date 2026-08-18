package com.example.android_go_librespot.service

import android.content.Context
import com.example.android_go_librespot.data.GoLibrespotConfig
import com.example.android_go_librespot.service.model.LogEntry
import com.example.android_go_librespot.service.model.LogLevel
import com.example.android_go_librespot.util.GoLibrespotPaths
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.concurrent.thread

/**
 * Launches and supervises the go-librespot daemon binary shipped in jniLibs (see
 * [GoLibrespotPaths.daemonBinary]) as a plain child process, and streams its merged
 * stdout/stderr (logrus text output) back as parsed [LogEntry] lines.
 */
class GoProcessController(
    private val context: Context,
    private val onLog: (LogEntry) -> Unit,
    private val onExit: (exitCode: Int) -> Unit,
) {
    private var process: Process? = null
    private var readerThread: Thread? = null

    val isRunning: Boolean get() = process?.isAlive == true

    fun start(config: GoLibrespotConfig) {
        if (isRunning) return

        val binary = GoLibrespotPaths.daemonBinary(context)
        if (!binary.exists()) {
            onLog(
                LogEntry(
                    LogLevel.ERROR,
                    "go-librespot binary not found at ${binary.absolutePath}. " +
                        "Build it with scripts/build-go-native.sh and rebuild the app.",
                )
            )
            onExit(-1)
            return
        }

        val configFile = GoLibrespotConfigWriter.write(context, config)

        val builder = ProcessBuilder(
            binary.absolutePath,
            "--config_dir", GoLibrespotPaths.configDir(context).absolutePath,
        ).apply {
            redirectErrorStream(true)
            directory(GoLibrespotPaths.configDir(context))
        }

        onLog(LogEntry(LogLevel.INFO, "Starting go-librespot with config ${configFile.absolutePath}"))

        val started = try {
            builder.start()
        } catch (e: Exception) {
            onLog(LogEntry(LogLevel.ERROR, "Failed to start go-librespot: ${e.message}"))
            onExit(-1)
            return
        }
        process = started

        readerThread = thread(name = "golibrespot-log-reader", isDaemon = true) {
            try {
                BufferedReader(InputStreamReader(started.inputStream)).use { reader ->
                    reader.forEachLine { line -> onLog(parseLogLine(line)) }
                }
            } catch (_: Exception) {
                // Reading ends when the process exits and closes its stdout; nothing to report.
            }
            val exitCode = try {
                started.waitFor()
            } catch (_: InterruptedException) {
                -1
            }
            onExit(exitCode)
        }
    }

    fun stop() {
        val running = process ?: return
        running.destroy()
        thread(isDaemon = true) {
            if (!running.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)) {
                running.destroyForcibly()
            }
        }
        process = null
        readerThread = null
    }

    private companion object {
        // logrus TextFormatter, non-TTY output: time="..." level=info msg="..." [key=value ...]
        val FIELD_REGEX = Regex("""(\w+)=(?:"((?:[^"\\]|\\.)*)"|(\S*))""")

        fun parseLogLine(line: String): LogEntry {
            val fields = FIELD_REGEX.findAll(line).associate { match ->
                val key = match.groupValues[1]
                val value = if (match.groups[2] != null) match.groupValues[2] else match.groupValues[3]
                key to value.replace("\\\"", "\"")
            }
            val level = when (fields["level"]?.lowercase()) {
                "debug", "trace" -> LogLevel.DEBUG
                "warning", "warn" -> LogLevel.WARN
                "error", "fatal", "panic" -> LogLevel.ERROR
                "info" -> LogLevel.INFO
                else -> LogLevel.INFO
            }
            val message = fields["msg"] ?: line
            return LogEntry(level, message)
        }
    }
}
