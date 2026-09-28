package com.tobfd.tsuzuki.core.designsystem.theme

import android.provider.Settings
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
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

    /** Scale of the current screen at the end of a predictive back gesture. */
    const val PREDICTIVE_BACK_SCALE = 0.9f

    /** Corner radius the current screen reaches during predictive back (shape extraLarge). */
    val PredictiveBackCornerRadius = ShapeTokens.extraLarge

    /** Dim of the previous screen at the start of predictive back; it clears as the gesture goes on. */
    const val ENTERING_SCRIM_ALPHA = 0.08f

    /** Timeline of the predictive back transition; the gesture drives it, so easing is linear. */
    const val PREDICTIVE_BACK_DURATION = 400

    private const val PREDICTIVE_BACK_SHIFT_FRACTION = 20
    private const val PREDICTIVE_BACK_PARALLAX_FRACTION = 10

    /**
     * The Material 3 predictive back pattern for the back gesture only: the current screen shrinks to
     * [PREDICTIVE_BACK_SCALE] and shifts slightly towards the side the swipe comes from, while the
     * previous screen, fully visible behind it, follows with a slight parallax from the left. Corners
     * are rounded by [PredictiveBackCorners]. The gesture's progress drives it; release finishes it
     * and cancelling runs it back. With animations turned off ([reducedMotion]) nothing moves.
     *
     * @param fromLeftEdge true when the swipe started at the left edge.
     */
    fun predictiveBack(fromLeftEdge: Boolean, reducedMotion: Boolean): ContentTransform {
        if (reducedMotion) return EnterTransition.None togetherWith ExitTransition.None
        val direction = if (fromLeftEdge) 1 else -1
        val slide = tween<IntOffset>(PREDICTIVE_BACK_DURATION, easing = LinearEasing)
        val enter = slideInHorizontally(slide) { fullWidth -> -fullWidth / PREDICTIVE_BACK_PARALLAX_FRACTION }
        val exit =
            scaleOut(tween(PREDICTIVE_BACK_DURATION, easing = LinearEasing), targetScale = PREDICTIVE_BACK_SCALE) +
                slideOutHorizontally(slide) { fullWidth -> direction * fullWidth / PREDICTIVE_BACK_SHIFT_FRACTION }
        return enter togetherWith exit
    }
}

/**
 * Decorates every navigation entry for the transitions: rounds the corners of a leaving screen
 * ([EnterExitState.PostExit]) up to [TsuzukiTransitions.PredictiveBackCornerRadius], and dims an
 * entering screen by [TsuzukiTransitions.ENTERING_SCRIM_ALPHA] until it has arrived. Both show
 * during predictive back, where the leaving screen stays on top of the dimmed previous one; in the
 * other transitions the screens have faded before either is noticeable.
 */
@Composable
fun PredictiveBackCorners(scope: AnimatedVisibilityScope, content: @Composable () -> Unit) {
    val spec = tween<Float>(TsuzukiTransitions.PREDICTIVE_BACK_DURATION, easing = LinearEasing)
    val corner by scope.transition.animateDp(
        transitionSpec = { tween(TsuzukiTransitions.PREDICTIVE_BACK_DURATION, easing = LinearEasing) },
        label = "predictiveBackCorner"
    ) { state -> if (state == EnterExitState.PostExit) TsuzukiTransitions.PredictiveBackCornerRadius else 0.dp }
    val scrim by scope.transition.animateFloat(transitionSpec = { spec }, label = "enteringScrim") { state ->
        if (state == EnterExitState.PreEnter) TsuzukiTransitions.ENTERING_SCRIM_ALPHA else 0f
    }
    Box(
        modifier = Modifier
            .graphicsLayer {
                shape = RoundedCornerShape(corner)
                clip = corner > 0.dp
            }
            .drawWithContent {
                drawContent()
                if (scrim > 0f) drawRect(Color.Black.copy(alpha = scrim))
            }
    ) {
        content()
    }
}

/** True when the user turned animations off (animator duration scale 0 in the system settings). */
@Composable
fun rememberReducedMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) {
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}
