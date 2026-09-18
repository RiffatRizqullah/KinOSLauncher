package com.riffat.kinoslauncher

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Scrim blur + translusen untuk keterbacaan tanpa hitam pekat.
 * - API 31+: blur beneran via Modifier.blur (RenderEffect di belakang layar).
 * - API 24-30: fallback gradient/dim saja (hemat GPU low-end).
 *
 * Untuk View system, lihat [HomeOverlayView] yang memakai pendekatan setara
 * (RenderEffect + gradient fallback) agar konsisten.
 */
@Composable
fun BlurScrim(
    visible: Boolean,
    isNight: Boolean,
    modifier: Modifier = Modifier
) {
    if (!visible) return
    val dim: Color = if (isNight) KinOSPalette.ScrimNight else KinOSPalette.Scrim
    val canBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    Box(
        modifier = modifier
            .fillMaxSize()
            .then(if (canBlur) Modifier.blur(20.dp) else Modifier)
            .background(dim)
    )
}
