package com.tobfd.tsuzuki

import com.tobfd.tsuzuki.core.model.LogoutReason
import com.tobfd.tsuzuki.core.model.SessionState
import com.tobfd.tsuzuki.core.testing.FakeListRepository
import com.tobfd.tsuzuki.core.testing.FakeNotificationsRepository
import com.tobfd.tsuzuki.core.testing.FakeSessionRepository
import com.tobfd.tsuzuki.core.testing.MainDispatcherRule
import com.tobfd.tsuzuki.core.testing.SampleData
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MainViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val sessionRepository = FakeSessionRepository()
    private val notificationsRepository = FakeNotificationsRepository(initialCount = 3)
    private val listRepository = FakeListRepository()

    /** 100 days before the sample token expires, so no warning unless a test moves the clock. */
    private fun viewModel(now: Instant = SampleData.tokenExpiry - Duration.ofDays(100)) =
        MainViewModel(sessionRepository, notificationsRepository, listRepository, Clock.fixed(now, ZoneOffset.UTC))

    private val loggedIn = SessionState.LoggedIn(SampleData.viewer, SampleData.tokenExpiry)

    @Test
    fun loggedOutSession_isLoggedOut() {
        sessionRepository.sessionState.value = SessionState.LoggedOut(LogoutReason.Expired)
        assertEquals(MainUiState.LoggedOut, viewModel().uiState.value)
    }

    @Test
    fun loadingSession_keepsTheSplash() {
        sessionRepository.sessionState.value = SessionState.Loading
        assertEquals(MainUiState.Loading, viewModel().uiState.value)
    }

    @Test
    fun guestSession_isGuest() {
        sessionRepository.sessionState.value = SessionState.Guest
        assertEquals(MainUiState.Guest, viewModel().uiState.value)
    }

    @Test
    fun loggedInSession_showsViewerAndUnreadCountWithoutWarning() {
        sessionRepository.sessionState.value = loggedIn
        assertEquals(
            MainUiState.LoggedIn(SampleData.viewer, expiryWarningDays = null, unreadNotificationCount = 3),
            viewModel().uiState.value
        )
    }

    @Test
    fun tokenExpiringWithin14Days_showsTheWarningDays() {
        sessionRepository.sessionState.value = loggedIn
        val state = viewModel(now = SampleData.tokenExpiry - Duration.ofDays(5)).uiState.value
        assertEquals(5L, (state as MainUiState.LoggedIn).expiryWarningDays)
    }

    @Test
    fun newUnreadCount_updatesTheState() {
        sessionRepository.sessionState.value = loggedIn
        val viewModel = viewModel()

        notificationsRepository.unreadCount.value = 12

        assertEquals(12, (viewModel.uiState.value as MainUiState.LoggedIn).unreadNotificationCount)
    }

    @Test
    fun start_validatesTheSession() {
        viewModel()
        assertEquals(1, sessionRepository.validateCalls)
    }

    @Test
    fun startWhileLoggedIn_forcesOneBadgeRefresh() {
        sessionRepository.sessionState.value = loggedIn
        viewModel()
        assertEquals(listOf(true), notificationsRepository.refreshCalls)
    }

    @Test
    fun login_forcesABadgeRefreshOncePerViewer() {
        val viewModel = viewModel()
        assertEquals(emptyList<Boolean>(), notificationsRepository.refreshCalls)

        sessionRepository.sessionState.value = loggedIn
        // The same viewer with a later expiry (e.g. after a viewer refresh) is not a new login.
        sessionRepository.sessionState.value = loggedIn.copy(expiresAt = SampleData.tokenExpiry + Duration.ofDays(1))

        assertEquals(listOf(true), notificationsRepository.refreshCalls)
        assertEquals(MainUiState.LoggedIn::class, viewModel.uiState.value::class)
    }

    @Test
    fun appResumedWhileLoggedIn_refreshesWithTheFiveMinuteLimit() {
        sessionRepository.sessionState.value = loggedIn
        val viewModel = viewModel()

        viewModel.onAppResumed()

        assertEquals(listOf(true, false), notificationsRepository.refreshCalls)
    }

    @Test
    fun login_syncsTheListsAndSchedulesBackgroundSync() {
        viewModel()
        assertEquals(emptyList<Boolean>(), listRepository.refreshCalls)

        sessionRepository.sessionState.value = loggedIn

        assertEquals(listOf(false), listRepository.refreshCalls)
        assertEquals(true, listRepository.backgroundSyncScheduled)
    }

    @Test
    fun appResumedWhileLoggedIn_syncsTheListsWithTheFifteenMinuteRule() {
        sessionRepository.sessionState.value = loggedIn
        val viewModel = viewModel()

        viewModel.onAppResumed()

        assertEquals(listOf(false, false), listRepository.refreshCalls)
    }

    @Test
    fun appResumedAsGuest_makesNoRequest() {
        sessionRepository.sessionState.value = SessionState.Guest
        viewModel().onAppResumed()
        assertEquals(emptyList<Boolean>(), notificationsRepository.refreshCalls)
        assertEquals(emptyList<Boolean>(), listRepository.refreshCalls)
    }

    @Test
    fun logOut_endsTheSession() {
        sessionRepository.sessionState.value = loggedIn
        val viewModel = viewModel()

        viewModel.onLogOut()

        assertEquals(MainUiState.LoggedOut, viewModel.uiState.value)
    }
}
