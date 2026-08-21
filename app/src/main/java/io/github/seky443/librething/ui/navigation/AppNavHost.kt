package io.github.seky443.librething.ui.navigation

import android.app.Activity
import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.seky443.librething.R
import io.github.seky443.librething.data.DashboardBackgroundStyle
import io.github.seky443.librething.ui.dashboard.DashboardScreen
import io.github.seky443.librething.ui.dashboard.DashboardViewModel
import io.github.seky443.librething.ui.dashboard.SimpleDashboardScreen
import io.github.seky443.librething.ui.dashboard.StartStopFab
import io.github.seky443.librething.ui.settings.SettingsScreen
import io.github.seky443.librething.ui.settings.SettingsViewModel
import kotlinx.coroutines.CancellationException
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

    if (nerdModeEnabled) {
        NerdModeNavHost(dashboardViewModel)
    } else {
        SimpleModeNavHost(dashboardViewModel, onSuppressSystemVolumePanelChange)
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

        if (showSettings || settingsBackProgress.value > 0f) {
            val progress = settingsBackProgress.value
            val edgeSign = if (settingsBackEdge == BackEventCompat.EDGE_LEFT) 1f else -1f
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
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
