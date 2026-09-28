package com.tobfd.tsuzuki.core.model

import java.time.Instant

/** Who is using the app right now. */
sealed interface SessionState {
    /** Still reading the stored session. */
    data object Loading : SessionState

    /** Nobody is logged in; [reason] explains why a previous session ended, if it did. */
    data class LoggedOut(val reason: LogoutReason? = null) : SessionState

    /** "Browse without an account": Browse, detail and people pages only. */
    data object Guest : SessionState

    /** Logged in; the token stops working at [expiresAt] and cannot be refreshed. */
    data class LoggedIn(val viewer: Viewer, val expiresAt: Instant) : SessionState
}

/** Why a session ended without the user logging out. */
enum class LogoutReason {
    /** The one-year AniList token expired. */
    Expired,

    /** AniList rejected the token (HTTP 401), e.g. because access was revoked. */
    Unauthorized
}
