package com.tobfd.tsuzuki.feature.notifications.alerts

import com.tobfd.tsuzuki.core.common.ApplicationScope
import com.tobfd.tsuzuki.core.data.notifications.AlertsRepository
import com.tobfd.tsuzuki.core.data.session.SessionRepository
import com.tobfd.tsuzuki.core.data.widget.AiringRepository
import com.tobfd.tsuzuki.core.model.SessionState
import com.tobfd.tsuzuki.core.model.UpcomingEpisode
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The Android notifications (docs/ROADMAP.md, Android notifications):
 * - New episodes of anime being watched or rewatched, announced by a local alarm at their airing time,
 *   from what the list sync stored in Room. No request.
 * - New AniList notifications (likes, replies, follows, media changes), checked about every 30 minutes
 *   with one small request, and a second one only when the unread count went up.
 *
 * Before planning an alarm or checking AniList it makes sure the notifications would be shown: with the
 * app's notifications or the matching channels off there is no alarm and no request. Logging out stops all.
 */
@Singleton
class AlertCoordinator @Inject internal constructor(
    @ApplicationScope private val scope: CoroutineScope,
    private val sessionRepository: SessionRepository,
    private val airingRepository: AiringRepository,
    private val alertsRepository: AlertsRepository,
    private val gate: AlertGate,
    private val alarms: EpisodeAlarmScheduler,
    private val checks: NotificationCheckScheduler,
    private val poster: AlertPoster,
    private val clock: Clock
) {
    private val mutex = Mutex()

    /** Called once when the app process starts: plans whenever the session or the airing times change. */
    fun start() {
        scope.launch {
            combine(viewerIds(), airingRepository.upcoming) { viewer, upcoming -> viewer to upcoming }
                .collectLatest { (viewer, upcoming) -> if (viewer == null) stop() else plan(upcoming) }
        }
    }

    /** Plans again, e.g. when an episode aired, the app comes to the front or a permission changed. */
    suspend fun replan() {
        if (viewerIds().first() == null) return
        plan(airingRepository.upcoming.first())
    }

    /** One periodic check for new AniList notifications; failures wait for the next period. */
    suspend fun checkNotifications() {
        val enabled = AlertChannel.aniList.filter(gate::isEnabled).toSet()
        if (viewerIds().first() == null || enabled.isEmpty()) {
            checks.cancel()
            return
        }
        val fresh = alertsRepository.newNotifications().getOrNull() ?: return
        val shown = fresh.filter { AlertChannel.of(it) in enabled }
        if (shown.isNotEmpty()) poster.postAniList(shown)
    }

    private suspend fun plan(upcoming: List<UpcomingEpisode>) = mutex.withLock {
        val now = clock.instant()
        if (gate.isEnabled(AlertChannel.Episodes)) {
            val plan = EpisodePlan.of(upcoming, alertsRepository.episodeCheckpoint(), now)
            if (plan.due.isNotEmpty()) poster.postEpisodes(plan.due)
            plan.nextAlarm?.let(alarms::schedule) ?: alarms.cancel()
        } else {
            alarms.cancel()
        }
        // On or off, what aired until now is handled: nothing is announced late once it is switched on again.
        alertsRepository.setEpisodeCheckpoint(now)
        if (AlertChannel.aniList.any(gate::isEnabled)) checks.ensureScheduled() else checks.cancel()
    }

    private suspend fun stop() = mutex.withLock {
        alarms.cancel()
        checks.cancel()
        poster.cancelAll()
        alertsRepository.clear()
    }

    private fun viewerIds(): Flow<Int?> = sessionRepository.session
        .filterNot { it is SessionState.Loading }
        .map { (it as? SessionState.LoggedIn)?.viewer?.id }
        .distinctUntilChanged()
}
