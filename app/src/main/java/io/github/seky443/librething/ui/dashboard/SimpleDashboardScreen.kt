package io.github.seky443.librething.ui.dashboard

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.rounded.BroadcastOnHome
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import android.content.res.Configuration
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import io.github.seky443.librething.R
import io.github.seky443.librething.data.DashboardBackgroundStyle
import io.github.seky443.librething.service.model.ConnectionState
import io.github.seky443.librething.service.model.LogEntry
import io.github.seky443.librething.service.model.LogLevel
import io.github.seky443.librething.service.model.TrackInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.roundToInt

/** Width of the cover-art card as a fraction of the available content width (portrait) --
 * also used by [TransportControlsRow] so the transport row lines up edge-to-edge with the
 * card above it instead of spanning the full column width. */
private const val MediaCardWidthFraction = 0.78f

/**
 * The default Dashboard UI (see [io.github.seky443.librething.data.AppPreferences.nerdModeEnabled]):
 * a single card that plays double duty as the status display (idle) and now-playing display
 * (active), instead of [DashboardScreen]'s separate status/log/now-playing cards and floating
 * buttons. There's no bottom navigation in this mode -- [onOpenSettings] is the only way to
 * Settings, reached via the top-right corner icon (tap while idle, long-press while running,
 * since a tap there stops the server once one is).
 */
@Composable
fun SimpleDashboardScreen(
    viewModel: DashboardViewModel,
    onOpenSettings: () -> Unit,
) {
    val connectionState by viewModel.connectionState.collectAsState()
    val nowPlaying by viewModel.nowPlaying.collectAsState()
    val volume by viewModel.volume.collectAsState()
    val localDeviceVolumeFraction by viewModel.localDeviceVolumeFraction.collectAsState()
    val isServiceRunning by viewModel.isServiceRunning.collectAsState()
    val logs by viewModel.logs.collectAsState()
    val volumeSliderEnabled by viewModel.volumeSliderEnabled.collectAsState()
    val volumeSliderLandscapeOnly by viewModel.volumeSliderLandscapeOnly.collectAsState()
    val landscapeLeftAlignTrackInfoEnabled by viewModel.landscapeLeftAlignTrackInfoEnabled.collectAsState()
    val hideConsoleButtonEnabled by viewModel.hideConsoleButtonEnabled.collectAsState()
    val hideFakeSleepButtonEnabled by viewModel.hideFakeSleepButtonEnabled.collectAsState()
    val hideIconRowInLandscapeEnabled by viewModel.hideIconRowInLandscapeEnabled.collectAsState()
    val hideLastSessionLabelInLandscapeEnabled by viewModel.hideLastSessionLabelInLandscapeEnabled.collectAsState()
    val landscapeStretchTransportRowEnabled by viewModel.landscapeStretchTransportRowEnabled.collectAsState()
    val lastSessionEndAtMillis by viewModel.lastSessionEndAtMillis.collectAsState()
    val dashboardBackgroundStyle by viewModel.dashboardBackgroundStyle.collectAsState()
    val haptics = LocalHapticFeedback.current
    val context = LocalContext.current

    var showConsole by remember { mutableStateOf(false) }
    val track = nowPlaying
    val hasTrack = track != null
    // No cover art exists to toggle to until a track loads, regardless of the toggle itself.
    val effectiveShowConsole = showConsole || !hasTrack

    // The whole page's color follows the current track's cover art (falls back to the plain
    // theme when idle/no artwork) -- same Palette-based extraction NowPlayingCard uses, just
    // applied to the page background and controls instead of one card. Blended into the theme's
    // own colors at the same fractions proven there (0.35 for container-like surfaces, 0.55 for
    // accent/primary-like ones) rather than used at full strength, so text/icon contrast stays
    // safe regardless of how saturated the art is.
    val albumAccent = rememberAlbumAccent(track?.albumCoverUrl)
    val pageBackground by animateColorAsState(
        targetValue = when (dashboardBackgroundStyle) {
            // Pure black regardless of album art -- OLED burn-in protection is the whole
            // point, so it can't be even lightly tinted.
            DashboardBackgroundStyle.OLED_BLACK -> Color.Black
            DashboardBackgroundStyle.TINTED, DashboardBackgroundStyle.BLURRED_COVER ->
                albumAccent?.let { lerp(MaterialTheme.colorScheme.background, it, 0.35f) } ?: MaterialTheme.colorScheme.background
        },
        animationSpec = tween(600),
        label = "pageBackground",
    )
    val containerAccent by animateColorAsState(
        targetValue = albumAccent?.let { lerp(MaterialTheme.colorScheme.surfaceVariant, it, 0.35f) } ?: MaterialTheme.colorScheme.surfaceVariant,
        animationSpec = tween(600),
        label = "containerAccent",
    )
    val playAccent by animateColorAsState(
        targetValue = albumAccent?.let { lerp(MaterialTheme.colorScheme.primary, it, 0.55f) } ?: MaterialTheme.colorScheme.primary,
        animationSpec = tween(600),
        label = "playAccent",
    )

    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val effectiveVolumeSliderEnabled = volumeSliderEnabled && (!volumeSliderLandscapeOnly || isLandscape)

    // Status bar and gesture-nav insets aren't the same size (status bar is taller), so
    // matching them 1:1 via plain safeDrawingPadding() left a visibly bigger gap above the
    // cover art than below the bottom buttons. Padding both sides by the larger of the two
    // instead makes the top/bottom gaps read as equal.
    val statusBarInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navigationBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val symmetricVerticalInset = maxOf(statusBarInset, navigationBarInset)

    // Resolved here (composable scope) rather than inside the lambdas below, which aren't
    // composable themselves.
    val consoleLongPressHint = stringResource(R.string.toast_console_long_press_hint)
    val fakeSleepWakeHint = stringResource(R.string.toast_fake_sleep_wake_hint)

    // The hint for the console's long-press options lives here (on the toggle button that
    // switches into console view), not on the console card itself -- it only makes sense the
    // moment you actually switch to console, not on every tap of the card afterwards.
    val onToggleConsole: () -> Unit = {
        haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
        showConsole = !showConsole
        if (showConsole) {
            Toast.makeText(context, consoleLongPressHint, Toast.LENGTH_SHORT).show()
        }
    }
    // Same idea for fake sleep -- the overlay itself shows no on-screen hint (that would
    // defeat the point of looking off), so this is the only place the wake gesture is ever
    // surfaced, right as it's triggered.
    val onFakeSleep: () -> Unit = {
        haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
        Toast.makeText(context, fakeSleepWakeHint, Toast.LENGTH_SHORT).show()
        viewModel.fakeSleep()
    }

    // OLED black is always a dark background; blurred-cover only is while it's actually
    // drawing the blurred cover (it falls back to the normal pageBackground -- light or dark
    // depending on the app theme -- when idle/no art). Either way, forcing white text only
    // applies while the background underneath is actually guaranteed dark; otherwise it stays
    // on the ambient theme's own colors, which are already correct for pageBackground.
    val forceLightContent = dashboardBackgroundStyle == DashboardBackgroundStyle.OLED_BLACK ||
        (dashboardBackgroundStyle == DashboardBackgroundStyle.BLURRED_COVER && track?.albumCoverUrl != null)
    val contentColorScheme = if (forceLightContent) {
        MaterialTheme.colorScheme.copy(
            onBackground = Color.White,
            onSurface = Color.White,
            onSurfaceVariant = Color.White.copy(alpha = 0.75f),
        )
    } else {
        MaterialTheme.colorScheme
    }

    MaterialTheme(colorScheme = contentColorScheme, typography = MaterialTheme.typography) {
    // Text with no explicit `color` (the title, notably) falls back to LocalContentColor, not
    // colorScheme.onBackground -- MaterialTheme doesn't set that on its own (only Surface
    // does), and its own platform default is plain black, invisible on a black background.
    CompositionLocalProvider(LocalContentColor provides contentColorScheme.onBackground) {
    Box(modifier = Modifier.fillMaxSize()) {
        // Blurred-cover mode paints the album art itself (heavily blurred and darkened) as
        // the page background instead of a flat tint; falls back to the normal flat
        // pageBackground when idle/no art, same as the other two styles. Crossfaded like the
        // cover-art card -- both the Coil image itself (track-to-track) and the switch
        // to/from the plain pageBackground (e.g. going idle) fade instead of popping.
        val blurredBackgroundUrl = if (dashboardBackgroundStyle == DashboardBackgroundStyle.BLURRED_COVER) track?.albumCoverUrl else null
        Crossfade(targetState = blurredBackgroundUrl, animationSpec = tween(350), label = "pageBlurredBackground") { albumCoverUrl ->
            if (albumCoverUrl != null) {
                Box(Modifier.fillMaxSize()) {
                    AsyncImage(
                        model = ImageRequest.Builder(context).data(albumCoverUrl).crossfade(true).build(),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .blur(64.dp),
                        contentScale = ContentScale.Crop,
                    )
                    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)))
                }
            } else {
                Box(modifier = Modifier.fillMaxSize().background(pageBackground))
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                .padding(horizontal = 32.dp)
                .padding(top = symmetricVerticalInset + 32.dp, bottom = symmetricVerticalInset + 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
        if (isLandscape) {
            // Side-by-side instead of stacked: the card is sized off the row's height (not the
            // screen's width, which would make it absurdly wide/short in a landscape frame),
            // with the info/controls column taking the remaining width. No leading spacer here
            // (unlike portrait below) -- the row is centered top-to-bottom on its own, and a
            // fixed offset before it would just push that centered block off-center again,
            // undoing equal top/bottom margins (most visible with the system bars hidden).
            Row(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(36.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    MediaCard(
                        modifier = Modifier.fillMaxHeight(0.85f).aspectRatio(1f),
                        effectiveShowConsole = effectiveShowConsole,
                        logs = logs,
                        track = track,
                        containerAccent = containerAccent,
                    )
                    // Idle shows when the last session ended; there's no "next track" data to
                    // show while playing -- the daemon only ever logs its prefetch, it doesn't
                    // publish an event/status field for it (checked against both the /events
                    // types and the /status schema).
                    if (!hasTrack && !hideLastSessionLabelInLandscapeEnabled) {
                        Text(
                            text = lastSessionLabel(lastSessionEndAtMillis),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
                // Not fillMaxHeight -- wraps its own (title/progress/controls) content instead
                // of stretching to the row's full height, so the Row's own CenterVertically
                // below centers this whole block the same way it centers the card, matching
                // top/bottom margins between the two instead of one being stretched flush to
                // the row's edges while the other floats centered.
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    NowPlayingInfoAndControls(
                        track = track,
                        connectionState = connectionState,
                        isServiceRunning = isServiceRunning,
                        hasTrack = hasTrack,
                        playAccent = playAccent,
                        containerAccent = containerAccent,
                        volumeSliderEnabled = effectiveVolumeSliderEnabled,
                        volume = volume,
                        localDeviceVolumeFraction = localDeviceVolumeFraction,
                        effectiveShowConsole = effectiveShowConsole,
                        onToggleConsole = onToggleConsole,
                        onFakeSleep = onFakeSleep,
                        onSettingsTap = {
                            haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
                            if (isServiceRunning) viewModel.toggleService() else onOpenSettings()
                        },
                        onSettingsLongPress = { haptics.performHapticFeedback(HapticFeedbackType.VirtualKey); onOpenSettings() },
                        onSeek = viewModel::seek,
                        onPrevious = viewModel::previous,
                        onPlayPause = viewModel::playPause,
                        onNext = viewModel::next,
                        onStartServer = viewModel::toggleService,
                        onVolumeChange = viewModel::setVolume,
                        pushControlsToBottom = false,
                        leftAlignTrackInfo = landscapeLeftAlignTrackInfoEnabled,
                        isLandscape = true,
                        hideConsoleButtonEnabled = hideConsoleButtonEnabled,
                        hideFakeSleepButtonEnabled = hideFakeSleepButtonEnabled,
                        hideIconRowInLandscapeEnabled = hideIconRowInLandscapeEnabled,
                        landscapeStretchTransportRowEnabled = landscapeStretchTransportRowEnabled,
                    )
                }
            }
        } else {
            // Narrower than full width -- a smaller card reads less bulky and leaves more of
            // the page background visible, with the title kept close underneath it (see the
            // small fixed gap below) rather than stranded in the middle of the screen.
            Spacer(modifier = Modifier.height(24.dp))
            MediaCard(
                modifier = Modifier.fillMaxWidth(MediaCardWidthFraction).aspectRatio(1f),
                effectiveShowConsole = effectiveShowConsole,
                logs = logs,
                track = track,
                containerAccent = containerAccent,
            )
            Spacer(modifier = Modifier.height(16.dp))
            // Idle shows when the last session ended; there's no "next track" data to show
            // while playing -- the daemon only ever logs its prefetch, it doesn't publish an
            // event/status field for it (checked against both the /events types and the
            // /status schema).
            if (!hasTrack) {
                Text(
                    text = lastSessionLabel(lastSessionEndAtMillis),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
            NowPlayingInfoAndControls(
                track = track,
                connectionState = connectionState,
                isServiceRunning = isServiceRunning,
                hasTrack = hasTrack,
                playAccent = playAccent,
                containerAccent = containerAccent,
                volumeSliderEnabled = effectiveVolumeSliderEnabled,
                volume = volume,
                localDeviceVolumeFraction = localDeviceVolumeFraction,
                effectiveShowConsole = effectiveShowConsole,
                onToggleConsole = onToggleConsole,
                onFakeSleep = onFakeSleep,
                onSettingsTap = {
                    haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
                    if (isServiceRunning) viewModel.toggleService() else onOpenSettings()
                },
                onSettingsLongPress = { haptics.performHapticFeedback(HapticFeedbackType.VirtualKey); onOpenSettings() },
                onSeek = viewModel::seek,
                onPrevious = viewModel::previous,
                onPlayPause = viewModel::playPause,
                onNext = viewModel::next,
                onStartServer = viewModel::toggleService,
                onVolumeChange = viewModel::setVolume,
                hideConsoleButtonEnabled = hideConsoleButtonEnabled,
                hideFakeSleepButtonEnabled = hideFakeSleepButtonEnabled,
            )
        }
        }
    }
    }
    }
}

/** The cover-art/console card, shared between portrait (full-width) and landscape (full-height)
 * layouts -- see [SimpleDashboardScreen]. The fake-sleep trigger lives in the icon row under
 * the transport controls (see [NowPlayingInfoAndControls]), not overlaid on this card. */
@Composable
private fun MediaCard(
    modifier: Modifier,
    effectiveShowConsole: Boolean,
    logs: List<LogEntry>,
    track: TrackInfo?,
    containerAccent: Color,
) {
    var activeLevels by remember { mutableStateOf(LogLevel.entries.toSet()) }
    var showConsoleOptions by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    val context = LocalContext.current

    val cardModifier = modifier
        .clip(RoundedCornerShape(28.dp))
        .background(if (effectiveShowConsole) ConsoleBackground else containerAccent)
    Box(
        // Long-press only wired up in console mode -- cover art has nothing to filter or copy.
        // No tap handler (the toggle button is where the "long-press for options" hint lives,
        // see SimpleDashboardScreen) -- a plain tap here does nothing.
        modifier = if (effectiveShowConsole) {
            cardModifier.combinedClickable(
                onClick = {},
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    showConsoleOptions = true
                },
            )
        } else {
            cardModifier
        },
    ) {
        // Crossfade rather than an instant swap -- covers both the console/cover-art toggle
        // and idle's cover-art/placeholder-icon switch, keyed on whichever of the two is
        // showing so it only replays when the shown content actually changes.
        Crossfade(targetState = effectiveShowConsole to track?.albumCoverUrl, animationSpec = tween(350), label = "mediaCardContent") { (showConsole, albumCoverUrl) ->
            if (showConsole) {
                ConsoleLogList(
                    logs = logs.filter { it.level in activeLevels },
                    modifier = Modifier.fillMaxSize(),
                )
            } else if (albumCoverUrl != null) {
                AsyncImage(
                    model = ImageRequest.Builder(context).data(albumCoverUrl).crossfade(true).build(),
                    contentDescription = stringResource(R.string.content_desc_album_art),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Icon(
                    Icons.Filled.MusicNote,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().wrapContentSize(Alignment.Center).size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (showConsoleOptions) {
        val clipboard = LocalClipboardManager.current
        val copiedToClipboardMessage = stringResource(R.string.toast_copied_to_clipboard)
        ConsoleOptionsSheet(
            activeLevels = activeLevels,
            onToggleLevel = { level -> activeLevels = if (level in activeLevels) activeLevels - level else activeLevels + level },
            onCopy = {
                val visible = logs.filter { it.level in activeLevels }
                clipboard.setText(AnnotatedString(visible.joinToString("\n") { "[${it.level}] ${it.message}" }))
                Toast.makeText(context, copiedToClipboardMessage, Toast.LENGTH_SHORT).show()
                showConsoleOptions = false
            },
            onDismiss = { showConsoleOptions = false },
        )
    }
}

/** Long-press options for the console view -- level filter chips plus a copy action, replacing
 * the separate header/filter-chip row [LogConsole] uses in the nerd-mode dashboard with a
 * sheet reached via long-press instead, since this card has no room for a persistent header. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConsoleOptionsSheet(
    activeLevels: Set<LogLevel>,
    onToggleLevel: (LogLevel) -> Unit,
    onCopy: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.console_options_log_levels_label), style = MaterialTheme.typography.labelLarge)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LogLevel.entries.forEach { level ->
                    FilterChip(
                        selected = level in activeLevels,
                        onClick = { onToggleLevel(level) },
                        label = {
                            Text(
                                level.name,
                                fontFamily = FontFamily.Monospace,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        },
                        leadingIcon = { LogLevelDot(level) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Button(onClick = onCopy, modifier = Modifier.fillMaxWidth()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = null)
                    Text(stringResource(R.string.console_options_copy_logs_button))
                }
            }
        }
    }
}

/** Fades the left and/or right edge of whatever this is applied to toward transparent -- used
 * on the marquee'd title (see [MarqueeTitleText]) so text scrolling out from under it reads as
 * gliding past a soft edge instead of getting hard-clipped by the layout bounds. Each side is
 * independently switchable so the fade can track which edge actually has content scrolled
 * under it at the moment (nothing to fade at an edge the text hasn't reached yet). Needs its
 * own offscreen compositing layer for [BlendMode.DstIn] to mask against the content drawn here
 * specifically, rather than whatever's already on the page background behind it. */
private fun Modifier.horizontalFadeEdges(
    edgeWidth: Dp = 24.dp,
    leftVisible: Boolean = true,
    rightVisible: Boolean = true,
): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val edgeFraction = (edgeWidth.toPx() / size.width).coerceIn(0f, 0.5f)
        if (edgeFraction > 0f && (leftVisible || rightVisible)) {
            drawRect(
                brush = Brush.horizontalGradient(
                    0f to if (leftVisible) Color.Transparent else Color.Black,
                    edgeFraction to Color.Black,
                    1f - edgeFraction to Color.Black,
                    1f to if (rightVisible) Color.Transparent else Color.Black,
                ),
                blendMode = BlendMode.DstIn,
            )
        }
    }

private val MarqueeVelocity = 30.dp
private const val MarqueePauseMillis = 1200L

/**
 * Hand-rolled replacement for [androidx.compose.foundation.basicMarquee] on the track title
 * specifically: basicMarquee always measures its child with an infinite width constraint (see
 * BasicMarquee.kt's MeasureScope.measure), so there's no way to read back from it whether the
 * text is actually overflowing, let alone which edge currently has content scrolled out from
 * under it -- both needed here so [horizontalFadeEdges] can fade only the edge(s) that actually
 * have something hidden under them at a given instant, rather than a static two-sided vignette
 * for the whole scroll. Drives its own [Animatable] offset instead so both are directly
 * readable. Not overflowing: sits at rest, centered or start-aligned per [leftAlignTrackInfo].
 * Overflowing: always start-anchored (scrolling only ever makes sense from a fixed edge),
 * bouncing back and forth -- pausing at each end, then animating the same distance back rather
 * than snapping -- instead of basicMarquee's own continuous one-direction, two-copy loop.
 */
@Composable
private fun MarqueeTitleText(
    text: String,
    style: TextStyle,
    leftAlignTrackInfo: Boolean,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    var availableWidthPx by remember { mutableIntStateOf(0) }
    val textWidthPx = remember(text, style) {
        textMeasurer.measure(AnnotatedString(text), style = style, maxLines = 1).size.width
    }
    val overflowPx = (textWidthPx - availableWidthPx).coerceAtLeast(0)
    val offsetX = remember { Animatable(0f) }

    LaunchedEffect(text, overflowPx) {
        offsetX.snapTo(0f)
        if (overflowPx <= 0) return@LaunchedEffect
        val pxPerSecond = with(density) { MarqueeVelocity.toPx() }
        val travelSpec = tween<Float>(
            durationMillis = (overflowPx / pxPerSecond * 1000f).roundToInt().coerceAtLeast(1),
            easing = LinearEasing,
        )
        // Bounces back and forth rather than snapping: scroll left to reveal the end, pause,
        // then scroll back right to the start instead of instantly jumping there -- the jump
        // was the "stutters and teleports" of it.
        while (isActive) {
            delay(MarqueePauseMillis)
            offsetX.animateTo(targetValue = -overflowPx.toFloat(), animationSpec = travelSpec)
            delay(MarqueePauseMillis)
            offsetX.animateTo(targetValue = 0f, animationSpec = travelSpec)
        }
    }

    // Only meaningful once actually scrolled: at rest (offset 0, including the whole time a
    // short title never overflows at all) neither edge has anything hidden under it.
    val leftFadeVisible = offsetX.value < -0.5f
    val rightFadeVisible = overflowPx > 0 && offsetX.value > -overflowPx + 0.5f

    Box(
        modifier = modifier
            .fillMaxWidth()
            .onSizeChanged { availableWidthPx = it.width }
            .clipToBounds()
            .let { if (overflowPx > 0) it.horizontalFadeEdges(leftVisible = leftFadeVisible, rightVisible = rightFadeVisible) else it },
        contentAlignment = if (overflowPx > 0 || leftAlignTrackInfo) Alignment.CenterStart else Alignment.Center,
    ) {
        Text(
            text,
            style = style,
            maxLines = 1,
            softWrap = false,
            // unbounded = true is the whole fix: without it, the Box's fillMaxWidth above
            // bounds this Text's own measurement too, so anything past that width gets
            // clipped away at layout time -- never actually laid out at all, regardless of
            // the offset animation -- instead of being fully measured and simply scrolled
            // into view. This is the direct replacement for what basicMarquee did internally
            // (see the class doc), just done explicitly instead of hidden inside it.
            modifier = Modifier
                .wrapContentWidth(unbounded = true, align = Alignment.Start)
                .offset { IntOffset(offsetX.value.roundToInt(), 0) },
        )
    }
}

/** Title/status text, wavy progress bar, transport row, the console/sleep/settings icon row,
 * and the optional volume slider -- shared between portrait (below the card) and landscape
 * (beside it) layouts. A [ColumnScope] extension so the weighted spacer below the title can
 * push the progress bar (and, at a fixed distance under it, the transport row/icons/volume,
 * which never move relative to the progress bar itself) down toward the bottom -- the title
 * stays put right under the card either way. */
@Composable
private fun ColumnScope.NowPlayingInfoAndControls(
    track: TrackInfo?,
    connectionState: ConnectionState,
    isServiceRunning: Boolean,
    hasTrack: Boolean,
    playAccent: Color,
    containerAccent: Color,
    volumeSliderEnabled: Boolean,
    volume: Pair<Int, Int>,
    localDeviceVolumeFraction: Float?,
    effectiveShowConsole: Boolean,
    onToggleConsole: () -> Unit,
    onFakeSleep: () -> Unit,
    onSettingsTap: () -> Unit,
    onSettingsLongPress: () -> Unit,
    onSeek: (Long) -> Unit,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onStartServer: () -> Unit,
    onVolumeChange: (Int) -> Unit,
    pushControlsToBottom: Boolean = true,
    leftAlignTrackInfo: Boolean = false,
    isLandscape: Boolean = false,
    hideConsoleButtonEnabled: Boolean = false,
    hideFakeSleepButtonEnabled: Boolean = false,
    hideIconRowInLandscapeEnabled: Boolean = false,
    landscapeStretchTransportRowEnabled: Boolean = false,
) {
    Column(
        horizontalAlignment = if (leftAlignTrackInfo) Alignment.Start else Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (track != null) {
            MarqueeTitleText(
                text = track.name,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                leftAlignTrackInfo = leftAlignTrackInfo,
            )
            Text(
                track.artistNames.joinToString(", "),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                textAlign = if (leftAlignTrackInfo) TextAlign.Start else TextAlign.Center,
                modifier = if (leftAlignTrackInfo) Modifier.fillMaxWidth() else Modifier,
            )
        } else {
            // A plain "Error" title here, not the shared label() (which prefixes the raw
            // daemon message onto the title for the nerd-mode status card, where there's no
            // second line to put it on) -- idleSubtitle() below already carries that detail.
            // Playing-with-no-track-yet is its own case too: the daemon reports playback
            // active a moment before it reports *what*, and "Playing"/"Playing" (label() and
            // idleSubtitle() both say the same thing here) read as a stuck/broken state rather
            // than the track info just being a beat behind.
            Text(
                when {
                    connectionState is ConnectionState.Error -> stringResource(R.string.idle_title_error)
                    connectionState == ConnectionState.Playing -> stringResource(R.string.idle_title_receiving_track_info)
                    else -> connectionState.label()
                },
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                if (connectionState == ConnectionState.Playing) stringResource(R.string.idle_subtitle_please_wait) else connectionState.idleSubtitle(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (pushControlsToBottom) {
        // Weighted rather than fixed: the title stays anchored right under the card and the
        // transport row/icons/volume stay anchored a fixed distance under the progress bar
        // (see the fixed gap below) -- this is the one gap that grows or shrinks to take up
        // whatever space is left, sliding the progress bar (and everything fixed under it)
        // down toward the bottom of the screen without moving the title or changing any other
        // spacing. Portrait only -- landscape has too little vertical room for a top-anchored
        // title and a bottom-anchored control cluster to read as anything but lopsided, so it
        // uses a fixed gap instead (see the caller) and centers as one block against the card.
        Spacer(modifier = Modifier.weight(1f))
    } else {
        Spacer(modifier = Modifier.height(20.dp))
    }

    // The wavy bar and its time labels are one visual unit (see PlaybackProgressBar) -- the
    // gap that follows is what separates that unit from the transport row, not anything
    // internal to it. Nothing to show a position/duration for without a track, so it's just
    // skipped rather than shown as an empty/placeholder bar.
    if (track != null) {
        PlaybackProgressBar(
            nowPlaying = track,
            isPlaying = connectionState == ConnectionState.Playing,
            accentColor = playAccent,
            onSeek = onSeek,
        )
    }

    // Fixed, not weighted -- just a bit more than the other fixed gaps in this stack.
    Spacer(modifier = Modifier.height(28.dp))

    TransportControlsRow(
        connectionState = connectionState,
        hasTrack = hasTrack,
        isServiceRunning = isServiceRunning,
        trackUri = track?.uri,
        accentColor = playAccent,
        secondaryColor = containerAccent,
        onPrevious = onPrevious,
        onPlayPause = onPlayPause,
        onNext = onNext,
        onStartServer = onStartServer,
        stretchFullWidth = isLandscape && landscapeStretchTransportRowEnabled,
    )

    // Landscape-only hide takes out the whole row, Settings included -- portrait is always
    // one rotation away, so it stays reachable rather than needing a separate escape hatch.
    if (!(isLandscape && hideIconRowInLandscapeEnabled)) {
        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterHorizontally),
        ) {
            // Console toggle and fake sleep are both meaningless before a server is running (no
            // console output worth toggling to yet, nothing playing to protect the screen from) --
            // hidden until then, leaving just Settings. Each can also be hidden unconditionally
            // via its own setting, independent of the landscape-only hide above.
            if (isServiceRunning) {
                if (!hideConsoleButtonEnabled) {
                    CornerIconButton(
                        icon = if (effectiveShowConsole) Icons.Filled.MusicNote else Icons.Filled.Terminal,
                        contentDescription = stringResource(if (effectiveShowConsole) R.string.content_desc_show_cover_art else R.string.content_desc_show_console),
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        enabled = hasTrack,
                        onClick = onToggleConsole,
                    )
                }
                if (!hideFakeSleepButtonEnabled) {
                    CornerIconButton(
                        icon = Icons.Filled.Bedtime,
                        contentDescription = stringResource(R.string.content_desc_fake_sleep),
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        onClick = onFakeSleep,
                    )
                }
            }
            // Gear while idle (tap goes straight to Settings); "link off" while running -- tap
            // stops the server, long-press still always reaches Settings. Never individually
            // hideable -- it's the only way back into Settings.
            CornerIconButton(
                icon = if (isServiceRunning) Icons.Filled.LinkOff else Icons.Filled.Settings,
                contentDescription = stringResource(if (isServiceRunning) R.string.content_desc_stop_long_press_settings else R.string.content_desc_settings),
                contentColor = MaterialTheme.colorScheme.onSurface,
                onClick = onSettingsTap,
                onLongClick = onSettingsLongPress,
            )
        }
    }

    if (volumeSliderEnabled) {
        Spacer(modifier = Modifier.height(16.dp))
        VolumeSlider(
            volume = volume,
            onVolumeChange = onVolumeChange,
            accentColor = playAccent,
            localOverrideFraction = localDeviceVolumeFraction,
        )
    }
}

private enum class TransportButton { NONE, PREVIOUS, PLAY_PAUSE, NEXT }

/**
 * Three buttons sharing one row, weight-sized rather than fixed-width -- pressing one squeezes
 * it larger and the other two smaller for a moment (matching the "press feedback" feel of
 * PixelPlayer's AnimatedPlaybackControls, an MIT-licensed reference as contributed before
 * 2026-05-12 -- see that project's THIRD_PARTY_NOTICES.md; this is an independent
 * reimplementation of the idea, not a copy of that code, wired to this app's own state). No
 * shared pill background behind them -- each button carries its own color/shape -- and the
 * center button's corner radius morphs between a rounder "paused" shape and a more pill-like
 * "playing" one.
 */
@Composable
private fun TransportControlsRow(
    connectionState: ConnectionState,
    hasTrack: Boolean,
    isServiceRunning: Boolean,
    trackUri: String?,
    accentColor: Color,
    secondaryColor: Color,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onStartServer: () -> Unit,
    stretchFullWidth: Boolean = false,
) {
    val haptics = LocalHapticFeedback.current
    var lastPressed by remember { mutableStateOf(TransportButton.NONE) }
    LaunchedEffect(lastPressed) {
        if (lastPressed != TransportButton.NONE) {
            delay(220)
            lastPressed = TransportButton.NONE
        }
    }
    // Previous/next are narrower than play/pause at rest (0.7 vs 1.6) -- they're
    // secondary actions, not the button someone's eye should land on first.
    fun baseWeightFor(button: TransportButton) = if (button == TransportButton.PLAY_PAUSE) 1.6f else 0.7f
    fun weightFor(button: TransportButton): Float {
        val pressMultiplier = when (lastPressed) {
            button -> 1.15f
            TransportButton.NONE -> 1f
            else -> 0.85f
        }
        return baseWeightFor(button) * pressMultiplier
    }
    val pressSpec = tween<Float>(220, easing = FastOutSlowInEasing)
    val isPlaying = connectionState == ConnectionState.Playing
    val onSecondaryColor = MaterialTheme.colorScheme.onSurface

    // Play/pause and track-change events reach this screen the same way whether they came
    // from a tap here or from another Spotify Connect client -- there's no signal that
    // distinguishes the two, so this just replays the same press-squish feedback either way,
    // skipping the very first composition so it doesn't pulse on initial load.
    var lastPlaybackSignature by remember { mutableStateOf<Pair<Boolean, String?>?>(null) }
    LaunchedEffect(isPlaying, trackUri) {
        val signature = isPlaying to trackUri
        if (lastPlaybackSignature != null && lastPlaybackSignature != signature) {
            lastPressed = TransportButton.PLAY_PAUSE
        }
        lastPlaybackSignature = signature
    }

    Row(
        modifier = Modifier
            .let { if (stretchFullWidth) it.fillMaxWidth() else it.fillMaxWidth(MediaCardWidthFraction) }
            .height(72.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Nothing to skip to/from without a track loaded -- hidden rather than just disabled,
        // so idle/no-track states show a single centered button instead of three.
        if (hasTrack) {
            val prevWeight by animateFloatAsState(weightFor(TransportButton.PREVIOUS), pressSpec, label = "prevWeight")
            TransportButtonBox(
                weight = prevWeight,
                shape = CircleShape,
                color = secondaryColor,
                enabled = true,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
                    lastPressed = TransportButton.PREVIOUS
                    onPrevious()
                },
            ) {
                Icon(Icons.Rounded.SkipPrevious, contentDescription = stringResource(R.string.content_desc_previous), tint = onSecondaryColor)
            }
        }

        val playWeight by animateFloatAsState(weightFor(TransportButton.PLAY_PAUSE), pressSpec, label = "playWeight")
        val playCorner by animateDpAsState(
            targetValue = if (isPlaying) 34.dp else 18.dp,
            animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
            label = "playCorner",
        )
        when {
            !isServiceRunning -> TransportButtonBox(
                weight = playWeight,
                shape = RoundedCornerShape(28.dp),
                color = accentColor,
                enabled = true,
                onClick = { haptics.performHapticFeedback(HapticFeedbackType.VirtualKey); onStartServer() },
            ) {
                Icon(
                    Icons.Filled.PowerSettingsNew,
                    contentDescription = stringResource(R.string.content_desc_start_server),
                    tint = Color.White,
                    modifier = Modifier.size(32.dp),
                )
            }
            hasTrack -> TransportButtonBox(
                weight = playWeight,
                shape = RoundedCornerShape(playCorner),
                color = accentColor,
                enabled = true,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
                    lastPressed = TransportButton.PLAY_PAUSE
                    onPlayPause()
                },
            ) {
                Icon(
                    if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = stringResource(R.string.content_desc_play_pause),
                    tint = Color.White,
                    modifier = Modifier.size(32.dp),
                )
            }
            // Running but no track loaded yet (e.g. Discoverable, waiting for a client):
            // nothing to control -- disabled, and alone in the row since prev/next are hidden.
            // Broadcast-on-home rather than a grayed-out play arrow: there's genuinely nothing
            // to "play" yet, and a disabled play icon reads as broken, not as "waiting".
            else -> TransportButtonBox(
                weight = playWeight,
                shape = RoundedCornerShape(playCorner),
                color = secondaryColor,
                enabled = false,
                onClick = {},
            ) {
                Icon(
                    Icons.Rounded.BroadcastOnHome,
                    contentDescription = stringResource(R.string.content_desc_discoverable),
                    tint = onSecondaryColor,
                    modifier = Modifier.size(32.dp),
                )
            }
        }

        if (hasTrack) {
            val nextWeight by animateFloatAsState(weightFor(TransportButton.NEXT), pressSpec, label = "nextWeight")
            TransportButtonBox(
                weight = nextWeight,
                shape = CircleShape,
                color = secondaryColor,
                enabled = true,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
                    lastPressed = TransportButton.NEXT
                    onNext()
                },
            ) {
                Icon(Icons.Rounded.SkipNext, contentDescription = stringResource(R.string.content_desc_next), tint = onSecondaryColor)
            }
        }
    }
}

@Composable
private fun RowScope.TransportButtonBox(
    weight: Float,
    shape: androidx.compose.ui.graphics.Shape,
    color: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .weight(weight)
            .fillMaxHeight()
            .clip(shape)
            .background(if (enabled) color else color.copy(alpha = 0.4f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

/** Icon only, no background box -- just the icon and its own tap/long-press target. */
@Composable
private fun CornerIconButton(
    icon: ImageVector,
    contentDescription: String?,
    contentColor: Color,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    enabled: Boolean = true,
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .combinedClickable(enabled = enabled, onClick = onClick, onLongClick = onLongClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = contentColor)
    }
}

@Composable
private fun lastSessionLabel(lastSessionEndAtMillis: Long?): String {
    if (lastSessionEndAtMillis == null) return stringResource(R.string.last_session_none)
    val elapsedMinutes = (System.currentTimeMillis() - lastSessionEndAtMillis).coerceAtLeast(0) / 60_000
    return when {
        elapsedMinutes < 1 -> stringResource(R.string.last_session_just_now)
        elapsedMinutes < 60 -> stringResource(R.string.last_session_minutes_ago_format, elapsedMinutes)
        elapsedMinutes < 1440 -> stringResource(R.string.last_session_hours_ago_format, elapsedMinutes / 60)
        else -> stringResource(R.string.last_session_days_ago_format, elapsedMinutes / 1440)
    }
}

@Composable
private fun ConnectionState.idleSubtitle(): String = when (this) {
    ConnectionState.Idle -> stringResource(R.string.idle_subtitle_server_not_running)
    ConnectionState.Starting -> stringResource(R.string.connection_state_starting)
    ConnectionState.Discoverable -> stringResource(R.string.idle_subtitle_discoverable)
    ConnectionState.Paused -> stringResource(R.string.connection_state_paused)
    ConnectionState.Playing -> stringResource(R.string.connection_state_playing)
    is ConnectionState.Error -> message
}

