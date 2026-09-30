package com.tobfd.tsuzuki.feature.profile

import app.cash.turbine.test
import com.tobfd.tsuzuki.core.common.AppError
import com.tobfd.tsuzuki.core.model.Activity
import com.tobfd.tsuzuki.core.model.ContentPage
import com.tobfd.tsuzuki.core.model.Favourites
import com.tobfd.tsuzuki.core.model.FollowUser
import com.tobfd.tsuzuki.core.model.ListStatistics
import com.tobfd.tsuzuki.core.model.SessionState
import com.tobfd.tsuzuki.core.model.UserLite
import com.tobfd.tsuzuki.core.model.UserProfile
import com.tobfd.tsuzuki.core.testing.FakeHomeRepository
import com.tobfd.tsuzuki.core.testing.FakeProfileRepository
import com.tobfd.tsuzuki.core.testing.FakeSessionRepository
import com.tobfd.tsuzuki.core.testing.MainDispatcherRule
import com.tobfd.tsuzuki.core.testing.SampleData
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ProfileViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val profiles = FakeProfileRepository()
    private val home = FakeHomeRepository()
    private val session = FakeSessionRepository(SessionState.LoggedIn(SampleData.viewer, SampleData.tokenExpiry))
    private val clock = Clock.fixed(Instant.parse("2026-09-30T23:30:00Z"), ZoneOffset.UTC)

    private val text = Activity.Text(
        id = 101,
        user = UserLite(9, "GeckoTV", null),
        createdAt = Instant.EPOCH,
        likeCount = 2,
        isLiked = false,
        replyCount = 0,
        siteUrl = null,
        html = "Hi"
    )

    private fun profile(id: Int) = UserProfile(
        id = id,
        name = "GeckoTV",
        aboutHtml = null,
        avatarUrl = null,
        bannerUrl = null,
        siteUrl = null,
        isFollowing = false,
        isFollower = true,
        anime = ListStatistics.Empty,
        manga = ListStatistics.Empty,
        activityHistory = emptyList(),
        favourites = Favourites(emptyList(), emptyList(), emptyList(), emptyList()),
        recentActivity = listOf(text)
    )

    private fun TestScope.viewModel(userId: Int = 9): ProfileViewModel =
        ProfileViewModel(userId, profiles, home, session, clock).also { viewModel ->
            backgroundScope.launch { viewModel.uiState.collect {} }
            runCurrent()
        }

    private fun ProfileViewModel.content() = uiState.value as ProfileUiState.Content

    @Test
    fun profile_isOneRequestAndKnowsWhetherItIsTheViewers() = runTest {
        profiles.profileResult = Result.success(profile(9))
        val other = viewModel()
        assertFalse(other.content().isOwn)
        assertEquals(LocalDate.of(2026, 9, 30), other.content().today)
        assertEquals(1, profiles.profileRequests)

        profiles.profileResult = Result.success(profile(SampleData.viewer.id))
        assertTrue(viewModel(SampleData.viewer.id).content().isOwn)
    }

    @Test
    fun profile_whenLoadingFails_showsTheErrorAndRetries() = runTest {
        profiles.profileResult = Result.failure(AppError.Offline)
        val viewModel = viewModel()
        assertEquals(ProfileUiState.Error(AppError.Offline), viewModel.uiState.value)

        profiles.profileResult = Result.success(profile(9))
        viewModel.onRetry()
        runCurrent()
        assertEquals("GeckoTV", viewModel.content().profile.name)
    }

    @Test
    fun refresh_keepsThePageAndReportsAFailure() = runTest {
        profiles.profileResult = Result.success(profile(9))
        val viewModel = viewModel()
        profiles.profileResult = Result.failure(AppError.RateLimited(30))

        viewModel.eventFlow.test {
            viewModel.onRefresh()
            assertEquals(ProfileEvent.Failed(AppError.RateLimited(30)), awaitItem())
        }
        assertFalse(viewModel.content().isRefreshing)
        assertEquals(2, profiles.profileRequests)
    }

    @Test
    fun socialList_loadsWhenShownAndOnlyOnce() = runTest {
        profiles.profileResult = Result.success(profile(9))
        profiles.followsResult = Result.success(ContentPage(listOf(FollowUser(UserLite(1, "a", null), true)), false))
        val viewModel = viewModel()

        viewModel.onShowFollowList(FollowList.Followers)
        runCurrent()
        viewModel.onShowFollowList(FollowList.Followers)
        runCurrent()

        assertEquals(listOf(true), profiles.followRequests)
        val list = viewModel.content().followLists.getValue(FollowList.Followers) as FollowListState.Loaded
        assertEquals("a", list.page.items.single().user.name)
    }

    @Test
    fun follow_isOptimisticAndRollsBack() = runTest {
        profiles.profileResult = Result.success(profile(9))
        profiles.followResult = Result.failure(AppError.Offline)
        val viewModel = viewModel()

        viewModel.eventFlow.test {
            viewModel.onToggleFollow()
            assertEquals(ProfileEvent.Failed(AppError.Offline), awaitItem())
        }
        assertFalse(viewModel.content().isFollowing)

        profiles.followResult = Result.success(true)
        viewModel.onToggleFollow()
        runCurrent()
        assertTrue(viewModel.content().isFollowing)
        assertEquals(listOf(9, 9), profiles.followToggles)
    }

    @Test
    fun follow_asGuest_asksToLogIn() = runTest {
        session.sessionState.value = SessionState.Guest
        profiles.profileResult = Result.success(profile(9))
        val viewModel = viewModel()

        viewModel.eventFlow.test {
            viewModel.onToggleFollow()
            assertEquals(ProfileEvent.LogInToUse, awaitItem())
        }
        assertTrue(profiles.followToggles.isEmpty())
    }

    @Test
    fun like_changesTheActivityAtOnce() = runTest {
        profiles.profileResult = Result.success(profile(9))
        val viewModel = viewModel()

        viewModel.onToggleLike(text)
        runCurrent()

        val liked = viewModel.content().profile.recentActivity.single()
        assertTrue(liked.isLiked)
        assertEquals(3, liked.likeCount)
        assertEquals(listOf(101), home.likedIds)
    }
}
