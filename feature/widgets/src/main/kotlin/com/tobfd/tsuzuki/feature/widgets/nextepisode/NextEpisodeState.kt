package com.tobfd.tsuzuki.feature.widgets.nextepisode

import com.tobfd.tsuzuki.core.model.UpcomingEpisode
import com.tobfd.tsuzuki.feature.widgets.WIDGET_MAX_ITEMS
import java.time.Duration
import java.time.Instant

/** One anime of the "Next episode" widget. */
internal data class UpcomingItem(
    val mediaId: Int,
    val title: String,
    val episode: Int,
    val airingAt: Instant,
    /** The viewer's progress, to say how many aired episodes they haven't watched yet. */
    val progress: Int,
    val coverUrl: String?,
    val coverColor: String?
) {
    /** Aired episodes not watched yet, not counting [episode]. */
    val behind: Int get() = (episode - 1 - progress).coerceAtLeast(0)
}

internal sealed interface NextEpisodeState {
    data object LoggedOut : NextEpisodeState

    data class Ready(val items: List<UpcomingItem>) : NextEpisodeState
}

internal fun upcomingItems(episodes: List<UpcomingEpisode>): List<UpcomingItem> = episodes
    .take(WIDGET_MAX_ITEMS)
    .map {
        UpcomingItem(
            mediaId = it.entry.mediaId,
            title = it.entry.media.title.userPreferred,
            episode = it.episode,
            airingAt = it.airingAt,
            progress = it.entry.progress,
            coverUrl = it.entry.media.coverUrl,
            coverColor = it.entry.media.coverColor
        )
    }

/** How a row shows the time until [UpcomingItem.airingAt]. */
internal sealed interface Countdown {
    /** Within a day: a live countdown (a `Chronometer`, so it ticks without redrawing the widget). */
    data class Live(val remaining: Duration) : Countdown

    /** Further away: days, rounded, next to the weekday and time (which never go stale). */
    data class Days(val days: Long) : Countdown

    /** The time has passed and the next update hasn't brought the following episode yet. */
    data object Aired : Countdown

    companion object {
        private val LIVE_WITHIN: Duration = Duration.ofDays(1)

        fun of(airingAt: Instant, now: Instant): Countdown {
            val remaining = Duration.between(now, airingAt)
            return when {
                remaining.isNegative || remaining.isZero -> Aired
                remaining < LIVE_WITHIN -> Live(remaining)
                else -> Days(remaining.plusHours(12).toDays())
            }
        }
    }
}
