package io.github.seky443.librething.ui.dashboard

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

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
     */
    fun show(context: Context, animate: Boolean = false, wakeOnSingleTap: Boolean = false) {
        if (overlayView != null) return

        val appContext = context.applicationContext
        val wm = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val view = buildOverlayView(appContext, wakeOnSingleTap)
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
    private fun buildOverlayView(context: Context, wakeOnSingleTap: Boolean): View {
        // Compose's pointerInput reports drag deltas in raw pixels, not dp; MotionEvent
        // coordinates are also raw pixels, so this threshold carries over unchanged from the
        // Compose implementation this replaces.
        val swipeDismissThresholdPx = -150f

        val root = object : FrameLayout(context) {
            private val gestureDetector = GestureDetector(
                context,
                object : GestureDetector.SimpleOnGestureListener() {
                    // Single-tap mode dismisses on onSingleTapUp (fires immediately) rather than
                    // onSingleTapConfirmed (which waits out the double-tap timeout to rule out a
                    // second tap) -- there's nothing to disambiguate against once double-tap
                    // itself isn't in play, so waiting would only add a needless delay to wake.
                    override fun onSingleTapUp(e: MotionEvent): Boolean {
                        if (wakeOnSingleTap) hide(animate = true)
                        return wakeOnSingleTap
                    }

                    override fun onDoubleTap(e: MotionEvent): Boolean {
                        if (!wakeOnSingleTap) hide(animate = true)
                        return true
                    }
                },
            )
            private var dragStartY = 0f

            override fun onTouchEvent(event: MotionEvent): Boolean {
                gestureDetector.onTouchEvent(event)
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> dragStartY = event.rawY
                    MotionEvent.ACTION_MOVE -> {
                        if (event.rawY - dragStartY < swipeDismissThresholdPx) hide(animate = true)
                    }
                }
                return true
            }
        }
        root.setBackgroundColor(Color.BLACK)
        return root
    }
}
