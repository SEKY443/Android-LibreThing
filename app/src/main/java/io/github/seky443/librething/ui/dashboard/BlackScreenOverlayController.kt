package io.github.seky443.librething.ui.dashboard

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.os.VibrationEffect
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import io.github.seky443.librething.service.SpotifyConnectServiceState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Pure-black, whole-screen "fake sleep" overlay to protect OLED panels from burn-in while the
 * service keeps running underneath. Added as a system overlay window (`SYSTEM_ALERT_WINDOW` /
 * "display over other apps") rather than rendered inside the Activity's own Compose tree: an
 * in-app view only ever covers the content area between the status and navigation bars, and
 * disappears the moment the Activity isn't on top -- neither is acceptable for something meant
 * to look like the screen is off. A real overlay window covers the entire display, including
 * the system bars and any display cutout, and keeps showing even if the app is backgrounded.
 *
 * A singleton (like [io.github.seky443.librething.service.SpotifyConnectServiceState]):
 * the overlay is a system-level resource independent of any one Activity/ViewModel instance,
 * so its lifecycle shouldn't be tied to either.
 */
object BlackScreenOverlayController {
    private var windowManager: WindowManager? = null
    private var overlayView: View? = null

    // Fire-and-forget, same as SpotifyConnectServiceState.playPause()/next()/previous()'s other
    // callers (see DashboardViewModel) -- those are synchronous blocking network calls, so they
    // need to run off the touch-handling thread regardless of caller.
    private val gestureScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val isShowing: Boolean get() = overlayView != null

    fun canShow(context: Context): Boolean = Settings.canDrawOverlays(context)

    /** Opens the system settings screen to grant "display over other apps" for this app. */
    fun requestPermission(context: Context) {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /**
     * @param animate Fades the overlay in instead of cutting straight to black -- used for the
     * automatic idle-triggered sleep (see `AppPreferences.autoSleepOnIdleEnabled` and
     * `MainActivity`), where an abrupt cut reads as a glitch; manual triggers (the fake-sleep
     * button/FAB) stay instant.
     * @param wakeOnSingleTap See `AppPreferences.fakeSleepSingleTapWakeEnabled` -- swaps the
     * overlay's dismiss-tap gesture from double-tap (the default, harder to trigger by accident)
     * to a single tap.
     * @param gestureControlsEnabled See `AppPreferences.gestureControlsEnabled`. Whenever a
     * track is loaded at touch-down, hands swipes to playback control (skip/volume) instead of
     * the old swipe-up dismiss; double-tap only takes over play/pause too when [wakeOnSingleTap]
     * is already true, since otherwise double-tap is still needed to wake -- see
     * [buildOverlayView].
     * @param gestureHapticIntensity See `AppPreferences.gestureHapticIntensity` -- multiplies
     * the gesture haptics' own scale, same as [io.github.seky443.librething.ui.dashboard.MediaCard]'s copy of these gestures.
     */
    fun show(
        context: Context,
        animate: Boolean = false,
        wakeOnSingleTap: Boolean = false,
        gestureControlsEnabled: Boolean = false,
        gestureHapticIntensity: Float = 1f,
    ) {
        if (overlayView != null) return

        val appContext = context.applicationContext
        val wm = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val view = buildOverlayView(appContext, wakeOnSingleTap, gestureControlsEnabled, gestureHapticIntensity)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayWindowType(),
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            // TRANSLUCENT, not OPAQUE: a fade animates this view's alpha, which needs the
            // window itself to actually support blending during composition.
            PixelFormat.TRANSLUCENT,
        ).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }
        }

        if (animate) view.alpha = 0f
        wm.addView(view, params)
        windowManager = wm
        overlayView = view
        hideSystemBars(view)
        if (animate) view.animate().alpha(1f).setDuration(FADE_DURATION_MS).start()
    }

    /** @param animate See [show]'s. */
    fun hide(animate: Boolean = false) {
        val wm = windowManager ?: return
        val view = overlayView ?: return
        windowManager = null
        overlayView = null
        if (animate) {
            view.animate().alpha(0f).setDuration(FADE_DURATION_MS).withEndAction { wm.removeView(view) }.start()
        } else {
            wm.removeView(view)
        }
    }

    private const val FADE_DURATION_MS = 600L

    private fun overlayWindowType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
        }

    private fun hideSystemBars(view: View) {
        val controller = ViewCompat.getWindowInsetsController(view) ?: return
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }

    /**
     * Plain Android views, not Compose: a [androidx.compose.ui.platform.ComposeView] added
     * directly via [WindowManager] has no Activity to source a `LifecycleOwner`/
     * `ViewModelStoreOwner`/`SavedStateRegistryOwner` from, which Compose requires to render at
     * all. The content here (a black background and two dismiss gestures) is simple enough that
     * reimplementing it as a manual `LifecycleOwner` trio isn't worth it. There's deliberately no
     * on-screen "how to exit" hint -- anything visible would undercut the pure-black look this
     * exists for; that instruction lives in Settings instead, next to the toggles for this
     * feature, see [io.github.seky443.librething.ui.settings.SettingsScreen].
     */
    private fun buildOverlayView(
        context: Context,
        wakeOnSingleTap: Boolean,
        gestureControlsEnabled: Boolean,
        gestureHapticIntensity: Float,
    ): View {
        // Compose's pointerInput reports drag deltas in raw pixels, not dp; MotionEvent
        // coordinates are also raw pixels, so this threshold carries over unchanged from the
        // Compose implementation this replaces.
        val swipeDismissThresholdPx = -150f
        val density = context.resources.displayMetrics.density
        val skipThresholdPx = 72 * density
        val volumeJitterThresholdPx = 24 * density
        val vibrator = GestureHaptics.vibratorFor(context)

        val root = object : FrameLayout(context) {
            // Whether gesture controls own the touch sequence currently in progress, decided
            // once at ACTION_DOWN from whatever's loaded at that instant -- fixed for the rest
            // of the gesture so a track ending or starting mid-swipe can't change what a finger
            // already on the glass does. False whenever gestureControlsEnabled is off, or
            // nothing is loaded to control, in which case this behaves exactly like before.
            private var gestureModeActive = false
            // Only true when double-tap isn't already spoken for as the wake gesture (i.e.
            // wakeOnSingleTap is on) -- otherwise double-tap keeps waking, matching
            // fakeSleepSingleTapWakeEnabled exactly instead of gesture mode silently overriding
            // it. See onSingleTapUp/onSingleTapConfirmed/onDoubleTap below.
            private var doubleTapControlsPlayback = false
            private var dragStartX = 0f
            private var dragStartY = 0f
            private var startVolume = 0
            private var lastHapticStep = -1
            private var skipHapticFired = false

            private val gestureDetector = GestureDetector(
                context,
                object : GestureDetector.SimpleOnGestureListener() {
                    // Fires immediately on every tap-up, including the first half of a double
                    // tap -- only safe to use for an instant wake when nothing else is listening
                    // for a second tap. Deferred to onSingleTapConfirmed instead whenever
                    // double-tap is also live (doubleTapControlsPlayback), so a real double-tap
                    // still gets a chance to land instead of being preempted by this firing on
                    // its first half.
                    override fun onSingleTapUp(e: MotionEvent): Boolean {
                        if (doubleTapControlsPlayback) return false
                        if (wakeOnSingleTap) hide(animate = true)
                        return wakeOnSingleTap
                    }

                    // Only reached once the double-tap timeout has ruled out a second tap --
                    // i.e. only matters in the doubleTapControlsPlayback case above, where
                    // onSingleTapUp deferred to here instead of firing immediately.
                    override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                        if (!doubleTapControlsPlayback) return false
                        if (wakeOnSingleTap) hide(animate = true)
                        return wakeOnSingleTap
                    }

                    override fun onDoubleTap(e: MotionEvent): Boolean {
                        if (doubleTapControlsPlayback) {
                            gestureScope.launch { SpotifyConnectServiceState.playPause() }
                        } else if (!wakeOnSingleTap) {
                            hide(animate = true)
                        }
                        return true
                    }
                },
            )

            override fun onTouchEvent(event: MotionEvent): Boolean {
                gestureDetector.onTouchEvent(event)
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        gestureModeActive = gestureControlsEnabled && SpotifyConnectServiceState.nowPlaying.value != null
                        doubleTapControlsPlayback = gestureModeActive && wakeOnSingleTap
                        dragStartX = event.rawX
                        dragStartY = event.rawY
                        startVolume = SpotifyConnectServiceState.volume.value.first
                        lastHapticStep = -1
                        skipHapticFired = false
                    }

                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.rawX - dragStartX
                        val dy = event.rawY - dragStartY
                        if (!gestureModeActive) {
                            if (dy < swipeDismissThresholdPx) hide(animate = true)
                        } else if (abs(dy) > abs(dx)) {
                            // Live haptic "detents" only -- the daemon isn't told anything until
                            // release (ACTION_UP below), same tradeoff as VolumeSlider's own
                            // drag handling (see its kdoc): a call per pixel of drag is what
                            // makes dragging feel stuttery.
                            val (_, max) = SpotifyConnectServiceState.volume.value
                            if (max > 0) {
                                val target = targetVolume(dy, max)
                                val step = target * GestureHaptics.HAPTIC_STEPS / max
                                if (step != lastHapticStep) {
                                    lastHapticStep = step
                                    val fraction = target.toFloat() / max.toFloat()
                                    val curve = GestureHaptics.MIN_VOLUME_HAPTIC_SCALE + fraction * (1f - GestureHaptics.MIN_VOLUME_HAPTIC_SCALE)
                                    GestureHaptics.vibratePrimitive(
                                        vibrator,
                                        VibrationEffect.Composition.PRIMITIVE_TICK,
                                        curve * gestureHapticIntensity,
                                        GestureHaptics.VOLUME_HAPTIC_DURATION_MS,
                                        GestureHaptics.VOLUME_HAPTIC_FALLBACK_AMPLITUDE,
                                    )
                                }
                            }
                        } else {
                            // Fires once, live, the moment the swipe crosses the skip threshold
                            // -- not repeated on every subsequent move tick while still past it,
                            // and reset if the drag comes back under threshold so crossing again
                            // re-fires it. Matches MediaCard's own copy of this gesture.
                            val pastThreshold = abs(dx) > skipThresholdPx
                            if (pastThreshold && !skipHapticFired) {
                                skipHapticFired = true
                                GestureHaptics.vibratePrimitive(
                                    vibrator,
                                    VibrationEffect.Composition.PRIMITIVE_CLICK,
                                    gestureHapticIntensity,
                                    GestureHaptics.SKIP_HAPTIC_DURATION_MS,
                                    GestureHaptics.SKIP_HAPTIC_FALLBACK_AMPLITUDE,
                                )
                            } else if (!pastThreshold) {
                                skipHapticFired = false
                            }
                        }
                    }

                    MotionEvent.ACTION_UP -> {
                        if (gestureModeActive) {
                            val dx = event.rawX - dragStartX
                            val dy = event.rawY - dragStartY
                            if (abs(dx) > abs(dy)) {
                                if (abs(dx) > skipThresholdPx) {
                                    gestureScope.launch {
                                        if (dx < 0) SpotifyConnectServiceState.next() else SpotifyConnectServiceState.previous()
                                    }
                                }
                            } else if (abs(dy) > volumeJitterThresholdPx) {
                                val (_, max) = SpotifyConnectServiceState.volume.value
                                if (max > 0) {
                                    val target = targetVolume(dy, max)
                                    gestureScope.launch { SpotifyConnectServiceState.setVolumeCommand(target) }
                                }
                            }
                        }
                        gestureModeActive = false
                    }

                    MotionEvent.ACTION_CANCEL -> gestureModeActive = false
                }
                return true
            }

            /** Maps a vertical drag (negative = swiped up) to an absolute volume, relative to
             * whatever it was at the start of this gesture -- a full-height swipe covers the
             * whole range, same proportions regardless of where on the card it started. */
            private fun targetVolume(dy: Float, max: Int): Int =
                (startVolume - (dy / height.toFloat()) * max).roundToInt().coerceIn(0, max)
        }
        root.setBackgroundColor(Color.BLACK)
        return root
    }
}
