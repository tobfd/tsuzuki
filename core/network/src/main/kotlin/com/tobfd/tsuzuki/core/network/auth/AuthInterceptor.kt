package com.tobfd.tsuzuki.core.network.auth

import java.net.HttpURLConnection.HTTP_UNAUTHORIZED
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import okhttp3.Interceptor
import okhttp3.Response

/** Supplies the current AniList access token, or null for guests. Implemented in `core/data`. */
fun interface AccessTokenProvider {
    fun accessToken(): String?
}

/**
 * Tells the session layer that AniList rejected the token (HTTP 401). `core/data` listens and ends the
 * session; the network layer stays unaware of how sessions are stored.
 */
@Singleton
class AuthEvents @Inject constructor() {
    private val unauthorizedEvents = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    val unauthorized: SharedFlow<Unit> = unauthorizedEvents.asSharedFlow()

    fun notifyUnauthorized() {
        unauthorizedEvents.tryEmit(Unit)
    }
}

/** Adds `Authorization: Bearer <token>` when logged in and reports 401 responses. Never logs the token. */
class AuthInterceptor @Inject constructor(
    private val tokenProvider: AccessTokenProvider,
    private val authEvents: AuthEvents
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = tokenProvider.accessToken()
        val request = if (token == null) {
            chain.request()
        } else {
            chain.request().newBuilder().header("Authorization", "Bearer $token").build()
        }
        val response = chain.proceed(request)
        if (token != null && response.code == HTTP_UNAUTHORIZED) {
            authEvents.notifyUnauthorized()
        }
        return response
    }
}
