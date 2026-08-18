package com.example.android_go_librespot.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.DeviceFontFamilyName
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Requests the OS/OEM-installed "Google Sans Text" family by name (Pixel's system font since
 * Android 12) via [DeviceFontFamilyName], rather than bundling font files in the app -- there's
 * no public API to query "the system's brand font", only to ask for a family name and let the
 * platform resolve it. This name isn't a documented, guaranteed-stable public contract (unlike
 * the generic "sans-serif" alias every device must resolve), so on a device that doesn't
 * register it -- non-Pixel phones, or a future Pixel release that renames it -- resolution
 * fails silently and falls back to the platform's own default typeface, never a crash: that
 * silent-fallback behaviour is the whole point of [DeviceFontFamilyName] as an API, unlike the
 * generic "sans-serif" alias every device must resolve to something.
 */
private val AppFontFamily = FontFamily(Font(DeviceFontFamilyName("google-sans-text")))

private fun defaultStyleFor(base: TextStyle) = base.copy(fontFamily = AppFontFamily)

private val base = Typography()

val AppTypography = Typography(
    displayLarge = defaultStyleFor(base.displayLarge),
    displayMedium = defaultStyleFor(base.displayMedium),
    displaySmall = defaultStyleFor(base.displaySmall),
    headlineLarge = defaultStyleFor(base.headlineLarge),
    headlineMedium = defaultStyleFor(base.headlineMedium),
    headlineSmall = defaultStyleFor(base.headlineSmall),
    titleLarge = base.titleLarge.copy(fontFamily = AppFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 22.sp),
    titleMedium = defaultStyleFor(base.titleMedium),
    titleSmall = defaultStyleFor(base.titleSmall),
    bodyLarge = defaultStyleFor(base.bodyLarge),
    bodyMedium = base.bodyMedium.copy(fontFamily = AppFontFamily, fontWeight = FontWeight.Normal, fontSize = 14.sp),
    bodySmall = defaultStyleFor(base.bodySmall),
    labelLarge = defaultStyleFor(base.labelLarge),
    labelMedium = defaultStyleFor(base.labelMedium),
    labelSmall = defaultStyleFor(base.labelSmall),
)
