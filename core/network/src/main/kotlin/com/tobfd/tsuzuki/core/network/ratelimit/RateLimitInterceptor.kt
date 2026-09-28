package com.tobfd.tsuzuki.core.network.ratelimit

import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.Interceptor
import okhttp3.Response

private const val HTTP_TOO_MANY_REQUESTS = 429
private const val DEFAULT_TIMEOUT_SEC = 60L
private const val MAX_IN_FLIGHT = 2

/**
 * App-wide gate in front of every AniList request (docs/ANILIST_API.md, "Rate limit"): at most
 * [MAX_IN_FLIGHT] requests at once, spacing and token bucket from [RateLimitPolicy], and on a 429 a
 * block until `Retry-After` has passed, then one retry. If the retry is rate limited too, the 429
 * reaches the caller and maps to `AppError.RateLimited`.
 *
 * OkHttp runs interceptors on its own threads, so waiting here blocks that thread only.
 */
class RateLimitInterceptor(
    private val policy: RateLimitPolicy = RateLimitPolicy(),
    private val clock: () -> Long = System::currentTimeMillis,
    private val sleep: (Long) -> Unit = Thread::sleep,
    private val log: (String) -> Unit = {}
) : Interceptor {
    private val inFlight = Semaphore(MAX_IN_FLIGHT, true)
    private val blockedUntil = MutableStateFlow(0L)

    /** Epoch millis until which requests are held back after a 429; in the past when not blocked. */
    val blockedUntilMs: StateFlow<Long> = blockedUntil.asStateFlow()

    override fun intercept(chain: Interceptor.Chain): Response {
        inFlight.acquire()
        try {
            val response = proceedWhenAllowed(chain)
            if (response.code != HTTP_TOO_MANY_REQUESTS) return response

            val retryAt = clock() + TimeUnit.SECONDS.toMillis(retryAfterSeconds(response))
            synchronized(policy) {
                policy.blockUntil(retryAt)
                blockedUntil.value = policy.blockedUntilMs
            }
            log("429 from AniList, holding requests until $retryAt")
            response.close()
            return proceedWhenAllowed(chain)
        } finally {
            inFlight.release()
        }
    }

    private fun proceedWhenAllowed(chain: Interceptor.Chain): Response {
        val startAt = synchronized(policy) { policy.reserve(clock()) }
        val wait = startAt - clock()
        if (wait > 0) sleep(wait)

        val response = chain.proceed(chain.request())
        val limit = response.header("X-RateLimit-Limit")?.toIntOrNull()
        val remaining = response.header("X-RateLimit-Remaining")?.toIntOrNull()
        synchronized(policy) { policy.onResponseHeaders(limit, remaining) }
        if (remaining != null) log("AniList quota: $remaining / ${limit ?: "?"} left")
        return response
    }

    private fun retryAfterSeconds(response: Response): Long {
        response.header("Retry-After")?.toLongOrNull()?.let { return it.coerceAtLeast(1) }
        response.header("X-RateLimit-Reset")?.toLongOrNull()?.let { resetEpochSec ->
            val seconds = resetEpochSec - TimeUnit.MILLISECONDS.toSeconds(clock())
            if (seconds > 0) return seconds
        }
        return DEFAULT_TIMEOUT_SEC
    }
}
