package com.tobfd.tsuzuki.core.designsystem.theme

import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import com.tobfd.tsuzuki.core.designsystem.theme.PredictiveBackState.Phase
import kotlinx.coroutines.delay
import kotlinx.coroutines.plus
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class PredictiveBackGeometryTest {

    private val size = Size(1000f, 2000f)
    private val margin = 20f
    private val offset = 240f
    private val full = Rect(0f, 0f, 1000f, 2000f)

    private fun assertRect(expected: Rect, actual: Rect) {
        assertEquals("left", expected.left, actual.left, DELTA)
        assertEquals("top", expected.top, actual.top, DELTA)
        assertEquals("right", expected.right, actual.right, DELTA)
        assertEquals("bottom", expected.bottom, actual.bottom, DELTA)
    }

    @Test
    fun closing_startsAtFullSize() {
        assertRect(full, PredictiveBackGeometry.closingPreCommit(size, 0f, BackSwipeEdge.Left, margin))
    }

    @Test
    fun closing_fromLeftEdge_endsAt90PercentAgainstTheRightMargin() {
        val rect = PredictiveBackGeometry.closingPreCommit(size, 1f, BackSwipeEdge.Left, margin)
        assertRect(Rect(80f, 100f, 980f, 1900f), rect)
    }

    @Test
    fun closing_withoutEdge_movesLikeFromTheLeft() {
        assertRect(
            PredictiveBackGeometry.closingPreCommit(size, 0.6f, BackSwipeEdge.Left, margin),
            PredictiveBackGeometry.closingPreCommit(size, 0.6f, BackSwipeEdge.None, margin)
        )
    }

    @Test
    fun closing_fromRightEdge_shrinksIntoTheMiddle() {
        val rect = PredictiveBackGeometry.closingPreCommit(size, 1f, BackSwipeEdge.Right, margin)
        assertRect(Rect(50f, 100f, 950f, 1900f), rect)
    }

    @Test
    fun entering_startsOffsetToTheLeftAtFullSize() {
        assertRect(Rect(-240f, 0f, 760f, 2000f), PredictiveBackGeometry.enteringPreCommit(size, 0f, offset))
    }

    @Test
    fun entering_shrinksInSyncAroundItsOffsetCenter() {
        val rect = PredictiveBackGeometry.enteringPreCommit(size, 1f, offset)
        assertRect(Rect(-190f, 100f, 710f, 1900f), rect)
    }

    @Test
    fun yShift_isZeroWithoutVerticalMovement() {
        val closing = PredictiveBackGeometry.closingPreCommit(size, 1f, BackSwipeEdge.Left, margin)
        assertEquals(0f, PredictiveBackGeometry.yShift(size, closing, 0f, margin), DELTA)
    }

    @Test
    fun yShift_isZeroWhileTheScreenIsFullSize() {
        assertEquals(0f, PredictiveBackGeometry.yShift(size, full, 500f, margin), DELTA)
    }

    @Test
    fun yShift_stopsAtTheMarginAfterHalfTheHeight() {
        val closing = PredictiveBackGeometry.closingPreCommit(size, 1f, BackSwipeEdge.Left, margin)
        // (2000 - 1800) / 2 - 20
        assertEquals(-80f, PredictiveBackGeometry.yShift(size, closing, -1000f, margin), DELTA)
        assertEquals(-80f, PredictiveBackGeometry.yShift(size, closing, -1500f, margin), DELTA)
        assertEquals(80f, PredictiveBackGeometry.yShift(size, closing, 1000f, margin), DELTA)
    }

    @Test
    fun yShift_decelerates() {
        val closing = PredictiveBackGeometry.closingPreCommit(size, 1f, BackSwipeEdge.Left, margin)
        // ratio 0.5 -> 1 - 0.5^2 = 0.75
        assertEquals(60f, PredictiveBackGeometry.yShift(size, closing, 500f, margin), DELTA)
    }

    @Test
    fun postCommit_closingEndsAtFullSizeMovedRight() {
        val start = Rect(80f, 150f, 980f, 1950f)
        assertRect(
            Rect(320f, 0f, 1320f, 2000f),
            PredictiveBackGeometry.closingPostCommit(size, start, 1f, offset)
        )
        assertRect(start, PredictiveBackGeometry.closingPostCommit(size, start, 0f, offset))
    }

    @Test
    fun postCommit_enteringReturnsToItsPlace() {
        val start = Rect(-190f, 150f, 710f, 1950f)
        assertRect(full, PredictiveBackGeometry.enteringPostCommit(size, start, 1f))
        assertRect(start, PredictiveBackGeometry.enteringPostCommit(size, start, 0f))
    }

    @Test
    fun postCommit_closingFadesOutInTheFirstFifth() {
        assertEquals(1f, PredictiveBackGeometry.closingPostCommitAlpha(0f), DELTA)
        assertEquals(0.5f, PredictiveBackGeometry.closingPostCommitAlpha(0.1f), DELTA)
        assertEquals(0f, PredictiveBackGeometry.closingPostCommitAlpha(0.2f), DELTA)
        assertEquals(0f, PredictiveBackGeometry.closingPostCommitAlpha(1f), DELTA)
    }

    @Test
    fun fling_scalesTheReleaseSpeed() {
        // 10 progress/s * 100 * (1 - 0.9) * 2 for an edge swipe
        assertEquals(-200f, PredictiveBackGeometry.flingStartVelocity(10f, 0.5f, BackSwipeEdge.Left), DELTA)
        assertEquals(-100f, PredictiveBackGeometry.flingStartVelocity(10f, 0.5f, BackSwipeEdge.None), DELTA)
    }

    @Test
    fun fling_hasAMinimumForShortGesturesAndAMaximum() {
        assertEquals(-120f, PredictiveBackGeometry.flingStartVelocity(0f, 0.05f, BackSwipeEdge.Left), DELTA)
        assertEquals(0f, PredictiveBackGeometry.flingStartVelocity(0f, 0.5f, BackSwipeEdge.Left), DELTA)
        assertEquals(0f, PredictiveBackGeometry.flingStartVelocity(-5f, 0.5f, BackSwipeEdge.Left), DELTA)
        assertEquals(-1000f, PredictiveBackGeometry.flingStartVelocity(100f, 0.5f, BackSwipeEdge.Left), DELTA)
    }

    private companion object {
        const val DELTA = 0.01f
    }
}

class PredictiveBackStateTest {

    private fun TestScope.newState() = PredictiveBackState(this + TestFrameClock())

    @Test
    fun progress_startsDragging_andFollowsTheFinger() = runTest {
        val state = newState()

        state.onGestureProgress(progress = 0f, touchY = 900f, swipeEdge = BackSwipeEdge.Left, frameTimeMillis = 0)
        state.onGestureProgress(progress = 1f, touchY = 700f, swipeEdge = BackSwipeEdge.Left, frameTimeMillis = 16)

        assertEquals(Phase.Dragging, state.phase)
        assertEquals(BackSwipeEdge.Left, state.swipeEdge)
        assertEquals(1f, state.progress, 0.001f)
        assertEquals(-200f, state.touchDeltaY, 0.001f)
    }

    @Test
    fun progress_usesTheGestureCurve() = runTest {
        val state = newState()
        state.onGestureProgress(progress = 0.5f, touchY = 0f, swipeEdge = BackSwipeEdge.Left, frameTimeMillis = 0)
        assertEquals(PredictiveBackTokens.GestureEasing.transform(0.5f), state.progress, 0.0001f)
    }

    @Test
    fun commit_runsThePostCommitAnimationAndReturnsToIdle() = runTest {
        val state = newState()
        state.onGestureProgress(progress = 0.4f, touchY = 0f, swipeEdge = BackSwipeEdge.Left, frameTimeMillis = 0)

        state.onGestureCommitted()
        assertEquals(Phase.Committed, state.phase)
        // The dispatcher going idle after the commit is not a cancel.
        state.onGestureEnded()
        assertEquals(Phase.Committed, state.phase)

        advanceUntilIdle()
        assertEquals(Phase.Idle, state.phase)
        assertEquals(1f, state.postCommitFraction, 0.0001f)
        assertEquals(1f, state.flingScale, 0.0001f)
    }

    @Test
    fun cancel_springsBackAndReturnsToIdle() = runTest {
        val state = newState()
        state.onGestureProgress(progress = 0.4f, touchY = 0f, swipeEdge = BackSwipeEdge.Left, frameTimeMillis = 0)

        state.onGestureEnded()
        assertEquals(Phase.Cancelling, state.phase)

        advanceUntilIdle()
        assertEquals(Phase.Idle, state.phase)
        assertEquals(0f, state.progress, 0.001f)
    }

    @Test
    fun commitOrEndWithoutAGesture_doNothing() = runTest {
        val state = newState()
        state.onGestureCommitted()
        state.onGestureEnded()
        assertEquals(Phase.Idle, state.phase)
    }

    @Test
    fun newGesture_restartsFromItsOwnTouch() = runTest {
        val state = newState()
        state.onGestureProgress(progress = 0.4f, touchY = 500f, swipeEdge = BackSwipeEdge.Left, frameTimeMillis = 0)
        state.onGestureCommitted()

        state.onGestureProgress(progress = 0.1f, touchY = 800f, swipeEdge = BackSwipeEdge.Right, frameTimeMillis = 16)

        assertEquals(Phase.Dragging, state.phase)
        assertEquals(BackSwipeEdge.Right, state.swipeEdge)
        assertEquals(0f, state.touchDeltaY, 0.001f)
        assertEquals(0f, state.postCommitFraction, 0.001f)
    }
}

/** A frame every 16 ms on the test's virtual time. */
private class TestFrameClock : MonotonicFrameClock {
    private var frameTimeNanos = 0L

    override suspend fun <R> withFrameNanos(onFrame: (frameTimeNanos: Long) -> R): R {
        delay(FRAME_MILLIS)
        frameTimeNanos += FRAME_MILLIS * 1_000_000
        return onFrame(frameTimeNanos)
    }

    private companion object {
        const val FRAME_MILLIS = 16L
    }
}
