package io.github.seky443.librething.ui.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.palette.graphics.Palette
import coil3.BitmapImage
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import io.github.seky443.librething.R
import io.github.seky443.librething.service.model.ConnectionState
import io.github.seky443.librething.service.model.TrackInfo
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun DashboardScreen(viewModel: DashboardViewModel) {
    val connectionState by viewModel.connectionState.collectAsState()
    val nowPlaying by viewModel.nowPlaying.collectAsState()
    val volume by viewModel.volume.collectAsState()
    val localDeviceVolumeFraction by viewModel.localDeviceVolumeFraction.collectAsState()
    val logs by viewModel.logs.collectAsState()
    val volumeSliderEnabled by viewModel.volumeSliderEnabled.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
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
                    localDeviceVolumeFraction = localDeviceVolumeFraction,
                    volumeSliderEnabled = volumeSliderEnabled,
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
            Text(stringResource(R.string.dashboard_fake_sleep_button))
        }

        LogConsole(
            logs = logs,
            onClear = viewModel::clearLogs,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * The floating start/stop control, hoisted up to [io.github.seky443.librething.ui.navigation.AppNavHost]'s
 * shared `Scaffold` so it stays on screen across both tabs rather than scrolling away with the
 * Dashboard's content. Both its container color and its icon change with connection state, so it
 * doubles as the status indicator without needing a separate dot. The color is blended into the
 * theme's own `primary` (Material You dynamic color on API 31+) rather than a flat hex, so it
 * reads as an accent of the actual runtime palette instead of clashing against it.
 */
@Composable
fun StartStopFab(connectionState: ConnectionState, isServiceRunning: Boolean, onToggle: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val containerColor by animateColorAsState(
        targetValue = connectionState.fabColor(),
        animationSpec = tween(400),
        label = "fabColor",
    )
    FloatingActionButton(
        onClick = { haptics.performHapticFeedback(HapticFeedbackType.VirtualKey); onToggle() },
        shape = RoundedCornerShape(16.dp),
        containerColor = containerColor,
        contentColor = Color.White,
    ) {
        Icon(
            connectionState.fabIcon(),
            contentDescription = if (isServiceRunning) "Stop" else "Start",
        )
    }
}

/** Leans blue-green across the non-alert states (idle/starting/discoverable/playing/paused),
 * keeping red only for [ConnectionState.Error] where a departure from that hue is meaningful. */
@Composable
internal fun ConnectionState.fabColor(): Color {
    val hue = when (this) {
        ConnectionState.Idle -> Color(0xFF78909C)
        ConnectionState.Starting -> Color(0xFF42A5F5)
        ConnectionState.Discoverable -> Color(0xFF29B6F6)
        ConnectionState.Playing -> Color(0xFF26A69A)
        ConnectionState.Paused -> Color(0xFF66BB6A)
        is ConnectionState.Error -> Color(0xFFEF5350)
    }
    return lerp(MaterialTheme.colorScheme.primary, hue, 0.6f)
}

internal fun ConnectionState.fabIcon() = when (this) {
    ConnectionState.Idle -> Icons.Filled.PowerSettingsNew
    ConnectionState.Starting -> Icons.Filled.Sync
    ConnectionState.Discoverable -> Icons.Filled.Speaker
    ConnectionState.Playing -> Icons.Filled.MusicNote
    ConnectionState.Paused -> Icons.Filled.Pause
    is ConnectionState.Error -> Icons.Filled.ErrorOutline
}

@Composable
internal fun ConnectionState.label(): String = when (this) {
    ConnectionState.Idle -> stringResource(R.string.connection_state_idle)
    ConnectionState.Starting -> stringResource(R.string.connection_state_starting)
    ConnectionState.Discoverable -> stringResource(R.string.connection_state_discoverable)
    ConnectionState.Playing -> stringResource(R.string.connection_state_playing)
    ConnectionState.Paused -> stringResource(R.string.connection_state_paused)
    is ConnectionState.Error -> stringResource(R.string.connection_state_error_format, message)
}

/**
 * Extracts a swatch from the album art via [Palette] and uses it to lightly tint the card's
 * surface and the seek bar's accent, so the "now playing" card visually matches the track --
 * the tint is blended into the theme's own colors (not used at full strength) so contrast against
 * the theme's text colors, which aren't otherwise adapted, stays safe in both light and dark mode.
 */
@Composable
internal fun rememberAlbumAccent(albumCoverUrl: String?): Color? {
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()
    var accent by remember(albumCoverUrl) { mutableStateOf<Color?>(null) }
    LaunchedEffect(albumCoverUrl, isDark) {
        if (albumCoverUrl == null) return@LaunchedEffect
        val result = withContext(Dispatchers.IO) {
            val request = ImageRequest.Builder(context)
                .data(albumCoverUrl)
                // Palette can't read HARDWARE-config bitmaps (Coil's decode default on API 26+).
                .allowHardware(false)
                .build()
            SingletonImageLoader.get(context).execute(request)
        }
        val bitmap = ((result as? SuccessResult)?.image as? BitmapImage)?.bitmap ?: return@LaunchedEffect
        val palette = withContext(Dispatchers.Default) { Palette.from(bitmap).generate() }
        val swatch = if (isDark) {
            palette.darkMutedSwatch ?: palette.darkVibrantSwatch ?: palette.dominantSwatch
        } else {
            palette.lightMutedSwatch ?: palette.lightVibrantSwatch ?: palette.dominantSwatch
        }
        accent = swatch?.let { Color(it.rgb) }
    }
    return accent
}

/** Only composed while a track is loaded -- see [DashboardScreen]'s `AnimatedVisibility`. */
@Composable
private fun NowPlayingCard(
    connectionState: ConnectionState,
    nowPlaying: TrackInfo,
    volume: Pair<Int, Int>,
    localDeviceVolumeFraction: Float?,
    volumeSliderEnabled: Boolean,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onVolumeChange: (Int) -> Unit,
    onSeek: (Long) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val albumAccent = rememberAlbumAccent(nowPlaying.albumCoverUrl)
    val baseContainer = MaterialTheme.colorScheme.surfaceVariant
    val basePrimary = MaterialTheme.colorScheme.primary
    val containerColor by animateColorAsState(
        targetValue = albumAccent?.let { lerp(baseContainer, it, 0.35f) } ?: baseContainer,
        animationSpec = tween(600),
        label = "nowPlayingContainerColor",
    )
    val seekAccentColor by animateColorAsState(
        targetValue = albumAccent?.let { lerp(basePrimary, it, 0.55f) } ?: basePrimary,
        animationSpec = tween(600),
        label = "nowPlayingSeekAccentColor",
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (nowPlaying.albumCoverUrl != null) {
                    AsyncImage(
                        model = nowPlaying.albumCoverUrl,
                        contentDescription = stringResource(R.string.content_desc_album_art),
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
                accentColor = seekAccentColor,
                onSeek = onSeek,
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { haptics.performHapticFeedback(HapticFeedbackType.VirtualKey); onPrevious() }) {
                    Icon(Icons.Filled.SkipPrevious, contentDescription = stringResource(R.string.content_desc_previous))
                }
                FilledTonalIconButton(
                    onClick = { haptics.performHapticFeedback(HapticFeedbackType.VirtualKey); onPlayPause() },
                ) {
                    Icon(
                        if (connectionState == ConnectionState.Playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = stringResource(R.string.content_desc_play_pause),
                    )
                }
                IconButton(onClick = { haptics.performHapticFeedback(HapticFeedbackType.VirtualKey); onNext() }) {
                    Icon(Icons.Filled.SkipNext, contentDescription = stringResource(R.string.content_desc_next))
                }
            }

            if (volumeSliderEnabled) {
                VolumeSlider(
                    volume = volume,
                    onVolumeChange = onVolumeChange,
                    accentColor = seekAccentColor,
                    localOverrideFraction = localDeviceVolumeFraction,
                )
            }
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
internal fun PlaybackProgressBar(
    nowPlaying: TrackInfo,
    isPlaying: Boolean,
    accentColor: Color,
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
    // Not gated on isPlaying: nowMs's own ticker loop already stops updating the instant
    // isPlaying goes false (see the LaunchedEffect above), so this naturally freezes at
    // wherever it was rather than needing to be forced back to 0 -- forcing it to 0 here was
    // the bug, since it snapped the bar back to the stale pre-tick anchor on every pause.
    val elapsedSincePlaybackSnapshotMs = (nowMs - snapshotAtMs).coerceAtLeast(0)
    val tickedPositionMs = (nowPlaying.positionMs + elapsedSincePlaybackSnapshotMs).coerceIn(0, durationMs)

    var dragPositionMs by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(tickedPositionMs) {
        val dragged = dragPositionMs ?: return@LaunchedEffect
        if (abs(tickedPositionMs - dragged) < 1500) dragPositionMs = null
    }
    val displayedPositionMs = dragPositionMs ?: tickedPositionMs

    // The position only actually updates once every 500ms tick (see the LaunchedEffect above),
    // which read as the thumb hopping forward in little jumps instead of gliding. Animating
    // across that same 500ms window smooths it into continuous motion instead. Dragging (or
    // being paused, where nothing should be gliding at all) snaps immediately so the thumb
    // never lags behind the finger or a fresh seek/track change.
    val animatedProgressFraction by animateFloatAsState(
        targetValue = displayedPositionMs.toFloat() / durationMs.toFloat(),
        animationSpec = if (isPlaying && dragPositionMs == null) tween(500, easing = LinearEasing) else snap(),
        label = "progressFraction",
    )

    Column {
        WavySeekBar(
            progressFraction = animatedProgressFraction,
            isPlaying = isPlaying,
            accentColor = accentColor,
            onSeek = { fraction -> onSeek((fraction * durationMs).toLong()) },
            onDrag = { fraction -> dragPositionMs = (fraction * durationMs).toLong() },
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatTrackTime(displayedPositionMs), style = MaterialTheme.typography.labelSmall)
            Text(formatTrackTime(durationMs), style = MaterialTheme.typography.labelSmall)
        }
    }
}

/**
 * A Material 3 Expressive-style "wavy" seek bar: the played portion is drawn as a travelling
 * sine wave (flattening to a still line when paused) instead of a straight bar, matching the
 * wavy progress/slider look from the current Material 3 spec -- which isn't available as a
 * ready-made component in the Material3 1.4.0 library this app is on (checked: no
 * `LinearWavyProgressIndicator`/wavy `Slider` track exists in that release), so it's hand-drawn
 * on a [Canvas] instead of pulled in as a dependency. Drag/tap-to-seek is implemented directly
 * on the canvas via pointer input rather than wrapping a [Slider], since Slider has no hook to
 * replace its track with a custom path.
 */
@Composable
private fun WavySeekBar(
    progressFraction: Float,
    isPlaying: Boolean,
    accentColor: Color,
    onSeek: (Float) -> Unit,
    onDrag: (Float) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val displayedFraction = (dragFraction ?: progressFraction).coerceIn(0f, 1f)
    // Coarser than the seek resolution -- paces the haptic "detents" felt while dragging to
    // something like discrete clicks, matching VolumeSlider's own drag feedback.
    var lastHapticStep by remember { mutableStateOf(-1) }

    val infiniteTransition = rememberInfiniteTransition(label = "wavySeekPhase")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(1200, easing = LinearEasing)),
        label = "wavySeekPhase",
    )
    val amplitudeFraction by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0f,
        animationSpec = tween(400),
        label = "wavySeekAmplitude",
    )
    val inactiveColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(24.dp)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { offset ->
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                        onSeek(fraction)
                    },
                )
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { haptics.performHapticFeedback(HapticFeedbackType.SegmentTick) },
                    onDragEnd = {
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        dragFraction?.let(onSeek)
                        dragFraction = null
                    },
                    onDragCancel = { dragFraction = null },
                ) { change, _ ->
                    change.consume()
                    val fraction = (change.position.x / size.width).coerceIn(0f, 1f)
                    dragFraction = fraction
                    onDrag(fraction)
                    val step = (fraction * HAPTIC_STEPS).toInt()
                    if (step != lastHapticStep) {
                        lastHapticStep = step
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    }
                }
            },
    ) {
        val strokeWidthPx = 4.dp.toPx()
        val thumbRadiusPx = 6.dp.toPx()
        val amplitudePx = 3.dp.toPx() * amplitudeFraction
        val wavelengthPx = 18.dp.toPx()
        val midY = size.height / 2f
        val activeEndX = size.width * displayedFraction

        if (activeEndX < size.width) {
            drawLine(
                color = inactiveColor,
                start = Offset((activeEndX + strokeWidthPx).coerceAtMost(size.width), midY),
                end = Offset(size.width, midY),
                strokeWidth = strokeWidthPx,
                cap = StrokeCap.Round,
            )
        }

        if (activeEndX > 0f) {
            if (amplitudePx < 0.5f) {
                drawLine(
                    color = accentColor,
                    start = Offset(0f, midY),
                    end = Offset(activeEndX, midY),
                    strokeWidth = strokeWidthPx,
                    cap = StrokeCap.Round,
                )
            } else {
                val path = Path().apply {
                    val phaseOffsetPx = phase * wavelengthPx
                    val step = 4.dp.toPx()
                    var x = 0f
                    var first = true
                    while (x <= activeEndX) {
                        val y = midY + amplitudePx * sin((2 * PI * (x - phaseOffsetPx) / wavelengthPx).toFloat())
                        if (first) {
                            moveTo(x, y)
                            first = false
                        } else {
                            lineTo(x, y)
                        }
                        x += step
                    }
                }
                drawPath(path, color = accentColor, style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round))
            }
        }

        drawCircle(color = accentColor, radius = thumbRadiusPx, center = Offset(activeEndX, midY))
    }
}

internal fun formatTrackTime(ms: Long): String {
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
internal fun VolumeSlider(
    volume: Pair<Int, Int>,
    onVolumeChange: (Int) -> Unit,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    localOverrideFraction: Float? = null,
) {
    val haptics = LocalHapticFeedback.current
    val (volValue, volMax) = volume
    val committedFraction = if (volMax > 0) volValue.toFloat() / volMax.toFloat() else 0f

    var dragFraction by remember { mutableStateOf<Float?>(null) }
    // Coarser than volMax's real resolution (often 65535 steps) -- this only paces the haptic
    // "detents" to something felt as discrete clicks, not the value actually sent on release.
    var lastHapticStep by remember { mutableStateOf(-1) }

    // Genuinely remote-origin changes (another Connect client, or the daemon's own echo) --
    // as opposed to a hardware volume key, which shows instantly via localOverrideFraction
    // instead of waiting on this -- animate into a glide. But while a drag or a hardware-key
    // override is the thing actually on screen (dragFraction/localOverrideFraction below), this
    // is kept silently snapped to committedFraction instead of animating toward it: without
    // that, it would sit stale at wherever it was before the drag/key press started, and the
    // moment that override let go, display would jump back to the stale value and visibly
    // animate forward again -- exactly the "plays an animation by itself a moment later" glitch
    // this replaced. Animating only ever starts once nothing local is overriding it.
    //
    // Convergence-clearing dragFraction and deciding snap-vs-animate used to be two separate
    // LaunchedEffects both keyed on committedFraction, with no guarantee which one's write
    // Compose would observe first once the daemon's echo confirms the drag. If the "clear
    // dragFraction" write landed before the "snap to the new value" one had a chance to run
    // against the now-converged committedFraction, the slider would go stale at the pre-drag
    // value for one frame -- visible as exactly the "jump back, then replay" glitch this whole
    // mechanism exists to prevent. Doing both in one effect body guarantees the snap always
    // happens before dragFraction clears, in the same coroutine, so there's nothing left to race.
    val animatedCommittedFraction = remember { Animatable(committedFraction) }
    LaunchedEffect(committedFraction, dragFraction, localOverrideFraction) {
        val dragged = dragFraction
        if (dragged != null && abs(committedFraction - dragged) < CONVERGENCE_THRESHOLD) {
            animatedCommittedFraction.snapTo(committedFraction)
            dragFraction = null
            return@LaunchedEffect
        }
        if (dragged != null || localOverrideFraction != null) {
            animatedCommittedFraction.snapTo(committedFraction)
        } else {
            animatedCommittedFraction.animateTo(committedFraction, animationSpec = tween(250, easing = FastOutSlowInEasing))
        }
    }

    Slider(
        // A hardware volume key's own immediate effect, shown as-is with no animation -- it's
        // already instantaneous, unlike the daemon round trip animatedCommittedFraction smooths
        // over. See SpotifyConnectServiceState.localDeviceVolumeFraction.
        value = dragFraction ?: localOverrideFraction ?: animatedCommittedFraction.value,
        onValueChange = { fraction ->
            dragFraction = fraction
            val step = (fraction * HAPTIC_STEPS).toInt()
            if (step != lastHapticStep) {
                lastHapticStep = step
                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
            }
        },
        onValueChangeFinished = { dragFraction?.let { onVolumeChange((it * volMax).toInt()) } },
        colors = SliderDefaults.colors(
            thumbColor = accentColor,
            activeTrackColor = accentColor,
            activeTickColor = accentColor,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

private const val HAPTIC_STEPS = 20
private const val CONVERGENCE_THRESHOLD = 0.02f
