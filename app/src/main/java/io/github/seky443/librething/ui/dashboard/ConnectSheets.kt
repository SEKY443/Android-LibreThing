package io.github.seky443.librething.ui.dashboard

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.DevicesOther
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.seky443.librething.R
import io.github.seky443.librething.service.model.ConnectDevice
import io.github.seky443.librething.service.model.ConnectDevices
import io.github.seky443.librething.service.model.PlayQueue
import io.github.seky443.librething.service.model.QueueEntry

private const val DEVICE_VOLUME_MAX = 65535f

/**
 * Bottom sheet listing the play order around the current track (`GET /player/queue`, kept
 * current by the service while this is open -- see [DashboardViewModel.setQueueObserved]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueSheet(viewModel: DashboardViewModel, onDismiss: () -> Unit) {
    DisposableEffect(Unit) {
        viewModel.setQueueObserved(true)
        onDispose { viewModel.setQueueObserved(false) }
    }
    val queue by viewModel.queue.collectAsState()

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            stringResource(R.string.queue_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        QueueContent(queue, onPlay = { entry ->
            viewModel.skipToTrack(entry.uri)
            onDismiss()
        })
    }
}

@Composable
private fun QueueContent(queue: PlayQueue?, onPlay: (QueueEntry) -> Unit) {
    if (queue == null || (queue.current == null && queue.next.isEmpty() && queue.previous.isEmpty())) {
        Text(
            stringResource(R.string.queue_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
        )
        return
    }
    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp)) {
        queue.current?.let { current ->
            item { SectionHeader(stringResource(R.string.queue_now_playing)) }
            item { QueueRow(current, highlight = true) }
        }
        if (queue.next.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.queue_up_next)) }
            items(queue.next) { QueueRow(it, onClick = { _ -> onPlay(it) }) }
        }
        if (queue.previous.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.queue_earlier)) }
            // Farthest first in the daemon's list; nearest-first reads more naturally here.
            items(queue.previous.asReversed()) { QueueRow(it, onClick = { _ -> onPlay(it) }) }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun QueueRow(entry: QueueEntry, highlight: Boolean = false, onClick: ((QueueEntry) -> Unit)? = null) {
    ListItem(
        // Tapping an entry plays it; the one already playing has nothing to jump to.
        modifier = if (onClick != null) Modifier.clickable { onClick(entry) } else Modifier,
        headlineContent = {
            Text(
                entry.name ?: stringResource(R.string.queue_loading_title),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
        },
        supportingContent = entry.artistNames.takeIf { it.isNotEmpty() }?.let {
            { Text(it.joinToString(", "), maxLines = 1, overflow = TextOverflow.Ellipsis) }
        },
        trailingContent = when (entry.provider) {
            "queue" -> ({ Text(stringResource(R.string.queue_badge_queued), style = MaterialTheme.typography.labelSmall) })
            "autoplay" -> ({ Text(stringResource(R.string.queue_badge_autoplay), style = MaterialTheme.typography.labelSmall) })
            else -> null
        },
        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
    )
}

/**
 * Bottom sheet listing the account's Spotify Connect devices (`GET /connect/devices`): tap a
 * device to move playback to it, and for whichever other device is currently active, basic
 * transport and volume controls (`POST /connect/command`).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevicesSheet(viewModel: DashboardViewModel, onDismiss: () -> Unit) {
    val devices by viewModel.connectDevices.collectAsState()
    val context = LocalContext.current
    LaunchedEffect(Unit) { viewModel.refreshConnectDevices() }

    fun toastOnFailure(messageRes: Int): (Boolean) -> Unit = { ok ->
        if (!ok) Toast.makeText(context, context.getString(messageRes), Toast.LENGTH_SHORT).show()
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            stringResource(R.string.devices_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        val cluster = devices
        if (cluster == null || cluster.devices.isEmpty()) {
            Text(
                stringResource(R.string.devices_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp)) {
                items(cluster.devices, key = { it.id }) { device ->
                    DeviceRow(
                        device = device,
                        cluster = cluster,
                        onTransfer = {
                            viewModel.transferTo(device.id) { ok ->
                                toastOnFailure(R.string.devices_transfer_failed)(ok)
                                if (ok) onDismiss()
                            }
                        },
                        onCommand = { endpoint ->
                            viewModel.commandDevice(device.id, endpoint, toastOnFailure(R.string.devices_command_failed))
                        },
                        onVolume = { volume ->
                            viewModel.setDeviceVolume(device.id, volume, toastOnFailure(R.string.devices_command_failed))
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun DeviceRow(
    device: ConnectDevice,
    cluster: ConnectDevices,
    onTransfer: () -> Unit,
    onCommand: (String) -> Unit,
    onVolume: (Int) -> Unit,
) {
    val canTransfer = !device.isSelf && !device.isOffline && device.canTransfer && !device.isActive
    val status = when {
        device.isOffline -> stringResource(R.string.devices_offline)
        device.isActive && cluster.isPlaying -> stringResource(R.string.devices_playing)
        device.isActive -> stringResource(R.string.devices_paused)
        device.isSelf -> stringResource(R.string.devices_this_device)
        else -> null
    }
    // Controls only for another device that actually holds the playback right now.
    val showControls = device.isActive && !device.isSelf && !device.isOffline

    Column {
        ListItem(
            modifier = Modifier.clickable(enabled = canTransfer, onClick = onTransfer),
            leadingContent = { Icon(deviceIcon(device.type), contentDescription = null) },
            headlineContent = {
                Text(
                    device.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (device.isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
            },
            supportingContent = status?.let { { Text(if (device.isSelf && device.isActive) "${stringResource(R.string.devices_this_device)} · $it" else it) } },
            colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
        )
        if (showControls) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { onCommand("skip_prev") }) {
                    Icon(Icons.Filled.SkipPrevious, contentDescription = stringResource(R.string.content_desc_device_previous))
                }
                IconButton(onClick = { onCommand(if (cluster.isPlaying) "pause" else "resume") }) {
                    Icon(
                        if (cluster.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = stringResource(R.string.content_desc_device_play_pause),
                    )
                }
                IconButton(onClick = { onCommand("skip_next") }) {
                    Icon(Icons.Filled.SkipNext, contentDescription = stringResource(R.string.content_desc_device_next))
                }
            }
            // Sent once on release, not per drag step: every call is a round trip through Spotify.
            var volume by remember(device.id, device.volume) { mutableFloatStateOf(device.volume.toFloat()) }
            Slider(
                value = volume,
                onValueChange = { volume = it },
                onValueChangeFinished = { onVolume(volume.toInt()) },
                valueRange = 0f..DEVICE_VOLUME_MAX,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            )
        }
    }
}

private fun deviceIcon(type: String): ImageVector = when (type) {
    "computer" -> Icons.Filled.Computer
    "smartphone", "tablet" -> Icons.Filled.Smartphone
    "tv", "castvideo" -> Icons.Filled.Tv
    "speaker", "castaudio", "audiodongle", "avr" -> Icons.Filled.Speaker
    else -> Icons.Filled.DevicesOther
}
