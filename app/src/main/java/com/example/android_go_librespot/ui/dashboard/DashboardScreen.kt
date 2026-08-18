package com.example.android_go_librespot.ui.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.example.android_go_librespot.service.model.ConnectionState
import com.example.android_go_librespot.service.model.TrackInfo
import kotlin.math.abs
import kotlinx.coroutines.delay

@Composable
fun DashboardScreen(viewModel: DashboardViewModel) {
    val connectionState by viewModel.connectionState.collectAsState()
    val nowPlaying by viewModel.nowPlaying.collectAsState()
    val volume by viewModel.volume.collectAsState()
    val isServiceRunning by viewModel.isServiceRunning.collectAsState()
    val logs by viewModel.logs.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        StatusCard(
            connectionState = connectionState,
            isServiceRunning = isServiceRunning,
            onToggle = viewModel::toggleService,
        )

        // Keeps showing the last known track while nowPlaying goes null and the card's exit
        // animation is still playing, instead of the content blanking out mid-fade.
        var lastTrack by remember { mutableStateOf<TrackInfo?>(null) }
        LaunchedEffect(nowPlaying) {
            if (nowPlaying != null) lastTrack = nowPlaying
        }
        AnimatedVisibility(visible = nowPlaying != null) {
            lastTrack?.let { track ->
                NowPlayingCard(
                    connectionState = connectionState,
                    nowPlaying = track,
                    volume = volume,
                    onPlayPause = viewModel::playPause,
                    onNext = viewModel::next,
                    onPrevious = viewModel::previous,
                    onVolumeChange = viewModel::setVolume,
                    onSeek = viewModel::seek,
                )
            }
        }

        OutlinedButton(
            onClick = viewModel::fakeSleep,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Filled.Bedtime, contentDescription = null)
            Spacer(Modifier.padding(4.dp))
            Text("Fake sleep (black screen)")
        }

        LogConsole(
            logs = logs,
            onClear = viewModel::clearLogs,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun StatusCard(
    connectionState: ConnectionState,
    isServiceRunning: Boolean,
    onToggle: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(connectionState)
                Spacer(Modifier.padding(6.dp))
                Text(connectionState.label(), style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.padding(top = 12.dp))
            Button(
                onClick = onToggle,
                modifier = Modifier.fillMaxWidth(),
                colors = if (isServiceRunning) {
                    androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    )
                } else {
                    androidx.compose.material3.ButtonDefaults.buttonColors()
                },
            ) {
                Icon(Icons.Filled.PowerSettingsNew, contentDescription = null)
                Spacer(Modifier.padding(4.dp))
                Text(if (isServiceRunning) "Stop" else "Start")
            }
        }
    }
}

@Composable
private fun StatusDot(state: ConnectionState) {
    val color = when (state) {
        ConnectionState.Idle -> Color.Gray
        ConnectionState.Starting -> Color(0xFFFFA000)
        ConnectionState.Discoverable -> Color(0xFF2196F3)
        ConnectionState.Playing -> Color(0xFF4CAF50)
        ConnectionState.Paused -> Color(0xFFFFA000)
        is ConnectionState.Error -> Color(0xFFE53935)
    }
    androidx.compose.foundation.Canvas(modifier = Modifier.size(12.dp)) {
        drawCircle(color)
    }
}

private fun ConnectionState.label(): String = when (this) {
    ConnectionState.Idle -> "Idle"
    ConnectionState.Starting -> "Starting…"
    ConnectionState.Discoverable -> "Discoverable"
    ConnectionState.Playing -> "Playing"
    ConnectionState.Paused -> "Paused"
    is ConnectionState.Error -> "Error: $message"
}

/** Only composed while a track is loaded -- see [DashboardScreen]'s `AnimatedVisibility`. */
@Composable
private fun NowPlayingCard(
    connectionState: ConnectionState,
    nowPlaying: TrackInfo,
    volume: Pair<Int, Int>,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onVolumeChange: (Int) -> Unit,
    onSeek: (Long) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (nowPlaying.albumCoverUrl != null) {
                    AsyncImage(
                        model = nowPlaying.albumCoverUrl,
                        contentDescription = "Album art",
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(8.dp)),
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surface),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.MusicNote, contentDescription = null)
                    }
                }
                Spacer(Modifier.padding(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = nowPlaying.name, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                    Text(
                        text = nowPlaying.artistNames.joinToString(", "),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                    )
                    Text(
                        text = nowPlaying.albumName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }

            PlaybackProgressBar(
                nowPlaying = nowPlaying,
                isPlaying = connectionState == ConnectionState.Playing,
                onSeek = onSeek,
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onPrevious) {
                    Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous")
                }
                FilledTonalIconButton(onClick = onPlayPause) {
                    Icon(
                        if (connectionState == ConnectionState.Playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = "Play/Pause",
                    )
                }
                IconButton(onClick = onNext) {
                    Icon(Icons.Filled.SkipNext, contentDescription = "Next")
                }
            }

            VolumeSlider(volume = volume, onVolumeChange = onVolumeChange)
        }
    }
}

/**
 * Position advances between metadata/seek snapshots by interpolating from wall-clock time
 * rather than polling: the daemon only ever pushes a position snapshot on track change or
 * seek, never a periodic tick, which matches how Spotify Connect state generally works.
 * Dragging keeps a local override so the thumb doesn't jump while ticking updates the "real"
 * position underneath it; the drag is only committed to the daemon (via [onSeek]) on release,
 * not on every intermediate value, so the drag itself stays smooth.
 */
@Composable
private fun PlaybackProgressBar(
    nowPlaying: TrackInfo,
    isPlaying: Boolean,
    onSeek: (Long) -> Unit,
) {
    val durationMs = nowPlaying.durationMs.coerceAtLeast(1L)
    val snapshotAtMs = remember(nowPlaying) { System.currentTimeMillis() }
    var nowMs by remember(nowPlaying) { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(nowPlaying, isPlaying) {
        if (!isPlaying) return@LaunchedEffect
        while (true) {
            delay(500)
            nowMs = System.currentTimeMillis()
        }
    }
    val elapsedWhilePlaying = if (isPlaying) (nowMs - snapshotAtMs).coerceAtLeast(0) else 0L
    val tickedPositionMs = (nowPlaying.positionMs + elapsedWhilePlaying).coerceIn(0, durationMs)

    var dragPositionMs by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(tickedPositionMs) {
        val dragged = dragPositionMs ?: return@LaunchedEffect
        if (abs(tickedPositionMs - dragged) < 1500) dragPositionMs = null
    }
    val displayedPositionMs = dragPositionMs ?: tickedPositionMs

    Column {
        Slider(
            value = displayedPositionMs.toFloat(),
            onValueChange = { dragPositionMs = it.toLong() },
            onValueChangeFinished = { dragPositionMs?.let(onSeek) },
            valueRange = 0f..durationMs.toFloat(),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatTrackTime(displayedPositionMs), style = MaterialTheme.typography.labelSmall)
            Text(formatTrackTime(durationMs), style = MaterialTheme.typography.labelSmall)
        }
    }
}

private fun formatTrackTime(ms: Long): String {
    val totalSeconds = ms / 1000
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}

/**
 * Same drag-then-commit shape as [PlaybackProgressBar]'s seek bar: reporting every intermediate
 * `onValueChange` tick to the daemon (the previous behaviour) meant a network round trip per
 * pixel of drag, which is what made dragging feel stuttery -- now the daemon is only told once,
 * on release, and the slider tracks purely local state while dragging.
 */
@Composable
private fun VolumeSlider(volume: Pair<Int, Int>, onVolumeChange: (Int) -> Unit) {
    val (volValue, volMax) = volume
    val committedFraction = if (volMax > 0) volValue.toFloat() / volMax.toFloat() else 0f

    var dragFraction by remember { mutableStateOf<Float?>(null) }
    LaunchedEffect(committedFraction) {
        val dragged = dragFraction ?: return@LaunchedEffect
        if (abs(committedFraction - dragged) < 0.02f) dragFraction = null
    }

    Slider(
        value = dragFraction ?: committedFraction,
        onValueChange = { dragFraction = it },
        onValueChangeFinished = { dragFraction?.let { onVolumeChange((it * volMax).toInt()) } },
        modifier = Modifier.fillMaxWidth(),
    )
}
