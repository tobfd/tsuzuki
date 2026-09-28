package com.tobfd.tsuzuki.core.data.session

import java.time.Duration
import java.time.Instant

/** Days before expiry from which the app warns that the login will run out (docs/ANILIST_API.md). */
const val EXPIRY_WARNING_DAYS = 14L

/**
 * Whole days left until [expiresAt], rounded up (anything under a day counts as 1), or null when no
 * warning is due yet. Zero or less means the token has expired.
 */
fun expiryWarningDays(expiresAt: Instant, now: Instant): Long? {
    val left = Duration.between(now, expiresAt)
    if (left.isNegative || left.isZero) return 0
    val days = (left.seconds + SECONDS_PER_DAY - 1) / SECONDS_PER_DAY
    return days.takeIf { it <= EXPIRY_WARNING_DAYS }
}

private const val SECONDS_PER_DAY = 86_400L
