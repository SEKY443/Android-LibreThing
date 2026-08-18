package com.example.android_go_librespot.ui.dashboard

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
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
 * A singleton (like [com.example.android_go_librespot.service.SpotifyConnectServiceState]):
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

    fun show(context: Context) {
        if (overlayView != null) return

        val appContext = context.applicationContext
        val wm = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val view = buildOverlayView(appContext)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayWindowType(),
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.OPAQUE,
        ).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }
        }

        wm.addView(view, params)
        windowManager = wm
        overlayView = view
        hideSystemBars(view)
    }

    fun hide() {
        val wm = windowManager ?: return
        overlayView?.let { wm.removeView(it) }
        windowManager = null
        overlayView = null
    }

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
     * all. The content here (a black background, a hint label, and two dismiss gestures) is
     * simple enough that reimplementing it as a manual `LifecycleOwner` trio isn't worth it.
     */
    private fun buildOverlayView(context: Context): View {
        // Compose's pointerInput reports drag deltas in raw pixels, not dp; MotionEvent
        // coordinates are also raw pixels, so this threshold carries over unchanged from the
        // Compose implementation this replaces.
        val swipeDismissThresholdPx = -150f

        val root = object : FrameLayout(context) {
            private val gestureDetector = GestureDetector(
                context,
                object : GestureDetector.SimpleOnGestureListener() {
                    override fun onDoubleTap(e: MotionEvent): Boolean {
                        hide()
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
                        if (event.rawY - dragStartY < swipeDismissThresholdPx) hide()
                    }
                }
                return true
            }
        }
        root.setBackgroundColor(Color.BLACK)

        val label = TextView(context).apply {
            text = "Double-tap or swipe up to wake"
            setTextColor(0xFF262626.toInt())
            textSize = 12f
            gravity = Gravity.CENTER_HORIZONTAL
        }
        val labelParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL,
        ).apply {
            val density = context.resources.displayMetrics.density
            bottomMargin = (48 * density).toInt()
        }
        root.addView(label, labelParams)

        return root
    }
}
