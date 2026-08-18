package com.example.android_go_librespot.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.example.android_go_librespot.service.model.ConnectionState

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

        if (isServiceRunning) {
            NowPlayingCard(
                connectionState = connectionState,
                nowPlaying = nowPlaying,
                volume = volume,
                onPlayPause = viewModel::playPause,
                onNext = viewModel::next,
                onPrevious = viewModel::previous,
                onVolumeChange = viewModel::setVolume,
            )
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

@Composable
private fun NowPlayingCard(
    connectionState: ConnectionState,
    nowPlaying: com.example.android_go_librespot.service.model.TrackInfo?,
    volume: Pair<Int, Int>,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onVolumeChange: (Int) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (nowPlaying?.albumCoverUrl != null) {
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
                    Text(
                        text = nowPlaying?.name ?: "Nothing playing",
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                    )
                    Text(
                        text = nowPlaying?.artistNames?.joinToString(", ") ?: "Waiting for a Spotify client to connect",
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                    )
                    if (nowPlaying != null) {
                        Text(
                            text = nowPlaying.albumName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onPrevious, enabled = nowPlaying != null) {
                    Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous")
                }
                FilledTonalIconButton(onClick = onPlayPause, enabled = nowPlaying != null) {
                    Icon(
                        if (connectionState == ConnectionState.Playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = "Play/Pause",
                    )
                }
                IconButton(onClick = onNext, enabled = nowPlaying != null) {
                    Icon(Icons.Filled.SkipNext, contentDescription = "Next")
                }
            }

            val (volValue, volMax) = volume
            Slider(
                value = if (volMax > 0) volValue.toFloat() / volMax.toFloat() else 0f,
                onValueChange = { fraction -> onVolumeChange((fraction * volMax).toInt()) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
