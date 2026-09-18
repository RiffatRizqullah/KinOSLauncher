package com.riffat.kinoslauncher

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Eye-comfort palette: hindari putih murni #FFFFFF.
 * Off-white hangat + scrim translusen + blur (bukan hitam pekat).
 */
object KinOSPalette {
    val WarmWhite = Color(0xFFF5F1E8)
    val WarmWhite70 = Color(0xB3F5F1E8)
    val WarmWhite60 = Color(0x99F5F1E8)
    val HintGrey = Color(0x8AE8E2D5)

    val DialSurface = Color(0xFF2C2C2E) // abu gelap opaque
    val DialSurfaceStroke = Color(0x33FFFFFF)
    val Scrim = Color(0x73000000) // ~45% — cukup untuk keterbacaan tanpa membutakan
    val ScrimNight = Color(0x99000000) // ~60% malam hari
}

val KinOSLightScheme = lightColorScheme(
    primary = Color(0xFF4A4438),
    onPrimary = KinOSPalette.WarmWhite,
    surface = Color(0xFFF2EDE3),
    onSurface = Color(0xFF1C1B19),
    surfaceVariant = Color(0xFFE7E0D2),
    onSurfaceVariant = Color(0xFF4A4438)
)

val KinOSDarkScheme = darkColorScheme(
    primary = KinOSPalette.WarmWhite,
    onPrimary = Color(0xFF1C1B19),
    surface = Color(0xFF1C1B19),
    onSurface = KinOSPalette.WarmWhite,
    surfaceVariant = Color(0xFF2A2926),
    onSurfaceVariant = KinOSPalette.WarmWhite70
)

@Composable
fun kinosColorScheme(isNight: Boolean) =
    if (isNight) KinOSDarkScheme else KinOSLightScheme
