package com.tobfd.tsuzuki.core.designsystem.theme

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

/**
 * Duration and easing tokens from the Material 3 motion spec (m3.material.io/styles/motion, "Easing
 * and duration"). The emphasized set is the one for transitions between screens.
 */
object TsuzukiMotionTokens {
    const val DURATION_MEDIUM_2 = 300
    const val DURATION_LONG_1 = 450

    /** Emphasized (cubic approximation): elements that begin and end on screen. */
    val EasingEmphasized: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** Emphasized decelerate: elements entering the screen. */
    val EasingEmphasizedDecelerate: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

    /** Emphasized accelerate: elements leaving the screen. */
    val EasingEmphasizedAccelerate: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
}

/**
 * Material transition patterns for whole screens, used by the app's `NavDisplay` so every screen gets
 * them. Timings follow Material's fade-through split: the outgoing screen fades out during the first
 * 35 % of the duration, the incoming one fades in during the rest.
 */
object TsuzukiTransitions {
    /** How far screens travel in a shared axis transition. */
    val SharedAxisSlideDistance = 30.dp

    /** Where the incoming screen starts in a fade through. */
    const val FADE_THROUGH_INITIAL_SCALE = 0.92f

    private const val OUTGOING_FRACTION = 0.35f

    /**
     * Shared axis X. [forward] (opening a screen): the new screen slides in from the right and fades
     * in while the old one moves slightly left and fades out. Backward (back and predictive back) is
     * the mirror image.
     *
     * @param slideDistancePx [SharedAxisSlideDistance] in pixels.
     */
    fun sharedAxisX(forward: Boolean, slideDistancePx: Int): ContentTransform {
        val duration = TsuzukiMotionTokens.DURATION_LONG_1
        val outgoing = (duration * OUTGOING_FRACTION).toInt()
        val direction = if (forward) 1 else -1
        val slide = tween<IntOffset>(duration, easing = TsuzukiMotionTokens.EasingEmphasized)
        val enter = slideInHorizontally(slide) { direction * slideDistancePx } +
            fadeIn(
                tween(
                    duration - outgoing,
                    delayMillis = outgoing,
                    easing = TsuzukiMotionTokens.EasingEmphasizedDecelerate
                )
            )
        val exit = slideOutHorizontally(slide) { -direction * slideDistancePx } +
            fadeOut(tween(outgoing, easing = TsuzukiMotionTokens.EasingEmphasizedAccelerate))
        return enter togetherWith exit
    }

    /**
     * Fade through, for switching between top-level destinations: the old content fades out quickly,
     * then the new one fades in while scaling from [FADE_THROUGH_INITIAL_SCALE] to full size.
     */
    fun fadeThrough(): ContentTransform {
        val duration = TsuzukiMotionTokens.DURATION_MEDIUM_2
        val outgoing = (duration * OUTGOING_FRACTION).toInt()
        val incoming = duration - outgoing
        val enter =
            fadeIn(tween(incoming, delayMillis = outgoing, easing = TsuzukiMotionTokens.EasingEmphasizedDecelerate)) +
                scaleIn(
                    tween(incoming, delayMillis = outgoing, easing = TsuzukiMotionTokens.EasingEmphasizedDecelerate),
                    initialScale = FADE_THROUGH_INITIAL_SCALE
                )
        val exit = fadeOut(tween(outgoing, easing = TsuzukiMotionTokens.EasingEmphasizedAccelerate))
        return enter togetherWith exit
    }
}
