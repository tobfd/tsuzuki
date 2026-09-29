package com.tobfd.tsuzuki.feature.lists

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.tobfd.tsuzuki.core.common.AppError
import com.tobfd.tsuzuki.core.data.list.RejectReason
import com.tobfd.tsuzuki.core.data.list.RejectedChange
import com.tobfd.tsuzuki.core.model.MediaListStatus
import com.tobfd.tsuzuki.core.model.MediaType
import com.tobfd.tsuzuki.core.model.ScoreFormat
import com.tobfd.tsuzuki.core.model.SessionState
import com.tobfd.tsuzuki.core.testing.FakeListRepository
import com.tobfd.tsuzuki.core.testing.FakeSessionRepository
import com.tobfd.tsuzuki.core.testing.MainDispatcherRule
import com.tobfd.tsuzuki.core.testing.SampleData
import com.tobfd.tsuzuki.core.testing.SampleData.listEntry
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ListsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val frieren = SampleData.frieren
    private val almostDone = listEntry(2, title = "Apothecary Diaries", progress = 23, total = 24)
    private val planned = listEntry(3, title = "Dandadan", status = MediaListStatus.PLANNING)
    private val manga = listEntry(4, title = "Vagabond", type = MediaType.MANGA, total = null)
    private val repository = FakeListRepository(
        entries = listOf(frieren, almostDone, planned, manga),
        customLists = mapOf(MediaType.ANIME to listOf("Favs"))
    )
    private val session = FakeSessionRepository(SessionState.LoggedIn(SampleData.viewer, SampleData.tokenExpiry))

    private fun viewModel() = ListsViewModel(SavedStateHandle(), repository, session)

    /** The state once collected, the way the screen collects it. */
    private suspend fun TestScope.state(viewModel: ListsViewModel): ListsUiState {
        backgroundScope.launch { viewModel.uiState.collect {} }
        runCurrent()
        return viewModel.uiState.value
    }

    @Test
    fun start_showsWatchingWithCountsAndTheViewersScoreFormat() = runTest {
        val state = state(viewModel())

        assertEquals(ListTabKey.Status(MediaListStatus.CURRENT), state.selectedTab)
        // Last updated first; both were updated at the same time, so by title.
        assertEquals(listOf(2, frieren.id), state.rows.map { it.id })
        assertEquals(2, state.tabs.first { it.key == ListTabKey.Status(MediaListStatus.CURRENT) }.count)
        assertEquals(ListTabKey.Custom("Favs"), state.tabs.last().key)
        assertEquals(ScoreFormat.POINT_10_DECIMAL, state.scoreFormat)
        assertTrue(state.loaded)
        assertEquals(listOf(false), repository.refreshCalls)
    }

    @Test
    fun switchingToManga_showsTheMangaListFromItsFirstTab() = runTest {
        val viewModel = viewModel()
        viewModel.onTabSelected(ListTabKey.Status(MediaListStatus.PLANNING))

        viewModel.onTypeSelected(MediaType.MANGA)

        val state = state(viewModel)
        assertEquals(MediaType.MANGA, state.type)
        assertEquals(ListTabKey.Status(MediaListStatus.CURRENT), state.selectedTab)
        assertEquals(listOf(4), state.rows.map { it.id })
    }

    @Test
    fun plusOne_reachingTheTotal_offersUndo() = runTest {
        val viewModel = viewModel()
        state(viewModel)

        viewModel.eventFlow.test {
            viewModel.onPlusOne(almostDone.id)
            val event = awaitItem() as ListsEvent.Completed
            assertEquals(almostDone, event.before)
        }
        assertEquals(MediaListStatus.COMPLETED, repository.find(almostDone.id)?.status)

        viewModel.onUndo(almostDone)
        assertEquals(almostDone, repository.find(almostDone.id))
    }

    @Test
    fun plusOne_belowTheTotal_offersNothing() = runTest {
        val viewModel = viewModel()
        viewModel.eventFlow.test {
            viewModel.onPlusOne(frieren.id)
            expectNoEvents()
        }
        assertEquals(19, repository.find(frieren.id)?.progress)
    }

    @Test
    fun start_movesAPlannedEntryToWatching() = runTest {
        val viewModel = viewModel()
        viewModel.onStart(planned.id)
        assertEquals(MediaListStatus.CURRENT, repository.find(planned.id)?.status)
        assertEquals(3, state(viewModel).tabs.first().count)
    }

    @Test
    fun search_findsAcrossTabsAndClosingClearsIt() = runTest {
        val viewModel = viewModel()
        viewModel.onSearchOpened()
        viewModel.onQueryChange("danda")

        assertEquals(listOf(3), state(viewModel).rows.map { it.id })

        viewModel.onSearchClosed()
        runCurrent()
        val state = viewModel.uiState.value
        assertFalse(state.searchActive)
        assertEquals("", state.query)
        assertEquals(setOf(frieren.id, 2), state.rows.map { it.id }.toSet())
    }

    @Test
    fun sort_isApplied() = runTest {
        val viewModel = viewModel()
        viewModel.onSortSelected(ListSort.Title)
        assertEquals(listOf(2, frieren.id), state(viewModel).rows.map { it.id })
    }

    @Test
    fun aRemovedCustomList_fallsBackToTheFirstTab() = runTest {
        val viewModel = viewModel()
        viewModel.onTabSelected(ListTabKey.Custom("Gone"))
        assertEquals(ListTabKey.Status(MediaListStatus.CURRENT), state(viewModel).selectedTab)
    }

    @Test
    fun failedFirstSync_withNothingStored_showsTheError() = runTest {
        repository.entries.value = emptyList()
        repository.refreshResult = Result.failure(AppError.Offline)

        val state = state(viewModel())

        assertTrue(state.listEmpty)
        assertEquals(AppError.Offline, state.loadError)
    }

    @Test
    fun failedSync_withAStoredList_keepsShowingIt() = runTest {
        repository.refreshResult = Result.failure(AppError.Offline)
        assertNull(state(viewModel()).loadError)
    }

    @Test
    fun pullToRefresh_forcesASyncAndReportsFailures() = runTest {
        val viewModel = viewModel()
        repository.refreshResult = Result.failure(AppError.RateLimited(30))

        viewModel.eventFlow.test {
            viewModel.onRefresh()
            assertEquals(ListsEvent.RefreshFailed(AppError.RateLimited(30)), awaitItem())
        }
        assertEquals(listOf(false, true), repository.refreshCalls)
    }

    @Test
    fun waitingChanges_showOnlyAfterAMoment() = runTest {
        val viewModel = viewModel()
        state(viewModel)
        repository.queued.value = 2
        runCurrent()
        assertEquals(0, viewModel.uiState.value.waitingChanges)

        advanceTimeBy(3_001)
        assertEquals(2, viewModel.uiState.value.waitingChanges)
    }

    @Test
    fun rejectedChange_isShownOnce() = runTest {
        val rejected =
            RejectedChange(1, frieren.mediaId, "Sousou no Frieren", RejectReason.Validation, "Too high", emptyMap())
        repository.rejected.value = listOf(rejected)
        val viewModel = viewModel()
        assertEquals(rejected, state(viewModel).rejected)

        viewModel.onRejectionShown(1)
        runCurrent()

        assertNull(viewModel.uiState.value.rejected)
    }
}
