package com.tobfd.tsuzuki.feature.notifications.alerts

import com.tobfd.tsuzuki.core.model.UpcomingEpisode
import java.time.Duration
import java.time.Instant

/**
 * Which new episodes to announce now and when to wake up next, from the airing times the list sync
 * stores in Room (`nextAiringEpisode`, only anime being watched or rewatched). No request involved.
 */
internal data class EpisodePlan(
    /** Episodes that aired after the checkpoint and not too long ago, oldest first. */
    val due: List<UpcomingEpisode>,
    /** The next airing time after now; null when nothing is scheduled. */
    val nextAlarm: Instant?
) {
    companion object {
        /** Episodes that aired longer ago than this (phone off, alarm missed) are skipped, not announced late. */
        val MAX_LATE: Duration = Duration.ofHours(6)

        /**
         * [checkpoint] is where the last plan stopped; null starts at [now], so a new session never
         * announces episodes that aired before it.
         */
        fun of(upcoming: List<UpcomingEpisode>, checkpoint: Instant?, now: Instant): EpisodePlan {
            val from = checkpoint ?: now
            val due = upcoming
                .filter { it.airingAt > from && it.airingAt <= now && Duration.between(it.airingAt, now) <= MAX_LATE }
                .sortedBy { it.airingAt }
            val next = upcoming.map { it.airingAt }.filter { it > now }.minOrNull()
            return EpisodePlan(due, next)
        }
    }
}
