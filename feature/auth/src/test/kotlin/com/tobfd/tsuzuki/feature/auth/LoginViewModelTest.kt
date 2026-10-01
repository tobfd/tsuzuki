package com.tobfd.tsuzuki.feature.auth

import app.cash.turbine.test
import com.tobfd.tsuzuki.core.common.AppError
import com.tobfd.tsuzuki.core.model.LogoutReason
import com.tobfd.tsuzuki.core.model.SessionState
import com.tobfd.tsuzuki.core.testing.FakeSessionRepository
import com.tobfd.tsuzuki.core.testing.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class LoginViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val sessionRepository = FakeSessionRepository()
    private val authRedirects = AuthRedirects()

    private fun viewModel() = LoginViewModel(sessionRepository, authRedirects, clientId = "12345")

    @Test
    fun logInClick_opensTheAniListAuthorizePageWithTheClientId() = runTest {
        val viewModel = viewModel()
        viewModel.effects.test {
            viewModel.onLogInClick()
            assertEquals(
                LoginEffect.OpenAuthorizePage(
                    "https://anilist.co/api/v2/oauth/authorize?client_id=12345&response_type=token"
                ),
                awaitItem()
            )
        }
    }

    @Test
    fun redirectWithToken_logsInAndShowsProgressUntilDone() = runTest {
        val gate = CompletableDeferred<Unit>()
        sessionRepository.logInGate = gate
        val viewModel = viewModel()
        viewModel.uiState.test {
            assertEquals(LoginUiState(), awaitItem())

            authRedirects.dispatch("tsuzuki://auth#access_token=a.b.c&token_type=Bearer")
            assertEquals(LoginUiState(loggingIn = true), awaitItem())

            gate.complete(Unit)
            assertEquals(LoginUiState(loggingIn = false), awaitItem())
        }
        assertEquals(listOf("a.b.c"), sessionRepository.loggedInTokens)
        assertTrue(sessionRepository.sessionState.value is SessionState.LoggedIn)
    }

    @Test
    fun failedLogin_showsTheError() = runTest {
        sessionRepository.logInResult = Result.failure(AppError.Offline)
        val viewModel = viewModel()
        viewModel.uiState.test {
            awaitItem()
            authRedirects.dispatch("tsuzuki://auth#access_token=a.b.c")
            assertEquals(LoginUiState(error = LoginError.Api(AppError.Offline)), expectMostRecentItem())
        }
    }

    @Test
    fun deniedRedirect_showsDeniedWithoutLoggingIn() = runTest {
        val viewModel = viewModel()
        viewModel.uiState.test {
            awaitItem()
            authRedirects.dispatch("tsuzuki://auth?error=access_denied")
            assertEquals(LoginUiState(error = LoginError.Denied), awaitItem())
        }
        assertTrue(sessionRepository.loggedInTokens.isEmpty())
    }

    @Test
    fun redirectWithoutToken_showsInvalidRedirect() = runTest {
        val viewModel = viewModel()
        viewModel.uiState.test {
            awaitItem()
            authRedirects.dispatch("tsuzuki://auth")
            assertEquals(LoginUiState(error = LoginError.InvalidRedirect), awaitItem())
        }
    }

    @Test
    fun redirectBeforeTheScreenExists_isStillHandled() = runTest {
        authRedirects.dispatch("tsuzuki://auth#access_token=early.token.value")
        viewModel()
        assertEquals(listOf("early.token.value"), sessionRepository.loggedInTokens)
    }

    @Test
    fun noBrowser_showsNoBrowserAndTryingAgainClearsIt() = runTest {
        val viewModel = viewModel()
        viewModel.uiState.test {
            awaitItem()
            viewModel.onBrowserUnavailable()
            assertEquals(LoginUiState(error = LoginError.NoBrowser), awaitItem())
            viewModel.onLogInClick()
            assertEquals(LoginUiState(), awaitItem())
        }
    }

    @Test
    fun expiredSession_showsTheReason() = runTest {
        sessionRepository.sessionState.value = SessionState.LoggedOut(LogoutReason.Expired)
        viewModel().uiState.test {
            assertEquals(LoginUiState(logoutReason = LogoutReason.Expired), expectMostRecentItem())
        }
    }

    @Test
    fun browseWithoutAccount_entersGuestMode() = runTest {
        viewModel().onBrowseAsGuestClick()
        assertEquals(SessionState.Guest, sessionRepository.sessionState.value)
    }
}
