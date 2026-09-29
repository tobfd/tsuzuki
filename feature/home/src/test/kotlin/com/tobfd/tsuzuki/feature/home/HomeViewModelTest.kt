package com.tobfd.tsuzuki.feature.home

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.tobfd.tsuzuki.core.common.AppError
import com.tobfd.tsuzuki.core.data.home.FeedScope
import com.tobfd.tsuzuki.core.model.Activity
import com.tobfd.tsuzuki.core.model.MediaListStatus
import com.tobfd.tsuzuki.core.model.MediaStatus
import com.tobfd.tsuzuki.core.model.MediaType
import com.tobfd.tsuzuki.core.model.SessionState
import com.tobfd.tsuzuki.core.model.UserLite
import com.tobfd.tsuzuki.core.testing.FakeHomeRepository
import com.tobfd.tsuzuki.core.testing.FakeListRepository
import com.tobfd.tsuzuki.core.testing.FakeSessionRepository
import com.tobfd.tsuzuki.core.testing.MainDispatcherRule
import com.tobfd.tsuzuki.core.testing.SampleData
import com.tobfd.tsuzuki.core.testing.SampleData.listEntry
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class HomeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val older = Instant.parse("2026-09-01T00:00:00Z")
    private val newer = Instant.parse("2026-09-20T00:00:00Z")
    private val watching = listEntry(1, title = "Frieren", progress = 18, total = 28, updatedAt = older)
    private val reading = listEntry(2, title = "Vagabond", type = MediaType.MANGA, updatedAt = newer)
    private val almostDone = listEntry(3, title = "Apothecary", progress = 23, total = 24, updatedAt = older)
    private val lists = FakeListRepository(listOf(watching, reading, almostDone))
    private val home = FakeHomeRepository()
    private val session = FakeSessionRepository(SessionState.LoggedIn(SampleData.viewer, SampleData.tokenExpiry))

    private val activity = Activity.Text(
        id = 7,
        user = UserLite(9, "someone", null),
        createdAt = Instant.EPOCH,
        likeCount = 2,
        isLiked = false,
        replyCount = 0,
        siteUrl = null,
        html = "Hello"
    )

    private fun viewModel() = HomeViewModel(SavedStateHandle(), home, lists, session)

    private fun TestScope.state(viewModel: HomeViewModel): HomeUiState {
        backgroundScope.launch { viewModel.uiState.collect {} }
        backgroundScope.launch { viewModel.feed.collect {} }
        runCurrent()
        return viewModel.uiState.value
    }

    @Test
    fun inProgress_hasWatchingAndReadingEntriesNewestFirst() = runTest {
        assertEquals(listOf(reading.id, watching.id, almostDone.id), state(viewModel()).inProgress.map { it.id })
    }

    @Test
    fun upNext_prefersWhatCanBeStartedThenTheNewest() {
        val notYet = listEntry(10, status = MediaListStatus.PLANNING, updatedAt = newer).let {
            it.copy(media = it.media.copy(status = MediaStatus.NOT_YET_RELEASED))
        }
        val releasedOld = listEntry(11, status = MediaListStatus.PLANNING, updatedAt = older)
        val releasedNew = listEntry(12, status = MediaListStatus.PLANNING, updatedAt = newer)
        val airing = listEntry(13, status = MediaListStatus.PLANNING, updatedAt = older).let {
            it.copy(media = it.media.copy(status = MediaStatus.RELEASING))
        }

        val upNext = upNextOf(listOf(notYet, releasedOld, releasedNew, airing, watching))

        assertEquals(listOf(12, 11, 13), upNext.map { it.id })
    }

    @Test
    fun feed_showsTheCacheFirstThenAsksTheNetwork() = runTest {
        home.firstPageFromCache = true
        state(viewModel())
        assertEquals(listOf(FeedScope.Following to true, FeedScope.Following to false), home.feedRequests)
    }

    @Test
    fun guests_onlyGetTheGlobalFeed() = runTest {
        session.sessionState.value = SessionState.Guest
        val viewModel = viewModel()
        viewModel.onFeedScopeSelected(FeedScope.Following)

        val state = state(viewModel)

        assertTrue(state.isGuest)
        assertEquals(FeedScope.Global, state.feedScope)
        assertEquals(listOf(FeedScope.Global to true), home.feedRequests)
    }

    @Test
    fun switchingToGlobal_loadsTheGlobalFeed() = runTest {
        val viewModel = viewModel()
        state(viewModel)
        viewModel.onFeedScopeSelected(FeedScope.Global)
        runCurrent()
        assertEquals(FeedScope.Global to true, home.feedRequests.last())
    }

    @Test
    fun plusOne_reachingTheTotal_offersUndo() = runTest {
        val viewModel = viewModel()
        viewModel.eventFlow.test {
            viewModel.onPlusOne(almostDone.id)
            assertEquals(HomeEvent.Completed(almostDone), awaitItem())
        }
        viewModel.onUndo(almostDone)
        assertEquals(almostDone, lists.find(almostDone.id))
    }

    @Test
    fun like_showsAtOnceAndStaysWhenAniListAgrees() = runTest {
        val viewModel = viewModel()
        state(viewModel)
        home.likeGate = CompletableDeferred()

        viewModel.onToggleLike(activity)
        runCurrent()
        assertEquals(LikeState(isLiked = true, likeCount = 3), viewModel.uiState.value.likes[7])

        home.likeGate?.complete(Unit)
        runCurrent()
        assertEquals(LikeState(isLiked = true, likeCount = 3), viewModel.uiState.value.likes[7])
        assertEquals(listOf(7), home.likedIds)
    }

    @Test
    fun failedLike_goesBackAndSaysWhy() = runTest {
        val viewModel = viewModel()
        state(viewModel)
        home.likeResult = Result.failure(AppError.Offline)

        viewModel.eventFlow.test {
            viewModel.onToggleLike(activity)
            assertEquals(HomeEvent.LikeFailed(AppError.Offline), awaitItem())
        }
        assertEquals(LikeState(isLiked = false, likeCount = 2), viewModel.uiState.value.likes[7])
    }

    @Test
    fun refresh_forcesTheListsAndTheFeedFromTheNetwork() = runTest {
        val viewModel = viewModel()
        state(viewModel)
        viewModel.onRefresh()
        runCurrent()
        assertEquals(listOf(true), lists.refreshCalls)
        assertEquals(FeedScope.Following to false, home.feedRequests.last())
    }

    @Test
    fun withLike_appliesTheTappedLike() {
        assertEquals(activity.copy(isLiked = true, likeCount = 5), activity.withLike(LikeState(true, 5)))
        assertEquals(activity, activity.withLike(null))
    }
}
