package io.github.seky443.librething.ui.navigation

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Color as AndroidColor
import android.graphics.Shader
import android.net.Uri
import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.seky443.librething.R
import io.github.seky443.librething.data.AppPreferences
import io.github.seky443.librething.data.DashboardBackgroundStyle
import io.github.seky443.librething.service.model.DeviceAuthPrompt
import io.github.seky443.librething.ui.dashboard.DashboardScreen
import io.github.seky443.librething.ui.dashboard.DashboardViewModel
import io.github.seky443.librething.ui.dashboard.SimpleDashboardScreen
import io.github.seky443.librething.ui.dashboard.StartStopFab
import io.github.seky443.librething.ui.settings.SettingsScreen
import io.github.seky443.librething.ui.settings.SettingsViewModel
import io.github.seky443.librething.util.UpdateInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Branches on [io.github.seky443.librething.data.AppPreferences.nerdModeEnabled]: on, this
 * is today's two-tab [HorizontalPager] + bottom nav shell around [DashboardScreen]; off (the
 * default), it's just [SimpleDashboardScreen] full-screen with no bottom nav at all, matching
 * that screen's own top-right icon being the sole way to Settings.
 */
@Composable
fun AppNavHost(onSuppressSystemVolumePanelChange: (Boolean) -> Unit) {
    // Hoisted here rather than inside either branch below: switching nerdModeEnabled shouldn't
    // recreate the ViewModel backing whichever screen is on screen.
    val dashboardViewModel = viewModel<DashboardViewModel>()
    val nerdModeEnabled by dashboardViewModel.nerdModeEnabled.collectAsState()

    // Both OLED-protection effects apply at this outermost level (not inside either branch)
    // so they cover Settings as well as the Dashboard, nerd mode or not -- rather than needing
    // to be threaded into each screen individually.
    val oledPixelShiftEnabled by dashboardViewModel.oledPixelShiftEnabled.collectAsState()
    val oledCheckerboardDimEnabled by dashboardViewModel.oledCheckerboardDimEnabled.collectAsState()
    val pixelShiftOffset = rememberOledPixelShiftOffset(oledPixelShiftEnabled)

    // Scheduled display filters -- same outermost-level placement as the OLED effects above,
    // for the same reason (covers Settings too, not just the Dashboard).
    val grayscaleFilterEnabled by dashboardViewModel.grayscaleFilterEnabled.collectAsState()
    val grayscaleFilterStartMinutes by dashboardViewModel.grayscaleFilterStartMinutes.collectAsState()
    val grayscaleFilterEndMinutes by dashboardViewModel.grayscaleFilterEndMinutes.collectAsState()
    val redLightFilterEnabled by dashboardViewModel.redLightFilterEnabled.collectAsState()
    val redLightFilterStartMinutes by dashboardViewModel.redLightFilterStartMinutes.collectAsState()
    val redLightFilterEndMinutes by dashboardViewModel.redLightFilterEndMinutes.collectAsState()
    val grayscaleFilterActive = rememberScheduledFilterActive(grayscaleFilterEnabled, grayscaleFilterStartMinutes, grayscaleFilterEndMinutes)
    val redLightFilterActive = rememberScheduledFilterActive(redLightFilterEnabled, redLightFilterStartMinutes, redLightFilterEndMinutes)
    // Red light wins if both schedules somehow overlap -- it's already a stronger transform
    // (luminance-only, mapped to the red channel) than plain grayscale, not a separate effect
    // to stack on top of it.
    val displayFilterMatrix = when {
        redLightFilterActive -> RedLightColorMatrix
        grayscaleFilterActive -> GrayscaleColorMatrix
        else -> null
    }

    Box(
        Modifier
            .fillMaxSize()
            .offset(x = pixelShiftOffset.x, y = pixelShiftOffset.y)
            .displayColorFilter(displayFilterMatrix),
    ) {
        if (nerdModeEnabled) {
            NerdModeNavHost(dashboardViewModel)
        } else {
            SimpleModeNavHost(dashboardViewModel, onSuppressSystemVolumePanelChange)
        }
        if (oledCheckerboardDimEnabled) {
            OledCheckerboardOverlay(Modifier.fillMaxSize())
        }
    }

    // Hosted at this top level (not inside either branch) so a device_auth login prompt shows
    // over whichever screen is up -- Dashboard or Settings, nerd mode or not -- rather than
    // only while one specific screen happens to be on screen.
    val deviceAuthPrompt by dashboardViewModel.deviceAuthPrompt.collectAsState()
    var dismissedPrompt by remember { mutableStateOf<DeviceAuthPrompt?>(null) }
    deviceAuthPrompt?.takeIf { it != dismissedPrompt }?.let { prompt ->
        DeviceAuthDialog(prompt, onDismiss = { dismissedPrompt = prompt })
    }

    // Same top-level placement as the device_auth prompt above, for the same reason. Unlike
    // that one, dismissal here is persisted (see DashboardViewModel.dismissUpdate) rather than
    // just local remember state, so a dismissed release doesn't come back on the next launch.
    val updateAvailable by dashboardViewModel.updateAvailable.collectAsState()
    updateAvailable?.let { info ->
        UpdateAvailableDialog(info, onDismiss = { dashboardViewModel.dismissUpdate(info) })
    }
}

/**
 * Shown while a "device_auth" login (session.go's DeviceAuthCredentials) is waiting on the
 * user to approve elsewhere -- see [DeviceAuthPrompt]'s kdoc for why that has to stay visible
 * here rather than just auto-launching a browser like the interactive flow does. "Open link"
 * is still offered since the flow works fine on this same device too, just not exclusively.
 */
@Composable
private fun DeviceAuthDialog(prompt: DeviceAuthPrompt, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.device_auth_dialog_title)) },
        text = {
            Column {
                Text(stringResource(R.string.device_auth_dialog_body))
                Spacer(Modifier.height(12.dp))
                Text(
                    prompt.userCode,
                    style = MaterialTheme.typography.headlineMedium,
                    fontFamily = FontFamily.Monospace,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    prompt.verificationUri,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(prompt.verificationUri))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                } catch (e: ActivityNotFoundException) {
                    // No browser to hand off to -- the code and link stay visible in the dialog
                    // either way, so there's nothing more to do here than leave it as is.
                }
            }) {
                Text(stringResource(R.string.device_auth_dialog_open_link))
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { clipboard.setText(AnnotatedString(prompt.userCode)) }) {
                    Text(stringResource(R.string.device_auth_dialog_copy_code))
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.device_auth_dialog_dismiss))
                }
            }
        },
    )
}

/** Shown once per newly-seen GitHub release -- see [DashboardViewModel.checkForUpdateIfDue] and
 * [UpdateChecker]. Displays the release's own changelog text ([UpdateInfo.changelog]) inline so
 * there's something to decide on without leaving the app; "View release" still opens
 * [UpdateInfo.releaseUrl] for the full page. "Later" persists the dismissal (via
 * [DashboardViewModel.dismissUpdate]) so this same release doesn't prompt again on a future
 * launch. */
@Composable
private fun UpdateAvailableDialog(info: UpdateInfo, onDismiss: () -> Unit) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.update_available_dialog_title)) },
        text = {
            Column {
                Text(stringResource(R.string.update_available_dialog_body, info.version))
                if (info.changelog.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = info.changelog,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp)
                            .verticalScroll(rememberScrollState()),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(info.releaseUrl))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                } catch (e: ActivityNotFoundException) {
                    // No browser to hand off to -- nothing more useful to do here than leave the
                    // dialog up so the user can at least see the version number.
                }
                onDismiss()
            }) {
                Text(stringResource(R.string.update_available_dialog_view_release))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.update_available_dialog_dismiss))
            }
        },
    )
}

/** Small ring of tiny offsets a burn-in-prone static layout cycles through, one step a minute,
 * so no single pixel sits under the same bright edge/icon for hours on end -- classic OLED
 * "pixel shifting". A plain jump rather than an animated slide: at 3dp it's not meant to be
 * seen happening, just to have happened. Returns a fixed (0dp, 0dp) while disabled, so the
 * caller can apply it unconditionally without an extra branch. */
@Composable
private fun rememberOledPixelShiftOffset(enabled: Boolean): DpOffset {
    if (!enabled) return DpOffset.Zero
    val offsets = remember {
        listOf(
            DpOffset(0.dp, 0.dp), DpOffset(3.dp, 0.dp), DpOffset(0.dp, 3.dp), DpOffset((-3).dp, 0.dp), DpOffset(0.dp, (-3).dp),
            DpOffset(3.dp, 3.dp), DpOffset((-3).dp, (-3).dp), DpOffset(3.dp, (-3).dp), DpOffset((-3).dp, 3.dp),
        )
    }
    var index by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(OLED_PROTECTION_STEP_MS)
            index = (index + 1) % offsets.size
        }
    }
    return offsets[index]
}

/**
 * Blacks out alternating screen pixels in a true checkerboard, flipping which half is lit once
 * a minute -- unlike [oledPixelShift] (which relocates content so no one pixel stays lit), this
 * instead halves how long any given pixel spends lit at all, at the cost of visibly dimming/
 * dithering the whole screen while it's on. Drawn as a 2x2 repeating tile via [BitmapShader]
 * (opaque black / fully transparent, REPEAT-tiled) rather than one draw call per physical pixel,
 * which a screen with millions of pixels can't afford -- the GPU tiles a shader for free.
 */
@Composable
private fun OledCheckerboardOverlay(modifier: Modifier = Modifier) {
    var phase by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(OLED_PROTECTION_STEP_MS)
            phase = 1 - phase
        }
    }
    val brush = remember(phase) { checkerboardBrush(phase) }
    Canvas(modifier = modifier) { drawRect(brush = brush) }
}

private fun checkerboardBrush(phase: Int): Brush {
    val tile = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
    val black = AndroidColor.BLACK
    val clear = AndroidColor.TRANSPARENT
    if (phase == 0) {
        tile.setPixel(0, 0, black); tile.setPixel(1, 0, clear)
        tile.setPixel(0, 1, clear); tile.setPixel(1, 1, black)
    } else {
        tile.setPixel(0, 0, clear); tile.setPixel(1, 0, black)
        tile.setPixel(0, 1, black); tile.setPixel(1, 1, clear)
    }
    return ShaderBrush(BitmapShader(tile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT))
}

private const val OLED_PROTECTION_STEP_MS = 60_000L

/** True while the current wall-clock time falls within [startMinutes, endMinutes) (both in
 * minutes since midnight, 0..1439) -- a start after the end is a valid overnight range (e.g.
 * 22:00-06:00) that wraps past midnight rather than being treated as empty. Re-checked every
 * [FILTER_SCHEDULE_CHECK_MS] rather than computed once, so a filter already on screen actually
 * turns off again when its window ends without the user having to background/reopen the app. */
@Composable
private fun rememberScheduledFilterActive(enabled: Boolean, startMinutes: Int, endMinutes: Int): Boolean {
    if (!enabled) return false
    var active by remember(startMinutes, endMinutes) { mutableStateOf(isWithinSchedule(startMinutes, endMinutes)) }
    LaunchedEffect(startMinutes, endMinutes) {
        while (true) {
            delay(FILTER_SCHEDULE_CHECK_MS)
            active = isWithinSchedule(startMinutes, endMinutes)
        }
    }
    return active
}

private fun isWithinSchedule(startMinutes: Int, endMinutes: Int): Boolean {
    val calendar = java.util.Calendar.getInstance()
    val nowMinutes = calendar.get(java.util.Calendar.HOUR_OF_DAY) * 60 + calendar.get(java.util.Calendar.MINUTE)
    return if (startMinutes <= endMinutes) {
        nowMinutes in startMinutes until endMinutes
    } else {
        nowMinutes >= startMinutes || nowMinutes < endMinutes
    }
}

private const val FILTER_SCHEDULE_CHECK_MS = 30_000L

/** Full-screen grayscale, luminance-preserving (the standard-weights [ColorMatrix.setToSaturation]
 * transform), for [AppPreferences.grayscaleFilterEnabled]. */
private val GrayscaleColorMatrix = ColorMatrix().apply { setToSaturation(0f) }

/** Full-screen "red light mode" for [AppPreferences.redLightFilterEnabled] -- the kind used to
 * preserve night vision/dark adaptation (astronomy, night driving): luminance is computed and
 * written to the red channel only, green and blue zeroed, so the whole screen reads as shades of
 * red instead of just being tinted warm. */
private val RedLightColorMatrix = ColorMatrix(
    floatArrayOf(
        0.2126f, 0.7152f, 0.0722f, 0f, 0f,
        0f, 0f, 0f, 0f, 0f,
        0f, 0f, 0f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f,
    ),
)

/** Applies [matrix] to everything drawn underneath via an offscreen [Canvas.saveLayer], or does
 * nothing at all when [matrix] is null -- works on every API level this app supports (unlike
 * `graphicsLayer(renderEffect = ...)`, which needs API 31+), since it's plain 2D compositing
 * rather than a hardware render effect. Doesn't reach content in a separate Android window (e.g.
 * [DeviceAuthDialog]'s own dialog surface) -- Compose has no draw-tree access to those. */
private fun Modifier.displayColorFilter(matrix: ColorMatrix?): Modifier {
    if (matrix == null) return this
    val paint = Paint().apply { colorFilter = ColorFilter.colorMatrix(matrix) }
    return drawWithContent {
        drawIntoCanvas { canvas ->
            canvas.saveLayer(Rect(Offset.Zero, size), paint)
            drawContent()
            canvas.restore()
        }
    }
}

/**
 * A [HorizontalPager] rather than Navigation Compose's `NavHost`: with just these two flat
 * tabs (no deep links, no back-stack-worthy "drill into detail" screens), a pager gets a real
 * swipe-to-switch gesture -- the page follows your finger and springs to place -- essentially
 * for free, where `NavHost` only offers scripted enter/exit transitions driven by taps.
 */
@Composable
private fun NerdModeNavHost(dashboardViewModel: DashboardViewModel) {
    val pagerState = rememberPagerState(pageCount = { AppDestination.entries.size })
    val scope = rememberCoroutineScope()

    val fullscreenModeEnabled by dashboardViewModel.fullscreenModeEnabled.collectAsState()
    val view = LocalView.current
    DisposableEffect(fullscreenModeEnabled) {
        val window = (view.context as Activity).window
        val insetsController = WindowCompat.getInsetsController(window, view)
        if (fullscreenModeEnabled) {
            insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            insetsController.show(WindowInsetsCompat.Type.systemBars())
        }
        onDispose { insetsController.show(WindowInsetsCompat.Type.systemBars()) }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                AppDestination.entries.forEachIndexed { index, destination ->
                    val selected = pagerState.currentPage == index
                    val label = stringResource(destination.labelRes)
                    NavigationBarItem(
                        selected = selected,
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                        icon = { Icon(if (selected) destination.selectedIcon else destination.unselectedIcon, contentDescription = label) },
                        label = { Text(label) },
                    )
                }
            }
        },
        floatingActionButton = {
            val connectionState by dashboardViewModel.connectionState.collectAsState()
            val isServiceRunning by dashboardViewModel.isServiceRunning.collectAsState()
            StartStopFab(
                connectionState = connectionState,
                isServiceRunning = isServiceRunning,
                onToggle = dashboardViewModel::toggleService,
            )
        },
        floatingActionButtonPosition = FabPosition.End,
    ) { paddingValues ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize(),
        ) { page ->
            when (AppDestination.entries[page]) {
                AppDestination.DASHBOARD -> DashboardScreen(viewModel = dashboardViewModel)
                AppDestination.SETTINGS -> SettingsScreen(viewModel = viewModel<SettingsViewModel>())
            }
        }
    }
}

/** No bottom nav, no pager -- just the card, with Settings as a local push/pop "screen" reached
 * via [SimpleDashboardScreen]'s own top-right icon. Settings follows the same
 * [AppPreferences.fullscreenModeEnabled] toggle the Dashboard does rather than always forcing
 * immersive on its own. */
@Composable
private fun SimpleModeNavHost(dashboardViewModel: DashboardViewModel, onSuppressSystemVolumePanelChange: (Boolean) -> Unit) {
    var showSettings by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // 0f = fully open/closed (whichever `showSettings` says), driving a shrink/round/slide
    // transform on the Settings overlay below. While a real predictive-back drag is in
    // progress it's snapped straight to the finger's own progress (0f..1f); opening/closing by
    // tap instead plays a scripted animateTo across that same 0f..1f range so both paths share
    // one visual. On a *committed* gesture (or a tap on Settings' own back arrow) it continues
    // animating past 1f up to 1.5f -- real per-frame drag values never exceed 1f, so that extra
    // range only ever plays as a deliberate "finish leaving" flourish (shrinks further and
    // fades, see the graphicsLayer below) instead of the overlay just vanishing mid-shrink the
    // instant the gesture commits.
    val settingsBackProgress = remember { Animatable(0f) }
    var settingsBackEdge by remember { mutableStateOf(BackEventCompat.EDGE_LEFT) }

    val closeSettings: () -> Unit = {
        scope.launch {
            settingsBackProgress.animateTo(1.5f, tween(180))
            showSettings = false
            settingsBackProgress.snapTo(0f)
        }
    }
    val openSettings: () -> Unit = {
        scope.launch {
            settingsBackEdge = BackEventCompat.EDGE_LEFT
            settingsBackProgress.snapTo(1f)
            showSettings = true
            settingsBackProgress.animateTo(0f, tween(220))
        }
    }

    PredictiveBackHandler(enabled = showSettings) { progress ->
        try {
            progress.collect { event ->
                settingsBackEdge = event.swipeEdge
                settingsBackProgress.snapTo(event.progress)
            }
            settingsBackProgress.animateTo(1.5f, tween(180))
            showSettings = false
            // Without this, settingsBackProgress would be left at 1.5f, so the render condition
            // below (`showSettings || progress > 0f`) would never go false and the fully-faded
            // Settings overlay would stay mounted (invisible, but still there) forever.
            settingsBackProgress.snapTo(0f)
        } catch (e: CancellationException) {
            settingsBackProgress.animateTo(0f)
        }
    }

    val fullscreenModeEnabled by dashboardViewModel.fullscreenModeEnabled.collectAsState()
    val dashboardBackgroundStyle by dashboardViewModel.dashboardBackgroundStyle.collectAsState()
    val nowPlaying by dashboardViewModel.nowPlaying.collectAsState()
    val view = LocalView.current

    // OLED-black and blurred-cover force a dark backdrop regardless of the app's own light/dark
    // theme (see SimpleDashboardScreen's matching forceLightContent), so status/nav bar icon
    // color has to follow that too, not just the theme, or it disappears into a black
    // background under a light theme. Settings and the Dashboard's plain tinted background
    // instead just follow the active theme's own background luminance.
    val forcedDarkBackground = !showSettings && (
        dashboardBackgroundStyle == DashboardBackgroundStyle.OLED_BLACK ||
            (dashboardBackgroundStyle == DashboardBackgroundStyle.BLURRED_COVER && nowPlaying?.albumCoverUrl != null)
        )
    val useLightStatusBarIcons = forcedDarkBackground || MaterialTheme.colorScheme.background.luminance() < 0.5f

    DisposableEffect(fullscreenModeEnabled, useLightStatusBarIcons) {
        val window = (view.context as Activity).window
        val insetsController = WindowCompat.getInsetsController(window, view)
        insetsController.isAppearanceLightStatusBars = !useLightStatusBarIcons
        insetsController.isAppearanceLightNavigationBars = !useLightStatusBarIcons
        if (fullscreenModeEnabled) {
            insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            insetsController.show(WindowInsetsCompat.Type.systemBars())
        }
        onDispose { insetsController.show(WindowInsetsCompat.Type.systemBars()) }
    }

    // The Dashboard has its own volume slider -- the system's volume panel on top of it is
    // redundant, so it's suppressed (see MainActivity.onKeyDown) whenever the Dashboard itself
    // is what's showing, not while Settings covers it.
    DisposableEffect(showSettings) {
        onSuppressSystemVolumePanelChange(!showSettings)
        onDispose { onSuppressSystemVolumePanelChange(false) }
    }

    Box(Modifier.fillMaxSize()) {
        // Dashboard sits underneath even while Settings is open: as the predictive-back gesture
        // shrinks Settings away, the Dashboard it's revealing is already there instead of the
        // two screens being mutually exclusive and popping abruptly at the gesture's end.
        SimpleDashboardScreen(viewModel = dashboardViewModel, onOpenSettings = openSettings)

        // Gates whether the overlay is emitted at all, so it has to be read directly in the
        // composable body -- but wrapped in derivedStateOf so that read only invalidates this
        // scope on the rare frames where the boolean itself flips (gesture start/end), not on
        // every one of settingsBackProgress's per-frame ticks in between. The progress-dependent
        // transform below reads settingsBackProgress.value again, but from inside graphicsLayer's
        // lambda, which samples state at draw time without recomposing -- reading it out here
        // instead (as this used to) meant every animation frame recomposed this whole
        // composable, including the full SettingsScreen tree mounted underneath, which is what
        // made the open/close animation stutter.
        val showOverlay by remember { derivedStateOf { showSettings || settingsBackProgress.value > 0f } }
        if (showOverlay) {
            val edgeSign = if (settingsBackEdge == BackEventCompat.EDGE_LEFT) 1f else -1f
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val progress = settingsBackProgress.value
                        val dragProgress = progress.coerceIn(0f, 1f)
                        val finishProgress = ((progress - 1f) / 0.5f).coerceIn(0f, 1f)
                        val scale = 1f - dragProgress * 0.1f - finishProgress * 0.15f
                        scaleX = scale
                        scaleY = scale
                        translationX = size.width * 0.05f * dragProgress * edgeSign
                        shape = RoundedCornerShape(28.dp * dragProgress)
                        clip = true
                        alpha = 1f - finishProgress
                    },
            ) {
                SettingsScreenWithTopBar(onBack = closeSettings)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreenWithTopBar(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_destination_settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.content_desc_back))
                    }
                },
            )
        },
    ) { paddingValues ->
        Box(Modifier.padding(paddingValues)) {
            SettingsScreen(viewModel = viewModel<SettingsViewModel>())
        }
    }
}
