package io.github.seky443.librething.service

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.system.StructPollfd
import java.io.File
import java.io.FileDescriptor
import kotlin.concurrent.thread

/**
 * Reads raw s16le/44100Hz/stereo PCM from the FIFO that the go-librespot daemon writes to
 * (`audio_backend: pipe`, see [GoLibrespotConfigWriter]) and streams it into an [AudioTrack].
 * This is the Android-native replacement for the Linux ALSA/PulseAudio backends: the daemon
 * never touches Android audio APIs directly, it just writes PCM bytes to a named pipe.
 *
 * The FIFO's read end is opened non-blocking and polled with a short timeout so [stop] can
 * always return promptly, instead of leaving the reader thread blocked inside a `read()`
 * syscall on the pipe (closing a [java.io.FileInputStream]'s fd from another thread while a
 * blocking read is in progress is a race, not a guaranteed unblock).
 */
class PipeAudioPlayer(
    private val pipeFile: File,
    private val onError: (String) -> Unit = {},
) {
    private companion object {
        const val SAMPLE_RATE = 44_100
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_OUT_STEREO
        const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
        const val POLL_TIMEOUT_MS = 200
    }

    @Volatile private var running = false
    private var readerThread: Thread? = null

    /**
     * Creates the FIFO node synchronously (a single fast syscall) so it is guaranteed to
     * exist before the caller launches the daemon process, then starts the background
     * reader thread. The daemon opens the pipe for writing in blocking mode and will fail
     * immediately with ENOENT if the node isn't there yet.
     */
    fun start() {
        if (running) return
        prepareFifo()
        running = true
        readerThread = thread(name = "pipe-audio-reader", isDaemon = true) { readLoop() }
    }

    fun stop() {
        running = false
        readerThread?.join(POLL_TIMEOUT_MS * 2L)
        readerThread = null
    }

    private fun prepareFifo() {
        if (pipeFile.exists()) pipeFile.delete()
        Os.mkfifo(pipeFile.absolutePath, OsConstants.S_IRUSR or OsConstants.S_IWUSR or OsConstants.S_IWGRP)
    }

    /**
     * The daemon closes its write end of the FIFO between playback sessions -- e.g. when this
     * device is switched away from in Spotify Connect -- and opens a fresh one when a session
     * resumes (`audio_output_pipe_wait_for_reader: true` makes its next `write()` block until a
     * reader is there to receive it). A `read()` of 0 is that close, not the daemon process
     * exiting: this loop reopens the FIFO and waits for the next writer instead of giving up,
     * which previously left no reader around to unblock that write ever again -- silently
     * hanging playback on every subsequent transfer back to this device after the first.
     */
    private fun readLoop() {
        val minBufferBytes = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, ENCODING).coerceAtLeast(4096)
        val audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(CHANNEL_CONFIG)
                    .setEncoding(ENCODING)
                    .build()
            )
            // 2x rather than 4x the platform minimum: daemon-side volume changes are baked
            // into PCM before it ever reaches this buffer (see driver-pipe.go), so whatever
            // is already sitting in here plays out at the old volume -- a smaller buffer
            // trades some underrun margin for a shorter worst-case volume-change delay.
            .setBufferSizeInBytes(minBufferBytes * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        val buffer = ByteArray(8192)
        try {
            audioTrack.play()
            while (running) {
                val fd = try {
                    Os.open(pipeFile.absolutePath, OsConstants.O_RDONLY or OsConstants.O_NONBLOCK, 0)
                } catch (e: Exception) {
                    onError("Failed to open audio pipe: ${e.message}")
                    break
                }
                try {
                    readSessionFromPipe(fd, audioTrack, buffer)
                } finally {
                    runCatching { Os.close(fd) }
                }
            }
        } catch (e: Exception) {
            onError("Audio pipe read error: ${e.message}")
        } finally {
            runCatching { audioTrack.stop() }
            audioTrack.release()
        }
    }

    /** Reads and plays one writer session's worth of PCM; returns on EOF (writer closed) or
     * when [running] goes false, either way leaving the fd for the caller to close. */
    private fun readSessionFromPipe(fd: FileDescriptor, audioTrack: AudioTrack, buffer: ByteArray) {
        val pollFd = StructPollfd().apply {
            this.fd = fd
            events = OsConstants.POLLIN.toShort()
        }
        while (running) {
            val readyCount = try {
                Os.poll(arrayOf(pollFd), POLL_TIMEOUT_MS)
            } catch (e: ErrnoException) {
                0
            }
            if (readyCount <= 0) continue

            val n = try {
                Os.read(fd, buffer, 0, buffer.size)
            } catch (e: ErrnoException) {
                if (e.errno == OsConstants.EAGAIN) continue else throw e
            }
            if (n < 0) return
            if (n == 0) return // Writer closed this session; caller reopens for the next one.
            audioTrack.write(buffer, 0, n)
        }
    }
}
