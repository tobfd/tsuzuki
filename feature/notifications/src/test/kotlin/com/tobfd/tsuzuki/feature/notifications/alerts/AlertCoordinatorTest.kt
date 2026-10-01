package com.tobfd.tsuzuki.feature.notifications.alerts

import com.tobfd.tsuzuki.core.common.AppError
import com.tobfd.tsuzuki.core.data.notifications.AlertsRepository
import com.tobfd.tsuzuki.core.data.widget.AiringRepository
import com.tobfd.tsuzuki.core.model.Notification
import com.tobfd.tsuzuki.core.model.SessionState
import com.tobfd.tsuzuki.core.model.UpcomingEpisode
import com.tobfd.tsuzuki.core.model.UserLite
import com.tobfd.tsuzuki.core.testing.FakeSessionRepository
import com.tobfd.tsuzuki.core.testing.SampleData
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertCoordinatorTest {

    private val now = Instant.parse("2026-10-01T18:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    private val session = FakeSessionRepository(SessionState.LoggedIn(SampleData.viewer, SampleData.tokenExpiry))
    private val airing = FakeAiringRepository()
    private val alerts = FakeAlertsRepository()
    private val gate = FakeAlertGate()
    private val alarms = FakeEpisodeAlarms()
    private val checks = FakeNotificationChecks()
    private val poster = FakeAlertPoster()

    private fun TestScope.coordinator() =
        AlertCoordinator(backgroundScope, session, airing, alerts, gate, alarms, checks, poster, clock)

    private fun episode(mediaId: Int, airingAt: Instant) =
        UpcomingEpisode(SampleData.listEntry(id = mediaId), episode = 5, airingAt = airingAt)

    @Test
    fun loggedIn_plansTheNextEpisode_andTheNotificationCheck() = runTest {
        airing.episodes.value = listOf(episode(1, now.plusSeconds(3_600)))

        coordinator().start()
        runCurrent()

        assertEquals(now.plusSeconds(3_600), alarms.scheduled)
        assertTrue(checks.scheduled)
        assertEquals(now, alerts.checkpoint)
    }

    @Test
    fun anEpisodeThatAired_isAnnounced_andTheNextOneIsPlanned() = runTest {
        alerts.checkpoint = now.minusSeconds(3_600)
        airing.episodes.value = listOf(episode(1, now.minusSeconds(5)), episode(2, now.plusSeconds(600)))

        coordinator().replan()

        assertEquals(listOf(1), poster.episodes.map { it.entry.mediaId })
        assertEquals(now.plusSeconds(600), alarms.scheduled)
    }

    @Test
    fun episodesChannelOff_noAlarm_andNothingAnnouncedLaterOn() = runTest {
        gate.disabled += AlertChannel.Episodes
        alerts.checkpoint = now.minusSeconds(3_600)
        airing.episodes.value = listOf(episode(1, now.minusSeconds(5)), episode(2, now.plusSeconds(600)))

        coordinator().replan()

        assertNull(alarms.scheduled)
        assertTrue(poster.episodes.isEmpty())
        assertEquals(now, alerts.checkpoint)
    }

    @Test
    fun allAniListChannelsOff_noCheckIsScheduled() = runTest {
        gate.disabled += AlertChannel.aniList
        checks.scheduled = true

        coordinator().replan()

        assertFalse(checks.scheduled)
    }

    @Test
    fun check_withAllAniListChannelsOff_makesNoRequest() = runTest {
        gate.disabled += AlertChannel.aniList

        coordinator().checkNotifications()

        assertEquals(0, alerts.checks)
    }

    @Test
    fun check_showsNewNotifications_ofEnabledChannelsOnly_andNeverAiring() = runTest {
        gate.disabled += AlertChannel.Follows
        val like = Notification.ActivityEvent(
            id = 3,
            createdAt = now,
            kind = com.tobfd.tsuzuki.core.model.ActivityNotificationKind.Like,
            users = listOf(UserLite(1, "Fern", null)),
            activityId = 9,
            listActivity = null
        )
        val follow = Notification.Follow(2, now, UserLite(2, "Stark", null))
        val airingNotification = Notification.Airing(1, now, 5, SampleData.frieren.media)
        alerts.newResult = Result.success(listOf(like, follow, airingNotification))

        coordinator().checkNotifications()

        assertEquals(listOf(like), poster.aniList)
    }

    @Test
    fun check_failure_showsNothing() = runTest {
        alerts.newResult = Result.failure(AppError.Offline)

        coordinator().checkNotifications()

        assertTrue(poster.aniList.isEmpty())
    }

    @Test
    fun logout_stopsEverything() = runTest {
        airing.episodes.value = listOf(episode(1, now.plusSeconds(3_600)))
        coordinator().start()
        runCurrent()

        session.logOut()
        runCurrent()

        assertNull(alarms.scheduled)
        assertFalse(checks.scheduled)
        assertEquals(1, poster.allCancels)
        assertTrue(alerts.cleared)
    }

    @Test
    fun guest_getsNoAlarmAndNoCheck() = runTest {
        session.sessionState.value = SessionState.Guest
        airing.episodes.value = listOf(episode(1, now.plusSeconds(3_600)))

        val coordinator = coordinator()
        coordinator.start()
        runCurrent()
        coordinator.checkNotifications()

        assertNull(alarms.scheduled)
        assertFalse(checks.scheduled)
        assertEquals(0, alerts.checks)
    }
}

private class FakeAiringRepository : AiringRepository {
    val episodes = MutableStateFlow<List<UpcomingEpisode>>(emptyList())
    override val upcoming = episodes.map { list -> list.sortedBy { it.airingAt } }

    override suspend fun refresh(): Result<Instant?> = Result.success(null)
}

private class FakeAlertsRepository : AlertsRepository {
    var checkpoint: Instant? = null
    var newResult: Result<List<Notification>> = Result.success(emptyList())
    var checks = 0
    var cleared = false
    override val permissionHintShown = MutableStateFlow(false)

    override suspend fun markPermissionHintShown() {
        permissionHintShown.value = true
    }

    override suspend fun episodeCheckpoint(): Instant? = checkpoint

    override suspend fun setEpisodeCheckpoint(at: Instant) {
        checkpoint = at
    }

    override suspend fun newNotifications(): Result<List<Notification>> {
        checks++
        return newResult
    }

    override suspend fun clear() {
        cleared = true
        checkpoint = null
    }
}
