package com.tobfd.tsuzuki.feature.notifications.alerts

import com.tobfd.tsuzuki.core.model.Notification
import com.tobfd.tsuzuki.core.model.UpcomingEpisode
import java.time.Instant

/** Records what would be shown. */
class FakeAlertPoster : AlertPoster {
    val episodes = mutableListOf<UpcomingEpisode>()
    val aniList = mutableListOf<Notification>()
    var aniListCancels = 0
    var allCancels = 0

    override fun postEpisodes(episodes: List<UpcomingEpisode>) {
        this.episodes += episodes
    }

    override fun postAniList(notifications: List<Notification>) {
        aniList += notifications
    }

    override fun cancelAniList() {
        aniListCancels++
    }

    override fun cancelAll() {
        allCancels++
    }
}

/** Channels switched on by default; tests switch them off. */
class FakeAlertGate : AlertGate {
    val disabled = mutableSetOf<AlertChannel>()

    override fun isEnabled(channel: AlertChannel): Boolean = channel !in disabled
}

class FakeEpisodeAlarms : EpisodeAlarmScheduler {
    /** The planned alarm, null when there is none. */
    var scheduled: Instant? = null

    override fun schedule(at: Instant) {
        scheduled = at
    }

    override fun cancel() {
        scheduled = null
    }
}

class FakeNotificationChecks : NotificationCheckScheduler {
    var scheduled = false

    override fun ensureScheduled() {
        scheduled = true
    }

    override fun cancel() {
        scheduled = false
    }
}
