package com.tobfd.tsuzuki.core.testing

import com.tobfd.tsuzuki.core.data.session.SessionRepository
import com.tobfd.tsuzuki.core.model.SessionState
import com.tobfd.tsuzuki.core.model.Viewer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** In-memory [SessionRepository]; set [logInResult] and, to observe progress, [logInGate]. */
class FakeSessionRepository(initial: SessionState = SessionState.LoggedOut()) : SessionRepository {
    val sessionState = MutableStateFlow(initial)
    override val session: StateFlow<SessionState> = sessionState

    var logInResult: Result<Viewer> = Result.success(SampleData.viewer)

    /** When set, [logIn] suspends until it is completed. */
    var logInGate: CompletableDeferred<Unit>? = null

    val loggedInTokens = mutableListOf<String>()
    var validateCalls = 0
        private set

    override suspend fun validate() {
        validateCalls++
    }

    override suspend fun logIn(accessToken: String): Result<Viewer> {
        loggedInTokens += accessToken
        logInGate?.await()
        logInResult.onSuccess { sessionState.value = SessionState.LoggedIn(it, SampleData.tokenExpiry) }
        return logInResult
    }

    override suspend fun continueAsGuest() {
        sessionState.value = SessionState.Guest
    }

    override suspend fun logOut() {
        sessionState.value = SessionState.LoggedOut()
    }
}
