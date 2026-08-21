package io.github.seky443.librething.service

import android.content.Context
import io.github.seky443.librething.data.GoLibrespotConfig
import io.github.seky443.librething.service.model.LogEntry
import io.github.seky443.librething.service.model.LogLevel
import io.github.seky443.librething.util.GoLibrespotPaths
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

    fun start(config: GoLibrespotConfig, initialVolumeSteps: Int) {
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

        val configFile = GoLibrespotConfigWriter.write(context, config, initialVolumeSteps)

        val builder = ProcessBuilder(
            binary.absolutePath,
            "--config_dir", GoLibrespotPaths.configDir(context).absolutePath,
        ).apply {
            redirectErrorStream(true)
            directory(GoLibrespotPaths.configDir(context))
            // Go's os.UserConfigDir() is evaluated unconditionally while the daemon computes
            // the *default* value for --config_dir (before it even looks at the override
            // above), and it hard-fails if neither $HOME nor $XDG_CONFIG_HOME is set -- which
            // is always true for an Android app's process environment. The value is never
            // actually used since we always pass --config_dir explicitly.
            environment()["HOME"] = GoLibrespotPaths.configDir(context).absolutePath
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

    internal companion object {
        // logrus TextFormatter, non-TTY output: time="..." level=info msg="..." [key=value ...]
        private val FIELD_REGEX = Regex("""(\w+)=(?:"((?:[^"\\]|\\.)*)"|(\S*))""")

        internal fun parseLogLine(line: String): LogEntry {
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
            val baseMessage = fields["msg"] ?: line
            // logrus's .WithError(err) attaches the underlying cause as a separate
            // "error" field rather than folding it into msg; surface it so failures like
            // "failed loading config" are actually actionable from the log console.
            val message = fields["error"]?.let { "$baseMessage: $it" } ?: baseMessage
            return LogEntry(level, message)
        }
    }
}
