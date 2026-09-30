package com.tobfd.tsuzuki.core.designsystem.theme

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

/**
 * [PredictiveBackEntry] inside an `AnimatedContent`, the way `NavDisplay` shows the app's screens.
 * Back is fed to [PredictiveBackState] like the app does: the back key and the gesture both start with
 * a progress event, then commit, then the screen is popped.
 */
class PredictiveBackEntryTest {

    @get:Rule
    val compose = createComposeRule()

    private val stack = mutableStateListOf("A")
    private lateinit var backState: PredictiveBackState
    private var popByGesture = false

    private fun show() {
        compose.setContent {
            val state = rememberPredictiveBackState().also { backState = it }
            AnimatedContent(
                targetState = stack.last(),
                transitionSpec = {
                    if (popByGesture) {
                        EnterTransition.None togetherWith ExitTransition.None
                    } else {
                        fadeIn(tween(OPEN_MILLIS)) togetherWith fadeOut(tween(OPEN_MILLIS))
                    }
                },
                label = "screens"
            ) { key ->
                PredictiveBackEntry(
                    scope = this,
                    state = state,
                    enabled = true,
                    position = BackStackPosition.of(key, stack.toList())
                ) {
                    Box(Modifier.fillMaxSize()) { Text("Screen $key") }
                }
            }
        }
        compose.mainClock.autoAdvance = false
    }

    private fun open(key: String) = compose.runOnUiThread { stack.add(key) }

    private fun back() {
        compose.runOnUiThread { backState.onGestureProgress(0f, 0f, BackSwipeEdge.None, 0L) }
        compose.mainClock.advanceTimeByFrame()
        compose.runOnUiThread {
            backState.onGestureCommitted()
            popByGesture = true
            stack.removeAt(stack.lastIndex)
        }
        compose.mainClock.advanceTimeBy(SETTLE_MILLIS)
        compose.mainClock.autoAdvance = true
    }

    @Test
    fun back_whileTheNextScreenIsStillOpening_showsThePreviousScreen() {
        show()
        open("B")
        compose.mainClock.advanceTimeBy(OPEN_MILLIS / 3L)

        back()

        compose.onNodeWithText("Screen A").assertIsDisplayed()
        compose.onNodeWithText("Screen B").assertDoesNotExist()
    }

    @Test
    fun back_afterTheNextScreenOpened_showsThePreviousScreen() {
        show()
        open("B")
        compose.mainClock.advanceTimeBy(OPEN_MILLIS * 2L)

        back()

        compose.onNodeWithText("Screen A").assertIsDisplayed()
        compose.onNodeWithText("Screen B").assertDoesNotExist()
    }

    @Test
    fun back_twiceInARow_whileOpening_showsTheFirstScreen() {
        show()
        open("B")
        compose.mainClock.advanceTimeBy(OPEN_MILLIS * 2L)
        popByGesture = false
        open("C")
        compose.mainClock.advanceTimeBy(OPEN_MILLIS / 3L)

        back()
        compose.mainClock.autoAdvance = false
        back()

        compose.onNodeWithText("Screen A").assertIsDisplayed()
        compose.onNodeWithText("Screen B").assertDoesNotExist()
        compose.onNodeWithText("Screen C").assertDoesNotExist()
    }

    private companion object {
        const val OPEN_MILLIS = 300
        const val SETTLE_MILLIS = 3_000L
    }
}
