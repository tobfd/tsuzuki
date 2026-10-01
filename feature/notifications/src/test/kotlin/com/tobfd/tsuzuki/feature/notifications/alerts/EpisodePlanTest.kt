package com.tobfd.tsuzuki.feature.notifications.alerts

import com.tobfd.tsuzuki.core.model.UpcomingEpisode
import com.tobfd.tsuzuki.core.testing.SampleData
import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EpisodePlanTest {

    private val now = Instant.parse("2026-10-01T18:00:00Z")

    private fun episode(mediaId: Int, airingAt: Instant) =
        UpcomingEpisode(SampleData.listEntry(id = mediaId), episode = 5, airingAt = airingAt)

    @Test
    fun newSession_announcesNothingThatAiredBefore_andWakesUpForTheNext() {
        val upcoming = listOf(episode(1, now.minusSeconds(60)), episode(2, now.plusSeconds(3_600)))

        val plan = EpisodePlan.of(upcoming, checkpoint = null, now = now)

        assertEquals(emptyList<UpcomingEpisode>(), plan.due)
        assertEquals(now.plusSeconds(3_600), plan.nextAlarm)
    }

    @Test
    fun episodesSinceTheCheckpoint_areDue_oldestFirst() {
        val upcoming = listOf(
            episode(1, now.minusSeconds(10)),
            episode(2, now.minusSeconds(600)),
            episode(3, now.minus(Duration.ofHours(2)))
        )

        val plan = EpisodePlan.of(upcoming, checkpoint = now.minusSeconds(3_600), now = now)

        assertEquals(listOf(2, 1), plan.due.map { it.entry.mediaId })
    }

    @Test
    fun anEpisodeAiringRightNow_isDue_notPlannedAgain() {
        val upcoming = listOf(episode(1, now))

        val plan = EpisodePlan.of(upcoming, checkpoint = now.minusSeconds(1), now = now)

        assertEquals(listOf(1), plan.due.map { it.entry.mediaId })
        assertNull(plan.nextAlarm)
    }

    @Test
    fun episodesMissedForTooLong_areSkipped() {
        val upcoming = listOf(episode(1, now.minus(EpisodePlan.MAX_LATE).minusSeconds(1)))

        val plan = EpisodePlan.of(upcoming, checkpoint = now.minus(Duration.ofDays(1)), now = now)

        assertEquals(emptyList<UpcomingEpisode>(), plan.due)
    }

    @Test
    fun nextAlarm_isTheSoonestFutureEpisode() {
        val upcoming = listOf(episode(1, now.plusSeconds(900)), episode(2, now.plusSeconds(300)))

        assertEquals(now.plusSeconds(300), EpisodePlan.of(upcoming, now, now).nextAlarm)
    }
}
