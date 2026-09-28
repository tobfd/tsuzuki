package com.tobfd.tsuzuki.core.common

/**
 * Every failure the app shows to the user. Repositories map network, API and storage failures to one
 * of these; screens show an error state with retry, actions use the snackbar (see CLAUDE.md).
 */
sealed class AppError(message: String? = null, cause: Throwable? = null) : Exception(message, cause) {
    /** No connection or a timeout. */
    data object Offline : AppError("Offline")

    /** AniList's rate limit was hit; requests resume after [retryAfterSec]. */
    data class RateLimited(val retryAfterSec: Long) : AppError("Rate limited, retry after $retryAfterSec s")

    /** AniList has temporarily disabled its API; keep working from cache. */
    data object ApiUnavailable : AppError("AniList API temporarily disabled")

    /** The token is invalid or expired; the session ends. */
    data object Unauthorized : AppError("Unauthorized")

    /** The API rejected input; [fields] maps field names to messages. */
    data class Validation(val fields: Map<String, List<String>>) : AppError("Validation failed: $fields")

    data object NotFound : AppError("Not found")

    data class Unknown(val detail: String?, val origin: Throwable? = null) : AppError(detail, origin)
}
