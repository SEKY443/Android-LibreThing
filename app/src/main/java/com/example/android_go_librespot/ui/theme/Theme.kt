package com.example.android_go_librespot.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = SpotifyGreen40,
    onPrimary = Neutral99,
    secondary = SpotifyGreen20,
    background = Neutral99,
    surface = Neutral99,
    onBackground = Neutral10,
    onSurface = Neutral10,
    error = Error40,
)

private val DarkColors = darkColorScheme(
    primary = SpotifyGreen80,
    onPrimary = SpotifyGreen20,
    secondary = SpotifyGreen80,
    background = Neutral10,
    surface = Neutral20,
    onBackground = Neutral90,
    onSurface = Neutral90,
    error = Error80,
)

/**
 * App theme: Material You dynamic color on Android 12+ (following system wallpaper), a
 * static Spotify-green-tinted Material 3 palette below that. Honors the system dark/light
 * setting either way.
 */
@Composable
fun AndroidGoLibrespotTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content,
    )
}
