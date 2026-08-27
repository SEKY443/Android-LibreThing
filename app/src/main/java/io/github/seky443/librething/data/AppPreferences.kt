package io.github.seky443.librething.data

import androidx.annotation.StringRes
import io.github.seky443.librething.R

/** Android-side app preferences, distinct from the go-librespot daemon config in [GoLibrespotConfig]. */
data class AppPreferences(
    val keepScreenOn: Boolean = false,
    val holdWakeLock: Boolean = true,
    val autostartOnBoot: Boolean = false,
    val volumeSliderEnabled: Boolean = true,
    val volumeSliderLandscapeOnly: Boolean = true,
    val landscapeLeftAlignTrackInfoEnabled: Boolean = true,
    val nerdModeEnabled: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dashboardBackgroundStyle: DashboardBackgroundStyle = DashboardBackgroundStyle.TINTED,
    val fullscreenModeEnabled: Boolean = false,
    val autoSleepOnIdleEnabled: Boolean = false,
    val autoSleepIdleDelaySeconds: Int = 30,
    val autoSleepNapModeEnabled: Boolean = false,
    val hideConsoleButtonEnabled: Boolean = false,
    val hideFakeSleepButtonEnabled: Boolean = false,
    val hideIconRowInLandscapeEnabled: Boolean = true,
    val hideLastSessionLabelInLandscapeEnabled: Boolean = true,
    val landscapeStretchTransportRowEnabled: Boolean = true,
    val autoRestartOnCrashEnabled: Boolean = true,
    val fakeSleepSingleTapWakeEnabled: Boolean = true,
    val maskTrackTransitionFlashEnabled: Boolean = true,
    val gestureControlsEnabled: Boolean = false,
    val gestureHapticIntensity: Float = 1f,
    val gestureTransitionShowConsoleEnabled: Boolean = false,
    val gestureTransitionRoundedCoverEnabled: Boolean = true,
    val gestureControlsFullScreenEnabled: Boolean = false,
    val oledPixelShiftEnabled: Boolean = false,
    val oledCheckerboardDimEnabled: Boolean = false,
    val grayscaleFilterEnabled: Boolean = false,
    val grayscaleFilterStartMinutes: Int = 22 * 60,
    val grayscaleFilterEndMinutes: Int = 6 * 60,
    val redLightFilterEnabled: Boolean = false,
    val redLightFilterStartMinutes: Int = 22 * 60,
    val redLightFilterEndMinutes: Int = 6 * 60,
    val autoCheckForUpdatesEnabled: Boolean = true,
    val autoClearCacheEnabled: Boolean = false,
    val autoClearCacheMaxSizeMb: Int = 500,
)

enum class ThemeMode(val wireValue: String, @param:StringRes val labelRes: Int) {
    SYSTEM("system", R.string.theme_mode_system),
    LIGHT("light", R.string.theme_mode_light),
    DARK("dark", R.string.theme_mode_dark),
    ;

    companion object {
        fun fromWireValue(value: String): ThemeMode = entries.firstOrNull { it.wireValue == value } ?: SYSTEM
    }
}

/** Simplified Dashboard's page background -- see [SimpleDashboardScreen]. */
enum class DashboardBackgroundStyle(val wireValue: String, @param:StringRes val labelRes: Int) {
    /** Theme colors lightly tinted by the current track's album-art accent. */
    TINTED("tinted", R.string.dashboard_background_tinted),

    /** Pure black regardless of album art -- for OLED burn-in protection. */
    OLED_BLACK("oled_black", R.string.dashboard_background_oled_black),

    /** The album art itself, heavily blurred and darkened, filling the page. */
    BLURRED_COVER("blurred_cover", R.string.dashboard_background_blurred_cover),
    ;

    companion object {
        fun fromWireValue(value: String): DashboardBackgroundStyle = entries.firstOrNull { it.wireValue == value } ?: TINTED
    }
}
