package com.tobfd.tsuzuki.feature.home

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.tobfd.tsuzuki.core.common.AppError
import com.tobfd.tsuzuki.core.data.home.FeedScope
import com.tobfd.tsuzuki.core.model.Activity
import com.tobfd.tsuzuki.core.model.AppUpdate
import com.tobfd.tsuzuki.core.model.MediaListStatus
import com.tobfd.tsuzuki.core.model.MediaStatus
import com.tobfd.tsuzuki.core.model.MediaType
import com.tobfd.tsuzuki.core.model.SessionState
import com.tobfd.tsuzuki.core.model.UserLite
import com.tobfd.tsuzuki.core.testing.FakeHomeRepository
import com.tobfd.tsuzuki.core.testing.FakeHomeRepository.PageRequest
import com.tobfd.tsuzuki.core.testing.FakeListRepository
import com.tobfd.tsuzuki.core.testing.FakeSessionRepository
import com.tobfd.tsuzuki.core.testing.FakeUpdateRepository
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
    private val updates = FakeUpdateRepository()

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

    private fun activities(ids: IntRange) = ids.map { activity.copy(id = it) }

    private fun viewModel() = HomeViewModel(SavedStateHandle(), home, lists, session, updates)

    private fun TestScope.state(viewModel: HomeViewModel): HomeUiState {
        backgroundScope.launch { viewModel.uiState.collect {} }
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
        home.cached = true
        state(viewModel())
        assertEquals(
            listOf(PageRequest(FeedScope.Following, 1, true), PageRequest(FeedScope.Following, 1, false)),
            home.pageRequests
        )
    }

    @Test
    fun feed_showsTheFirstPageAndOffersMore() = runTest {
        home.pages[1] = activities(1..25)
        home.pages[2] = activities(26..30)

        val feed = state(viewModel()).feed

        assertEquals((1..25).toList(), feed.activities.map { it.id })
        assertTrue(feed.hasMore)
        assertFalse(feed.isLoading)
    }

    @Test
    fun loadMore_addsTheNextPageAndShowsItIsLoading() = runTest {
        home.pages[1] = activities(1..25)
        home.pages[2] = activities(26..30)
        val viewModel = viewModel()
        state(viewModel)
        home.pageGate = CompletableDeferred()

        viewModel.onLoadMore()
        runCurrent()
        assertTrue(viewModel.uiState.value.feed.isLoadingMore)

        home.pageGate?.complete(Unit)
        runCurrent()
        val feed = viewModel.uiState.value.feed
        assertEquals((1..30).toList(), feed.activities.map { it.id })
        assertFalse(feed.isLoadingMore)
        assertFalse(feed.hasMore)
        assertEquals(PageRequest(FeedScope.Following, 2, false), home.pageRequests.last())
    }

    @Test
    fun loadMore_leavesOutActivitiesAlreadyShown() = runTest {
        home.pages[1] = activities(1..25)
        home.pages[2] = activities(25..27)
        val viewModel = viewModel()
        state(viewModel)

        viewModel.onLoadMore()
        runCurrent()

        assertEquals((1..27).toList(), viewModel.uiState.value.feed.activities.map { it.id })
    }

    @Test
    fun loadMore_failing_keepsTheFeedAndSaysWhy() = runTest {
        home.pages[1] = activities(1..25)
        home.pages[2] = activities(26..30)
        val viewModel = viewModel()
        state(viewModel)
        home.failure = AppError.Offline

        viewModel.eventFlow.test {
            viewModel.onLoadMore()
            assertEquals(HomeEvent.FeedFailed(AppError.Offline), awaitItem())
        }
        val feed = viewModel.uiState.value.feed
        assertEquals(25, feed.activities.size)
        assertFalse(feed.isLoadingMore)
        assertTrue(feed.hasMore)
        assertNull(feed.error)
    }

    @Test
    fun firstPage_failing_showsTheErrorAndRetryLoadsIt() = runTest {
        home.pages[1] = activities(1..3)
        home.failure = AppError.Offline
        val viewModel = viewModel()

        assertEquals(AppError.Offline, state(viewModel).feed.error)

        home.failure = null
        viewModel.onRetryFeed()
        runCurrent()
        val feed = viewModel.uiState.value.feed
        assertNull(feed.error)
        assertEquals(listOf(1, 2, 3), feed.activities.map { it.id })
    }

    @Test
    fun guests_onlyGetTheGlobalFeed() = runTest {
        session.sessionState.value = SessionState.Guest
        val viewModel = viewModel()
        viewModel.onFeedScopeSelected(FeedScope.Following)

        val state = state(viewModel)

        assertTrue(state.isGuest)
        assertEquals(FeedScope.Global, state.feedScope)
        assertEquals(setOf(FeedScope.Global), home.pageRequests.map { it.scope }.toSet())
    }

    @Test
    fun switchingToGlobal_loadsTheGlobalFeed() = runTest {
        val viewModel = viewModel()
        state(viewModel)
        viewModel.onFeedScopeSelected(FeedScope.Global)
        runCurrent()
        assertEquals(PageRequest(FeedScope.Global, 1, false), home.pageRequests.last())
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
    fun refresh_forcesTheListsAndTheFirstFeedPageFromTheNetwork() = runTest {
        home.pages[1] = activities(1..25)
        home.pages[2] = activities(26..30)
        val viewModel = viewModel()
        state(viewModel)
        viewModel.onLoadMore()
        runCurrent()
        home.pageRequests.clear()

        viewModel.onRefresh()
        runCurrent()

        assertEquals(listOf(true), lists.refreshCalls)
        assertEquals(listOf(PageRequest(FeedScope.Following, 1, false)), home.pageRequests)
        assertEquals(25, viewModel.uiState.value.feed.activities.size)
    }

    @Test
    fun withLike_appliesTheTappedLike() {
        assertEquals(activity.copy(isLiked = true, likeCount = 5), activity.withLike(LikeState(true, 5)))
        assertEquals(activity, activity.withLike(null))
    }

    @Test
    fun availableUpdate_showsOnHome_untilDismissed() = runTest {
        val update = AppUpdate("1.1.0", "https://github.com/tobfd/tsuzuki/releases/tag/v1.1.0")
        updates.availableUpdate.value = update
        val viewModel = viewModel()
        assertEquals(update, state(viewModel).update)

        viewModel.onUpdateDismissed(update)
        runCurrent()

        assertNull(viewModel.uiState.value.update)
        assertEquals(listOf(update), updates.dismissed)
    }
}
