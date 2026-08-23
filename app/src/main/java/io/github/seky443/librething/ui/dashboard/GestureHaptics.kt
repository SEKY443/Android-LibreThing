package io.github.seky443.librething.ui.dashboard

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlin.math.roundToInt

/**
 * Shared haptic tuning for the cover-art gesture controls ([MediaCard] in
 * SimpleDashboardScreen.kt) and the fake-sleep overlay's own copy of the same gestures
 * ([BlackScreenOverlayController]) -- both drive the same feel, scaled by the caller's own
 * `AppPreferences.gestureHapticIntensity` (0f mutes, 1f is the tuned default, up to 4f -- primitive
 * -based haptics (see [vibratePrimitive]) hard-cap at 1f regardless, since Android's own primitive
 * composition API has no way to overdrive a primitive past its tuned amplitude; the extra headroom
 * only does anything on the raw-amplitude fallback path, where [VOLUME_HAPTIC_FALLBACK_AMPLITUDE]/
 * [SKIP_HAPTIC_FALLBACK_AMPLITUDE] leave room below 255 to scale into).
 */
internal object GestureHaptics {
    const val HAPTIC_STEPS = 20
    const val MIN_VOLUME_HAPTIC_SCALE = 0.15f
    const val VOLUME_HAPTIC_FALLBACK_AMPLITUDE = 110
    const val VOLUME_HAPTIC_DURATION_MS = 12L
    const val SKIP_HAPTIC_FALLBACK_AMPLITUDE = 220
    const val SKIP_HAPTIC_DURATION_MS = 30L

    fun vibratorFor(context: Context): Vibrator =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

    /**
     * Prefers a composed haptic primitive (Android 11+) over a raw [VibrationEffect.createOneShot]
     * amplitude: primitives play the device's own tuned waveform for that feel (the "tick"/"click"
     * a linear resonant actuator is actually good at) instead of a generic buzz, while [scale]
     * still lets it come out softer or harder -- that's what the primitive composition API is
     * for. Falls back to a plain amplitude one-shot on older API levels or devices that don't
     * support primitives (some budget vibrator hardware doesn't). [scale] already has the
     * user's own intensity preference folded in by the caller; zero or below is treated as
     * "muted" rather than clamped up to the minimum audible amplitude.
     */
    fun vibratePrimitive(vibrator: Vibrator, primitiveId: Int, scale: Float, fallbackDurationMs: Long, fallbackAmplitude: Int) {
        if (scale <= 0f) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && vibrator.areAllPrimitivesSupported(primitiveId)) {
            vibrator.vibrate(VibrationEffect.startComposition().addPrimitive(primitiveId, scale.coerceIn(0f, 1f)).compose())
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val amplitude = (fallbackAmplitude * scale).roundToInt().coerceIn(1, 255)
            vibrator.vibrate(VibrationEffect.createOneShot(fallbackDurationMs, amplitude))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(fallbackDurationMs)
        }
    }
}
