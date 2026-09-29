package com.tobfd.tsuzuki.feature.lists

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.platform.app.InstrumentationRegistry
import com.tobfd.tsuzuki.core.data.list.ChangeState
import com.tobfd.tsuzuki.core.data.list.RejectReason
import com.tobfd.tsuzuki.core.data.list.RejectedChange
import com.tobfd.tsuzuki.core.designsystem.theme.TsuzukiTheme
import com.tobfd.tsuzuki.core.model.EntryChanges
import com.tobfd.tsuzuki.core.model.SessionState
import com.tobfd.tsuzuki.core.testing.FakeListRepository
import com.tobfd.tsuzuki.core.testing.FakeNetworkMonitor
import com.tobfd.tsuzuki.core.testing.FakeSessionRepository
import com.tobfd.tsuzuki.core.testing.SampleData
import com.tobfd.tsuzuki.feature.lists.editor.ListEditorScreen
import com.tobfd.tsuzuki.feature.lists.editor.ListEditorViewModel
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ListEditorSheetTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val frieren = SampleData.frieren
    private val repository = FakeListRepository(entries = listOf(frieren))
    private var dismissed = false

    private fun string(id: Int, vararg args: Any): String = context.getString(id, *args)

    private fun show(online: Boolean = false) {
        val viewModel = ListEditorViewModel(
            mediaId = frieren.mediaId,
            listRepository = repository,
            networkMonitor = FakeNetworkMonitor(online),
            sessionRepository = FakeSessionRepository(SessionState.LoggedIn(SampleData.viewer, SampleData.tokenExpiry)),
            clock = Clock.fixed(Instant.parse("2026-09-29T10:00:00Z"), ZoneOffset.UTC)
        )
        compose.setContent {
            TsuzukiTheme {
                ListEditorScreen(viewModel = viewModel, onDismiss = { dismissed = true }, onOpenDetails = {})
            }
        }
    }

    @Test
    fun increaseThenSave_savesTheNewProgressAndCloses() {
        show()
        compose.onNodeWithContentDescription(string(R.string.lists_editor_increase)).performClick()
        compose.onNodeWithText(string(R.string.lists_editor_save)).performClick()

        compose.waitUntil { dismissed }
        assertEquals(listOf(frieren.id to EntryChanges(progress = 19)), repository.updates)
    }

    @Test
    fun choosingCompleted_fillsInTheTotal() {
        show()
        compose.onNodeWithText(string(com.tobfd.tsuzuki.core.ui.R.string.ui_list_status_completed_anime)).performClick()
        compose.onNodeWithText("28").assertIsDisplayed()
    }

    @Test
    fun progressOverTheTotal_showsWhyAndBlocksSave() {
        show()
        compose.onNodeWithContentDescription(
            string(R.string.lists_editor_progress_episodes)
        ).performTextReplacement("30")

        compose.onNodeWithText(string(R.string.lists_editor_too_high, 28)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.lists_editor_save)).assertIsNotEnabled()
    }

    @Test
    fun remove_asksBeforeRemoving() {
        show()
        val remove = string(R.string.lists_editor_remove)
        compose.onNodeWithText(remove).performClick()
        compose.onNodeWithText(
            string(R.string.lists_editor_remove_title, frieren.media.title.userPreferred)
        ).assertIsDisplayed()
        assertEquals(emptyList<Int>(), repository.deleted)

        compose.onNode(hasText(remove) and hasAnyAncestor(isDialog())).performClick()

        compose.waitUntil { dismissed }
        assertEquals(listOf(frieren.id), repository.deleted)
    }

    @Test
    fun rejectedSave_showsAniListsReasonAndStaysOpen() {
        show(online = true)
        compose.onNodeWithContentDescription(string(R.string.lists_editor_increase)).performClick()
        compose.onNodeWithText(string(R.string.lists_editor_save)).performClick()
        val message = "The progress may not be greater than 28."
        compose.waitUntil { repository.changeStates.isNotEmpty() }

        compose.runOnIdle {
            repository.changeStates.getValue(1).value = ChangeState.Rejected(
                RejectedChange(
                    frieren.id,
                    frieren.mediaId,
                    "Sousou no Frieren",
                    RejectReason.Validation,
                    message,
                    mapOf("progress" to listOf(message))
                )
            )
        }

        compose.onNodeWithText(message).assertIsDisplayed()
        assertEquals(false, dismissed)
    }
}
