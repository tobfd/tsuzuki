package com.tobfd.tsuzuki.core.network.error

import com.tobfd.tsuzuki.core.common.AppError
import org.junit.Assert.assertEquals
import org.junit.Test

class AniListErrorsTest {

    @Test
    fun networkFailure_isOffline() {
        assertEquals(AppError.Offline, AniListFailure(networkFailure = true).toAppError())
    }

    @Test
    fun http429_isRateLimitedWithRetryAfter() {
        assertEquals(AppError.RateLimited(30), AniListFailure(httpStatus = 429, retryAfterSec = 30).toAppError())
    }

    @Test
    fun tooManyRequestsMessageWithoutRetryAfter_defaultsToOneMinute() {
        assertEquals(AppError.RateLimited(60), AniListFailure(message = "Too Many Requests.").toAppError())
    }

    @Test
    fun http403ApiDisabled_isApiUnavailable() {
        val failure = AniListFailure(
            httpStatus = 403,
            message = """{"errors":[{"message":"The API has been temporarily disabled."}]}"""
        )
        assertEquals(AppError.ApiUnavailable, failure.toAppError())
    }

    @Test
    fun otherHttp403_isUnknown() {
        assertEquals(
            AppError.Unknown("Forbidden"),
            AniListFailure(httpStatus = 403, message = "Forbidden").toAppError()
        )
    }

    @Test
    fun http401_isUnauthorized() {
        assertEquals(AppError.Unauthorized, AniListFailure(httpStatus = 401).toAppError())
    }

    @Test
    fun invalidTokenMessageWithHttp200_isUnauthorized() {
        assertEquals(AppError.Unauthorized, AniListFailure(httpStatus = 400, message = "Invalid token").toAppError())
    }

    @Test
    fun notFound_isNotFound() {
        assertEquals(AppError.NotFound, AniListFailure(httpStatus = 404, message = "Not Found.").toAppError())
        assertEquals(AppError.NotFound, AniListFailure(message = "Not Found.").toAppError())
    }

    @Test
    fun validationMap_isValidationWithFieldMessages() {
        val fields = mapOf("scoreRaw" to listOf("The score raw must be between 0 and 100."))
        assertEquals(
            AppError.Validation(fields),
            AniListFailure(httpStatus = 400, message = "validation", validation = fields).toAppError()
        )
    }

    @Test
    fun anythingElse_isUnknownWithTheMessage() {
        assertEquals(
            AppError.Unknown("Internal Server Error"),
            AniListFailure(httpStatus = 500, message = "Internal Server Error").toAppError()
        )
    }
}
