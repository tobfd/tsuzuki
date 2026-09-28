package com.tobfd.tsuzuki.core.network.error

import com.apollographql.apollo.api.ApolloResponse
import com.apollographql.apollo.api.Error
import com.apollographql.apollo.api.Operation
import com.apollographql.apollo.exception.ApolloException
import com.apollographql.apollo.exception.ApolloHttpException
import com.apollographql.apollo.exception.ApolloNetworkException
import com.apollographql.apollo.exception.ApolloOfflineException
import com.tobfd.tsuzuki.core.common.AppError

private const val HTTP_UNAUTHORIZED = 401
private const val HTTP_FORBIDDEN = 403
private const val HTTP_NOT_FOUND = 404
private const val HTTP_TOO_MANY_REQUESTS = 429
private const val DEFAULT_RETRY_AFTER_SEC = 60L

/** An AniList failure reduced to the facts the mapping needs, independent of Apollo types. */
data class AniListFailure(
    val httpStatus: Int? = null,
    val message: String? = null,
    val retryAfterSec: Long? = null,
    val validation: Map<String, List<String>>? = null,
    val networkFailure: Boolean = false
)

/** The rules from docs/ANILIST_API.md ("Errors"). Pure, so it is unit tested directly. */
fun AniListFailure.toAppError(): AppError {
    val text = message.orEmpty()
    return when {
        networkFailure -> AppError.Offline

        httpStatus == HTTP_TOO_MANY_REQUESTS || text.contains("Too Many Requests", ignoreCase = true) ->
            AppError.RateLimited(retryAfterSec ?: DEFAULT_RETRY_AFTER_SEC)

        httpStatus == HTTP_FORBIDDEN && text.contains("temporarily disabled", ignoreCase = true) ->
            AppError.ApiUnavailable

        httpStatus == HTTP_UNAUTHORIZED || text.contains("Invalid token", ignoreCase = true) ->
            AppError.Unauthorized

        !validation.isNullOrEmpty() -> AppError.Validation(validation)

        httpStatus == HTTP_NOT_FOUND || text.equals("Not Found.", ignoreCase = true) -> AppError.NotFound

        else -> AppError.Unknown(message)
    }
}

/**
 * The [AppError] for a failed response, or null when it succeeded. AniList also reports errors with
 * HTTP 200, so GraphQL `errors` are checked even when data is present.
 */
fun <D : Operation.Data> ApolloResponse<D>.appErrorOrNull(): AppError? {
    exception?.let { return it.toAppError() }
    val firstError = errors?.firstOrNull() ?: return null
    return firstError.toFailure().toAppError()
}

fun ApolloException.toAppError(): AppError = when (this) {
    is ApolloOfflineException, is ApolloNetworkException -> AniListFailure(networkFailure = true).toAppError()

    is ApolloHttpException -> AniListFailure(
        httpStatus = statusCode,
        // Only available with httpExposeErrorBody(true); the body must be closed after reading.
        message = body?.use { it.readUtf8() },
        retryAfterSec = headers.firstOrNull { it.name.equals("Retry-After", ignoreCase = true) }
            ?.value?.toLongOrNull()
    ).toAppError()

    else -> AppError.Unknown(message, this)
}

/** AniList puts `status` and `validation` next to `message`, which Apollo keeps as non-standard fields. */
private fun Error.toFailure(): AniListFailure = AniListFailure(
    httpStatus = (nonStandardFields?.get("status") as? Number)?.toInt(),
    message = message,
    validation = (nonStandardFields?.get("validation") as? Map<*, *>)?.toValidation()
)

private fun Map<*, *>.toValidation(): Map<String, List<String>> = entries.associate { (field, messages) ->
    field.toString() to when (messages) {
        is List<*> -> messages.map { it.toString() }
        null -> emptyList()
        else -> listOf(messages.toString())
    }
}
