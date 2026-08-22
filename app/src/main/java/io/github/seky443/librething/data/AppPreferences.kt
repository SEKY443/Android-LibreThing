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
    val fakeSleepSingleTapWakeEnabled: Boolean = false,
    val maskTrackTransitionFlashEnabled: Boolean = true,
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
