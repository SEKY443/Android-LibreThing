package com.example.android_go_librespot.data

/** Android-side app preferences, distinct from the go-librespot daemon config in [GoLibrespotConfig]. */
data class AppPreferences(
    val keepScreenOn: Boolean = false,
    val holdWakeLock: Boolean = true,
    val autostartOnBoot: Boolean = false,
)
