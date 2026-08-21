package io.github.seky443.librething.service

import android.net.Uri
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.SimpleBasePlayer.MediaItemData
import androidx.media3.common.SimpleBasePlayer.PositionSupplier
import io.github.seky443.librething.service.model.ConnectionState
import io.github.seky443.librething.service.model.TrackInfo
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * A [SimpleBasePlayer] that wraps [SpotifyConnectServiceState] instead of decoding media itself,
 * so its [androidx.media3.session.MediaSession] can route system/hardware media-button and
 * Bluetooth AVRCP transport commands (play, pause, next, previous, seek) into the same
 * [GoLibrespotApiClient] calls the Dashboard UI already uses. Must be constructed and released
 * on [applicationLooper] (the main looper, since [SpotifyConnectServiceState]'s flows are
 * collected on [scope]'s default -- Main -- dispatcher).
 */
internal class GoLibrespotPlayer(
    looper: Looper,
    private val scope: CoroutineScope,
) : SimpleBasePlayer(looper) {

    private data class Snapshot(val track: TrackInfo?, val isPlaying: Boolean)

    @Volatile private var snapshot = Snapshot(track = null, isPlaying = false)

    private val observerJob: Job = scope.launch {
        combine(SpotifyConnectServiceState.connectionState, SpotifyConnectServiceState.nowPlaying) { state, track ->
            Snapshot(track = track, isPlaying = state is ConnectionState.Playing)
        }.collect {
            snapshot = it
            invalidateState()
        }
    }

    override fun getState(): State {
        val (track, isPlaying) = snapshot

        val commands = Player.Commands.Builder()
            .addAll(
                Player.COMMAND_PLAY_PAUSE,
                Player.COMMAND_SEEK_TO_NEXT,
                Player.COMMAND_SEEK_TO_PREVIOUS,
                Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
                Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
                Player.COMMAND_GET_TIMELINE,
                Player.COMMAND_GET_METADATA,
                Player.COMMAND_RELEASE,
            )
            .build()

        val playlist = if (track != null) listOf(toMediaItemData(track)) else emptyList()

        return State.Builder()
            .setAvailableCommands(commands)
            .setPlaybackState(if (track != null) Player.STATE_READY else Player.STATE_IDLE)
            .setPlayWhenReady(isPlaying, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .setPlaylist(playlist)
            .setCurrentMediaItemIndex(0)
            .setContentPositionMs(PositionSupplier.getExtrapolating(track?.positionMs ?: 0L, if (isPlaying) 1f else 0f))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build()
            )
            .build()
    }

    private fun toMediaItemData(track: TrackInfo): MediaItemData {
        val metadata = MediaMetadata.Builder()
            .setTitle(track.name)
            .setArtist(track.artistNames.joinToString(", "))
            .setAlbumTitle(track.albumName)
            .apply { track.albumCoverUrl?.let { setArtworkUri(Uri.parse(it)) } }
            .build()
        val mediaItem = MediaItem.Builder()
            .setMediaId(track.uri)
            .setMediaMetadata(metadata)
            .build()
        return MediaItemData.Builder(track.uri)
            .setMediaItem(mediaItem)
            .setDurationUs(track.durationMs * 1000)
            .build()
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> = runOnIo {
        if (playWhenReady) SpotifyConnectServiceState.resume() else SpotifyConnectServiceState.pause()
    }

    override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> = runOnIo {
        when (seekCommand) {
            Player.COMMAND_SEEK_TO_NEXT -> SpotifyConnectServiceState.next()
            Player.COMMAND_SEEK_TO_PREVIOUS -> SpotifyConnectServiceState.previous()
            else -> SpotifyConnectServiceState.seek(positionMs)
        }
    }

    override fun handleRelease(): ListenableFuture<*> {
        observerJob.cancel()
        return Futures.immediateVoidFuture()
    }

    /** [GoLibrespotApiClient]'s commands block on synchronous network I/O; run them off the
     * player's own (main) looper and resolve the future when done. */
    private fun runOnIo(block: () -> Unit): ListenableFuture<*> {
        val future = SettableFuture.create<Any?>()
        scope.launch(Dispatchers.IO) {
            block()
            future.set(null)
        }
        return future
    }
}
