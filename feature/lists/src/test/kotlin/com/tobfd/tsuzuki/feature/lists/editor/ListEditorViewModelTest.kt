package com.tobfd.tsuzuki.feature.lists.editor

import app.cash.turbine.test
import com.tobfd.tsuzuki.core.data.list.ChangeState
import com.tobfd.tsuzuki.core.data.list.RejectReason
import com.tobfd.tsuzuki.core.data.list.RejectedChange
import com.tobfd.tsuzuki.core.model.EntryChanges
import com.tobfd.tsuzuki.core.model.FuzzyDate
import com.tobfd.tsuzuki.core.model.MediaListStatus
import com.tobfd.tsuzuki.core.model.ScoreFormat
import com.tobfd.tsuzuki.core.model.SessionState
import com.tobfd.tsuzuki.core.testing.FakeListRepository
import com.tobfd.tsuzuki.core.testing.FakeNetworkMonitor
import com.tobfd.tsuzuki.core.testing.FakeSessionRepository
import com.tobfd.tsuzuki.core.testing.MainDispatcherRule
import com.tobfd.tsuzuki.core.testing.SampleData
import com.tobfd.tsuzuki.core.testing.SampleData.listEntry
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ListEditorViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val frieren = SampleData.frieren
    private val planned = listEntry(3, title = "Dandadan", status = MediaListStatus.PLANNING, total = 12)
    private val repository = FakeListRepository(
        entries = listOf(frieren, planned),
        customLists = mapOf(frieren.type to listOf("Favs", "Later"))
    )
    private val network = FakeNetworkMonitor(online = false)
    private val clock = Clock.fixed(Instant.parse("2026-09-29T10:00:00Z"), ZoneOffset.UTC)
    private val session = FakeSessionRepository(SessionState.LoggedIn(SampleData.viewer, SampleData.tokenExpiry))
    private val today = FuzzyDate(2026, 9, 29)

    private fun viewModel(mediaId: Int = frieren.mediaId) =
        ListEditorViewModel(mediaId, repository, network, session, clock)

    private val ListEditorViewModel.editing: ListEditorUiState.Editing
        get() = uiState.value as ListEditorUiState.Editing

    @Test
    fun opens_withTheEntryAndTheScoreInTheViewersFormat() = runTest {
        val state = viewModel().editing

        assertEquals(frieren, state.entry)
        assertEquals(9.0, state.form.score, 0.0)
        assertEquals(listOf("Favs", "Later"), state.customListNames)
        assertFalse(state.hasChanges)
    }

    @Test
    fun missingEntry_closes() = runTest {
        val viewModel = viewModel(mediaId = 999)
        viewModel.close.test { awaitItem() }
    }

    @Test
    fun completing_fillsTheTotalAndTodaysFinishDate() = runTest {
        val viewModel = viewModel()
        viewModel.onStatusChange(MediaListStatus.COMPLETED)

        val form = viewModel.editing.form
        assertEquals(28, form.progress)
        assertEquals(today, form.completedAt)
    }

    @Test
    fun startingAPlannedEntry_setsTodaysStartDate() = runTest {
        val viewModel = viewModel(mediaId = planned.mediaId)
        viewModel.onStatusChange(MediaListStatus.CURRENT)
        assertEquals(today, viewModel.editing.form.startedAt)
    }

    @Test
    fun progressOverTheTotal_isAnErrorAndCantBeSaved() = runTest {
        val viewModel = viewModel()
        viewModel.onProgressChange(30)

        assertEquals(FieldError.TooHigh(28), viewModel.editing.errors[EditorField.Progress])
        viewModel.onSave()
        assertEquals(emptyList<Pair<Int, EntryChanges>>(), repository.updates)
    }

    @Test
    fun saveOffline_queuesOnlyTheChangesAndCloses() = runTest {
        val viewModel = viewModel()
        viewModel.onProgressChange(20)
        viewModel.onNotesChange("Rewatch with friends")
        viewModel.onCustomListToggle("Favs", true)

        viewModel.close.test {
            viewModel.onSave()
            awaitItem()
        }
        assertEquals(
            listOf(
                frieren.id to EntryChanges(progress = 20, notes = "Rewatch with friends", customLists = setOf("Favs"))
            ),
            repository.updates
        )
    }

    @Test
    fun unchangedScore_keepsTheExactRawScore() = runTest {
        repository.entries.value = listOf(frieren.copy(scoreRaw = 87))
        session.sessionState.value = SessionState.LoggedIn(
            SampleData.viewer.copy(options = SampleData.viewer.options.copy(scoreFormat = ScoreFormat.POINT_10)),
            SampleData.tokenExpiry
        )
        val viewModel = viewModel()
        assertEquals(9.0, viewModel.editing.form.score, 0.0)

        viewModel.onProgressChange(19)
        viewModel.onSave()

        assertEquals(listOf(frieren.id to EntryChanges(progress = 19)), repository.updates)
    }

    @Test
    fun saveOnline_waitsForAniListThenCloses() = runTest {
        network.online = true
        val viewModel = viewModel()
        viewModel.onProgressChange(20)

        viewModel.close.test {
            viewModel.onSave()
            assertTrue(viewModel.editing.saving)
            repository.changeStates.getValue(1).value = ChangeState.Sent
            awaitItem()
        }
    }

    @Test
    fun saveOnline_rejected_showsAniListsReasonAtTheField() = runTest {
        network.online = true
        val viewModel = viewModel()
        viewModel.onProgressChange(20)
        viewModel.onSave()
        val rejected = RejectedChange(
            entryId = frieren.id,
            mediaId = frieren.mediaId,
            title = "Sousou no Frieren",
            reason = RejectReason.Validation,
            detail = "The progress may not be greater than 28.",
            fields = mapOf("progress" to listOf("The progress may not be greater than 28."))
        )
        repository.rejected.value = listOf(rejected)

        repository.changeStates.getValue(1).value = ChangeState.Rejected(rejected)

        val state = viewModel.editing
        assertFalse(state.saving)
        assertEquals(FieldError.Server("The progress may not be greater than 28."), state.errors[EditorField.Progress])
        assertEquals(20, state.form.progress)
        // Shown in the sheet, not again on the Lists screen.
        assertEquals(emptyList<RejectedChange>(), repository.rejected.value)
    }

    @Test
    fun remove_deletesAndCloses() = runTest {
        val viewModel = viewModel()
        viewModel.close.test {
            viewModel.onRemove()
            awaitItem()
        }
        assertEquals(listOf(frieren.id), repository.deleted)
    }

    @Test
    fun serverFieldNames_mapToTheEditorsFields() {
        val errors = serverFieldErrors(
            mapOf(
                "progressVolumes" to listOf("Too many volumes"),
                "scoreRaw" to listOf("Invalid score"),
                "startedAt.year" to listOf("Invalid year"),
                "somethingElse" to listOf("Ignored")
            )
        )
        assertEquals(
            mapOf(
                EditorField.Volumes to FieldError.Server("Too many volumes"),
                EditorField.Score to FieldError.Server("Invalid score"),
                EditorField.StartedAt to FieldError.Server("Invalid year")
            ),
            errors
        )
    }
}
