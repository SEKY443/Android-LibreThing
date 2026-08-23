package io.github.seky443.librething.ui.dashboard

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import android.content.Context
import android.content.res.Configuration
import android.os.VibrationEffect
import android.os.Vibrator
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.roundToInt

/** Width of the cover-art card as a fraction of the available content width (portrait) --
 * also used by [TransportControlsRow] so the transport row lines up edge-to-edge with the
 * card above it instead of spanning the full column width. */
private const val MediaCardWidthFraction = 0.78f

// Gesture-drag haptic tuning lives in GestureHaptics, shared with BlackScreenOverlayController's
// own copy of the same gestures on the fake-sleep overlay.

// MediaCard's swipe-to-skip cover transition (scale/slide, see triggerCoverSkipTransition).
private const val COVER_TRANSITION_EXIT_SCALE = 0.75f
private const val COVER_TRANSITION_SCALE_MS = 160
private const val COVER_TRANSITION_SLIDE_MS = 200
private const val COVER_TRANSITION_WAIT_TIMEOUT_MS = 4000L
// Fraction of the card's own width, not a pixel value -- resolved against the actual
// graphicsLayer size in coverSkipTransition. >1 so the cover fully clears the card at any
// aspect before the entrance animation snaps it to the opposite side.
private const val COVER_TRANSITION_OFFSET_MAGNITUDE = 1.3f
// The *drag itself* is damped, not the post-release animations: instead of snapping the scale
// exactly to the raw drag distance every tick, it chases that live target with critically-damped
// spring physics (no bounce, dampingRatio 1) -- gives the shrink some resistance/weight under
// the finger rather than pinning to it 1:1.
private val CoverTransitionDragChaseSpring = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 300f)

/** Scale/slide for [MediaCard]'s swipe-to-skip cover transition -- graphicsLayer reads the
 * Animatables at draw time only, so the rest of the card doesn't recompose every animation
 * frame (see AppNavHost's Settings-panel stutter fix for why that matters). */
private fun Modifier.coverSkipTransition(scale: Animatable<Float, AnimationVector1D>, offsetFraction: Animatable<Float, AnimationVector1D>): Modifier =
    this.graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
        translationX = offsetFraction.value * size.width
    }

/**
 * Full-screen counterpart to [MediaCard]'s own cover-scoped swipe/double-tap gestures, behind
 * `AppPreferences.gestureControlsFullScreenEnabled` -- same double-tap/skip/volume behavior, just
 * anywhere on the dashboard instead of only the cover art, including the same live shrink-while-
 * dragging preview and release-triggered slide MediaCard's own gesture drives (via the same
 * shared coverTransitionScale/coverTransitionOffset/isCoverTransitioningState -- see
 * SimpleDashboardScreen). A deliberately separate copy rather than sharing MediaCard's
 * implementation outright: that one is threaded through at several specific points to drive the
 * cover-shrink animation's state, and generalizing that coupling risked destabilizing a gesture
 * path that had already broken (and been fixed) more than once earlier in this feature's life.
 * This one instead requires every pointer event to still be unconsumed as it goes, bailing the
 * instant a descendant (a button, the volume slider, the console's own scrolling) claims the
 * touch, so it only ever fires on parts of the screen nothing else is already handling. See the
 * call site for why that's safe to assume: this modifier lives on the page's outermost Box, an
 * ancestor of every other interactive element on it, and Compose dispatches pointer events to
 * descendants first within a pass.
 */
private fun Modifier.fullScreenPlaybackGestures(
    vibrator: Vibrator,
    haptics: HapticFeedback,
    hapticIntensity: () -> Float,
    volume: () -> Pair<Int, Int>,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onVolumeChange: (Int) -> Unit,
    // Cover-shrink animation, shared with MediaCard's own on-cover gesture (see
    // SimpleDashboardScreen's lifted coverTransitionScale/coverTransitionOffset/
    // isCoverTransitioningState) -- coverDragScope drives the live damped chase below the same
    // way MediaCard's own ACTION_MOVE handling does, since AwaitPointerEventScope can't directly
    // await Animatable.animateTo itself.
    coverTransitionScale: Animatable<Float, AnimationVector1D>,
    coverDragScope: CoroutineScope,
    isCoverTransitioningState: MutableState<Boolean>,
    triggerCoverSkipTransition: (exitDirection: Float) -> Unit,
    springCoverBack: () -> Unit,
): Modifier = pointerInput(Unit) {
    val skipThresholdPx = 72.dp.toPx()
    val volumeJitterThresholdPx = 24.dp.toPx()
    val doubleTapTimeoutMillis = viewConfiguration.doubleTapTimeoutMillis
    val touchSlopPx = viewConfiguration.touchSlop
    var lastTapUpMillis = 0L

    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = true)
        var pointerId = down.id
        var totalDragX = 0f
        var totalDragY = 0f
        var isDrag = false
        val dragStartVolume = volume().first
        var lastHapticStep = -1
        var skipHapticFired = false

        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == pointerId } ?: break
            // Something else (a button, the slider, the console's own scroll) already claimed
            // this touch sequence -- back off entirely rather than also acting on it.
            if (change.isConsumed) break
            if (!change.pressed) {
                if (isDrag) {
                    if (abs(totalDragX) > abs(totalDragY)) {
                        if (abs(totalDragX) > skipThresholdPx) {
                            if (totalDragX < 0) {
                                triggerCoverSkipTransition(-COVER_TRANSITION_OFFSET_MAGNITUDE)
                                onNext()
                            } else {
                                triggerCoverSkipTransition(COVER_TRANSITION_OFFSET_MAGNITUDE)
                                onPrevious()
                            }
                        } else {
                            // Let go before committing -- spring the live-shrunk cover back to
                            // full size instead of leaving it stuck mid-shrink.
                            springCoverBack()
                        }
                    } else if (abs(totalDragY) > volumeJitterThresholdPx) {
                        val max = volume().second
                        if (max > 0) {
                            val target = (dragStartVolume - (totalDragY / size.height) * max).roundToInt().coerceIn(0, max)
                            onVolumeChange(target)
                        }
                    }
                } else if (change.uptimeMillis - lastTapUpMillis <= doubleTapTimeoutMillis) {
                    haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
                    onPlayPause()
                    lastTapUpMillis = 0L
                } else {
                    lastTapUpMillis = change.uptimeMillis
                }
                break
            }

            val posChange = change.position - change.previousPosition
            totalDragX += posChange.x
            totalDragY += posChange.y
            if (!isDrag && (abs(totalDragX) > touchSlopPx || abs(totalDragY) > touchSlopPx)) {
                isDrag = true
            }
            if (isDrag) {
                change.consume()
                if (abs(totalDragY) > abs(totalDragX)) {
                    // A drag that started leaning horizontal (shrinking the cover live) can still
                    // turn out to be a volume adjustment once the finger moves more vertically --
                    // spring the cover back rather than leaving it stuck shrunk while volume
                    // takes over, matching MediaCard's own on-cover gesture.
                    springCoverBack()
                    val max = volume().second
                    if (max > 0) {
                        val target = (dragStartVolume - (totalDragY / size.height) * max).roundToInt().coerceIn(0, max)
                        val step = target * GestureHaptics.HAPTIC_STEPS / max
                        if (step != lastHapticStep) {
                            lastHapticStep = step
                            val fraction = target.toFloat() / max.toFloat()
                            val curve = GestureHaptics.MIN_VOLUME_HAPTIC_SCALE + fraction * (1f - GestureHaptics.MIN_VOLUME_HAPTIC_SCALE)
                            GestureHaptics.vibratePrimitive(
                                vibrator,
                                VibrationEffect.Composition.PRIMITIVE_TICK,
                                curve * hapticIntensity(),
                                GestureHaptics.VOLUME_HAPTIC_DURATION_MS,
                                GestureHaptics.VOLUME_HAPTIC_FALLBACK_AMPLITUDE,
                            )
                        }
                    }
                } else {
                    // Live preview: shrinks with how far the finger has moved, damped the same
                    // way MediaCard's own on-cover gesture is (see CoverTransitionDragChaseSpring)
                    // -- launched, not awaited inline, since AwaitPointerEventScope can't directly
                    // call a suspend function like Animatable.animateTo.
                    val dragProgress = (abs(totalDragX) / skipThresholdPx).coerceIn(0f, 1f)
                    if (dragProgress > 0f) isCoverTransitioningState.value = true
                    coverDragScope.launch {
                        coverTransitionScale.animateTo(
                            1f - dragProgress * (1f - COVER_TRANSITION_EXIT_SCALE),
                            CoverTransitionDragChaseSpring,
                        )
                    }

                    val pastThreshold = abs(totalDragX) > skipThresholdPx
                    if (pastThreshold && !skipHapticFired) {
                        skipHapticFired = true
                        GestureHaptics.vibratePrimitive(
                            vibrator,
                            VibrationEffect.Composition.PRIMITIVE_CLICK,
                            hapticIntensity(),
                            GestureHaptics.SKIP_HAPTIC_DURATION_MS,
                            GestureHaptics.SKIP_HAPTIC_FALLBACK_AMPLITUDE,
                        )
                    } else if (!pastThreshold) {
                        skipHapticFired = false
                    }
                }
            }
            pointerId = change.id
        }
    }
}

// Deliberately more than could ever fit any realistic card, so there's always enough to overflow
// the available height rather than run short -- the trade for guaranteeing that is the top entry
// sometimes getting clipped mid-row.
private const val TRANSITION_CONSOLE_PEEK_LINES = 40

/** A lighter stand-in for [ConsoleLogList] behind the cover-skip transition (see [MediaCard]):
 * just the tail of whatever [logs] currently is, no scroll state/LazyColumn/level filtering --
 * those exist for the console button's actual full view, not a few hundred milliseconds of peek
 * behind a shrinking cover. Renders each entry via the exact same [LogEntryRow] that view uses,
 * so the two look identical rather than just similar. Recomposes for free as [logs] updates.
 *
 * Bottom-aligned with deliberately more entries than could ever fit, so it reliably fills the
 * frame. */
@Composable
private fun TransitionConsolePeek(logs: List<LogEntry>, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp, alignment = Alignment.Bottom),
    ) {
        logs.takeLast(TRANSITION_CONSOLE_PEEK_LINES).forEach { entry -> LogEntryRow(entry) }
    }
}

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
    val gestureControlsEnabled by viewModel.gestureControlsEnabled.collectAsState()
    val gestureHapticIntensity by viewModel.gestureHapticIntensity.collectAsState()
    val gestureTransitionShowConsoleEnabled by viewModel.gestureTransitionShowConsoleEnabled.collectAsState()
    val gestureTransitionRoundedCoverEnabled by viewModel.gestureTransitionRoundedCoverEnabled.collectAsState()
    val gestureControlsFullScreenEnabled by viewModel.gestureControlsFullScreenEnabled.collectAsState()
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

    // Only actually built when the full-screen gesture area setting is on -- everyone else
    // pays nothing for it.
    val fullScreenGestureVibrator = if (gestureControlsFullScreenEnabled) remember { GestureHaptics.vibratorFor(context) } else null

    // Swipe-to-skip's cover-art transition, lifted up here (out of MediaCard) so both MediaCard's
    // own on-cover gesture and fullScreenPlaybackGestures below (a swipe starting anywhere else on
    // screen) can trigger the exact same shrink/slide animation -- see MediaCard's kdoc comment on
    // its coverTransitionScale/coverTransitionOffset/isCoverTransitioningState parameters for the
    // full behavior description.
    val coverTransitionScale = remember { Animatable(1f) }
    val coverTransitionOffset = remember { Animatable(0f) }
    val isCoverTransitioningState = remember { mutableStateOf(false) }
    var isCoverTransitioning by isCoverTransitioningState
    var coverTransitionJob by remember { mutableStateOf<Job?>(null) }
    val coverTransitionScope = rememberCoroutineScope()
    val currentTrackUri by rememberUpdatedState(track?.uri)

    fun triggerCoverSkipTransition(exitDirection: Float) {
        coverTransitionJob?.cancel()
        coverTransitionJob = coverTransitionScope.launch {
            isCoverTransitioning = true
            val trackAtStart = currentTrackUri
            // Scale's own damped chase toward the drag distance (see MediaCard's ACTION_MOVE
            // handling) may still be a little behind at the exact moment of release -- finish it
            // instantly here rather than let the tail of that catch-up play out alongside the
            // slide-out below. A full-screen-gesture skip has no such chase (it commits straight
            // from the release), so this snap is a no-op there.
            coverTransitionScale.snapTo(COVER_TRANSITION_EXIT_SCALE)
            coverTransitionOffset.animateTo(exitDirection, tween(COVER_TRANSITION_SLIDE_MS, easing = FastOutSlowInEasing))
            // Bounded wait for the actual track change (a skip can be a no-op, e.g. at the end
            // of a non-repeating queue) -- past the timeout, just settle back as if nothing
            // happened rather than leaving the cover stranded off-screen.
            withTimeoutOrNull(COVER_TRANSITION_WAIT_TIMEOUT_MS) {
                snapshotFlow { currentTrackUri }.first { it != trackAtStart }
            }
            coverTransitionOffset.snapTo(-exitDirection)
            coverTransitionScale.snapTo(COVER_TRANSITION_EXIT_SCALE)
            // Mirrored on the way in: slide back to center while still small, then grow.
            coverTransitionOffset.animateTo(0f, tween(COVER_TRANSITION_SLIDE_MS, easing = FastOutSlowInEasing))
            coverTransitionScale.animateTo(1f, tween(COVER_TRANSITION_SCALE_MS, easing = FastOutSlowInEasing))
            isCoverTransitioning = false
        }
    }

    // Springs a live-shrunk (but not yet committed) cover back to full size -- reached both when
    // a horizontal drag on the cover lets go before crossing the skip threshold, and when the
    // drag turns out to be a volume adjustment instead. No-ops if a spring-back is already in
    // flight, so repeated calls while e.g. still dragging vertically don't keep restarting it from
    // scratch every tick. Never called from fullScreenPlaybackGestures -- that gesture only ever
    // commits a skip on release, it has no live mid-drag shrink to spring back from.
    fun springCoverBack() {
        if (!isCoverTransitioning || coverTransitionJob?.isActive == true) return
        coverTransitionJob = coverTransitionScope.launch {
            coverTransitionScale.animateTo(1f, tween(COVER_TRANSITION_SCALE_MS, easing = FastOutSlowInEasing))
            isCoverTransitioning = false
        }
    }

    MaterialTheme(colorScheme = contentColorScheme, typography = MaterialTheme.typography) {
    // Text with no explicit `color` (the title, notably) falls back to LocalContentColor, not
    // colorScheme.onBackground -- MaterialTheme doesn't set that on its own (only Surface
    // does), and its own platform default is plain black, invisible on a black background.
    CompositionLocalProvider(LocalContentColor provides contentColorScheme.onBackground) {
    Box(
        // Ancestor of every button/slider/console on the page, not a sibling -- Compose
        // dispatches pointer events to descendants first within a pass, so a button's own click
        // recognizer gets first claim on a touch landing on it (and consumes it) before this
        // gesture detector, further up the tree, ever sees it. fullScreenPlaybackGestures checks
        // for exactly that consumption and backs off, so it only ever fires on parts of the
        // screen nothing else already claimed.
        modifier = if (gestureControlsEnabled && gestureControlsFullScreenEnabled && hasTrack && fullScreenGestureVibrator != null) {
            Modifier.fillMaxSize().fullScreenPlaybackGestures(
                vibrator = fullScreenGestureVibrator,
                haptics = haptics,
                hapticIntensity = { gestureHapticIntensity },
                volume = { volume },
                onPlayPause = viewModel::playPause,
                onNext = viewModel::next,
                onPrevious = viewModel::previous,
                onVolumeChange = viewModel::setVolume,
                coverTransitionScale = coverTransitionScale,
                coverDragScope = coverTransitionScope,
                isCoverTransitioningState = isCoverTransitioningState,
                triggerCoverSkipTransition = ::triggerCoverSkipTransition,
                springCoverBack = ::springCoverBack,
            )
        } else {
            Modifier.fillMaxSize()
        },
    ) {
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
                        gestureControlsEnabled = gestureControlsEnabled,
                        gestureHapticIntensity = gestureHapticIntensity,
                        gestureTransitionShowConsoleEnabled = gestureTransitionShowConsoleEnabled,
                        gestureTransitionRoundedCoverEnabled = gestureTransitionRoundedCoverEnabled,
                        hasTrack = hasTrack,
                        volume = volume,
                        onPlayPause = viewModel::playPause,
                        onNext = viewModel::next,
                        onPrevious = viewModel::previous,
                        onVolumeChange = viewModel::setVolume,
                        coverTransitionScale = coverTransitionScale,
                        coverTransitionOffset = coverTransitionOffset,
                        isCoverTransitioningState = isCoverTransitioningState,
                        triggerCoverSkipTransition = ::triggerCoverSkipTransition,
                        springCoverBack = ::springCoverBack,
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
                gestureControlsEnabled = gestureControlsEnabled,
                gestureHapticIntensity = gestureHapticIntensity,
                gestureTransitionShowConsoleEnabled = gestureTransitionShowConsoleEnabled,
                gestureTransitionRoundedCoverEnabled = gestureTransitionRoundedCoverEnabled,
                hasTrack = hasTrack,
                volume = volume,
                onPlayPause = viewModel::playPause,
                onNext = viewModel::next,
                onPrevious = viewModel::previous,
                onVolumeChange = viewModel::setVolume,
                coverTransitionScale = coverTransitionScale,
                coverTransitionOffset = coverTransitionOffset,
                isCoverTransitioningState = isCoverTransitioningState,
                triggerCoverSkipTransition = ::triggerCoverSkipTransition,
                springCoverBack = ::springCoverBack,
            )
            Spacer(modifier = Modifier.height(16.dp))
            // Idle shows the last-session label here; while playing there's no equivalent
            // "next track" data to show (the daemon only ever logs its prefetch, it doesn't
            // publish an event/status field for it -- checked against both the /events types
            // and the /status schema). Kept laid out but invisible instead of omitted so the
            // title/artist below don't jump up when a track starts playing.
            Text(
                text = lastSessionLabel(lastSessionEndAtMillis),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().alpha(if (hasTrack) 0f else 1f),
            )
            Spacer(modifier = Modifier.height(16.dp))
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
    gestureControlsEnabled: Boolean = false,
    gestureHapticIntensity: Float = 1f,
    gestureTransitionShowConsoleEnabled: Boolean = false,
    gestureTransitionRoundedCoverEnabled: Boolean = true,
    hasTrack: Boolean = false,
    volume: Pair<Int, Int> = 0 to 0,
    onPlayPause: () -> Unit = {},
    onNext: () -> Unit = {},
    onPrevious: () -> Unit = {},
    onVolumeChange: (Int) -> Unit = {},
    // Cover-skip transition state/triggers, lifted up to SimpleDashboardScreen so
    // fullScreenPlaybackGestures can drive the exact same animation as this card's own gesture
    // below -- see that function's kdoc.
    coverTransitionScale: Animatable<Float, AnimationVector1D> = remember { Animatable(1f) },
    coverTransitionOffset: Animatable<Float, AnimationVector1D> = remember { Animatable(0f) },
    isCoverTransitioningState: MutableState<Boolean> = remember { mutableStateOf(false) },
    triggerCoverSkipTransition: (Float) -> Unit = {},
    springCoverBack: () -> Unit = {},
) {
    var activeLevels by remember { mutableStateOf(LogLevel.entries.toSet()) }
    var showConsoleOptions by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    val context = LocalContext.current


    // Compose's HapticFeedbackType has no amplitude control -- it always plays a fixed,
    // device-defined effect. The volume drag wants its "detent" to physically feel louder as
    // the target volume climbs, which needs the raw Vibrator API instead.
    val vibrator = remember { GestureHaptics.vibratorFor(context) }

    // Read fresh inside the gesture coroutines below despite pointerInput(Unit) never
    // restarting them -- otherwise they'd keep closing over whatever these were on first launch.
    val currentVolume by rememberUpdatedState(volume)
    val currentHapticIntensity by rememberUpdatedState(gestureHapticIntensity)
    val currentOnPlayPause by rememberUpdatedState(onPlayPause)
    val currentOnNext by rememberUpdatedState(onNext)
    val currentOnPrevious by rememberUpdatedState(onPrevious)
    val currentOnVolumeChange by rememberUpdatedState(onVolumeChange)

    // Swipe-to-skip's own cover-art transition: shrinks and slides the outgoing cover off in the
    // swipe direction (revealing the card's plain black background underneath, not the themed
    // containerAccent -- see cardModifier below), then once the new track's cover actually shows
    // up, slides the replacement in from the opposite side and back up to full size. Purely
    // decorative on top of Crossfade's own cover-art cross-dissolve (which still runs when the
    // albumCoverUrl itself changes) -- this only drives scale/position, graphicsLayer-read so it
    // doesn't recompose the rest of the card every frame (see AppNavHost's Settings-panel fix for
    // why that matters). Triggered on release, not live mid-drag -- an earlier attempt to fire it
    // (and next()/previous()) the instant the drag crossed the skip threshold caused runaway
    // repeat skips, so this stays release-triggered until that's root-caused. State and the two
    // trigger functions now live in the caller (SimpleDashboardScreen) so fullScreenPlaybackGestures
    // can drive this same animation from a swipe that starts anywhere on screen, not just on the
    // cover -- see this function's coverTransitionScale/coverTransitionOffset/
    // isCoverTransitioningState/triggerCoverSkipTransition/springCoverBack parameters. The live
    // damped drag-chase below (see ACTION_MOVE handling) stays purely local, though -- it's driven
    // straight off this card's own drag, fullScreenPlaybackGestures has no equivalent live phase.
    var isCoverTransitioning by isCoverTransitioningState
    val coverTransitionScope = rememberCoroutineScope()

    val cardModifier = modifier
        .clip(RoundedCornerShape(28.dp))
        .background(
            when {
                isCoverTransitioning -> Color.Black
                effectiveShowConsole -> ConsoleBackground
                else -> containerAccent
            },
        )
    Box(
        // Long-press only wired up in console mode -- cover art has nothing to filter or copy.
        // No tap handler (the toggle button is where the "long-press for options" hint lives,
        // see SimpleDashboardScreen) -- a plain tap here does nothing, other than the gesture
        // controls below when those are on. Console mode never gets gestures even if enabled --
        // the log list needs vertical drags for its own scrolling, and long-press is already
        // spoken for by the options sheet.
        modifier = if (effectiveShowConsole) {
            cardModifier.combinedClickable(
                onClick = {},
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    showConsoleOptions = true
                },
            )
        } else if (gestureControlsEnabled && hasTrack) {
            // A single hand-rolled recognizer instead of separate detectTapGestures +
            // detectDragGestures pointerInput blocks -- two independent detectors chained on the
            // same node compete over the same touch stream (the drag detector's own touch-slop
            // consumption can starve the tap detector of events it needs), which made both
            // double-tap and swipes unreliable in practice. One state machine per gesture avoids
            // that entirely: it classifies drag-vs-tap itself via touch slop, and double-tap via
            // a plain timestamp comparison between successive taps (Android's own double-tap
            // timeout, not a fixed guess) instead of relying on two detectors to cooperate.
            cardModifier.pointerInput(Unit) {
                val skipThresholdPx = 72.dp.toPx()
                val volumeJitterThresholdPx = 24.dp.toPx()
                val doubleTapTimeoutMillis = viewConfiguration.doubleTapTimeoutMillis
                val touchSlopPx = viewConfiguration.touchSlop
                var lastTapUpMillis = 0L

                awaitEachGesture {
                    val down = awaitFirstDown()
                    var pointerId = down.id
                    var totalDragX = 0f
                    var totalDragY = 0f
                    var isDrag = false
                    val dragStartVolume = currentVolume.first
                    var lastHapticStep = -1
                    // Fires once, live, the moment the swipe crosses the skip threshold -- not
                    // repeated on every subsequent move tick while still past it, and reset if
                    // the drag comes back under threshold so crossing again re-fires it.
                    var skipHapticFired = false

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                        if (!change.pressed) {
                            // Consumed unconditionally, not just on a drag -- a plain tap release
                            // used to fall through unconsumed, which let fullScreenPlaybackGestures
                            // (an ancestor of this card once that setting is on) independently see
                            // the same up event and track its own double-tap timing on top of this
                            // one, firing onPlayPause() a second time a moment after this gesture's
                            // own call and toggling straight back. Consuming here makes this
                            // gesture the sole owner of any touch that started on the cover,
                            // matching how a drag already behaved.
                            change.consume()
                            if (isDrag) {
                                if (abs(totalDragX) > abs(totalDragY)) {
                                    if (abs(totalDragX) > skipThresholdPx) {
                                        if (totalDragX < 0) {
                                            triggerCoverSkipTransition(-COVER_TRANSITION_OFFSET_MAGNITUDE)
                                            currentOnNext()
                                        } else {
                                            triggerCoverSkipTransition(COVER_TRANSITION_OFFSET_MAGNITUDE)
                                            currentOnPrevious()
                                        }
                                    } else {
                                        // Let go before committing -- spring the live-shrunk
                                        // cover back to full size instead of leaving it stuck
                                        // mid-shrink.
                                        springCoverBack()
                                    }
                                } else if (abs(totalDragY) > volumeJitterThresholdPx) {
                                    val max = currentVolume.second
                                    if (max > 0) {
                                        val target = (dragStartVolume - (totalDragY / size.height) * max).roundToInt().coerceIn(0, max)
                                        currentOnVolumeChange(target)
                                    }
                                }
                            } else if (change.uptimeMillis - lastTapUpMillis <= doubleTapTimeoutMillis) {
                                haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
                                currentOnPlayPause()
                                lastTapUpMillis = 0L
                            } else {
                                lastTapUpMillis = change.uptimeMillis
                            }
                            break
                        }

                        val posChange = change.position - change.previousPosition
                        totalDragX += posChange.x
                        totalDragY += posChange.y
                        if (!isDrag && (abs(totalDragX) > touchSlopPx || abs(totalDragY) > touchSlopPx)) {
                            isDrag = true
                        }
                        if (isDrag) {
                            change.consume()
                            // Live feedback only -- the daemon/callbacks aren't told anything
                            // until release above, same tradeoff as VolumeSlider's own drag
                            // handling (see its kdoc): a call per pixel of drag is what makes
                            // dragging feel stuttery.
                            if (abs(totalDragY) > abs(totalDragX)) {
                                // A drag that started leaning horizontal (shrinking the cover
                                // live) can still turn out to be a volume adjustment once the
                                // finger moves more vertically -- spring the cover back rather
                                // than leaving it stuck shrunk while volume takes over.
                                springCoverBack()
                                val max = currentVolume.second
                                if (max > 0) {
                                    val target = (dragStartVolume - (totalDragY / size.height) * max).roundToInt().coerceIn(0, max)
                                    val step = target * GestureHaptics.HAPTIC_STEPS / max
                                    if (step != lastHapticStep) {
                                        lastHapticStep = step
                                        // Louder as the target volume climbs, so the "detent"
                                        // itself hints at how loud you're about to make it --
                                        // then the user's own overall intensity preference on
                                        // top of that curve.
                                        val fraction = target.toFloat() / max.toFloat()
                                        val curve = GestureHaptics.MIN_VOLUME_HAPTIC_SCALE + fraction * (1f - GestureHaptics.MIN_VOLUME_HAPTIC_SCALE)
                                        GestureHaptics.vibratePrimitive(
                                            vibrator,
                                            VibrationEffect.Composition.PRIMITIVE_TICK,
                                            curve * currentHapticIntensity,
                                            GestureHaptics.VOLUME_HAPTIC_DURATION_MS,
                                            GestureHaptics.VOLUME_HAPTIC_FALLBACK_AMPLITUDE,
                                        )
                                    }
                                }
                            } else {
                                // Shrinks with how far the finger has moved, but damped rather
                                // than pinned exactly to it: re-targeting an already-running
                                // animateTo on every tick makes the value chase the live drag
                                // distance with a bit of resistance/lag instead of snapping
                                // straight to it, like it has some weight under the finger.
                                // Sliding away only happens afterwards, on release (see below).
                                val dragProgress = (abs(totalDragX) / skipThresholdPx).coerceIn(0f, 1f)
                                if (dragProgress > 0f) isCoverTransitioning = true
                                // Launched, not awaited inline: AwaitPointerEventScope is a
                                // restricted suspend scope, it can't directly call an arbitrary
                                // suspend function like Animatable.animateTo.
                                coverTransitionScope.launch {
                                    coverTransitionScale.animateTo(
                                        1f - dragProgress * (1f - COVER_TRANSITION_EXIT_SCALE),
                                        CoverTransitionDragChaseSpring,
                                    )
                                }

                                val pastThreshold = abs(totalDragX) > skipThresholdPx
                                if (pastThreshold && !skipHapticFired) {
                                    skipHapticFired = true
                                    // Fixed, firmer than any volume tick -- a "this WILL skip if
                                    // you let go now" click, not a graded detent.
                                    GestureHaptics.vibratePrimitive(
                                        vibrator,
                                        VibrationEffect.Composition.PRIMITIVE_CLICK,
                                        currentHapticIntensity,
                                        GestureHaptics.SKIP_HAPTIC_DURATION_MS,
                                        GestureHaptics.SKIP_HAPTIC_FALLBACK_AMPLITUDE,
                                    )
                                } else if (!pastThreshold) {
                                    skipHapticFired = false
                                }
                            }
                        }
                        pointerId = change.id
                    }
                }
            }
        } else {
            cardModifier
        },
    ) {
        // Plain if, not a Crossfade/AnimatedVisibility -- disappears the instant
        // isCoverTransitioning goes false (see springCoverBack/triggerCoverSkipTransition), no
        // lingering fade-out. Drawn first so it sits behind the shrinking cover in the Crossfade
        // below, showing through the space the cover opens up as it shrinks.
        if (isCoverTransitioning && gestureTransitionShowConsoleEnabled) {
            TransitionConsolePeek(logs = logs, modifier = Modifier.fillMaxSize())
        }
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
                    // Clipped *after* (inside) the scale/translate transform, not before --
                    // Modifier chain order puts an earlier .clip() outside the later
                    // graphicsLayer, so it'd clip a fixed full-size rounded rect around the
                    // now-shrunk image instead of shrinking with it, making it invisible (the
                    // small image never reaches those far-away edges). Applied after
                    // coverSkipTransition instead, the clip sits inside that same layer and gets
                    // scaled down as part of it. At scale=1 this is a no-op, since the card's own
                    // outer clip already matches it exactly.
                    modifier = Modifier.fillMaxSize()
                        .coverSkipTransition(coverTransitionScale, coverTransitionOffset)
                        .let { if (gestureTransitionRoundedCoverEnabled) it.clip(RoundedCornerShape(28.dp)) else it },
                    contentScale = ContentScale.Crop,
                )
            } else {
                Icon(
                    Icons.Filled.MusicNote,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().wrapContentSize(Alignment.Center).size(64.dp)
                        .coverSkipTransition(coverTransitionScale, coverTransitionOffset),
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

