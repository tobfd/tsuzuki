package com.tobfd.tsuzuki.core.designsystem.theme

import android.view.RoundedCorner
import android.view.View
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.PathEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.util.VelocityTracker1D
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * The system's back animation between activities, rebuilt for the screens inside the app, so the
 * back gesture looks the same in Tsuzuki as between apps and activities.
 *
 * Source: AOSP `frameworks/base/libs/WindowManager/Shell`, `src/com/android/wm/shell/back/`
 * `CrossActivityBackAnimation.kt` and `DefaultCrossActivityBackAnimation.kt` (Android 15 and 16),
 * dimensions from `res/values/dimen.xml`, curves from `android.view.animation.BackGestureInterpolator`
 * and the Shell's `Interpolators.EMPHASIZED`. The names in backticks below are the AOSP names.
 */
object PredictiveBackTokens {
    /** Scale both screens reach at the end of the gesture (`MAX_SCALE`). */
    const val MAX_SCALE = 0.9f

    /** Gap between the shrunken screen and the edge it moves to (`cross_task_back_vertical_margin`). */
    val DisplayBoundsMargin = 8.dp

    /** How far left of its place the previous screen starts (`cross_activity_back_entering_start_offset`). */
    val EnteringStartOffset = 96.dp

    /** Black scrim between the two screens in the light theme (`MAX_SCRIM_ALPHA_LIGHT`). */
    const val SCRIM_ALPHA_LIGHT = 0.2f

    /** Black scrim between the two screens in the dark theme (`MAX_SCRIM_ALPHA_DARK`). */
    const val SCRIM_ALPHA_DARK = 0.8f

    /** Length of the animation after the finger lifts (`POST_COMMIT_DURATION`). */
    const val POST_COMMIT_DURATION = 450

    /** The leaving screen fades out in this share of the post-commit animation (alpha `1 - 5t`). */
    const val CLOSING_FADE_OUT_FRACTION = 0.2f

    /** Curve applied to the gesture's progress (`BackGestureInterpolator`). */
    val GestureEasing: Easing = CubicBezierEasing(0.1f, 0.1f, 0f, 1f)

    /**
     * Curve of the animation after the finger lifts (`Interpolators.EMPHASIZED`, path based).
     * Built on first use, since it needs the platform's `Path`.
     */
    val PostCommitEasing: Easing by lazy {
        PathEasing(
            Path().apply {
                moveTo(0f, 0f)
                cubicTo(0.05f, 0f, 0.133333f, 0.06f, 0.166666f, 0.4f)
                cubicTo(0.208333f, 0.82f, 0.25f, 1f, 1f, 1f)
            }
        )
    }

    /**
     * After the finger lifts, both screens shrink a little further with the release speed and
     * spring back (`FlingMode.FLING_BOUNCE`). The spring runs on a value that rests at
     * [FLING_SPRING_REST] (`SPRING_SCALE`), with low stiffness and low bouncy damping.
     */
    const val FLING_SPRING_REST = 100f
    const val FLING_MAX_VELOCITY = 1000f
    const val FLING_DEFAULT_VELOCITY = 120f
    const val FLING_STIFFNESS = Spring.StiffnessLow
    const val FLING_DAMPING_RATIO = Spring.DampingRatioLowBouncy

    /** How the progress returns to zero when the gesture is cancelled (`BackProgressAnimator`). */
    const val CANCEL_STIFFNESS = Spring.StiffnessMedium
}

/** The screen edge a back gesture started from. */
enum class BackSwipeEdge { Left, Right, None }

/**
 * Where the two screens are drawn during the back animation, as rectangles inside the area of
 * `NavDisplay`. A screen is scaled by `rect.width / size.width` around its top left corner and
 * then moved to `rect.topLeft`. Mirrors `DefaultCrossActivityBackAnimation` step by step.
 */
internal object PredictiveBackGeometry {

    /** The leaving screen at [progress] (after [PredictiveBackTokens.GestureEasing]). */
    fun closingPreCommit(size: Size, progress: Float, swipeEdge: BackSwipeEdge, marginPx: Float): Rect {
        val start = Rect(0f, 0f, size.width, size.height)
        var target = start.scaleCentered(PredictiveBackTokens.MAX_SCALE)
        // Shrinks into the middle for a swipe from the right edge, towards the right otherwise.
        if (swipeEdge != BackSwipeEdge.Right) {
            target = target.translate(start.right - target.right - marginPx, 0f)
        }
        return lerp(start, target, progress)
    }

    /** The previous screen at [progress]: starts [offsetPx] to the left and shrinks in sync. */
    fun enteringPreCommit(size: Size, progress: Float, offsetPx: Float): Rect {
        val start = Rect(-offsetPx, 0f, size.width - offsetPx, size.height)
        val target = start.scaleCentered(PredictiveBackTokens.MAX_SCALE)
        return lerp(start, target, progress)
    }

    /**
     * Vertical shift of both screens following the finger: at most half the screen height of
     * finger travel counts, decelerated, and the leaving screen never passes [marginPx] from the
     * top or bottom.
     */
    fun yShift(size: Size, closing: Rect, touchDeltaY: Float, marginPx: Float): Float {
        val halfHeight = size.height / 2f
        if (halfHeight <= 0f) return 0f
        val ratio = min(halfHeight, abs(touchDeltaY)) / halfHeight
        val decelerated = 1f - (1f - ratio) * (1f - ratio)
        val direction = if (touchDeltaY < 0f) -1f else 1f
        return max(0f, (size.height - closing.height) / 2f - marginPx) * decelerated * direction
    }

    /** After the finger lifts, the leaving screen grows back to full size while moving right by [offsetPx]. */
    fun closingPostCommit(size: Size, start: Rect, fraction: Float, offsetPx: Float): Rect =
        lerp(start, Rect(0f, 0f, size.width, size.height).translate(start.left + offsetPx, 0f), fraction)

    /** After the finger lifts, the previous screen returns to its place. */
    fun enteringPostCommit(size: Size, start: Rect, fraction: Float): Rect =
        lerp(start, Rect(0f, 0f, size.width, size.height), fraction)

    /** Alpha of the leaving screen after the finger lifts, at the linear post-commit [fraction]. */
    fun closingPostCommitAlpha(fraction: Float): Float =
        max(1f - fraction / PredictiveBackTokens.CLOSING_FADE_OUT_FRACTION, 0f)

    /**
     * Start velocity of the fling spring, from the gesture's velocity in eased progress per
     * second (`onGestureCommitted`). Negative, so the screens first shrink.
     */
    fun flingStartVelocity(progressVelocity: Float, progress: Float, swipeEdge: BackSwipeEdge): Float {
        val edgeFactor = if (swipeEdge == BackSwipeEdge.None) 1f else 2f
        var velocity =
            progressVelocity * PredictiveBackTokens.FLING_SPRING_REST * (1f - PredictiveBackTokens.MAX_SCALE) *
                edgeFactor
        if (progress < 0.1f) velocity = velocity.coerceAtLeast(PredictiveBackTokens.FLING_DEFAULT_VELOCITY)
        return -velocity.coerceIn(0f, PredictiveBackTokens.FLING_MAX_VELOCITY)
    }

    fun Rect.scaleCentered(scale: Float): Rect {
        val halfWidth = width * scale / 2f
        val halfHeight = height * scale / 2f
        return Rect(center.x - halfWidth, center.y - halfHeight, center.x + halfWidth, center.y + halfHeight)
    }
}

/**
 * The state of the back gesture for [PredictiveBackEntry]. The app feeds it the gesture's events
 * (from the `NavigationEventDispatcher`); the screens read it while drawing, so a gesture frame
 * redraws them without recomposing.
 */
@Stable
class PredictiveBackState internal constructor(private val scope: CoroutineScope) {

    internal enum class Phase { Idle, Dragging, Committed, Cancelling }

    internal var phase by mutableStateOf(Phase.Idle)
        private set

    internal var swipeEdge = BackSwipeEdge.None
        private set

    private var rawProgress by mutableFloatStateOf(0f)
    private var startTouchY = 0f

    /** Finger travel in y since the gesture started, in pixels. */
    internal var touchDeltaY by mutableFloatStateOf(0f)
        private set

    /** Linear time fraction of the post-commit animation. */
    internal var postCommitFraction by mutableFloatStateOf(0f)
        private set

    private var flingValue by mutableFloatStateOf(PredictiveBackTokens.FLING_SPRING_REST)
    private val velocityTracker = VelocityTracker1D(isDataDifferential = false)
    private var animation: Job? = null

    /** Gesture progress after [PredictiveBackTokens.GestureEasing]. */
    internal val progress: Float
        get() = PredictiveBackTokens.GestureEasing.transform(rawProgress.coerceIn(0f, 1f))

    /** Extra scale of both screens from the fling spring; never above 1. */
    internal val flingScale: Float
        get() = min(flingValue / PredictiveBackTokens.FLING_SPRING_REST, 1f)

    /**
     * A back gesture started or moved. [progress] is the system's progress (0 to 1), [touchY] the
     * finger's y position in pixels.
     */
    fun onGestureProgress(progress: Float, touchY: Float, swipeEdge: BackSwipeEdge, frameTimeMillis: Long) {
        if (phase != Phase.Dragging) {
            animation?.cancel()
            velocityTracker.resetTracking()
            startTouchY = touchY
            this.swipeEdge = swipeEdge
            postCommitFraction = 0f
            flingValue = PredictiveBackTokens.FLING_SPRING_REST
            phase = Phase.Dragging
        }
        rawProgress = progress
        touchDeltaY = touchY - startTouchY
        velocityTracker.addDataPoint(frameTimeMillis, this.progress)
    }

    /** The finger lifted and the screen is being closed: runs the post-commit animation. */
    fun onGestureCommitted() {
        if (phase != Phase.Dragging) return
        phase = Phase.Committed
        val flingVelocity = PredictiveBackGeometry.flingStartVelocity(
            progressVelocity = velocityTracker.calculateVelocity().takeIf { it.isFinite() } ?: 0f,
            progress = progress,
            swipeEdge = swipeEdge
        )
        animation = scope.launch {
            val fling = launch {
                animate(
                    initialValue = PredictiveBackTokens.FLING_SPRING_REST,
                    targetValue = PredictiveBackTokens.FLING_SPRING_REST,
                    initialVelocity = flingVelocity,
                    animationSpec = spring(
                        dampingRatio = PredictiveBackTokens.FLING_DAMPING_RATIO,
                        stiffness = PredictiveBackTokens.FLING_STIFFNESS
                    )
                ) { value, _ -> flingValue = value }
            }
            animate(0f, 1f, animationSpec = tween(PredictiveBackTokens.POST_COMMIT_DURATION, easing = LinearEasing)) {
                    value,
                    _
                ->
                postCommitFraction = value
            }
            fling.cancel()
            phase = Phase.Idle
        }
    }

    /**
     * The gesture ended. Unless [onGestureCommitted] came first, it was cancelled: the screens
     * return to their places.
     */
    fun onGestureEnded() {
        if (phase != Phase.Dragging) return
        phase = Phase.Cancelling
        animation = scope.launch {
            animate(
                initialValue = rawProgress,
                targetValue = 0f,
                animationSpec = spring(stiffness = PredictiveBackTokens.CANCEL_STIFFNESS)
            ) { value, _ -> rawProgress = value }
            phase = Phase.Idle
        }
    }
}

@Composable
fun rememberPredictiveBackState(): PredictiveBackState {
    val scope = rememberCoroutineScope()
    return remember(scope) { PredictiveBackState(scope) }
}

private enum class BackRole { Closing, Entering }

/** Mutable holder for the role an entry had during the gesture; read only while drawing. */
private class RoleMemory {
    var role: BackRole? = null
}

/**
 * Decorates every navigation entry with the system's back animation (see [PredictiveBackTokens]):
 * while the finger moves, the leaving screen shrinks to 90 % and moves towards the right edge (or
 * stays centered for a swipe from the right), with the device's display corner radius; the
 * previous screen behind it starts 96 dp to the left, shrinks in sync and sits under a black scrim.
 * Both follow the finger vertically. After release, the leaving screen fades out within 90 ms while
 * moving on to the right, and the previous screen grows back into place in 450 ms (emphasized),
 * with a small spring from the release speed. Cancelling springs back.
 *
 * Other transitions are left alone. Pair it with [TsuzukiTransitions.predictiveBack] as the
 * `NavDisplay`'s predictive pop transition. With [enabled] false (animations turned off) nothing moves.
 */
@Composable
fun PredictiveBackEntry(
    scope: AnimatedVisibilityScope,
    state: PredictiveBackState,
    enabled: Boolean,
    content: @Composable () -> Unit
) {
    val transition = scope.transition
    val phase = state.phase
    val active = enabled && phase != PredictiveBackState.Phase.Idle
    // Navigation 3 finishes a committed gesture in (1 - progress) of the transition's duration, which
    // otherwise only has this entry's animations. This keeps both screens long enough for the leaving
    // one to fade out; the animation itself runs on its own clock in PredictiveBackState.
    transition.animateFloat(
        transitionSpec = { if (active) tween(HOLD_DURATION, easing = LinearEasing) else snap() },
        label = "predictiveBackHold"
    ) { if (it == EnterExitState.Visible) 0f else 1f }

    val memory = remember { RoleMemory() }
    val role = when {
        !active -> null

        phase == PredictiveBackState.Phase.Dragging -> when {
            transition.currentState == EnterExitState.Visible && transition.targetState == EnterExitState.PostExit ->
                BackRole.Closing

            transition.currentState == EnterExitState.PreEnter && transition.targetState == EnterExitState.Visible ->
                BackRole.Entering

            else -> null
        }

        // After release only the entries from the gesture animate, even once Navigation 3 is done.
        else -> memory.role
    }
    SideEffect { memory.role = role }

    // Once faded out, the leaving screen leaves composition, so it stops taking touches and cannot
    // show again at full size when the animation ends before Navigation 3 removes it.
    val postCommitFadedOut by remember(state) {
        derivedStateOf {
            state.phase == PredictiveBackState.Phase.Committed &&
                state.postCommitFraction >= PredictiveBackTokens.CLOSING_FADE_OUT_FRACTION
        }
    }
    var gone by remember { mutableStateOf(false) }
    if (role == BackRole.Closing && postCommitFadedOut) SideEffect { gone = true }

    val view = LocalView.current
    val scrimAlpha = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) {
        PredictiveBackTokens.SCRIM_ALPHA_DARK
    } else {
        PredictiveBackTokens.SCRIM_ALPHA_LIGHT
    }

    Box(
        modifier = Modifier.drawWithContent {
            drawContent()
            // The system's scrim lies on top of the previous screen and whatever shows around it.
            if (role == BackRole.Entering) {
                val alpha = when (state.phase) {
                    PredictiveBackState.Phase.Committed -> scrimAlpha * (1f - state.postCommitFraction)
                    else -> scrimAlpha
                }
                if (alpha > 0f) drawRect(Color.Black, alpha = alpha)
            }
        }
    ) {
        Box(
            modifier = Modifier.graphicsLayer {
                if (role == null) return@graphicsLayer
                val margin = PredictiveBackTokens.DisplayBoundsMargin.toPx()
                val offset = PredictiveBackTokens.EnteringStartOffset.toPx()
                val closing = PredictiveBackGeometry.closingPreCommit(size, state.progress, state.swipeEdge, margin)
                val shift = PredictiveBackGeometry.yShift(size, closing, state.touchDeltaY, margin)
                var rect = when (role) {
                    BackRole.Closing -> closing
                    BackRole.Entering -> PredictiveBackGeometry.enteringPreCommit(size, state.progress, offset)
                }.translate(0f, shift)
                if (state.phase == PredictiveBackState.Phase.Committed) {
                    val fraction = PredictiveBackTokens.PostCommitEasing.transform(state.postCommitFraction)
                    rect = with(PredictiveBackGeometry) {
                        when (role) {
                            BackRole.Closing -> closingPostCommit(size, rect, fraction, offset)
                            BackRole.Entering -> enteringPostCommit(size, rect, fraction)
                        }.scaleCentered(state.flingScale)
                    }
                    if (role == BackRole.Closing) {
                        alpha = PredictiveBackGeometry.closingPostCommitAlpha(state.postCommitFraction)
                    }
                }
                transformOrigin = TransformOrigin(0f, 0f)
                scaleX = rect.width / size.width
                scaleY = rect.height / size.height
                translationX = rect.left
                translationY = rect.top
                shape = RoundedCornerShape(view.windowCornerRadius())
                clip = true
            }
        ) {
            if (!gone) content()
        }
    }
}

/** Transition length Navigation 3 uses to finish a committed gesture; long enough for the fade-out. */
private const val HOLD_DURATION = PredictiveBackTokens.POST_COMMIT_DURATION * 2

/**
 * The display's corner radius in pixels, as the system's back animation uses it for both screens
 * (`ScreenDecorationsUtils.getWindowCornerRadius`: the smallest corner). 0 without rounded corners.
 */
private fun View.windowCornerRadius(): Float {
    val insets = rootWindowInsets ?: return 0f
    var radius = Int.MAX_VALUE
    for (position in CORNER_POSITIONS) {
        radius = min(radius, insets.getRoundedCorner(position)?.radius ?: 0)
    }
    return radius.toFloat()
}

private val CORNER_POSITIONS = intArrayOf(
    RoundedCorner.POSITION_TOP_LEFT,
    RoundedCorner.POSITION_TOP_RIGHT,
    RoundedCorner.POSITION_BOTTOM_LEFT,
    RoundedCorner.POSITION_BOTTOM_RIGHT
)
