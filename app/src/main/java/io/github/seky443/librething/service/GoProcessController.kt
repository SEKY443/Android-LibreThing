package io.github.seky443.librething.service

import android.content.Context
import android.system.Os
import android.system.OsConstants
import io.github.seky443.librething.data.GoLibrespotConfig
import io.github.seky443.librething.service.model.LogEntry
import io.github.seky443.librething.service.model.LogLevel
import io.github.seky443.librething.util.GoLibrespotPaths
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit
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

    // Set by stop() before it destroys the process, so the reader thread below can tell "we
    // killed this on purpose" apart from a genuine unexpected exit -- destroy() sends SIGTERM,
    // which go-librespot handles by exiting cleanly (code 0), and without this flag that looked
    // identical to onExit's caller as a real self-initiated clean exit. SpotifyConnectService
    // used to treat exit code 0 as "the daemon quit on its own for a good reason, stop the whole
    // service" -- which fired for real during a health-check-triggered restart: stop() (from
    // teardownDaemonProcess) killed the old process, this same reader thread observed the code-0
    // exit, and handleProcessExit(0) called stopSelf() before launchDaemon() ever got a chance to
    // start a new one, turning a routine restart into a full outage until Android eventually
    // restarted the service on its own.
    @Volatile private var stopRequested = false

    val isRunning: Boolean get() = process?.isAlive == true

    fun start(config: GoLibrespotConfig, initialVolumeSteps: Int) {
        if (isRunning) return
        stopRequested = false

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
            if (!stopRequested) onExit(exitCode)
        }
    }

    fun stop() {
        val running = process ?: return
        stopRequested = true
        running.destroy()
        thread(isDaemon = true) {
            if (!running.waitFor(3, TimeUnit.SECONDS)) {
                killForcibly(running)
            }
        }
        process = null
        readerThread = null
    }

    /**
     * [Process.destroyForcibly] is unreliable on Android -- observed on-device (a custom
     * AOSP-based ROM, API 30) to leave a process that's ignoring `SIGTERM` alive indefinitely
     * instead of actually forcing termination, where a plain shell `kill -9` on the same pid
     * killed it instantly. Sending a real `SIGKILL` via [Os.kill] is what "forcibly" is supposed
     * to mean. The pid comes via reflection on Android's own `ProcessImpl.pid` field -- the
     * traditional way to get a subprocess's pid on Android, predating (and, on this toolchain,
     * more reliable to depend on than) the public `Process.pid()` API -- falling back to
     * [Process.destroyForcibly] only if that field is ever absent/renamed.
     */
    private fun killForcibly(process: Process) {
        val pid = runCatching {
            val field = process.javaClass.getDeclaredField("pid")
            field.isAccessible = true
            field.getInt(process)
        }.getOrNull()
        if (pid != null) {
            runCatching { Os.kill(pid, OsConstants.SIGKILL) }
        } else {
            process.destroyForcibly()
        }
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
