package com.tobfd.tsuzuki.core.data.session

import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.exception.ApolloException
import com.apollographql.cache.normalized.FetchPolicy
import com.apollographql.cache.normalized.apolloStore
import com.apollographql.cache.normalized.fetchPolicy
import com.tobfd.tsuzuki.core.common.AppError
import com.tobfd.tsuzuki.core.common.ApplicationScope
import com.tobfd.tsuzuki.core.data.mapper.toViewer
import com.tobfd.tsuzuki.core.datastore.SessionStore
import com.tobfd.tsuzuki.core.datastore.StoredSession
import com.tobfd.tsuzuki.core.model.LogoutReason
import com.tobfd.tsuzuki.core.model.SessionState
import com.tobfd.tsuzuki.core.model.Viewer
import com.tobfd.tsuzuki.core.network.ViewerQuery
import com.tobfd.tsuzuki.core.network.auth.AuthEvents
import com.tobfd.tsuzuki.core.network.error.appErrorOrNull
import com.tobfd.tsuzuki.core.network.error.toAppError
import java.time.Clock
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private val VIEWER_MAX_AGE: Duration = Duration.ofHours(1)

@Singleton
class DefaultSessionRepository @Inject constructor(
    private val store: SessionStore,
    private val apolloClient: ApolloClient,
    private val clock: Clock,
    authEvents: AuthEvents,
    @ApplicationScope appScope: CoroutineScope
) : SessionRepository {

    /** True while [logIn] runs, so its own 401 shows as a login error, not as "session ended". */
    @Volatile
    private var loggingIn = false

    init {
        appScope.launch {
            authEvents.unauthorized.collect {
                if (!loggingIn && store.accessToken() != null) endSession(LogoutReason.Unauthorized)
            }
        }
    }

    override val session: Flow<SessionState> = store.session
        .map { it.toSessionState() }
        .distinctUntilChanged()

    override suspend fun validate() {
        val stored = store.session.first()
        val expiresAt = stored.expiresAt
        if (stored.accessToken == null || expiresAt == null) return
        if (!expiresAt.isAfter(clock.instant())) {
            endSession(LogoutReason.Expired)
            return
        }
        val fetchedAt = stored.viewerFetchedAt
        val stale = stored.viewer == null || fetchedAt == null ||
            Duration.between(fetchedAt, clock.instant()) >= VIEWER_MAX_AGE
        if (stale) {
            // Offline or rate limited: keep the cached viewer. A 401 ends the session via AuthEvents.
            fetchViewer()
        }
    }

    override suspend fun logIn(accessToken: String): Result<Viewer> {
        val expiresAt = JwtClaims.parse(accessToken)?.expiresAt
        if (expiresAt == null || !expiresAt.isAfter(clock.instant())) {
            return Result.failure(AppError.Unauthorized)
        }
        loggingIn = true
        return try {
            store.saveToken(accessToken, expiresAt)
            fetchViewer().onFailure { store.clear() }
        } finally {
            loggingIn = false
        }
    }

    override suspend fun continueAsGuest() {
        store.enterGuestMode()
    }

    override suspend fun logOut() {
        endSession(reason = null)
    }

    private suspend fun fetchViewer(): Result<Viewer> {
        val response = try {
            apolloClient.query(ViewerQuery()).fetchPolicy(FetchPolicy.NetworkOnly).execute()
        } catch (e: ApolloException) {
            return Result.failure(e.toAppError())
        }
        response.appErrorOrNull()?.let { return Result.failure(it) }
        val viewer = response.data?.Viewer?.toViewer()
            ?: return Result.failure(AppError.Unknown("AniList returned no viewer"))
        store.saveViewer(viewer, clock.instant())
        return Result.success(viewer)
    }

    private suspend fun endSession(reason: LogoutReason?) {
        store.clear(reason)
        apolloClient.apolloStore.clearAll()
    }

    private fun StoredSession.toSessionState(): SessionState {
        val viewer = viewer
        val expiresAt = expiresAt
        return when {
            // An expired token counts as logged out right away; validate() then clears it.
            accessToken != null && expiresAt != null && !expiresAt.isAfter(clock.instant()) ->
                SessionState.LoggedOut(LogoutReason.Expired)

            accessToken != null && viewer != null && expiresAt != null -> SessionState.LoggedIn(viewer, expiresAt)

            guest -> SessionState.Guest

            else -> SessionState.LoggedOut(logoutReason)
        }
    }
}
