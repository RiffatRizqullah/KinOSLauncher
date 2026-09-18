package com.riffat.kinoslauncher

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

/**
 * Single source of truth for motion — target 60fps stabil di low-end.
 * Durasi pendek + easing standar Material (pengganti DecelerateInterpolator acak).
 */
object MotionTokens {
    const val FastMs = 150
    const val StandardMs = 250
    const val EmphasizedMs = 350
    const val StaggerMs = 24

    val StandardEasing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
    val EmphasizedEasing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)
    val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f)

    fun <T> fast() = tween<T>(FastMs, easing = StandardEasing)
    fun <T> standard() = tween<T>(StandardMs, easing = StandardEasing)
    fun <T> emphasized() = tween<T>(EmphasizedMs, easing = EmphasizedEasing)

    fun <T> blobSpring() = spring<T>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMediumLow
    )
}
