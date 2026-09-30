package com.tobfd.tsuzuki.feature.lists

import com.tobfd.tsuzuki.core.common.AppError
import com.tobfd.tsuzuki.core.data.list.OtherUserList
import com.tobfd.tsuzuki.core.data.list.UserList
import com.tobfd.tsuzuki.core.data.list.UserListRepository
import com.tobfd.tsuzuki.core.model.MediaListStatus
import com.tobfd.tsuzuki.core.model.MediaType
import com.tobfd.tsuzuki.core.model.ScoreFormat
import com.tobfd.tsuzuki.core.model.SessionState
import com.tobfd.tsuzuki.core.testing.FakeSessionRepository
import com.tobfd.tsuzuki.core.testing.MainDispatcherRule
import com.tobfd.tsuzuki.core.testing.SampleData
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class UserListViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private class FakeUserListRepository(var result: Result<OtherUserList>) : UserListRepository {
        val requests = mutableListOf<Pair<Int, MediaType>>()

        override suspend fun list(userId: Int, type: MediaType): Result<OtherUserList> {
            requests += userId to type
            return result
        }
    }

    private val watching = SampleData.listEntry(id = 1, title = "Frieren", status = MediaListStatus.CURRENT)
    private val planned = SampleData.listEntry(id = 2, title = "Apothecary", status = MediaListStatus.PLANNING)
    private val done = SampleData.listEntry(id = 3, title = "Bocchi", status = MediaListStatus.COMPLETED)

    private val repository = FakeUserListRepository(
        Result.success(OtherUserList.Visible(UserList(MediaType.ANIME, listOf(watching, planned, done), emptyList())))
    )
    private val session = FakeSessionRepository(SessionState.LoggedIn(SampleData.viewer, SampleData.tokenExpiry))

    private fun TestScope.viewModel() = UserListViewModel(9, MediaType.ANIME, repository, session).also {
        backgroundScope.launch { it.uiState.collect {} }
        runCurrent()
    }

    private fun UserListViewModel.content() = uiState.value as UserListUiState.Content

    @Test
    fun list_loadsOnceAndShowsTheFirstTabWithEntries() = runTest {
        val viewModel = viewModel()

        assertEquals(listOf(9 to MediaType.ANIME), repository.requests)
        assertEquals(ListTabKey.Status(MediaListStatus.CURRENT), viewModel.content().selectedTab)
        assertEquals(listOf(1), viewModel.content().rows.map { it.id })
        assertEquals(ScoreFormat.POINT_10_DECIMAL, viewModel.content().scoreFormat)
    }

    @Test
    fun tabsSortAndSearch_workLikeTheListsTab() = runTest {
        val viewModel = viewModel()

        viewModel.onTabSelected(ListTabKey.Status(MediaListStatus.COMPLETED))
        runCurrent()
        assertEquals(listOf(3), viewModel.content().rows.map { it.id })

        viewModel.onSearchOpened()
        viewModel.onQueryChange("i")
        runCurrent()
        // Search covers the whole list, not just the tab, sorted by title.
        assertEquals(listOf("Bocchi", "Frieren"), viewModel.content().rows.map { it.media.title.userPreferred })
    }

    @Test
    fun privateList_showsThePrivateState() = runTest {
        repository.result = Result.success(OtherUserList.Private)
        assertEquals(UserListUiState.Private, viewModel().uiState.value)
    }

    @Test
    fun failure_showsTheErrorAndRetryLoadsAgain() = runTest {
        repository.result = Result.failure(AppError.Offline)
        val viewModel = viewModel()
        assertEquals(UserListUiState.Error(AppError.Offline), viewModel.uiState.value)

        repository.result = Result.success(OtherUserList.Private)
        viewModel.onRetry()
        runCurrent()
        assertEquals(UserListUiState.Private, viewModel.uiState.value)
        assertEquals(2, repository.requests.size)
    }
}
