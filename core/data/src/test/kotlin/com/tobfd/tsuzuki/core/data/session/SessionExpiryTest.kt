package com.tobfd.tsuzuki.core.data.session

import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SessionExpiryTest {

    private val now = Instant.parse("2026-09-28T12:00:00Z")

    @Test
    fun moreThan14DaysLeft_noWarning() {
        assertNull(expiryWarningDays(now + Duration.ofDays(15), now))
    }

    @Test
    fun exactly14DaysLeft_warnsWith14() {
        assertEquals(14L, expiryWarningDays(now + Duration.ofDays(14), now))
    }

    @Test
    fun partialDays_roundUp() {
        assertEquals(1L, expiryWarningDays(now + Duration.ofHours(3), now))
        assertEquals(3L, expiryWarningDays(now + Duration.ofDays(2) + Duration.ofMinutes(1), now))
    }

    @Test
    fun expiredToken_isZero() {
        assertEquals(0L, expiryWarningDays(now, now))
        assertEquals(0L, expiryWarningDays(now - Duration.ofDays(1), now))
    }
}
