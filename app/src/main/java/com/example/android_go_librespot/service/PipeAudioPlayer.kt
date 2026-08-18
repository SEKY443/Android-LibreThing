package com.example.android_go_librespot.service

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

    private fun readLoop() {
        val fd: FileDescriptor
        try {
            fd = Os.open(pipeFile.absolutePath, OsConstants.O_RDONLY or OsConstants.O_NONBLOCK, 0)
        } catch (e: Exception) {
            onError("Failed to open audio pipe: ${e.message}")
            running = false
            return
        }

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
            .setBufferSizeInBytes(minBufferBytes * 4)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        val buffer = ByteArray(8192)
        try {
            audioTrack.play()
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
                if (n < 0) break
                if (n == 0) {
                    // Writer end closed (daemon process exited); nothing left to read.
                    break
                }
                audioTrack.write(buffer, 0, n)
            }
        } catch (e: Exception) {
            onError("Audio pipe read error: ${e.message}")
        } finally {
            runCatching { audioTrack.stop() }
            audioTrack.release()
            runCatching { Os.close(fd) }
        }
    }
}
