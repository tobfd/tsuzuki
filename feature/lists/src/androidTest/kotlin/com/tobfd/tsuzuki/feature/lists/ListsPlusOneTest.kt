package com.tobfd.tsuzuki.feature.lists

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import androidx.test.platform.app.InstrumentationRegistry
import com.tobfd.tsuzuki.core.designsystem.theme.TsuzukiTheme
import com.tobfd.tsuzuki.core.model.MediaListStatus
import com.tobfd.tsuzuki.core.model.SessionState
import com.tobfd.tsuzuki.core.testing.FakeListRepository
import com.tobfd.tsuzuki.core.testing.FakeSessionRepository
import com.tobfd.tsuzuki.core.testing.SampleData
import com.tobfd.tsuzuki.core.testing.SampleData.listEntry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ListsPlusOneTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val almostDone = listEntry(2, title = "Apothecary Diaries", progress = 23, total = 24)
    private val repository = FakeListRepository(entries = listOf(almostDone))

    private fun show() {
        val viewModel = ListsViewModel(
            SavedStateHandle(),
            repository,
            FakeSessionRepository(SessionState.LoggedIn(SampleData.viewer, SampleData.tokenExpiry))
        )
        compose.setContent {
            TsuzukiTheme {
                ListsContent(
                    viewModel = viewModel,
                    onOpenMedia = {},
                    onEditEntry = {},
                    onBrowse = {},
                    frame = { actions, content ->
                        Column {
                            Row { actions() }
                            content(PaddingValues())
                        }
                    }
                )
            }
        }
    }

    @Test
    fun plusOne_reachingTheTotal_completesAndUndoBringsItBack() {
        show()
        val plusOne = context.getString(com.tobfd.tsuzuki.core.designsystem.R.string.designsystem_plus_one_description)

        compose.onNodeWithContentDescription(plusOne).performClick()

        compose.onNodeWithText(
            context.getString(R.string.lists_completed_anime, "Apothecary Diaries")
        ).assertIsDisplayed()
        assertEquals(MediaListStatus.COMPLETED, repository.find(2)?.status)

        compose.onNodeWithText(context.getString(R.string.lists_undo)).performClick()

        compose.waitUntil { repository.find(2)?.status == MediaListStatus.CURRENT }
        compose.onNodeWithText("23 / 24").assertIsDisplayed()
    }

    @Test
    fun plusOne_belowTheTotal_countsUp() {
        repository.entries.value = listOf(almostDone.copy(progress = 10))
        show()
        val plusOne = context.getString(com.tobfd.tsuzuki.core.designsystem.R.string.designsystem_plus_one_description)

        compose.onNodeWithContentDescription(plusOne).performClick()

        compose.onNodeWithText("11 / 24").assertIsDisplayed()
    }
}
