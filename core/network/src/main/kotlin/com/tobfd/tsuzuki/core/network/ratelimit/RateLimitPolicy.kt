package com.tobfd.tsuzuki.core.network.ratelimit

import kotlin.math.ceil

private const val MILLIS_PER_MINUTE = 60_000.0

/**
 * The scheduling rules from docs/ANILIST_API.md ("Rate limit"), without threads or clocks so they can
 * be tested with plain numbers. Not thread-safe; [RateLimitInterceptor] synchronizes access.
 *
 * - Token bucket sized from the last `X-RateLimit-Limit` (starting at [initialLimitPerMinute]),
 *   refilled continuously over a minute. Requests wait for a token instead of failing.
 * - At least [minSpacingMs] between request starts (AniList's burst limiter).
 * - After a 429, nothing starts before [blockedUntilMs].
 */
class RateLimitPolicy(
    initialLimitPerMinute: Int = DEFAULT_LIMIT_PER_MINUTE,
    private val minSpacingMs: Long = DEFAULT_MIN_SPACING_MS
) {
    private var limit = initialLimitPerMinute.toDouble()
    private var tokens = limit

    /** Time the token count refers to; may lie in the future when requests are already scheduled. */
    private var tokensAtMs: Long? = null
    private var lastStartMs: Long? = null

    var blockedUntilMs: Long = 0L
        private set

    val limitPerMinute: Int get() = limit.toInt()

    private val refillPerMs: Double get() = limit / MILLIS_PER_MINUTE

    /**
     * Reserves the next slot and returns when the request may start (never before [nowMs]). Each call
     * uses up one token, so callers must start their request at the returned time.
     */
    fun reserve(nowMs: Long): Long {
        val base = tokensAtMs ?: nowMs
        if (nowMs > base) {
            tokens = minOf(limit, tokens + (nowMs - base) * refillPerMs)
            tokensAtMs = nowMs
        } else {
            tokensAtMs = base
        }
        val tokenTime = tokensAtMs!!

        var start = maxOf(nowMs, blockedUntilMs, tokenTime)
        lastStartMs?.let { start = maxOf(start, it + minSpacingMs) }

        var available = minOf(limit, tokens + (start - tokenTime) * refillPerMs)
        if (available < 1.0) {
            start += ceil((1.0 - available) / refillPerMs).toLong()
            available = 1.0
        }

        tokens = available - 1.0
        tokensAtMs = start
        lastStartMs = start
        return start
    }

    /** Applies the `X-RateLimit-Limit` / `X-RateLimit-Remaining` headers of a response. */
    fun onResponseHeaders(limitPerMinute: Int?, remaining: Int?) {
        if (limitPerMinute != null && limitPerMinute > 0) {
            limit = limitPerMinute.toDouble()
            tokens = minOf(tokens, limit)
        }
        if (remaining != null && remaining >= 0) {
            tokens = minOf(tokens, remaining.toDouble())
        }
    }

    /** After a 429: block every request until [untilMs]. */
    fun blockUntil(untilMs: Long) {
        blockedUntilMs = maxOf(blockedUntilMs, untilMs)
        tokens = 0.0
    }

    companion object {
        /** AniList's limit is 90/min normally but currently degraded to 30/min. */
        const val DEFAULT_LIMIT_PER_MINUTE = 30
        const val DEFAULT_MIN_SPACING_MS = 300L
    }
}
