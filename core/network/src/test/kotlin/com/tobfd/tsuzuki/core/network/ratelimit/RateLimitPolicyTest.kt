package com.tobfd.tsuzuki.core.network.ratelimit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RateLimitPolicyTest {

    @Test
    fun firstRequest_startsImmediately() {
        val policy = RateLimitPolicy()
        assertEquals(1_000L, policy.reserve(nowMs = 1_000L))
    }

    @Test
    fun backToBackRequests_areSpacedByAtLeast300Ms() {
        val policy = RateLimitPolicy()
        val starts = List(5) { policy.reserve(nowMs = 0L) }
        assertEquals(listOf(0L, 300L, 600L, 900L, 1_200L), starts)
    }

    @Test
    fun requestAfterTheSpacing_isNotDelayed() {
        val policy = RateLimitPolicy()
        policy.reserve(nowMs = 0L)
        assertEquals(5_000L, policy.reserve(nowMs = 5_000L))
    }

    @Test
    fun emptyBucket_waitsForTheNextToken() {
        // 30 per minute without spacing: 30 tokens up front, then one every 2 seconds.
        val policy = RateLimitPolicy(initialLimitPerMinute = 30, minSpacingMs = 0L)
        repeat(30) { assertEquals(0L, policy.reserve(nowMs = 0L)) }
        assertEquals(2_000L, policy.reserve(nowMs = 0L))
        assertEquals(4_000L, policy.reserve(nowMs = 0L))
    }

    @Test
    fun bucketRefills_overTime() {
        val policy = RateLimitPolicy(initialLimitPerMinute = 30, minSpacingMs = 0L)
        repeat(30) { policy.reserve(nowMs = 0L) }
        // A full minute later the bucket is full again.
        repeat(30) { assertEquals(60_000L, policy.reserve(nowMs = 60_000L)) }
    }

    @Test
    fun remainingHeaderOfZero_makesTheNextRequestWait() {
        val policy = RateLimitPolicy(initialLimitPerMinute = 30, minSpacingMs = 0L)
        policy.reserve(nowMs = 0L)
        policy.onResponseHeaders(limitPerMinute = 30, remaining = 0)
        assertEquals(2_000L, policy.reserve(nowMs = 0L))
    }

    @Test
    fun higherLimitHeader_refillsFaster() {
        val policy = RateLimitPolicy(initialLimitPerMinute = 30, minSpacingMs = 0L)
        policy.onResponseHeaders(limitPerMinute = 90, remaining = 0)
        assertEquals(90, policy.limitPerMinute)
        // 90 per minute: one token every 667 ms.
        assertEquals(667L, policy.reserve(nowMs = 0L))
    }

    @Test
    fun afterA429_nothingStartsBeforeTheBlockEnds() {
        val policy = RateLimitPolicy()
        policy.reserve(nowMs = 0L)
        policy.blockUntil(untilMs = 60_000L)
        val start = policy.reserve(nowMs = 1_000L)
        assertTrue("started at $start", start >= 60_000L)
        assertEquals(60_000L, policy.blockedUntilMs)
    }

    @Test
    fun blockUntil_neverShortensAnExistingBlock() {
        val policy = RateLimitPolicy()
        policy.blockUntil(untilMs = 60_000L)
        policy.blockUntil(untilMs = 30_000L)
        assertEquals(60_000L, policy.blockedUntilMs)
    }
}
