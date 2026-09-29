package com.tobfd.tsuzuki.core.data.session

import com.tobfd.tsuzuki.core.model.SessionState
import com.tobfd.tsuzuki.core.model.Viewer
import kotlinx.coroutines.flow.Flow

/** Login, logout and guest mode. The single source of truth for who uses the app. */
interface SessionRepository {
    val session: Flow<SessionState>

    /**
     * Call on app start: ends an expired session and refreshes the cached viewer when it is older than
     * an hour (one request; a 401 ends the session).
     */
    suspend fun validate()

    /**
     * Completes a login with the token from the OAuth redirect: stores it encrypted, fetches the
     * [Viewer] and caches it. On failure the token is discarded and the error is an `AppError`.
     */
    suspend fun logIn(accessToken: String): Result<Viewer>

    suspend fun continueAsGuest()

    /**
     * Clears the token, the cached viewer and AniList options, the Apollo cache, and the lists in
     * Room with their unsent changes; background list work stops.
     */
    suspend fun logOut()
}
